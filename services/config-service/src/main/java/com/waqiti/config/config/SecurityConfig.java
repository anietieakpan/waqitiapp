package com.waqiti.config.config;

import jakarta.annotation.PostConstruct;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.core.userdetails.User;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.provisioning.InMemoryUserDetailsManager;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.csrf.CookieCsrfTokenRepository;
import org.springframework.util.StringUtils;

/**
 * CRITICAL P0 SECURITY FIX: Config Service Security Configuration
 *
 * Fixed vulnerabilities:
 * 1. ENABLED CSRF protection with cookie-based token repository
 * 2. REMOVED weak default password "changeme"
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

    @Value("${config.server.username}")
    private String username;

    @Value("${config.server.password}")
    private String password;

    /**
     * SECURITY FIX: Validate credentials at startup to prevent weak passwords
     */
    @PostConstruct
    public void validateCredentials() {
        if (!StringUtils.hasText(username) || !StringUtils.hasText(password)) {
            String error = "CRITICAL SECURITY ERROR: Config server username/password not set. " +
                    "Set config.server.username and config.server.password in environment variables or Vault.";
            log.error(error);
            throw new IllegalStateException(error);
        }

        // Enforce strong password policy
        if (password.length() < 16) {
            String error = "CRITICAL SECURITY ERROR: Config server password must be at least 16 characters. " +
                    "Current length: " + password.length();
            log.error(error);
            throw new IllegalStateException(error);
        }

        // Check for weak/default passwords
        String[] weakPasswords = {"admin", "password", "changeme", "admin123", "config123", "secret"};
        String lowerPassword = password.toLowerCase();
        for (String weak : weakPasswords) {
            if (lowerPassword.contains(weak)) {
                String error = "CRITICAL SECURITY ERROR: Config server password contains weak/common pattern. " +
                        "Use a strong randomly generated password from Vault.";
                log.error(error);
                throw new IllegalStateException(error);
            }
        }

        log.info("✅ Config server security credentials validated successfully");
    }

    /**
     * SECURITY FIX: ENABLED CSRF protection for Config Server
     *
     * Config server endpoints can mutate configuration state and must be protected
     * against CSRF attacks. Using cookie-based CSRF tokens for compatibility with
     * both browser-based and API clients.
     */
    @Bean
    public SecurityFilterChain filterChain(HttpSecurity http) throws Exception {
        http
            // SECURITY FIX: ENABLE CSRF protection (was disabled!)
            .csrf(csrf -> csrf
                .csrfTokenRepository(CookieCsrfTokenRepository.withHttpOnlyFalse())
                // Allow actuator health/info endpoints without CSRF for monitoring
                .ignoringRequestMatchers("/actuator/health", "/actuator/info")
            )
            .sessionManagement(session -> session
                .sessionCreationPolicy(SessionCreationPolicy.STATELESS)
            )
            .authorizeHttpRequests(authz -> authz
                .requestMatchers("/actuator/health", "/actuator/info").permitAll()
                .requestMatchers("/encrypt", "/decrypt").hasRole("ADMIN")
                .anyRequest().authenticated()
            )
            .httpBasic();

        return http.build();
    }

    @Bean
    public UserDetailsService userDetailsService() {
        UserDetails user = User.builder()
            .username(username)
            .password(passwordEncoder().encode(password))
            .roles("ADMIN", "USER")
            .build();
        
        return new InMemoryUserDetailsManager(user);
    }

    @Bean
    public PasswordEncoder passwordEncoder() {
        return new BCryptPasswordEncoder();
    }
}