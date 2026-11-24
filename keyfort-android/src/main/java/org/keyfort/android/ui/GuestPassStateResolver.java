package org.keyfort.android.ui;

public class GuestPassStateResolver {

    public enum DisplayState {
        WAITING_FOR_FRIEND("Waiting for friend", false, true),
        FRIEND_LOGGED_IN("Friend logged in", true, true),
        LINK_USED("Link used", false, false),
        EXPIRED("Expired", false, false);

        private final String label;
        private final boolean canEndSession;
        private final boolean shouldContinuePolling;

        DisplayState(String label, boolean canEndSession, boolean shouldContinuePolling) {
            this.label = label;
            this.canEndSession = canEndSession;
            this.shouldContinuePolling = shouldContinuePolling;
        }

        public String getLabel() {
            return label;
        }

        public boolean canEndSession() {
            return canEndSession;
        }

        public boolean shouldContinuePolling() {
            return shouldContinuePolling;
        }
    }

    public static DisplayState resolve(String passState, boolean hasActiveSession) {
        if (hasActiveSession || "CONSUMED".equalsIgnoreCase(passState)) {
            return DisplayState.FRIEND_LOGGED_IN;
        }
        if ("BURNED".equalsIgnoreCase(passState)) {
            return DisplayState.LINK_USED;
        }
        if ("EXPIRED".equalsIgnoreCase(passState) || "REVOKED".equalsIgnoreCase(passState)) {
            return DisplayState.EXPIRED;
        }
        return DisplayState.WAITING_FOR_FRIEND;
    }

    public static String buildRedeemUrl(String baseUrl, String rawToken) {
        if (baseUrl == null || rawToken == null) {
            throw new IllegalArgumentException("baseUrl and rawToken must not be null");
        }
        String cleanBase = baseUrl.endsWith("/") ? baseUrl.substring(0, baseUrl.length() - 1) : baseUrl;
        return cleanBase + "/g/" + rawToken;
    }

    public static int durationToSeconds(int durationMinutes) {
        if (durationMinutes <= 0) return 900; // default 15 min
        return durationMinutes * 60;
    }
}
