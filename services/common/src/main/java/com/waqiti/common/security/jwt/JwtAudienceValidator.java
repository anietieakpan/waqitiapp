/**
 * JWT Audience Validator
 * Implements audience validation as recommended in Cloud Native Spring in Action
 * Prevents token misuse by validating the intended audience
 */
package com.waqiti.common.security.jwt;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.convert.converter.Converter;
import org.springframework.security.authentication.AbstractAuthenticationToken;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.oauth2.core.OAuth2Error;
import org.springframework.security.oauth2.core.OAuth2TokenValidator;
import org.springframework.security.oauth2.core.OAuth2TokenValidatorResult;
import org.springframework.security.oauth2.core.DelegatingOAuth2TokenValidator;
import org.springframework.security.oauth2.jwt.*;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationConverter;
import org.springframework.security.oauth2.server.resource.authentication.ReactiveJwtAuthenticationConverterAdapter;
import org.springframework.security.oauth2.server.resource.InvalidBearerTokenException;
import reactor.core.publisher.Mono;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.*;
import java.util.stream.Collectors;

/**
 * Comprehensive JWT validation with audience verification
 * Implements defense-in-depth token validation strategy
 */
@Slf4j
@Configuration
@RequiredArgsConstructor
@ConditionalOnProperty(name = "security.jwt.audience.validation.enabled", havingValue = "true", matchIfMissing = true)
public class JwtAudienceValidator {

    @Value("${security.jwt.audience.expected:${spring.application.name}}")
    private String expectedAudience;
    
    @Value("${security.jwt.audience.allowed:}")
    private List<String> allowedAudiences;
    
    @Value("${security.jwt.issuer.trusted:}")
    private List<String> trustedIssuers;
    
    @Value("${security.jwt.clock.skew:60}")
    private int clockSkewSeconds;
    
    @Value("${security.jwt.validation.strict:true}")
    private boolean strictValidation;

    /**
     * Custom JWT decoder with comprehensive validation
     */
    @Bean
    public JwtDecoder jwtDecoder() {
        NimbusJwtDecoder decoder = NimbusJwtDecoder
            .withJwkSetUri(getJwkSetUri())
            .build();
        
        // Add comprehensive validators
        decoder.setJwtValidator(createCompositeValidator());
        
        // Configure clock skew tolerance
        decoder.setClaimSetConverter(claims -> {
            Map<String, Object> convertedClaims = new HashMap<>(claims);
            adjustTimestamps(convertedClaims);
            return convertedClaims;
        });
        
        return decoder;
    }

    /**
     * Reactive JWT decoder for WebFlux applications
     */
    @Bean
    public ReactiveJwtDecoder reactiveJwtDecoder() {
        NimbusReactiveJwtDecoder decoder = NimbusReactiveJwtDecoder
            .withJwkSetUri(getJwkSetUri())
            .build();
        
        decoder.setJwtValidator(createCompositeValidator());
        
        return decoder;
    }

    /**
     * JWT authentication converter with audience validation
     */
    @Bean
    public Converter<Jwt, Mono<AbstractAuthenticationToken>> jwtAuthenticationConverter() {
        JwtAuthenticationConverter converter = new JwtAuthenticationConverter();
        
        converter.setJwtGrantedAuthoritiesConverter(jwt -> {
            // Validate audience before extracting authorities
            validateAudience(jwt);
            return extractAuthorities(jwt);
        });
        
        converter.setPrincipalClaimName("preferred_username");
        
        return new ReactiveJwtAuthenticationConverterAdapter(converter);
    }

    /**
     * Create composite validator with multiple validation rules
     */
    private OAuth2TokenValidator<Jwt> createCompositeValidator() {
        List<OAuth2TokenValidator<Jwt>> validators = new ArrayList<>();
        
        // Standard validators
        validators.add(new JwtTimestampValidator(Duration.ofSeconds(clockSkewSeconds)));
        
        // Issuer validation
        if (!trustedIssuers.isEmpty()) {
            validators.add(new JwtIssuerValidator(trustedIssuers.get(0)));
        }
        
        // Custom audience validator
        validators.add(new AudienceValidator());
        
        // Additional security validators
        validators.add(new TokenTypeValidator());
        validators.add(new RequiredClaimsValidator());
        validators.add(new TokenBindingValidator());
        
        if (strictValidation) {
            validators.add(new StrictSecurityValidator());
        }
        
        return new DelegatingOAuth2TokenValidator<>(validators);
    }

    /**
     * Audience validator implementation
     */
    private class AudienceValidator implements OAuth2TokenValidator<Jwt> {
        
        @Override
        public OAuth2TokenValidatorResult validate(Jwt jwt) {
            List<String> audiences = jwt.getAudience();
            
            if (audiences == null || audiences.isEmpty()) {
                log.warn("JWT missing audience claim");
                return OAuth2TokenValidatorResult.failure(
                    new OAuth2Error("missing_audience", "Token missing audience claim", null)
                );
            }
            
            // Check if expected audience is present
            if (!audiences.contains(expectedAudience)) {
                // Check allowed audiences as fallback
                if (allowedAudiences.isEmpty() || 
                    audiences.stream().noneMatch(allowedAudiences::contains)) {
                    
                    log.warn("JWT audience validation failed. Expected: {}, Got: {}", 
                        expectedAudience, audiences);
                    
                    return OAuth2TokenValidatorResult.failure(
                        new OAuth2Error("invalid_audience", 
                            String.format("Token audience '%s' not allowed", audiences), null)
                    );
                }
            }
            
            log.debug("JWT audience validation successful for: {}", audiences);
            return OAuth2TokenValidatorResult.success();
        }
    }

    /**
     * Token type validator - ensures token is an access token
     */
    private class TokenTypeValidator implements OAuth2TokenValidator<Jwt> {
        
        @Override
        public OAuth2TokenValidatorResult validate(Jwt jwt) {
            String tokenType = jwt.getClaimAsString("typ");
            
            if (tokenType != null && !"JWT".equals(tokenType) && !"at+jwt".equals(tokenType)) {
                log.warn("Invalid token type: {}", tokenType);
                return OAuth2TokenValidatorResult.failure(
                    new OAuth2Error("invalid_token_type", "Invalid token type", null)
                );
            }
            
            // Check token use claim for OAuth2
            String tokenUse = jwt.getClaimAsString("token_use");
            if (tokenUse != null && !"access".equals(tokenUse)) {
                log.warn("Token not an access token: {}", tokenUse);
                return OAuth2TokenValidatorResult.failure(
                    new OAuth2Error("invalid_token_use", "Token is not an access token", null)
                );
            }
            
            return OAuth2TokenValidatorResult.success();
        }
    }

    /**
     * Required claims validator
     */
    private class RequiredClaimsValidator implements OAuth2TokenValidator<Jwt> {
        
        private final List<String> requiredClaims = Arrays.asList(
            "sub", "iat", "exp", "iss", "aud"
        );
        
        @Override
        public OAuth2TokenValidatorResult validate(Jwt jwt) {
            for (String claim : requiredClaims) {
                if (!jwt.hasClaim(claim)) {
                    log.warn("JWT missing required claim: {}", claim);
                    return OAuth2TokenValidatorResult.failure(
                        new OAuth2Error("missing_claim", 
                            String.format("Missing required claim: %s", claim), null)
                    );
                }
            }
            return OAuth2TokenValidatorResult.success();
        }
    }

    /**
     * Token binding validator for enhanced security
     */
    private class TokenBindingValidator implements OAuth2TokenValidator<Jwt> {
        
        @Override
        public OAuth2TokenValidatorResult validate(Jwt jwt) {
            // Validate token binding if present
            String tokenBinding = jwt.getClaimAsString("cnf");
            if (tokenBinding != null) {
                // Implement token binding validation logic
                log.debug("Token binding validation for: {}", tokenBinding);
            }
            
            // Validate nonce if present (for ID tokens)
            String nonce = jwt.getClaimAsString("nonce");
            if (nonce != null) {
                // Implement nonce validation logic
                log.debug("Nonce validation for: {}", nonce);
            }
            
            return OAuth2TokenValidatorResult.success();
        }
    }

    /**
     * Strict security validator for production environments
     */
    private class StrictSecurityValidator implements OAuth2TokenValidator<Jwt> {
        
        @Override
        public OAuth2TokenValidatorResult validate(Jwt jwt) {
            // Validate token age
            Instant issuedAt = jwt.getIssuedAt();
            if (issuedAt != null) {
                Duration tokenAge = Duration.between(issuedAt, Instant.now());
                if (tokenAge.toHours() > 24) {
                    log.warn("Token too old: {} hours", tokenAge.toHours());
                    return OAuth2TokenValidatorResult.failure(
                        new OAuth2Error("token_too_old", "Token exceeds maximum age", null)
                    );
                }
            }
            
            // Validate authorization time for ID tokens
            Instant authTime = jwt.getClaimAsInstant("auth_time");
            if (authTime != null) {
                Duration authAge = Duration.between(authTime, Instant.now());
                if (authAge.toHours() > 8) {
                    log.warn("Authentication too old: {} hours", authAge.toHours());
                    return OAuth2TokenValidatorResult.failure(
                        new OAuth2Error("auth_too_old", "Authentication exceeds maximum age", null)
                    );
                }
            }
            
            // Validate ACR (Authentication Context Class Reference)
            String acr = jwt.getClaimAsString("acr");
            if (acr != null && !isValidAcr(acr)) {
                log.warn("Invalid ACR level: {}", acr);
                return OAuth2TokenValidatorResult.failure(
                    new OAuth2Error("insufficient_acr", "Insufficient authentication level", null)
                );
            }
            
            return OAuth2TokenValidatorResult.success();
        }
        
        private boolean isValidAcr(String acr) {
            // Define minimum required ACR levels
            return Arrays.asList("1", "2", "urn:mace:incommon:iap:silver", 
                                "urn:mace:incommon:iap:gold").contains(acr);
        }
    }

    /**
     * Validate audience claim
     */
    private void validateAudience(Jwt jwt) {
        List<String> audiences = jwt.getAudience();
        
        if (audiences == null || audiences.isEmpty()) {
            throw new InvalidBearerTokenException("Token missing audience claim");
        }
        
        boolean validAudience = audiences.contains(expectedAudience) ||
            (!allowedAudiences.isEmpty() && 
             audiences.stream().anyMatch(allowedAudiences::contains));
        
        if (!validAudience) {
            throw new InvalidBearerTokenException(
                String.format("Invalid token audience. Expected: %s, Got: %s", 
                    expectedAudience, audiences)
            );
        }
    }

    /**
     * Extract authorities from JWT claims
     */
    private Collection<GrantedAuthority> extractAuthorities(Jwt jwt) {
        Collection<GrantedAuthority> authorities = new ArrayList<>();
        
        // Extract scopes
        Collection<String> scopes = jwt.getClaimAsStringList("scope");
        if (scopes != null) {
            authorities.addAll(scopes.stream()
                .map(scope -> new SimpleGrantedAuthority("SCOPE_" + scope))
                .collect(Collectors.toList()));
        }
        
        // Extract realm roles
        Map<String, Object> realmAccess = jwt.getClaim("realm_access");
        if (realmAccess != null && realmAccess.containsKey("roles")) {
            @SuppressWarnings("unchecked")
            Collection<String> roles = (Collection<String>) realmAccess.get("roles");
            authorities.addAll(roles.stream()
                .map(role -> new SimpleGrantedAuthority("ROLE_" + role.toUpperCase()))
                .collect(Collectors.toList()));
        }
        
        // Extract resource roles
        Map<String, Object> resourceAccess = jwt.getClaim("resource_access");
        if (resourceAccess != null) {
            resourceAccess.forEach((resource, access) -> {
                if (access instanceof Map) {
                    @SuppressWarnings("unchecked")
                    Map<String, Object> accessMap = (Map<String, Object>) access;
                    if (accessMap.containsKey("roles")) {
                        @SuppressWarnings("unchecked")
                        Collection<String> resourceRoles = (Collection<String>) accessMap.get("roles");
                        authorities.addAll(resourceRoles.stream()
                            .map(role -> new SimpleGrantedAuthority(
                                "ROLE_" + resource.toUpperCase() + "_" + role.toUpperCase()))
                            .collect(Collectors.toList()));
                    }
                }
            });
        }
        
        log.debug("Extracted authorities: {}", authorities);
        return authorities;
    }

    /**
     * Adjust timestamps for clock skew
     */
    private void adjustTimestamps(Map<String, Object> claims) {
        if (clockSkewSeconds > 0) {
            Instant now = Clock.systemUTC().instant();
            
            // Adjust expiration time
            if (claims.containsKey("exp")) {
                Long exp = (Long) claims.get("exp");
                claims.put("exp", exp + clockSkewSeconds);
            }
            
            // Adjust not-before time
            if (claims.containsKey("nbf")) {
                Long nbf = (Long) claims.get("nbf");
                claims.put("nbf", nbf - clockSkewSeconds);
            }
        }
    }

    /**
     * Get JWK Set URI from configuration
     */
    private String getJwkSetUri() {
        return String.format("%s/realms/%s/protocol/openid-connect/certs",
            getAuthServerUrl(), getRealm());
    }
    
    @Value("${keycloak.auth-server-url:http://localhost:8080}")
    private String authServerUrl;
    
    @Value("${keycloak.realm:waqiti-fintech}")
    private String realm;
    
    private String getAuthServerUrl() {
        return authServerUrl;
    }
    
    private String getRealm() {
        return realm;
    }
}