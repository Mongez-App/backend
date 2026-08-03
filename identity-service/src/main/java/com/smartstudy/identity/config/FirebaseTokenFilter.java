package com.smartstudy.identity.config;

import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseAuthException;
import com.google.firebase.auth.FirebaseToken;
import com.smartstudy.shared.logging.LoggerFactory;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
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

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain filterChain)
            throws ServletException, IOException {

        String xUserId = request.getHeader("X-User-Id");
        String bearerToken = request.getHeader("Authorization");

        if (xUserId != null && !xUserId.isBlank()) {
            log.info("Authenticated via X-User-Id header for path: {}", request.getRequestURI());
            UsernamePasswordAuthenticationToken authentication = new UsernamePasswordAuthenticationToken(
                    xUserId, null, Collections.emptyList());
            authentication.setDetails(new WebAuthenticationDetailsSource().buildDetails(request));
            SecurityContextHolder.getContext().setAuthentication(authentication);
        } else if (bearerToken != null && bearerToken.startsWith("Bearer ")) {
            String token = bearerToken.substring(7);
            try {
                FirebaseToken firebaseToken = FirebaseAuth.getInstance().verifyIdToken(token);
                log.info("Firebase token verified for path: {}", request.getRequestURI());
                UsernamePasswordAuthenticationToken authentication = new UsernamePasswordAuthenticationToken(
                        firebaseToken, token, Collections.emptyList());
                authentication.setDetails(new WebAuthenticationDetailsSource().buildDetails(request));
                SecurityContextHolder.getContext().setAuthentication(authentication);
            } catch (FirebaseAuthException e) {
                log.warn("Invalid Firebase token for path: {}", request.getRequestURI());
                request.setAttribute("firebase_auth_error", e);
            }
        }

        filterChain.doFilter(request, response);
    }
}
