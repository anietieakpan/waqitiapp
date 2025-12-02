package com.waqiti.lending.config;

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
 * Automatically populates audit fields (created_by, updated_by) in entities
 * by extracting the authenticated user's information from the security context
 */
@Configuration
@EnableJpaAuditing(auditorAwareRef = "auditorProvider")
@Slf4j
public class JpaAuditConfig {

    /**
     * AuditorAware Bean
     *
     * Provides the current auditor (authenticated user) for JPA audit fields
     * Extracts user information from JWT token in security context
     *
     * @return AuditorAware<String> that resolves the current user
     */
    @Bean
    public AuditorAware<String> auditorProvider() {
        return new SpringSecurityAuditorAware();
    }

    /**
     * Implementation of AuditorAware that extracts auditor from Spring Security context
     */
    public static class SpringSecurityAuditorAware implements AuditorAware<String> {

        @Override
        public Optional<String> getCurrentAuditor() {
            Authentication authentication = SecurityContextHolder.getContext().getAuthentication();

            if (authentication == null || !authentication.isAuthenticated()) {
                log.debug("No authenticated user found, using 'SYSTEM' as auditor");
                return Optional.of("SYSTEM");
            }

            // Handle anonymous authentication
            if ("anonymousUser".equals(authentication.getPrincipal())) {
                log.debug("Anonymous user detected, using 'ANONYMOUS' as auditor");
                return Optional.of("ANONYMOUS");
            }

            try {
                // Extract user identifier from JWT token
                if (authentication.getPrincipal() instanceof Jwt jwt) {
                    // Try to get preferred_username (Keycloak default)
                    String username = jwt.getClaimAsString("preferred_username");
                    if (username != null && !username.isEmpty()) {
                        log.trace("Auditor resolved from JWT preferred_username: {}", username);
                        return Optional.of(username);
                    }

                    // Fall back to sub (subject) claim
                    String subject = jwt.getSubject();
                    if (subject != null && !subject.isEmpty()) {
                        log.trace("Auditor resolved from JWT subject: {}", subject);
                        return Optional.of(subject);
                    }

                    // Fall back to email claim
                    String email = jwt.getClaimAsString("email");
                    if (email != null && !email.isEmpty()) {
                        log.trace("Auditor resolved from JWT email: {}", email);
                        return Optional.of(email);
                    }
                }

                // Fall back to authentication name
                String name = authentication.getName();
                if (name != null && !name.isEmpty()) {
                    log.trace("Auditor resolved from authentication name: {}", name);
                    return Optional.of(name);
                }

            } catch (Exception e) {
                log.error("Error extracting auditor from security context", e);
            }

            log.warn("Could not determine auditor, using 'UNKNOWN'");
            return Optional.of("UNKNOWN");
        }
    }
}
