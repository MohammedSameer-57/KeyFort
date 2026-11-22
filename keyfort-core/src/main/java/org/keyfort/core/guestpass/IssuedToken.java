package org.keyfort.core.guestpass;

public record IssuedToken(String id, String rawToken, GuestPass pass) {
    public IssuedToken {
        if (id == null || rawToken == null || pass == null) {
            throw new IllegalArgumentException("IssuedToken fields must not be null");
        }
    }

    @Override
    public String toString() {
        return "IssuedToken[id=" + id + ", rawToken=" + id + ".[MASKED], pass=" + pass + "]";
    }
}
