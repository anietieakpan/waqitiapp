/**
 * Token Introspection Service
 * Implements RFC 7662 OAuth 2.0 Token Introspection
 * Provides online token validation for enhanced security
 */
package com.waqiti.common.security.token;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.Builder;
import lombok.Data;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.actuate.health.Health;
import org.springframework.boot.actuate.health.HealthIndicator;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.*;
import org.springframework.security.oauth2.core.OAuth2AuthenticationException;
import org.springframework.security.oauth2.core.OAuth2Error;
import org.springframework.security.oauth2.server.resource.introspection.BadOpaqueTokenException;
import org.springframework.security.oauth2.server.resource.introspection.OAuth2IntrospectionException;
import org.springframework.security.oauth2.server.resource.introspection.OpaqueTokenIntrospector;
import org.springframework.security.oauth2.server.resource.introspection.ReactiveOpaqueTokenIntrospector;
import org.springframework.security.oauth2.core.OAuth2AuthenticatedPrincipal;
import org.springframework.security.oauth2.core.DefaultOAuth2AuthenticatedPrincipal;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.stereotype.Service;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;
import org.springframework.web.client.RestTemplate;
import org.springframework.web.reactive.function.BodyInserters;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.core.publisher.Mono;
import reactor.util.retry.Retry;

import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.Instant;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicLong;

/**
 * Advanced token introspection with caching and resilience
 * Implements both blocking and reactive introspection
 */
@Slf4j
@Service
@Configuration
@RequiredArgsConstructor
@ConditionalOnProperty(name = "security.oauth2.introspection.enabled", havingValue = "true")
public class TokenIntrospectionService implements OpaqueTokenIntrospector, HealthIndicator {

    private final RestTemplate restTemplate;
    private final WebClient webClient;
    private final ObjectMapper objectMapper;
    
    // Metrics
    private final AtomicLong introspectionRequests = new AtomicLong(0);
    private final AtomicLong introspectionSuccesses = new AtomicLong(0);
    private final AtomicLong introspectionFailures = new AtomicLong(0);
    private final AtomicLong cacheHits = new AtomicLong(0);
    private final AtomicLong cacheMisses = new AtomicLong(0);
    
    // Token cache with TTL
    private final ConcurrentHashMap<String, CachedIntrospection> tokenCache = new ConcurrentHashMap<>();
    
    @Value("${keycloak.auth-server-url:http://localhost:8080}")
    private String authServerUrl;
    
    @Value("${keycloak.realm:waqiti-fintech}")
    private String realm;
    
    @Value("${keycloak.resource:${spring.application.name}}")
    private String clientId;
    
    @Value("${keycloak.credentials.secret:${vault:secret/keycloak/${spring.application.name}/client-secret}}")
    private String clientSecret;
    
    @Value("${security.oauth2.introspection.cache.enabled:true}")
    private boolean cacheEnabled;
    
    @Value("${security.oauth2.introspection.cache.ttl:300}")
    private int cacheTtlSeconds;
    
    @Value("${security.oauth2.introspection.timeout:5000}")
    private int timeoutMillis;
    
    @Value("${security.oauth2.introspection.retry.max-attempts:3}")
    private int maxRetryAttempts;
    
    @Value("${security.oauth2.introspection.required-scopes:}")
    private List<String> requiredScopes;

    /**
     * Blocking token introspection
     */
    @Override
    public OAuth2AuthenticatedPrincipal introspect(String token) {
        introspectionRequests.incrementAndGet();
        
        try {
            // Check cache first
            if (cacheEnabled) {
                CachedIntrospection cached = getFromCache(token);
                if (cached != null) {
                    cacheHits.incrementAndGet();
                    return cached.getPrincipal();
                }
                cacheMisses.incrementAndGet();
            }
            
            // Perform introspection
            IntrospectionResponse response = performIntrospection(token);
            
            // Validate response
            validateIntrospectionResponse(response);
            
            // Create principal
            OAuth2AuthenticatedPrincipal principal = createPrincipal(response);
            
            // Cache result
            if (cacheEnabled && response.isActive()) {
                cacheIntrospection(token, principal, response);
            }
            
            introspectionSuccesses.incrementAndGet();
            return principal;
            
        } catch (Exception e) {
            introspectionFailures.incrementAndGet();
            log.error("Token introspection failed for token", e);
            throw new BadOpaqueTokenException("Token introspection failed", e);
        }
    }

    /**
     * Reactive token introspection - provides reactive alternative
     * Can be used by reactive applications
     */
    public Mono<OAuth2AuthenticatedPrincipal> introspectReactive(String token) {
        return Mono.defer(() -> {
            introspectionRequests.incrementAndGet();
            
            // Check cache first
            if (cacheEnabled) {
                CachedIntrospection cached = getFromCache(token);
                if (cached != null) {
                    cacheHits.incrementAndGet();
                    return Mono.just(cached.getPrincipal());
                }
                cacheMisses.incrementAndGet();
            }
            
            // Perform reactive introspection
            return performReactiveIntrospection(token)
                .doOnSuccess(response -> validateIntrospectionResponse(response))
                .map(this::createPrincipal)
                .doOnSuccess(principal -> {
                    introspectionSuccesses.incrementAndGet();
                    if (cacheEnabled) {
                        // Cache in background
                        Mono.fromRunnable(() -> 
                            cacheIntrospection(token, principal, null))
                            .subscribe();
                    }
                })
                .doOnError(error -> {
                    introspectionFailures.incrementAndGet();
                    log.error("Reactive token introspection failed", error);
                })
                .onErrorMap(e -> new BadOpaqueTokenException("Token introspection failed", e));
        });
    }

    /**
     * Perform blocking introspection request
     */
    private IntrospectionResponse performIntrospection(String token) {
        String introspectionUri = getIntrospectionEndpoint();
        
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_FORM_URLENCODED);
        headers.setBasicAuth(clientId, clientSecret);
        
        MultiValueMap<String, String> body = new LinkedMultiValueMap<>();
        body.add("token", token);
        body.add("token_type_hint", "access_token");
        
        HttpEntity<MultiValueMap<String, String>> request = new HttpEntity<>(body, headers);
        
        try {
            ResponseEntity<String> response = restTemplate.exchange(
                introspectionUri,
                HttpMethod.POST,
                request,
                String.class
            );
            
            if (response.getStatusCode() != HttpStatus.OK) {
                throw new OAuth2IntrospectionException(
                    "Introspection endpoint returned " + response.getStatusCode()
                );
            }
            
            return parseIntrospectionResponse(response.getBody());
            
        } catch (Exception e) {
            log.error("Failed to introspect token", e);
            throw new OAuth2IntrospectionException("Token introspection failed", e);
        }
    }

    /**
     * Perform reactive introspection request
     */
    private Mono<IntrospectionResponse> performReactiveIntrospection(String token) {
        String introspectionUri = getIntrospectionEndpoint();
        
        MultiValueMap<String, String> formData = new LinkedMultiValueMap<>();
        formData.add("token", token);
        formData.add("token_type_hint", "access_token");
        
        return webClient.post()
            .uri(introspectionUri)
            .headers(headers -> headers.setBasicAuth(clientId, clientSecret))
            .contentType(MediaType.APPLICATION_FORM_URLENCODED)
            .body(BodyInserters.fromFormData(formData))
            .retrieve()
            .onStatus(HttpStatusCode::isError, response -> 
                Mono.error(new OAuth2IntrospectionException(
                    "Introspection failed with status " + response.statusCode()
                ))
            )
            .bodyToMono(String.class)
            .map(this::parseIntrospectionResponse)
            .timeout(Duration.ofMillis(timeoutMillis))
            .retryWhen(Retry.backoff(maxRetryAttempts, Duration.ofMillis(100))
                .maxBackoff(Duration.ofSeconds(2))
                .doBeforeRetry(signal -> 
                    log.warn("Retrying introspection, attempt {}", signal.totalRetries() + 1)
                )
            );
    }

    /**
     * Parse introspection response
     */
    private IntrospectionResponse parseIntrospectionResponse(String responseBody) {
        try {
            JsonNode json = objectMapper.readTree(responseBody);
            
            return IntrospectionResponse.builder()
                .active(json.path("active").asBoolean(false))
                .scope(json.path("scope").asText())
                .clientId(json.path("client_id").asText())
                .username(json.path("username").asText())
                .tokenType(json.path("token_type").asText())
                .exp(json.path("exp").asLong(0))
                .iat(json.path("iat").asLong(0))
                .nbf(json.path("nbf").asLong(0))
                .sub(json.path("sub").asText())
                .aud(parseAudience(json.path("aud")))
                .iss(json.path("iss").asText())
                .jti(json.path("jti").asText())
                .claims(json)
                .build();
                
        } catch (Exception e) {
            log.error("Failed to parse introspection response: {}", responseBody, e);
            throw new OAuth2IntrospectionException("Invalid introspection response", e);
        }
    }

    /**
     * Parse audience claim
     */
    private List<String> parseAudience(JsonNode audNode) {
        List<String> audience = new ArrayList<>();
        if (audNode.isArray()) {
            audNode.forEach(node -> audience.add(node.asText()));
        } else if (audNode.isTextual()) {
            audience.add(audNode.asText());
        }
        return audience;
    }

    /**
     * Validate introspection response
     */
    private void validateIntrospectionResponse(IntrospectionResponse response) {
        // Check if token is active
        if (!response.isActive()) {
            throw new BadOpaqueTokenException("Token is not active");
        }
        
        // Check expiration
        if (response.getExp() > 0) {
            Instant expiry = Instant.ofEpochSecond(response.getExp());
            if (expiry.isBefore(Instant.now())) {
                throw new BadOpaqueTokenException("Token has expired");
            }
        }
        
        // Check not-before
        if (response.getNbf() > 0) {
            Instant notBefore = Instant.ofEpochSecond(response.getNbf());
            if (notBefore.isAfter(Instant.now())) {
                throw new BadOpaqueTokenException("Token not yet valid");
            }
        }
        
        // Validate required scopes
        if (!requiredScopes.isEmpty()) {
            Set<String> tokenScopes = new HashSet<>(Arrays.asList(response.getScope().split(" ")));
            if (!tokenScopes.containsAll(requiredScopes)) {
                throw new BadOpaqueTokenException("Token missing required scopes");
            }
        }
        
        // Validate audience
        if (!response.getAud().isEmpty() && !response.getAud().contains(clientId)) {
            log.warn("Token audience mismatch. Expected: {}, Got: {}", clientId, response.getAud());
        }
    }

    /**
     * Create OAuth2 principal from introspection response
     */
    private OAuth2AuthenticatedPrincipal createPrincipal(IntrospectionResponse response) {
        Map<String, Object> attributes = new HashMap<>();
        
        // Standard claims
        attributes.put("active", response.isActive());
        attributes.put("sub", response.getSub());
        attributes.put("username", response.getUsername());
        attributes.put("client_id", response.getClientId());
        attributes.put("scope", response.getScope());
        attributes.put("aud", response.getAud());
        attributes.put("iss", response.getIss());
        attributes.put("exp", response.getExp());
        attributes.put("iat", response.getIat());
        attributes.put("nbf", response.getNbf());
        attributes.put("jti", response.getJti());
        
        // Additional claims from response
        if (response.getClaims() != null) {
            response.getClaims().fields().forEachRemaining(entry -> {
                if (!attributes.containsKey(entry.getKey())) {
                    attributes.put(entry.getKey(), entry.getValue().asText());
                }
            });
        }
        
        // Extract authorities
        Collection<GrantedAuthority> authorities = extractAuthorities(response);
        
        return new DefaultOAuth2AuthenticatedPrincipal(
            response.getUsername() != null ? response.getUsername() : response.getSub(),
            attributes,
            authorities
        );
    }

    /**
     * Extract authorities from introspection response
     */
    private Collection<GrantedAuthority> extractAuthorities(IntrospectionResponse response) {
        Set<GrantedAuthority> authorities = new HashSet<>();
        
        // Extract scopes as authorities
        if (response.getScope() != null && !response.getScope().isEmpty()) {
            Arrays.stream(response.getScope().split(" "))
                .map(scope -> new SimpleGrantedAuthority("SCOPE_" + scope))
                .forEach(authorities::add);
        }
        
        // Extract roles from claims if present
        JsonNode claims = response.getClaims();
        if (claims != null) {
            // Realm roles
            JsonNode realmAccess = claims.path("realm_access");
            if (!realmAccess.isMissingNode()) {
                JsonNode roles = realmAccess.path("roles");
                if (roles.isArray()) {
                    roles.forEach(role -> 
                        authorities.add(new SimpleGrantedAuthority("ROLE_" + role.asText().toUpperCase()))
                    );
                }
            }
            
            // Resource roles
            JsonNode resourceAccess = claims.path("resource_access");
            if (!resourceAccess.isMissingNode()) {
                resourceAccess.fields().forEachRemaining(entry -> {
                    JsonNode resource = entry.getValue();
                    JsonNode roles = resource.path("roles");
                    if (roles.isArray()) {
                        roles.forEach(role -> 
                            authorities.add(new SimpleGrantedAuthority(
                                "ROLE_" + entry.getKey().toUpperCase() + "_" + role.asText().toUpperCase()
                            ))
                        );
                    }
                });
            }
        }
        
        return authorities;
    }

    /**
     * Cache introspection result
     */
    private void cacheIntrospection(String token, OAuth2AuthenticatedPrincipal principal, 
                                   IntrospectionResponse response) {
        // Calculate TTL based on token expiry or default
        long ttl = cacheTtlSeconds;
        if (response != null && response.getExp() > 0) {
            long tokenTtl = response.getExp() - Instant.now().getEpochSecond();
            ttl = Math.min(tokenTtl, cacheTtlSeconds);
        }
        
        if (ttl > 0) {
            CachedIntrospection cached = new CachedIntrospection(
                principal,
                Instant.now().plusSeconds(ttl)
            );
            tokenCache.put(token, cached);
            
            // Clean expired entries periodically
            cleanExpiredCache();
        }
    }

    /**
     * Get from cache if valid
     */
    private CachedIntrospection getFromCache(String token) {
        CachedIntrospection cached = tokenCache.get(token);
        if (cached != null) {
            if (cached.isValid()) {
                return cached;
            } else {
                tokenCache.remove(token);
            }
        }
        return null;
    }

    /**
     * Clean expired cache entries
     */
    private void cleanExpiredCache() {
        tokenCache.entrySet().removeIf(entry -> !entry.getValue().isValid());
    }

    /**
     * Get introspection endpoint URL
     */
    private String getIntrospectionEndpoint() {
        return String.format("%s/realms/%s/protocol/openid-connect/token/introspect",
            authServerUrl, realm);
    }

    /**
     * Health indicator implementation
     */
    @Override
    public Health health() {
        try {
            // Test introspection endpoint availability
            String testToken = "test";
            HttpHeaders headers = new HttpHeaders();
            headers.setContentType(MediaType.APPLICATION_FORM_URLENCODED);
            headers.setBasicAuth(clientId, clientSecret);
            
            MultiValueMap<String, String> body = new LinkedMultiValueMap<>();
            body.add("token", testToken);
            
            HttpEntity<MultiValueMap<String, String>> request = new HttpEntity<>(body, headers);
            
            ResponseEntity<String> response = restTemplate.exchange(
                getIntrospectionEndpoint(),
                HttpMethod.POST,
                request,
                String.class
            );
            
            if (response.getStatusCode() == HttpStatus.OK) {
                return Health.up()
                    .withDetail("endpoint", getIntrospectionEndpoint())
                    .withDetail("total_requests", introspectionRequests.get())
                    .withDetail("successful_requests", introspectionSuccesses.get())
                    .withDetail("failed_requests", introspectionFailures.get())
                    .withDetail("cache_hits", cacheHits.get())
                    .withDetail("cache_misses", cacheMisses.get())
                    .withDetail("cache_size", tokenCache.size())
                    .build();
            }
            
        } catch (Exception e) {
            return Health.down()
                .withDetail("error", e.getMessage())
                .build();
        }
        
        return Health.down().build();
    }

    /**
     * Introspection response model
     */
    @Data
    @Builder
    private static class IntrospectionResponse {
        private boolean active;
        private String scope;
        private String clientId;
        private String username;
        private String tokenType;
        private long exp;
        private long iat;
        private long nbf;
        private String sub;
        private List<String> aud;
        private String iss;
        private String jti;
        private JsonNode claims;
    }

    /**
     * Cached introspection result
     */
    private static class CachedIntrospection {
        private final OAuth2AuthenticatedPrincipal principal;
        private final Instant expiresAt;
        
        public CachedIntrospection(OAuth2AuthenticatedPrincipal principal, Instant expiresAt) {
            this.principal = principal;
            this.expiresAt = expiresAt;
        }
        
        public OAuth2AuthenticatedPrincipal getPrincipal() {
            return principal;
        }
        
        public boolean isValid() {
            return Instant.now().isBefore(expiresAt);
        }
    }

}