package tech.lerk.meshtalk.db.dao;

import androidx.room.Dao;
import androidx.room.Delete;
import androidx.room.Insert;
import androidx.room.OnConflictStrategy;
import androidx.room.Query;
import androidx.room.Update;

import java.util.List;
import java.util.UUID;

import tech.lerk.meshtalk.entities.db.MessageDbo;

@Dao
public interface MessageDao {
    @Query("select * from message")
    List<MessageDbo> getMessages();

    @Query("select * from message where id = :id")
    MessageDbo getMessageById(UUID id);

    @Query("select * from message where sender = :senderId")
    MessageDbo getMessageBySender(UUID senderId);

    @Query("select * from message where receiver = :receiverId")
    MessageDbo getMessageByReceiver(UUID receiverId);

    @Query("select * from message where chat = :chatId")
    MessageDbo getMessageByChat(UUID chatId);

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    void insertMessage(MessageDbo message);

    @Update
    void updateMessage(MessageDbo message);

    @Delete
    void deleteMessage(MessageDbo message);

    @Query("delete from message where id = :id")
    void deleteMessageById(UUID id);
}
