package tech.lerk.meshtalk.workers;

import android.content.Context;
import android.content.SharedPreferences;
import android.util.Log;

import androidx.annotation.NonNull;
import androidx.preference.PreferenceManager;
import androidx.work.Data;
import androidx.work.ListenableWorker;
import androidx.work.Worker;
import androidx.work.WorkerParameters;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonIOException;
import com.google.gson.JsonSyntaxException;
import com.google.gson.typeadapters.RuntimeTypeAdapterFactory;

import java.io.IOException;
import java.io.OutputStreamWriter;
import java.net.HttpURLConnection;
import java.net.MalformedURLException;
import java.net.URL;
import java.security.PrivateKey;
import java.security.PublicKey;
import java.sql.Time;
import java.util.UUID;

import tech.lerk.meshtalk.R;
import tech.lerk.meshtalk.adapters.PrivateKeyTypeAdapter;
import tech.lerk.meshtalk.adapters.PublicKeyTypeAdapter;
import tech.lerk.meshtalk.entities.Chat;
import tech.lerk.meshtalk.entities.Message;
import tech.lerk.meshtalk.entities.Preferences;

public class SubmitMessageWorker extends Worker {
    private static final String TAG = GatewayMetaWorker.class.getCanonicalName();

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

    private final SharedPreferences preferences;
    private final Gson gson;

    public SubmitMessageWorker(@NonNull Context context, @NonNull WorkerParameters params) {
        super(context, params);
        preferences = PreferenceManager.getDefaultSharedPreferences(getApplicationContext());
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
    public ListenableWorker.Result doWork() {
        String defaultGatewayHost = getApplicationContext().getString(R.string.pref_default_message_gateway_host);
        String defaultGatewayPort = getApplicationContext().getString(R.string.pref_default_message_gateway_port);
        String gatewayHost = preferences.getString(Preferences.MESSAGE_GATEWAY_HOST.toString(), defaultGatewayHost);
        int gatewayPort = Integer.parseInt(preferences.getString(Preferences.MESSAGE_GATEWAY_PORT.toString(), defaultGatewayPort));
        String gatewayMetricsPath = preferences.getString(Preferences.MESSAGE_GATEWAY_PATH.toString(), "") + "/";
        String gatewayProtocol = preferences.getString(Preferences.MESSAGE_GATEWAY_PROTOCOL.toString(), "http");
        int errorCode = ERROR_INVALID_SETTINGS;
        if (!gatewayHost.isEmpty()) {
            errorCode = ERROR_NONE;
            String hostString = gatewayProtocol + "://" + gatewayHost + ":" + gatewayPort + "/" + gatewayMetricsPath;
            try {
                URL gatewayMetaUrl = new URL(gatewayProtocol, gatewayHost, gatewayPort, gatewayMetricsPath);
                HttpURLConnection connection = (HttpURLConnection) gatewayMetaUrl.openConnection();
                connection.setRequestMethod("POST");
                try {
                    Message message = new Message();
                    message.setId(UUID.fromString(getInputData().getString(MESSAGE_ID)));
                    message.setChat(UUID.fromString(getInputData().getString(MESSAGE_CHAT)));
                    message.setSender(UUID.fromString(getInputData().getString(MESSAGE_SENDER)));
                    message.setReceiver(UUID.fromString(getInputData().getString(MESSAGE_RECEIVER)));
                    message.setDate(Time.valueOf(getInputData().getString(MESSAGE_DATE)));
                    message.setContent(getInputData().getString(MESSAGE_CONTENT));

                    gson.toJson(message, new OutputStreamWriter(connection.getOutputStream()));

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
