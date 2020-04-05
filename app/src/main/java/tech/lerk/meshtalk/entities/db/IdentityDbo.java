package tech.lerk.meshtalk.entities.db;

import androidx.annotation.NonNull;
import androidx.room.ColumnInfo;
import androidx.room.Entity;
import androidx.room.Ignore;
import androidx.room.PrimaryKey;

import java.util.UUID;

@Entity(tableName = "identity")
public class IdentityDbo {
    @NonNull
    @PrimaryKey
    @ColumnInfo(name = "id")
    private UUID id;

    @ColumnInfo(name = "encrypted")
    private String encrypted;

    public IdentityDbo(UUID id, String encrypted) {
        this.id = id;
        this.encrypted = encrypted;
    }

    @Ignore
    public IdentityDbo() {
    }

    public UUID getId() {
        return id;
    }

    public void setId(UUID id) {
        this.id = id;
    }

    public String getEncrypted() {
        return encrypted;
    }

    public void setEncrypted(String encrypted) {
        this.encrypted = encrypted;
    }
}
