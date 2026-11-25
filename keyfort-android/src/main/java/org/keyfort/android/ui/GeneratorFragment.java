package org.keyfort.android.ui;

import android.content.res.ColorStateList;
import android.os.Bundle;
import android.text.SpannableStringBuilder;
import android.text.Spanned;
import android.text.style.ForegroundColorSpan;
import android.util.TypedValue;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.AttrRes;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;

import com.google.android.material.button.MaterialButtonToggleGroup;
import com.google.android.material.chip.ChipGroup;
import com.google.android.material.materialswitch.MaterialSwitch;
import com.google.android.material.progressindicator.LinearProgressIndicator;
import com.google.android.material.slider.Slider;

import org.keyfort.android.R;
import org.keyfort.android.generator.PasswordGenerator;

import java.util.ArrayList;
import java.util.List;

public class GeneratorFragment extends Fragment {

    private static final int MAX_RECENT = 4;

    private MaterialButtonToggleGroup toggleMode;
    private View groupPasswordOptions;
    private View groupPassphraseOptions;

    private Slider sliderLength;
    private TextView tvLengthLabel;
    private MaterialSwitch switchUpper, switchLower, switchDigits, switchSymbols, switchAvoidAmbiguous;

    private Slider sliderWordCount;
    private TextView tvWordCountLabel;
    private ChipGroup chipsSeparator;
    private MaterialSwitch switchCapitalize, switchIncludeNumber;

    private TextView tvResult;
    private LinearProgressIndicator progressStrength;
    private TextView tvEntropySummary;
    private TextView tvClipboardStatus;

    private View cardRecent;
    private LinearLayout containerRecent;

    private final List<String> recentHistory = new ArrayList<>();
    private final ClipboardAutoClear clipboardHelper = new ClipboardAutoClear();
    private String currentSecret = "";
    private boolean isPassphraseMode = false;

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

        toggleMode = view.findViewById(R.id.toggle_gen_mode);
        groupPasswordOptions = view.findViewById(R.id.group_password_options);
        groupPassphraseOptions = view.findViewById(R.id.group_passphrase_options);

        sliderLength = view.findViewById(R.id.slider_length);
        tvLengthLabel = view.findViewById(R.id.tv_length_label);
        switchUpper = view.findViewById(R.id.switch_upper);
        switchLower = view.findViewById(R.id.switch_lower);
        switchDigits = view.findViewById(R.id.switch_digits);
        switchSymbols = view.findViewById(R.id.switch_symbols);
        switchAvoidAmbiguous = view.findViewById(R.id.switch_avoid_ambiguous);

        sliderWordCount = view.findViewById(R.id.slider_word_count);
        tvWordCountLabel = view.findViewById(R.id.tv_word_count_label);
        chipsSeparator = view.findViewById(R.id.chips_separator);
        switchCapitalize = view.findViewById(R.id.switch_capitalize);
        switchIncludeNumber = view.findViewById(R.id.switch_include_number);

        tvResult = view.findViewById(R.id.tv_password_result);
        progressStrength = view.findViewById(R.id.progress_strength);
        tvEntropySummary = view.findViewById(R.id.tv_entropy_summary);
        tvClipboardStatus = view.findViewById(R.id.tv_clipboard_status);

        cardRecent = view.findViewById(R.id.card_recent_history);
        containerRecent = view.findViewById(R.id.container_recent_items);

        toggleMode.addOnButtonCheckedListener((group, checkedId, isChecked) -> {
            if (!isChecked) return;
            isPassphraseMode = (checkedId == R.id.btn_mode_passphrase);
            groupPasswordOptions.setVisibility(isPassphraseMode ? View.GONE : View.VISIBLE);
            groupPassphraseOptions.setVisibility(isPassphraseMode ? View.VISIBLE : View.GONE);
            generateCurrent(true);
        });

        sliderLength.addOnChangeListener((slider, value, fromUser) -> {
            tvLengthLabel.setText(getString(R.string.password_length_label, Math.round(value)));
            generateCurrent(false);
        });

        View.OnClickListener charsetListener = v -> {
            if (!switchUpper.isChecked() && !switchLower.isChecked()
                    && !switchDigits.isChecked() && !switchSymbols.isChecked()) {
                if (v instanceof MaterialSwitch sw) {
                    sw.setChecked(true);
                }
                Toast.makeText(getContext(), R.string.select_charset, Toast.LENGTH_SHORT).show();
                return;
            }
            generateCurrent(false);
        };
        switchUpper.setOnClickListener(charsetListener);
        switchLower.setOnClickListener(charsetListener);
        switchDigits.setOnClickListener(charsetListener);
        switchSymbols.setOnClickListener(charsetListener);
        switchAvoidAmbiguous.setOnClickListener(v -> generateCurrent(false));

        sliderWordCount.addOnChangeListener((slider, value, fromUser) -> {
            tvWordCountLabel.setText(getString(R.string.word_count_label, Math.round(value)));
            generateCurrent(false);
        });
        chipsSeparator.setOnCheckedStateChangeListener((group, checkedIds) -> generateCurrent(false));
        switchCapitalize.setOnClickListener(v -> generateCurrent(false));
        switchIncludeNumber.setOnClickListener(v -> generateCurrent(false));

        view.findViewById(R.id.btn_generate).setOnClickListener(v -> generateCurrent(true));
        view.findViewById(R.id.btn_copy).setOnClickListener(v -> copyValue(currentSecret));

        generateCurrent(false);
    }

    private void generateCurrent(boolean recordPrevious) {
        if (recordPrevious && !currentSecret.isEmpty()) {
            addRecent(currentSecret);
        }

        double entropyBits;
        if (isPassphraseMode) {
            int words = Math.round(sliderWordCount.getValue());
            String sep = resolveSeparator();
            boolean cap = switchCapitalize.isChecked();
            boolean num = switchIncludeNumber.isChecked();
            currentSecret = PasswordGenerator.generatePassphrase(words, sep, cap, num);
            entropyBits = PasswordGenerator.calculatePassphraseEntropyBits(words, num);
        } else {
            int length = Math.round(sliderLength.getValue());
            boolean upper = switchUpper.isChecked();
            boolean lower = switchLower.isChecked();
            boolean digits = switchDigits.isChecked();
            boolean symbols = switchSymbols.isChecked();
            boolean avoid = switchAvoidAmbiguous.isChecked();
            currentSecret = PasswordGenerator.generate(length, upper, lower, digits, symbols, avoid);
            entropyBits = PasswordGenerator.calculatePasswordEntropyBits(
                    length, upper, lower, digits, symbols, avoid);
        }

        tvResult.setText(formatColoredSecret(currentSecret));
        updateStrengthIndicator(entropyBits);
    }

    private String resolveSeparator() {
        int id = chipsSeparator.getCheckedChipId();
        if (id == R.id.chip_sep_dot) return ".";
        if (id == R.id.chip_sep_underscore) return "_";
        if (id == R.id.chip_sep_space) return " ";
        return "-";
    }

    private CharSequence formatColoredSecret(String secret) {
        SpannableStringBuilder ssb = new SpannableStringBuilder(secret);
        int digitColor = resolveThemeColor(androidx.appcompat.R.attr.colorPrimary);
        int symbolColor = resolveThemeColor(com.google.android.material.R.attr.colorTertiary);

        for (int i = 0; i < secret.length(); i++) {
            char c = secret.charAt(i);
            if (Character.isDigit(c)) {
                ssb.setSpan(new ForegroundColorSpan(digitColor), i, i + 1, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
            } else if (!Character.isLetter(c) && !Character.isWhitespace(c)) {
                ssb.setSpan(new ForegroundColorSpan(symbolColor), i, i + 1, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
            }
        }
        return ssb;
    }

    private void updateStrengthIndicator(double entropyBits) {
        PasswordGenerator.StrengthTier tier = PasswordGenerator.assessStrength(entropyBits);
        progressStrength.setProgressCompat(tier.getProgress(), true);

        int colorAttr = switch (tier) {
            case WEAK -> androidx.appcompat.R.attr.colorError;
            case FAIR -> com.google.android.material.R.attr.colorTertiary;
            case STRONG, VERY_STRONG -> androidx.appcompat.R.attr.colorPrimary;
        };
        progressStrength.setIndicatorColor(resolveThemeColor(colorAttr));
        tvEntropySummary.setText(getString(
                R.string.entropy_summary,
                (int) Math.round(entropyBits),
                tier.getLabel(),
                tier.getCrackEstimate()
        ));
    }

    private int resolveThemeColor(@AttrRes int attr) {
        TypedValue tv = new TypedValue();
        if (requireContext().getTheme().resolveAttribute(attr, tv, true)) {
            return tv.data;
        }
        return 0xFF3949AB;
    }

    private void addRecent(String secret) {
        recentHistory.remove(secret);
        recentHistory.add(0, secret);
        if (recentHistory.size() > MAX_RECENT) {
            recentHistory.remove(recentHistory.size() - 1);
        }
        renderRecentHistory();
    }

    private void renderRecentHistory() {
        if (!isAdded()) return;
        containerRecent.removeAllViews();
        if (recentHistory.isEmpty()) {
            cardRecent.setVisibility(View.GONE);
            return;
        }
        cardRecent.setVisibility(View.VISIBLE);
        int secondaryText = resolveThemeColor(com.google.android.material.R.attr.colorOnSurfaceVariant);

        for (String item : recentHistory) {
            TextView row = new TextView(requireContext());
            row.setText(item);
            row.setTypeface(android.graphics.Typeface.MONOSPACE);
            row.setTextSize(13f);
            row.setTextColor(secondaryText);
            row.setSingleLine(true);
            row.setEllipsize(android.text.TextUtils.TruncateAt.MIDDLE);
            row.setPadding(8, 16, 8, 16);
            row.setBackgroundTintList(ColorStateList.valueOf(0));

            TypedValue ripple = new TypedValue();
            if (requireContext().getTheme().resolveAttribute(android.R.attr.selectableItemBackground, ripple, true)) {
                row.setBackgroundResource(ripple.resourceId);
            }
            row.setOnClickListener(v -> copyValue(item));
            containerRecent.addView(row);
        }
    }

    private void copyValue(String value) {
        if (value == null || value.isEmpty() || !isAdded()) {
            Toast.makeText(getContext(), R.string.generate_first, Toast.LENGTH_SHORT).show();
            return;
        }
        Toast.makeText(getContext(), R.string.copied_to_clipboard, Toast.LENGTH_SHORT).show();
        clipboardHelper.copyWithAutoClear(requireContext(), "password", value, secondsLeft -> {
            if (!isAdded()) return;
            if (secondsLeft > 0) {
                tvClipboardStatus.setVisibility(View.VISIBLE);
                tvClipboardStatus.setText(getString(R.string.clipboard_countdown, secondsLeft));
            } else {
                tvClipboardStatus.setVisibility(View.GONE);
            }
        });
    }

    @Override
    public void onDestroyView() {
        super.onDestroyView();
        clipboardHelper.cancel();
        recentHistory.clear();
        currentSecret = "";
    }
}
