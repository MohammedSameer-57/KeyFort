package org.keyfort.android.ui;

import org.junit.Test;

import static org.junit.Assert.*;

public class GuestPassFlowTest {

    @Test
    public void testStateResolutionLifecycle() {
        // Initial state before friend clicks link
        GuestPassStateResolver.DisplayState waiting =
                GuestPassStateResolver.resolve("ACTIVE", false);
        assertEquals(GuestPassStateResolver.DisplayState.WAITING_FOR_FRIEND, waiting);
        assertEquals("Waiting for friend", waiting.getLabel());
        assertFalse(waiting.canEndSession());
        assertTrue(waiting.shouldContinuePolling());

        // Friend has redeemed and webhook created active session
        GuestPassStateResolver.DisplayState loggedIn =
                GuestPassStateResolver.resolve("CONSUMED", true);
        assertEquals(GuestPassStateResolver.DisplayState.FRIEND_LOGGED_IN, loggedIn);
        assertEquals("Friend logged in", loggedIn.getLabel());
        assertTrue(loggedIn.canEndSession());
        assertTrue(loggedIn.shouldContinuePolling());

        // Pass burned and session terminated
        GuestPassStateResolver.DisplayState burned =
                GuestPassStateResolver.resolve("BURNED", false);
        assertEquals(GuestPassStateResolver.DisplayState.LINK_USED, burned);
        assertEquals("Link used", burned.getLabel());
        assertFalse(burned.canEndSession());
        assertFalse(burned.shouldContinuePolling());

        // Expired pass
        GuestPassStateResolver.DisplayState expired =
                GuestPassStateResolver.resolve("EXPIRED", false);
        assertEquals(GuestPassStateResolver.DisplayState.EXPIRED, expired);
        assertEquals("Expired", expired.getLabel());
        assertFalse(expired.canEndSession());
        assertFalse(expired.shouldContinuePolling());

        // Revoked pass
        GuestPassStateResolver.DisplayState revoked =
                GuestPassStateResolver.resolve("REVOKED", false);
        assertEquals(GuestPassStateResolver.DisplayState.EXPIRED, revoked);
        assertFalse(revoked.shouldContinuePolling());
    }

    @Test
    public void testBuildRedeemUrl() {
        assertEquals("http://10.0.2.2:8080/g/tok_abc123",
                GuestPassStateResolver.buildRedeemUrl("http://10.0.2.2:8080", "tok_abc123"));

        assertEquals("http://10.0.2.2:8080/g/tok_abc123",
                GuestPassStateResolver.buildRedeemUrl("http://10.0.2.2:8080/", "tok_abc123"));
    }

    @Test
    public void testDurationToSeconds() {
        assertEquals(300, GuestPassStateResolver.durationToSeconds(5));
        assertEquals(900, GuestPassStateResolver.durationToSeconds(15));
        assertEquals(3600, GuestPassStateResolver.durationToSeconds(60));
        assertEquals(900, GuestPassStateResolver.durationToSeconds(0));
    }
}
