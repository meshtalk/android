package tech.lerk.meshtalk.db.dao;

import androidx.room.Dao;
import androidx.room.Delete;
import androidx.room.Insert;
import androidx.room.OnConflictStrategy;
import androidx.room.Query;
import androidx.room.Update;

import java.util.List;
import java.util.UUID;

import tech.lerk.meshtalk.entities.db.ContactDbo;

@Dao
public interface ContactDao {
    @Query("select * from contact")
    List<ContactDbo> getContacts();

    @Query("select * from contact where id = :id")
    ContactDbo getContactById(UUID id);

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    void insertContact(ContactDbo contact);

    @Update
    void updateContact(ContactDbo contact);

    @Delete
    void deleteContact(ContactDbo contact);

    @Query("delete from contact where id == :id")
    void deleteContactById(UUID id);
}
