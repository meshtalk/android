package tech.lerk.meshtalk.adapters;

import android.util.Base64;
import android.util.Log;

import com.google.gson.JsonDeserializationContext;
import com.google.gson.JsonDeserializer;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParseException;
import com.google.gson.JsonSerializationContext;
import com.google.gson.JsonSerializer;

import java.lang.reflect.Type;
import java.security.KeyFactory;
import java.security.NoSuchAlgorithmException;
import java.security.PrivateKey;
import java.security.spec.InvalidKeySpecException;
import java.security.spec.PKCS8EncodedKeySpec;

public class PrivateKeyTypeAdapter implements JsonSerializer<PrivateKey>, JsonDeserializer<PrivateKey> {
    private static final String DATA = "DATA";
    private static final String TAG = PrivateKeyTypeAdapter.class.getCanonicalName();

    public PrivateKey deserialize(JsonElement jsonElement, Type type, JsonDeserializationContext jsonDeserializationContext) throws JsonParseException {
        JsonObject jsonObject = jsonElement.getAsJsonObject();
        String deserialized = jsonDeserializationContext.deserialize(jsonObject.get(DATA), String.class);
        byte[] decodedKey = Base64.decode(deserialized, Base64.DEFAULT);
        try {
            KeyFactory kf = KeyFactory.getInstance("RSA");
            return kf.generatePrivate(new PKCS8EncodedKeySpec(decodedKey));
        } catch (NoSuchAlgorithmException | InvalidKeySpecException e) {
            String msg = "Unable to decode key!";
            Log.e(TAG, msg, e);
            throw new JsonParseException(msg, e);
        }
    }

    public JsonElement serialize(PrivateKey privateKey, Type type, JsonSerializationContext jsonSerializationContext) {
        JsonObject jsonObject = new JsonObject();
        String encodedKey = Base64.encodeToString(privateKey.getEncoded(), Base64.DEFAULT);
        jsonObject.add(DATA, jsonSerializationContext.serialize(encodedKey));
        return jsonObject;
    }
}

