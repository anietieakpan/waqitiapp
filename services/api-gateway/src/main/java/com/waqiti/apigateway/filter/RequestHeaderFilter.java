package com.waqiti.apigateway.filter;

import com.waqiti.apigateway.security.JwtUtil;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.cloud.gateway.filter.GatewayFilterChain;
import org.springframework.cloud.gateway.filter.GlobalFilter;
import org.springframework.core.Ordered;
import org.springframework.http.HttpHeaders;
import org.springframework.http.server.reactive.ServerHttpRequest;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;
import org.springframework.web.server.ServerWebExchange;
import reactor.core.publisher.Mono;

/**
 * Filter to add user ID header to requests when user is authenticated
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class RequestHeaderFilter implements GlobalFilter, Ordered {

    private final JwtUtil jwtUtil;
    private static final String BEARER_PREFIX = "Bearer ";
    private static final String USER_ID_HEADER = "X-User-ID";

    @Override
    public Mono<Void> filter(ServerWebExchange exchange, GatewayFilterChain chain) {
        String token = resolveToken(exchange.getRequest());

        if (StringUtils.hasText(token) && jwtUtil.validateToken(token)) {
            try {
                // Extract user ID from token
                String userId = jwtUtil.getUserId(token).toString();

                // Add user ID to request headers for downstream services
                ServerHttpRequest request = exchange.getRequest().mutate()
                        .header(USER_ID_HEADER, userId)
                        .build();

                return chain.filter(exchange.mutate().request(request).build());
            } catch (Exception e) {
                log.error("Error extracting user ID from token", e);
            }
        }

        return chain.filter(exchange);
    }

    private String resolveToken(ServerHttpRequest request) {
        String bearerToken = request.getHeaders().getFirst(HttpHeaders.AUTHORIZATION);
        if (StringUtils.hasText(bearerToken) && bearerToken.startsWith(BEARER_PREFIX)) {
            return bearerToken.substring(BEARER_PREFIX.length());
        }
        return null;
    }

    @Override
    public int getOrder() {
        // Execute after logging filter but before other filters
        return Ordered.HIGHEST_PRECEDENCE + 10;
    }
}