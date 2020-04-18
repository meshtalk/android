package tech.lerk.meshtalk;

import android.content.Intent;
import android.content.SharedPreferences;
import android.os.IBinder;
import android.util.Log;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.lifecycle.LifecycleService;
import androidx.preference.PreferenceManager;
import androidx.work.Constraints;
import androidx.work.Data;
import androidx.work.NetworkType;
import androidx.work.OneTimeWorkRequest;
import androidx.work.WorkManager;

import java.nio.charset.StandardCharsets;
import java.security.InvalidKeyException;
import java.security.NoSuchAlgorithmException;
import java.util.Base64;

import javax.crypto.BadPaddingException;
import javax.crypto.Cipher;
import javax.crypto.IllegalBlockSizeException;
import javax.crypto.NoSuchPaddingException;
import javax.crypto.SecretKey;
import javax.crypto.spec.SecretKeySpec;

import tech.lerk.meshtalk.entities.Handshake;
import tech.lerk.meshtalk.entities.Identity;
import tech.lerk.meshtalk.workers.DataKeys;
import tech.lerk.meshtalk.workers.FetchHandshakesWorker;
import tech.lerk.meshtalk.workers.FetchMessagesWorker;

public class MessageGatewayClientService extends LifecycleService {
    private static final String TAG = MessageGatewayClientService.class.getCanonicalName();

    private SharedPreferences preferences;

    private void startFetchMessageWorker() {
        OneTimeWorkRequest fetchMessagesRequest = new OneTimeWorkRequest
                .Builder(FetchMessagesWorker.class)
                .setConstraints(new Constraints.Builder().setRequiredNetworkType(NetworkType.CONNECTED).build())
                .build();
        WorkManager instance = WorkManager.getInstance(this);
        instance.enqueue(fetchMessagesRequest);
        instance.getWorkInfoByIdLiveData(fetchMessagesRequest.getId()).observe(this, info -> {
            if (info != null && info.getState().isFinished()) {
                Data data = info.getOutputData();
                int errorCode = data.getInt(DataKeys.ERROR_CODE.toString(), -1);
                switch (errorCode) {
                    case FetchMessagesWorker.ERROR_INVALID_SETTINGS:
                    case FetchMessagesWorker.ERROR_URI:
                    case FetchMessagesWorker.ERROR_CONNECTION:
                    case FetchMessagesWorker.ERROR_PARSING:
                    case FetchMessagesWorker.ERROR_NOT_FOUND:
                        String msg = "Got error " + errorCode + " while fetching messages!";
                        Log.w(TAG, msg);
                        if (preferences.getBoolean(Preferences.TOAST_FETCH_ERRORS.toString(), true)) {
                            Toast.makeText(getApplicationContext(), msg, Toast.LENGTH_LONG).show();
                        }
                        break;
                    case FetchMessagesWorker.ERROR_NONE:
                        Log.i(TAG, "Messages fetched successfully!");
                        break;
                    default:
                        Log.wtf(TAG, "Invalid error code: " + errorCode + "!");
                        break;
                }
            }
        });
    }

    private void startFetchHandshakeWorker() {
        OneTimeWorkRequest fetchHandshakesRequest = new OneTimeWorkRequest
                .Builder(FetchHandshakesWorker.class)
                .setConstraints(new Constraints.Builder().setRequiredNetworkType(NetworkType.CONNECTED).build())
                .build();
        WorkManager instance = WorkManager.getInstance(this);
        instance.enqueue(fetchHandshakesRequest);
        instance.getWorkInfoByIdLiveData(fetchHandshakesRequest.getId()).observe(this, info -> {
            if (info != null && info.getState().isFinished()) {
                Data data = info.getOutputData();
                int errorCode = data.getInt(DataKeys.ERROR_CODE.toString(), -1);
                switch (errorCode) {
                    case FetchHandshakesWorker.ERROR_INVALID_SETTINGS:
                    case FetchHandshakesWorker.ERROR_URI:
                    case FetchHandshakesWorker.ERROR_CONNECTION:
                    case FetchHandshakesWorker.ERROR_PARSING:
                        String msg = "Got error " + errorCode + " while fetching handshakes!";
                        Log.e(TAG, msg);
                        if (preferences.getBoolean(Preferences.TOAST_FETCH_ERRORS.toString(), true)) {
                            Toast.makeText(getApplicationContext(), msg, Toast.LENGTH_LONG).show();
                        }
                        break;
                    case FetchHandshakesWorker.ERROR_NOT_FOUND:
                    case FetchHandshakesWorker.ERROR_NONE:
                        Log.i(TAG, "Handshakes fetched successfully!");
                        break;
                    default:
                        Log.wtf(TAG, "Invalid error code: " + errorCode + "!");
                        break;
                }
            }
        });
    }

    @Nullable
    public static SecretKey getSecretKeyFromHandshake(Handshake handshake, Identity identity) {
        try {
            Cipher cipher = Cipher.getInstance("RSA");
            cipher.init(Cipher.DECRYPT_MODE, identity.getPrivateKey());
            byte[] decryptedKey = cipher.doFinal(Base64.getMimeDecoder().decode(handshake.getKey()));
            return new SecretKeySpec(decryptedKey, "AES");
        } catch (NoSuchAlgorithmException | InvalidKeyException |
                NoSuchPaddingException | BadPaddingException | IllegalBlockSizeException e) {
            Log.e(TAG, "Unable to decrypt handshake '" + handshake.getId() + "' with identity '" + identity.getId() + "'");
        }
        return null;
    }

    @Nullable
    public static byte[] getIvFromHandshake(Handshake handshake, Identity identity) {
        try {
            Cipher cipher = Cipher.getInstance("RSA");
            cipher.init(Cipher.DECRYPT_MODE, identity.getPrivateKey());
            byte[] decodedIv = Base64.getMimeDecoder().decode(handshake.getIv().getBytes(StandardCharsets.UTF_8));
            return cipher.doFinal(decodedIv);
        } catch (NoSuchAlgorithmException | InvalidKeyException |
                NoSuchPaddingException | BadPaddingException | IllegalBlockSizeException e) {
            Log.e(TAG, "Unable to decrypt handshake '" + handshake.getId() + "' with identity '" + identity.getId() + "'");
        }
        return null;
    }

    @Override
    public void onCreate() {
        super.onCreate();
        preferences = PreferenceManager.getDefaultSharedPreferences(getApplicationContext());
    }


    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        super.onStartCommand(intent, flags, startId);

        startFetchHandshakeWorker();
        startFetchMessageWorker();
        // If we get killed, after returning from here, restart
        return START_STICKY;
    }


    @Override
    public IBinder onBind(@NonNull Intent intent) {
        super.onBind(intent);
        // We don't provide binding, so return null
        return null;
    }

    @Override
    public void onDestroy() {
        super.onDestroy();
    }
}
