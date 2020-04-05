package tech.lerk.meshtalk.db.dao;

import androidx.room.Dao;
import androidx.room.Delete;
import androidx.room.Insert;
import androidx.room.OnConflictStrategy;
import androidx.room.Query;
import androidx.room.Update;

import java.util.List;
import java.util.UUID;

import tech.lerk.meshtalk.entities.db.HandshakeDbo;

@Dao
public interface HandshakeDao {
    @Query("select * from handshake")
    List<HandshakeDbo> getHandshakes();

    @Query("select * from handshake where id = :id")
    HandshakeDbo getHandshakeById(UUID id);

    @Query("select * from handshake where sender = :senderId")
    HandshakeDbo getHandshakeBySender(UUID senderId);

    @Query("select * from handshake where receiver = :receiverId")
    HandshakeDbo getHandshakeByReceiver(UUID receiverId);

    @Query("select * from handshake where chat = :chatId")
    HandshakeDbo getHandshakeByChat(UUID chatId);

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    void insertHandshake(HandshakeDbo handshake);

    @Update
    void updateHandshake(HandshakeDbo handshake);

    @Delete
    void deleteHandshake(HandshakeDbo handshake);

    @Query("delete from handshake where 1 = 1")
    void deleteAll();
}
