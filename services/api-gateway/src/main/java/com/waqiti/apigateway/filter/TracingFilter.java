package com.waqiti.apigateway.filter;

import org.springframework.cloud.gateway.filter.GatewayFilterChain;
import org.springframework.cloud.gateway.filter.GlobalFilter;
import org.springframework.core.Ordered;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ServerWebExchange;
import reactor.core.publisher.Mono;
import java.util.UUID;

@Component
public class TracingFilter implements GlobalFilter, Ordered {
    private static final String CORRELATION_ID = "X-Correlation-ID";

    @Override
    public Mono<Void> filter(ServerWebExchange exchange, GatewayFilterChain chain) {
        // Use final variable to comply with lambda requirements
        final String correlationId = exchange.getRequest().getHeaders().getFirst(CORRELATION_ID);

        // Generate a new correlation ID if one doesn't exist
        final String finalCorrelationId = correlationId != null ? correlationId : UUID.randomUUID().toString();

        // Add the correlation ID to the outgoing request
        ServerWebExchange modifiedExchange = exchange.mutate()
                .request(exchange.getRequest().mutate()
                        .header(CORRELATION_ID, finalCorrelationId)
                        .build())
                .build();

        // Add the correlation ID to the response as well
        return chain.filter(modifiedExchange)
                .then(Mono.fromRunnable(() ->
                        modifiedExchange.getResponse().getHeaders().add(CORRELATION_ID, finalCorrelationId)
                ));
    }

    @Override
    public int getOrder() {
        // Execute this filter before the existing logging filter
        return Ordered.HIGHEST_PRECEDENCE;
    }
}