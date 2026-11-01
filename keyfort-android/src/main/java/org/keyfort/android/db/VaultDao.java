package org.keyfort.android.db;

import androidx.room.Dao;
import androidx.room.Insert;
import androidx.room.OnConflictStrategy;
import androidx.room.Query;

import java.util.List;

@Dao
public interface VaultDao {

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    void upsertVault(CachedVaultEntity vault);

    @Query("SELECT * FROM cached_vault WHERE userId = :userId LIMIT 1")
    CachedVaultEntity getVault(String userId);

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    void upsertEntry(CachedEntryEntity entry);

    @Query("SELECT * FROM cached_entry WHERE userId = :userId")
    List<CachedEntryEntity> getEntries(String userId);

    @Query("DELETE FROM cached_entry WHERE userId = :userId")
    void deleteEntries(String userId);
}
