package tech.lerk.meshtalk;

import android.content.Context;
import android.util.Log;

import java.io.ByteArrayOutputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.interfaces.RSAPublicKey;
import java.util.Base64;
import java.util.UUID;

import tech.lerk.meshtalk.entities.ui.UserDO;
import tech.lerk.meshtalk.exceptions.DecryptionException;
import tech.lerk.meshtalk.providers.Provider;
import tech.lerk.meshtalk.providers.impl.ContactProvider;
import tech.lerk.meshtalk.providers.impl.IdentityProvider;

public class Stuff {
    private static final String TAG = Stuff.class.getCanonicalName();

    public static final String KEY_STORE_TYPE = "AndroidKeyStore";
    public static final String AES_MODE = "AES/GCM/NoPadding";
    public static final String APP_KEY_ALIAS = "MeshTalkKey";
    public static final String NONE = "NONE";

    public static void waitOrDonT(long millis) {
        try {
            Thread.sleep(millis);
        } catch (InterruptedException e) {
            Log.w(TAG, "Couldn't wait...", e);
        }
    }

    public static String getFingerprintForKey(RSAPublicKey publicKey) {
        try {
            byte[] n = publicKey.getModulus().toByteArray(); // Java is 2sC bigendian
            byte[] e = publicKey.getPublicExponent().toByteArray(); // and so is SSH
            ByteArrayOutputStream os = new ByteArrayOutputStream();
            DataOutputStream dout = new DataOutputStream(os);
            dout.writeInt(e.length);
            dout.write(e);
            dout.writeInt(n.length);
            dout.write(n);
            byte[] encoded = os.toByteArray();
            MessageDigest digest = MessageDigest.getInstance("SHA256");
            byte[] result = digest.digest(encoded);
            return Base64.getMimeEncoder().encodeToString(result);
        } catch (NoSuchAlgorithmException | IOException e) {
            Log.e(TAG, "Unable to get fingerprint for key!", e);
            return "ERROR!";
        }
    }

    public static byte[] getReducedIV(byte[] deviceIV) {
        byte[] reducedIV = new byte[12];
        System.arraycopy(deviceIV, 0, reducedIV, 0, reducedIV.length);
        return reducedIV;
    }

    public static void getUserDO(UUID uuid, Context context, Provider.LookupCallback<UserDO> callback) {
        ContactProvider contactProvider = ContactProvider.get(context);
        IdentityProvider identityProvider = IdentityProvider.get(context);
        try {
            identityProvider.getById(uuid, i -> {
                if (i != null) {
                    callback.call(new UserDO(i.getId().toString(), i.getName()));
                } else {
                    contactProvider.getById(uuid, c -> {
                        if (c != null) {
                            callback.call(new UserDO(c.getId().toString(), c.getName()));
                        } else {
                            callback.call(null);
                        }
                    });
                }
            });
        } catch (DecryptionException e) {
            Log.i(TAG, "Unable to decrypt identity, probably not found...", e);
        }
    }

    public static void determineSelfId(UUID sender, UUID recipient, IdentityProvider identityProvider, Provider.LookupCallback<UUID> callback) {
        identityProvider.exists(sender, e -> {
            if (e) {
                callback.call(sender);
            } else {
                identityProvider.exists(recipient, e1 -> {
                    if (e1) {
                        callback.call(recipient);
                    } else {
                        callback.call(null);
                    }
                });
            }
        });
    }

    public static void determineOtherId(UUID sender, UUID recipient, IdentityProvider identityProvider, Provider.LookupCallback<UUID> callback) {
        identityProvider.exists(sender, e -> {
            if (e) {
                callback.call(recipient);
            } else {
                identityProvider.exists(recipient, e1 -> {
                    if (e1) {
                        callback.call(sender);
                    } else {
                        callback.call(null);
                    }
                });
            }
        });
    }
}
