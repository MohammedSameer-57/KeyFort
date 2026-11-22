package org.keyfort.demosite.service;

public class TicketValidationException extends RuntimeException {
    public TicketValidationException(String message) {
        super(message);
    }
}
