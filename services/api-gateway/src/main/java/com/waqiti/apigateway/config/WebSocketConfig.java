package com.waqiti.apigateway.config;

import org.springframework.cloud.gateway.filter.GatewayFilter;
import org.springframework.cloud.gateway.filter.factory.AbstractGatewayFilterFactory;
import org.springframework.cloud.gateway.route.RouteLocator;
import org.springframework.cloud.gateway.route.builder.RouteLocatorBuilder;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpHeaders;
import org.springframework.http.server.reactive.ServerHttpRequest;
import org.springframework.stereotype.Component;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.reactive.CorsWebFilter;
import org.springframework.web.cors.reactive.UrlBasedCorsConfigurationSource;

import java.util.Arrays;

/**
 * WebSocket configuration for API Gateway
 * Enables WebSocket proxying with proper headers and security
 */
@Configuration
public class WebSocketConfig {

    @Bean
    public RouteLocator webSocketRouteLocator(RouteLocatorBuilder builder,
                                             WebSocketUpgradeFilter webSocketUpgradeFilter) {
        return builder.routes()
            // WebSocket route for real-time communication
            .route("websocket-service", r -> r
                .path("/ws/**", "/ws-native/**")
                .filters(f -> f
                    .filter(webSocketUpgradeFilter.apply(new WebSocketUpgradeFilter.Config()))
                    .preserveHostHeader()
                    .removeRequestHeader("Cookie")
                    .removeRequestHeader("X-Forwarded-For"))
                .uri("lb:ws://WEBSOCKET-SERVICE"))
            
            // Server-Sent Events route for one-way streaming
            .route("sse-notifications", r -> r
                .path("/api/v1/sse/**")
                .filters(f -> f
                    .setResponseHeader("Content-Type", "text/event-stream")
                    .setResponseHeader("Cache-Control", "no-cache")
                    .setResponseHeader("Connection", "keep-alive")
                    .setResponseHeader("X-Accel-Buffering", "no"))
                .uri("lb://NOTIFICATION-SERVICE"))
            
            .build();
    }

    /**
     * Custom filter for WebSocket upgrade handling
     */
    @Component
    public static class WebSocketUpgradeFilter extends AbstractGatewayFilterFactory<WebSocketUpgradeFilter.Config> {

        public WebSocketUpgradeFilter() {
            super(Config.class);
        }

        @Override
        public GatewayFilter apply(Config config) {
            return (exchange, chain) -> {
                ServerHttpRequest request = exchange.getRequest();
                
                // Check if this is a WebSocket upgrade request
                if (isWebSocketUpgradeRequest(request)) {
                    // Add necessary headers for WebSocket
                    ServerHttpRequest modifiedRequest = request.mutate()
                        .header("X-Forwarded-Proto", "ws")
                        .build();
                    
                    return chain.filter(exchange.mutate().request(modifiedRequest).build());
                }
                
                return chain.filter(exchange);
            };
        }

        private boolean isWebSocketUpgradeRequest(ServerHttpRequest request) {
            HttpHeaders headers = request.getHeaders();
            String upgrade = headers.getFirst(HttpHeaders.UPGRADE);
            String connection = headers.getFirst(HttpHeaders.CONNECTION);
            
            return "websocket".equalsIgnoreCase(upgrade) && 
                   connection != null && connection.toLowerCase().contains("upgrade");
        }

        public static class Config {
            // Configuration properties if needed
        }
    }

    /**
     * Enhanced CORS configuration for WebSocket support
     */
    @Bean
    public CorsWebFilter corsWebSocketFilter() {
        CorsConfiguration config = new CorsConfiguration();
        
        // Configure allowed origins
        config.setAllowedOriginPatterns(Arrays.asList(
            "https://*.waqiti.com",
            "https://app.example.com",
            "https://admin.example.com",
            "http://localhost:[*]",
            "capacitor://localhost",  // For mobile apps
            "ionic://localhost"       // For mobile apps
        ));
        
        // Configure allowed methods
        config.setAllowedMethods(Arrays.asList("GET", "POST", "PUT", "DELETE", "OPTIONS", "HEAD"));
        
        // Configure allowed headers
        config.setAllowedHeaders(Arrays.asList(
            "Origin",
            "Content-Type",
            "Accept",
            "Authorization",
            "X-Auth-Token",
            "X-Request-ID",
            "X-Device-ID",
            "X-Platform",
            "Sec-WebSocket-Key",
            "Sec-WebSocket-Version",
            "Sec-WebSocket-Extensions",
            "Sec-WebSocket-Protocol"
        ));
        
        // Configure exposed headers
        config.setExposedHeaders(Arrays.asList(
            "X-Request-ID",
            "X-RateLimit-Limit",
            "X-RateLimit-Remaining",
            "X-RateLimit-Reset"
        ));
        
        config.setAllowCredentials(true);
        config.setMaxAge(3600L);
        
        UrlBasedCorsConfigurationSource source = new UrlBasedCorsConfigurationSource();
        source.registerCorsConfiguration("/ws/**", config);
        source.registerCorsConfiguration("/ws-native/**", config);
        source.registerCorsConfiguration("/api/v1/sse/**", config);
        
        return new CorsWebFilter(source);
    }
}