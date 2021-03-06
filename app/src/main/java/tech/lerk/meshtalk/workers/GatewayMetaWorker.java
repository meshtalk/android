package tech.lerk.meshtalk.workers;

import android.content.Context;
import android.util.Log;

import androidx.annotation.NonNull;
import androidx.work.Data;
import androidx.work.WorkerParameters;

import com.google.gson.Gson;
import com.google.gson.JsonIOException;
import com.google.gson.JsonSyntaxException;

import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.MalformedURLException;
import java.net.URL;
import java.nio.charset.StandardCharsets;

import tech.lerk.meshtalk.Utils;
import tech.lerk.meshtalk.entities.MetaInfo;

public class GatewayMetaWorker extends GatewayWorker {
    private static final String TAG = GatewayMetaWorker.class.getCanonicalName();

    private final Gson gson;

    public GatewayMetaWorker(@NonNull Context context, @NonNull WorkerParameters params) {
        super(context, params);
        gson = Utils.getGson();
    }

    @NonNull
    @Override
    public Result doWork() {
        GatewayInfo gatewayInfo = getGatewayInfo();
        int errorCode = ERROR_NONE;
        String s = gatewayInfo.toString();
        String hostString = s + "/meta";
        try {
            URL gatewayMetaUrl = new URL(hostString);
            HttpURLConnection connection = (HttpURLConnection) gatewayMetaUrl.openConnection();
            try {
                try (InputStream inputStream = connection.getInputStream()) {
                    InputStreamReader metaReader = new InputStreamReader(inputStream, StandardCharsets.UTF_8);
                    MetaInfo metaInfo = gson.fromJson(metaReader, MetaInfo.class);
                    Log.i(TAG, "Fetching meta from gateway: '" + gatewayMetaUrl + "'!");
                    return Result.success(new Data.Builder()
                            .putInt(DataKeys.ERROR_CODE.toString(), errorCode)
                            .putInt(DataKeys.API_VERSION.toString(), metaInfo.getApiVersion())
                            .putString(DataKeys.CORE_VERSION.toString(), metaInfo.getCoreVersion())
                            .build());
                }
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

        return Result.failure(new Data.Builder().putInt(DataKeys.ERROR_CODE.toString(), errorCode).build());
    }
}
