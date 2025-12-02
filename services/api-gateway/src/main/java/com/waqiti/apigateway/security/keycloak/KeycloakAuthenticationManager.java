package com.waqiti.apigateway.security.keycloak;

import com.nimbusds.jose.JWSAlgorithm;
import com.nimbusds.jose.jwk.source.JWKSource;
import com.nimbusds.jose.jwk.source.RemoteJWKSet;
import com.nimbusds.jose.proc.JWSVerificationKeySelector;
import com.nimbusds.jose.proc.SecurityContext;
import com.nimbusds.jwt.JWTClaimsSet;
import com.nimbusds.jwt.proc.ConfigurableJWTProcessor;
import com.nimbusds.jwt.proc.DefaultJWTProcessor;
import com.waqiti.apigateway.security.WaqitiUserPrincipal;
import io.github.resilience4j.retry.Retry;
import io.github.resilience4j.retry.RetryConfig;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.stereotype.Component;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.core.publisher.Mono;
import reactor.core.scheduler.Schedulers;

import jakarta.annotation.PostConstruct;
import java.net.URL;
import java.time.Duration;
import java.time.Instant;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Collectors;

/**
 * Keycloak authentication manager for validating and processing Keycloak-issued JWT tokens.
 * Implements caching, retry logic, and proper token validation.
 */
@Component
@Slf4j
public class KeycloakAuthenticationManager {

    @Value("${keycloak.auth-server-url}")
    private String keycloakServerUrl;

    @Value("${keycloak.realm}")
    private String realm;

    @Value("${keycloak.resource}")
    private String clientId;

    @Value("${keycloak.verify-token-audience:true}")
    private boolean verifyTokenAudience;

    @Value("${keycloak.principal-attribute:preferred_username}")
    private String principalAttribute;

    @Value("${keycloak.use-resource-role-mappings:true}")
    private boolean useResourceRoleMappings;

    @Value("${gateway.auth.keycloak.cache.ttl:300}")
    private int cacheTtlSeconds;

    @Value("${gateway.auth.keycloak.retry.max-attempts:3}")
    private int maxRetryAttempts;

    @Value("${gateway.auth.keycloak.retry.wait-duration:1000}")
    private long retryWaitDuration;

    private WebClient webClient;
    private ConfigurableJWTProcessor<SecurityContext> jwtProcessor;
    private Retry retry;
    private final Map<String, CachedAuthentication> authCache = new ConcurrentHashMap<>();

    @PostConstruct
    public void init() {
        // Initialize WebClient
        this.webClient = WebClient.builder()
                .baseUrl(keycloakServerUrl)
                .codecs(configurer -> configurer.defaultCodecs().maxInMemorySize(10 * 1024 * 1024))
                .build();

        // Initialize JWT processor
        try {
            String jwkSetUrl = String.format("%s/realms/%s/protocol/openid-connect/certs", 
                    keycloakServerUrl, realm);
            
            JWKSource<SecurityContext> keySource = new RemoteJWKSet<>(new URL(jwkSetUrl));
            JWSAlgorithm expectedJWSAlg = JWSAlgorithm.RS256;
            JWSVerificationKeySelector<SecurityContext> keySelector = 
                    new JWSVerificationKeySelector<>(expectedJWSAlg, keySource);
            
            jwtProcessor = new DefaultJWTProcessor<>();
            jwtProcessor.setJWSKeySelector(keySelector);
            
            log.info("Keycloak JWT processor initialized with JWK URL: {}", jwkSetUrl);
        } catch (Exception e) {
            log.error("Failed to initialize Keycloak JWT processor", e);
            throw new RuntimeException("Failed to initialize Keycloak authentication", e);
        }

        // Initialize retry mechanism
        RetryConfig retryConfig = RetryConfig.custom()
                .maxAttempts(maxRetryAttempts)
                .waitDuration(Duration.ofMillis(retryWaitDuration))
                .retryExceptions(Exception.class)
                .build();
        
        this.retry = Retry.of("keycloak-auth", retryConfig);
        
        // Start cache cleanup task
        startCacheCleanup();
    }

    public Mono<Authentication> authenticate(String token, String requestId) {
        log.debug("Authenticating token for request: {}", requestId);
        
        // Check cache first
        CachedAuthentication cached = authCache.get(token);
        if (cached != null && !cached.isExpired()) {
            log.debug("Using cached authentication for request: {}", requestId);
            return Mono.just(cached.authentication);
        }
        
        return Mono.fromCallable(() -> validateToken(token))
                .subscribeOn(Schedulers.boundedElastic())
                .flatMap(claims -> createAuthentication(claims, token))
                .doOnSuccess(auth -> {
                    // Cache successful authentication
                    authCache.put(token, new CachedAuthentication(auth, cacheTtlSeconds));
                    log.debug("Authentication successful for request: {}", requestId);
                })
                .doOnError(error -> log.error("Authentication failed for request {}: {}", 
                        requestId, error.getMessage()));
    }

    private JWTClaimsSet validateToken(String token) throws Exception {
        return Retry.decorateCallable(retry, () -> jwtProcessor.process(token, null))
                .call();
    }

    private Mono<Authentication> createAuthentication(JWTClaimsSet claims, String token) {
        try {
            // Validate token expiration
            Date expiration = claims.getExpirationTime();
            if (expiration != null && expiration.toInstant().isBefore(java.time.Instant.now())) {
                return Mono.error(new TokenExpiredException("Token has expired"));
            }

            // Validate audience if required
            if (verifyTokenAudience) {
                List<String> audiences = claims.getAudience();
                if (audiences == null || !audiences.contains(clientId)) {
                    return Mono.error(new InvalidTokenException("Token audience validation failed"));
                }
            }

            // Extract user information
            String userId = claims.getStringClaim("sub");
            String username = claims.getStringClaim(principalAttribute);
            String email = claims.getStringClaim("email");
            Boolean emailVerified = claims.getBooleanClaim("email_verified");
            
            // Extract roles
            Collection<GrantedAuthority> authorities = extractAuthorities(claims);
            
            // Create principal
            WaqitiUserPrincipal principal = WaqitiUserPrincipal.builder()
                    .userId(userId)
                    .username(username != null ? username : userId)
                    .email(email)
                    .emailVerified(emailVerified != null ? emailVerified : false)
                    .authorities(authorities)
                    .attributes(claims.getClaims())
                    .build();
            
            // Create authentication token
            UsernamePasswordAuthenticationToken authentication = 
                    new UsernamePasswordAuthenticationToken(principal, token, authorities);
            
            // Add additional details
            Map<String, Object> details = new HashMap<>();
            details.put("token_id", claims.getJWTID());
            details.put("issued_at", claims.getIssueTime());
            details.put("expiration", claims.getExpirationTime());
            details.put("issuer", claims.getIssuer());
            authentication.setDetails(details);
            
            return Mono.just(authentication);
            
        } catch (Exception e) {
            log.error("Failed to create authentication from token claims", e);
            return Mono.error(new InvalidTokenException("Failed to process token claims", e));
        }
    }

    @SuppressWarnings("unchecked")
    private Collection<GrantedAuthority> extractAuthorities(JWTClaimsSet claims) {
        Set<GrantedAuthority> authorities = new HashSet<>();
        
        try {
            // Extract realm roles
            Map<String, Object> realmAccess = claims.getJSONObjectClaim("realm_access");
            if (realmAccess != null && realmAccess.containsKey("roles")) {
                List<String> realmRoles = (List<String>) realmAccess.get("roles");
                authorities.addAll(realmRoles.stream()
                        .map(role -> new SimpleGrantedAuthority("ROLE_" + role.toUpperCase()))
                        .collect(Collectors.toSet()));
            }
            
            // Extract resource/client roles if configured
            if (useResourceRoleMappings) {
                Map<String, Object> resourceAccess = claims.getJSONObjectClaim("resource_access");
                if (resourceAccess != null && resourceAccess.containsKey(clientId)) {
                    Map<String, Object> clientAccess = (Map<String, Object>) resourceAccess.get(clientId);
                    if (clientAccess != null && clientAccess.containsKey("roles")) {
                        List<String> clientRoles = (List<String>) clientAccess.get("roles");
                        authorities.addAll(clientRoles.stream()
                                .map(role -> new SimpleGrantedAuthority("ROLE_" + clientId.toUpperCase() + 
                                        "_" + role.toUpperCase()))
                                .collect(Collectors.toSet()));
                    }
                }
            }
            
            // Extract scopes as authorities
            String scope = claims.getStringClaim("scope");
            if (scope != null && !scope.isEmpty()) {
                String[] scopes = scope.split(" ");
                authorities.addAll(Arrays.stream(scopes)
                        .map(s -> new SimpleGrantedAuthority("SCOPE_" + s.toUpperCase()))
                        .collect(Collectors.toSet()));
            }
            
        } catch (Exception e) {
            log.warn("Error extracting authorities from token claims: {}", e.getMessage());
        }
        
        // Add default user authority if no authorities found
        if (authorities.isEmpty()) {
            authorities.add(new SimpleGrantedAuthority("ROLE_USER"));
        }
        
        return authorities;
    }

    public Mono<Boolean> introspectToken(String token) {
        String introspectionUrl = String.format("%s/realms/%s/protocol/openid-connect/token/introspect",
                keycloakServerUrl, realm);
        
        return webClient.post()
                .uri(introspectionUrl)
                .bodyValue("token=" + token + "&token_type_hint=access_token")
                .header("Content-Type", "application/x-www-form-urlencoded")
                .retrieve()
                .bodyToMono(Map.class)
                .map(response -> Boolean.TRUE.equals(response.get("active")))
                .onErrorReturn(false);
    }

    public Mono<Void> revokeToken(String token) {
        String revocationUrl = String.format("%s/realms/%s/protocol/openid-connect/revoke",
                keycloakServerUrl, realm);
        
        return webClient.post()
                .uri(revocationUrl)
                .bodyValue("token=" + token + "&token_type_hint=access_token")
                .header("Content-Type", "application/x-www-form-urlencoded")
                .retrieve()
                .bodyToMono(Void.class)
                .doOnSuccess(v -> {
                    // Remove from cache
                    authCache.remove(token);
                    log.info("Token revoked successfully");
                })
                .doOnError(error -> log.error("Failed to revoke token: {}", error.getMessage()));
    }

    private void startCacheCleanup() {
        // Periodic cache cleanup task
        Schedulers.boundedElastic().schedulePeriodically(() -> {
            try {
                int removed = 0;
                Iterator<Map.Entry<String, CachedAuthentication>> iterator = authCache.entrySet().iterator();
                while (iterator.hasNext()) {
                    Map.Entry<String, CachedAuthentication> entry = iterator.next();
                    if (entry.getValue().isExpired()) {
                        iterator.remove();
                        removed++;
                    }
                }
                if (removed > 0) {
                    log.debug("Removed {} expired entries from authentication cache", removed);
                }
            } catch (Exception e) {
                log.error("Error during cache cleanup", e);
            }
        }, cacheTtlSeconds, cacheTtlSeconds, java.util.concurrent.TimeUnit.SECONDS);
    }

    private static class CachedAuthentication {
        private final Authentication authentication;
        private final Instant expiresAt;
        
        public CachedAuthentication(Authentication authentication, int ttlSeconds) {
            this.authentication = authentication;
            this.expiresAt = Instant.now().plusSeconds(ttlSeconds);
        }
        
        public boolean isExpired() {
            return Instant.now().isAfter(expiresAt);
        }
    }

    public static class TokenExpiredException extends RuntimeException {
        private static final long serialVersionUID = 1L;
        
        public TokenExpiredException(String message) {
            super(message);
        }
        
        public TokenExpiredException(String message, Throwable cause) {
            super(message, cause);
        }
    }

    public static class InvalidTokenException extends RuntimeException {
        private static final long serialVersionUID = 1L;
        
        public InvalidTokenException(String message) {
            super(message);
        }
        
        public InvalidTokenException(String message, Throwable cause) {
            super(message, cause);
        }
    }
}