package tech.lerk.meshtalk.workers;

import android.content.Context;
import android.content.SharedPreferences;
import android.util.Log;

import androidx.annotation.NonNull;
import androidx.preference.PreferenceManager;
import androidx.work.Data;
import androidx.work.Worker;
import androidx.work.WorkerParameters;

import com.google.gson.Gson;
import com.google.gson.JsonIOException;
import com.google.gson.JsonSyntaxException;

import java.io.BufferedInputStream;
import java.io.IOException;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.MalformedURLException;
import java.net.URL;
import java.nio.charset.StandardCharsets;

import tech.lerk.meshtalk.R;
import tech.lerk.meshtalk.entities.MetaInfo;
import tech.lerk.meshtalk.entities.Preferences;

public class GatewayMetaWorker extends Worker {
    private static final String TAG = GatewayMetaWorker.class.getCanonicalName();

    public static final int ERROR_INVALID_SETTINGS = -1;
    public static final int ERROR_NONE = 0;
    public static final int ERROR_URI = 1;
    public static final int ERROR_CONNECTION = 2;
    public static final int ERROR_PARSING = 3;

    private final SharedPreferences preferences;

    public GatewayMetaWorker(@NonNull Context context, @NonNull WorkerParameters params) {
        super(context, params);
        preferences = PreferenceManager.getDefaultSharedPreferences(getApplicationContext());
    }

    @NonNull
    @Override
    public Result doWork() {
        String defaultGatewayHost = getApplicationContext().getString(R.string.pref_default_message_gateway_host);
        String gatewayHost = preferences.getString(Preferences.MESSAGE_GATEWAY_HOST.toString(), defaultGatewayHost);
        String gatewayMetricsPath = preferences.getString(Preferences.MESSAGE_GATEWAY_PATH.toString(), "") + "/metrics";
        String gatewayProtocol = preferences.getString(Preferences.MESSAGE_GATEWAY_PROTOCOL.toString(), "http");
        int errorCode = ERROR_INVALID_SETTINGS;
        if (!gatewayHost.isEmpty()) {
            errorCode = ERROR_NONE;
            try {
                URL gatewayMetaUrl = new URL(gatewayProtocol, gatewayHost, gatewayMetricsPath);
                HttpURLConnection connection = (HttpURLConnection) gatewayMetaUrl.openConnection();

                try {
                    InputStreamReader metaReader = new InputStreamReader(new BufferedInputStream(connection.getInputStream()), StandardCharsets.UTF_8);
                    MetaInfo metaInfo = new Gson().fromJson(metaReader, MetaInfo.class);
                    Log.i(TAG, "connected to gateway: '" + gatewayMetaUrl + "'!");
                    return Result.success(new Data.Builder()
                            .putInt(DataKeys.ERROR_CODE.toString(), errorCode)
                            .putInt(DataKeys.API_VERSION.toString(), metaInfo.getApiVersion())
                            .putString(DataKeys.CORE_VERSION.toString(), metaInfo.getCoreVersion())
                            .build());
                } catch (JsonSyntaxException | JsonIOException e) {
                    Log.e(TAG, "Unable to parse gateway metadata!", e);
                    errorCode = ERROR_PARSING;
                } finally {
                    connection.disconnect();
                }
            } catch (MalformedURLException e) {
                Log.e(TAG, "Unable to parse uri: '" + gatewayProtocol + "://" + gatewayHost + "/" + gatewayMetricsPath + "'!", e);
                errorCode = ERROR_URI;
            } catch (IOException e) {
                Log.e(TAG, "Unable to open connection to: '" + gatewayProtocol + "://" + gatewayHost + "/" + gatewayMetricsPath + "'!", e);
                errorCode = ERROR_CONNECTION;
            }
        }
        return Result.failure(new Data.Builder().putInt(DataKeys.ERROR_CODE.toString(), errorCode).build());
    }
}
