package org.keyfort.android.generator;

import java.security.SecureRandom;

public class PasswordGenerator {

    private static final String UPPER = "ABCDEFGHIJKLMNOPQRSTUVWXYZ";
    private static final String LOWER = "abcdefghijklmnopqrstuvwxyz";
    private static final String DIGITS = "0123456789";
    private static final String SYMBOLS = "!@#$%^&*()-_=+[]{}|;:,.<>?";

    private static final SecureRandom RANDOM = new SecureRandom();

    /**
     * Generates a secure random password.
     *
     * @param length  desired password length (minimum 8)
     * @param upper   include uppercase letters
     * @param lower   include lowercase letters
     * @param digits  include digits
     * @param symbols include symbols
     * @return generated password string
     */
    public static String generate(int length, boolean upper, boolean lower,
                                  boolean digits, boolean symbols) {
        if (length < 8) length = 8;

        StringBuilder charPool = new StringBuilder();
        StringBuilder guaranteed = new StringBuilder();

        if (upper) {
            charPool.append(UPPER);
            guaranteed.append(UPPER.charAt(RANDOM.nextInt(UPPER.length())));
        }
        if (lower) {
            charPool.append(LOWER);
            guaranteed.append(LOWER.charAt(RANDOM.nextInt(LOWER.length())));
        }
        if (digits) {
            charPool.append(DIGITS);
            guaranteed.append(DIGITS.charAt(RANDOM.nextInt(DIGITS.length())));
        }
        if (symbols) {
            charPool.append(SYMBOLS);
            guaranteed.append(SYMBOLS.charAt(RANDOM.nextInt(SYMBOLS.length())));
        }

        // Fallback: if no charset selected, use alphanumeric
        if (charPool.length() == 0) {
            charPool.append(UPPER).append(LOWER).append(DIGITS);
        }

        String pool = charPool.toString();
        StringBuilder sb = new StringBuilder();

        // Fill remaining characters randomly
        int remaining = length - guaranteed.length();
        for (int i = 0; i < remaining; i++) {
            sb.append(pool.charAt(RANDOM.nextInt(pool.length())));
        }

        // Combine guaranteed + random, then shuffle
        sb.append(guaranteed);
        char[] result = sb.toString().toCharArray();
        for (int i = result.length - 1; i > 0; i--) {
            int j = RANDOM.nextInt(i + 1);
            char tmp = result[i];
            result[i] = result[j];
            result[j] = tmp;
        }
        return new String(result);
    }

    /** Default generation: length=20, all charsets. */
    public static String generateDefault() {
        return generate(20, true, true, true, true);
    }
}
