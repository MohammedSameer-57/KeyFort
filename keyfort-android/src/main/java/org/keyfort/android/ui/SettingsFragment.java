package org.keyfort.android.ui;

import android.content.Intent;
import android.content.SharedPreferences;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AlertDialog;
import androidx.fragment.app.Fragment;
import androidx.preference.PreferenceManager;

import com.google.android.material.textfield.TextInputEditText;

import org.keyfort.android.AppSession;
import org.keyfort.android.KeyFortApp;
import org.keyfort.android.R;
import org.keyfort.android.network.ApiClient;
import org.keyfort.android.security.SecurePrefs;

import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class SettingsFragment extends Fragment {

    private final ExecutorService executor = Executors.newSingleThreadExecutor();

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

        view.findViewById(R.id.btn_server_url).setOnClickListener(v -> showServerUrlDialog());
        view.findViewById(R.id.btn_lock_now).setOnClickListener(v -> lockNow());
        view.findViewById(R.id.btn_revoke_device).setOnClickListener(v -> revokeDevice());
        view.findViewById(R.id.btn_verify_ledger).setOnClickListener(v -> verifyLedger());
        view.findViewById(R.id.btn_sign_out).setOnClickListener(v -> signOut());
    }

    private void showServerUrlDialog() {
        View dialogView = LayoutInflater.from(getContext())
                .inflate(R.layout.dialog_server_url, null);
        TextInputEditText etUrl = dialogView.findViewById(R.id.et_server_url);
        etUrl.setText(ApiClient.getInstance().getBaseUrl());

        new AlertDialog.Builder(requireContext())
                .setTitle(R.string.server_url)
                .setView(dialogView)
                .setPositiveButton(R.string.save, (d, w) -> {
                    String url = etUrl.getText() != null ? etUrl.getText().toString().trim() : "";
                    if (!url.isEmpty()) {
                        ApiClient.getInstance().setBaseUrl(url);
                        SharedPreferences prefs = PreferenceManager
                                .getDefaultSharedPreferences(requireContext());
                        prefs.edit().putString("server_url", url).apply();
                        Toast.makeText(getContext(), R.string.server_url_saved, Toast.LENGTH_SHORT).show();
                    }
                })
                .setNegativeButton(R.string.cancel, null)
                .show();
    }

    private void lockNow() {
        AppSession session = KeyFortApp.getInstance().getAppSession();
        session.lock();
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
        String token = session.getJwtToken();
        String deviceId = session.getDeviceId();

        executor.execute(() -> {
            try {
                ApiClient.getInstance().revokeDevice(token, deviceId);
                requireActivity().runOnUiThread(() -> {
                    SecurePrefs.getInstance(requireContext()).clear();
                    session.clear();
                    navigateToUnlock();
                });
            } catch (Exception e) {
                requireActivity().runOnUiThread(() ->
                        Toast.makeText(getContext(), getString(R.string.error_prefix) + e.getMessage(),
                                Toast.LENGTH_LONG).show());
            }
        });
    }

    private void verifyLedger() {
        executor.execute(() -> {
            try {
                String result = ApiClient.getInstance().verifyLedger();
                requireActivity().runOnUiThread(() ->
                        new AlertDialog.Builder(requireContext())
                                .setTitle(R.string.ledger_verification)
                                .setMessage(result)
                                .setPositiveButton(R.string.ok, null)
                                .show());
            } catch (Exception e) {
                requireActivity().runOnUiThread(() ->
                        Toast.makeText(getContext(), getString(R.string.error_prefix) + e.getMessage(),
                                Toast.LENGTH_LONG).show());
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

    private void navigateToUnlock() {
        Intent intent = new Intent(requireContext(), UnlockActivity.class);
        intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TASK);
        startActivity(intent);
    }
}
