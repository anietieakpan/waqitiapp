package com.waqiti.apigateway.filter;

import lombok.extern.slf4j.Slf4j;
import org.springframework.cloud.gateway.filter.GatewayFilterChain;
import org.springframework.cloud.gateway.filter.GlobalFilter;
import org.springframework.core.Ordered;
import org.springframework.http.server.reactive.ServerHttpRequest;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ServerWebExchange;
import reactor.core.publisher.Mono;

import java.util.UUID;

@Component
@Slf4j
public class LoggingFilter implements GlobalFilter, Ordered {

    @Override
    public Mono<Void> filter(ServerWebExchange exchange, GatewayFilterChain chain) {
        ServerHttpRequest request = exchange.getRequest();
        String requestId = UUID.randomUUID().toString();

        // Add request ID to the exchange attributes
        exchange.getAttributes().put("requestId", requestId);

        // Log the incoming request
        log.info("Request {} {} from {}, Request ID: {}",
                request.getMethod(), request.getURI(),
                request.getRemoteAddress(), requestId);

        // Measure request timing
        long startTime = System.currentTimeMillis();

        return chain.filter(exchange)
                .doFinally(signalType -> {
                    // Log request completion with timing
                    long endTime = System.currentTimeMillis();
                    log.info("Response for Request ID: {}, Status: {}, Duration: {} ms",
                            requestId, exchange.getResponse().getStatusCode(), (endTime - startTime));
                });
    }

    @Override
    public int getOrder() {
        // Execute this filter first
        return Ordered.HIGHEST_PRECEDENCE;
    }
}