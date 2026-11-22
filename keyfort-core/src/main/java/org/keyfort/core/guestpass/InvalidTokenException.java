package org.keyfort.core.guestpass;

public class InvalidTokenException extends GuestPassException {
    public InvalidTokenException(String message) {
        super(message);
    }
}
