package org.keyfort.android.ui;

import android.content.Intent;
import android.os.Bundle;
import android.view.View;
import android.widget.Button;
import android.widget.EditText;
import android.widget.Toast;

import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

import org.keyfort.android.AppSession;
import org.keyfort.android.KeyFortApp;
import org.keyfort.android.MainActivity;
import org.keyfort.android.R;
import org.keyfort.android.network.ApiClient;
import org.keyfort.android.repository.VaultRepository;
import org.keyfort.android.security.BiometricHelper;
import org.keyfort.android.security.SecurePrefs;
import org.keyfort.core.Vault;

import android.content.ClipData;
import android.content.ClipboardManager;
import java.util.Arrays;
import java.util.Base64;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class UnlockActivity extends AppCompatActivity {

    private EditText etUserId, etDeviceId, etDeviceName, etPassword, etVerifyCode;
    private Button btnLogin, btnRegister, btnBiometric, btnSwitchMode, btnForgotPassword;
    private View layoutDeviceName, layoutVerifyCode;

    private boolean isRegisterMode = false;
    private boolean biometricPhase = false; // true after successful password entry
    private final ExecutorService executor = Executors.newSingleThreadExecutor();

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_unlock);

        etUserId = findViewById(R.id.et_user_id);
        etDeviceId = findViewById(R.id.et_device_id);
        etDeviceName = findViewById(R.id.et_device_name);
        etPassword = findViewById(R.id.et_master_password);
        etVerifyCode = findViewById(R.id.et_verify_code);
        layoutDeviceName = findViewById(R.id.layout_device_name);
        layoutVerifyCode = findViewById(R.id.layout_verify_code);

        btnLogin = findViewById(R.id.btn_login);
        btnRegister = findViewById(R.id.btn_register);
        btnBiometric = findViewById(R.id.btn_biometric);
        btnSwitchMode = findViewById(R.id.btn_switch_mode);
        btnForgotPassword = findViewById(R.id.btn_forgot_password);

        btnLogin.setOnClickListener(v -> performLogin());
        btnRegister.setOnClickListener(v -> performRegister());
        btnBiometric.setOnClickListener(v -> performBiometricUnlock());
        btnSwitchMode.setOnClickListener(v -> toggleMode());
        btnForgotPassword.setOnClickListener(v -> showRecoveryDialog());

        // Check if saved credentials exist
        SecurePrefs prefs = SecurePrefs.getInstance(this);
        String savedUserId = prefs.getUserId();
        String savedDeviceId = prefs.getDeviceId();

        if (savedUserId != null) {
            etUserId.setText(savedUserId);
        }
        if (savedDeviceId != null) {
            etDeviceId.setText(savedDeviceId);
        }

        updateUI();
    }

    private void toggleMode() {
        isRegisterMode = !isRegisterMode;
        updateUI();
    }

    private void updateUI() {
        layoutDeviceName.setVisibility(isRegisterMode ? View.VISIBLE : View.GONE);
        layoutVerifyCode.setVisibility(isRegisterMode ? View.VISIBLE : View.GONE);
        btnLogin.setVisibility(isRegisterMode ? View.GONE : View.VISIBLE);
        btnRegister.setVisibility(isRegisterMode ? View.VISIBLE : View.GONE);
        btnForgotPassword.setVisibility(isRegisterMode ? View.GONE : View.VISIBLE);
        btnSwitchMode.setText(isRegisterMode ? R.string.switch_to_login : R.string.switch_to_register);

        // Show biometric only if available and we have saved credentials
        boolean hasSavedCreds = SecurePrefs.getInstance(this).getUserId() != null;
        btnBiometric.setVisibility(
                (!isRegisterMode && hasSavedCreds && BiometricHelper.isBiometricAvailable(this))
                        ? View.VISIBLE : View.GONE);
    }

    private void performLogin() {
        String userId = etUserId.getText().toString().trim();
        String deviceId = etDeviceId.getText().toString().trim();
        char[] password = etPassword.getText().toString().toCharArray();

        if (userId.isEmpty() || deviceId.isEmpty() || password.length == 0) {
            Toast.makeText(this, R.string.fill_all_fields, Toast.LENGTH_SHORT).show();
            Arrays.fill(password, '\0');
            return;
        }

        String verifyCode = etVerifyCode.getText().toString().trim();

        showLoading(true);
        executor.execute(() -> {
            try {
                String token;
                // Try verify device (normal login)
                String authJson;
                if (!verifyCode.isEmpty()) {
                    authJson = ApiClient.getInstance().verifyDevice(userId, deviceId, verifyCode);
                } else {
                    // Try registering first, then verify
                    authJson = ApiClient.getInstance().registerDevice(userId, deviceId, "Android Device");
                }
                JsonObject authResp = JsonParser.parseString(authJson).getAsJsonObject();
                token = authResp.has("token") ? authResp.get("token").getAsString() : null;

                if (token == null || token.isEmpty()) {
                    runOnUiThread(() -> {
                        showLoading(false);
                        Toast.makeText(this, R.string.auth_failed, Toast.LENGTH_LONG).show();
                    });
                    Arrays.fill(password, '\0');
                    return;
                }

                // Sync vault from server and unlock
                VaultRepository repo = new VaultRepository(getApplicationContext());
                Vault vault = repo.syncFromServer(token, userId);
                Vault.Session session = vault.unlock(password);

                // Save credentials
                SecurePrefs prefs = SecurePrefs.getInstance(getApplicationContext());
                prefs.saveUserId(userId);
                prefs.saveDeviceId(deviceId);
                prefs.saveToken(token);

                // Populate AppSession
                AppSession appSession = KeyFortApp.getInstance().getAppSession();
                appSession.setVault(vault);
                appSession.setSession(session);
                appSession.setUserId(userId);
                appSession.setDeviceId(deviceId);
                appSession.setJwtToken(token);

                runOnUiThread(() -> {
                    Arrays.fill(password, '\0');
                    showLoading(false);
                    launchMainActivity();
                });
            } catch (Exception e) {
                Arrays.fill(password, '\0');
                runOnUiThread(() -> {
                    showLoading(false);
                    Toast.makeText(this, getString(R.string.error_prefix) + e.getMessage(),
                            Toast.LENGTH_LONG).show();
                });
            }
        });
    }

    private void performRegister() {
        String userId = etUserId.getText().toString().trim();
        String deviceId = etDeviceId.getText().toString().trim();
        String deviceName = etDeviceName.getText().toString().trim();
        char[] password = etPassword.getText().toString().toCharArray();

        if (userId.isEmpty() || deviceId.isEmpty() || password.length == 0) {
            Toast.makeText(this, R.string.fill_all_fields, Toast.LENGTH_SHORT).show();
            Arrays.fill(password, '\0');
            return;
        }
        if (deviceName.isEmpty()) deviceName = "Android Device";

        final String finalDeviceName = deviceName;
        showLoading(true);
        executor.execute(() -> {
            try {
                // Register device
                String regJson = ApiClient.getInstance().registerDevice(userId, deviceId, finalDeviceName);
                JsonObject regResp = JsonParser.parseString(regJson).getAsJsonObject();
                String token = regResp.has("token") ? regResp.get("token").getAsString() : null;

                if (token == null || token.isEmpty()) {
                    runOnUiThread(() -> {
                        showLoading(false);
                        Toast.makeText(this, R.string.registration_failed, Toast.LENGTH_LONG).show();
                    });
                    Arrays.fill(password, '\0');
                    return;
                }

                // Create a new vault
                Vault.Created created = Vault.create(userId, password);
                Vault vault = created.vault();

                // Push to server
                AppSession appSession = KeyFortApp.getInstance().getAppSession();
                appSession.setVault(vault);
                appSession.setUserId(userId);
                appSession.setDeviceId(deviceId);
                appSession.setJwtToken(token);

                Vault.Session vaultSession = vault.unlock(password);
                appSession.setSession(vaultSession);

                VaultRepository repo = new VaultRepository(getApplicationContext());
                repo.syncToServer(appSession);

                // Save credentials
                SecurePrefs prefs = SecurePrefs.getInstance(getApplicationContext());
                prefs.saveUserId(userId);
                prefs.saveDeviceId(deviceId);
                prefs.saveToken(token);

                String kitBase64 = Base64.getEncoder().encodeToString(created.recoveryKit());

                runOnUiThread(() -> {
                    Arrays.fill(password, '\0');
                    showLoading(false);
                    new AlertDialog.Builder(this)
                            .setTitle(R.string.recovery_kit)
                            .setMessage(getString(R.string.recovery_kit_desc) + "\n\n" + kitBase64)
                            .setCancelable(false)
                            .setPositiveButton(R.string.copy_recovery_kit, (d, w) -> {
                                ClipboardManager cm = (ClipboardManager) getSystemService(CLIPBOARD_SERVICE);
                                if (cm != null) {
                                    cm.setPrimaryClip(ClipData.newPlainText("Recovery Kit", kitBase64));
                                    Toast.makeText(this, R.string.copied_to_clipboard, Toast.LENGTH_SHORT).show();
                                }
                                launchMainActivity();
                            })
                            .setNegativeButton(R.string.ok, (d, w) -> launchMainActivity())
                            .show();
                });
            } catch (Exception e) {
                Arrays.fill(password, '\0');
                runOnUiThread(() -> {
                    showLoading(false);
                    Toast.makeText(this, getString(R.string.error_prefix) + e.getMessage(),
                            Toast.LENGTH_LONG).show();
                });
            }
        });
    }

    private void showRecoveryDialog() {
        String userId = etUserId.getText() != null ? etUserId.getText().toString().trim() : "";
        if (userId.isEmpty()) {
            Toast.makeText(this, R.string.fill_all_fields, Toast.LENGTH_SHORT).show();
            return;
        }

        View dialogView = getLayoutInflater().inflate(R.layout.dialog_recovery, null);
        EditText etKit = dialogView.findViewById(R.id.et_recovery_kit);
        EditText etNewPass = dialogView.findViewById(R.id.et_new_password);

        new AlertDialog.Builder(this)
                .setTitle(R.string.recover_vault)
                .setView(dialogView)
                .setPositiveButton(R.string.recover_and_unlock, (d, w) -> {
                    String kitStr = etKit.getText() != null ? etKit.getText().toString().trim() : "";
                    char[] newPass = etNewPass.getText() != null ? etNewPass.getText().toString().toCharArray() : new char[0];
                    if (kitStr.isEmpty() || newPass.length == 0) {
                        Toast.makeText(this, R.string.fill_all_fields, Toast.LENGTH_SHORT).show();
                        Arrays.fill(newPass, '\0');
                        return;
                    }
                    performRecovery(userId, kitStr, newPass);
                })
                .setNegativeButton(R.string.cancel, null)
                .show();
    }

    private void performRecovery(String userId, String kitBase64, char[] newPassword) {
        showLoading(true);
        executor.execute(() -> {
            try {
                byte[] kitBytes = Base64.getDecoder().decode(kitBase64);
                VaultRepository repo = new VaultRepository(getApplicationContext());
                SecurePrefs prefs = SecurePrefs.getInstance(getApplicationContext());
                String token = prefs.getToken();
                if (token == null) token = "";

                // Fetch latest vault ciphertext
                Vault vault = repo.syncFromServer(token, userId);
                vault.recover(kitBytes, newPassword);

                Vault.Session session = vault.unlock(newPassword);

                AppSession appSession = KeyFortApp.getInstance().getAppSession();
                appSession.setVault(vault);
                appSession.setSession(session);
                appSession.setUserId(userId);
                String deviceId = etDeviceId.getText() != null ? etDeviceId.getText().toString().trim() : "phone_001";
                appSession.setDeviceId(deviceId);
                appSession.setJwtToken(token);

                repo.syncToServer(appSession);
                prefs.saveUserId(userId);
                prefs.saveDeviceId(deviceId);

                runOnUiThread(() -> {
                    Arrays.fill(newPassword, '\0');
                    showLoading(false);
                    Toast.makeText(this, R.string.vault_recovered_success, Toast.LENGTH_LONG).show();
                    launchMainActivity();
                });
            } catch (Exception e) {
                Arrays.fill(newPassword, '\0');
                runOnUiThread(() -> {
                    showLoading(false);
                    Toast.makeText(this, getString(R.string.error_prefix) + e.getMessage(), Toast.LENGTH_LONG).show();
                });
            }
        });
    }

    private void performBiometricUnlock() {
        BiometricHelper.authenticate(this,
                () -> {
                    // On biometric success: re-use saved token and refresh from server
                    SecurePrefs prefs = SecurePrefs.getInstance(this);
                    String savedToken = prefs.getToken();
                    String savedUserId = prefs.getUserId();
                    String savedDeviceId = prefs.getDeviceId();

                    if (savedToken == null) {
                        Toast.makeText(this, R.string.please_login_first, Toast.LENGTH_LONG).show();
                        return;
                    }

                    // Ask for master password even after biometric (biometric just verifies identity)
                    char[] password = etPassword.getText().toString().toCharArray();
                    if (password.length == 0) {
                        Toast.makeText(this, R.string.enter_password_after_biometric, Toast.LENGTH_SHORT).show();
                        Arrays.fill(password, '\0');
                        return;
                    }

                    showLoading(true);
                    executor.execute(() -> {
                        try {
                            VaultRepository repo = new VaultRepository(getApplicationContext());
                            Vault vault = repo.syncFromServer(savedToken, savedUserId);
                            Vault.Session session = vault.unlock(password);

                            AppSession appSession = KeyFortApp.getInstance().getAppSession();
                            appSession.setVault(vault);
                            appSession.setSession(session);
                            appSession.setUserId(savedUserId);
                            appSession.setDeviceId(savedDeviceId);
                            appSession.setJwtToken(savedToken);

                            runOnUiThread(() -> {
                                Arrays.fill(password, '\0');
                                showLoading(false);
                                launchMainActivity();
                            });
                        } catch (Exception e) {
                            Arrays.fill(password, '\0');
                            runOnUiThread(() -> {
                                showLoading(false);
                                Toast.makeText(this,
                                        getString(R.string.error_prefix) + e.getMessage(),
                                        Toast.LENGTH_LONG).show();
                            });
                        }
                    });
                },
                () -> Toast.makeText(this, R.string.biometric_failed, Toast.LENGTH_SHORT).show()
        );
    }

    private void showLoading(boolean loading) {
        btnLogin.setEnabled(!loading);
        btnRegister.setEnabled(!loading);
        btnBiometric.setEnabled(!loading);
        btnSwitchMode.setEnabled(!loading);
    }

    private void launchMainActivity() {
        Intent intent = new Intent(this, MainActivity.class);
        intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TASK);
        startActivity(intent);
        finish();
    }
}
