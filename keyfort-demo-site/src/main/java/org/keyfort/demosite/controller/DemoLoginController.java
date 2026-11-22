package org.keyfort.demosite.controller;

import jakarta.servlet.http.HttpSession;
import org.keyfort.demosite.entity.TicketEntity;
import org.keyfort.demosite.service.TicketService;
import org.keyfort.demosite.service.TicketValidationException;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseBody;

import java.time.Instant;
import java.util.Map;

@Controller
public class DemoLoginController {

    private final TicketService ticketService;

    public DemoLoginController(TicketService ticketService) {
        this.ticketService = ticketService;
    }

    @GetMapping(value = "/keyfort/login")
    public String keyfortLogin(@RequestParam(name = "ticket", required = false) String ticket, HttpSession session) {
        if (ticket == null || ticket.isBlank()) {
            return "redirect:/login?error=missing_ticket";
        }

        try {
            TicketEntity consumed = ticketService.consumeTicket(ticket);
            session.setAttribute("user", "guest-" + consumed.getEntryId());
            session.setAttribute("scope", consumed.getScope());
            session.setAttribute("role", "GUEST_READONLY");
            session.setAttribute("guestExpiresAt", Instant.now().plusSeconds(900).toString());
            return "redirect:/dashboard";
        } catch (TicketValidationException e) {
            return "redirect:/login?error=invalid_ticket";
        }
    }

    @GetMapping(value = "/login", produces = MediaType.TEXT_HTML_VALUE)
    @ResponseBody
    public String loginPage(@RequestParam(name = "error", required = false) String error) {
        String errorMsg = "";
        if ("invalid_ticket".equals(error)) {
            errorMsg = "<div class='error'>Invalid or expired login ticket.</div>";
        } else if ("missing_ticket".equals(error)) {
            errorMsg = "<div class='error'>Missing ticket parameter.</div>";
        }

        return "<!DOCTYPE html>\n" +
                "<html lang=\"en\">\n" +
                "<head>\n" +
                "    <meta charset=\"UTF-8\">\n" +
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
    }

    @PostMapping("/login")
    public String handleLogin(@RequestParam("username") String username, @RequestParam("password") String password, HttpSession session) {
        session.setAttribute("user", username);
        session.setAttribute("role", "OWNER");
        session.setAttribute("scope", "full");
        return "redirect:/dashboard";
    }

    @GetMapping(value = "/dashboard", produces = MediaType.TEXT_HTML_VALUE)
    @ResponseBody
    public String dashboard(HttpSession session) {
        String user = (String) session.getAttribute("user");
        if (user == null) {
            return "redirect:/login";
        }
        String role = String.valueOf(session.getAttribute("role"));
        String scope = String.valueOf(session.getAttribute("scope"));
        String expires = (String) session.getAttribute("guestExpiresAt");

        String expiresHtml = expires != null ? "<p>Guest Expiry: <code>" + expires + "</code></p>" : "";

        return "<!DOCTYPE html>\n" +
                "<html lang=\"en\">\n" +
                "<head>\n" +
                "    <meta charset=\"UTF-8\">\n" +
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
    }

    @GetMapping("/api/session")
    @ResponseBody
    public ResponseEntity<Map<String, Object>> sessionInfo(HttpSession session) {
        String user = (String) session.getAttribute("user");
        if (user == null) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).build();
        }
        return ResponseEntity.ok(Map.of(
                "user", user,
                "role", session.getAttribute("role"),
                "scope", session.getAttribute("scope")
        ));
    }
}
