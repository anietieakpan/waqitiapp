package com.waqiti.payment.config;

import com.waqiti.common.security.SecurityHeadersConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;
import org.springframework.boot.context.properties.ConfigurationProperties;
import lombok.Data;
import lombok.extern.slf4j.Slf4j;

/**
 * Payment service specific security headers configuration
 * Extends the common security configuration with payment-specific requirements
 */
@Configuration
@EnableWebSecurity
@Slf4j
public class PaymentSecurityHeadersConfig {
    
    private final SecurityHeadersConfiguration securityHeadersConfiguration;
    private final PaymentSecurityProperties paymentSecurityProperties;
    
    public PaymentSecurityHeadersConfig(SecurityHeadersConfiguration securityHeadersConfiguration,
                                      PaymentSecurityProperties paymentSecurityProperties) {
        this.securityHeadersConfiguration = securityHeadersConfiguration;
        this.paymentSecurityProperties = paymentSecurityProperties;
    }
    
    @Bean
    public SecurityFilterChain paymentSecurityFilterChain(HttpSecurity http) throws Exception {
        // Apply common security headers
        securityHeadersConfiguration.configureSecurityHeaders(http);
        
        // Add payment-specific security configurations
        http.headers(headers -> headers
            // Additional CSP directives for payment processing
            .contentSecurityPolicy(csp -> csp
                .policyDirectives(getPaymentSpecificCsp())
            )
            
            // Feature policy for payment features
            .featurePolicy(fp -> 
                "payment 'self' https://payments.example.com; " +
                "publickey-credentials-get 'self'; " +
                "publickey-credentials-create 'self'"
            )
            
            // Additional headers for PCI compliance
            .addHeaderWriter((request, response) -> {
                // PCI DSS compliance headers
                response.setHeader("X-PCI-Compliance", "PCI-DSS-3.2.1");
                response.setHeader("X-Payment-Security-Version", paymentSecurityProperties.getSecurityVersion());
                
                // Additional payment security headers
                response.setHeader("X-Payment-Encryption", "AES-256-GCM");
                response.setHeader("X-Tokenization-Enabled", "true");
                
                // Fraud detection headers
                response.setHeader("X-Fraud-Detection", "enabled");
                response.setHeader("X-Risk-Assessment", "real-time");
            })
        );
        
        // Configure payment-specific endpoints
        http.authorizeHttpRequests(authz -> authz
            // Public endpoints
            .requestMatchers("/api/v1/payments/public/**").permitAll()
            .requestMatchers("/api/v1/payments/webhook/**").permitAll()
            
            // Authenticated endpoints
            .requestMatchers("/api/v1/payments/user/**").authenticated()
            .requestMatchers("/api/v1/payments/split/**").authenticated()
            .requestMatchers("/api/v1/payments/schedule/**").authenticated()
            
            // Admin endpoints
            .requestMatchers("/api/v1/payments/admin/**").hasRole("PAYMENT_ADMIN")
            .requestMatchers("/api/v1/payments/reports/**").hasAnyRole("PAYMENT_ADMIN", "ANALYST")
            
            // Internal service endpoints
            .requestMatchers("/internal/payments/**").hasRole("SERVICE")
            
            // Deny all other requests
            .anyRequest().denyAll()
        );
        
        return http.build();
    }
    
    /**
     * Get payment-specific Content Security Policy
     */
    private String getPaymentSpecificCsp() {
        return "default-src 'self'; " +
               "script-src 'self' 'strict-dynamic' 'nonce-{nonce}' https://js.stripe.com https://checkout.stripe.com; " +
               "style-src 'self' 'unsafe-inline' https://fonts.googleapis.com; " +
               "img-src 'self' data: https: blob: https://stripe.com; " +
               "font-src 'self' https://fonts.gstatic.com; " +
               "connect-src 'self' https://api.example.com wss://ws.waqiti.com https://api.stripe.com; " +
               "frame-src 'self' https://checkout.stripe.com https://hooks.stripe.com; " +
               "frame-ancestors 'none'; " +
               "form-action 'self' https://checkout.stripe.com; " +
               "base-uri 'self'; " +
               "object-src 'none'; " +
               "block-all-mixed-content; " +
               "upgrade-insecure-requests; " +
               "report-uri https://csp.example.com/payment-report";
    }
    
    /**
     * Payment security properties
     */
    @ConfigurationProperties(prefix = "waqiti.payment.security")
    @Data
    public static class PaymentSecurityProperties {
        private String securityVersion = "2.0";
        private boolean enablePciCompliance = true;
        private boolean enableTokenization = true;
        private boolean enableFraudDetection = true;
        private boolean enable3DSecure = true;
        private boolean enableWebhookValidation = true;
        
        // Encryption settings
        private String encryptionAlgorithm = "AES-256-GCM";
        private String keyDerivationFunction = "PBKDF2";
        private int keyIterations = 100000;
        
        // Rate limiting for payment endpoints
        private int maxPaymentsPerMinute = 10;
        private int maxPaymentsPerHour = 100;
        private int maxPaymentsPerDay = 500;
        
        // Webhook security
        private String webhookSigningSecret;
        private long webhookTimestampTolerance = 300; // 5 minutes
        
        // Transaction limits
        private double maxTransactionAmount = 10000.0;
        private double dailyTransactionLimit = 50000.0;
        private double monthlyTransactionLimit = 500000.0;
    }
    
    @Bean
    @ConfigurationProperties(prefix = "waqiti.payment.security")
    public PaymentSecurityProperties paymentSecurityProperties() {
        return new PaymentSecurityProperties();
    }
}