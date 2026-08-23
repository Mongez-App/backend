package com.smartstudy.gateway.config;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.google.firebase.FirebaseApp;
import com.google.firebase.auth.FirebaseAuth;
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
import reactor.core.scheduler.Schedulers;
import org.slf4j.Logger;

import java.nio.charset.StandardCharsets;
import java.time.Instant;

@Component
public class FirebaseTokenGatewayFilter implements GlobalFilter, Ordered {

    private static final Logger log = LoggerFactory.getLogger(FirebaseTokenGatewayFilter.class);
    private final ObjectMapper objectMapper;
    private final String sharedSecret;

    public FirebaseTokenGatewayFilter(ObjectMapper objectMapper,
                                      @org.springframework.beans.factory.annotation.Value("${gateway.shared-secret:}") String sharedSecret) {
        this.objectMapper = objectMapper;
        this.sharedSecret = sharedSecret;
    }

    @Override
    public Mono<Void> filter(ServerWebExchange exchange, GatewayFilterChain chain) {
        String path = exchange.getRequest().getURI().getPath();

        if (shouldSkipFilter(exchange, path)) {
            return chain.filter(exchange);
        }

        String authorization = exchange.getRequest().getHeaders().getFirst("Authorization");

        if (authorization == null || !authorization.startsWith("Bearer ")) {
            return writeUnauthorizedResponse(exchange, "Missing or malformed Authorization header");
        }

        String token = authorization.substring(7).trim();
        if (token.isEmpty()) {
            return writeUnauthorizedResponse(exchange, "Bearer token cannot be empty");
        }

        if (FirebaseApp.getApps().isEmpty()) {
            log.error("FirebaseApp is not initialized. Please verify FIREBASE_CREDENTIALS configuration.");
            return writeUnauthorizedResponse(exchange, "Authentication service is unavailable (Firebase not initialized)");
        }

        return Mono.fromCallable(() -> FirebaseAuth.getInstance().verifyIdToken(token))
                .subscribeOn(Schedulers.boundedElastic())
                .flatMap(firebaseToken -> {
                    String userId = firebaseToken.getUid();
                    String email = firebaseToken.getEmail();
                    log.info("Firebase token verified for user: {} on path: {}", userId, path);

                    ServerWebExchange mutatedExchange = exchange.mutate()
                            .request(exchange.getRequest().mutate()
                                    .headers(httpHeaders -> {
                                        httpHeaders.remove("X-User-Id");
                                        httpHeaders.remove("X-User-Email");
                                        httpHeaders.remove("X-Gateway-Secret");
                                        httpHeaders.remove("Authorization");
                                    })
                                    .header("X-User-Id", userId)
                                    .header("X-User-Email", email)
                                    .header("X-Gateway-Secret", sharedSecret)
                                    .build())
                            .build();

                    return chain.filter(mutatedExchange);
                })
                .onErrorResume(e -> {
                    log.warn("Invalid or failed Firebase token verification for path {}: {}", path, e.getMessage());
                    return writeUnauthorizedResponse(exchange, "Invalid or expired Firebase token");
                });
    }

    private boolean shouldSkipFilter(ServerWebExchange exchange, String path) {
        if (path.startsWith("/api/v1/auth/handshake")) {
            return true;
        }
        return path.startsWith("/actuator") || path.startsWith("/swagger-ui") || path.startsWith("/v3/api-docs");
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
