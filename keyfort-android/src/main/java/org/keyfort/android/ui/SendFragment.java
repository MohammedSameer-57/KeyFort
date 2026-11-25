package org.keyfort.android.ui;

import android.content.ClipboardManager;
import android.content.Context;
import android.content.Intent;
import android.os.Bundle;
import android.text.Editable;
import android.text.TextWatcher;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.EditText;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;

import com.google.android.material.button.MaterialButtonToggleGroup;
import com.google.android.material.chip.ChipGroup;
import com.google.android.material.materialswitch.MaterialSwitch;

import org.keyfort.android.R;
import org.keyfort.android.send.SendEnvelope;

import java.text.SimpleDateFormat;
import java.util.Arrays;
import java.util.Date;
import java.util.Locale;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class SendFragment extends Fragment {

    private final ExecutorService executor = Executors.newSingleThreadExecutor();
    private final ClipboardAutoClear clipboardHelper = new ClipboardAutoClear();

    private MaterialButtonToggleGroup toggleMode;
    private View panelCreate, panelOpen;

    private EditText etContent, etCreatePin;
    private ChipGroup chipsExpiry;
    private MaterialSwitch switchPin;
    private View layoutCreatePin, cardResult;
    private TextView tvPayload, tvMetaBadge, tvClipboardTimer;
    private Button btnCreateSend;

    private EditText etDecryptBundle, etDecryptPin;
    private View layoutDecryptPin, cardDecryptResult;
    private TextView tvBundleDetection, tvDecryptedMeta, tvDecryptedSecret;
    private Button btnDecryptSend;

    private String lastBundle = "";
    private String lastDecrypted = "";

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater,
                             @Nullable ViewGroup container,
                             @Nullable Bundle savedInstanceState) {
        return inflater.inflate(R.layout.fragment_send, container, false);
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);

        toggleMode = view.findViewById(R.id.toggle_send_mode);
        panelCreate = view.findViewById(R.id.panel_send_create);
        panelOpen = view.findViewById(R.id.panel_send_open);

        etContent = view.findViewById(R.id.et_send_content);
        chipsExpiry = view.findViewById(R.id.chips_send_expiry);
        switchPin = view.findViewById(R.id.switch_send_pin);
        layoutCreatePin = view.findViewById(R.id.layout_send_pin);
        etCreatePin = view.findViewById(R.id.et_send_pin);
        btnCreateSend = view.findViewById(R.id.btn_create_send);

        cardResult = view.findViewById(R.id.card_send_result);
        tvPayload = view.findViewById(R.id.tv_send_payload);
        tvMetaBadge = view.findViewById(R.id.tv_send_meta_badge);
        tvClipboardTimer = view.findViewById(R.id.tv_send_clipboard_timer);

        etDecryptBundle = view.findViewById(R.id.et_decrypt_bundle);
        tvBundleDetection = view.findViewById(R.id.tv_bundle_detection);
        layoutDecryptPin = view.findViewById(R.id.layout_decrypt_pin);
        etDecryptPin = view.findViewById(R.id.et_decrypt_pin);
        btnDecryptSend = view.findViewById(R.id.btn_decrypt_send);
        cardDecryptResult = view.findViewById(R.id.card_decrypt_result);
        tvDecryptedMeta = view.findViewById(R.id.tv_decrypted_meta);
        tvDecryptedSecret = view.findViewById(R.id.tv_decrypted_secret);

        toggleMode.addOnButtonCheckedListener((group, checkedId, isChecked) -> {
            if (!isChecked) return;
            boolean openMode = (checkedId == R.id.btn_send_mode_open);
            panelCreate.setVisibility(openMode ? View.GONE : View.VISIBLE);
            panelOpen.setVisibility(openMode ? View.VISIBLE : View.GONE);
        });

        switchPin.setOnCheckedChangeListener((btn, isChecked) -> {
            layoutCreatePin.setVisibility(isChecked ? View.VISIBLE : View.GONE);
            if (!isChecked && etCreatePin.getText() != null) {
                etCreatePin.getText().clear();
            }
        });

        btnCreateSend.setOnClickListener(v -> createSend());
        view.findViewById(R.id.btn_copy_send).setOnClickListener(v -> copyWithCountdown("KeyFort Send", lastBundle));
        view.findViewById(R.id.btn_share_send).setOnClickListener(v -> shareBundle());
        view.findViewById(R.id.btn_test_decrypt).setOnClickListener(v -> switchToTestDecrypt());

        view.findViewById(R.id.btn_paste_bundle).setOnClickListener(v -> pasteFromClipboard());
        btnDecryptSend.setOnClickListener(v -> decryptSend());
        view.findViewById(R.id.btn_copy_decrypted).setOnClickListener(v -> copyWithCountdown("Decrypted Secret", lastDecrypted));
        view.findViewById(R.id.btn_burn_screen).setOnClickListener(v -> clearOpenScreen());

        etDecryptBundle.addTextChangedListener(new TextWatcher() {
            @Override public void beforeTextChanged(CharSequence s, int start, int count, int after) {}
            @Override public void onTextChanged(CharSequence s, int start, int before, int count) {}
            @Override public void afterTextChanged(Editable s) {
                updateBundleDetection(s != null ? s.toString() : "");
            }
        });
    }

    private long getSelectedTtlSeconds() {
        int id = chipsExpiry.getCheckedChipId();
        if (id == R.id.chip_exp_15m) return 900L;
        if (id == R.id.chip_exp_1h) return 3600L;
        if (id == R.id.chip_exp_24h) return 86400L;
        return 0L;
    }

    private String getExpiryLabel(long ttlSeconds) {
        if (ttlSeconds == 900L) return "15m";
        if (ttlSeconds == 3600L) return "1h";
        if (ttlSeconds == 86400L) return "24h";
        return "No expiry";
    }

    private void createSend() {
        String text = etContent.getText() != null ? etContent.getText().toString().trim() : "";
        if (text.isEmpty()) {
            Toast.makeText(getContext(), R.string.enter_secret_text, Toast.LENGTH_SHORT).show();
            return;
        }

        boolean usePin = switchPin.isChecked();
        char[] passcode = usePin && etCreatePin.getText() != null
                ? etCreatePin.getText().toString().toCharArray()
                : new char[0];

        if (usePin && passcode.length == 0) {
            Toast.makeText(getContext(), R.string.send_pin_hint, Toast.LENGTH_SHORT).show();
            return;
        }

        long ttl = getSelectedTtlSeconds();
        long nowSec = System.currentTimeMillis() / 1000L;
        btnCreateSend.setEnabled(false);

        executor.execute(() -> {
            try {
                String bundle = SendEnvelope.create(text, ttl, passcode, nowSec);
                Arrays.fill(passcode, '\0');

                if (!isAdded()) return;
                requireActivity().runOnUiThread(() -> {
                    btnCreateSend.setEnabled(true);
                    lastBundle = bundle;
                    tvPayload.setText(bundle);
                    tvMetaBadge.setText("AES-256-GCM · " + getExpiryLabel(ttl) + (usePin ? " · PIN" : ""));
                    cardResult.setVisibility(View.VISIBLE);
                    copyWithCountdown("KeyFort Send", bundle);
                });
            } catch (Exception e) {
                Arrays.fill(passcode, '\0');
                if (!isAdded()) return;
                requireActivity().runOnUiThread(() -> {
                    btnCreateSend.setEnabled(true);
                    Toast.makeText(getContext(), getString(R.string.error_prefix) + e.getMessage(),
                            Toast.LENGTH_LONG).show();
                });
            }
        });
    }

    private void shareBundle() {
        if (lastBundle.isEmpty() || !isAdded()) return;
        Intent intent = new Intent(Intent.ACTION_SEND);
        intent.setType("text/plain");
        intent.putExtra(Intent.EXTRA_TEXT, lastBundle);
        startActivity(Intent.createChooser(intent, getString(R.string.share_send_link)));
    }

    private void switchToTestDecrypt() {
        if (lastBundle.isEmpty()) return;
        toggleMode.check(R.id.btn_send_mode_open);
        etDecryptBundle.setText(lastBundle);
        etDecryptPin.setText("");
        cardDecryptResult.setVisibility(View.GONE);
    }

    private void pasteFromClipboard() {
        if (!isAdded()) return;
        ClipboardManager cm = (ClipboardManager) requireContext().getSystemService(Context.CLIPBOARD_SERVICE);
        if (cm != null && cm.hasPrimaryClip() && cm.getPrimaryClip() != null && cm.getPrimaryClip().getItemCount() > 0) {
            CharSequence clip = cm.getPrimaryClip().getItemAt(0).coerceToText(requireContext());
            if (clip != null) {
                etDecryptBundle.setText(clip.toString().trim());
                return;
            }
        }
        Toast.makeText(getContext(), R.string.send_detected_invalid, Toast.LENGTH_SHORT).show();
    }

    private void updateBundleDetection(String raw) {
        SendEnvelope.Inspection info = SendEnvelope.inspect(raw);
        if (!info.valid()) {
            tvBundleDetection.setText(R.string.send_detected_invalid);
            layoutDecryptPin.setVisibility(View.GONE);
        } else if (info.requiresPasscode()) {
            tvBundleDetection.setText(R.string.send_detected_v2p);
            layoutDecryptPin.setVisibility(View.VISIBLE);
        } else if (info.version() == 2) {
            tvBundleDetection.setText(R.string.send_detected_v2);
            layoutDecryptPin.setVisibility(View.GONE);
        } else {
            tvBundleDetection.setText(R.string.send_detected_v1);
            layoutDecryptPin.setVisibility(View.GONE);
        }
    }

    private void decryptSend() {
        String bundle = etDecryptBundle.getText() != null ? etDecryptBundle.getText().toString().trim() : "";
        char[] pin = etDecryptPin.getText() != null ? etDecryptPin.getText().toString().toCharArray() : new char[0];
        long nowSec = System.currentTimeMillis() / 1000L;

        btnDecryptSend.setEnabled(false);
        executor.execute(() -> {
            try {
                SendEnvelope.Decrypted res = SendEnvelope.open(bundle, pin, nowSec);
                Arrays.fill(pin, '\0');

                if (!isAdded()) return;
                requireActivity().runOnUiThread(() -> {
                    btnDecryptSend.setEnabled(true);
                    lastDecrypted = res.text();
                    tvDecryptedSecret.setText(res.text());
                    tvDecryptedMeta.setText(formatDecryptedMeta(res));
                    cardDecryptResult.setVisibility(View.VISIBLE);
                });
            } catch (Exception e) {
                Arrays.fill(pin, '\0');
                if (!isAdded()) return;
                requireActivity().runOnUiThread(() -> {
                    btnDecryptSend.setEnabled(true);
                    cardDecryptResult.setVisibility(View.GONE);
                    String msg = e instanceof SendEnvelope.ExpiredException
                            ? "Expired — this encrypted Send can no longer be opened"
                            : e instanceof SendEnvelope.PasscodeRequiredException
                                ? getString(R.string.decrypt_pin_hint)
                                : getString(R.string.send_wrong_pin);
                    Toast.makeText(getContext(), msg, Toast.LENGTH_LONG).show();
                });
            }
        });
    }

    private String formatDecryptedMeta(SendEnvelope.Decrypted res) {
        String base = "v" + res.version() + (res.passcodeProtected() ? " · Passcode verified" : " · AEAD verified");
        if (res.expiresAtEpochSeconds() > 0L) {
            String time = new SimpleDateFormat("MMM d, HH:mm", Locale.getDefault())
                    .format(new Date(res.expiresAtEpochSeconds() * 1000L));
            return base + " · Expires " + time;
        }
        return base + " · No expiry";
    }

    private void clearOpenScreen() {
        clipboardHelper.cancel();
        lastDecrypted = "";
        if (etDecryptBundle.getText() != null) etDecryptBundle.getText().clear();
        if (etDecryptPin.getText() != null) etDecryptPin.getText().clear();
        tvDecryptedSecret.setText("");
        cardDecryptResult.setVisibility(View.GONE);
    }

    private void copyWithCountdown(String label, String value) {
        if (value == null || value.isEmpty() || !isAdded()) return;
        Toast.makeText(getContext(), R.string.send_created_toast, Toast.LENGTH_SHORT).show();
        clipboardHelper.copyWithAutoClear(requireContext(), label, value, secondsLeft -> {
            if (!isAdded()) return;
            if (secondsLeft > 0) {
                tvClipboardTimer.setVisibility(View.VISIBLE);
                tvClipboardTimer.setText(getString(R.string.clipboard_countdown, secondsLeft));
            } else {
                tvClipboardTimer.setVisibility(View.GONE);
            }
        });
    }

    @Override
    public void onDestroyView() {
        super.onDestroyView();
        clipboardHelper.cancel();
        lastBundle = "";
        lastDecrypted = "";
    }

    @Override
    public void onDestroy() {
        super.onDestroy();
        executor.shutdownNow();
    }
}
