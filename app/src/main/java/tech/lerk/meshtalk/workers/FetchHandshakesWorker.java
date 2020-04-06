package tech.lerk.meshtalk.workers;

import android.content.Context;
import android.os.Looper;
import android.util.Log;
import android.util.Pair;
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
import tech.lerk.meshtalk.Preferences;

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
        String defaultdentityString = preferences.getString(Preferences.DEFAULT_IDENTITY.toString(), null);
        if (defaultdentityString != null) {
            if (Looper.myLooper() == null) {
                Looper.prepare();
            }
            Toast.makeText(getApplicationContext(), R.string.info_fetching_handshakes, Toast.LENGTH_SHORT).show();

            ArrayList<Handshake> handshakes = new ArrayList<>();

            Pair<Integer, ArrayList<Handshake>> senderPair = fetchHandshakesSender(defaultdentityString, gatewayInfo);
            Pair<Integer, ArrayList<Handshake>> receiverPair = fetchHandshakesReceiver(defaultdentityString, gatewayInfo);

            Integer receiverRes = receiverPair.first;
            Integer senderRes = senderPair.first;
            if (!senderRes.equals(receiverRes)) {
                Log.w(TAG, "Request results not the same, potential error might be skipped!");
            }

            handshakes.addAll(senderPair.second);
            handshakes.addAll(receiverPair.second);

            Data.Builder dataBuilder = new Data.Builder().putInt(DataKeys.ERROR_CODE.toString(), pickGreater(senderRes, receiverRes));
            dataBuilder.putInt(DataKeys.HANDSHAKE_LIST_SIZE.toString(), handshakes.size());
            for (int i = 0; i < handshakes.size(); i++) {
                dataBuilder.putString(DataKeys.HANDSHAKE_LIST_ELEMENT_PREFIX + String.valueOf(i), gson.toJson(handshakes.get(i)));
            }
            return ListenableWorker.Result.success(dataBuilder.build());
        }
        return ListenableWorker.Result.failure(new Data.Builder().putInt(DataKeys.ERROR_CODE.toString(), -1).build());
    }

    /**
     * Returns the greater of two integers. If equal the first one will eb returned.
     *
     * @param a first int
     * @param b second int
     * @return which ever int is greater or the first one if they are equal
     */
    private Integer pickGreater(Integer a, Integer b) {
        if (a > b) {
            return a;
        } else if (a < b) {
            return b;
        } else {
            return a;
        }
    }

    private Pair<Integer, ArrayList<Handshake>> fetchHandshakesSender(String identityId, GatewayInfo gatewayInfo) {
        try {
            URL gatewayMetaUrl = new URL(gatewayInfo.toString() + "/handshakes/sender/" + identityId);
            HttpURLConnection connection = (HttpURLConnection) gatewayMetaUrl.openConnection();
            try (InputStream io = connection.getInputStream()) {
                return new Pair<>(ERROR_NONE, gson.fromJson(new JsonReader(new InputStreamReader(io)), getHandshakeListType()));
            } catch (JsonSyntaxException | JsonIOException e) {
                Log.e(TAG, "Unable to parse gateway response!", e);
                return new Pair<>(ERROR_PARSING, new ArrayList<>());
            } finally {
                connection.disconnect();
            }
        } catch (MalformedURLException e) {
            Log.e(TAG, "Unable to parse uri!", e);
            return new Pair<>(ERROR_URI, new ArrayList<>());
        } catch (FileNotFoundException e) {
            Log.i(TAG, "No handshakes found for: '" + identityId + "'!");
            return new Pair<>(ERROR_NOT_FOUND, new ArrayList<>());
        } catch (IOException e) {
            Log.e(TAG, "Unable to open connection!", e);
            return new Pair<>(ERROR_CONNECTION, new ArrayList<>());
        }
    }

    private Pair<Integer, ArrayList<Handshake>> fetchHandshakesReceiver(String identityId, GatewayInfo gatewayInfo) {
        try {
            URL gatewayMetaUrl = new URL(gatewayInfo.toString() + "/handshakes/receiver/" + identityId);
            HttpURLConnection connection = (HttpURLConnection) gatewayMetaUrl.openConnection();
            try (InputStream io = connection.getInputStream()) {
                return new Pair<>(ERROR_NONE, gson.fromJson(new JsonReader(new InputStreamReader(io)), getHandshakeListType()));
            } catch (JsonSyntaxException | JsonIOException e) {
                Log.e(TAG, "Unable to parse gateway response!", e);
                return new Pair<>(ERROR_PARSING, new ArrayList<>());
            } finally {
                connection.disconnect();
            }
        } catch (MalformedURLException e) {
            Log.e(TAG, "Unable to parse uri!", e);
            return new Pair<>(ERROR_URI, new ArrayList<>());
        } catch (FileNotFoundException e) {
            Log.i(TAG, "No handshakes found for: '" + identityId + "'!");
            return new Pair<>(ERROR_NOT_FOUND, new ArrayList<>());
        } catch (IOException e) {
            Log.e(TAG, "Unable to open connection!", e);
            return new Pair<>(ERROR_CONNECTION, new ArrayList<>());
        }
    }

    private Type getHandshakeListType() {
        //@formatter:off
        return new TypeToken<ArrayList<Handshake>>() {}.getType();
        //@formatter:on
    }
}
