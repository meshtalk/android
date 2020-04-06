package tech.lerk.meshtalk.db.dao;

import androidx.room.Dao;
import androidx.room.Delete;
import androidx.room.Insert;
import androidx.room.OnConflictStrategy;
import androidx.room.Query;
import androidx.room.Update;

import java.util.List;
import java.util.UUID;

import tech.lerk.meshtalk.db.entities.IdentityDbo;

@Dao
public interface IdentityDao {
    @Query("select * from identity")
    List<IdentityDbo> getIdentities();

    @Query("select * from identity where id = :id")
    IdentityDbo getIdentityById(UUID id);

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    void insertIdentity(IdentityDbo identity);

    @Update
    void updateIdentity(IdentityDbo identity);

    @Delete
    void deleteIdentity(IdentityDbo identity);

    @Query("delete from identity where id = :id")
    void deleteIdentityById(UUID id);

    @Query("delete from identity where 1 = 1")
    void deleteAll();
}
