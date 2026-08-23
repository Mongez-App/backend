package com.smartstudy.planning.config;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.slf4j.Logger;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;
import com.smartstudy.shared.logging.LoggerFactory;

import java.io.IOException;

@Component
@Order(1)
public class GatewaySecretFilter extends OncePerRequestFilter {

    private static final Logger log = LoggerFactory.getLogger(GatewaySecretFilter.class);

    private final String sharedSecret;

    public GatewaySecretFilter(@Value("${gateway.shared-secret:}") String sharedSecret) {
        this.sharedSecret = sharedSecret;
        if (sharedSecret == null || sharedSecret.isBlank()) {
            log.warn("gateway.shared-secret is not configured: X-User-Id headers are accepted without proof " +
                    "that they came from the API gateway. Set GATEWAY_SHARED_SECRET in production.");
        }
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain filterChain)
            throws ServletException, IOException {

        String xUserId = request.getHeader("X-User-Id");
        boolean usesForwardedIdentity = xUserId != null && !xUserId.isBlank();

        if (usesForwardedIdentity && !isTrustedGatewayRequest(request)) {
            log.warn("Rejected request to {} with X-User-Id but missing/invalid X-Gateway-Secret",
                    request.getRequestURI());
            response.setStatus(HttpServletResponse.SC_UNAUTHORIZED);
            response.setContentType("application/json");
            response.getWriter().write("{\"error_code\":\"UNAUTHORIZED\",\"message\":\"Missing or invalid " +
                    "gateway credentials.\",\"timestamp\":\"" + java.time.Instant.now() + "\"}");
            return;
        }

        filterChain.doFilter(request, response);
    }

    private boolean isTrustedGatewayRequest(HttpServletRequest request) {
        if (sharedSecret == null || sharedSecret.isBlank()) {
            return true;
        }
        return sharedSecret.equals(request.getHeader("X-Gateway-Secret"));
    }
}
