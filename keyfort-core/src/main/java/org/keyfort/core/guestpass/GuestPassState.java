package org.keyfort.core.guestpass;

public enum GuestPassState {
    CREATED,
    ISSUED,
    REDEEMED,
    CONSUMED,
    BURNED,
    EXPIRED,
    REVOKED;

    public boolean isTerminal() {
        return this == BURNED || this == EXPIRED || this == REVOKED;
    }
}
