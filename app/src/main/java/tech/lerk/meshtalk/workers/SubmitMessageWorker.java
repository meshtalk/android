package tech.lerk.meshtalk.workers;

import android.content.Context;
import android.util.Log;

import androidx.annotation.NonNull;
import androidx.work.Data;
import androidx.work.ListenableWorker;
import androidx.work.WorkerParameters;

import com.google.gson.Gson;
import com.google.gson.JsonIOException;
import com.google.gson.JsonSyntaxException;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.MalformedURLException;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.UUID;

import tech.lerk.meshtalk.Utils;
import tech.lerk.meshtalk.entities.Message;

public class SubmitMessageWorker extends GatewayWorker {
    private static final String TAG = SubmitMessageWorker.class.getCanonicalName();

    private final Gson gson;

    public SubmitMessageWorker(@NonNull Context context, @NonNull WorkerParameters params) {
        super(context, params);
        gson = Utils.getGson();
    }

    @NonNull
    @Override
    public ListenableWorker.Result doWork() {
        int errorCode = ERROR_INVALID_SETTINGS;
        String hostString = getGatewayInfo().toString() + "/messages/save";
        errorCode = ERROR_NONE;
        try {
            URL gatewayMetaUrl = new URL(hostString);
            HttpURLConnection connection = (HttpURLConnection) gatewayMetaUrl.openConnection();
            connection.setRequestMethod("POST");
            try {
                Message message = new Message();
                message.setId(UUID.fromString(getInputData().getString(DataKeys.MESSAGE_ID.toString())));
                message.setChat(UUID.fromString(getInputData().getString(DataKeys.MESSAGE_CHAT.toString())));
                message.setSender(UUID.fromString(getInputData().getString(DataKeys.MESSAGE_SENDER.toString())));
                message.setReceiver(UUID.fromString(getInputData().getString(DataKeys.MESSAGE_RECEIVER.toString())));
                message.setDate(LocalDateTime.ofEpochSecond(getInputData().getLong(DataKeys.MESSAGE_DATE.toString(), 0), 0, ZoneOffset.UTC));
                message.setContent(getInputData().getString(DataKeys.MESSAGE_CONTENT.toString()));

                byte[] jsonBytes = gson.toJson(message).getBytes(StandardCharsets.UTF_8);
                try (OutputStream os = connection.getOutputStream()) {
                    os.write(jsonBytes, 0, jsonBytes.length);
                    os.flush();
                }

                try (InputStream inputStream = connection.getInputStream()) {
                    BufferedReader reader = new BufferedReader(new InputStreamReader(inputStream, StandardCharsets.UTF_8), 8);
                    StringBuilder result = new StringBuilder();
                    String s = reader.readLine();
                    while (s != null) {
                        result.append(s);
                        s = reader.readLine();
                    }
                    String r = result.toString(); //TODO: remove debug stuff when no longer needed
                    return ListenableWorker.Result.success(new Data.Builder()
                            .putInt(DataKeys.ERROR_CODE.toString(), errorCode)
                            .build());
                }
            } catch (JsonSyntaxException | JsonIOException e) {
                Log.e(TAG, "Unable to parse gateway response!", e);
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
        return ListenableWorker.Result.failure(new Data.Builder().putInt(DataKeys.ERROR_CODE.toString(), errorCode).build());
    }
}
