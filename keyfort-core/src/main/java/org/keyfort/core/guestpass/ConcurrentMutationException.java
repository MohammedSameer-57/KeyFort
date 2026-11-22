package org.keyfort.core.guestpass;

public class ConcurrentMutationException extends GuestPassException {
    public ConcurrentMutationException(String message) {
        super(message);
    }
}
