package com.waqiti.common.security.hsm;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;
import java.util.Map;

/**
 * Comprehensive HSM status and health information for enterprise-grade financial systems
 * Supports industrial HSM requirements including FIPS 140-2, Common Criteria, and regulatory compliance
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class HSMStatus {
    
    // Core Status Fields
    private HSMProviderState state;
    private String version;
    private String serialNumber;
    private String model;
    private String manufacturer;
    private LocalDateTime lastHealthCheck;
    private boolean authenticated;
    
    // Resource Management
    private int availableSlots;
    private int usedSlots;
    private long totalMemory;
    private long freeMemory;
    private int keyCount;
    
    // Performance Monitoring
    private double cpuUsage;
    private double temperatureCelsius;
    private long operationsPerSecond;
    private long averageResponseTimeMs;
    private int queuedOperations;
    
    // Security & Compliance
    private boolean tamperDetected;
    private HSMAuthenticationMode authMode;
    private boolean fips140Level2Compliant;
    private boolean fips140Level3Compliant;
    private boolean commonCriteriaCompliant;
    private String securityLevel;
    private LocalDateTime lastSecurityAudit;
    
    // Capabilities & Configuration
    private String[] supportedAlgorithms;
    private HSMCapabilities capabilities;
    private Map<String, Object> additionalInfo;
    private String configurationHash;
    
    // High Availability & Clustering
    private boolean isClusterMember;
    private String clusterNodeId;
    private int clusterSize;
    private HSMClusterHealth clusterHealth;
    
    // Error & Alert Information
    private String lastErrorMessage;
    private LocalDateTime lastErrorTime;
    private int errorCount;
    private HSMAlertLevel alertLevel;
    private String[] activeAlerts;
    
    /**
     * HSM Provider State - Enterprise grade state management
     */
    public enum HSMProviderState {
        UNKNOWN,
        INITIALIZING,
        READY,
        BUSY,
        MAINTENANCE,
        ERROR,
        TAMPERED,
        OFFLINE,
        DEGRADED,
        FAILOVER_ACTIVE,
        CLUSTER_SYNC,
        FIRMWARE_UPDATE
    }
    
    /**
     * HSM Authentication Modes for enterprise security
     */
    public enum HSMAuthenticationMode {
        NO_AUTHENTICATION,
        PASSWORD,
        SMART_CARD,
        BIOMETRIC,
        MULTI_FACTOR,
        CERTIFICATE,
        HARDWARE_TOKEN,
        ROLE_BASED,
        DUAL_CONTROL
    }
    
    /**
     * HSM Alert Levels for operational monitoring
     */
    public enum HSMAlertLevel {
        NORMAL,
        INFO,
        WARNING,
        CRITICAL,
        EMERGENCY
    }
    
    /**
     * Cluster health status
     */
    public enum HSMClusterHealth {
        HEALTHY,
        DEGRADED,
        PARTIAL_FAILURE,
        CRITICAL_FAILURE,
        SPLIT_BRAIN,
        SYNCHRONIZING
    }
    
    /**
     * Comprehensive HSM Capabilities for enterprise features
     */
    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class HSMCapabilities {
        // Cryptographic Capabilities
        private boolean supportsAES;
        private boolean supportsRSA;
        private boolean supportsECC;
        private boolean supportsDES;
        private boolean supports3DES;
        private boolean supportsChaCha20;
        
        // Key Management
        private boolean supportsKeyGeneration;
        private boolean supportsKeyWrapping;
        private boolean supportsKeyDerivation;
        private boolean supportsKeyEscrow;
        private boolean supportsKeyArchival;
        
        // Digital Signatures
        private boolean supportsDigitalSigning;
        private boolean supportsTimeStamping;
        private boolean supportsNonRepudiation;
        
        // Protocols & Standards
        private boolean supportsPKCS11;
        private boolean supportsPKCS7;
        private boolean supportsSSL;
        private boolean supportsTLS;
        private boolean supportsIPSec;
        
        // Compliance & Certification
        private boolean supportsFIPS140Level2;
        private boolean supportsFIPS140Level3;
        private boolean supportsFIPS140Level4;
        private boolean supportsCommonCriteria;
        private String[] certifications;
        
        // Performance & Scalability
        private int maxKeySize;
        private int maxConcurrentOperations;
        private long maxOperationsPerSecond;
        private boolean supportsHighAvailability;
        private boolean supportsLoadBalancing;
        private boolean supportsFailover;
        
        // Enterprise Features
        private boolean supportsRoleBasedAccess;
        private boolean supportsAuditLogging;
        private boolean supportsRemoteManagement;
        private boolean supportsBackupRestore;
        private boolean supportsFirmwareUpdate;
        private boolean supportsNetworkTimeProtocol;
    }
    
    /**
     * Check if HSM is healthy and ready for production operations
     */
    public boolean isHealthy() {
        return state == HSMProviderState.READY && 
               authenticated && 
               !tamperDetected && 
               freeMemory > 0 &&
               alertLevel != HSMAlertLevel.CRITICAL &&
               alertLevel != HSMAlertLevel.EMERGENCY;
    }
    
    /**
     * Check if HSM is in production-ready state
     */
    public boolean isProductionReady() {
        return isHealthy() &&
               (fips140Level2Compliant || fips140Level3Compliant) &&
               authMode != HSMAuthenticationMode.NO_AUTHENTICATION &&
               temperatureCelsius < 80.0 &&
               getMemoryUtilization() < 85.0;
    }
    
    /**
     * Get memory utilization percentage
     */
    public double getMemoryUtilization() {
        return totalMemory > 0 ? 
               ((double) (totalMemory - freeMemory) / totalMemory) * 100.0 : 0.0;
    }
    
    /**
     * Get slot utilization percentage
     */
    public double getSlotUtilization() {
        int totalSlots = availableSlots + usedSlots;
        return totalSlots > 0 ? ((double) usedSlots / totalSlots) * 100.0 : 0.0;
    }
    
    /**
     * Check if HSM requires immediate attention
     */
    public boolean requiresAttention() {
        return tamperDetected || 
               state == HSMProviderState.ERROR || 
               state == HSMProviderState.TAMPERED ||
               alertLevel == HSMAlertLevel.CRITICAL ||
               alertLevel == HSMAlertLevel.EMERGENCY ||
               temperatureCelsius > 85.0 ||
               getMemoryUtilization() > 90.0 ||
               errorCount > 10;
    }
    
    /**
     * Check if HSM supports required compliance level
     */
    public boolean meetsComplianceRequirements(String requiredLevel) {
        switch (requiredLevel.toUpperCase()) {
            case "FIPS_140_2":
                return fips140Level2Compliant || fips140Level3Compliant;
            case "FIPS_140_3":
                return fips140Level3Compliant;
            case "COMMON_CRITERIA":
                return commonCriteriaCompliant;
            default:
                return false;
        }
    }
    
    /**
     * Get comprehensive health score (0-100)
     */
    public int getHealthScore() {
        int score = 100;
        
        // State penalties
        if (state != HSMProviderState.READY) score -= 30;
        if (tamperDetected) score -= 50;
        if (!authenticated) score -= 40;
        
        // Performance penalties
        if (temperatureCelsius > 80) score -= 20;
        if (getMemoryUtilization() > 85) score -= 15;
        if (errorCount > 5) score -= 10;
        
        // Alert level penalties
        switch (alertLevel) {
            case WARNING: score -= 10; break;
            case CRITICAL: score -= 30; break;
            case EMERGENCY: score -= 50; break;
        }
        
        return Math.max(0, score);
    }
    
    /**
     * Check if HSM is operational (backward compatibility)
     */
    public boolean isOperational() {
        return isHealthy();
    }
    
    /**
     * Get memory usage percentage (backward compatibility)
     */
    public double getMemoryUsagePercent() {
        return getMemoryUtilization();
    }
    
    /**
     * Get slot usage percentage (backward compatibility)
     */
    public double getSlotUsagePercent() {
        return getSlotUtilization();
    }
}