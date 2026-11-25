package org.keyfort.android.generator;

import java.security.SecureRandom;

/**
 * Cryptographically strong password and diceware-style passphrase generator
 * with Shannon/combinatorial entropy estimation.
 */
public class PasswordGenerator {

    private static final String UPPER = "ABCDEFGHIJKLMNOPQRSTUVWXYZ";
    private static final String LOWER = "abcdefghijklmnopqrstuvwxyz";
    private static final String DIGITS = "0123456789";
    private static final String SYMBOLS = "!@#$%^&*()-_=+[]{}|;:,.<>?";
    private static final String AMBIGUOUS = "O0Il1|";

    private static final SecureRandom RANDOM = new SecureRandom();

    /**
     * Curated 256-word vocabulary (exactly 8.0 bits of entropy per word).
     */
    static final String[] WORDLIST = {
            "acorn", "anchor", "apple", "apron", "arbor", "arrow", "aspen", "atlas",
            "autumn", "badge", "baker", "bamboo", "banjo", "basin", "beacon", "beech",
            "berry", "birch", "bison", "blade", "blaze", "bloom", "board", "boulder",
            "branch", "brass", "bread", "breeze", "brick", "bridge", "bright", "brook",
            "brush", "bucket", "bugle", "cabin", "cable", "cactus", "camel", "canal",
            "candle", "cannon", "canyon", "carbon", "cargo", "carpet", "castle", "cedar",
            "cello", "chalk", "charm", "chess", "chime", "cider", "cipher", "circle",
            "citadel", "clamor", "cliff", "clock", "cloud", "clover", "cobalt", "cobble",
            "coffee", "comet", "compass", "copper", "coral", "corner", "cotton", "cougar",
            "coyote", "cradle", "craft", "crane", "creek", "crest", "cricket", "crimson",
            "crown", "crystal", "current", "cypress", "dagger", "daisy", "dancer", "dawn",
            "delta", "desert", "diamond", "dolphin", "domino", "dragon", "drift", "drum",
            "eagle", "echo", "eclipse", "ember", "emerald", "engine", "falcon", "feather",
            "fiddle", "field", "figure", "finch", "fjord", "flame", "flint", "flora",
            "forest", "forge", "fossil", "fountain", "frost", "galaxy", "garden", "garnet",
            "gazelle", "geyser", "glacier", "glider", "globe", "granite", "grape", "gravel",
            "groove", "harbor", "harvest", "haven", "hazel", "hearth", "helmet", "heron",
            "hickory", "hollow", "honey", "horizon", "hornet", "hound", "hunter", "icicle",
            "indigo", "island", "ivory", "jacket", "jaguar", "jasper", "jester", "juniper",
            "kayak", "keeper", "kelp", "kernel", "kettle", "kingdom", "kite", "knight",
            "lagoon", "lantern", "larch", "lark", "laurel", "lava", "lemon", "leopard",
            "lichen", "lighthouse", "lilac", "linden", "linen", "lizard", "lotus", "lumber",
            "magnet", "magpie", "maple", "marble", "marina", "meadow", "melon", "mercury",
            "meteor", "mellow", "mimosa", "mineral", "mirror", "mitten", "monarch", "monsoon",
            "mosaic", "mountain", "mulberry", "mustang", "nebula", "nectar", "needle", "nickel",
            "nimbus", "nomad", "noodle", "north", "nutmeg", "oasis", "obsidian", "ocean",
            "olive", "onyx", "opal", "orange", "orbit", "orchid", "oriole", "osprey",
            "otter", "owl", "paddle", "palace", "palms", "panther", "paper", "parcel",
            "parrot", "pebble", "pelican", "pencil", "pepper", "peony", "petal", "pewter",
            "phoenix", "piano", "picnic", "pigeon", "pillow", "pine", "pioneer", "planet",
            "plaza", "plume", "pocket", "poem", "pollen", "ponder", "poplar", "portal",
            "potion", "prairie", "prism", "proton", "puffin", "pulse", "pumpkin", "quartz"
    };

    public enum StrengthTier {
        WEAK("Weak", "Instantly to minutes", 25),
        FAIR("Fair", "Hours to days", 50),
        STRONG("Strong", "Years to decades", 75),
        VERY_STRONG("Very Strong", "Centuries+", 100);

        private final String label;
        private final String crackEstimate;
        private final int progress;

        StrengthTier(String label, String crackEstimate, int progress) {
            this.label = label;
            this.crackEstimate = crackEstimate;
            this.progress = progress;
        }

        public String getLabel() { return label; }
        public String getCrackEstimate() { return crackEstimate; }
        public int getProgress() { return progress; }
    }

    public static String generate(int length, boolean upper, boolean lower,
                                  boolean digits, boolean symbols) {
        return generate(length, upper, lower, digits, symbols, false);
    }

    public static String generate(int length, boolean upper, boolean lower,
                                  boolean digits, boolean symbols, boolean avoidAmbiguous) {
        if (length < 8) length = 8;

        String upperPool = filterAmbiguous(UPPER, avoidAmbiguous);
        String lowerPool = filterAmbiguous(LOWER, avoidAmbiguous);
        String digitPool = filterAmbiguous(DIGITS, avoidAmbiguous);
        String symbolPool = filterAmbiguous(SYMBOLS, avoidAmbiguous);

        StringBuilder charPool = new StringBuilder();
        StringBuilder guaranteed = new StringBuilder();

        if (upper && !upperPool.isEmpty()) {
            charPool.append(upperPool);
            guaranteed.append(upperPool.charAt(RANDOM.nextInt(upperPool.length())));
        }
        if (lower && !lowerPool.isEmpty()) {
            charPool.append(lowerPool);
            guaranteed.append(lowerPool.charAt(RANDOM.nextInt(lowerPool.length())));
        }
        if (digits && !digitPool.isEmpty()) {
            charPool.append(digitPool);
            guaranteed.append(digitPool.charAt(RANDOM.nextInt(digitPool.length())));
        }
        if (symbols && !symbolPool.isEmpty()) {
            charPool.append(symbolPool);
            guaranteed.append(symbolPool.charAt(RANDOM.nextInt(symbolPool.length())));
        }

        if (charPool.length() == 0) {
            charPool.append(upperPool).append(lowerPool).append(digitPool);
        }

        String pool = charPool.toString();
        StringBuilder sb = new StringBuilder();

        int remaining = Math.max(0, length - guaranteed.length());
        for (int i = 0; i < remaining; i++) {
            sb.append(pool.charAt(RANDOM.nextInt(pool.length())));
        }

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
        return generate(20, true, true, true, true, false);
    }

    /**
     * Generates a random diceware-style passphrase from the built-in 256-word list.
     */
    public static String generatePassphrase(int wordCount, String separator,
                                            boolean capitalize, boolean includeNumber) {
        int count = Math.max(3, Math.min(10, wordCount));
        String sep = separator != null ? separator : "-";
        int numberWordIdx = includeNumber ? RANDOM.nextInt(count) : -1;

        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < count; i++) {
            if (i > 0) {
                sb.append(sep);
            }
            String word = WORDLIST[RANDOM.nextInt(WORDLIST.length)];
            if (capitalize && !word.isEmpty()) {
                word = Character.toUpperCase(word.charAt(0)) + word.substring(1);
            }
            sb.append(word);
            if (i == numberWordIdx) {
                sb.append(RANDOM.nextInt(10));
            }
        }
        return sb.toString();
    }

    public static double calculatePasswordEntropyBits(int length, boolean upper, boolean lower,
                                                      boolean digits, boolean symbols, boolean avoidAmbiguous) {
        int pool = 0;
        if (upper) pool += filterAmbiguous(UPPER, avoidAmbiguous).length();
        if (lower) pool += filterAmbiguous(LOWER, avoidAmbiguous).length();
        if (digits) pool += filterAmbiguous(DIGITS, avoidAmbiguous).length();
        if (symbols) pool += filterAmbiguous(SYMBOLS, avoidAmbiguous).length();
        if (pool <= 1) {
            pool = filterAmbiguous(UPPER + LOWER + DIGITS, avoidAmbiguous).length();
        }
        return Math.max(8, length) * (Math.log(pool) / Math.log(2.0));
    }

    public static double calculatePassphraseEntropyBits(int wordCount, boolean includeNumber) {
        int count = Math.max(3, Math.min(10, wordCount));
        double bits = count * (Math.log(WORDLIST.length) / Math.log(2.0));
        if (includeNumber) {
            bits += Math.log(10.0 * count) / Math.log(2.0);
        }
        return bits;
    }

    public static StrengthTier assessStrength(double entropyBits) {
        if (entropyBits < 40.0) return StrengthTier.WEAK;
        if (entropyBits < 60.0) return StrengthTier.FAIR;
        if (entropyBits < 80.0) return StrengthTier.STRONG;
        return StrengthTier.VERY_STRONG;
    }

    private static String filterAmbiguous(String source, boolean avoidAmbiguous) {
        if (!avoidAmbiguous) return source;
        StringBuilder sb = new StringBuilder(source.length());
        for (int i = 0; i < source.length(); i++) {
            char c = source.charAt(i);
            if (AMBIGUOUS.indexOf(c) == -1) {
                sb.append(c);
            }
        }
        return sb.toString();
    }
}
