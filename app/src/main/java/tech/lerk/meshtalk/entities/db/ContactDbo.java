package tech.lerk.meshtalk.entities.db;

import androidx.annotation.NonNull;
import androidx.room.ColumnInfo;
import androidx.room.Entity;
import androidx.room.Ignore;
import androidx.room.PrimaryKey;

import java.security.PublicKey;
import java.util.UUID;

import tech.lerk.meshtalk.Utils;

@Entity(tableName = "contact")
public class ContactDbo {
    @NonNull
    @PrimaryKey
    @ColumnInfo(name = "id")
    private UUID id;

    @ColumnInfo(name = "name")
    private String name;

    @ColumnInfo(name = "public_key")
    private String publicKey;

    public ContactDbo(UUID id, String name, String publicKey) {
        this.id = id;
        this.name = name;
        this.publicKey = publicKey;
    }

    @Ignore
    public ContactDbo() {
    }

    public UUID getId() {
        return id;
    }

    public void setId(UUID id) {
        this.id = id;
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public String getPublicKey() {
        return publicKey;
    }

    public void setPublicKey(String publicKey) {
        this.publicKey = publicKey;
    }

    @Ignore
    public PublicKey getPublicKeyObj() {
        return Utils.getGson().fromJson(publicKey, PublicKey.class);
    }

    @Ignore
    public void setPublicKeyObj(PublicKey publicKey) {
        this.publicKey = Utils.getGson().toJson(publicKey);
    }
}
