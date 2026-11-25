package org.keyfort.android;

import org.junit.Test;
import org.keyfort.android.generator.PasswordGenerator;
import org.keyfort.android.send.SendEnvelope;
import org.keyfort.core.Crypto;

import java.nio.charset.StandardCharsets;
import java.util.Base64;
import java.util.HashSet;
import java.util.Set;

import javax.crypto.AEADBadTagException;

import static org.junit.Assert.*;

public class GeneratorAndSendTest {

    @Test
    public void testAvoidAmbiguousCharacters() {
        for (int i = 0; i < 20; i++) {
            String pwd = PasswordGenerator.generate(32, true, true, true, true, true);
            assertEquals(32, pwd.length());
            for (char bad : new char[]{'O', '0', 'I', 'l', '1', '|'}) {
                assertEquals("Should not contain ambiguous char " + bad, -1, pwd.indexOf(bad));
            }
        }
    }

    @Test
    public void testPassphraseGenerationAndWordlistUniqueness() {
        String phrase = PasswordGenerator.generatePassphrase(6, "-", true, true);
        String[] parts = phrase.split("-");
        assertEquals(6, parts.length);

        try {
            java.lang.reflect.Field f = PasswordGenerator.class.getDeclaredField("WORDLIST");
            f.setAccessible(true);
            String[] words = (String[]) f.get(null);
            assertEquals(256, words.length);
            Set<String> unique = new HashSet<>(java.util.Arrays.asList(words));
            assertEquals("All 256 words in WORDLIST must be unique", 256, unique.size());
        } catch (Exception e) {
            fail(e.getMessage());
        }

        boolean hasDigit = false;
        for (String w : parts) {
            assertTrue("Should be capitalized: " + w, Character.isUpperCase(w.charAt(0)));
            if (Character.isDigit(w.charAt(w.length() - 1))) {
                hasDigit = true;
            }
        }
        assertTrue("Passphrase should include one random digit", hasDigit);

        double bits = PasswordGenerator.calculatePassphraseEntropyBits(6, true);
        assertTrue(bits > 50.0);
        assertEquals(PasswordGenerator.StrengthTier.FAIR, PasswordGenerator.assessStrength(bits));
        assertEquals(PasswordGenerator.StrengthTier.VERY_STRONG,
                PasswordGenerator.assessStrength(PasswordGenerator.calculatePasswordEntropyBits(20, true, true, true, true, false)));
    }

    @Test
    public void testSendEnvelopeRoundTripWithoutPin() throws Exception {
        long now = 1_700_000_000L;
        String bundle = SendEnvelope.create("wifi-secret-99", 3600L, null, now);
        assertTrue(bundle.startsWith("keyfort-send://v2."));

        SendEnvelope.Inspection info = SendEnvelope.inspect(bundle);
        assertTrue(info.valid());
        assertEquals(2, info.version());
        assertFalse(info.requiresPasscode());

        SendEnvelope.Decrypted out = SendEnvelope.open(bundle, null, now + 120L);
        assertEquals("wifi-secret-99", out.text());
        assertEquals(now + 3600L, out.expiresAtEpochSeconds());
        assertFalse(out.passcodeProtected());
    }

    @Test
    public void testSendEnvelopePasscodeAndExpiryEnforcement() throws Exception {
        long now = 1_700_000_000L;
        String bundle = SendEnvelope.create("top-secret-pin", 900L, "pin1234".toCharArray(), now);
        assertTrue(bundle.startsWith("keyfort-send://v2p."));

        SendEnvelope.Inspection info = SendEnvelope.inspect(bundle);
        assertTrue(info.valid());
        assertTrue(info.requiresPasscode());

        try {
            SendEnvelope.open(bundle, null, now + 10L);
            fail("Expected PasscodeRequiredException");
        } catch (SendEnvelope.PasscodeRequiredException expected) {
            // ok
        }

        try {
            SendEnvelope.open(bundle, "wrongpin".toCharArray(), now + 10L);
            fail("Expected AEADBadTagException on wrong passcode");
        } catch (AEADBadTagException expected) {
            // ok
        }

        SendEnvelope.Decrypted ok = SendEnvelope.open(bundle, "pin1234".toCharArray(), now + 800L);
        assertEquals("top-secret-pin", ok.text());
        assertTrue(ok.passcodeProtected());

        try {
            SendEnvelope.open(bundle, "pin1234".toCharArray(), now + 901L);
            fail("Expected ExpiredException after TTL");
        } catch (SendEnvelope.ExpiredException expected) {
            // ok
        }
    }

    @Test
    public void testLegacyV1SendCompatibility() throws Exception {
        byte[] key = Crypto.random(32);
        byte[] ct = Crypto.seal(key, "legacy-secret".getBytes(StandardCharsets.UTF_8), "keyfort|send|v1".getBytes(StandardCharsets.UTF_8));
        Base64.Encoder enc = Base64.getUrlEncoder().withoutPadding();
        String v1Bundle = "keyfort-send://" + enc.encodeToString(ct) + "#" + enc.encodeToString(key);

        SendEnvelope.Inspection info = SendEnvelope.inspect(v1Bundle);
        assertTrue(info.valid());
        assertEquals(1, info.version());
        assertFalse(info.requiresPasscode());

        SendEnvelope.Decrypted out = SendEnvelope.open(v1Bundle, null, 1_700_000_000L);
        assertEquals("legacy-secret", out.text());
        assertEquals(1, out.version());
    }
}
