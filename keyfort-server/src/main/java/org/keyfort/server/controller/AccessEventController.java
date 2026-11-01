package org.keyfort.server.controller;

import jakarta.validation.Valid;
import org.keyfort.server.dto.AccessEventRequest;
import org.keyfort.server.dto.DecisionResponse;
import org.keyfort.server.service.RiskAssessmentService;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/access-events")
public class AccessEventController {

    private final RiskAssessmentService riskService;

    public AccessEventController(RiskAssessmentService riskService) {
        this.riskService = riskService;
    }

    @PostMapping
    public ResponseEntity<DecisionResponse> reportAccessEvent(@Valid @RequestBody AccessEventRequest req) {
        DecisionResponse decision = riskService.assess(req);
        return ResponseEntity.ok(decision);
    }
}
