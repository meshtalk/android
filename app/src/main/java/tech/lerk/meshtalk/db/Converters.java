package tech.lerk.meshtalk.db;

import androidx.room.TypeConverter;

import com.google.gson.reflect.TypeToken;

import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.Set;
import java.util.UUID;

import tech.lerk.meshtalk.Utils;
import tech.lerk.meshtalk.entities.Handshake;

public class Converters {
    @TypeConverter
    public static UUID uuid(String uuid) {
        return UUID.fromString(uuid);
    }

    @TypeConverter
    public static String uuid(UUID uuid) {
        return uuid.toString();
    }

    @TypeConverter
    public static Set<UUID> uuidSet(String uuidSet) {
        return Utils.getGson().fromJson(uuidSet, new TypeToken<Set<UUID>>() {
        }.getType());
    }

    @TypeConverter
    public static String uuidSet(Set<UUID> uuidSet) {
        return Utils.getGson().toJson(uuidSet);
    }

    @TypeConverter
    public static HashMap<UUID, Handshake> handshakeIdMap(String handshakeIdMap) {
        return Utils.getGson().fromJson(handshakeIdMap, new TypeToken<HashMap<UUID, Handshake>>() {
        }.getType());
    }

    @TypeConverter
    public static String handshakeIdMap(HashMap<UUID, Handshake> handshakeIdMap) {
        return Utils.getGson().toJson(handshakeIdMap);
    }

    @TypeConverter
    public static String localDateTime(LocalDateTime localDateTime) {
        return Utils.getGson().toJson(localDateTime);
    }

    @TypeConverter
    public static LocalDateTime localDateTime(String localDateTime) {
        return Utils.getGson().fromJson(localDateTime, LocalDateTime.class);
    }
}
