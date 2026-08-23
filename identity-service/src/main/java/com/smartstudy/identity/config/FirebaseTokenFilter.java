package com.smartstudy.identity.config;

import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseAuthException;
import com.google.firebase.auth.FirebaseToken;
import com.smartstudy.shared.logging.LoggerFactory;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.web.authentication.WebAuthenticationDetailsSource;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;
import org.slf4j.Logger;

import java.io.IOException;
import java.util.Collections;

@Component
public class FirebaseTokenFilter extends OncePerRequestFilter {

    private static final Logger log = LoggerFactory.getLogger(FirebaseTokenFilter.class);

    private final String sharedSecret;

    public FirebaseTokenFilter(@Value("${gateway.shared-secret:}") String sharedSecret) {
        this.sharedSecret = sharedSecret;
        if (sharedSecret == null || sharedSecret.isBlank()) {
            log.warn("gateway.shared-secret is not configured: X-User-Id headers are accepted without proof " +
                    "that they came from the API gateway. Set GATEWAY_SHARED_SECRET in production.");
        }
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain filterChain)
            throws ServletException, IOException {

        String bearerToken = request.getHeader("Authorization");

        if (bearerToken != null && bearerToken.startsWith("Bearer ")) {
            try {
                String token = bearerToken.substring(7);
                FirebaseToken firebaseToken = FirebaseAuth.getInstance().verifyIdToken(token);
                log.info("Firebase token verified for path: {}", request.getRequestURI());
                authenticate(firebaseToken.getUid(), request);
            } catch (FirebaseAuthException e) {
                log.warn("Invalid Firebase token for path: {}: {}", request.getRequestURI(), e.getMessage());
            }
        } else if (isTrustedGatewayRequest(request)) {
            String xUserId = request.getHeader("X-User-Id");
            if (xUserId != null && !xUserId.isBlank()) {
                authenticate(xUserId, request);
            }
        }

        filterChain.doFilter(request, response);
    }

    private boolean isTrustedGatewayRequest(HttpServletRequest request) {
        if (sharedSecret == null || sharedSecret.isBlank()) {
            return true;
        }
        return sharedSecret.equals(request.getHeader("X-Gateway-Secret"));
    }

    private void authenticate(String uid, HttpServletRequest request) {
        UsernamePasswordAuthenticationToken authentication = new UsernamePasswordAuthenticationToken(
                uid, null, Collections.emptyList());
        authentication.setDetails(new WebAuthenticationDetailsSource().buildDetails(request));
        SecurityContextHolder.getContext().setAuthentication(authentication);
    }
}
