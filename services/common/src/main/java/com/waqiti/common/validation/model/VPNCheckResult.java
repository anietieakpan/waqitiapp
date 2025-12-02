package com.waqiti.common.validation.model;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.databind.annotation.JsonDeserialize;
import com.fasterxml.jackson.databind.annotation.JsonSerialize;
import com.fasterxml.jackson.datatype.jsr310.deser.LocalDateTimeDeserializer;
import com.fasterxml.jackson.datatype.jsr310.ser.LocalDateTimeSerializer;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.With;

import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import java.io.Serializable;
import java.time.LocalDateTime;
import java.util.Map;
import java.util.HashMap;
import java.util.Set;
import java.util.HashSet;

/**
 * Enterprise-grade VPN Check Result model for comprehensive VPN/Proxy/Tor detection.
 * Provides detailed information about IP address analysis including VPN, proxy, Tor,
 * relay, and hosting provider detection with confidence scoring and metadata.
 * 
 * This class is designed for production use with full validation, serialization support,
 * and comprehensive metadata tracking for audit and analysis purposes.
 */
@Data
@Builder(toBuilder = true)
@NoArgsConstructor
@AllArgsConstructor
@With
@JsonIgnoreProperties(ignoreUnknown = true)
public class VPNCheckResult implements Serializable {
    
    private static final long serialVersionUID = 1L;
    
    /**
     * The IP address that was analyzed
     */
    @NotNull(message = "IP address is required")
    @Pattern(regexp = "^(?:(?:25[0-5]|2[0-4][0-9]|[01]?[0-9][0-9]?)\\.){3}(?:25[0-5]|2[0-4][0-9]|[01]?[0-9][0-9]?)$|^(?:[A-Fa-f0-9]{1,4}:){7}[A-Fa-f0-9]{1,4}$",
            message = "Invalid IP address format")
    private String ipAddress;
    
    /**
     * Whether the IP is identified as a VPN
     */
    @NotNull
    private Boolean isVpn;
    
    /**
     * Whether the IP is identified as a proxy server
     */
    @NotNull
    private Boolean isProxy;
    
    /**
     * Whether the IP is identified as a Tor exit node
     */
    @NotNull
    private Boolean isTor;
    
    /**
     * Whether the IP is identified as a relay service
     */
    @NotNull
    private Boolean isRelay;
    
    /**
     * Whether the IP belongs to a hosting provider (datacenter)
     */
    @NotNull
    private Boolean isHosting;
    
    /**
     * Whether the IP is identified as residential
     */
    @Builder.Default
    private Boolean isResidential = false;
    
    /**
     * Whether the IP is identified as mobile network
     */
    @Builder.Default
    private Boolean isMobile = false;
    
    /**
     * Whether the IP is identified as business/corporate
     */
    @Builder.Default
    private Boolean isBusiness = false;
    
    /**
     * The detected VPN/Proxy provider name
     */
    @Size(max = 255, message = "Provider name cannot exceed 255 characters")
    private String provider;
    
    /**
     * The organization associated with the IP
     */
    @Size(max = 255, message = "Organization name cannot exceed 255 characters")
    private String organization;
    
    /**
     * The ASN (Autonomous System Number) of the IP
     */
    private Long asn;
    
    /**
     * The ASN organization name
     */
    @Size(max = 255, message = "ASN organization cannot exceed 255 characters")
    private String asnOrganization;
    
    /**
     * Overall confidence score of the detection (0.0 to 1.0)
     */
    @NotNull
    @DecimalMin(value = "0.0", message = "Confidence must be at least 0.0")
    @DecimalMax(value = "1.0", message = "Confidence must be at most 1.0")
    private Double confidence;
    
    /**
     * Individual VPN detection confidence score (0.0 to 1.0)
     */
    @DecimalMin(value = "0.0", message = "VPN score must be at least 0.0")
    @DecimalMax(value = "1.0", message = "VPN score must be at most 1.0")
    private Double vpnScore;
    
    /**
     * Individual proxy detection confidence score (0.0 to 1.0)
     */
    @DecimalMin(value = "0.0", message = "Proxy score must be at least 0.0")
    @DecimalMax(value = "1.0", message = "Proxy score must be at most 1.0")
    private Double proxyScore;
    
    /**
     * Individual Tor detection confidence score (0.0 to 1.0)
     */
    @DecimalMin(value = "0.0", message = "Tor score must be at least 0.0")
    @DecimalMax(value = "1.0", message = "Tor score must be at most 1.0")
    private Double torScore;
    
    /**
     * Risk level assessment
     */
    @Builder.Default
    private RiskLevel riskLevel = RiskLevel.LOW;
    
    /**
     * Detection methods used
     */
    @Builder.Default
    private Set<DetectionMethod> detectionMethods = new HashSet<>();
    
    /**
     * Country code of the IP
     */
    @Pattern(regexp = "^[A-Z]{2}$", message = "Country code must be 2 uppercase letters")
    private String countryCode;
    
    /**
     * City associated with the IP
     */
    @Size(max = 255, message = "City name cannot exceed 255 characters")
    private String city;
    
    /**
     * Region/State associated with the IP
     */
    @Size(max = 255, message = "Region name cannot exceed 255 characters")
    private String region;
    
    /**
     * Timestamp when the check was performed
     */
    @NotNull
    @JsonSerialize(using = LocalDateTimeSerializer.class)
    @JsonDeserialize(using = LocalDateTimeDeserializer.class)
    @Builder.Default
    private LocalDateTime checkedAt = LocalDateTime.now();
    
    /**
     * Time taken to perform the check in milliseconds
     */
    private Long checkDurationMs;
    
    /**
     * Data source used for the check
     */
    @Size(max = 100, message = "Data source cannot exceed 100 characters")
    private String dataSource;
    
    /**
     * Cache status of the result
     */
    @Builder.Default
    private CacheStatus cacheStatus = CacheStatus.MISS;
    
    /**
     * Additional metadata for extensibility
     */
    @Builder.Default
    private Map<String, Object> metadata = new HashMap<>();
    
    /**
     * List of detected characteristics
     */
    @Builder.Default
    private Set<String> characteristics = new HashSet<>();
    
    /**
     * Reasons for the detection
     */
    @Builder.Default
    private Set<String> detectionReasons = new HashSet<>();
    
    /**
     * Risk level enumeration
     */
    public enum RiskLevel {
        LOW("Low Risk", 0),
        MEDIUM("Medium Risk", 1),
        HIGH("High Risk", 2),
        CRITICAL("Critical Risk", 3);
        
        private final String description;
        private final int severity;
        
        RiskLevel(String description, int severity) {
            this.description = description;
            this.severity = severity;
        }
        
        public String getDescription() {
            return description;
        }
        
        public int getSeverity() {
            return severity;
        }
        
        /**
         * Calculate risk level based on confidence and detection flags
         */
        public static RiskLevel calculateRiskLevel(boolean isVpn, boolean isProxy, boolean isTor, 
                                                   boolean isRelay, boolean isHosting, double confidence) {
            if (isTor) {
                return CRITICAL;
            } else if ((isVpn || isProxy) && confidence > 0.8) {
                return HIGH;
            } else if ((isVpn || isProxy || isRelay) && confidence > 0.5) {
                return MEDIUM;
            } else if (isHosting && confidence > 0.7) {
                return MEDIUM;
            } else {
                return LOW;
            }
        }
    }
    
    /**
     * Detection method enumeration
     */
    public enum DetectionMethod {
        IP_DATABASE("IP Database Lookup"),
        ASN_ANALYSIS("ASN Analysis"),
        PORT_SCANNING("Port Scanning"),
        REVERSE_DNS("Reverse DNS Lookup"),
        BEHAVIORAL_ANALYSIS("Behavioral Analysis"),
        BLACKLIST_CHECK("Blacklist Check"),
        ML_DETECTION("Machine Learning Detection"),
        REPUTATION_CHECK("Reputation Check"),
        GEOLOCATION_ANOMALY("Geolocation Anomaly Detection"),
        NETWORK_FINGERPRINT("Network Fingerprinting");
        
        private final String description;
        
        DetectionMethod(String description) {
            this.description = description;
        }
        
        public String getDescription() {
            return description;
        }
    }
    
    /**
     * Cache status enumeration
     */
    public enum CacheStatus {
        HIT("Cache Hit"),
        MISS("Cache Miss"),
        REFRESH("Cache Refresh"),
        BYPASS("Cache Bypass");
        
        private final String description;
        
        CacheStatus(String description) {
            this.description = description;
        }
        
        public String getDescription() {
            return description;
        }
    }
    
    /**
     * Check if the IP represents any form of anonymization
     */
    public boolean isAnonymized() {
        return Boolean.TRUE.equals(isVpn) || 
               Boolean.TRUE.equals(isProxy) || 
               Boolean.TRUE.equals(isTor) || 
               Boolean.TRUE.equals(isRelay);
    }
    
    /**
     * Check if the IP is considered suspicious
     */
    public boolean isSuspicious() {
        return isAnonymized() || Boolean.TRUE.equals(isHosting);
    }
    
    /**
     * Get a combined risk score
     */
    public double getCombinedRiskScore() {
        double score = 0.0;
        int factors = 0;
        
        if (vpnScore != null) {
            score += vpnScore;
            factors++;
        }
        if (proxyScore != null) {
            score += proxyScore;
            factors++;
        }
        if (torScore != null) {
            score += torScore * 2; // Tor is weighted higher
            factors += 2;
        }
        
        return factors > 0 ? score / factors : 0.0;
    }
    
    /**
     * Add a detection method
     */
    public void addDetectionMethod(DetectionMethod method) {
        if (detectionMethods == null) {
            detectionMethods = new HashSet<>();
        }
        detectionMethods.add(method);
    }
    
    /**
     * Add a detection reason
     */
    public void addDetectionReason(String reason) {
        if (detectionReasons == null) {
            detectionReasons = new HashSet<>();
        }
        detectionReasons.add(reason);
    }
    
    /**
     * Add a characteristic
     */
    public void addCharacteristic(String characteristic) {
        if (characteristics == null) {
            characteristics = new HashSet<>();
        }
        characteristics.add(characteristic);
    }
    
    /**
     * Add metadata
     */
    public void addMetadata(String key, Object value) {
        if (metadata == null) {
            metadata = new HashMap<>();
        }
        metadata.put(key, value);
    }
    
    /**
     * Create a summary string for logging
     */
    public String toSummaryString() {
        return String.format("VPNCheckResult[ip=%s, vpn=%s, proxy=%s, tor=%s, confidence=%.2f, risk=%s, provider=%s]",
                ipAddress, isVpn, isProxy, isTor, confidence, riskLevel, provider);
    }
}