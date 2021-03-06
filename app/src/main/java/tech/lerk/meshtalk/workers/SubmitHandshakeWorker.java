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
import tech.lerk.meshtalk.entities.Handshake;
import tech.lerk.meshtalk.providers.impl.ChatProvider;

public class SubmitHandshakeWorker extends GatewayWorker {
    private static final String TAG = SubmitHandshakeWorker.class.getCanonicalName();

    private final Gson gson;

    public SubmitHandshakeWorker(@NonNull Context context, @NonNull WorkerParameters params) {
        super(context, params);
        gson = Utils.getGson();
    }

    @NonNull
    @Override
    public ListenableWorker.Result doWork() {
        GatewayInfo gatewayInfo = getGatewayInfo();
        int errorCode = ERROR_NONE;
        String hostString = gatewayInfo.toString() + "/handshakes/save";
        try {
            URL gatewayMetaUrl = new URL(hostString);
            HttpURLConnection connection = (HttpURLConnection) gatewayMetaUrl.openConnection();
            connection.setRequestMethod("POST");
            connection.setDoOutput(true);
            connection.connect();
            try {
                UUID chatId = UUID.fromString(getInputData().getString(DataKeys.HANDSHAKE_CHAT.toString()));
                UUID handshakeId = UUID.fromString(getInputData().getString(DataKeys.HANDSHAKE_ID.toString()));
                Handshake handshake = new Handshake();
                handshake.setId(handshakeId);
                handshake.setChat(chatId);
                handshake.setSender(UUID.fromString(getInputData().getString(DataKeys.HANDSHAKE_SENDER.toString())));
                handshake.setReceiver(UUID.fromString(getInputData().getString(DataKeys.HANDSHAKE_RECEIVER.toString())));
                handshake.setDate(LocalDateTime.ofEpochSecond(getInputData().getLong(DataKeys.HANDSHAKE_DATE.toString(), 0), 0, ZoneOffset.UTC));
                handshake.setKey(getInputData().getString(DataKeys.HANDSHAKE_KEY.toString()));
                handshake.setIv(getInputData().getString(DataKeys.HANDSHAKE_IV.toString()));
                handshake.setReply(UUID.fromString(getInputData().getString(DataKeys.HANDSHAKE_REPLY.toString())));

                byte[] jsonBytes = gson.toJson(handshake).getBytes(StandardCharsets.UTF_8);
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
                    ChatProvider.get(getApplicationContext()).getById(chatId, chat -> {
                        if (chat != null) {
                            chat.setHandshake(handshakeId);
                            ChatProvider.get(getApplicationContext()).save(chat);
                        } else {
                            Log.wtf(TAG, "Unable to find chat!");
                        }
                    });
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
