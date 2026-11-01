package org.keyfort.server.dto;

import org.keyfort.core.risk.Action;
import java.util.List;

public record DecisionResponse(
        Action action,
        int score,
        boolean coordinatedAttack,
        List<String> reasons
) {}
