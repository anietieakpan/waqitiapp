package com.waqiti.common.validation.constraints;

import com.waqiti.common.validation.validators.IPAddressValidator;
import jakarta.validation.Constraint;
import jakarta.validation.Payload;
import java.lang.annotation.*;

/**
 * Comprehensive IP address validation constraint with advanced features
 * Supports IPv4, IPv6, CIDR notation, and security checks
 */
@Documented
@Constraint(validatedBy = IPAddressValidator.class)
@Target({ElementType.METHOD, ElementType.FIELD, ElementType.ANNOTATION_TYPE, ElementType.PARAMETER, ElementType.TYPE_USE})
@Retention(RetentionPolicy.RUNTIME)
@Repeatable(ValidIPAddress.List.class)
public @interface ValidIPAddress {
    String message() default "Invalid IP address: ${validatedValue}";
    
    Class<?>[] groups() default {};
    
    Class<? extends Payload>[] payload() default {};
    
    /**
     * Allow null values
     */
    boolean allowNull() default true;
    
    /**
     * Allow empty string values
     */
    boolean allowEmpty() default false;
    
    /**
     * IP version to validate
     */
    IPVersion version() default IPVersion.BOTH;
    
    /**
     * Allow CIDR notation (e.g., 192.168.1.0/24)
     */
    boolean allowCIDR() default false;
    
    /**
     * Allow private IP addresses (RFC 1918)
     */
    boolean allowPrivate() default true;
    
    /**
     * Allow loopback addresses (127.0.0.0/8, ::1)
     */
    boolean allowLoopback() default true;
    
    /**
     * Allow multicast addresses
     */
    boolean allowMulticast() default false;
    
    /**
     * Allow link-local addresses
     */
    boolean allowLinkLocal() default false;
    
    /**
     * Allow reserved addresses
     */
    boolean allowReserved() default false;
    
    /**
     * Check if IP is from a known proxy/VPN
     */
    boolean detectProxy() default false;
    
    /**
     * Check if IP is from a known TOR exit node
     */
    boolean detectTor() default false;
    
    /**
     * Check if IP is from a cloud provider
     */
    boolean detectCloud() default false;
    
    /**
     * Block IPs from specific countries (ISO codes)
     */
    String[] blockedCountries() default {};
    
    /**
     * Allow IPs only from specific countries (ISO codes)
     */
    String[] allowedCountries() default {};
    
    /**
     * Block specific IP ranges (CIDR notation)
     */
    String[] blockedRanges() default {};
    
    /**
     * Allow only specific IP ranges (CIDR notation)
     */
    String[] allowedRanges() default {};
    
    /**
     * Perform reverse DNS lookup
     */
    boolean verifyReverseDNS() default false;
    
    /**
     * Check IP reputation
     */
    boolean checkReputation() default false;
    
    /**
     * Validation mode
     */
    ValidationMode mode() default ValidationMode.STANDARD;
    
    /**
     * Custom validation provider
     */
    Class<? extends IPValidationProvider> provider() default DefaultIPValidationProvider.class;
    
    /**
     * IP versions
     */
    enum IPVersion {
        IPv4,   // IPv4 only
        IPv6,   // IPv6 only
        BOTH    // Both IPv4 and IPv6
    }
    
    /**
     * Validation modes
     */
    enum ValidationMode {
        BASIC,          // Simple format validation
        STANDARD,       // Standard validation with type checks
        STRICT,         // Strict validation with all checks
        SECURITY,       // Security-focused validation
        GEOGRAPHIC,     // Geographic restriction validation
        NETWORK         // Network topology validation
    }
    
    /**
     * IP address types
     */
    enum IPType {
        PUBLIC,
        PRIVATE,
        LOOPBACK,
        MULTICAST,
        LINK_LOCAL,
        RESERVED,
        BROADCAST,
        DOCUMENTATION,
        CARRIER_GRADE_NAT,
        BENCHMARK_TESTING,
        FUTURE_USE
    }
    
    /**
     * Interface for custom IP validation
     */
    interface IPValidationProvider {
        boolean isValid(String ipAddress, ValidIPAddress annotation);
        IPInfo getIPInfo(String ipAddress);
        boolean isInRange(String ipAddress, String cidr);
    }
    
    /**
     * Default implementation
     */
    class DefaultIPValidationProvider implements IPValidationProvider {
        @Override
        public boolean isValid(String ipAddress, ValidIPAddress annotation) {
            return true; // Implementation in validator
        }
        
        @Override
        public IPInfo getIPInfo(String ipAddress) {
            return null; // Implementation in validator
        }
        
        @Override
        public boolean isInRange(String ipAddress, String cidr) {
            return false; // Implementation in validator
        }
    }
    
    /**
     * IP address information
     */
    interface IPInfo {
        String getAddress();
        IPVersion getVersion();
        IPType getType();
        boolean isPrivate();
        boolean isLoopback();
        boolean isMulticast();
        boolean isLinkLocal();
        boolean isReserved();
        boolean isProxy();
        boolean isTor();
        boolean isCloud();
        String getCountryCode();
        String getCity();
        String getISP();
        String getOrganization();
        String getReverseDNS();
        double getLatitude();
        double getLongitude();
        String getTimeZone();
        ReputationInfo getReputation();
        NetworkInfo getNetworkInfo();
    }
    
    /**
     * IP reputation information
     */
    interface ReputationInfo {
        double getScore();
        boolean isMalicious();
        boolean isSpam();
        boolean isBot();
        int getAbuseReports();
        String getThreatLevel();
        String[] getBlacklists();
    }
    
    /**
     * Network information
     */
    interface NetworkInfo {
        String getASN();
        String getASName();
        String getNetworkCIDR();
        String getNetworkName();
        boolean isMobile();
        boolean isDataCenter();
        boolean isEducational();
        boolean isGovernment();
        boolean isBusiness();
        boolean isResidential();
    }
    
    /**
     * Container annotation for repeated constraints
     */
    @Target({ElementType.METHOD, ElementType.FIELD, ElementType.ANNOTATION_TYPE, ElementType.PARAMETER, ElementType.TYPE_USE})
    @Retention(RetentionPolicy.RUNTIME)
    @Documented
    @interface List {
        ValidIPAddress[] value();
    }
}