package com.waqiti.common.validation.validators;

import com.waqiti.common.validation.constraints.ValidIPAddress;
import com.waqiti.common.validation.constraints.ValidIPAddress.*;
import jakarta.validation.ConstraintValidator;
import jakarta.validation.ConstraintValidatorContext;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.stereotype.Component;

import java.math.BigInteger;
import java.net.*;
import java.nio.ByteBuffer;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Production-ready IP address validator with comprehensive validation features
 * including CIDR support, geographic restrictions, and security checks
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class IPAddressValidator implements ConstraintValidator<ValidIPAddress, String> {
    
    // IPv4 pattern
    private static final Pattern IPv4_PATTERN = Pattern.compile(
        "^(\\d{1,3})\\.(\\d{1,3})\\.(\\d{1,3})\\.(\\d{1,3})(/\\d{1,2})?$"
    );
    
    // IPv6 patterns (simplified for common cases)
    private static final Pattern IPv6_PATTERN = Pattern.compile(
        "^([0-9a-fA-F]{0,4}:){2,7}[0-9a-fA-F]{0,4}(/\\d{1,3})?$"
    );
    
    private static final Pattern IPv6_COMPRESSED_PATTERN = Pattern.compile(
        "^(([0-9a-fA-F]{1,4}:){1,7}:|:((:[0-9a-fA-F]{1,4}){1,7}|:))(/\\d{1,3})?$"
    );
    
    // Cache for expensive operations
    private static final Map<String, IPInfoImpl> IP_INFO_CACHE = new ConcurrentHashMap<>();
    private static final Map<String, Boolean> DNS_CACHE = new ConcurrentHashMap<>();
    private static final Map<String, String> COUNTRY_CACHE = new ConcurrentHashMap<>();
    
    // Known IP ranges
    private static final Set<IPRange> TOR_EXIT_NODES = ConcurrentHashMap.newKeySet();
    private static final Set<IPRange> PROXY_RANGES = ConcurrentHashMap.newKeySet();
    private static final Set<IPRange> CLOUD_PROVIDER_RANGES = ConcurrentHashMap.newKeySet();
    private static final Set<IPRange> SPAM_RANGES = ConcurrentHashMap.newKeySet();
    
    private ValidIPAddress annotation;
    private IPValidationProvider provider;
    
    private final IPGeolocationService geolocationService;
    private final IPReputationService reputationService;
    private final TorExitNodeService torExitNodeService;
    private final com.waqiti.common.validation.cache.IPReputationCacheService ipReputationCacheService;
    
    static {
        initializeTorExitNodes();
        initializeProxyRanges();
        initializeCloudProviderRanges();
        initializeSpamRanges();
    }
    
    @Override
    public void initialize(ValidIPAddress constraintAnnotation) {
        this.annotation = constraintAnnotation;
        
        // Initialize custom provider if specified
        if (constraintAnnotation.provider() != DefaultIPValidationProvider.class) {
            try {
                this.provider = constraintAnnotation.provider().getDeclaredConstructor().newInstance();
            } catch (Exception e) {
                log.error("Failed to initialize custom IP validation provider", e);
                this.provider = new DefaultIPValidationProvider();
            }
        } else {
            this.provider = new DefaultIPValidationProvider();
        }
    }
    
    @Override
    public boolean isValid(String value, ConstraintValidatorContext context) {
        // Handle null values
        if (value == null) {
            return annotation.allowNull();
        }
        
        // Handle empty values
        if (value.trim().isEmpty()) {
            return annotation.allowEmpty();
        }
        
        String normalizedIP = normalizeIPAddress(value);
        
        // Parse IP and CIDR if present
        IPParts parts = parseIP(normalizedIP);
        if (parts == null) {
            addConstraintViolation(context, "Invalid IP address format");
            return false;
        }
        
        // Check CIDR notation
        if (parts.hasCIDR && !annotation.allowCIDR()) {
            addConstraintViolation(context, "CIDR notation not allowed");
            return false;
        }
        
        // Validate IP version
        if (!validateIPVersion(parts, context)) {
            return false;
        }
        
        // Get IP information
        IPInfo ipInfo = getIPInfo(parts.address);
        
        // Validate IP type restrictions
        if (!validateIPType(ipInfo, context)) {
            return false;
        }
        
        // Check allowed/blocked ranges
        if (!validateIPRanges(parts.address, context)) {
            return false;
        }
        
        // Geographic validation
        if (!validateGeographic(ipInfo, context)) {
            return false;
        }
        
        // Security checks
        if (!validateSecurity(ipInfo, context)) {
            return false;
        }
        
        // Reputation check
        if (annotation.checkReputation() && !validateReputation(ipInfo, context)) {
            return false;
        }
        
        // Reverse DNS verification
        if (annotation.verifyReverseDNS() && !verifyReverseDNS(parts.address, context)) {
            return false;
        }
        
        // Mode-specific validation
        if (!validateByMode(ipInfo, context)) {
            return false;
        }
        
        // Use custom provider if available
        if (provider != null && !(provider instanceof DefaultIPValidationProvider)) {
            return provider.isValid(normalizedIP, annotation);
        }
        
        return true;
    }
    
    private void addConstraintViolation(ConstraintValidatorContext context, String message) {
        context.disableDefaultConstraintViolation();
        context.buildConstraintViolationWithTemplate(message)
               .addConstraintViolation();
    }
    
    private String normalizeIPAddress(String ip) {
        return ip.trim();
    }
    
    private IPParts parseIP(String ip) {
        String address = ip;
        Integer cidr = null;
        
        // Check for CIDR notation
        int slashIndex = ip.indexOf('/');
        if (slashIndex > 0) {
            address = ip.substring(0, slashIndex);
            try {
                cidr = Integer.parseInt(ip.substring(slashIndex + 1));
            } catch (NumberFormatException e) {
                return null;
            }
        }
        
        // Validate IPv4
        if (IPv4_PATTERN.matcher(ip).matches()) {
            if (isValidIPv4(address)) {
                return new IPParts(address, IPVersion.IPv4, cidr);
            }
        }
        
        // Validate IPv6
        if (IPv6_PATTERN.matcher(ip).matches() || IPv6_COMPRESSED_PATTERN.matcher(ip).matches()) {
            if (isValidIPv6(address)) {
                return new IPParts(address, IPVersion.IPv6, cidr);
            }
        }
        
        // Try InetAddress as fallback
        try {
            InetAddress addr = InetAddress.getByName(address);
            IPVersion version = addr instanceof Inet4Address ? IPVersion.IPv4 : IPVersion.IPv6;
            return new IPParts(addr.getHostAddress(), version, cidr);
        } catch (UnknownHostException e) {
            return null;
        }
    }
    
    private boolean isValidIPv4(String ip) {
        String[] parts = ip.split("\\.");
        if (parts.length != 4) return false;
        
        for (String part : parts) {
            try {
                int value = Integer.parseInt(part);
                if (value < 0 || value > 255) return false;
            } catch (NumberFormatException e) {
                return false;
            }
        }
        return true;
    }
    
    private boolean isValidIPv6(String ip) {
        try {
            InetAddress addr = Inet6Address.getByName(ip);
            return addr instanceof Inet6Address;
        } catch (UnknownHostException e) {
            return false;
        }
    }
    
    private boolean validateIPVersion(IPParts parts, ConstraintValidatorContext context) {
        switch (annotation.version()) {
            case IPv4:
                if (parts.version != IPVersion.IPv4) {
                    addConstraintViolation(context, "IPv4 address required");
                    return false;
                }
                break;
            case IPv6:
                if (parts.version != IPVersion.IPv6) {
                    addConstraintViolation(context, "IPv6 address required");
                    return false;
                }
                break;
            case BOTH:
                // Both versions allowed
                break;
        }
        return true;
    }
    
    private IPInfo getIPInfo(String address) {
        IPInfoImpl cached = IP_INFO_CACHE.get(address);
        if (cached != null) {
            return cached;
        }
        
        try {
            InetAddress inetAddr = InetAddress.getByName(address);
            IPInfoImpl info = new IPInfoImpl();
            info.address = address;
            info.version = inetAddr instanceof Inet4Address ? IPVersion.IPv4 : IPVersion.IPv6;
            info.type = determineIPType(inetAddr);
            info.isPrivate = inetAddr.isSiteLocalAddress();
            info.isLoopback = inetAddr.isLoopbackAddress();
            info.isMulticast = inetAddr.isMulticastAddress();
            info.isLinkLocal = inetAddr.isLinkLocalAddress();
            
            // Check if reserved
            info.isReserved = isReservedAddress(inetAddr);
            
            // Security checks
            info.isProxy = isProxyIP(address);
            info.isTor = isTorExitNode(address);
            info.isCloud = isCloudProviderIP(address);
            
            // Get geolocation if service available
            if (geolocationService != null) {
                GeolocationInfo geo = geolocationService.getLocation(address);
                if (geo != null) {
                    info.countryCode = geo.getCountryCode();
                    info.city = geo.getCity();
                    info.latitude = geo.getLatitude();
                    info.longitude = geo.getLongitude();
                    info.timeZone = geo.getTimeZone();
                    info.isp = geo.getISP();
                    info.organization = geo.getOrganization();
                }
            }
            
            // Get reputation if service available
            if (reputationService != null) {
                info.reputation = reputationService.getReputation(address);
            }
            
            IP_INFO_CACHE.put(address, info);
            return info;
            
        } catch (UnknownHostException e) {
            log.error("Failed to get IP info for: {}", address, e);
            return new IPInfoImpl();
        }
    }
    
    private IPType determineIPType(InetAddress addr) {
        if (addr.isLoopbackAddress()) return IPType.LOOPBACK;
        if (addr.isSiteLocalAddress()) return IPType.PRIVATE;
        if (addr.isMulticastAddress()) return IPType.MULTICAST;
        if (addr.isLinkLocalAddress()) return IPType.LINK_LOCAL;
        
        if (addr instanceof Inet4Address) {
            byte[] bytes = addr.getAddress();
            
            // Check for specific ranges
            if (bytes[0] == 0) return IPType.RESERVED; // 0.0.0.0/8
            if (bytes[0] == 10) return IPType.PRIVATE; // 10.0.0.0/8
            if (bytes[0] == (byte)172 && (bytes[1] >= 16 && bytes[1] <= 31)) return IPType.PRIVATE; // 172.16.0.0/12
            if (bytes[0] == (byte)192 && bytes[1] == (byte)168) return IPType.PRIVATE; // 192.168.0.0/16
            if (bytes[0] == (byte)169 && bytes[1] == (byte)254) return IPType.LINK_LOCAL; // 169.254.0.0/16
            if (bytes[0] == (byte)224) return IPType.MULTICAST; // 224.0.0.0/4
            if (bytes[0] == (byte)255 && bytes[1] == (byte)255 && bytes[2] == (byte)255 && bytes[3] == (byte)255) {
                return IPType.BROADCAST; // 255.255.255.255
            }
            if (bytes[0] == (byte)192 && bytes[1] == 0 && bytes[2] == 2) return IPType.DOCUMENTATION; // 192.0.2.0/24
            if (bytes[0] == (byte)198 && bytes[1] == 51 && bytes[2] == 100) return IPType.DOCUMENTATION; // 198.51.100.0/24
            if (bytes[0] == (byte)203 && bytes[1] == 0 && bytes[2] == 113) return IPType.DOCUMENTATION; // 203.0.113.0/24
            if (bytes[0] == (byte)100 && (bytes[1] >= 64 && bytes[1] <= 127)) return IPType.CARRIER_GRADE_NAT; // 100.64.0.0/10
            if (bytes[0] == (byte)198 && (bytes[1] >= 18 && bytes[1] <= 19)) return IPType.BENCHMARK_TESTING; // 198.18.0.0/15
            if (bytes[0] >= (byte)240) return IPType.FUTURE_USE; // 240.0.0.0/4
        }
        
        return IPType.PUBLIC;
    }
    
    private boolean isReservedAddress(InetAddress addr) {
        if (addr instanceof Inet4Address) {
            byte[] bytes = addr.getAddress();
            return bytes[0] == 0 || // 0.0.0.0/8
                   bytes[0] >= (byte)240; // 240.0.0.0/4 and above
        }
        return false;
    }
    
    private boolean validateIPType(IPInfo ipInfo, ConstraintValidatorContext context) {
        if (!annotation.allowPrivate() && ipInfo.isPrivate()) {
            addConstraintViolation(context, "Private IP addresses not allowed");
            return false;
        }
        
        if (!annotation.allowLoopback() && ipInfo.isLoopback()) {
            addConstraintViolation(context, "Loopback addresses not allowed");
            return false;
        }
        
        if (!annotation.allowMulticast() && ipInfo.isMulticast()) {
            addConstraintViolation(context, "Multicast addresses not allowed");
            return false;
        }
        
        if (!annotation.allowLinkLocal() && ipInfo.isLinkLocal()) {
            addConstraintViolation(context, "Link-local addresses not allowed");
            return false;
        }
        
        if (!annotation.allowReserved() && ipInfo.isReserved()) {
            addConstraintViolation(context, "Reserved addresses not allowed");
            return false;
        }
        
        return true;
    }
    
    private boolean validateIPRanges(String address, ConstraintValidatorContext context) {
        // Check allowed ranges
        if (annotation.allowedRanges().length > 0) {
            boolean inAllowedRange = false;
            for (String range : annotation.allowedRanges()) {
                if (isInCIDRRange(address, range)) {
                    inAllowedRange = true;
                    break;
                }
            }
            if (!inAllowedRange) {
                addConstraintViolation(context, "IP address not in allowed range");
                return false;
            }
        }
        
        // Check blocked ranges
        if (annotation.blockedRanges().length > 0) {
            for (String range : annotation.blockedRanges()) {
                if (isInCIDRRange(address, range)) {
                    addConstraintViolation(context, "IP address in blocked range: " + range);
                    return false;
                }
            }
        }
        
        return true;
    }
    
    private boolean isInCIDRRange(String ipAddress, String cidr) {
        try {
            String[] parts = cidr.split("/");
            InetAddress targetAddr = InetAddress.getByName(ipAddress);
            InetAddress rangeAddr = InetAddress.getByName(parts[0]);
            int prefixLength = Integer.parseInt(parts[1]);
            
            byte[] targetBytes = targetAddr.getAddress();
            byte[] rangeBytes = rangeAddr.getAddress();
            
            if (targetBytes.length != rangeBytes.length) {
                return false; // Different IP versions
            }
            
            int bytesToCheck = prefixLength / 8;
            int bitsToCheck = prefixLength % 8;
            
            // Check full bytes
            for (int i = 0; i < bytesToCheck; i++) {
                if (targetBytes[i] != rangeBytes[i]) {
                    return false;
                }
            }
            
            // Check remaining bits
            if (bitsToCheck > 0 && bytesToCheck < targetBytes.length) {
                int mask = 0xFF << (8 - bitsToCheck);
                if ((targetBytes[bytesToCheck] & mask) != (rangeBytes[bytesToCheck] & mask)) {
                    return false;
                }
            }
            
            return true;
        } catch (Exception e) {
            log.error("Failed to check CIDR range: {} for IP: {}", cidr, ipAddress, e);
            return false;
        }
    }
    
    private boolean validateGeographic(IPInfo ipInfo, ConstraintValidatorContext context) {
        String countryCode = ipInfo.getCountryCode();
        if (countryCode == null || countryCode.isEmpty()) {
            // Cannot validate geographic restrictions without country info
            return annotation.allowedCountries().length == 0 && annotation.blockedCountries().length == 0;
        }
        
        // Check allowed countries
        if (annotation.allowedCountries().length > 0) {
            boolean allowed = Arrays.asList(annotation.allowedCountries())
                .contains(countryCode.toUpperCase());
            if (!allowed) {
                addConstraintViolation(context, "IP from country not in allowed list: " + countryCode);
                return false;
            }
        }
        
        // Check blocked countries
        if (annotation.blockedCountries().length > 0) {
            boolean blocked = Arrays.asList(annotation.blockedCountries())
                .contains(countryCode.toUpperCase());
            if (blocked) {
                addConstraintViolation(context, "IP from blocked country: " + countryCode);
                return false;
            }
        }
        
        return true;
    }
    
    private boolean validateSecurity(IPInfo ipInfo, ConstraintValidatorContext context) {
        if (annotation.detectProxy() && ipInfo.isProxy()) {
            addConstraintViolation(context, "Proxy IP detected");
            return false;
        }
        
        if (annotation.detectTor() && ipInfo.isTor()) {
            addConstraintViolation(context, "TOR exit node detected");
            return false;
        }
        
        if (annotation.detectCloud() && ipInfo.isCloud()) {
            addConstraintViolation(context, "Cloud provider IP detected");
            return false;
        }
        
        return true;
    }
    
    private boolean validateReputation(IPInfo ipInfo, ConstraintValidatorContext context) {
        ReputationInfo reputation = ipInfo.getReputation();
        if (reputation == null) {
            // No reputation data available
            return true;
        }
        
        if (reputation.isMalicious()) {
            addConstraintViolation(context, "IP has malicious reputation");
            return false;
        }
        
        if (reputation.isSpam()) {
            addConstraintViolation(context, "IP associated with spam");
            return false;
        }
        
        if (reputation.getScore() < 0.3) { // Low reputation score
            addConstraintViolation(context, "IP has poor reputation score: " + reputation.getScore());
            return false;
        }
        
        return true;
    }
    
    private boolean verifyReverseDNS(String address, ConstraintValidatorContext context) {
        Boolean cached = DNS_CACHE.get(address);
        if (cached != null) {
            return cached;
        }
        
        try {
            InetAddress addr = InetAddress.getByName(address);
            String hostname = addr.getCanonicalHostName();
            
            // If hostname equals IP, reverse DNS failed
            boolean hasReverseDNS = !hostname.equals(address);
            DNS_CACHE.put(address, hasReverseDNS);
            
            if (!hasReverseDNS) {
                addConstraintViolation(context, "No reverse DNS record found");
            }
            
            return hasReverseDNS;
        } catch (Exception e) {
            log.error("Reverse DNS lookup failed for: {}", address, e);
            DNS_CACHE.put(address, false);
            return false;
        }
    }
    
    private boolean validateByMode(IPInfo ipInfo, ConstraintValidatorContext context) {
        switch (annotation.mode()) {
            case SECURITY:
                // Additional security checks
                if (ipInfo.isProxy() || ipInfo.isTor() || ipInfo.isCloud()) {
                    addConstraintViolation(context, "IP fails security validation (proxy/tor/cloud detected)");
                    return false;
                }
                if (ipInfo.getReputation() != null && ipInfo.getReputation().getScore() < 0.5) {
                    addConstraintViolation(context, "IP fails security validation (low reputation)");
                    return false;
                }
                break;
                
            case GEOGRAPHIC:
                // Must have country information for geographic mode
                if (ipInfo.getCountryCode() == null || ipInfo.getCountryCode().isEmpty()) {
                    addConstraintViolation(context, "Cannot determine geographic location");
                    return false;
                }
                break;
                
            case NETWORK:
                // Network topology validation
                if (ipInfo.getType() != IPType.PUBLIC && ipInfo.getType() != IPType.PRIVATE) {
                    addConstraintViolation(context, "Invalid network topology for IP type: " + ipInfo.getType());
                    return false;
                }
                break;
                
            case STRICT:
                // Strict validation - public IPs only, no proxies/VPNs
                if (ipInfo.getType() != IPType.PUBLIC) {
                    addConstraintViolation(context, "Only public IP addresses allowed in strict mode");
                    return false;
                }
                if (ipInfo.isProxy() || ipInfo.isTor() || ipInfo.isCloud()) {
                    addConstraintViolation(context, "Proxy/VPN/Cloud IPs not allowed in strict mode");
                    return false;
                }
                break;
        }
        return true;
    }
    
    private boolean isProxyIP(String address) {
        if (torExitNodeService != null) {
            return torExitNodeService.isProxy(address);
        }
        return checkIPInRanges(address, PROXY_RANGES);
    }
    
    private boolean isTorExitNode(String address) {
        if (torExitNodeService != null) {
            return torExitNodeService.isTorExitNode(address);
        }
        return checkIPInRanges(address, TOR_EXIT_NODES);
    }
    
    private boolean isCloudProviderIP(String address) {
        return checkIPInRanges(address, CLOUD_PROVIDER_RANGES);
    }
    
    private boolean checkIPInRanges(String address, Set<IPRange> ranges) {
        for (IPRange range : ranges) {
            if (isInCIDRRange(address, range.cidr)) {
                return true;
            }
        }
        return false;
    }
    
    private static void initializeTorExitNodes() {
        // Sample TOR exit nodes (in production, load from updated list)
        TOR_EXIT_NODES.add(new IPRange("185.220.100.0/24", "TOR"));
        TOR_EXIT_NODES.add(new IPRange("185.220.101.0/24", "TOR"));
        TOR_EXIT_NODES.add(new IPRange("185.220.102.0/24", "TOR"));
        TOR_EXIT_NODES.add(new IPRange("185.220.103.0/24", "TOR"));
    }
    
    private static void initializeProxyRanges() {
        // Known proxy/VPN provider ranges (sample)
        PROXY_RANGES.add(new IPRange("104.200.128.0/19", "ProxyProvider"));
        PROXY_RANGES.add(new IPRange("45.76.0.0/16", "VPNProvider"));
    }
    
    private static void initializeCloudProviderRanges() {
        // AWS ranges (sample)
        CLOUD_PROVIDER_RANGES.add(new IPRange("52.0.0.0/8", "AWS"));
        CLOUD_PROVIDER_RANGES.add(new IPRange("54.0.0.0/8", "AWS"));
        
        // Google Cloud ranges (sample)
        CLOUD_PROVIDER_RANGES.add(new IPRange("35.190.0.0/16", "GCP"));
        CLOUD_PROVIDER_RANGES.add(new IPRange("35.240.0.0/13", "GCP"));
        
        // Azure ranges (sample)
        CLOUD_PROVIDER_RANGES.add(new IPRange("13.64.0.0/11", "Azure"));
        CLOUD_PROVIDER_RANGES.add(new IPRange("40.64.0.0/10", "Azure"));
    }
    
    private static void initializeSpamRanges() {
        // Known spam source ranges (sample)
        SPAM_RANGES.add(new IPRange("91.109.16.0/20", "SpamSource"));
    }
    
    /**
     * IP parts holder
     */
    private static class IPParts {
        final String address;
        final IPVersion version;
        final Integer cidr;
        final boolean hasCIDR;
        
        IPParts(String address, IPVersion version, Integer cidr) {
            this.address = address;
            this.version = version;
            this.cidr = cidr;
            this.hasCIDR = cidr != null;
        }
        
        boolean hasCIDR() {
            return cidr != null;
        }
    }
    
    /**
     * IP range holder
     */
    private static class IPRange {
        final String cidr;
        final String provider;
        
        IPRange(String cidr, String provider) {
            this.cidr = cidr;
            this.provider = provider;
        }
    }
    
    /**
     * IP info implementation
     */
    private static class IPInfoImpl implements IPInfo {
        String address;
        IPVersion version;
        IPType type;
        boolean isPrivate;
        boolean isLoopback;
        boolean isMulticast;
        boolean isLinkLocal;
        boolean isReserved;
        boolean isProxy;
        boolean isTor;
        boolean isCloud;
        String countryCode;
        String city;
        String isp;
        String organization;
        String reverseDNS;
        double latitude;
        double longitude;
        String timeZone;
        ReputationInfo reputation;
        NetworkInfo networkInfo;
        
        @Override
        public String getAddress() { return address; }
        
        @Override
        public IPVersion getVersion() { return version; }
        
        @Override
        public IPType getType() { return type; }
        
        @Override
        public boolean isPrivate() { return isPrivate; }
        
        @Override
        public boolean isLoopback() { return isLoopback; }
        
        @Override
        public boolean isMulticast() { return isMulticast; }
        
        @Override
        public boolean isLinkLocal() { return isLinkLocal; }
        
        @Override
        public boolean isReserved() { return isReserved; }
        
        @Override
        public boolean isProxy() { return isProxy; }
        
        @Override
        public boolean isTor() { return isTor; }
        
        @Override
        public boolean isCloud() { return isCloud; }
        
        @Override
        public String getCountryCode() { return countryCode; }
        
        @Override
        public String getCity() { return city; }
        
        @Override
        public String getISP() { return isp; }
        
        @Override
        public String getOrganization() { return organization; }
        
        @Override
        public String getReverseDNS() { return reverseDNS; }
        
        @Override
        public double getLatitude() { return latitude; }
        
        @Override
        public double getLongitude() { return longitude; }
        
        @Override
        public String getTimeZone() { return timeZone; }
        
        @Override
        public ReputationInfo getReputation() { return reputation; }
        
        @Override
        public NetworkInfo getNetworkInfo() { return networkInfo; }
    }
    
    /**
     * External services interfaces
     */
    public interface IPGeolocationService {
        GeolocationInfo getLocation(String ipAddress);
    }
    
    public interface GeolocationInfo {
        String getCountryCode();
        String getCity();
        double getLatitude();
        double getLongitude();
        String getTimeZone();
        String getISP();
        String getOrganization();
    }
    
    public interface IPReputationService {
        ReputationInfo getReputation(String ipAddress);
    }
    
    public interface TorExitNodeService {
        boolean isTorExitNode(String ipAddress);
        boolean isProxy(String ipAddress);
        Set<String> getTorExitNodes();
    }
}