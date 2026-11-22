package org.keyfort.core.guestpass;

public class IllegalTransitionException extends GuestPassException {
    public IllegalTransitionException(String message) {
        super(message);
    }
}
