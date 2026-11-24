package org.keyfort.android.ui;

import android.content.ClipData;
import android.content.ClipboardManager;
import android.content.Context;
import android.content.Intent;
import android.graphics.Color;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.view.WindowManager;
import android.widget.Button;
import android.widget.RadioGroup;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.DialogFragment;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

import org.keyfort.android.AppSession;
import org.keyfort.android.KeyFortApp;
import org.keyfort.android.R;
import org.keyfort.android.network.ApiClient;

import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class GuestPassDialog extends DialogFragment {

    private static final String ARG_ENTRY_ID = "entry_id";
    private static final String ARG_SITE = "site";

    private final ExecutorService executor = Executors.newSingleThreadExecutor();
    private final Handler handler = new Handler(Looper.getMainLooper());
    private Runnable pollRunnable;
    private Runnable clipboardClearRunnable;

    private String passId;
    private String fullLink;
    private View layoutDuration;
    private View layoutReady;
    private TextView tvLink;
    private TextView tvStatus;
    private Button btnEndSession;
    private RadioGroup rgDuration;

    public static GuestPassDialog newInstance(String entryId, String site) {
        GuestPassDialog dialog = new GuestPassDialog();
        Bundle args = new Bundle();
        args.putString(ARG_ENTRY_ID, entryId);
        args.putString(ARG_SITE, site);
        dialog.setArguments(args);
        return dialog;
    }

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater,
                             @Nullable ViewGroup container,
                             @Nullable Bundle savedInstanceState) {
        return inflater.inflate(R.layout.dialog_guest_pass, container, false);
    }

    @Override
    public void onStart() {
        super.onStart();
        if (getDialog() != null && getDialog().getWindow() != null) {
            getDialog().getWindow().setFlags(
                    WindowManager.LayoutParams.FLAG_SECURE,
                    WindowManager.LayoutParams.FLAG_SECURE
            );
        }
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);

        String entryId = requireArguments().getString(ARG_ENTRY_ID);
        String site = requireArguments().getString(ARG_SITE);

        layoutDuration = view.findViewById(R.id.layout_duration_selection);
        layoutReady = view.findViewById(R.id.layout_link_ready);
        tvLink = view.findViewById(R.id.tv_guest_link);
        tvStatus = view.findViewById(R.id.tv_status_chip);
        btnEndSession = view.findViewById(R.id.btn_end_session);
        rgDuration = view.findViewById(R.id.rg_duration);

        view.findViewById(R.id.btn_generate_pass).setOnClickListener(v -> createGuestPass(entryId));
        view.findViewById(R.id.btn_share_link).setOnClickListener(v -> shareLink());
        view.findViewById(R.id.btn_copy_link).setOnClickListener(v -> copyLink());
        btnEndSession.setOnClickListener(v -> endSession());
        view.findViewById(R.id.btn_close_pass).setOnClickListener(v -> dismiss());
    }

    private int getSelectedDurationSeconds() {
        int checkedId = rgDuration.getCheckedRadioButtonId();
        if (checkedId == R.id.rb_duration_5m) return 300;
        if (checkedId == R.id.rb_duration_1h) return 3600;
        return 900; // default 15m
    }

    private void createGuestPass(String entryId) {
        AppSession session = KeyFortApp.getInstance().getAppSession();
        String token = session.getJwtToken();
        if (token == null || token.isEmpty()) {
            token = org.keyfort.android.security.SecurePrefs.getInstance(requireContext()).getToken();
        }
        final String authToken = token;
        int ttl = getSelectedDurationSeconds();

        executor.execute(() -> {
            try {
                String respJson = ApiClient.getInstance().createGuestPass(authToken, entryId, ttl);
                JsonObject json = JsonParser.parseString(respJson).getAsJsonObject();
                passId = json.has("id") ? json.get("id").getAsString() : "";
                String rawToken = "";
                if (json.has("token") && !json.get("token").isJsonNull()) {
                    rawToken = json.get("token").getAsString();
                } else if (json.has("rawToken") && !json.get("rawToken").isJsonNull()) {
                    rawToken = json.get("rawToken").getAsString();
                }
                fullLink = GuestPassStateResolver.buildRedeemUrl(ApiClient.getInstance().getBaseUrl(), rawToken);

                requireActivity().runOnUiThread(() -> {
                    tvLink.setText(fullLink);
                    layoutDuration.setVisibility(View.GONE);
                    layoutReady.setVisibility(View.VISIBLE);
                    copyLink();
                    startPolling();
                });
            } catch (Exception e) {
                requireActivity().runOnUiThread(() ->
                        Toast.makeText(getContext(), getString(R.string.error_prefix) + e.getMessage(), Toast.LENGTH_LONG).show());
            }
        });
    }

    private void shareLink() {
        if (fullLink == null) return;
        Intent intent = new Intent(Intent.ACTION_SEND);
        intent.setType("text/plain");
        intent.putExtra(Intent.EXTRA_TEXT, fullLink);
        startActivity(Intent.createChooser(intent, getString(R.string.share_link)));
    }

    private void copyLink() {
        if (fullLink == null || getContext() == null) return;
        ClipboardManager cm = (ClipboardManager) requireContext().getSystemService(Context.CLIPBOARD_SERVICE);
        if (cm != null) {
            cm.setPrimaryClip(ClipData.newPlainText("Guest Pass Link", fullLink));
            Toast.makeText(getContext(), R.string.copied_to_clipboard, Toast.LENGTH_SHORT).show();

            if (clipboardClearRunnable != null) handler.removeCallbacks(clipboardClearRunnable);
            clipboardClearRunnable = () -> {
                if (getContext() != null) {
                    ClipboardManager c = (ClipboardManager) requireContext().getSystemService(Context.CLIPBOARD_SERVICE);
                    if (c != null) c.setPrimaryClip(ClipData.newPlainText("", ""));
                }
            };
            handler.postDelayed(clipboardClearRunnable, 30_000L);
        }
    }

    private void startPolling() {
        pollRunnable = new Runnable() {
            @Override
            public void run() {
                if (passId == null || !isAdded()) return;
                AppSession session = KeyFortApp.getInstance().getAppSession();
                String token = session.getJwtToken();

                executor.execute(() -> {
                    try {
                        String respJson = ApiClient.getInstance().getGuestPass(token, passId);
                        JsonObject json = JsonParser.parseString(respJson).getAsJsonObject();
                        String state = json.has("state") && !json.get("state").isJsonNull() ? json.get("state").getAsString() : "";
                        boolean hasSession = json.has("hasSession") && json.get("hasSession").getAsBoolean();

                        requireActivity().runOnUiThread(() -> {
                            updateStatus(state, hasSession);
                            if (!"EXPIRED".equals(state) && !"REVOKED".equals(state) && !"BURNED".equals(state)) {
                                handler.postDelayed(pollRunnable, 2000L);
                            }
                        });
                    } catch (Exception e) {
                        // Retry on intermittent failure if not finished
                        requireActivity().runOnUiThread(() -> handler.postDelayed(pollRunnable, 3000L));
                    }
                });
            }
        };
        handler.postDelayed(pollRunnable, 2000L);
    }

    private void updateStatus(String state, boolean hasSession) {
        if (!isAdded()) return;
        GuestPassStateResolver.DisplayState display = GuestPassStateResolver.resolve(state, hasSession);
        switch (display) {
            case FRIEND_LOGGED_IN:
                tvStatus.setText(R.string.status_active);
                tvStatus.setBackgroundColor(Color.parseColor("#C8E6C9"));
                tvStatus.setTextColor(Color.parseColor("#1B5E20"));
                btnEndSession.setVisibility(View.VISIBLE);
                break;
            case LINK_USED:
                tvStatus.setText(R.string.status_used);
                tvStatus.setBackgroundColor(Color.parseColor("#EAE8E0"));
                tvStatus.setTextColor(Color.parseColor("#1B1C18"));
                btnEndSession.setVisibility(View.GONE);
                break;
            case EXPIRED:
                tvStatus.setText(R.string.status_expired);
                tvStatus.setBackgroundColor(Color.parseColor("#FFDAD6"));
                tvStatus.setTextColor(Color.parseColor("#BA1A1A"));
                btnEndSession.setVisibility(View.GONE);
                break;
            case WAITING_FOR_FRIEND:
            default:
                tvStatus.setText(R.string.status_waiting);
                tvStatus.setBackgroundColor(Color.parseColor("#DEE0FF"));
                tvStatus.setTextColor(Color.parseColor("#00006E"));
                btnEndSession.setVisibility(View.GONE);
                break;
        }
    }

    private void endSession() {
        if (passId == null) return;
        AppSession session = KeyFortApp.getInstance().getAppSession();
        String token = session.getJwtToken();

        executor.execute(() -> {
            try {
                ApiClient.getInstance().deleteGuestSession(token, passId);
                requireActivity().runOnUiThread(() -> {
                    Toast.makeText(getContext(), R.string.session_ended, Toast.LENGTH_SHORT).show();
                    btnEndSession.setVisibility(View.GONE);
                    tvStatus.setText(R.string.session_ended);
                });
            } catch (Exception e) {
                requireActivity().runOnUiThread(() ->
                        Toast.makeText(getContext(), getString(R.string.error_prefix) + e.getMessage(), Toast.LENGTH_LONG).show());
            }
        });
    }

    @Override
    public void onDestroyView() {
        super.onDestroyView();
        if (pollRunnable != null) handler.removeCallbacks(pollRunnable);
        if (clipboardClearRunnable != null) handler.removeCallbacks(clipboardClearRunnable);
    }
}
