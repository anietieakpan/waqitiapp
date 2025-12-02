package com.waqiti.common.security;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationConverter;
import org.springframework.security.oauth2.server.resource.authentication.JwtGrantedAuthoritiesConverter;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.CorsConfigurationSource;
import org.springframework.web.cors.UrlBasedCorsConfigurationSource;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

import java.util.Arrays;
import java.util.List;

/**
 * Zero Trust Security Configuration
 * 
 * Implements comprehensive zero trust security principles:
 * - Never trust, always verify
 * - Principle of least privilege
 * - Assume breach
 * - Continuous verification
 * - Context-aware access control
 */
@Configuration
@EnableWebSecurity
@RequiredArgsConstructor
@Slf4j
public class ZeroTrustSecurityConfig {

    private final ServiceMeshAuthenticationFilter serviceMeshFilter;
    private final ContinuousRiskAssessmentFilter riskAssessmentFilter;
    private final DeviceFingerprintingFilter deviceFingerprintingFilter;
    private final GeolocationVerificationFilter geolocationFilter;
    private final BehaviorAnalysisFilter behaviorAnalysisFilter;
    
    /**
     * Main security filter chain implementing zero trust principles
     */
    @Bean
    public SecurityFilterChain zeroTrustFilterChain(HttpSecurity http) throws Exception {
        return http
            // Disable CSRF for stateless APIs (mTLS provides protection)
            .csrf(csrf -> csrf.disable())
            
            // Configure CORS for cross-origin requests
            .cors(cors -> cors.configurationSource(corsConfigurationSource()))
            
            // Stateless session management
            .sessionManagement(session -> 
                session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
            
            // Configure OAuth2 Resource Server with JWT
            .oauth2ResourceServer(oauth2 -> oauth2
                .jwt(jwt -> jwt
                    .jwtAuthenticationConverter(jwtAuthenticationConverter())
                )
            )
            
            // Authorization rules - implementing principle of least privilege
            .authorizeHttpRequests(authz -> authz
                // Public endpoints (health checks, metrics)
                .requestMatchers("/actuator/health", "/actuator/info").permitAll()
                .requestMatchers("/metrics").permitAll()
                
                // Authentication endpoints
                .requestMatchers("/auth/login", "/auth/register").permitAll()
                .requestMatchers("/auth/refresh").authenticated()
                
                // API endpoints - require authentication and authorization
                .requestMatchers("/api/v1/users/**").hasRole("USER")
                .requestMatchers("/api/v1/payments/**").hasRole("USER")
                .requestMatchers("/api/v1/transactions/**").hasRole("USER")
                .requestMatchers("/api/v1/wallets/**").hasRole("USER")
                
                // Admin endpoints
                .requestMatchers("/api/v1/admin/**").hasRole("ADMIN")
                .requestMatchers("/api/v1/analytics/**").hasAnyRole("ADMIN", "ANALYST")
                
                // Internal service communication
                .requestMatchers("/internal/**").hasRole("SERVICE")
                
                // ML and fraud detection endpoints
                .requestMatchers("/fraud/**").hasRole("ML_SERVICE")
                .requestMatchers("/risk/**").hasRole("RISK_SERVICE")
                
                // Deny all other requests
                .anyRequest().denyAll()
            )
            
            // Add custom security filters in proper order
            .addFilterBefore(serviceMeshFilter, UsernamePasswordAuthenticationFilter.class)
            .addFilterAfter(deviceFingerprintingFilter, ServiceMeshAuthenticationFilter.class)
            .addFilterAfter(geolocationFilter, DeviceFingerprintingFilter.class)
            .addFilterAfter(riskAssessmentFilter, GeolocationVerificationFilter.class)
            .addFilterAfter(behaviorAnalysisFilter, ContinuousRiskAssessmentFilter.class)
            
            // Security headers
            .headers(headers -> headers
                .frameOptions(frameOptions -> frameOptions.deny())
                .contentTypeOptions(contentTypeOptions -> {})
                .httpStrictTransportSecurity(hsts -> hsts
                    .maxAgeInSeconds(31536000)
                    .includeSubDomains(true)
                    .preload(true)
                )
            )
            
            .build();
    }
    
    /**
     * JWT Authentication Converter with custom authorities mapping
     */
    @Bean
    public JwtAuthenticationConverter jwtAuthenticationConverter() {
        JwtGrantedAuthoritiesConverter authoritiesConverter = new JwtGrantedAuthoritiesConverter();
        authoritiesConverter.setAuthorityPrefix("ROLE_");
        authoritiesConverter.setAuthoritiesClaimName("roles");
        
        JwtAuthenticationConverter converter = new JwtAuthenticationConverter();
        converter.setJwtGrantedAuthoritiesConverter(authoritiesConverter);
        converter.setPrincipalClaimName("sub");
        
        return converter;
    }
    
    /**
     * CORS configuration for secure cross-origin requests
     */
    @Bean
    public CorsConfigurationSource corsConfigurationSource() {
        CorsConfiguration configuration = new CorsConfiguration();
        
        // Allow specific origins only
        configuration.setAllowedOriginPatterns(Arrays.asList(
            "https://*.waqiti.com",
            "https://api.example.com",
            "https://api.example.com"
        ));
        
        // Allow specific methods
        configuration.setAllowedMethods(Arrays.asList(
            "GET", "POST", "PUT", "DELETE", "PATCH", "OPTIONS"
        ));
        
        // Allow specific headers
        configuration.setAllowedHeaders(Arrays.asList(
            "Authorization",
            "Content-Type",
            "X-Requested-With",
            "X-Device-ID",
            "X-Session-ID",
            "X-Transaction-ID",
            "X-Correlation-ID",
            "X-User-Agent",
            "X-Real-IP",
            "X-Forwarded-For"
        ));
        
        // Expose specific headers
        configuration.setExposedHeaders(Arrays.asList(
            "X-Total-Count",
            "X-Page-Number",
            "X-Page-Size",
            "X-Rate-Limit-Remaining",
            "X-Rate-Limit-Reset"
        ));
        
        configuration.setAllowCredentials(true);
        configuration.setMaxAge(3600L);
        
        UrlBasedCorsConfigurationSource source = new UrlBasedCorsConfigurationSource();
        source.registerCorsConfiguration("/api/**", configuration);
        source.registerCorsConfiguration("/auth/**", configuration);
        
        return source;
    }
    
    /**
     * Zero Trust Security Properties
     */
    @ConfigurationProperties(prefix = "waqiti.security.zero-trust")
    public static class ZeroTrustProperties {
        
        private boolean enabled = true;
        private boolean strictMtls = true;
        private boolean continuousVerification = true;
        private boolean deviceFingerprinting = true;
        private boolean geolocationVerification = true;
        private boolean behaviorAnalysisEnabled = true;
        private boolean riskBasedAuthentication = true;
        
        // Risk assessment configuration
        private RiskAssessmentConfig riskAssessment = new RiskAssessmentConfig();
        
        // Device trust configuration
        private DeviceTrustConfig deviceTrust = new DeviceTrustConfig();
        
        // Geolocation configuration
        private GeolocationConfig geolocation = new GeolocationConfig();
        
        // Behavior analysis configuration
        private BehaviorAnalysisConfig behaviorAnalysis = new BehaviorAnalysisConfig();
        
        // Network security configuration
        private NetworkSecurityConfig networkSecurity = new NetworkSecurityConfig();
        
        // Getters and setters
        public boolean isEnabled() { return enabled; }
        public void setEnabled(boolean enabled) { this.enabled = enabled; }
        
        public boolean isStrictMtls() { return strictMtls; }
        public void setStrictMtls(boolean strictMtls) { this.strictMtls = strictMtls; }
        
        public boolean isContinuousVerification() { return continuousVerification; }
        public void setContinuousVerification(boolean continuousVerification) { 
            this.continuousVerification = continuousVerification; 
        }
        
        public boolean isDeviceFingerprinting() { return deviceFingerprinting; }
        public void setDeviceFingerprinting(boolean deviceFingerprinting) { 
            this.deviceFingerprinting = deviceFingerprinting; 
        }
        
        public boolean isGeolocationVerification() { return geolocationVerification; }
        public void setGeolocationVerification(boolean geolocationVerification) { 
            this.geolocationVerification = geolocationVerification; 
        }
        
        public boolean isBehaviorAnalysisEnabled() { return behaviorAnalysisEnabled; }
        public void setBehaviorAnalysisEnabled(boolean behaviorAnalysisEnabled) { 
            this.behaviorAnalysisEnabled = behaviorAnalysisEnabled; 
        }
        
        public boolean isRiskBasedAuthentication() { return riskBasedAuthentication; }
        public void setRiskBasedAuthentication(boolean riskBasedAuthentication) { 
            this.riskBasedAuthentication = riskBasedAuthentication; 
        }
        
        public RiskAssessmentConfig getRiskAssessment() { return riskAssessment; }
        public void setRiskAssessment(RiskAssessmentConfig riskAssessment) { 
            this.riskAssessment = riskAssessment; 
        }
        
        public DeviceTrustConfig getDeviceTrust() { return deviceTrust; }
        public void setDeviceTrust(DeviceTrustConfig deviceTrust) { 
            this.deviceTrust = deviceTrust; 
        }
        
        public GeolocationConfig getGeolocation() { return geolocation; }
        public void setGeolocation(GeolocationConfig geolocation) { 
            this.geolocation = geolocation; 
        }
        
        public BehaviorAnalysisConfig getBehaviorAnalysisConfig() { return behaviorAnalysis; }
        public void setBehaviorAnalysisConfig(BehaviorAnalysisConfig behaviorAnalysis) { 
            this.behaviorAnalysis = behaviorAnalysis; 
        }
        
        public NetworkSecurityConfig getNetworkSecurity() { return networkSecurity; }
        public void setNetworkSecurity(NetworkSecurityConfig networkSecurity) { 
            this.networkSecurity = networkSecurity; 
        }
        
        public static class RiskAssessmentConfig {
            private double highRiskThreshold = 0.7;
            private double mediumRiskThreshold = 0.4;
            private long assessmentIntervalMs = 30000; // 30 seconds
            private boolean enableMlScoring = true;
            private boolean enableRuleBasedScoring = true;
            
            // Getters and setters
            public double getHighRiskThreshold() { return highRiskThreshold; }
            public void setHighRiskThreshold(double highRiskThreshold) { 
                this.highRiskThreshold = highRiskThreshold; 
            }
            
            public double getMediumRiskThreshold() { return mediumRiskThreshold; }
            public void setMediumRiskThreshold(double mediumRiskThreshold) { 
                this.mediumRiskThreshold = mediumRiskThreshold; 
            }
            
            public long getAssessmentIntervalMs() { return assessmentIntervalMs; }
            public void setAssessmentIntervalMs(long assessmentIntervalMs) { 
                this.assessmentIntervalMs = assessmentIntervalMs; 
            }
            
            public boolean isEnableMlScoring() { return enableMlScoring; }
            public void setEnableMlScoring(boolean enableMlScoring) { 
                this.enableMlScoring = enableMlScoring; 
            }
            
            public boolean isEnableRuleBasedScoring() { return enableRuleBasedScoring; }
            public void setEnableRuleBasedScoring(boolean enableRuleBasedScoring) { 
                this.enableRuleBasedScoring = enableRuleBasedScoring; 
            }
        }
        
        public static class DeviceTrustConfig {
            private long deviceTrustExpiryMs = 86400000L; // 24 hours
            private boolean requireDeviceRegistration = true;
            private boolean enableJailbreakDetection = true;
            private List<String> trustedDeviceAttributes = Arrays.asList(
                "deviceId", "fingerprint", "osVersion", "appVersion"
            );
            
            // Getters and setters
            public long getDeviceTrustExpiryMs() { return deviceTrustExpiryMs; }
            public void setDeviceTrustExpiryMs(long deviceTrustExpiryMs) { 
                this.deviceTrustExpiryMs = deviceTrustExpiryMs; 
            }
            
            public boolean isRequireDeviceRegistration() { return requireDeviceRegistration; }
            public void setRequireDeviceRegistration(boolean requireDeviceRegistration) { 
                this.requireDeviceRegistration = requireDeviceRegistration; 
            }
            
            public boolean isEnableJailbreakDetection() { return enableJailbreakDetection; }
            public void setEnableJailbreakDetection(boolean enableJailbreakDetection) { 
                this.enableJailbreakDetection = enableJailbreakDetection; 
            }
            
            public List<String> getTrustedDeviceAttributes() { return trustedDeviceAttributes; }
            public void setTrustedDeviceAttributes(List<String> trustedDeviceAttributes) { 
                this.trustedDeviceAttributes = trustedDeviceAttributes; 
            }
        }
        
        public static class GeolocationConfig {
            private boolean enableGeoBlocking = true;
            private List<String> blockedCountries = Arrays.asList("CN", "RU", "KP", "IR");
            private List<String> allowedCountries = Arrays.asList("US", "CA", "GB", "DE", "FR", "AU");
            private double maxLocationDeviationKm = 1000.0;
            private boolean enableVpnDetection = true;
            
            // Getters and setters
            public boolean isEnableGeoBlocking() { return enableGeoBlocking; }
            public void setEnableGeoBlocking(boolean enableGeoBlocking) { 
                this.enableGeoBlocking = enableGeoBlocking; 
            }
            
            public List<String> getBlockedCountries() { return blockedCountries; }
            public void setBlockedCountries(List<String> blockedCountries) { 
                this.blockedCountries = blockedCountries; 
            }
            
            public List<String> getAllowedCountries() { return allowedCountries; }
            public void setAllowedCountries(List<String> allowedCountries) { 
                this.allowedCountries = allowedCountries; 
            }
            
            public double getMaxLocationDeviationKm() { return maxLocationDeviationKm; }
            public void setMaxLocationDeviationKm(double maxLocationDeviationKm) { 
                this.maxLocationDeviationKm = maxLocationDeviationKm; 
            }
            
            public boolean isEnableVpnDetection() { return enableVpnDetection; }
            public void setEnableVpnDetection(boolean enableVpnDetection) { 
                this.enableVpnDetection = enableVpnDetection; 
            }
        }
        
        public static class BehaviorAnalysisConfig {
            private boolean enableKeystrokeDynamics = true;
            private boolean enableMouseMovementAnalysis = true;
            private boolean enableTimingAnalysis = true;
            private double behaviorDeviationThreshold = 0.3;
            private long behaviorLearningPeriodMs = 604800000L; // 7 days
            
            // Getters and setters
            public boolean isEnableKeystrokeDynamics() { return enableKeystrokeDynamics; }
            public void setEnableKeystrokeDynamics(boolean enableKeystrokeDynamics) { 
                this.enableKeystrokeDynamics = enableKeystrokeDynamics; 
            }
            
            public boolean isEnableMouseMovementAnalysis() { return enableMouseMovementAnalysis; }
            public void setEnableMouseMovementAnalysis(boolean enableMouseMovementAnalysis) { 
                this.enableMouseMovementAnalysis = enableMouseMovementAnalysis; 
            }
            
            public boolean isEnableTimingAnalysis() { return enableTimingAnalysis; }
            public void setEnableTimingAnalysis(boolean enableTimingAnalysis) { 
                this.enableTimingAnalysis = enableTimingAnalysis; 
            }
            
            public double getBehaviorDeviationThreshold() { return behaviorDeviationThreshold; }
            public void setBehaviorDeviationThreshold(double behaviorDeviationThreshold) { 
                this.behaviorDeviationThreshold = behaviorDeviationThreshold; 
            }
            
            public long getBehaviorLearningPeriodMs() { return behaviorLearningPeriodMs; }
            public void setBehaviorLearningPeriodMs(long behaviorLearningPeriodMs) { 
                this.behaviorLearningPeriodMs = behaviorLearningPeriodMs; 
            }
        }
        
        public static class NetworkSecurityConfig {
            private boolean enableTlsInspection = true;
            private boolean requireStrictTransportSecurity = true;
            private boolean enableCertificatePinning = true;
            private List<String> allowedTlsVersions = Arrays.asList("TLSv1.2", "TLSv1.3");
            private List<String> allowedCipherSuites = Arrays.asList(
                "TLS_ECDHE_RSA_WITH_AES_256_GCM_SHA384",
                "TLS_ECDHE_RSA_WITH_AES_128_GCM_SHA256",
                "TLS_AES_256_GCM_SHA384",
                "TLS_AES_128_GCM_SHA256"
            );
            
            // Getters and setters
            public boolean isEnableTlsInspection() { return enableTlsInspection; }
            public void setEnableTlsInspection(boolean enableTlsInspection) { 
                this.enableTlsInspection = enableTlsInspection; 
            }
            
            public boolean isRequireStrictTransportSecurity() { return requireStrictTransportSecurity; }
            public void setRequireStrictTransportSecurity(boolean requireStrictTransportSecurity) { 
                this.requireStrictTransportSecurity = requireStrictTransportSecurity; 
            }
            
            public boolean isEnableCertificatePinning() { return enableCertificatePinning; }
            public void setEnableCertificatePinning(boolean enableCertificatePinning) { 
                this.enableCertificatePinning = enableCertificatePinning; 
            }
            
            public List<String> getAllowedTlsVersions() { return allowedTlsVersions; }
            public void setAllowedTlsVersions(List<String> allowedTlsVersions) { 
                this.allowedTlsVersions = allowedTlsVersions; 
            }
            
            public List<String> getAllowedCipherSuites() { return allowedCipherSuites; }
            public void setAllowedCipherSuites(List<String> allowedCipherSuites) { 
                this.allowedCipherSuites = allowedCipherSuites; 
            }
        }
    }
}