package org.keyfort.android.repository;

import android.content.Context;
import android.util.Base64;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

import org.keyfort.android.AppSession;
import org.keyfort.android.db.CachedEntryEntity;
import org.keyfort.android.db.CachedVaultEntity;
import org.keyfort.android.db.KeyFortDatabase;
import org.keyfort.android.db.VaultDao;
import org.keyfort.android.network.ApiClient;
import org.keyfort.core.Entry;
import org.keyfort.core.Vault;

import java.io.IOException;
import java.security.GeneralSecurityException;
import java.security.KeyFactory;
import java.security.PublicKey;
import java.security.spec.X509EncodedKeySpec;
import java.util.LinkedHashMap;
import java.util.Map;

public class VaultRepository {

    private final ApiClient apiClient;
    private final VaultDao vaultDao;

    public VaultRepository(Context context) {
        this.apiClient = ApiClient.getInstance();
        this.vaultDao = KeyFortDatabase.getInstance(context).vaultDao();
    }

    /**
     * Serializes vault state to JSON and PUT to server.
     */
    public void syncToServer(AppSession appSession) throws IOException {
        Vault vault = appSession.getVault();
        if (vault == null) throw new IllegalStateException("No vault in session");

        String userId = appSession.getUserId();
        String token = appSession.getJwtToken();

        StringBuilder entriesJson = new StringBuilder("[");
        boolean first = true;
        for (Map.Entry<String, Entry> me : vault.entries().entrySet()) {
            Entry e = me.getValue();
            if (!first) entriesJson.append(",");
            first = false;
            String sealedB64 = Base64.encodeToString(e.sealedPassword(), Base64.NO_WRAP);
            entriesJson.append(String.format(
                    "{\"id\":\"%s\",\"site\":\"%s\",\"username\":\"%s\"," +
                    "\"sealedPasswordBase64\":\"%s\",\"keyVersion\":%d}",
                    escape(e.id()), escape(e.site()), escape(e.username()),
                    sealedB64, e.keyVersion()));
        }
        entriesJson.append("]");

        String recoveryPubB64 = Base64.encodeToString(vault.recoveryPub().getEncoded(), Base64.NO_WRAP);
        String kdfSaltB64 = Base64.encodeToString(vault.kdfSalt(), Base64.NO_WRAP);
        String wrappedDekB64 = Base64.encodeToString(vault.wrappedDek(), Base64.NO_WRAP);
        String recoveryWrappedDekB64 = Base64.encodeToString(vault.recoveryWrappedDek(), Base64.NO_WRAP);

        String body = String.format(
                "{\"userId\":\"%s\",\"iterations\":%d," +
                "\"recoveryPubBase64\":\"%s\",\"kdfSaltBase64\":\"%s\"," +
                "\"wrappedDekBase64\":\"%s\",\"recoveryWrappedDekBase64\":\"%s\"," +
                "\"keyVersion\":%d,\"rotationPending\":%b,\"entries\":%s}",
                escape(userId),
                vault.iterations(),
                recoveryPubB64, kdfSaltB64,
                wrappedDekB64, recoveryWrappedDekB64,
                vault.keyVersion(), vault.rotationPending(),
                entriesJson.toString());

        apiClient.putVaultSync(token, body);

        // Also update local Room cache
        cacheVaultLocally(vault, userId);
    }

    /**
     * GET vault from server, reconstruct Vault via Vault.restore().
     * Does NOT unlock — caller must call vault.unlock(masterSecret) afterward.
     */
    public Vault syncFromServer(String token, String userId)
            throws IOException, GeneralSecurityException {
        String json = apiClient.getVaultSync(token);
        return parseVaultResponse(json, userId);
    }

    public void acknowledgeRotation(String token, String deviceId, int newKeyVersion)
            throws IOException {
        apiClient.postRotationAck(token, deviceId, newKeyVersion);
    }

    // ─── Private helpers ─────────────────────────────────────────────────────

    private Vault parseVaultResponse(String json, String userId)
            throws GeneralSecurityException {
        JsonObject obj = JsonParser.parseString(json).getAsJsonObject();

        int iterations = obj.get("iterations").getAsInt();
        int keyVersion = obj.get("keyVersion").getAsInt();
        boolean rotationPending = obj.has("rotationPending") && obj.get("rotationPending").getAsBoolean();

        byte[] recoveryPubBytes = Base64.decode(obj.get("recoveryPubBase64").getAsString(), Base64.NO_WRAP);
        byte[] kdfSalt = Base64.decode(obj.get("kdfSaltBase64").getAsString(), Base64.NO_WRAP);
        byte[] wrappedDek = Base64.decode(obj.get("wrappedDekBase64").getAsString(), Base64.NO_WRAP);
        byte[] recoveryWrappedDek = Base64.decode(obj.get("recoveryWrappedDekBase64").getAsString(), Base64.NO_WRAP);

        PublicKey recoveryPub = KeyFactory.getInstance("RSA")
                .generatePublic(new X509EncodedKeySpec(recoveryPubBytes));

        Map<String, Entry> entries = new LinkedHashMap<>();
        if (obj.has("entries") && !obj.get("entries").isJsonNull()) {
            JsonArray arr = obj.getAsJsonArray("entries");
            for (JsonElement el : arr) {
                JsonObject entryObj = el.getAsJsonObject();
                String id = entryObj.get("id").getAsString();
                String site = entryObj.get("site").getAsString();
                String username = entryObj.get("username").getAsString();
                byte[] sealedPassword = Base64.decode(
                        entryObj.get("sealedPasswordBase64").getAsString(), Base64.NO_WRAP);
                int ev = entryObj.get("keyVersion").getAsInt();
                entries.put(id, new Entry(id, site, username, sealedPassword, ev));
            }
        }

        return Vault.restore(userId, iterations, recoveryPub,
                kdfSalt, wrappedDek, recoveryWrappedDek,
                keyVersion, rotationPending, entries);
    }

    private void cacheVaultLocally(Vault vault, String userId) {
        CachedVaultEntity entity = new CachedVaultEntity();
        entity.userId = userId;
        entity.recoveryPubBase64 = Base64.encodeToString(vault.recoveryPub().getEncoded(), Base64.NO_WRAP);
        entity.kdfSaltBase64 = Base64.encodeToString(vault.kdfSalt(), Base64.NO_WRAP);
        entity.wrappedDekBase64 = Base64.encodeToString(vault.wrappedDek(), Base64.NO_WRAP);
        entity.recoveryWrappedDekBase64 = Base64.encodeToString(vault.recoveryWrappedDek(), Base64.NO_WRAP);
        entity.iterations = vault.iterations();
        entity.keyVersion = vault.keyVersion();
        entity.rotationPending = vault.rotationPending();
        entity.lastSyncedAt = System.currentTimeMillis();
        vaultDao.upsertVault(entity);

        // Cache entries
        vaultDao.deleteEntries(userId);
        for (Map.Entry<String, Entry> me : vault.entries().entrySet()) {
            Entry e = me.getValue();
            CachedEntryEntity ce = new CachedEntryEntity();
            ce.id = e.id();
            ce.userId = userId;
            ce.site = e.site();
            ce.username = e.username();
            ce.sealedPasswordBase64 = Base64.encodeToString(e.sealedPassword(), Base64.NO_WRAP);
            ce.keyVersion = e.keyVersion();
            vaultDao.upsertEntry(ce);
        }
    }

    private static String escape(String s) {
        if (s == null) return "";
        return s.replace("\\", "\\\\").replace("\"", "\\\"");
    }
}
