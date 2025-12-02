package com.waqiti.analytics.config;

import lombok.extern.slf4j.Slf4j;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.domain.AuditorAware;
import org.springframework.data.jpa.repository.config.EnableJpaAuditing;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.jwt.Jwt;

import java.util.Optional;

/**
 * JPA Auditing Configuration
 *
 * Enables automatic population of audit fields (created_by, updated_by) in JPA entities.
 * Extracts user information from JWT token in SecurityContext.
 * Falls back to "SYSTEM" for automated/background processes.
 *
 * @author Waqiti Analytics Team
 * @version 1.0.0-PRODUCTION
 * @since 2025-11-15
 */
@Configuration
@EnableJpaAuditing(auditorAwareRef = "auditorProvider")
@Slf4j
public class JpaAuditingConfig {

    /**
     * Provides the current auditor (user) for JPA audit fields.
     *
     * Extraction Strategy:
     * 1. Try to get authenticated user from SecurityContext
     * 2. Extract user ID from JWT token claims (preferred: "sub" claim)
     * 3. Fall back to "preferred_username" claim if "sub" not available
     * 4. Fall back to "SYSTEM" for automated processes
     *
     * @return AuditorAware bean that returns current user ID or "SYSTEM"
     */
    @Bean
    public AuditorAware<String> auditorProvider() {
        return new AuditorAwareImpl();
    }

    /**
     * Implementation of AuditorAware that extracts user information from JWT token.
     */
    static class AuditorAwareImpl implements AuditorAware<String> {

        private static final String SYSTEM_USER = "SYSTEM";
        private static final String SUB_CLAIM = "sub";
        private static final String PREFERRED_USERNAME_CLAIM = "preferred_username";
        private static final String EMAIL_CLAIM = "email";

        @Override
        public Optional<String> getCurrentAuditor() {
            try {
                Authentication authentication = SecurityContextHolder.getContext().getAuthentication();

                // No authentication or not authenticated - system operation
                if (authentication == null || !authentication.isAuthenticated()) {
                    log.trace("No authentication found, using SYSTEM auditor");
                    return Optional.of(SYSTEM_USER);
                }

                // Check if principal is a JWT token (OAuth2/Keycloak)
                Object principal = authentication.getPrincipal();
                if (principal instanceof Jwt jwt) {
                    return extractUserFromJwt(jwt);
                }

                // Check if principal is a string (username)
                if (principal instanceof String username) {
                    log.trace("Using string principal as auditor: {}", username);
                    return Optional.of(username);
                }

                // Fall back to authentication name
                String name = authentication.getName();
                if (name != null && !name.equals("anonymousUser")) {
                    log.trace("Using authentication name as auditor: {}", name);
                    return Optional.of(name);
                }

                // Default to SYSTEM
                log.trace("No user information available, using SYSTEM auditor");
                return Optional.of(SYSTEM_USER);

            } catch (Exception e) {
                log.warn("Error extracting current auditor, falling back to SYSTEM", e);
                return Optional.of(SYSTEM_USER);
            }
        }

        /**
         * Extracts user identifier from JWT token claims.
         *
         * Precedence:
         * 1. "sub" claim (subject - standard JWT claim for user ID)
         * 2. "preferred_username" claim (Keycloak username)
         * 3. "email" claim (as fallback identifier)
         * 4. "SYSTEM" if no claims found
         *
         * @param jwt JWT token from authentication
         * @return Optional containing user identifier
         */
        private Optional<String> extractUserFromJwt(Jwt jwt) {
            // Try "sub" claim first (standard subject claim)
            String subject = jwt.getClaimAsString(SUB_CLAIM);
            if (subject != null && !subject.isBlank()) {
                log.trace("Using JWT 'sub' claim as auditor: {}", subject);
                return Optional.of(subject);
            }

            // Try "preferred_username" claim (Keycloak)
            String preferredUsername = jwt.getClaimAsString(PREFERRED_USERNAME_CLAIM);
            if (preferredUsername != null && !preferredUsername.isBlank()) {
                log.trace("Using JWT 'preferred_username' claim as auditor: {}", preferredUsername);
                return Optional.of(preferredUsername);
            }

            // Try "email" claim as last resort
            String email = jwt.getClaimAsString(EMAIL_CLAIM);
            if (email != null && !email.isBlank()) {
                log.trace("Using JWT 'email' claim as auditor: {}", email);
                return Optional.of(email);
            }

            // No usable claims found
            log.warn("No usable claims found in JWT token, using SYSTEM auditor");
            return Optional.of(SYSTEM_USER);
        }
    }
}
