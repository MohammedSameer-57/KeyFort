package org.keyfort.demosite.filter;

import jakarta.servlet.Filter;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.ServletRequest;
import jakarta.servlet.ServletResponse;
import jakarta.servlet.http.Cookie;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.servlet.http.HttpSession;
import org.keyfort.demosite.entity.GuestSessionEntity;
import org.keyfort.demosite.service.GuestSessionService;
import org.keyfort.demosite.service.SessionInvalidException;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;

import java.io.IOException;

@Component
@Order(1)
public class GuestSessionFilter implements Filter {

    public static final String SESSION_COOKIE_NAME = "KEYFORT_GUEST_SESSION";
    public static final String DEVICE_COOKIE_NAME = "KEYFORT_DEVICE_ID";

    private final GuestSessionService sessionService;

    public GuestSessionFilter(GuestSessionService sessionService) {
        this.sessionService = sessionService;
    }

    @Override
    public void doFilter(ServletRequest servletRequest, ServletResponse servletResponse, FilterChain chain)
            throws IOException, ServletException {
        HttpServletRequest request = (HttpServletRequest) servletRequest;
        HttpServletResponse response = (HttpServletResponse) servletResponse;

        String path = request.getRequestURI();

        // Connector and public endpoints bypass session filter
        if (isPublicPath(path)) {
            chain.doFilter(request, response);
            return;
        }

        Cookie guestCookie = findCookie(request, SESSION_COOKIE_NAME);

        if (guestCookie != null) {
            String sessionId = guestCookie.getValue();
            String deviceId = findCookieValue(request, DEVICE_COOKIE_NAME);
            String userAgent = request.getHeader("User-Agent");

            try {
                // Immediate kill switch and TTL validation directly against DB on every request
                GuestSessionEntity session = sessionService.validateAndTouchSession(sessionId, userAgent, deviceId);

                // Scope validation: read-only sessions cannot perform mutating requests
                if (isWriteMethod(request.getMethod()) && "read-only".equalsIgnoreCase(session.getScope())) {
                    response.setStatus(HttpServletResponse.SC_FORBIDDEN);
                    response.setContentType("application/json");
                    response.getWriter().write("{\"error\": \"Guest session is restricted to read-only access\"}");
                    return;
                }

                request.setAttribute("guestSession", session);
                request.setAttribute("user", "guest-" + session.getAccount());

                HttpSession httpSession = request.getSession(true);
                httpSession.setAttribute("user", "guest-" + session.getAccount());
                httpSession.setAttribute("scope", session.getScope());
                httpSession.setAttribute("role", "GUEST_READONLY");
                httpSession.setAttribute("guestExpiresAt", session.getExpiresAt().toString());

                chain.doFilter(request, response);
                return;
            } catch (SessionInvalidException ex) {
                // Invalidate any local HTTP session as well
                HttpSession httpSession = request.getSession(false);
                if (httpSession != null) {
                    httpSession.invalidate();
                }

                if (isApiRequest(path)) {
                    response.setStatus(HttpServletResponse.SC_UNAUTHORIZED);
                    response.setContentType("application/json");
                    response.getWriter().write("{\"error\": \"Session invalid or expired\"}");
                    return;
                } else {
                    response.sendRedirect("/login?error=session_expired");
                    return;
                }
            }
        }

        // If no guest cookie, check if request is accessing protected resources
        if (isProtectedPath(path)) {
            HttpSession httpSession = request.getSession(false);
            if (httpSession == null || httpSession.getAttribute("user") == null || "GUEST_READONLY".equals(httpSession.getAttribute("role"))) {
                if (httpSession != null && "GUEST_READONLY".equals(httpSession.getAttribute("role"))) {
                    httpSession.invalidate();
                }
                if (isApiRequest(path)) {
                    response.setStatus(HttpServletResponse.SC_UNAUTHORIZED);
                    return;
                } else {
                    response.sendRedirect("/login");
                    return;
                }
            }
        }

        chain.doFilter(request, response);
    }

    private boolean isPublicPath(String path) {
        return path.startsWith("/connector/") ||
                path.startsWith("/keyfort/login") ||
                path.equals("/login") ||
                path.equals("/") ||
                path.startsWith("/h2-console");
    }

    private boolean isProtectedPath(String path) {
        return path.equals("/dashboard") ||
                path.startsWith("/api/session") ||
                path.startsWith("/account/");
    }

    private boolean isApiRequest(String path) {
        return path.startsWith("/api/") || path.startsWith("/account/");
    }

    private boolean isWriteMethod(String method) {
        return "POST".equalsIgnoreCase(method) ||
                "PUT".equalsIgnoreCase(method) ||
                "DELETE".equalsIgnoreCase(method) ||
                "PATCH".equalsIgnoreCase(method);
    }

    private static Cookie findCookie(HttpServletRequest request, String name) {
        Cookie[] cookies = request.getCookies();
        if (cookies == null) return null;
        for (Cookie c : cookies) {
            if (name.equals(c.getName())) {
                return c;
            }
        }
        return null;
    }

    private static String findCookieValue(HttpServletRequest request, String name) {
        Cookie c = findCookie(request, name);
        return c != null ? c.getValue() : null;
    }
}
