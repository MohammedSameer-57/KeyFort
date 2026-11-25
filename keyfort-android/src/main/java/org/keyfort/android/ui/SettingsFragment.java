package org.keyfort.android.ui;

import android.content.Intent;
import android.content.SharedPreferences;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AlertDialog;
import androidx.fragment.app.Fragment;
import androidx.preference.PreferenceManager;

import com.google.android.material.textfield.TextInputEditText;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

import org.keyfort.android.AppSession;
import org.keyfort.android.KeyFortApp;
import org.keyfort.android.R;
import org.keyfort.android.network.ApiClient;
import org.keyfort.android.repository.VaultRepository;
import org.keyfort.android.security.BiometricHelper;
import org.keyfort.android.security.SecurePrefs;
import org.keyfort.core.Crypto;
import org.keyfort.core.Vault;

import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class SettingsFragment extends Fragment {

    private final ExecutorService executor = Executors.newSingleThreadExecutor();

    private TextView tvUser, tvDevice, tvKeyVersion, tvBioStatus, tvCurrentServerUrl;

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater,
                             @Nullable ViewGroup container,
                             @Nullable Bundle savedInstanceState) {
        return inflater.inflate(R.layout.fragment_settings, container, false);
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);

        tvUser = view.findViewById(R.id.tv_settings_user);
        tvDevice = view.findViewById(R.id.tv_settings_device);
        tvKeyVersion = view.findViewById(R.id.tv_settings_key_version);
        tvBioStatus = view.findViewById(R.id.tv_settings_bio_status);
        tvCurrentServerUrl = view.findViewById(R.id.tv_current_server_url);

        view.findViewById(R.id.row_rotate_key).setOnClickListener(v -> confirmRotateKey());
        view.findViewById(R.id.row_crypto_specs).setOnClickListener(v -> showCryptoSpecs());
        view.findViewById(R.id.btn_lock_now).setOnClickListener(v -> lockNow());

        view.findViewById(R.id.row_connected_devices).setOnClickListener(v -> showRegisteredDevices());
        view.findViewById(R.id.btn_verify_ledger).setOnClickListener(v -> verifyLedger());
        view.findViewById(R.id.btn_revoke_device).setOnClickListener(v -> revokeDevice());

        view.findViewById(R.id.btn_server_url).setOnClickListener(v -> showServerUrlDialog());
        view.findViewById(R.id.btn_sign_out).setOnClickListener(v -> signOut());

        refreshStatusCard();
    }

    @Override
    public void onResume() {
        super.onResume();
        refreshStatusCard();
    }

    private void refreshStatusCard() {
        if (!isAdded()) return;
        AppSession session = KeyFortApp.getInstance().getAppSession();
        SecurePrefs prefs = SecurePrefs.getInstance(requireContext());

        String userId = nonEmpty(session.getUserId(), prefs.getUserId(), "Unknown user");
        String deviceId = nonEmpty(session.getDeviceId(), prefs.getDeviceId(), "Unbound device");

        tvUser.setText(userId);
        tvDevice.setText("Device · " + deviceId);

        Vault vault = session.getVault();
        if (vault != null) {
            String ver = "Key v" + vault.keyVersion()
                    + (vault.rotationPending() ? " · Renewal Pending" : "");
            tvKeyVersion.setText(ver);
        } else {
            tvKeyVersion.setText("Locked");
        }

        boolean bioAvailable = BiometricHelper.isBiometricAvailable(requireContext());
        tvBioStatus.setText(bioAvailable ? R.string.biometric_ready : R.string.biometric_unavailable);
        tvCurrentServerUrl.setText(ApiClient.getInstance().getBaseUrl());
    }

    private String resolveToken() {
        AppSession session = KeyFortApp.getInstance().getAppSession();
        String token = session.getJwtToken();
        if (token == null || token.isEmpty()) {
            token = SecurePrefs.getInstance(requireContext()).getToken();
        }
        return token != null ? token : "";
    }

    private void confirmRotateKey() {
        AppSession session = KeyFortApp.getInstance().getAppSession();
        if (!session.isUnlocked()) {
            Toast.makeText(getContext(), R.string.vault_locked, Toast.LENGTH_SHORT).show();
            return;
        }
        new AlertDialog.Builder(requireContext())
                .setTitle(R.string.rotate_key_now)
                .setMessage(R.string.rotate_key_confirm)
                .setPositiveButton(R.string.rotate_key_now, (d, w) -> performKeyRotation())
                .setNegativeButton(R.string.cancel, null)
                .show();
    }

    private void performKeyRotation() {
        AppSession session = KeyFortApp.getInstance().getAppSession();
        if (!session.isUnlocked()) return;
        String token = resolveToken();
        if ((session.getJwtToken() == null || session.getJwtToken().isEmpty()) && !token.isEmpty()) {
            session.setJwtToken(token);
        }
        String deviceId = nonEmpty(session.getDeviceId(), SecurePrefs.getInstance(requireContext()).getDeviceId(), "android");

        executor.execute(() -> {
            try {
                session.getSession().rotateKeys();
                int newVersion = session.getVault().keyVersion();
                VaultRepository repo = new VaultRepository(requireContext());
                repo.syncToServer(session);
                repo.acknowledgeRotation(token, deviceId, newVersion);

                if (!isAdded()) return;
                requireActivity().runOnUiThread(() -> {
                    refreshStatusCard();
                    Toast.makeText(getContext(), getString(R.string.rotate_key_success, newVersion), Toast.LENGTH_LONG).show();
                });
            } catch (Exception e) {
                showErrorOnUi(e);
            }
        });
    }

    private void showCryptoSpecs() {
        executor.execute(() -> {
            final String finalMessage = buildCryptoSpecsMessage();
            if (!isAdded()) return;
            requireActivity().runOnUiThread(() ->
                    new AlertDialog.Builder(requireContext())
                            .setTitle(R.string.crypto_specs)
                            .setMessage(finalMessage)
                            .setPositiveButton(R.string.ok, null)
                            .show());
        });
    }

    private String buildCryptoSpecsMessage() {
        try {
            String json = ApiClient.getInstance().getCryptoFacts();
            JsonObject obj = JsonParser.parseString(json).getAsJsonObject();
            return "Source: Live KeyFort Server\n\n"
                    + "• Key Derivation: " + str(obj, "kdfAlgorithm") + " (" + obj.get("kdfIterations").getAsInt() + " rounds)\n"
                    + "• Vault Cipher: " + str(obj, "cipherAlgorithm") + "\n"
                    + "• Entry Subkeys: " + str(obj, "hkdfAlgorithm") + "\n"
                    + "• Recovery Wrap: " + str(obj, "recoveryKeyAlgorithm") + "\n"
                    + "• Token Entropy: " + obj.get("tokenSecretBits").getAsInt() + "-bit secret + " + obj.get("tokenIdBits").getAsInt() + "-bit ID\n"
                    + "• Token Storage: " + str(obj, "tokenHashAlgorithm") + "\n"
                    + "• Audit Ledger: " + str(obj, "ledgerHashAlgorithm") + " hash chain\n"
                    + "• Connector Auth: " + str(obj, "connectorHmacAlgorithm") + " (±" + obj.get("clockSkewAllowanceSeconds").getAsLong() + "s window)";
        } catch (Exception offline) {
            return "Source: Local keyfort-core Engine\n\n"
                    + "• Key Derivation: PBKDF2WithHmacSHA256 (" + Crypto.PBKDF2_ITERATIONS + " rounds)\n"
                    + "• Vault Cipher: AES-256-GCM (128-bit tag + AAD)\n"
                    + "• Entry Subkeys: HKDF-SHA256\n"
                    + "• Recovery Wrap: RSA-3072-OAEP\n"
                    + "• Audit Ledger: SHA-256 hash chain\n"
                    + "• Local Prefs: Android Keystore AES-256-GCM";
        }
    }

    private void showRegisteredDevices() {
        String token = resolveToken();
        String currentDevice = nonEmpty(KeyFortApp.getInstance().getAppSession().getDeviceId(),
                SecurePrefs.getInstance(requireContext()).getDeviceId(), "");

        executor.execute(() -> {
            try {
                String json = ApiClient.getInstance().getDevices(token);
                JsonArray arr = JsonParser.parseString(json).getAsJsonArray();
                StringBuilder sb = new StringBuilder();
                if (arr.isEmpty()) {
                    sb.append("No devices registered for this account.");
                } else {
                    for (JsonElement el : arr) {
                        JsonObject d = el.getAsJsonObject();
                        String id = str(d, "deviceId");
                        String name = str(d, "deviceName");
                        String state = str(d, "state");
                        sb.append("• ").append(name)
                                .append(id.equals(currentDevice) ? " (this phone)" : "")
                                .append("\n   ID: ").append(id)
                                .append(" · State: ").append(state)
                                .append("\n\n");
                    }
                }
                if (!isAdded()) return;
                requireActivity().runOnUiThread(() ->
                        new AlertDialog.Builder(requireContext())
                                .setTitle(R.string.connected_devices)
                                .setMessage(sb.toString().trim())
                                .setPositiveButton(R.string.ok, null)
                                .show());
            } catch (Exception e) {
                showErrorOnUi(e);
            }
        });
    }

    private void verifyLedger() {
        String token = resolveToken();
        executor.execute(() -> {
            try {
                String verifyJson = ApiClient.getInstance().verifyLedger(token);
                JsonObject v = JsonParser.parseString(verifyJson).getAsJsonObject();
                boolean valid = v.has("valid") && v.get("valid").getAsBoolean();
                long count = v.has("blockCount") ? v.get("blockCount").getAsLong() : 0L;
                String msg = str(v, "message");

                StringBuilder sb = new StringBuilder();
                sb.append(valid ? "STATUS: VERIFIED INTACT" : "STATUS: TAMPER DETECTED")
                        .append("\nBlocks: ").append(count)
                        .append("\n").append(msg);

                try {
                    String blocksJson = ApiClient.getInstance().getLedgerBlocks(token);
                    JsonArray arr = JsonParser.parseString(blocksJson).getAsJsonArray();
                    if (!arr.isEmpty()) {
                        sb.append("\n\nRecent Audit Events:\n");
                        int start = Math.max(0, arr.size() - 6);
                        for (int i = arr.size() - 1; i >= start; i--) {
                            JsonObject b = arr.get(i).getAsJsonObject();
                            long seq = b.get("seq").getAsLong();
                            String type = str(b, "type");
                            String hash = str(b, "hash");
                            String shortHash = hash.length() >= 8 ? hash.substring(0, 8) : hash;
                            sb.append("  #").append(seq).append(" · ").append(type)
                                    .append(" (").append(shortHash).append(")\n");
                        }
                    }
                } catch (Exception ignored) {}

                if (!isAdded()) return;
                requireActivity().runOnUiThread(() ->
                        new AlertDialog.Builder(requireContext())
                                .setTitle(R.string.ledger_verification)
                                .setMessage(sb.toString().trim())
                                .setPositiveButton(R.string.ok, null)
                                .show());
            } catch (Exception e) {
                showErrorOnUi(e);
            }
        });
    }

    private void showServerUrlDialog() {
        View dialogView = LayoutInflater.from(getContext()).inflate(R.layout.dialog_server_url, null);
        TextInputEditText etUrl = dialogView.findViewById(R.id.et_server_url);
        etUrl.setText(ApiClient.getInstance().getBaseUrl());

        AlertDialog dialog = new AlertDialog.Builder(requireContext())
                .setTitle(R.string.server_url)
                .setView(dialogView)
                .setPositiveButton(R.string.save, (d, w) -> {
                    String url = etUrl.getText() != null ? etUrl.getText().toString().trim() : "";
                    if (!url.isEmpty()) {
                        String clean = url.endsWith("/") ? url.substring(0, url.length() - 1) : url;
                        ApiClient.getInstance().setBaseUrl(clean);
                        SharedPreferences prefs = PreferenceManager.getDefaultSharedPreferences(requireContext());
                        prefs.edit().putString("server_url", clean).apply();
                        refreshStatusCard();
                        Toast.makeText(getContext(), R.string.server_url_saved, Toast.LENGTH_SHORT).show();
                    }
                })
                .setNeutralButton(R.string.test_connection, null)
                .setNegativeButton(R.string.cancel, null)
                .create();

        dialog.show();
        dialog.getButton(AlertDialog.BUTTON_NEUTRAL).setOnClickListener(v -> {
            String url = etUrl.getText() != null ? etUrl.getText().toString().trim() : "";
            if (url.isEmpty()) return;
            executor.execute(() -> {
                long start = System.currentTimeMillis();
                try {
                    ApiClient.getInstance().getCryptoFacts(url);
                    long ms = System.currentTimeMillis() - start;
                    if (!isAdded()) return;
                    requireActivity().runOnUiThread(() ->
                            Toast.makeText(getContext(), "Connected in " + ms + " ms", Toast.LENGTH_SHORT).show());
                } catch (Exception e) {
                    showErrorOnUi(e);
                }
            });
        });
    }

    private void lockNow() {
        KeyFortApp.getInstance().getAppSession().lock();
        navigateToUnlock();
    }

    private void revokeDevice() {
        new AlertDialog.Builder(requireContext())
                .setTitle(R.string.revoke_device)
                .setMessage(R.string.revoke_device_confirm)
                .setPositiveButton(R.string.revoke, (d, w) -> performRevoke())
                .setNegativeButton(R.string.cancel, null)
                .show();
    }

    private void performRevoke() {
        AppSession session = KeyFortApp.getInstance().getAppSession();
        String token = resolveToken();
        String deviceId = nonEmpty(session.getDeviceId(), SecurePrefs.getInstance(requireContext()).getDeviceId(), "");

        executor.execute(() -> {
            try {
                ApiClient.getInstance().revokeDevice(token, deviceId);
                if (!isAdded()) return;
                requireActivity().runOnUiThread(() -> {
                    SecurePrefs.getInstance(requireContext()).clear();
                    session.clear();
                    navigateToUnlock();
                });
            } catch (Exception e) {
                showErrorOnUi(e);
            }
        });
    }

    private void signOut() {
        new AlertDialog.Builder(requireContext())
                .setTitle(R.string.sign_out)
                .setMessage(R.string.sign_out_confirm)
                .setPositiveButton(R.string.sign_out, (d, w) -> {
                    SecurePrefs.getInstance(requireContext()).clear();
                    KeyFortApp.getInstance().getAppSession().clear();
                    navigateToUnlock();
                })
                .setNegativeButton(R.string.cancel, null)
                .show();
    }

    private void showErrorOnUi(Exception e) {
        if (!isAdded()) return;
        requireActivity().runOnUiThread(() ->
                Toast.makeText(getContext(), getString(R.string.error_prefix) + e.getMessage(),
                        Toast.LENGTH_LONG).show());
    }

    private static String nonEmpty(String primary, String fallback, String defaultVal) {
        if (primary != null && !primary.isBlank()) return primary;
        if (fallback != null && !fallback.isBlank()) return fallback;
        return defaultVal;
    }

    private static String str(JsonObject obj, String field) {
        return obj.has(field) && !obj.get(field).isJsonNull() ? obj.get(field).getAsString() : "";
    }

    private void navigateToUnlock() {
        Intent intent = new Intent(requireContext(), UnlockActivity.class);
        intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TASK);
        startActivity(intent);
    }

    @Override
    public void onDestroy() {
        super.onDestroy();
        executor.shutdownNow();
    }
}
