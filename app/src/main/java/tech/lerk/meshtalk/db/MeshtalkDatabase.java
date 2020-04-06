package tech.lerk.meshtalk.db;

import android.content.Context;

import androidx.room.Database;
import androidx.room.Room;
import androidx.room.RoomDatabase;
import androidx.room.TypeConverters;

import tech.lerk.meshtalk.db.dao.ChatDao;
import tech.lerk.meshtalk.db.dao.ContactDao;
import tech.lerk.meshtalk.db.dao.HandshakeDao;
import tech.lerk.meshtalk.db.dao.IdentityDao;
import tech.lerk.meshtalk.db.dao.MessageDao;
import tech.lerk.meshtalk.db.entities.ChatDbo;
import tech.lerk.meshtalk.db.entities.ContactDbo;
import tech.lerk.meshtalk.db.entities.HandshakeDbo;
import tech.lerk.meshtalk.db.entities.IdentityDbo;
import tech.lerk.meshtalk.db.entities.MessageDbo;

@Database(version = 2, entities = {ChatDbo.class, MessageDbo.class, IdentityDbo.class, HandshakeDbo.class, ContactDbo.class})
@TypeConverters({Converters.class})
public abstract class MeshtalkDatabase extends RoomDatabase {
    private static final String DB_NAME = "meshtalk_db";
    private static MeshtalkDatabase instance;

    public static synchronized MeshtalkDatabase get(Context context) {
        if (instance == null) {
            instance = Room.databaseBuilder(context.getApplicationContext(), MeshtalkDatabase.class, DB_NAME)
                    .fallbackToDestructiveMigration()
                    .build();
        }
        return instance;
    }

    public abstract ChatDao chatDao();

    public abstract ContactDao contactDao();

    public abstract HandshakeDao handshakeDao();

    public abstract IdentityDao identityDao();

    public abstract MessageDao messageDao();
}
