package tech.lerk.meshtalk.db.entities;

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

    public IdentityDbo(@NonNull UUID id, String encrypted) {
        this.id = id;
        this.encrypted = encrypted;
    }

    @NonNull
    public UUID getId() {
        return id;
    }

    public void setId(@NonNull UUID id) {
        this.id = id;
    }

    public String getEncrypted() {
        return encrypted;
    }

    public void setEncrypted(String encrypted) {
        this.encrypted = encrypted;
    }
}
