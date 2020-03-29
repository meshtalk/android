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

import java.io.IOException;
import java.io.OutputStreamWriter;
import java.net.HttpURLConnection;
import java.net.MalformedURLException;
import java.net.URL;
import java.sql.Time;
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
                message.setDate(Time.valueOf(getInputData().getString(DataKeys.MESSAGE_DATE.toString())));
                message.setContent(getInputData().getString(DataKeys.MESSAGE_CONTENT.toString()));

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
        return ListenableWorker.Result.failure(new Data.Builder().putInt(DataKeys.ERROR_CODE.toString(), errorCode).build());
    }
}
