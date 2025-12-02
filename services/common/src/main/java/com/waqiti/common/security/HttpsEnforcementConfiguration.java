package com.waqiti.common.security;

import lombok.extern.slf4j.Slf4j;
import org.apache.catalina.Context;
import org.apache.catalina.connector.Connector;
import org.apache.tomcat.util.descriptor.web.SecurityCollection;
import org.apache.tomcat.util.descriptor.web.SecurityConstraint;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.web.embedded.tomcat.TomcatServletWebServerFactory;
import org.springframework.boot.web.servlet.server.ServletWebServerFactory;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Profile;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.header.writers.ReferrerPolicyHeaderWriter;

/**
 * HTTPS Enforcement Configuration for Waqiti Platform
 * 
 * This configuration ensures all HTTP traffic is redirected to HTTPS
 * and implements comprehensive security headers for production deployment.
 */
@Configuration
@EnableWebSecurity
@Slf4j
@Profile("!test") // Exclude from test environment
public class HttpsEnforcementConfiguration {

    @Value("${server.port:8443}")
    private int httpsPort;
    
    @Value("${server.http.port:8080}")
    private int httpPort;
    
    @Value("${security.require-ssl:true}")
    private boolean requireSsl;
    
    @Value("${security.hsts.max-age:31536000}")
    private long hstsMaxAge;
    
    @Value("${security.hsts.include-subdomains:true}")
    private boolean hstsIncludeSubdomains;
    
    @Value("${security.hsts.preload:true}")
    private boolean hstsPreload;

    /**
     * Configure HTTPS enforcement and security headers
     */
    @Bean
    public SecurityFilterChain httpsSecurityFilterChain(HttpSecurity http) throws Exception {
        log.info("Configuring HTTPS enforcement and security headers");
        
        http
            // Require HTTPS for all requests
            .requiresChannel(channel -> 
                channel.requestMatchers(request -> 
                    requireSsl && !request.isSecure()
                ).requiresSecure()
            )
            
            // Configure comprehensive security headers
            .headers(headers -> headers
                // Strict Transport Security (HSTS)
                .httpStrictTransportSecurity(hstsConfig -> hstsConfig
                    .maxAgeInSeconds(hstsMaxAge)
                    .includeSubDomains(hstsIncludeSubdomains)
                    .preload(hstsPreload)
                )
                
                // Frame options - prevent clickjacking
                .frameOptions(frameOptions -> frameOptions.deny())
                
                // Content type options - prevent MIME sniffing
                .contentTypeOptions(contentTypeOptions -> {})
                
                // XSS protection
                .xssProtection(xss -> xss.disable())
                
                // Referrer policy
                .referrerPolicy(referrer -> referrer.policy(ReferrerPolicyHeaderWriter.ReferrerPolicy.STRICT_ORIGIN_WHEN_CROSS_ORIGIN))
                
                // Content Security Policy  
                .contentSecurityPolicy(csp -> csp
                    .policyDirectives("default-src 'self'; " +
                        "script-src 'self' 'unsafe-eval'; " +
                        "style-src 'self' 'unsafe-inline'; " +
                        "img-src 'self' data: blob:; " +
                        "font-src 'self' data:; " +
                        "connect-src 'self' wss: https:; " +
                        "frame-ancestors 'none'; " +
                        "base-uri 'self'; " +
                        "form-action 'self'"))
                
                // Additional security headers
                .permissionsPolicy(permissions -> permissions
                    .policy("camera=(), microphone=(), geolocation=(), payment=()"))
            );
            
            // Custom headers
            http.headers(headers -> headers
                .addHeaderWriter((request, response) -> {
                    // X-Permitted-Cross-Domain-Policies
                    response.setHeader("X-Permitted-Cross-Domain-Policies", "none");
                    
                    // Clear-Site-Data on logout
                    if (request.getRequestURI().contains("/logout")) {
                        response.setHeader("Clear-Site-Data", "\"cache\", \"cookies\", \"storage\"");
                    }
                    
                    // Server identification removal
                    response.setHeader("Server", "Waqiti-Platform");
                    
                    // Cross-Origin policies
                    response.setHeader("Cross-Origin-Embedder-Policy", "require-corp");
                    response.setHeader("Cross-Origin-Opener-Policy", "same-origin");
                    response.setHeader("Cross-Origin-Resource-Policy", "same-site");
                })
            );
            
        return http.build();
    }

    /**
     * Configure Tomcat to redirect HTTP to HTTPS
     */
    @Bean
    @Profile("production")
    public ServletWebServerFactory servletContainer() {
        log.info("Configuring HTTPS redirect from port {} to {}", httpPort, httpsPort);
        
        TomcatServletWebServerFactory tomcat = new TomcatServletWebServerFactory() {
            @Override
            protected void postProcessContext(Context context) {
                if (requireSsl) {
                    SecurityConstraint securityConstraint = new SecurityConstraint();
                    securityConstraint.setUserConstraint("CONFIDENTIAL");
                    
                    SecurityCollection collection = new SecurityCollection();
                    collection.addPattern("/*");
                    securityConstraint.addCollection(collection);
                    
                    context.addConstraint(securityConstraint);
                }
            }
        };
        
        if (requireSsl) {
            tomcat.addAdditionalTomcatConnectors(redirectConnector());
        }
        
        return tomcat;
    }

    /**
     * Create HTTP connector that redirects to HTTPS
     */
    private Connector redirectConnector() {
        Connector connector = new Connector(TomcatServletWebServerFactory.DEFAULT_PROTOCOL);
        connector.setScheme("http");
        connector.setPort(httpPort);
        connector.setSecure(false);
        connector.setRedirectPort(httpsPort);
        
        log.info("Created HTTP to HTTPS redirect connector: {}:{} -> {}:{}", 
            "http", httpPort, "https", httpsPort);
        
        return connector;
    }

    /**
     * Custom security configuration for specific endpoints
     */
    @Bean
    public SecurityFilterChain apiSecurityFilterChain(HttpSecurity http) throws Exception {
        http
            .securityMatcher("/api/**")
            .requiresChannel(channel -> 
                channel.anyRequest().requiresSecure()
            )
            .headers(headers -> headers
                // API-specific CSP
                .contentSecurityPolicy(csp -> csp.policyDirectives("default-src 'none'; " +
                    "script-src 'self'; " +
                    "connect-src 'self' https:; " +
                    "img-src 'self' data:; " +
                    "style-src 'self' 'unsafe-inline'; " +
                    "base-uri 'none'; " +
                    "form-action 'none'"))
            );
            
            // API-specific headers
            http.headers(headers -> headers
                .addHeaderWriter((request, response) -> {
                    // No-cache for API responses containing sensitive data
                    if (request.getRequestURI().contains("/api/v1/")) {
                        response.setHeader("Cache-Control", "no-cache, no-store, must-revalidate");
                        response.setHeader("Pragma", "no-cache");
                        response.setHeader("Expires", "0");
                    }
                    
                    // CORS headers for API endpoints
                    response.setHeader("Access-Control-Allow-Origin", "https://api.example.com");
                    response.setHeader("Access-Control-Allow-Methods", "GET, POST, PUT, DELETE, OPTIONS");
                    response.setHeader("Access-Control-Allow-Headers", 
                        "Authorization, Content-Type, X-Requested-With, X-Correlation-Id");
                    response.setHeader("Access-Control-Max-Age", "3600");
                    response.setHeader("Access-Control-Allow-Credentials", "true");
                })
            );
            
        return http.build();
    }

    /**
     * Health check endpoints with relaxed security for internal monitoring
     */
    @Bean
    public SecurityFilterChain healthCheckSecurityFilterChain(HttpSecurity http) throws Exception {
        http
            .securityMatcher("/actuator/health/**", "/health/**", "/readiness/**", "/liveness/**")
            .authorizeHttpRequests(authz -> authz
                .anyRequest().permitAll()
            )
            .headers(headers -> headers
                .contentSecurityPolicy(csp -> csp.policyDirectives("default-src 'none'"))
                .addHeaderWriter((request, response) -> {
                    // Minimal headers for health checks
                    response.setHeader("Cache-Control", "no-cache");
                    response.setHeader("X-Content-Type-Options", "nosniff");
                })
            );
            
        return http.build();
    }

    /**
     * Metrics endpoints with IP-based access control
     */
    @Bean
    public SecurityFilterChain metricsSecurityFilterChain(HttpSecurity http) throws Exception {
        http
            .securityMatcher("/actuator/metrics/**", "/actuator/prometheus/**")
            .authorizeHttpRequests(authz -> authz
                .requestMatchers(request -> {
                    String remoteAddr = request.getRemoteAddr();
                    // Allow internal Kubernetes networks
                    return remoteAddr.startsWith("10.") || 
                           remoteAddr.startsWith("172.") || 
                           remoteAddr.startsWith("192.168.") ||
                           remoteAddr.equals("127.0.0.1");
                }).permitAll()
                .anyRequest().denyAll()
            )
            .headers(headers -> headers
                .contentSecurityPolicy(csp -> csp.policyDirectives("default-src 'none'"))
            );
            
        return http.build();
    }
}