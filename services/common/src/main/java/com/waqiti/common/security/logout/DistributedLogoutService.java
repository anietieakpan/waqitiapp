/**
 * Distributed Logout Service
 * Implements single logout (SLO) across all microservices
 * Ensures session invalidation propagation throughout the system
 */
package com.waqiti.common.security.logout;

import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.Builder;
import lombok.Data;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.cloud.client.ServiceInstance;
import org.springframework.cloud.client.discovery.DiscoveryClient;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.context.event.EventListener;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.http.*;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.scheduling.annotation.Async;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.client.registration.ClientRegistration;
import org.springframework.security.oauth2.client.registration.ClientRegistrationRepository;
import org.springframework.security.oauth2.client.registration.ReactiveClientRegistrationRepository;
import org.springframework.security.oauth2.core.oidc.OidcIdToken;
import org.springframework.security.oauth2.core.oidc.user.OidcUser;
import org.springframework.security.web.server.WebFilterExchange;
import org.springframework.security.web.server.authentication.logout.ServerLogoutHandler;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;
import org.springframework.web.reactive.function.client.WebClient;
import org.springframework.web.server.ServerWebExchange;
import org.springframework.web.server.WebSession;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import reactor.util.retry.Retry;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.net.URI;
import java.time.Duration;
import java.time.Instant;
import java.util.*;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;

/**
 * Comprehensive distributed logout implementation
 * Handles both front-channel and back-channel logout
 */
@Slf4j
@Service
@RequiredArgsConstructor
@ConditionalOnProperty(name = "security.logout.distributed.enabled", havingValue = "true", matchIfMissing = true)
public class DistributedLogoutService implements ServerLogoutHandler {

    private final DiscoveryClient discoveryClient;
    private final RestTemplate restTemplate;
    private final WebClient webClient;
    private final RedisTemplate<String, Object> redisTemplate;
    private final KafkaTemplate<String, Object> kafkaTemplate;
    private final ApplicationEventPublisher eventPublisher;
    private final ObjectMapper objectMapper;
    
    // Track logout propagation status
    private final ConcurrentHashMap<String, LogoutPropagationStatus> propagationStatus = new ConcurrentHashMap<>();
    
    @Value("${keycloak.auth-server-url:http://localhost:8080}")
    private String authServerUrl;
    
    @Value("${keycloak.realm:waqiti-fintech}")
    private String realm;
    
    @Value("${security.logout.propagation.timeout:5000}")
    private int propagationTimeoutMs;
    
    @Value("${security.logout.propagation.retry.max:3}")
    private int maxRetryAttempts;
    
    @Value("${security.logout.redis.blacklist.ttl:3600}")
    private int blacklistTtlSeconds;
    
    @Value("${security.logout.kafka.topic:logout-events}")
    private String logoutEventTopic;
    
    @Value("${security.logout.post-logout-redirect-uri:${app.base-url}/logout-success}")
    private String postLogoutRedirectUri;

    /**
     * Initiate distributed logout
     */
    public Mono<LogoutResponse> initiateLogout(Authentication authentication, ServerWebExchange exchange) {
        if (authentication == null || !authentication.isAuthenticated()) {
            return Mono.just(LogoutResponse.builder()
                .success(false)
                .message("No active session")
                .build());
        }
        
        String sessionId = extractSessionId(authentication);
        String userId = authentication.getName();
        String idToken = extractIdToken(authentication);
        
        log.info("Initiating distributed logout for user: {}, session: {}", userId, sessionId);
        
        // Create logout event
        LogoutEvent event = LogoutEvent.builder()
            .sessionId(sessionId)
            .userId(userId)
            .idToken(idToken)
            .timestamp(Instant.now())
            .initiator(getServiceName())
            .build();
        
        // Execute logout steps in parallel
        return Mono.when(
            // 1. Invalidate local session
            invalidateLocalSession(exchange),
            // 2. Blacklist tokens
            blacklistTokens(event),
            // 3. Publish logout event
            publishLogoutEvent(event),
            // 4. Propagate to all services
            propagateLogout(event),
            // 5. Notify Keycloak
            notifyKeycloak(event)
        )
        .then(buildLogoutResponse(event))
        .doOnSuccess(response -> log.info("Distributed logout completed for user: {}", userId))
        .doOnError(error -> log.error("Distributed logout failed for user: {}", userId, error))
        .onErrorResume(error -> Mono.just(LogoutResponse.builder()
            .success(false)
            .message("Logout partially completed: " + error.getMessage())
            .build()));
    }

    /**
     * Handle logout for reactive applications
     */
    @Override
    public Mono<Void> logout(WebFilterExchange exchange, Authentication authentication) {
        return initiateLogout(authentication, exchange.getExchange())
            .then();
    }

    /**
     * Invalidate local session
     */
    private Mono<Void> invalidateLocalSession(ServerWebExchange exchange) {
        return exchange.getSession()
            .flatMap(WebSession::invalidate)
            .doOnSuccess(v -> log.debug("Local session invalidated"))
            .onErrorResume(error -> {
                log.warn("Failed to invalidate local session", error);
                return Mono.empty();
            });
    }

    /**
     * Blacklist tokens in Redis
     */
    private Mono<Void> blacklistTokens(LogoutEvent event) {
        return Mono.fromRunnable(() -> {
            try {
                // Blacklist session ID
                String sessionKey = "blacklist:session:" + event.getSessionId();
                redisTemplate.opsForValue().set(sessionKey, event, blacklistTtlSeconds, TimeUnit.SECONDS);
                
                // Blacklist user tokens
                String userKey = "blacklist:user:" + event.getUserId();
                redisTemplate.opsForSet().add(userKey, event.getSessionId());
                redisTemplate.expire(userKey, blacklistTtlSeconds, TimeUnit.SECONDS);
                
                // Blacklist ID token if present
                if (event.getIdToken() != null) {
                    String tokenKey = "blacklist:token:" + extractJti(event.getIdToken());
                    redisTemplate.opsForValue().set(tokenKey, event, blacklistTtlSeconds, TimeUnit.SECONDS);
                }
                
                log.debug("Tokens blacklisted for session: {}", event.getSessionId());
            } catch (Exception e) {
                log.error("Failed to blacklist tokens", e);
            }
        });
    }

    /**
     * Publish logout event via Kafka
     */
    private Mono<Void> publishLogoutEvent(LogoutEvent event) {
        return Mono.fromFuture(() -> {
            CompletableFuture<Void> future = new CompletableFuture<>();
            
            kafkaTemplate.send(logoutEventTopic, event.getSessionId(), event)
                .whenComplete((result, error) -> {
                    if (error != null) {
                        log.error("Failed to publish logout event", error);
                        future.completeExceptionally(error);
                    } else {
                        log.debug("Logout event published: {}", event.getSessionId());
                        future.complete(null);
                    }
                });
            
            return future;
        });
    }

    /**
     * Propagate logout to all microservices
     */
    private Mono<Void> propagateLogout(LogoutEvent event) {
        // Track propagation status
        LogoutPropagationStatus status = new LogoutPropagationStatus(event.getSessionId());
        propagationStatus.put(event.getSessionId(), status);
        
        // Get all service instances
        List<String> services = discoveryClient.getServices();
        
        // Propagate to each service
        List<Mono<ServiceLogoutResult>> propagationTasks = services.stream()
            .filter(service -> !service.equals(getServiceName())) // Skip self
            .flatMap(service -> discoveryClient.getInstances(service).stream())
            .map(instance -> propagateToService(instance, event, status))
            .collect(Collectors.toList());
        
        // Execute all propagations in parallel
        return Flux.merge(propagationTasks)
            .collectList()
            .doOnSuccess(results -> {
                status.setCompleted(true);
                log.info("Logout propagated to {} services", results.size());
            })
            .then();
    }

    /**
     * Propagate logout to a specific service instance
     */
    private Mono<ServiceLogoutResult> propagateToService(ServiceInstance instance, 
                                                        LogoutEvent event, 
                                                        LogoutPropagationStatus status) {
        String serviceUrl = String.format("http://%s:%d/internal/logout",
            instance.getHost(), instance.getPort());
        
        return webClient.post()
            .uri(serviceUrl)
            .contentType(MediaType.APPLICATION_JSON)
            .bodyValue(event)
            .retrieve()
            .toBodilessEntity()
            .timeout(Duration.ofMillis(propagationTimeoutMs))
            .retryWhen(Retry.backoff(maxRetryAttempts, Duration.ofMillis(100)))
            .map(response -> {
                status.addSuccessfulService(instance.getServiceId());
                return ServiceLogoutResult.success(instance.getServiceId());
            })
            .onErrorResume(error -> {
                log.warn("Failed to propagate logout to service: {}", instance.getServiceId(), error);
                status.addFailedService(instance.getServiceId());
                return Mono.just(ServiceLogoutResult.failure(instance.getServiceId(), error.getMessage()));
            });
    }

    /**
     * Notify Keycloak about logout (back-channel logout)
     */
    private Mono<Void> notifyKeycloak(LogoutEvent event) {
        if (event.getIdToken() == null) {
            return Mono.empty();
        }
        
        String logoutUrl = buildKeycloakLogoutUrl(event.getIdToken());
        
        return webClient.get()
            .uri(logoutUrl)
            .retrieve()
            .toBodilessEntity()
            .timeout(Duration.ofMillis(propagationTimeoutMs))
            .doOnSuccess(v -> log.debug("Keycloak notified of logout"))
            .onErrorResume(error -> {
                log.warn("Failed to notify Keycloak of logout", error);
                return Mono.empty();
            })
            .then();
    }

    /**
     * Build Keycloak logout URL
     */
    private String buildKeycloakLogoutUrl(String idToken) {
        return String.format("%s/realms/%s/protocol/openid-connect/logout?id_token_hint=%s&post_logout_redirect_uri=%s",
            authServerUrl, realm, idToken, postLogoutRedirectUri);
    }

    /**
     * Handle incoming logout notification from other services
     */
    @KafkaListener(topics = "${security.logout.kafka.topic:logout-events}")
    public void handleLogoutNotification(LogoutEvent event) {
        if (event.getInitiator().equals(getServiceName())) {
            // Skip if we initiated this logout
            return;
        }
        
        log.info("Received logout notification for session: {}", event.getSessionId());
        
        // Invalidate local caches and sessions
        invalidateLocalResources(event);
        
        // Publish local event for components to handle
        eventPublisher.publishEvent(new LocalLogoutEvent(event));
    }

    /**
     * Invalidate local resources for a logout event
     */
    private void invalidateLocalResources(LogoutEvent event) {
        try {
            // Clear security context
            SecurityContextHolder.clearContext();
            
            // Clear any local session stores
            String sessionKey = "session:" + event.getSessionId();
            redisTemplate.delete(sessionKey);
            
            // Clear user-specific caches
            String userCacheKey = "user:cache:" + event.getUserId();
            redisTemplate.delete(userCacheKey);
            
            log.debug("Local resources invalidated for session: {}", event.getSessionId());
            
        } catch (Exception e) {
            log.error("Failed to invalidate local resources", e);
        }
    }

    /**
     * Check if a token/session is blacklisted
     */
    public boolean isBlacklisted(String identifier) {
        try {
            // Check session blacklist
            Boolean sessionBlacklisted = redisTemplate.hasKey("blacklist:session:" + identifier);
            if (Boolean.TRUE.equals(sessionBlacklisted)) {
                return true;
            }
            
            // Check token blacklist
            Boolean tokenBlacklisted = redisTemplate.hasKey("blacklist:token:" + identifier);
            return Boolean.TRUE.equals(tokenBlacklisted);
            
        } catch (Exception e) {
            log.error("Failed to check blacklist status", e);
            return false; // Fail open for availability
        }
    }

    /**
     * Build logout response
     */
    private Mono<LogoutResponse> buildLogoutResponse(LogoutEvent event) {
        return Mono.fromCallable(() -> {
            LogoutPropagationStatus status = propagationStatus.get(event.getSessionId());
            
            LogoutResponse response = LogoutResponse.builder()
                .success(true)
                .sessionId(event.getSessionId())
                .logoutUrl(buildKeycloakLogoutUrl(event.getIdToken()))
                .timestamp(Instant.now())
                .build();
            
            if (status != null) {
                response.setPropagationStatus(status.toMap());
                propagationStatus.remove(event.getSessionId());
            }
            
            return response;
        });
    }

    /**
     * Extract session ID from authentication
     */
    private String extractSessionId(Authentication authentication) {
        // Try to get from details
        if (authentication.getDetails() instanceof Map) {
            Map<?, ?> details = (Map<?, ?>) authentication.getDetails();
            Object sessionId = details.get("sessionId");
            if (sessionId != null) {
                return sessionId.toString();
            }
        }
        
        // Generate from principal
        return UUID.nameUUIDFromBytes(authentication.getName().getBytes()).toString();
    }

    /**
     * Extract ID token from authentication
     */
    private String extractIdToken(Authentication authentication) {
        if (authentication.getPrincipal() instanceof OidcUser) {
            OidcUser oidcUser = (OidcUser) authentication.getPrincipal();
            OidcIdToken idToken = oidcUser.getIdToken();
            return idToken != null ? idToken.getTokenValue() : null;
        }
        return null;
    }

    /**
     * Extract JTI from token
     */
    private String extractJti(String token) {
        try {
            String[] parts = token.split("\\.");
            if (parts.length > 1) {
                String payload = new String(Base64.getUrlDecoder().decode(parts[1]));
                Map<String, Object> claims = objectMapper.readValue(payload, Map.class);
                Object jti = claims.get("jti");
                return jti != null ? jti.toString() : UUID.randomUUID().toString();
            }
        } catch (Exception e) {
            log.warn("Failed to extract JTI from token", e);
        }
        return UUID.randomUUID().toString();
    }

    /**
     * Get current service name
     */
    private String getServiceName() {
        return System.getProperty("spring.application.name", "unknown-service");
    }

    /**
     * Logout event model
     */
    @Data
    @Builder
    public static class LogoutEvent {
        private String sessionId;
        private String userId;
        private String idToken;
        private Instant timestamp;
        private String initiator;
        private Map<String, Object> metadata;
    }

    /**
     * Local logout event for internal propagation
     */
    public static class LocalLogoutEvent {
        private final LogoutEvent logoutEvent;
        
        public LocalLogoutEvent(LogoutEvent logoutEvent) {
            this.logoutEvent = logoutEvent;
        }
        
        public LogoutEvent getLogoutEvent() {
            return logoutEvent;
        }
    }

    /**
     * Logout response model
     */
    @Data
    @Builder
    public static class LogoutResponse {
        private boolean success;
        private String message;
        private String sessionId;
        private String logoutUrl;
        private Instant timestamp;
        private Map<String, Object> propagationStatus;
    }

    /**
     * Service logout result
     */
    @Data
    @Builder
    private static class ServiceLogoutResult {
        private String serviceId;
        private boolean success;
        private String errorMessage;
        
        public static ServiceLogoutResult success(String serviceId) {
            return ServiceLogoutResult.builder()
                .serviceId(serviceId)
                .success(true)
                .build();
        }
        
        public static ServiceLogoutResult failure(String serviceId, String error) {
            return ServiceLogoutResult.builder()
                .serviceId(serviceId)
                .success(false)
                .errorMessage(error)
                .build();
        }
    }

    /**
     * Logout propagation status tracker
     */
    private static class LogoutPropagationStatus {
        private final String sessionId;
        private final Set<String> successfulServices = ConcurrentHashMap.newKeySet();
        private final Set<String> failedServices = ConcurrentHashMap.newKeySet();
        private volatile boolean completed = false;
        private final Instant startTime = Instant.now();
        
        public LogoutPropagationStatus(String sessionId) {
            this.sessionId = sessionId;
        }
        
        public void addSuccessfulService(String serviceId) {
            successfulServices.add(serviceId);
        }
        
        public void addFailedService(String serviceId) {
            failedServices.add(serviceId);
        }
        
        public void setCompleted(boolean completed) {
            this.completed = completed;
        }
        
        public Map<String, Object> toMap() {
            Map<String, Object> map = new HashMap<>();
            map.put("sessionId", sessionId);
            map.put("successfulServices", successfulServices);
            map.put("failedServices", failedServices);
            map.put("completed", completed);
            map.put("duration", Duration.between(startTime, Instant.now()).toMillis());
            return map;
        }
    }
}