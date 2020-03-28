package tech.lerk.meshtalk;

import android.content.Context;
import android.util.Log;

import androidx.annotation.Nullable;

import java.io.ByteArrayOutputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.interfaces.RSAPublicKey;
import java.util.Base64;
import java.util.UUID;

import tech.lerk.meshtalk.entities.Contact;
import tech.lerk.meshtalk.entities.Identity;
import tech.lerk.meshtalk.entities.ui.UserDO;
import tech.lerk.meshtalk.exceptions.DecryptionException;
import tech.lerk.meshtalk.providers.ContactProvider;
import tech.lerk.meshtalk.providers.IdentityProvider;

public class Stuff {
    private static final String TAG = Stuff.class.getCanonicalName();

    public static final String KEY_STORE_TYPE = "AndroidKeyStore";
    public static final String AES_MODE = "AES/GCM/NoPadding";
    public static final String APP_KEY_ALIAS = "MeshTalkKey";
    public static final String EMPTY_OBJECT = "{}";
    public static final String NONE = "NONE";
    public static final String HANDSHAKE_CONTENT = "Henlo!";

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

    public static UserDO getUserDO(UUID uuid, Context context) {
        ContactProvider contactProvider = ContactProvider.get(context);
        IdentityProvider identityProvider = IdentityProvider.get(context);
        try {
            Identity identityById = identityProvider.getById(uuid);
            if (identityById != null) {
                return new UserDO(identityById.getId().toString(), identityById.getName());
            }
        } catch (DecryptionException e) {
            Log.i(TAG, "Unable to decrypt identity, probably not found...", e);
        }
        Contact contactById = contactProvider.getById(uuid);
        if (contactById != null) {
            return new UserDO(contactById.getId().toString(), contactById.getName());
        }
        return null;
    }

    @Nullable
    public static UUID determineSelfId(UUID sender, UUID recipient, IdentityProvider identityProvider) {
       if(identityProvider.exists(sender)) {
           return sender;
       } else if (identityProvider.exists(recipient)) {
           return recipient;
       }
       return null;
    }

    @Nullable
    public static UUID determineOtherId(UUID sender, UUID recipient, IdentityProvider identityProvider) {
        if(identityProvider.exists(sender)) {
            return recipient;
        } else if (identityProvider.exists(recipient)) {
            return sender;
        }
        return null;
    }
}
