package com.smartstudy.gateway.config;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.google.firebase.FirebaseApp;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseAuthException;
import com.google.firebase.auth.FirebaseToken;
import com.smartstudy.shared.dto.ErrorResponse;
import com.smartstudy.shared.logging.LoggerFactory;
import org.springframework.cloud.gateway.filter.GatewayFilterChain;
import org.springframework.cloud.gateway.filter.GlobalFilter;
import org.springframework.core.Ordered;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.server.reactive.ServerHttpResponse;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ServerErrorException;
import org.springframework.web.server.ServerWebExchange;
import reactor.core.publisher.Mono;
import org.slf4j.Logger;

import java.nio.charset.StandardCharsets;
import java.time.Instant;

@Component
public class FirebaseTokenGatewayFilter implements GlobalFilter, Ordered {

    private static final Logger log = LoggerFactory.getLogger(FirebaseTokenGatewayFilter.class);
    private final ObjectMapper objectMapper;

    public FirebaseTokenGatewayFilter(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
    }

    @Override
    public Mono<Void> filter(ServerWebExchange exchange, GatewayFilterChain chain) {
        if (FirebaseApp.getApps().isEmpty()) {
            throw new ServerErrorException("Firebase App is down", null);
        }

        String path = exchange.getRequest().getURI().getPath();
        if (shouldSkipFilter(exchange, path)) {
            return chain.filter(exchange);
        }

        String authorization = exchange.getRequest().getHeaders().getFirst("Authorization");

        if (authorization == null || !authorization.startsWith("Bearer ")) {
            return chain.filter(exchange);
        }

        String token = authorization.substring(7);
        try {
            FirebaseToken firebaseToken = FirebaseAuth.getInstance().verifyIdToken(token);
            String userId = firebaseToken.getUid();
            log.info("Firebase token verified for user: {} on path: {}", userId, exchange.getRequest().getURI().getPath());

            ServerWebExchange mutatedExchange = exchange.mutate()
                    .request(exchange.getRequest().mutate()
                            .header("X-User-Id", userId)
                            .build())
                    .build();

            return chain.filter(mutatedExchange);
        } catch (FirebaseAuthException e) {
            log.warn("Invalid Firebase token for path: {}", exchange.getRequest().getURI().getPath());
            return writeUnauthorizedResponse(exchange, "Invalid or expired Firebase token");
        }
    }

    private boolean shouldSkipFilter(ServerWebExchange exchange, String path) {
        if (path.startsWith("/auth")) {
            return true;
        }
        if ("POST".equalsIgnoreCase(exchange.getRequest().getMethod().name()) && "/users".equals(path)) {
            return true;
        }
        return false;
    }

    @Override
    public int getOrder() {
        return Ordered.HIGHEST_PRECEDENCE;
    }

    private Mono<Void> writeUnauthorizedResponse(ServerWebExchange exchange, String message) {
        ServerHttpResponse response = exchange.getResponse();
        response.setStatusCode(HttpStatus.UNAUTHORIZED);
        response.getHeaders().setContentType(MediaType.APPLICATION_JSON);

        try {
            ErrorResponse errorResponse = new ErrorResponse(
                    "UNAUTHORIZED",
                    message,
                    Instant.now().toString()
            );
            String json = objectMapper.writeValueAsString(errorResponse);
            byte[] bytes = json.getBytes(StandardCharsets.UTF_8);
            return response.writeWith(Mono.just(response.bufferFactory().wrap(bytes)));
        } catch (Exception e) {
            response.setStatusCode(HttpStatus.INTERNAL_SERVER_ERROR);
            return response.setComplete();
        }
    }
}
