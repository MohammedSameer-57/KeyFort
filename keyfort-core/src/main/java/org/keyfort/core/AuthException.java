package org.keyfort.core;

/**
 * Thrown when vault authentication, unwrap, or cryptographic integrity check fails.
 */
public final class AuthException extends Exception {
    public AuthException(String message) {
        super(message);
    }

    public AuthException(String message, Throwable cause) {
        super(message, cause);
    }
}
