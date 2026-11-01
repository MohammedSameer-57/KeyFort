package org.keyfort.core;

import java.util.Arrays;
import java.util.Objects;

/**
 * Encrypted vault entry. Password remains encrypted under HKDF-derived entry key.
 */
public record Entry(
        String id,
        String site,
        String username,
        byte[] sealedPassword,
        int keyVersion
) {
    public Entry {
        Objects.requireNonNull(id, "id must not be null");
        Objects.requireNonNull(site, "site must not be null");
        Objects.requireNonNull(username, "username must not be null");
        Objects.requireNonNull(sealedPassword, "sealedPassword must not be null");
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof Entry entry)) return false;
        return keyVersion == entry.keyVersion
                && id.equals(entry.id)
                && site.equals(entry.site)
                && username.equals(entry.username)
                && Arrays.equals(sealedPassword, entry.sealedPassword);
    }

    @Override
    public int hashCode() {
        int result = Objects.hash(id, site, username, keyVersion);
        result = 31 * result + Arrays.hashCode(sealedPassword);
        return result;
    }
}
