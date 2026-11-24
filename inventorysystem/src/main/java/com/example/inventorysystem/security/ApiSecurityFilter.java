package com.example.inventorysystem.security;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.Instant;
import java.util.regex.Pattern;

@Component
public class ApiSecurityFilter extends OncePerRequestFilter {

    private static final Pattern PAYMENT_WEBHOOK =
            Pattern.compile("^/api/orders/\\d+/payment/(success|failure)$");

    private final String apiKey;
    private final String webhookSecret;
    private final long toleranceSeconds;

    public ApiSecurityFilter(
            @Value("${inventory.security.api-key}") String apiKey,
            @Value("${inventory.security.webhook-secret}") String webhookSecret,
            @Value("${inventory.security.webhook-tolerance-seconds:300}")
            long toleranceSeconds) {
        if (apiKey == null || apiKey.isBlank()
                || webhookSecret == null || webhookSecret.isBlank()) {
            throw new IllegalStateException(
                    "inventory.security.api-key and "
                            + "inventory.security.webhook-secret must be set");
        }
        this.apiKey = apiKey;
        this.webhookSecret = webhookSecret;
        this.toleranceSeconds = toleranceSeconds;
    }

    @Override
    protected void doFilterInternal(
            HttpServletRequest request,
            HttpServletResponse response,
            FilterChain chain) throws ServletException, IOException {

        String path = request.getRequestURI();

        if (!path.startsWith("/api/")) {
            chain.doFilter(request, response);
            return;
        }

        if (PAYMENT_WEBHOOK.matcher(path).matches()) {
            boolean valid = WebhookSignature.verify(
                    webhookSecret,
                    request.getHeader("X-Webhook-Timestamp"),
                    request.getMethod(),
                    path,
                    request.getHeader("X-Webhook-Signature"),
                    toleranceSeconds,
                    Instant.now());
            if (!valid) {
                reject(response, "Invalid or missing webhook signature");
                return;
            }
        } else if (!validApiKey(request.getHeader("X-API-Key"))) {
            reject(response, "Missing or invalid API key");
            return;
        }

        chain.doFilter(request, response);
    }

    private boolean validApiKey(String provided) {
        return provided != null && MessageDigest.isEqual(
                apiKey.getBytes(StandardCharsets.UTF_8),
                provided.getBytes(StandardCharsets.UTF_8));
    }

    private void reject(HttpServletResponse response, String message)
            throws IOException {
        response.setStatus(HttpServletResponse.SC_UNAUTHORIZED);
        response.setContentType("application/json");
        response.getWriter().write(
                "{\"status\":401,\"error\":\"Unauthorized\",\"message\":\""
                        + message + "\"}");
    }
}
