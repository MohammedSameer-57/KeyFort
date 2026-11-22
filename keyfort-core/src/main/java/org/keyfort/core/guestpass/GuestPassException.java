package org.keyfort.core.guestpass;

public class GuestPassException extends RuntimeException {
    public GuestPassException(String message) {
        super(message);
    }

    public GuestPassException(String message, Throwable cause) {
        super(message, cause);
    }
}
