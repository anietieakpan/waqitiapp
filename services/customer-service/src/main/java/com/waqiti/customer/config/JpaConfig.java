package com.waqiti.customer.config;

import lombok.extern.slf4j.Slf4j;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.domain.AuditorAware;
import org.springframework.data.jpa.repository.config.EnableJpaAuditing;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.transaction.annotation.EnableTransactionManagement;

import java.util.Optional;

/**
 * JPA Configuration for Customer Service.
 * Enables JPA auditing with automatic tracking of created/modified by user,
 * and configures entity auditing support.
 *
 * @author Waqiti Engineering Team
 * @version 1.0
 * @since 2025-11-20
 */
@Configuration
@EnableJpaAuditing(auditorAwareRef = "auditorProvider")
@EnableTransactionManagement
@Slf4j
public class JpaConfig {

    /**
     * Configures AuditorAware to provide current user information.
     * Extracts username from JWT token in SecurityContext for audit fields.
     *
     * @return AuditorAware implementation
     */
    @Bean
    public AuditorAware<String> auditorProvider() {
        return new SpringSecurityAuditorAware();
    }

    /**
     * Implementation of AuditorAware that extracts the current user
     * from Spring Security context.
     */
    static class SpringSecurityAuditorAware implements AuditorAware<String> {

        private static final String SYSTEM_USER = "SYSTEM";
        private static final String ANONYMOUS_USER = "ANONYMOUS";

        @Override
        public Optional<String> getCurrentAuditor() {
            try {
                Authentication authentication = SecurityContextHolder.getContext().getAuthentication();

                if (authentication == null || !authentication.isAuthenticated()) {
                    log.debug("No authentication found, using SYSTEM as auditor");
                    return Optional.of(SYSTEM_USER);
                }

                // Check if principal is JWT
                if (authentication.getPrincipal() instanceof Jwt jwt) {
                    String username = extractUsernameFromJwt(jwt);
                    log.debug("Current auditor: {}", username);
                    return Optional.of(username);
                }

                // Fallback to authentication name
                String name = authentication.getName();
                if (name != null && !name.equals("anonymousUser")) {
                    log.debug("Current auditor from authentication name: {}", name);
                    return Optional.of(name);
                }

                log.debug("Anonymous user detected, using ANONYMOUS as auditor");
                return Optional.of(ANONYMOUS_USER);

            } catch (Exception e) {
                log.error("Error determining current auditor: {}", e.getMessage(), e);
                return Optional.of(SYSTEM_USER);
            }
        }

        /**
         * Extracts username from JWT token.
         * Tries multiple claim names in order of preference.
         *
         * @param jwt JWT token
         * @return Username from JWT
         */
        private String extractUsernameFromJwt(Jwt jwt) {
            // Try preferred_username (Keycloak default)
            String username = jwt.getClaim("preferred_username");
            if (username != null && !username.isBlank()) {
                return username;
            }

            // Try email
            username = jwt.getClaim("email");
            if (username != null && !username.isBlank()) {
                return username;
            }

            // Try sub (subject)
            username = jwt.getSubject();
            if (username != null && !username.isBlank()) {
                return username;
            }

            // Fallback to token ID
            return jwt.getId() != null ? jwt.getId() : SYSTEM_USER;
        }
    }
}
