package com.waqiti.common.validation.facade;

import com.waqiti.common.validation.model.ValidationModels.GeoLocationResult;
import com.waqiti.common.validation.model.ValidationModels.ThreatAssessmentResult;
import com.waqiti.common.validation.model.ValidationModels.VPNDetectionResult;
import org.springframework.validation.annotation.Validated;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;

/**
 * Enterprise IP Reputation Facade
 * 
 * Public interface for IP reputation services following enterprise patterns:
 * - Clean separation of concerns
 * - Async operations for scalability
 * - Comprehensive validation
 * - Circuit breaker integration
 * - Audit trail support
 * - Metric collection points
 * 
 * This facade abstracts the complexity of underlying services while providing
 * a stable, versioned API for enterprise consumers.
 * 
 * @author Waqiti Security Team
 * @version 2.0.0
 * @since 2024-01-18
 */
@Validated
public interface IPReputationFacade {
    
    /**
     * Get comprehensive geolocation information for an IP address
     * 
     * @param ipAddress Valid IPv4 or IPv6 address
     * @return Future containing geolocation results with accuracy metrics
     * @throws ValidationException for invalid IP addresses
     * @throws ServiceUnavailableException when geolocation services are down
     */
    CompletableFuture<GeoLocationResult> getGeoLocation(
        @NotBlank @Pattern(regexp = "^(?:[0-9]{1,3}\\.){3}[0-9]{1,3}$|^[0-9a-fA-F:]+$") String ipAddress
    );
    
    /**
     * Detect VPN/Proxy/Tor usage with confidence scoring
     * 
     * @param ipAddress IP address to analyze
     * @return Future containing VPN detection results with confidence levels
     */
    CompletableFuture<VPNDetectionResult> detectVPNUsage(
        @NotBlank @Pattern(regexp = "^(?:[0-9]{1,3}\\.){3}[0-9]{1,3}$|^[0-9a-fA-F:]+$") String ipAddress
    );
    
    /**
     * Perform comprehensive threat intelligence assessment
     * 
     * @param ipAddress IP address to assess
     * @return Future containing threat assessment with risk scoring
     */
    CompletableFuture<ThreatAssessmentResult> assessThreatLevel(
        @NotBlank @Pattern(regexp = "^(?:[0-9]{1,3}\\.){3}[0-9]{1,3}$|^[0-9a-fA-F:]+$") String ipAddress
    );
    
    /**
     * Batch process multiple IP addresses efficiently
     * 
     * @param ipAddresses List of IP addresses to process
     * @return Future containing map of IP to comprehensive reputation data
     */
    CompletableFuture<Map<String, IPReputationSummary>> batchAssessment(
        @jakarta.validation.Valid List<@Pattern(regexp = "^(?:[0-9]{1,3}\\.){3}[0-9]{1,3}$|^[0-9a-fA-F:]+$") String> ipAddresses
    );
    
    /**
     * Check if IP is from a high-risk jurisdiction
     * 
     * @param ipAddress IP address to check
     * @param riskProfile Risk profile configuration
     * @return Future containing jurisdiction risk assessment
     */
    CompletableFuture<JurisdictionRiskResult> assessJurisdictionRisk(
        @NotBlank String ipAddress,
        @jakarta.validation.Valid RiskProfile riskProfile
    );
    
    /**
     * Composite reputation score combining all factors
     * 
     * @param ipAddress IP address to score
     * @return Future containing overall reputation score (0-100)
     */
    CompletableFuture<ReputationScore> calculateReputationScore(
        @NotBlank String ipAddress
    );
    
    // Supporting model classes
    public static class IPReputationSummary {
        private String ipAddress;
        private GeoLocationResult geoLocation;
        private VPNDetectionResult vpnDetection;
        private ThreatAssessmentResult threatAssessment;
        private JurisdictionRiskResult jurisdictionRisk;
        private ReputationScore overallScore;
        private long assessmentTimestamp;
        private String assessmentId;
        // ... getters, setters, builder
    }
    
    public static class RiskProfile {
        private List<String> highRiskCountries;
        private List<String> sanctionedRegions;
        private double riskThreshold;
        private boolean includeVPNRisk;
        private boolean includeThreatIntelligence;
        // ... getters, setters, builder
    }
    
    public static class JurisdictionRiskResult {
        private String countryCode;
        private String jurisdiction;
        private double riskScore;
        private List<String> riskFactors;
        private boolean isSanctioned;
        private boolean isHighRisk;
        // ... getters, setters, builder
    }
    
    public static class ReputationScore {
        private double score; // 0-100
        private String riskLevel; // LOW, MEDIUM, HIGH, CRITICAL
        private Map<String, Double> factorWeights;
        private List<String> riskIndicators;
        private long calculatedAt;
        // ... getters, setters, builder
    }
}