package org.keyfort.demosite.controller;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.servlet.http.HttpSession;
import org.keyfort.core.Crypto;
import org.keyfort.demosite.entity.GuestSessionEntity;
import org.keyfort.demosite.entity.TicketEntity;
import org.keyfort.demosite.service.TicketService;
import org.keyfort.demosite.service.TicketValidationException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseCookie;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.CookieValue;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseBody;

import java.time.Duration;
import java.time.Instant;
import java.util.Base64;
import java.util.Map;

@Controller
public class DemoLoginController {

    private static final Logger log = LoggerFactory.getLogger(DemoLoginController.class);
    private static final String REFERRER_POLICY_HEADER = "Referrer-Policy";
    private static final String NO_REFERRER = "no-referrer";

    private final TicketService ticketService;

    public DemoLoginController(TicketService ticketService) {
        this.ticketService = ticketService;
    }

    @GetMapping(value = "/keyfort/login", produces = MediaType.TEXT_HTML_VALUE)
    public ResponseEntity<String> keyfortLoginConfirmation(
            @RequestParam(name = "ticket", required = false) String ticket
    ) {
        if (ticket == null || ticket.isBlank()) {
            return ResponseEntity.status(HttpStatus.FOUND)
                    .header(HttpHeaders.LOCATION, "/login?error=missing_ticket")
                    .header(REFERRER_POLICY_HEADER, NO_REFERRER)
                    .build();
        }

        try {
            // Read-only validation: does not consume the ticket so prefetching or reloading does not burn it
            TicketEntity validTicket = ticketService.validateTicket(ticket);

            String html = "<!DOCTYPE html>\n" +
                    "<html lang=\"en\">\n" +
                    "<head>\n" +
                    "    <meta charset=\"UTF-8\">\n" +
                    "    <meta name=\"referrer\" content=\"no-referrer\">\n" +
                    "    <title>KeyFort Guest Access Confirmation</title>\n" +
                    "    <style>\n" +
                    "        body { font-family: -apple-system, BlinkMacSystemFont, \"Segoe UI\", Roboto, sans-serif; background: #0f172a; color: #f8fafc; display: flex; justify-content: center; align-items: center; min-height: 100vh; margin: 0; }\n" +
                    "        .card { background: #1e293b; border: 1px solid #334155; border-radius: 8px; padding: 28px; width: 360px; box-sizing: border-box; text-align: center; }\n" +
                    "        h2 { margin-top: 0; font-size: 18px; }\n" +
                    "        p { font-size: 14px; color: #94a3b8; }\n" +
                    "        .badge { display: inline-block; padding: 4px 8px; border-radius: 4px; font-size: 12px; font-weight: 600; background: #2563eb; color: #fff; margin: 8px 0 16px; }\n" +
                    "        button { width: 100%; padding: 10px; background: #2563eb; color: #fff; border: none; border-radius: 4px; cursor: pointer; font-weight: 600; font-size: 14px; }\n" +
                    "        button:hover { background: #1d4ed8; }\n" +
                    "    </style>\n" +
                    "</head>\n" +
                    "<body>\n" +
                    "    <div class=\"card\">\n" +
                    "        <h2>KeyFort Guest Access</h2>\n" +
                    "        <p>A single-use temporary guest session is ready for account: <strong>" + validTicket.getEntryId() + "</strong></p>\n" +
                    "        <div class=\"badge\">Scope: " + validTicket.getScope() + "</div>\n" +
                    "        <form action=\"/keyfort/login\" method=\"post\">\n" +
                    "            <input type=\"hidden\" name=\"ticket\" value=\"" + validTicket.getTicketToken() + "\">\n" +
                    "            <button type=\"submit\">Confirm and Enter Site</button>\n" +
                    "        </form>\n" +
                    "    </div>\n" +
                    "</body>\n" +
                    "</html>";

            return ResponseEntity.ok()
                    .header(REFERRER_POLICY_HEADER, NO_REFERRER)
                    .contentType(MediaType.TEXT_HTML)
                    .body(html);
        } catch (TicketValidationException e) {
            return ResponseEntity.status(HttpStatus.FOUND)
                    .header(HttpHeaders.LOCATION, "/login?error=invalid_ticket")
                    .header(REFERRER_POLICY_HEADER, NO_REFERRER)
                    .build();
        }
    }

    @PostMapping(value = "/keyfort/login")
    public ResponseEntity<Void> keyfortLoginConsume(
            @RequestParam(name = "ticket", required = false) String ticket,
            @CookieValue(name = "KEYFORT_DEVICE_ID", required = false) String deviceCookie,
            @RequestHeader(name = "User-Agent", required = false) String userAgent,
            HttpServletRequest request,
            HttpServletResponse response,
            HttpSession session
    ) {
        if (ticket == null || ticket.isBlank()) {
            return ResponseEntity.status(HttpStatus.FOUND)
                    .header(HttpHeaders.LOCATION, "/login?error=missing_ticket")
                    .header(REFERRER_POLICY_HEADER, NO_REFERRER)
                    .build();
        }

        try {
            String deviceId = deviceCookie;
            boolean setDeviceCookie = false;
            if (deviceId == null || deviceId.isBlank()) {
                deviceId = "dev_" + Base64.getUrlEncoder().withoutPadding().encodeToString(Crypto.random(16));
                setDeviceCookie = true;
            }

            GuestSessionEntity guestSession = ticketService.consumeTicketAndCreateSession(ticket, userAgent, deviceId);

            session.setAttribute("user", "guest-" + guestSession.getAccount());
            session.setAttribute("scope", guestSession.getScope());
            session.setAttribute("role", "GUEST_READONLY");
            session.setAttribute("guestExpiresAt", guestSession.getExpiresAt().toString());

            ResponseCookie sessionCookie = ResponseCookie.from("KEYFORT_GUEST_SESSION", guestSession.getId())
                    .httpOnly(true)
                    .sameSite("Strict")
                    .path("/")
                    .secure(request.isSecure())
                    .maxAge(Duration.ofMinutes(10).toSeconds())
                    .build();

            var respBuilder = ResponseEntity.status(HttpStatus.FOUND)
                    .header(HttpHeaders.LOCATION, "/dashboard")
                    .header(REFERRER_POLICY_HEADER, NO_REFERRER)
                    .header(HttpHeaders.SET_COOKIE, sessionCookie.toString());

            if (setDeviceCookie) {
                ResponseCookie devCookie = ResponseCookie.from("KEYFORT_DEVICE_ID", deviceId)
                        .httpOnly(true)
                        .sameSite("Strict")
                        .path("/")
                        .secure(request.isSecure())
                        .maxAge(Duration.ofDays(365).toSeconds())
                        .build();
                respBuilder.header(HttpHeaders.SET_COOKIE, devCookie.toString());
            }

            return respBuilder.build();
        } catch (TicketValidationException e) {
            return ResponseEntity.status(HttpStatus.FOUND)
                    .header(HttpHeaders.LOCATION, "/login?error=invalid_ticket")
                    .header(REFERRER_POLICY_HEADER, NO_REFERRER)
                    .build();
        }
    }

    @GetMapping(value = "/login", produces = MediaType.TEXT_HTML_VALUE)
    @ResponseBody
    public ResponseEntity<String> loginPage(
            @RequestParam(name = "error", required = false) String error,
            HttpServletResponse response
    ) {
        String errorMsg = "";
        if ("invalid_ticket".equals(error)) {
            errorMsg = "<div class='error'>Invalid or expired login ticket.</div>";
        } else if ("missing_ticket".equals(error)) {
            errorMsg = "<div class='error'>Missing ticket parameter.</div>";
        }

        String body = "<!DOCTYPE html>\n" +
                "<html lang=\"en\">\n" +
                "<head>\n" +
                "    <meta charset=\"UTF-8\">\n" +
                "    <meta name=\"referrer\" content=\"no-referrer\">\n" +
                "    <title>Demo Site - Login</title>\n" +
                "    <style>\n" +
                "        body { font-family: -apple-system, BlinkMacSystemFont, \"Segoe UI\", Roboto, sans-serif; background: #0f172a; color: #f8fafc; display: flex; justify-content: center; align-items: center; min-height: 100vh; margin: 0; }\n" +
                "        .card { background: #1e293b; border: 1px solid #334155; border-radius: 8px; padding: 28px; width: 340px; box-sizing: border-box; }\n" +
                "        h2 { margin-top: 0; font-size: 18px; }\n" +
                "        .error { background: #7f1d1d; color: #fca5a5; padding: 8px 12px; border-radius: 4px; font-size: 13px; margin-bottom: 12px; }\n" +
                "        input { width: 100%; box-sizing: border-box; padding: 8px 12px; margin: 8px 0 16px; background: #0f172a; border: 1px solid #475569; color: #fff; border-radius: 4px; }\n" +
                "        button { width: 100%; padding: 10px; background: #2563eb; color: #fff; border: none; border-radius: 4px; cursor: pointer; font-weight: 600; }\n" +
                "    </style>\n" +
                "</head>\n" +
                "<body>\n" +
                "    <div class=\"card\">\n" +
                "        <h2>Target Service Login</h2>\n" +
                "        " + errorMsg + "\n" +
                "        <form action=\"/login\" method=\"post\">\n" +
                "            <label>Username</label>\n" +
                "            <input type=\"text\" name=\"username\" value=\"alice\" required>\n" +
                "            <label>Password</label>\n" +
                "            <input type=\"password\" name=\"password\" value=\"••••••••\" required>\n" +
                "            <button type=\"submit\">Sign In</button>\n" +
                "        </form>\n" +
                "    </div>\n" +
                "</body>\n" +
                "</html>";

        return ResponseEntity.ok()
                .header(REFERRER_POLICY_HEADER, NO_REFERRER)
                .contentType(MediaType.TEXT_HTML)
                .body(body);
    }

    @PostMapping("/login")
    public ResponseEntity<Void> handleLogin(
            @RequestParam(name = "username") String username,
            @RequestParam(name = "password") String password,
            HttpSession session
    ) {
        session.setAttribute("user", username);
        session.setAttribute("role", "OWNER");
        session.setAttribute("scope", "full");
        return ResponseEntity.status(HttpStatus.FOUND)
                .header(HttpHeaders.LOCATION, "/dashboard")
                .header(REFERRER_POLICY_HEADER, NO_REFERRER)
                .build();
    }

    @GetMapping(value = "/dashboard", produces = MediaType.TEXT_HTML_VALUE)
    @ResponseBody
    public ResponseEntity<String> dashboard(HttpSession session) {
        String user = (String) session.getAttribute("user");
        if (user == null) {
            return ResponseEntity.status(HttpStatus.FOUND)
                    .header(HttpHeaders.LOCATION, "/login")
                    .header(REFERRER_POLICY_HEADER, NO_REFERRER)
                    .build();
        }
        String role = String.valueOf(session.getAttribute("role"));
        String scope = String.valueOf(session.getAttribute("scope"));
        String expires = (String) session.getAttribute("guestExpiresAt");

        String expiresHtml = expires != null ? "<p>Guest Expiry: <code>" + expires + "</code></p>" : "";

        String body = "<!DOCTYPE html>\n" +
                "<html lang=\"en\">\n" +
                "<head>\n" +
                "    <meta charset=\"UTF-8\">\n" +
                "    <meta name=\"referrer\" content=\"no-referrer\">\n" +
                "    <title>Demo Site - Dashboard</title>\n" +
                "    <style>\n" +
                "        body { font-family: -apple-system, BlinkMacSystemFont, \"Segoe UI\", Roboto, sans-serif; background: #0f172a; color: #f8fafc; padding: 32px; }\n" +
                "        .card { background: #1e293b; border: 1px solid #334155; border-radius: 8px; padding: 24px; max-width: 500px; }\n" +
                "        .badge { display: inline-block; padding: 4px 8px; border-radius: 4px; font-size: 12px; font-weight: 600; background: #3b82f6; color: #fff; }\n" +
                "    </style>\n" +
                "</head>\n" +
                "<body>\n" +
                "    <div class=\"card\">\n" +
                "        <h2>Account Session Active</h2>\n" +
                "        <p>Logged in as: <strong>" + user + "</strong></p>\n" +
                "        <p>Role: <span class=\"badge\">" + role + "</span></p>\n" +
                "        <p>Scope: <code>" + scope + "</code></p>\n" +
                "        " + expiresHtml + "\n" +
                "        <a href=\"/login\" style=\"color: #60a5fa; text-decoration: none;\">Log Out</a>\n" +
                "    </div>\n" +
                "</body>\n" +
                "</html>";

        return ResponseEntity.ok()
                .header(REFERRER_POLICY_HEADER, NO_REFERRER)
                .contentType(MediaType.TEXT_HTML)
                .body(body);
    }

    @GetMapping("/api/session")
    @ResponseBody
    public ResponseEntity<Map<String, Object>> sessionInfo(HttpSession session) {
        String user = (String) session.getAttribute("user");
        if (user == null) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).build();
        }
        return ResponseEntity.ok()
                .header(REFERRER_POLICY_HEADER, NO_REFERRER)
                .body(Map.of(
                        "user", user,
                        "role", session.getAttribute("role"),
                        "scope", session.getAttribute("scope")
                ));
    }

    @GetMapping(value = "/account/settings", produces = MediaType.APPLICATION_JSON_VALUE)
    @ResponseBody
    public ResponseEntity<Map<String, String>> getAccountSettings() {
        return ResponseEntity.ok(Map.of("status", "ok", "role", "READ_ONLY"));
    }

    @PostMapping(value = "/account/settings", produces = MediaType.APPLICATION_JSON_VALUE)
    @ResponseBody
    public ResponseEntity<Map<String, String>> updateAccountSettings() {
        return ResponseEntity.ok(Map.of("status", "updated"));
    }

    @PostMapping(value = "/account/transfer", produces = MediaType.APPLICATION_JSON_VALUE)
    @ResponseBody
    public ResponseEntity<Map<String, String>> transferAccountFunds() {
        return ResponseEntity.ok(Map.of("status", "transferred"));
    }
}
