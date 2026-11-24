package org.keyfort.server.orchestration.controller;

import org.keyfort.server.orchestration.dto.DemoStateDto;
import org.keyfort.server.orchestration.service.DemoRunnerService;
import org.springframework.context.annotation.Profile;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.util.Map;

/**
 * REST controller for interactive and automated demo workflows.
 * Exists exclusively under the "demo" profile.
 */
@RestController
@RequestMapping("/api/demo")
@Profile("demo")
public class DemoRunnerController {

    private final DemoRunnerService demoRunnerService;

    public DemoRunnerController(DemoRunnerService demoRunnerService) {
        this.demoRunnerService = demoRunnerService;
    }

    @GetMapping("/state")
    public DemoStateDto getState() {
        return demoRunnerService.getState();
    }

    @PostMapping("/unlock")
    public DemoStateDto unlock(@RequestBody(required = false) Map<String, String> request) {
        String masterKey = request != null ? request.get("masterKey") : null;
        return demoRunnerService.unlock(masterKey);
    }

    @PostMapping("/lock")
    public DemoStateDto lock() {
        return demoRunnerService.lock();
    }

    @PostMapping("/create-link")
    public DemoStateDto createLink(@RequestBody(required = false) Map<String, Integer> request) {
        int durationMinutes = 10;
        if (request != null && request.containsKey("durationMinutes")) {
            durationMinutes = request.get("durationMinutes");
        }
        return demoRunnerService.createLink(durationMinutes);
    }

    @PostMapping("/friend-login")
    public DemoStateDto friendLogin() {
        return demoRunnerService.friendLogin();
    }

    @PostMapping("/copier-try")
    public DemoStateDto copierTry() {
        return demoRunnerService.copierTry();
    }

    @PostMapping("/end-session")
    public DemoStateDto endSession() {
        return demoRunnerService.endSession();
    }

    @PostMapping("/renew-key")
    public DemoStateDto renewKey() {
        return demoRunnerService.renewKey();
    }

    @PostMapping("/reset")
    public DemoStateDto reset() {
        return demoRunnerService.reset();
    }

    @GetMapping("/check-history")
    public org.keyfort.server.dto.LedgerVerificationResponse checkHistory() {
        return demoRunnerService.checkHistory();
    }

    @GetMapping(value = "/stream", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    public SseEmitter stream() {
        return demoRunnerService.createEmitter();
    }

    @ExceptionHandler(DemoRunnerService.VaultLockedException.class)
    public ResponseEntity<Map<String, String>> handleVaultLocked(DemoRunnerService.VaultLockedException ex) {
        return ResponseEntity.status(HttpStatus.CONFLICT).body(Map.of("error", "vault locked"));
    }

    @ExceptionHandler(DemoRunnerService.WrongKeyException.class)
    public ResponseEntity<Map<String, String>> handleWrongKey(DemoRunnerService.WrongKeyException ex) {
        return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body(Map.of("error", "wrong key"));
    }

    @ExceptionHandler(DemoRunnerService.LockoutException.class)
    public ResponseEntity<Map<String, String>> handleLockout(DemoRunnerService.LockoutException ex) {
        return ResponseEntity.status(HttpStatus.TOO_MANY_REQUESTS).body(Map.of("error", "locked out"));
    }

    @ExceptionHandler(ResponseStatusException.class)
    public ResponseEntity<Map<String, String>> handleResponseStatus(ResponseStatusException ex) {
        return ResponseEntity.status(ex.getStatusCode())
                .body(Map.of("error", ex.getReason() != null ? ex.getReason() : ""));
    }
}
