package com.finabits.hrms.config;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.finabits.hrms.util.JwtUtil;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.web.authentication.WebAuthenticationDetailsSource;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.Map;

@Component
@RequiredArgsConstructor
@Slf4j
public class JwtAuthFilter extends OncePerRequestFilter {

    private final JwtUtil             jwtUtil;
    private final UserDetailsService  userDetailsService;
    private final ObjectMapper        objectMapper;

    @Override
    protected void doFilterInternal(HttpServletRequest request,
                                    HttpServletResponse response,
                                    FilterChain filterChain) throws ServletException, IOException {
        try {
            String jwt = parseJwt(request);

            if (jwt != null) {
                // ── Token present — try to validate ──────────────────────────
                String username = jwtUtil.extractUsername(jwt);

                if (username == null) {
                    // Token is malformed — return 401 immediately
                    sendUnauthorized(response, "Invalid token. Please log in again.");
                    return;
                }

                if (SecurityContextHolder.getContext().getAuthentication() == null) {
                    UserDetails userDetails = userDetailsService.loadUserByUsername(username);

                    if (jwtUtil.validateToken(jwt, userDetails)) {
                        // ✅ Valid token — set authentication
                        UsernamePasswordAuthenticationToken authToken =
                                new UsernamePasswordAuthenticationToken(
                                        userDetails, null, userDetails.getAuthorities());
                        authToken.setDetails(
                                new WebAuthenticationDetailsSource().buildDetails(request));
                        SecurityContextHolder.getContext().setAuthentication(authToken);

                    } else {
                        // ❌ Token expired or invalid — return 401 so frontend redirects to login
                        log.warn("JWT token expired or invalid for user: {}", username);
                        sendUnauthorized(response, "Session expired. Please log in again.");
                        return;
                    }
                }
            }
            // No token present — continue filter chain (Spring Security handles 403 for protected routes)

        } catch (Exception e) {
            log.error("JWT authentication error: {}", e.getMessage());
            // On any unexpected error — also return 401 to avoid silent 403
            sendUnauthorized(response, "Authentication failed. Please log in again.");
            return;
        }

        filterChain.doFilter(request, response);
    }

    // ── Write a proper JSON 401 response ─────────────────────────────────────
    private void sendUnauthorized(HttpServletResponse response, String message) throws IOException {
        response.setStatus(HttpServletResponse.SC_UNAUTHORIZED); // 401
        response.setContentType("application/json");
        response.setCharacterEncoding("UTF-8");

        Map<String, Object> body = new HashMap<>();
        body.put("status",    401);
        body.put("errorCode", "UNAUTHORIZED");
        body.put("message",   message);
        body.put("timestamp", LocalDateTime.now().toString());

        response.getWriter().write(objectMapper.writeValueAsString(body));
    }

    private String parseJwt(HttpServletRequest request) {
        String headerAuth = request.getHeader("Authorization");
        if (StringUtils.hasText(headerAuth) && headerAuth.startsWith("Bearer ")) {
            return headerAuth.substring(7);
        }
        return null;
    }
}