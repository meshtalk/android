package tech.lerk.meshtalk.workers;

import android.content.Context;
import android.os.Looper;
import android.util.Log;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.work.Data;
import androidx.work.ListenableWorker;
import androidx.work.WorkerParameters;

import com.google.gson.Gson;
import com.google.gson.JsonIOException;
import com.google.gson.JsonSyntaxException;
import com.google.gson.reflect.TypeToken;
import com.google.gson.stream.JsonReader;

import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.lang.reflect.Type;
import java.net.HttpURLConnection;
import java.net.MalformedURLException;
import java.net.URL;
import java.util.ArrayList;

import tech.lerk.meshtalk.R;
import tech.lerk.meshtalk.Utils;
import tech.lerk.meshtalk.entities.Handshake;
import tech.lerk.meshtalk.entities.Preferences;

public class FetchHandshakesWorker extends GatewayWorker {
    private static final String TAG = FetchHandshakesWorker.class.getCanonicalName();

    public static final int ERROR_NOT_FOUND = 4;

    private final Gson gson;

    public FetchHandshakesWorker(@NonNull Context context, @NonNull WorkerParameters params) {
        super(context, params);
        gson = Utils.getGson();
    }

    @NonNull
    @Override
    public Result doWork() {
        GatewayInfo gatewayInfo = getGatewayInfo();
        int errorCode = ERROR_INVALID_SETTINGS;
        String defaultdentityString = preferences.getString(Preferences.DEFAULT_IDENTITY.toString(), null);
        if (defaultdentityString != null) {
            errorCode = ERROR_NONE;
            String hostString = gatewayInfo.toString() + "/handshakes/receiver/" + defaultdentityString;
            if (Looper.myLooper() == null) {
                Looper.prepare();
            }
            Toast.makeText(getApplicationContext(), R.string.info_fetching_handshakes, Toast.LENGTH_SHORT).show();
            try {
                URL gatewayMetaUrl = new URL(hostString);
                HttpURLConnection connection = (HttpURLConnection) gatewayMetaUrl.openConnection();
                try (InputStream io = connection.getInputStream()) {
                    ArrayList<Handshake> handshakes = gson.fromJson(new JsonReader(new InputStreamReader(io)), getHandshakeListType());
                    Data.Builder dataBuilder = new Data.Builder().putInt(DataKeys.ERROR_CODE.toString(), errorCode);
                    dataBuilder.putInt(DataKeys.HANDSHAKE_LIST_SIZE.toString(), handshakes.size());
                    for (int i = 0; i < handshakes.size(); i++) {
                        dataBuilder.putString(DataKeys.HANDSHAKE_LIST_ELEMENT_PREFIX + String.valueOf(i), gson.toJson(handshakes.get(i)));
                    }
                    return ListenableWorker.Result.success(dataBuilder.build());
                } catch (JsonSyntaxException | JsonIOException e) {
                    Log.e(TAG, "Unable to parse gateway metadata!", e);
                    errorCode = ERROR_PARSING;
                } finally {
                    connection.disconnect();
                }
            } catch (MalformedURLException e) {
                Log.e(TAG, "Unable to parse uri: '" + hostString + "'!", e);
                errorCode = ERROR_URI;
            } catch (FileNotFoundException e) {
                Log.i(TAG, "No handshakes found for: '" + defaultdentityString + "'!");
                errorCode = ERROR_NOT_FOUND;
            } catch (IOException e) {
                Log.e(TAG, "Unable to open connection to: '" + hostString + "'!", e);
                errorCode = ERROR_CONNECTION;
            }
        }
        return ListenableWorker.Result.failure(new Data.Builder().putInt(DataKeys.ERROR_CODE.toString(), errorCode).build());
    }

    private Type getHandshakeListType() {
        //@formatter:off
        return new TypeToken<ArrayList<Handshake>>() {}.getType();
        //@formatter:on
    }
}
