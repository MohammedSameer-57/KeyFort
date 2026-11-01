package org.keyfort.android.network;

import java.io.IOException;
import java.util.concurrent.TimeUnit;

import okhttp3.MediaType;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.RequestBody;
import okhttp3.Response;

public class ApiClient {

    private static final MediaType JSON = MediaType.get("application/json; charset=utf-8");
    private static ApiClient instance;

    private String baseUrl = "http://10.0.2.2:8080";
    private final OkHttpClient client;

    private ApiClient() {
        client = new OkHttpClient.Builder()
                .connectTimeout(15, TimeUnit.SECONDS)
                .readTimeout(30, TimeUnit.SECONDS)
                .writeTimeout(30, TimeUnit.SECONDS)
                .build();
    }

    public static synchronized ApiClient getInstance() {
        if (instance == null) {
            instance = new ApiClient();
        }
        return instance;
    }

    public void setBaseUrl(String baseUrl) {
        this.baseUrl = baseUrl;
    }

    public String getBaseUrl() {
        return baseUrl;
    }

    // ─── Auth ────────────────────────────────────────────────────────────────

    public String registerDevice(String userId, String deviceId, String deviceName) throws IOException {
        String body = String.format(
                "{\"userId\":\"%s\",\"deviceId\":\"%s\",\"deviceName\":\"%s\"}",
                escape(userId), escape(deviceId), escape(deviceName));
        return post(baseUrl + "/auth/device/register", null, body);
    }

    public String verifyDevice(String userId, String deviceId, String code) throws IOException {
        String body = String.format(
                "{\"userId\":\"%s\",\"deviceId\":\"%s\",\"verificationCode\":\"%s\"}",
                escape(userId), escape(deviceId), escape(code));
        return post(baseUrl + "/auth/device/verify", null, body);
    }

    // ─── Vault Sync ─────────────────────────────────────────────────────────

    public String getVaultSync(String token) throws IOException {
        Request request = new Request.Builder()
                .url(baseUrl + "/vault/sync")
                .addHeader("Authorization", "Bearer " + token)
                .get()
                .build();
        return execute(request);
    }

    public String putVaultSync(String token, String jsonBody) throws IOException {
        Request request = new Request.Builder()
                .url(baseUrl + "/vault/sync")
                .addHeader("Authorization", "Bearer " + token)
                .put(RequestBody.create(jsonBody, JSON))
                .build();
        return execute(request);
    }

    public String postRotationAck(String token, String deviceId, int newKeyVersion) throws IOException {
        String body = String.format(
                "{\"deviceId\":\"%s\",\"newKeyVersion\":%d}",
                escape(deviceId), newKeyVersion);
        return post(baseUrl + "/vault/rotation/ack", token, body);
    }

    // ─── Access Events ──────────────────────────────────────────────────────

    public String postAccessEvent(String token, String userId, String deviceId,
                                   String ip, String geo, boolean success) throws IOException {
        String body = String.format(
                "{\"userId\":\"%s\",\"deviceId\":\"%s\",\"ip\":\"%s\",\"geo\":\"%s\",\"success\":%b}",
                escape(userId), escape(deviceId), escape(ip), escape(geo), success);
        return post(baseUrl + "/access-events", token, body);
    }

    // ─── Devices ────────────────────────────────────────────────────────────

    public String getDevices(String token) throws IOException {
        Request request = new Request.Builder()
                .url(baseUrl + "/devices")
                .addHeader("Authorization", "Bearer " + token)
                .get()
                .build();
        return execute(request);
    }

    public String revokeDevice(String token, String deviceId) throws IOException {
        Request request = new Request.Builder()
                .url(baseUrl + "/devices/" + deviceId + "/revoke")
                .addHeader("Authorization", "Bearer " + token)
                .post(RequestBody.create("", JSON))
                .build();
        return execute(request);
    }

    // ─── Ledger ─────────────────────────────────────────────────────────────

    public String verifyLedger() throws IOException {
        Request request = new Request.Builder()
                .url(baseUrl + "/ledger/verify")
                .get()
                .build();
        return execute(request);
    }

    // ─── Internal helpers ────────────────────────────────────────────────────

    private String post(String url, String token, String jsonBody) throws IOException {
        Request.Builder builder = new Request.Builder()
                .url(url)
                .post(RequestBody.create(jsonBody, JSON));
        if (token != null && !token.isEmpty()) {
            builder.addHeader("Authorization", "Bearer " + token);
        }
        return execute(builder.build());
    }

    private String execute(Request request) throws IOException {
        try (Response response = client.newCall(request).execute()) {
            String responseBody = response.body() != null ? response.body().string() : "";
            if (!response.isSuccessful()) {
                throw new IOException("HTTP " + response.code() + ": " + responseBody);
            }
            return responseBody;
        }
    }

    /** Minimal JSON string escaping. */
    private static String escape(String s) {
        if (s == null) return "";
        return s.replace("\\", "\\\\").replace("\"", "\\\"");
    }
}
