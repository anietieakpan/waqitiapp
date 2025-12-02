package com.waqiti.security.converter;

import com.waqiti.security.config.KeycloakProperties;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.core.convert.converter.Converter;
import org.springframework.lang.NonNull;
import org.springframework.security.authentication.AbstractAuthenticationToken;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;
import org.springframework.util.StringUtils;

import java.util.*;
import java.util.stream.Collectors;
import java.util.stream.Stream;

/**
 * JWT Authentication Converter for Keycloak tokens
 * 
 * Converts Keycloak JWT tokens into Spring Security authentication tokens
 * with proper role and scope mapping.
 */
@RequiredArgsConstructor
@Slf4j
public class JwtAuthenticationConverter implements Converter<Jwt, AbstractAuthenticationToken> {
    
    private final KeycloakProperties keycloakProperties;
    private static final String ROLE_PREFIX = "ROLE_";
    private static final String SCOPE_PREFIX = "SCOPE_";
    
    @Override
    public AbstractAuthenticationToken convert(@NonNull Jwt jwt) {
        log.debug("Converting JWT token for user: {}", jwt.getSubject());
        
        try {
            // Extract authorities from roles and scopes
            Collection<GrantedAuthority> authorities = extractAuthorities(jwt);
            
            // Create custom authentication token with additional claims
            return new KeycloakAuthenticationToken(jwt, authorities, extractUserInfo(jwt));
            
        } catch (Exception e) {
            log.error("Error converting JWT token", e);
            throw new SecurityException("Invalid JWT token", e);
        }
    }
    
    /**
     * Extract authorities from Keycloak JWT token
     */
    private Collection<GrantedAuthority> extractAuthorities(Jwt jwt) {
        Set<GrantedAuthority> authorities = new HashSet<>();
        
        // Extract realm roles
        Collection<String> realmRoles = extractRealmRoles(jwt);
        authorities.addAll(realmRoles.stream()
                .map(role -> new SimpleGrantedAuthority(ROLE_PREFIX + role.toUpperCase()))
                .collect(Collectors.toSet()));
        
        // Extract resource roles (client-specific roles)
        Collection<String> resourceRoles = extractResourceRoles(jwt);
        authorities.addAll(resourceRoles.stream()
                .map(role -> new SimpleGrantedAuthority(ROLE_PREFIX + role.toUpperCase()))
                .collect(Collectors.toSet()));
        
        // Extract scopes
        Collection<String> scopes = extractScopes(jwt);
        authorities.addAll(scopes.stream()
                .map(scope -> new SimpleGrantedAuthority(SCOPE_PREFIX + scope))
                .collect(Collectors.toSet()));
        
        log.debug("Extracted authorities for user {}: {}", jwt.getSubject(), authorities);
        return authorities;
    }
    
    /**
     * Extract realm roles from JWT token
     */
    @SuppressWarnings("unchecked")
    private Collection<String> extractRealmRoles(Jwt jwt) {
        try {
            Map<String, Object> realmAccess = jwt.getClaimAsMap("realm_access");
            if (realmAccess != null && realmAccess.containsKey("roles")) {
                Object roles = realmAccess.get("roles");
                if (roles instanceof Collection) {
                    return (Collection<String>) roles;
                }
            }
        } catch (Exception e) {
            log.warn("Error extracting realm roles from JWT", e);
        }
        return Collections.emptyList();
    }
    
    /**
     * Extract resource/client roles from JWT token
     */
    @SuppressWarnings("unchecked")
    private Collection<String> extractResourceRoles(Jwt jwt) {
        try {
            Map<String, Object> resourceAccess = jwt.getClaimAsMap("resource_access");
            if (resourceAccess == null) {
                return Collections.emptyList();
            }
            
            Set<String> allResourceRoles = new HashSet<>();
            
            // Extract roles for current client
            String clientId = keycloakProperties.getClientId();
            if (StringUtils.hasText(clientId) && resourceAccess.containsKey(clientId)) {
                Map<String, Object> clientAccess = (Map<String, Object>) resourceAccess.get(clientId);
                if (clientAccess != null && clientAccess.containsKey("roles")) {
                    Object roles = clientAccess.get("roles");
                    if (roles instanceof Collection) {
                        allResourceRoles.addAll((Collection<String>) roles);
                    }
                }
            }
            
            // Extract roles for other configured clients if needed
            for (Map.Entry<String, Object> entry : resourceAccess.entrySet()) {
                if (entry.getValue() instanceof Map) {
                    Map<String, Object> clientAccess = (Map<String, Object>) entry.getValue();
                    if (clientAccess.containsKey("roles")) {
                        Object roles = clientAccess.get("roles");
                        if (roles instanceof Collection) {
                            Collection<String> clientRoles = (Collection<String>) roles;
                            // Prefix with client name to avoid conflicts
                            allResourceRoles.addAll(clientRoles.stream()
                                    .map(role -> entry.getKey() + ":" + role)
                                    .collect(Collectors.toSet()));
                        }
                    }
                }
            }
            
            return allResourceRoles;
            
        } catch (Exception e) {
            log.warn("Error extracting resource roles from JWT", e);
        }
        return Collections.emptyList();
    }
    
    /**
     * Extract scopes from JWT token
     */
    private Collection<String> extractScopes(Jwt jwt) {
        try {
            String scopeClaimValue = jwt.getClaimAsString("scope");
            if (StringUtils.hasText(scopeClaimValue)) {
                return Arrays.asList(scopeClaimValue.split(" "));
            }
            
            // Also check for scopes in scp claim (some configurations use this)
            Object scpClaim = jwt.getClaim("scp");
            if (scpClaim instanceof Collection) {
                return (Collection<String>) scpClaim;
            }
            
        } catch (Exception e) {
            log.warn("Error extracting scopes from JWT", e);
        }
        return Collections.emptyList();
    }
    
    /**
     * Extract user information from JWT token
     */
    private UserInfo extractUserInfo(Jwt jwt) {
        return UserInfo.builder()
                .userId(jwt.getSubject())
                .username(jwt.getClaimAsString("preferred_username"))
                .email(jwt.getClaimAsString("email"))
                .firstName(jwt.getClaimAsString("given_name"))
                .lastName(jwt.getClaimAsString("family_name"))
                .fullName(jwt.getClaimAsString("name"))
                .emailVerified(jwt.getClaimAsBoolean("email_verified"))
                .sessionId(jwt.getClaimAsString("session_state"))
                .issuedAt(jwt.getIssuedAt())
                .expiresAt(jwt.getExpiresAt())
                .issuer(jwt.getIssuer().toString())
                .audience(jwt.getAudience())
                .claims(jwt.getClaims())
                .build();
    }
    
    /**
     * Custom Authentication Token with user info
     */
    public static class KeycloakAuthenticationToken extends JwtAuthenticationToken {
        
        private final UserInfo userInfo;
        
        public KeycloakAuthenticationToken(Jwt jwt, Collection<? extends GrantedAuthority> authorities, UserInfo userInfo) {
            super(jwt, authorities);
            this.userInfo = userInfo;
        }
        
        public UserInfo getUserInfo() {
            return userInfo;
        }
        
        public String getUserId() {
            return userInfo != null ? userInfo.getUserId() : null;
        }
        
        public String getUsername() {
            return userInfo != null ? userInfo.getUsername() : null;
        }
        
        public String getEmail() {
            return userInfo != null ? userInfo.getEmail() : null;
        }
        
        public String getFullName() {
            return userInfo != null ? userInfo.getFullName() : null;
        }
    }
}