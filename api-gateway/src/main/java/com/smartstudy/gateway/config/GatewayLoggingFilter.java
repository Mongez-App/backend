package com.smartstudy.gateway.config;

import com.smartstudy.shared.logging.LoggerFactory;
import org.slf4j.Logger;
import org.springframework.cloud.gateway.filter.GatewayFilterChain;
import org.springframework.cloud.gateway.filter.GlobalFilter;
import org.springframework.core.Ordered;
import org.springframework.http.server.reactive.ServerHttpRequest;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ServerWebExchange;
import reactor.core.publisher.Mono;

import java.util.Optional;

@Component
public class GatewayLoggingFilter implements GlobalFilter, Ordered {
    private static final Logger log = LoggerFactory.getLogger(GatewayLoggingFilter.class);

    @Override
    public Mono<Void> filter(ServerWebExchange exchange, GatewayFilterChain chain) {
        long startTime = System.currentTimeMillis();
        ServerHttpRequest request = exchange.getRequest();
        String clientIp = getClientIp(exchange);

        log.info("Gateway Request: {} {} | IP: {} | Content-Type: {}", 
                request.getMethod(), 
                request.getURI(),
                clientIp,
                Optional.ofNullable(request.getHeaders().getContentType()).orElse(null));

        return chain.filter(exchange)
                .doOnSuccess(aVoid -> logResponse(exchange, startTime))
                .doOnError(error -> logError(exchange, startTime, error));
    }

    private void logResponse(ServerWebExchange exchange, long startTime) {
        long duration = System.currentTimeMillis() - startTime;
        log.info("Gateway Response: {} {} | Status: {} | Duration: {}ms", 
                exchange.getRequest().getMethod().name(), 
                exchange.getRequest().getURI(),
                exchange.getResponse().getStatusCode(),
                duration);
    }

    private void logError(ServerWebExchange exchange, long startTime, Throwable error) {
        long duration = System.currentTimeMillis() - startTime;
        log.error("Gateway Error: {} {} | Error: {} | Duration: {}ms", 
                exchange.getRequest().getMethod().name(), 
                exchange.getRequest().getURI(),
                error.getMessage() != null ? error.getMessage() : error,
                duration);
    }

    private String getClientIp(ServerWebExchange exchange) {
        String xForwardedFor = exchange.getRequest().getHeaders().getFirst("X-Forwarded-For");
        if (xForwardedFor != null && !xForwardedFor.isEmpty()) {
            return xForwardedFor.split(",")[0].trim();
        }
        return exchange.getRequest().getRemoteAddress() != null 
                ? exchange.getRequest().getRemoteAddress().getAddress().getHostAddress() 
                : "unknown";
    }

    @Override
    public int getOrder() {
        return Ordered.LOWEST_PRECEDENCE;
    }
}
