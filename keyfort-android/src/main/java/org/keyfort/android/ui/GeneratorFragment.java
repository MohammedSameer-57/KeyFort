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
import android.widget.CheckBox;
import android.widget.SeekBar;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;

import org.keyfort.android.R;
import org.keyfort.android.generator.PasswordGenerator;

public class GeneratorFragment extends Fragment {

    private SeekBar seekBarLength;
    private TextView tvLengthLabel;
    private CheckBox checkUpper, checkLower, checkDigits, checkSymbols;
    private TextView tvResult;
    private final Handler handler = new Handler(Looper.getMainLooper());
    private Runnable clipboardClearRunnable;

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater,
                             @Nullable ViewGroup container,
                             @Nullable Bundle savedInstanceState) {
        return inflater.inflate(R.layout.fragment_generator, container, false);
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);

        seekBarLength = view.findViewById(R.id.seek_bar_length);
        tvLengthLabel = view.findViewById(R.id.tv_length_label);
        checkUpper = view.findViewById(R.id.check_upper);
        checkLower = view.findViewById(R.id.check_lower);
        checkDigits = view.findViewById(R.id.check_digits);
        checkSymbols = view.findViewById(R.id.check_symbols);
        tvResult = view.findViewById(R.id.tv_password_result);
        Button btnGenerate = view.findViewById(R.id.btn_generate);
        Button btnCopy = view.findViewById(R.id.btn_copy);

        // SeekBar: 8–64, default 20
        seekBarLength.setMax(56); // 64 - 8 = 56
        seekBarLength.setProgress(12); // 20 - 8 = 12
        updateLengthLabel(20);

        seekBarLength.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() {
            @Override
            public void onProgressChanged(SeekBar seekBar, int progress, boolean fromUser) {
                updateLengthLabel(progress + 8);
            }
            @Override public void onStartTrackingTouch(SeekBar seekBar) {}
            @Override public void onStopTrackingTouch(SeekBar seekBar) {}
        });

        btnGenerate.setOnClickListener(v -> generatePassword());
        btnCopy.setOnClickListener(v -> copyToClipboard());
    }

    private void updateLengthLabel(int length) {
        tvLengthLabel.setText(getString(R.string.password_length_label, length));
    }

    private void generatePassword() {
        int length = seekBarLength.getProgress() + 8;
        boolean upper = checkUpper.isChecked();
        boolean lower = checkLower.isChecked();
        boolean digits = checkDigits.isChecked();
        boolean symbols = checkSymbols.isChecked();

        if (!upper && !lower && !digits && !symbols) {
            Toast.makeText(getContext(), R.string.select_charset, Toast.LENGTH_SHORT).show();
            return;
        }

        String password = PasswordGenerator.generate(length, upper, lower, digits, symbols);
        tvResult.setText(password);
    }

    private void copyToClipboard() {
        String password = tvResult.getText().toString();
        if (password.isEmpty()) {
            Toast.makeText(getContext(), R.string.generate_first, Toast.LENGTH_SHORT).show();
            return;
        }

        ClipboardManager clipboard = (ClipboardManager) requireContext()
                .getSystemService(Context.CLIPBOARD_SERVICE);
        ClipData clip = ClipData.newPlainText("password", password);
        clipboard.setPrimaryClip(clip);
        Toast.makeText(getContext(), R.string.copied_to_clipboard, Toast.LENGTH_SHORT).show();

        // Auto-clear clipboard after 30 seconds
        if (clipboardClearRunnable != null) {
            handler.removeCallbacks(clipboardClearRunnable);
        }
        clipboardClearRunnable = () -> {
            ClipboardManager cb = (ClipboardManager) requireContext()
                    .getSystemService(Context.CLIPBOARD_SERVICE);
            cb.setPrimaryClip(ClipData.newPlainText("", ""));
        };
        handler.postDelayed(clipboardClearRunnable, 30_000L);
    }

    @Override
    public void onDestroyView() {
        super.onDestroyView();
        if (clipboardClearRunnable != null) {
            handler.removeCallbacks(clipboardClearRunnable);
        }
    }
}
