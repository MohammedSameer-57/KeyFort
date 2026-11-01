package org.keyfort.android.ui;

import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.EditText;
import android.widget.ImageButton;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.DialogFragment;

import org.keyfort.android.AppSession;
import org.keyfort.android.KeyFortApp;
import org.keyfort.android.R;
import org.keyfort.android.generator.PasswordGenerator;
import org.keyfort.android.repository.VaultRepository;

import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class AddEntryDialog extends DialogFragment {

    public interface OnEntrySavedListener {
        void onEntrySaved();
    }

    private OnEntrySavedListener listener;
    private EditText etSite, etUsername, etPassword;
    private boolean passwordVisible = false;
    private final ExecutorService executor = Executors.newSingleThreadExecutor();

    public static AddEntryDialog newInstance() {
        return new AddEntryDialog();
    }

    public void setOnEntrySavedListener(OnEntrySavedListener listener) {
        this.listener = listener;
    }

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater,
                             @Nullable ViewGroup container,
                             @Nullable Bundle savedInstanceState) {
        return inflater.inflate(R.layout.dialog_add_entry, container, false);
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);

        etSite = view.findViewById(R.id.et_site);
        etUsername = view.findViewById(R.id.et_username);
        etPassword = view.findViewById(R.id.et_password);
        ImageButton btnTogglePassword = view.findViewById(R.id.btn_toggle_password);
        Button btnGenerate = view.findViewById(R.id.btn_generate_password);
        Button btnSave = view.findViewById(R.id.btn_save);
        Button btnCancel = view.findViewById(R.id.btn_cancel);

        btnTogglePassword.setOnClickListener(v -> togglePasswordVisibility());
        btnGenerate.setOnClickListener(v ->
                etPassword.setText(PasswordGenerator.generateDefault()));
        btnSave.setOnClickListener(v -> saveEntry());
        btnCancel.setOnClickListener(v -> dismiss());
    }

    private void togglePasswordVisibility() {
        passwordVisible = !passwordVisible;
        if (passwordVisible) {
            etPassword.setInputType(
                    android.text.InputType.TYPE_CLASS_TEXT |
                    android.text.InputType.TYPE_TEXT_VARIATION_VISIBLE_PASSWORD);
        } else {
            etPassword.setInputType(
                    android.text.InputType.TYPE_CLASS_TEXT |
                    android.text.InputType.TYPE_TEXT_VARIATION_PASSWORD);
        }
        etPassword.setSelection(etPassword.length());
    }

    private void saveEntry() {
        String site = etSite.getText() != null ? etSite.getText().toString().trim() : "";
        String username = etUsername.getText() != null ? etUsername.getText().toString().trim() : "";
        String password = etPassword.getText() != null ? etPassword.getText().toString() : "";

        if (site.isEmpty() || username.isEmpty() || password.isEmpty()) {
            Toast.makeText(getContext(), R.string.fill_all_fields, Toast.LENGTH_SHORT).show();
            return;
        }

        AppSession session = KeyFortApp.getInstance().getAppSession();
        if (!session.isUnlocked()) {
            Toast.makeText(getContext(), R.string.vault_locked, Toast.LENGTH_SHORT).show();
            return;
        }

        executor.execute(() -> {
            try {
                session.getSession().add(site, username, password);
                // Sync to server
                VaultRepository repo = new VaultRepository(requireContext());
                repo.syncToServer(session);

                requireActivity().runOnUiThread(() -> {
                    if (listener != null) listener.onEntrySaved();
                    dismiss();
                });
            } catch (Exception e) {
                requireActivity().runOnUiThread(() ->
                        Toast.makeText(getContext(),
                                getString(R.string.error_prefix) + e.getMessage(),
                                Toast.LENGTH_LONG).show());
            }
        });
    }
}
