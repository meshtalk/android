package tech.lerk.meshtalk.db.dao;

import androidx.room.Dao;
import androidx.room.Delete;
import androidx.room.Insert;
import androidx.room.OnConflictStrategy;
import androidx.room.Query;
import androidx.room.Update;

import java.util.List;
import java.util.UUID;

import tech.lerk.meshtalk.db.entities.ChatDbo;

@Dao
public interface ChatDao {
    @Query("select * from chat")
    List<ChatDbo> getChats();

    @Query("select * from chat where id = :id")
    ChatDbo getChatById(UUID id);

    @Query("select * from chat where sender = :senderId")
    List<ChatDbo> getChatsBySender(UUID senderId);

    @Query("select * from chat where recipient = :recipientId")
    List<ChatDbo> getChatsByRecipient(UUID recipientId);

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    void insertChat(ChatDbo chat);

    @Update
    void updateChat(ChatDbo chat);

    @Delete
    void deleteChat(ChatDbo chat);

    @Query("delete from chat where id = :id")
    void deleteChatById(UUID id);

    @Query("delete from chat where 1 = 1")
    void deleteAll();
}
