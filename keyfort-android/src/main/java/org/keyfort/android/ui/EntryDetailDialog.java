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
import android.widget.Button;
import android.widget.EditText;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.DialogFragment;

import org.keyfort.android.AppSession;
import org.keyfort.android.KeyFortApp;
import org.keyfort.android.R;
import org.keyfort.android.repository.VaultRepository;
import org.keyfort.core.Entry;

import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class EntryDetailDialog extends DialogFragment {

    public interface OnEntryChangedListener {
        void onEntryChanged();
    }

    private static final String ARG_ENTRY_ID = "entry_id";
    private static final String ARG_SITE = "site";
    private static final String ARG_USERNAME = "username";

    private final ExecutorService executor = Executors.newSingleThreadExecutor();
    private final Handler handler = new Handler(Looper.getMainLooper());
    private Runnable clipboardClearRunnable;

    private OnEntryChangedListener listener;

    public static EntryDetailDialog newInstance(Entry entry) {
        EntryDetailDialog dialog = new EntryDetailDialog();
        Bundle args = new Bundle();
        args.putString(ARG_ENTRY_ID, entry.id());
        args.putString(ARG_SITE, entry.site());
        args.putString(ARG_USERNAME, entry.username());
        dialog.setArguments(args);
        return dialog;
    }

    public void setOnEntryChangedListener(OnEntryChangedListener listener) {
        this.listener = listener;
    }

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater,
                             @Nullable ViewGroup container,
                             @Nullable Bundle savedInstanceState) {
        return inflater.inflate(R.layout.dialog_entry_detail, container, false);
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);

        Bundle args = requireArguments();
        String entryId = args.getString(ARG_ENTRY_ID);
        String site = args.getString(ARG_SITE);
        String username = args.getString(ARG_USERNAME);

        TextView tvSite = view.findViewById(R.id.tv_detail_site);
        TextView tvUsername = view.findViewById(R.id.tv_detail_username);
        TextView tvPassword = view.findViewById(R.id.tv_detail_password);
        Button btnReveal = view.findViewById(R.id.btn_reveal);
        Button btnEdit = view.findViewById(R.id.btn_edit);
        Button btnDelete = view.findViewById(R.id.btn_delete);

        tvSite.setText(site);
        tvUsername.setText(username);

        btnReveal.setOnClickListener(v -> revealPassword(entryId, tvPassword));
        btnEdit.setOnClickListener(v -> showEditDialog(entryId, site, username));
        btnDelete.setOnClickListener(v -> deleteEntry(entryId));
    }

    private void revealPassword(String entryId, TextView tvPassword) {
        AppSession session = KeyFortApp.getInstance().getAppSession();
        if (!session.isUnlocked()) {
            Toast.makeText(getContext(), R.string.vault_locked, Toast.LENGTH_SHORT).show();
            return;
        }
        executor.execute(() -> {
            try {
                String password = session.getSession().reveal(entryId);
                requireActivity().runOnUiThread(() -> {
                    tvPassword.setText(password);
                    tvPassword.setVisibility(View.VISIBLE);
                    // Copy to clipboard
                    ClipboardManager clipboard = (ClipboardManager) requireContext()
                            .getSystemService(Context.CLIPBOARD_SERVICE);
                    clipboard.setPrimaryClip(ClipData.newPlainText("password", password));
                    Toast.makeText(getContext(), R.string.copied_to_clipboard, Toast.LENGTH_SHORT).show();

                    // Auto-clear clipboard in 30s
                    if (clipboardClearRunnable != null) handler.removeCallbacks(clipboardClearRunnable);
                    clipboardClearRunnable = () -> {
                        ClipboardManager cb = (ClipboardManager) requireContext()
                                .getSystemService(Context.CLIPBOARD_SERVICE);
                        cb.setPrimaryClip(ClipData.newPlainText("", ""));
                        tvPassword.setText("");
                        tvPassword.setVisibility(View.GONE);
                    };
                    handler.postDelayed(clipboardClearRunnable, 30_000L);
                });
            } catch (Exception e) {
                requireActivity().runOnUiThread(() ->
                        Toast.makeText(getContext(),
                                getString(R.string.error_prefix) + e.getMessage(),
                                Toast.LENGTH_LONG).show());
            }
        });
    }

    private void showEditDialog(String entryId, String site, String username) {
        View dialogView = LayoutInflater.from(requireContext())
                .inflate(R.layout.dialog_add_entry, null);
        EditText etSite = dialogView.findViewById(R.id.et_site);
        EditText etUsername = dialogView.findViewById(R.id.et_username);
        EditText etPassword = dialogView.findViewById(R.id.et_password);
        etSite.setText(site);
        etUsername.setText(username);

        new androidx.appcompat.app.AlertDialog.Builder(requireContext())
                .setTitle(R.string.edit_entry)
                .setView(dialogView)
                .setPositiveButton(R.string.save, (d, w) -> {
                    String newSite = etSite.getText().toString().trim();
                    String newUsername = etUsername.getText().toString().trim();
                    String newPassword = etPassword.getText().toString();
                    if (!newSite.isEmpty() && !newUsername.isEmpty() && !newPassword.isEmpty()) {
                        performUpdate(entryId, newSite, newUsername, newPassword);
                    }
                })
                .setNegativeButton(R.string.cancel, null)
                .show();
    }

    private void performUpdate(String entryId, String site, String username, String password) {
        AppSession session = KeyFortApp.getInstance().getAppSession();
        executor.execute(() -> {
            try {
                session.getSession().update(entryId, site, username, password);
                VaultRepository repo = new VaultRepository(requireContext());
                repo.syncToServer(session);
                requireActivity().runOnUiThread(() -> {
                    if (listener != null) listener.onEntryChanged();
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

    private void deleteEntry(String entryId) {
        AppSession session = KeyFortApp.getInstance().getAppSession();
        executor.execute(() -> {
            try {
                session.getSession().remove(entryId);
                VaultRepository repo = new VaultRepository(requireContext());
                repo.syncToServer(session);
                requireActivity().runOnUiThread(() -> {
                    if (listener != null) listener.onEntryChanged();
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

    @Override
    public void onDestroyView() {
        super.onDestroyView();
        if (clipboardClearRunnable != null) handler.removeCallbacks(clipboardClearRunnable);
    }
}
