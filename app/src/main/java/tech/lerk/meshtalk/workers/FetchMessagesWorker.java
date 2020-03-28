package tech.lerk.meshtalk.workers;

import android.content.Context;
import android.content.SharedPreferences;
import android.util.Log;

import androidx.annotation.NonNull;
import androidx.preference.PreferenceManager;
import androidx.work.Data;
import androidx.work.ListenableWorker;
import androidx.work.WorkerParameters;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonIOException;
import com.google.gson.JsonSyntaxException;
import com.google.gson.reflect.TypeToken;
import com.google.gson.stream.JsonReader;
import com.google.gson.typeadapters.RuntimeTypeAdapterFactory;

import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStreamWriter;
import java.net.HttpURLConnection;
import java.net.MalformedURLException;
import java.net.URL;
import java.security.PrivateKey;
import java.security.PublicKey;
import java.sql.Time;
import java.util.ArrayList;
import java.util.UUID;

import tech.lerk.meshtalk.Meta;
import tech.lerk.meshtalk.R;
import tech.lerk.meshtalk.adapters.PrivateKeyTypeAdapter;
import tech.lerk.meshtalk.adapters.PublicKeyTypeAdapter;
import tech.lerk.meshtalk.entities.Chat;
import tech.lerk.meshtalk.entities.Message;
import tech.lerk.meshtalk.entities.Preferences;
import tech.lerk.meshtalk.providers.IdentityProvider;

public class FetchMessagesWorker extends GatewayWorker {
    private static final String TAG = FetchMessagesWorker.class.getCanonicalName();

    public static final int ERROR_INVALID_SETTINGS = -1;
    public static final int ERROR_NONE = 0;
    public static final int ERROR_URI = 1;
    public static final int ERROR_CONNECTION = 2;
    public static final int ERROR_PARSING = 3;

    public static final String MESSAGE_ID = "id";
    public static final String MESSAGE_SENDER = "sender";
    public static final String MESSAGE_RECEIVER = "receiver";
    public static final String MESSAGE_CHAT = "chat";
    public static final String MESSAGE_DATE = "date";
    public static final String MESSAGE_CONTENT = "content";

    private final IdentityProvider identityProvider;
    private final Gson gson;

    public FetchMessagesWorker(@NonNull Context context, @NonNull WorkerParameters params) {
        super(context, params);
        identityProvider = IdentityProvider.get(getApplicationContext());
        gson = new GsonBuilder()
                .registerTypeAdapter(PrivateKey.class, new PrivateKeyTypeAdapter())
                .registerTypeAdapter(PublicKey.class, new PublicKeyTypeAdapter())
                .registerTypeAdapter(Message.class, RuntimeTypeAdapterFactory.of(Message.class, "type")
                        .registerSubtype(Message.class, Message.class.getName())
                        .registerSubtype(Chat.Handshake.class, Chat.Handshake.class.getName()))
                .create();
    }

    @NonNull
    @Override
    public Result doWork() {
        GatewayInfo gatewayInfo = getGatewayInfo();
        int errorCode = ERROR_INVALID_SETTINGS;
        String defaultdentityString = preferences.getString(Preferences.DEFAULT_IDENTITY.toString(), null);
        if (defaultdentityString != null) {
            errorCode = ERROR_NONE;
            String hostString = gatewayInfo.toString() + "/receiver/" + defaultdentityString;
            try {
                URL gatewayMetaUrl = new URL(hostString);
                HttpURLConnection connection = (HttpURLConnection) gatewayMetaUrl.openConnection();
                try (InputStream io = connection.getInputStream()) {
                    gson.fromJson(new JsonReader(new InputStreamReader(io)),
                            new TypeToken<ArrayList<Message>>() {
                            }.getType());
                    return ListenableWorker.Result.success(new Data.Builder()
                            .putInt(DataKeys.ERROR_CODE.toString(), errorCode)
                            .build());
                } catch (JsonSyntaxException | JsonIOException e) {
                    Log.e(TAG, "Unable to parse gateway metadata!", e);
                    errorCode = ERROR_PARSING;
                } finally {
                    connection.disconnect();
                }
            } catch (MalformedURLException e) {
                Log.e(TAG, "Unable to parse uri: '" + hostString + "'!", e);
                errorCode = ERROR_URI;
            } catch (IOException e) {
                Log.e(TAG, "Unable to open connection to: '" + hostString + "'!", e);
                errorCode = ERROR_CONNECTION;
            }
        }
        return ListenableWorker.Result.failure(new Data.Builder().putInt(DataKeys.ERROR_CODE.toString(), errorCode).build());
    }
}
