package com.waqiti.apigateway.security;

import com.waqiti.apigateway.config.AuthenticationFeatureFlags;
import com.waqiti.apigateway.security.keycloak.KeycloakAuthenticationManager;
import com.waqiti.gateway.security.legacy.LegacyJwtAuthenticationManager;
import io.github.resilience4j.circuitbreaker.CircuitBreaker;
import io.github.resilience4j.circuitbreaker.CircuitBreakerConfig;
import io.github.resilience4j.circuitbreaker.CircuitBreakerRegistry;
import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Timer;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.cloud.gateway.filter.GatewayFilterChain;
import org.springframework.cloud.gateway.filter.GlobalFilter;
import org.springframework.core.Ordered;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.server.reactive.ServerHttpRequest;
import org.springframework.http.server.reactive.ServerHttpResponse;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.context.ReactiveSecurityContextHolder;
import org.springframework.security.core.context.SecurityContext;
import org.springframework.security.core.context.SecurityContextImpl;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ServerWebExchange;
import reactor.core.publisher.Mono;

import jakarta.annotation.PostConstruct;
import java.time.Duration;
import java.util.Collection;
import java.util.List;
import java.util.UUID;

/**
 * Dual-mode authentication filter supporting both Keycloak and legacy JWT authentication
 * during the migration period. This filter implements circuit breaker pattern for
 * automatic fallback to legacy authentication when Keycloak is unavailable.
 */
@Component
@Slf4j
public class DualModeAuthenticationFilter implements GlobalFilter, Ordered {

    private static final String AUTH_HEADER = "Authorization";
    private static final String BEARER_PREFIX = "Bearer ";
    private static final String AUTH_MODE_HEADER = "X-Auth-Mode";
    private static final String USER_ID_HEADER = "X-User-Id";
    private static final String USER_ROLES_HEADER = "X-User-Roles";
    private static final String REQUEST_ID_HEADER = "X-Request-Id";

    private final AuthenticationFeatureFlags featureFlags;
    private final KeycloakAuthenticationManager keycloakAuthManager;
    private final LegacyJwtAuthenticationManager legacyAuthManager;
    private final MeterRegistry meterRegistry;

    public DualModeAuthenticationFilter(
            AuthenticationFeatureFlags featureFlags,
            KeycloakAuthenticationManager keycloakAuthManager,
            LegacyJwtAuthenticationManager legacyAuthManager,
            MeterRegistry meterRegistry) {
        this.featureFlags = featureFlags;
        this.keycloakAuthManager = keycloakAuthManager;
        this.legacyAuthManager = legacyAuthManager;
        this.meterRegistry = meterRegistry;
    }

    @Value("${gateway.auth.circuit-breaker.enabled:true}")
    private boolean circuitBreakerEnabled;

    @Value("${gateway.auth.circuit-breaker.failure-threshold:50}")
    private float failureThreshold;

    @Value("${gateway.auth.circuit-breaker.wait-duration:60}")
    private int waitDurationInSeconds;

    @Value("${gateway.auth.cache.enabled:true}")
    private boolean cacheEnabled;

    @Value("${gateway.auth.cache.ttl:300}")
    private int cacheTtlSeconds;

    private CircuitBreaker keycloakCircuitBreaker;
    private Counter authSuccessCounter;
    private Counter authFailureCounter;
    private Counter fallbackCounter;
    private Timer authLatencyTimer;

    @PostConstruct
    public void init() {
        // Initialize circuit breaker
        if (circuitBreakerEnabled) {
            CircuitBreakerConfig config = CircuitBreakerConfig.custom()
                    .failureRateThreshold(failureThreshold)
                    .waitDurationInOpenState(Duration.ofSeconds(waitDurationInSeconds))
                    .slidingWindowSize(100)
                    .permittedNumberOfCallsInHalfOpenState(5)
                    .automaticTransitionFromOpenToHalfOpenEnabled(true)
                    .build();

            CircuitBreakerRegistry registry = CircuitBreakerRegistry.of(config);
            keycloakCircuitBreaker = registry.circuitBreaker("keycloak-auth");

            // Add event listeners
            keycloakCircuitBreaker.getEventPublisher()
                    .onStateTransition(event -> {
                        log.warn("Circuit breaker state transition: {}", event);
                        if (event.getStateTransition().equals(
                                CircuitBreaker.StateTransition.CLOSED_TO_OPEN)) {
                            alertOperations("Keycloak authentication circuit breaker opened - falling back to legacy auth");
                        }
                    });
        }

        // Initialize metrics
        authSuccessCounter = Counter.builder("gateway.auth.success")
                .description("Successful authentications")
                .register(meterRegistry);

        authFailureCounter = Counter.builder("gateway.auth.failure")
                .description("Failed authentications")
                .register(meterRegistry);

        fallbackCounter = Counter.builder("gateway.auth.fallback")
                .description("Authentication fallbacks to legacy")
                .register(meterRegistry);

        authLatencyTimer = Timer.builder("gateway.auth.latency")
                .description("Authentication latency")
                .register(meterRegistry);
    }

    @Override
    public Mono<Void> filter(ServerWebExchange exchange, GatewayFilterChain chain) {
        ServerHttpRequest request = exchange.getRequest();
        String requestId = generateRequestId();
        
        // Add request ID for tracing
        ServerHttpRequest mutatedRequest = request.mutate()
                .header(REQUEST_ID_HEADER, requestId)
                .build();

        // Extract token from request
        String token = extractToken(request);
        
        if (token.isEmpty()) {
            // No token provided - check if endpoint requires authentication
            if (isPublicEndpoint(request.getPath().value())) {
                return chain.filter(exchange.mutate().request(mutatedRequest).build());
            }
            return unauthorizedResponse(exchange, "No authentication token provided");
        }

        // Start timing authentication
        Timer.Sample sample = Timer.start(meterRegistry);

        // Determine authentication mode
        AuthMode authMode = determineAuthMode(request, token);
        
        log.debug("Request {} using auth mode: {}", requestId, authMode);

        // Perform authentication based on mode
        return authenticate(token, authMode, requestId)
                .flatMap(authentication -> {
                    sample.stop(authLatencyTimer);
                    authSuccessCounter.increment();
                    
                    // Create security context
                    SecurityContext context = new SecurityContextImpl(authentication);
                    
                    // Add user information to headers for downstream services
                    ServerHttpRequest authenticatedRequest = mutatedRequest.mutate()
                            .header(AUTH_MODE_HEADER, authMode.name())
                            .header(USER_ID_HEADER, extractUserId(authentication))
                            .header(USER_ROLES_HEADER, extractRoles(authentication))
                            .build();
                    
                    // Continue with authenticated request
                    return chain.filter(exchange.mutate().request(authenticatedRequest).build())
                            .contextWrite(ReactiveSecurityContextHolder.withSecurityContext(Mono.just(context)));
                })
                .onErrorResume(error -> {
                    sample.stop(authLatencyTimer);
                    log.error("Authentication failed for request {}: {}", requestId, error.getMessage());
                    
                    // Try fallback if Keycloak fails and dual mode is enabled
                    if (authMode == AuthMode.KEYCLOAK && featureFlags.isDualModeEnabled()) {
                        log.info("Attempting fallback to legacy authentication for request {}", requestId);
                        fallbackCounter.increment();
                        
                        return authenticateLegacy(token, requestId)
                                .flatMap(authentication -> {
                                    authSuccessCounter.increment();
                                    
                                    SecurityContext context = new SecurityContextImpl(authentication);
                                    
                                    ServerHttpRequest authenticatedRequest = mutatedRequest.mutate()
                                            .header(AUTH_MODE_HEADER, AuthMode.LEGACY.name())
                                            .header(USER_ID_HEADER, extractUserId(authentication))
                                            .header(USER_ROLES_HEADER, extractRoles(authentication))
                                            .build();
                                    
                                    return chain.filter(exchange.mutate().request(authenticatedRequest).build())
                                            .contextWrite(ReactiveSecurityContextHolder.withSecurityContext(Mono.just(context)));
                                })
                                .onErrorResume(fallbackError -> {
                                    authFailureCounter.increment();
                                    return unauthorizedResponse(exchange, "Authentication failed: " + fallbackError.getMessage());
                                });
                    }
                    
                    authFailureCounter.increment();
                    return unauthorizedResponse(exchange, "Authentication failed: " + error.getMessage());
                });
    }

    private Mono<Authentication> authenticate(String token, AuthMode mode, String requestId) {
        switch (mode) {
            case KEYCLOAK:
                if (circuitBreakerEnabled && keycloakCircuitBreaker != null) {
                    return Mono.fromCallable(() -> 
                            keycloakCircuitBreaker.executeSupplier(() -> 
                                    keycloakAuthManager.authenticate(token, requestId).block()
                            ))
                            .onErrorResume(error -> {
                                log.warn("Keycloak authentication failed with circuit breaker: {}", error.getMessage());
                                throw new AuthenticationException("Keycloak authentication failed", error);
                            });
                }
                return keycloakAuthManager.authenticate(token, requestId);
                
            case LEGACY:
                return legacyAuthManager.authenticate(token, requestId);
                
            default:
                return Mono.error(new AuthenticationException("Unknown authentication mode: " + mode));
        }
    }

    private Mono<Authentication> authenticateLegacy(String token, String requestId) {
        return legacyAuthManager.authenticate(token, requestId);
    }

    private AuthMode determineAuthMode(ServerHttpRequest request, String token) {
        // Check for explicit auth mode header (for testing)
        String authModeHeader = request.getHeaders().getFirst("X-Force-Auth-Mode");
        if (authModeHeader != null) {
            try {
                return AuthMode.valueOf(authModeHeader.toUpperCase());
            } catch (IllegalArgumentException e) {
                log.warn("Invalid auth mode header: {}", authModeHeader);
            }
        }

        // Check feature flags
        if (!featureFlags.isKeycloakEnabled()) {
            return AuthMode.LEGACY;
        }

        if (featureFlags.isDualModeEnabled()) {
            // Determine based on user ID or percentage rollout
            String userId = extractUserIdFromToken(token);
            if (userId != null) {
                AuthMode mode = featureFlags.getAuthMode(userId);
                log.debug("User {} assigned auth mode: {}", userId, mode);
                return mode;
            }
        }

        return featureFlags.isKeycloakEnabled() ? AuthMode.KEYCLOAK : AuthMode.LEGACY;
    }

    private String extractToken(ServerHttpRequest request) {
        String authHeader = request.getHeaders().getFirst(AUTH_HEADER);
        if (authHeader != null && authHeader.startsWith(BEARER_PREFIX)) {
            return authHeader.substring(BEARER_PREFIX.length());
        }
        return ""; // Return empty string instead of null
    }

    private String extractUserIdFromToken(String token) {
        try {
            // Simple JWT decode to get user ID without validation
            String[] parts = token.split("\\.");
            if (parts.length == 3) {
                String payload = new String(java.util.Base64.getUrlDecoder().decode(parts[1]));
                // Parse JSON payload to extract user ID
                // This is a simplified version - use proper JSON parsing in production
                if (payload.contains("user_id")) {
                    int start = payload.indexOf("\"user_id\":\"") + 11;
                    int end = payload.indexOf("\"", start);
                    return payload.substring(start, end);
                }
            }
        } catch (Exception e) {
            log.debug("Could not extract user ID from token: {}", e.getMessage());
        }
        return "ANONYMOUS"; // Return explicit anonymous identifier instead of null
    }

    private String extractUserId(Authentication authentication) {
        if (authentication.getPrincipal() instanceof WaqitiUserPrincipal) {
            return ((WaqitiUserPrincipal) authentication.getPrincipal()).getUserId();
        }
        return authentication.getName();
    }

    private String extractRoles(Authentication authentication) {
        Collection<? extends GrantedAuthority> authorities = authentication.getAuthorities();
        return String.join(",", authorities.stream()
                .map(GrantedAuthority::getAuthority)
                .toArray(String[]::new));
    }

    private boolean isPublicEndpoint(String path) {
        List<String> publicPaths = List.of(
                "/api/public",
                "/api/auth/login",
                "/api/auth/register",
                "/api/auth/forgot-password",
                "/actuator/health",
                "/actuator/info",
                "/swagger-ui",
                "/v3/api-docs"
        );
        
        return publicPaths.stream().anyMatch(path::startsWith);
    }

    private Mono<Void> unauthorizedResponse(ServerWebExchange exchange, String message) {
        ServerHttpResponse response = exchange.getResponse();
        response.setStatusCode(HttpStatus.UNAUTHORIZED);
        response.getHeaders().add(HttpHeaders.CONTENT_TYPE, "application/json");
        
        String body = String.format("{\"error\": \"Unauthorized\", \"message\": \"%s\"}", message);
        
        return response.writeWith(Mono.just(response.bufferFactory().wrap(body.getBytes())));
    }

    private String generateRequestId() {
        return UUID.randomUUID().toString();
    }

    private void alertOperations(String message) {
        // Implement alerting logic (e.g., send to monitoring system, Slack, PagerDuty)
        log.error("ALERT: {}", message);
    }

    @Override
    public int getOrder() {
        return -100; // Execute early in the filter chain
    }

    public enum AuthMode {
        KEYCLOAK,
        LEGACY,
        DUAL
    }

    public static class AuthenticationException extends RuntimeException {
        public AuthenticationException(String message) {
            super(message);
        }
        
        public AuthenticationException(String message, Throwable cause) {
            super(message, cause);
        }
    }
}