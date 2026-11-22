package org.keyfort.core.guestpass;

import java.util.Optional;

public interface GuestPassStore {
    Optional<GuestPass> findById(String id);
    void save(GuestPass pass);
    boolean compareAndSet(String id, long expectedVersion, GuestPass newPass);
}
