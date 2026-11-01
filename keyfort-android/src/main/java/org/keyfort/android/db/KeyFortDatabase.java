package org.keyfort.android.db;

import android.content.Context;

import androidx.room.Database;
import androidx.room.Room;
import androidx.room.RoomDatabase;

@Database(
        entities = {CachedVaultEntity.class, CachedEntryEntity.class},
        version = 1,
        exportSchema = false
)
public abstract class KeyFortDatabase extends RoomDatabase {

    private static volatile KeyFortDatabase instance;

    public abstract VaultDao vaultDao();

    public static KeyFortDatabase getInstance(Context context) {
        if (instance == null) {
            synchronized (KeyFortDatabase.class) {
                if (instance == null) {
                    instance = Room.databaseBuilder(
                                    context.getApplicationContext(),
                                    KeyFortDatabase.class,
                                    "keyfort_db"
                            )
                            .fallbackToDestructiveMigration()
                            .build();
                }
            }
        }
        return instance;
    }
}
