package tech.lerk.meshtalk.db.dao;

import androidx.lifecycle.LiveData;
import androidx.room.Dao;
import androidx.room.Delete;
import androidx.room.Insert;
import androidx.room.OnConflictStrategy;
import androidx.room.Query;
import androidx.room.Update;

import java.util.List;
import java.util.UUID;

import tech.lerk.meshtalk.db.entities.MessageDbo;

@Dao
public interface MessageDao {
    @Query("select * from message")
    List<MessageDbo> getMessages();

    @Query("select * from message")
    LiveData<List<MessageDbo>> getLiveMessages();

    @Query("select * from message where id = :id")
    MessageDbo getMessageById(UUID id);

    @Query("select * from message where sender = :senderId")
    List<MessageDbo> getMessageBySender(UUID senderId);

    @Query("select * from message where sender = :senderId")
    LiveData<List<MessageDbo>> getLiveMessageBySender(UUID senderId);

    @Query("select * from message where receiver = :receiverId")
    List<MessageDbo> getMessagesByReceiver(UUID receiverId);

    @Query("select * from message where receiver = :receiverId")
    LiveData<List<MessageDbo>> getLiveMessagesByReceiver(UUID receiverId);

    @Query("select * from message where chat = :chatId")
    List<MessageDbo> getMessagesByChat(UUID chatId);

    @Query("select * from message where chat = :chatId")
    LiveData<List<MessageDbo>> getLiveMessagesByChat(UUID chatId);

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    void insertMessage(MessageDbo message);

    @Update
    void updateMessage(MessageDbo message);

    @Delete
    void deleteMessage(MessageDbo message);

    @Query("delete from message where id = :id")
    void deleteMessageById(UUID id);

    @Query("delete from message where 1 = 1")
    void deleteAll();

    @Query("delete from message where chat = :chatId")
    void deleteMessagesByChat(UUID chatId);
}
