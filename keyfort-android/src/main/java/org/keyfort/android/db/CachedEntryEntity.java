package org.keyfort.android.db;

import androidx.room.Entity;
import androidx.room.PrimaryKey;

import androidx.annotation.NonNull;

@Entity(tableName = "cached_entry")
public class CachedEntryEntity {

    @PrimaryKey
    @NonNull
    public String id = "";

    public String userId;
    public String site;
    public String username;
    public String sealedPasswordBase64;
    public int keyVersion;
}
