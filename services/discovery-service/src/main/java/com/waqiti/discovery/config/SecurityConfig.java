package com.waqiti.discovery.config;

import jakarta.annotation.PostConstruct;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.core.userdetails.User;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.provisioning.InMemoryUserDetailsManager;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.csrf.CookieCsrfTokenRepository;
import org.springframework.util.StringUtils;

/**
 * CRITICAL P0 SECURITY FIX: Discovery Service Security Configuration
 *
 * Fixed vulnerabilities:
 * 1. ENABLED CSRF protection (was ignoring /eureka/** endpoints)
 * 2. REMOVED weak default password "password"
 * 3. ENFORCED strong password requirements via validation
 * 4. Added startup validation to prevent weak credentials
 *
 * @author Waqiti Engineering Team - Production Security Fix
 * @version 2.0.0
 */
@Configuration
@EnableWebSecurity
@Slf4j
public class SecurityConfig {

    @Value("${eureka.username}")
    private String username;

    @Value("${eureka.password}")
    private String password;

    /**
     * SECURITY FIX: Validate credentials at startup to prevent weak passwords
     */
    @PostConstruct
    public void validateCredentials() {
        if (!StringUtils.hasText(username) || !StringUtils.hasText(password)) {
            String error = "CRITICAL SECURITY ERROR: Eureka username/password not set. " +
                    "Set eureka.username and eureka.password in environment variables or Vault.";
            log.error(error);
            throw new IllegalStateException(error);
        }

        // Enforce strong password policy
        if (password.length() < 16) {
            String error = "CRITICAL SECURITY ERROR: Eureka password must be at least 16 characters. " +
                    "Current length: " + password.length();
            log.error(error);
            throw new IllegalStateException(error);
        }

        // Check for weak/default passwords
        String[] weakPasswords = {"password", "eureka", "admin", "changeme", "admin123", "eureka123", "secret"};
        String lowerPassword = password.toLowerCase();
        for (String weak : weakPasswords) {
            if (lowerPassword.contains(weak)) {
                String error = "CRITICAL SECURITY ERROR: Eureka password contains weak/common pattern. " +
                        "Use a strong randomly generated password from Vault.";
                log.error(error);
                throw new IllegalStateException(error);
            }
        }

        log.info("✅ Eureka security credentials validated successfully");
    }

    /**
     * SECURITY FIX: ENABLED full CSRF protection for Discovery Service
     *
     * Discovery service registration endpoints can mutate service registry state
     * and must be protected against CSRF attacks. Using cookie-based CSRF tokens.
     */
    @Bean
    public SecurityFilterChain filterChain(HttpSecurity http) throws Exception {
        http
            // SECURITY FIX: ENABLE CSRF protection for all endpoints
            .csrf(csrf -> csrf
                .csrfTokenRepository(CookieCsrfTokenRepository.withHttpOnlyFalse())
                // Only allow health/info and static resources without CSRF
                .ignoringRequestMatchers(
                    "/actuator/health",
                    "/actuator/info",
                    "/eureka/css/**",
                    "/eureka/js/**",
                    "/eureka/fonts/**",
                    "/eureka/images/**"
                )
            )
            .sessionManagement(session -> session
                .sessionCreationPolicy(SessionCreationPolicy.ALWAYS)
            )
            .requestCache(cache -> cache.disable())
            .authorizeHttpRequests(authz -> authz
                .requestMatchers("/actuator/health", "/actuator/info").permitAll()
                .requestMatchers("/eureka/css/**", "/eureka/js/**", "/eureka/fonts/**", "/eureka/images/**").permitAll()
                .anyRequest().authenticated()
            )
            .httpBasic();

        return http.build();
    }

    @Bean
    public UserDetailsService userDetailsService() {
        return new InMemoryUserDetailsManager(
            User.builder()
                .username(username)
                .password(passwordEncoder().encode(password))
                .roles("ADMIN")
                .build()
        );
    }

    @Bean
    public PasswordEncoder passwordEncoder() {
        return new BCryptPasswordEncoder();
    }
}