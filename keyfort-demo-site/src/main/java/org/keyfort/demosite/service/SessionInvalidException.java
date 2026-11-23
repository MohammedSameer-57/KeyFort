package org.keyfort.demosite.service;

public class SessionInvalidException extends RuntimeException {
    public SessionInvalidException(String message) {
        super(message);
    }
}
