package tech.lerk.meshtalk.db.dao;

import androidx.room.Dao;
import androidx.room.Delete;
import androidx.room.Insert;
import androidx.room.OnConflictStrategy;
import androidx.room.Query;
import androidx.room.Update;

import java.util.List;
import java.util.UUID;

import tech.lerk.meshtalk.entities.db.ChatDbo;

@Dao
public interface ChatDao {
    @Query("select * from chat")
    List<ChatDbo> getChats();

    @Query("select * from chat where id == :id")
    ChatDbo getChatById(UUID id);

    @Query("select * from chat where sender == :senderId")
    ChatDbo getChatBySender(UUID senderId);

    @Query("select * from chat where recipient == :recipientId")
    ChatDbo getChatByRecipient(UUID recipientId);

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    void insertChat(ChatDbo chat);

    @Update
    void updateChat(ChatDbo chat);

    @Delete
    void deleteChat(ChatDbo chat);

    @Query("delete from chat where id == :id")
    void deleteChatbyId(UUID id);
}
