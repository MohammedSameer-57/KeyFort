package org.keyfort.android.db;

import androidx.room.Entity;
import androidx.room.PrimaryKey;

import androidx.annotation.NonNull;

@Entity(tableName = "cached_vault")
public class CachedVaultEntity {

    @PrimaryKey
    @NonNull
    public String userId = "";

    public String recoveryPubBase64;
    public String kdfSaltBase64;
    public String wrappedDekBase64;
    public String recoveryWrappedDekBase64;
    public int iterations;
    public int keyVersion;
    public boolean rotationPending;
    public long lastSyncedAt;
}
