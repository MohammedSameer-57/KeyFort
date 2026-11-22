package org.keyfort.core.guestpass;

import java.util.Objects;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

public class InMemoryGuestPassStore implements GuestPassStore {
    private final ConcurrentHashMap<String, GuestPass> store = new ConcurrentHashMap<>();

    @Override
    public Optional<GuestPass> findById(String id) {
        return Optional.ofNullable(store.get(id));
    }

    @Override
    public void save(GuestPass pass) {
        Objects.requireNonNull(pass, "pass must not be null");
        GuestPass existing = store.putIfAbsent(pass.id(), pass);
        if (existing != null) {
            throw new IllegalStateException("GuestPass already exists with id: " + pass.id());
        }
    }

    @Override
    public boolean compareAndSet(String id, long expectedVersion, GuestPass newPass) {
        Objects.requireNonNull(id, "id must not be null");
        Objects.requireNonNull(newPass, "newPass must not be null");
        boolean[] updated = new boolean[1];
        store.compute(id, (k, current) -> {
            if (current != null && current.version() == expectedVersion) {
                updated[0] = true;
                return newPass;
            }
            return current;
        });
        return updated[0];
    }
}
