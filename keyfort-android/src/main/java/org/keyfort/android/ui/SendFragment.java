package org.keyfort.android.ui;

import android.content.ClipData;
import android.content.ClipboardManager;
import android.content.Context;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.EditText;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;

import org.keyfort.android.R;
import org.keyfort.core.Crypto;

import java.nio.charset.StandardCharsets;
import java.util.Base64;

public class SendFragment extends Fragment {

    private EditText etContent;
    private View cardResult;
    private TextView tvPayload;
    private String lastBundle = "";

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

        etContent = view.findViewById(R.id.et_send_content);
        cardResult = view.findViewById(R.id.card_send_result);
        tvPayload = view.findViewById(R.id.tv_send_payload);

        view.findViewById(R.id.btn_create_send).setOnClickListener(v -> createSend());
        view.findViewById(R.id.btn_copy_send).setOnClickListener(v -> copySendPayload());
    }

    private void createSend() {
        String text = etContent.getText() != null ? etContent.getText().toString().trim() : "";
        if (text.isEmpty()) {
            Toast.makeText(getContext(), R.string.enter_secret_text, Toast.LENGTH_SHORT).show();
            return;
        }

        try {
            // Generate ephemeral 256-bit encryption key
            byte[] key = Crypto.random(32);
            byte[] plaintext = text.getBytes(StandardCharsets.UTF_8);
            byte[] aad = "keyfort|send|v1".getBytes(StandardCharsets.UTF_8);
            byte[] ciphertext = Crypto.seal(key, plaintext, aad);

            // Construct zero-knowledge send bundle: Base64(ciphertext)#Base64(key)
            String ctB64 = Base64.getUrlEncoder().withoutPadding().encodeToString(ciphertext);
            String keyB64 = Base64.getUrlEncoder().withoutPadding().encodeToString(key);
            Crypto.wipe(key);

            lastBundle = "keyfort-send://" + ctB64 + "#" + keyB64;
            tvPayload.setText(lastBundle);
            cardResult.setVisibility(View.VISIBLE);

            copySendPayload();
        } catch (Exception e) {
            Toast.makeText(getContext(), getString(R.string.error_prefix) + e.getMessage(), Toast.LENGTH_LONG).show();
        }
    }

    private void copySendPayload() {
        if (lastBundle.isEmpty() || getContext() == null) return;

        ClipboardManager cm = (ClipboardManager) requireContext().getSystemService(Context.CLIPBOARD_SERVICE);
        if (cm != null) {
            cm.setPrimaryClip(ClipData.newPlainText("KeyFort Send", lastBundle));
            Toast.makeText(getContext(), R.string.send_created_toast, Toast.LENGTH_SHORT).show();

            // Auto-clear clipboard after 30 seconds
            new Handler(Looper.getMainLooper()).postDelayed(() -> {
                if (getContext() != null) {
                    ClipboardManager c = (ClipboardManager) requireContext().getSystemService(Context.CLIPBOARD_SERVICE);
                    if (c != null) {
                        c.setPrimaryClip(ClipData.newPlainText("", ""));
                    }
                }
            }, 30_000);
        }
    }
}
