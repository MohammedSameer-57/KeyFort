package org.keyfort.core.guestpass;

public interface GuestPassListener {
    void onCreated(GuestPass pass);
    void onIssued(GuestPass pass);
    void onRedeemed(GuestPass pass);
    void onConsumed(GuestPass pass);
    void onBurned(GuestPass pass);
    void onRevoked(GuestPass pass);
    void onExpired(GuestPass pass);

    GuestPassListener NOOP = new GuestPassListener() {
        @Override public void onCreated(GuestPass pass) {}
        @Override public void onIssued(GuestPass pass) {}
        @Override public void onRedeemed(GuestPass pass) {}
        @Override public void onConsumed(GuestPass pass) {}
        @Override public void onBurned(GuestPass pass) {}
        @Override public void onRevoked(GuestPass pass) {}
        @Override public void onExpired(GuestPass pass) {}
    };
}
