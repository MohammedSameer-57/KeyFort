package org.keyfort.core.risk;

import java.util.List;
import java.util.Objects;

/**
 * Result of the risk assessment engine.
 */
public record Decision(
        Action action,
        int score,
        boolean coordinatedAttack,
        List<String> reasons
) {
    public Decision {
        Objects.requireNonNull(action, "action must not be null");
        reasons = reasons == null ? List.of() : List.copyOf(reasons);
    }
}
