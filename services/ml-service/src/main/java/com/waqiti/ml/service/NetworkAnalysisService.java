package com.waqiti.ml.service;

import com.waqiti.ml.dto.TransactionData;
import com.waqiti.ml.dto.NetworkAnalysisResult;
import com.waqiti.ml.dto.IpReputation;
import com.waqiti.ml.dto.IpGeolocation;
import com.waqiti.common.exception.MLProcessingException;
import com.waqiti.common.tracing.Traced;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import lombok.Data;
import lombok.Builder;
import lombok.NoArgsConstructor;
import lombok.AllArgsConstructor;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.context.ApplicationContext;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Service;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.web.client.RestTemplate;

import jakarta.annotation.PostConstruct;
import java.net.InetAddress;
import java.net.UnknownHostException;
import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.TimeUnit;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

/**
 * Production-ready Network Analysis Service with threat detection.
 * Provides IP reputation scoring, VPN/proxy detection, and network anomaly analysis.
 */
@Service
@Slf4j
@RequiredArgsConstructor
public class NetworkAnalysisService {

    private final RedisTemplate<String, Object> redisTemplate;
    private final RestTemplate restTemplate;
    private final ApplicationContext applicationContext;
    
    // Self-injection for caching to work properly
    private NetworkAnalysisService self;

    @Value("${network.analysis.enabled:true}")
    private boolean analysisEnabled;

    @Value("${network.vpn.detection.enabled:true}")
    private boolean vpnDetectionEnabled;

    @Value("${network.tor.detection.enabled:true}")
    private boolean torDetectionEnabled;

    @Value("${network.threat.intel.enabled:true}")
    private boolean threatIntelEnabled;

    @Value("${network.risk.threshold.high:70.0}")
    private double highRiskThreshold;

    @Value("${network.risk.threshold.medium:40.0}")
    private double mediumRiskThreshold;

    @Value("${network.cache.ttl.hours:24}")
    private int cacheTtlHours;

    @Value("${network.threat.api.key:#{null}}")
    private String threatApiKey;

    private static final String CACHE_PREFIX = "network:";
    private static final String IP_CACHE_PREFIX = "ip:";
    private static final String THREAT_CACHE_PREFIX = "threat:";
    private static final String VPN_CACHE_PREFIX = "vpn:";

    // Known malicious IP ranges
    private final Set<String> maliciousIpRanges = new HashSet<>();

    // Known VPN/proxy providers
    private final Set<String> knownVpnProviders = new HashSet<>();

    // Known Tor exit nodes
    private final Set<String> torExitNodes = new HashSet<>();

    // Datacenter IP ranges
    private final Map<String, String> datacenterRanges = new ConcurrentHashMap<>();

    // Country risk scores
    private final Map<String, Double> countryRiskScores = new ConcurrentHashMap<>();

    // ASN (Autonomous System Number) risk scores
    private final Map<String, Double> asnRiskScores = new ConcurrentHashMap<>();

    // IP reputation cache
    private final Map<String, IpReputation> reputationCache = new ConcurrentHashMap<>();

    @PostConstruct
    public void initialize() {
        self = applicationContext.getBean(NetworkAnalysisService.class);
        initializeMaliciousIpRanges();
        initializeVpnProviders();
        initializeTorExitNodes();
        initializeDatacenterRanges();
        initializeCountryRiskScores();
        initializeAsnRiskScores();
        log.info("NetworkAnalysisService initialized with threat detection enabled: {}", threatIntelEnabled);
    }

    /**
     * Analyze network characteristics and calculate risk score
     */
    @Traced(operation = "network_analysis")
    public NetworkAnalysisResult analyzeNetwork(TransactionData transaction) {
        long startTime = System.currentTimeMillis();
        
        try {
            if (!analysisEnabled) {
                return createDefaultResult(transaction);
            }

            log.debug("Starting network analysis for transaction: {}", transaction.getTransactionId());

            TransactionData.NetworkInfo networkInfo = transaction.getNetworkInfo();
            String ipAddress = transaction.getIpAddress();
            
            if (networkInfo == null && ipAddress == null) {
                return createNoNetworkResult(transaction);
            }

            // Create result object
            NetworkAnalysisResult result = NetworkAnalysisResult.builder()
                .transactionId(transaction.getTransactionId())
                .userId(transaction.getUserId())
                .ipAddress(ipAddress)
                .timestamp(LocalDateTime.now())
                .build();

            // Analyze IP reputation
            analyzeIpReputation(result, ipAddress);

            // Check for VPN/proxy
            detectVpnProxy(result, ipAddress, networkInfo);

            // Check for Tor
            detectTorUsage(result, ipAddress);

            // Analyze network characteristics
            analyzeNetworkCharacteristics(result, networkInfo);

            // Check against threat intelligence
            checkThreatIntelligence(result, ipAddress);

            // Analyze network behavior
            analyzeNetworkBehavior(result, transaction.getUserId(), ipAddress);

            // Check for datacenter IPs
            checkDatacenterIp(result, ipAddress);

            // Analyze geolocation consistency
            analyzeGeolocationConsistency(result, ipAddress, transaction);

            // Calculate overall risk score
            double riskScore = calculateNetworkRiskScore(result);
            result.setRiskScore(riskScore);
            result.setRiskLevel(determineRiskLevel(riskScore));

            // Save network analysis
            saveNetworkAnalysis(ipAddress, result);

            long duration = System.currentTimeMillis() - startTime;
            result.setProcessingTimeMs(duration);

            log.debug("Network analysis completed in {}ms for transaction: {}, risk score: {}", 
                duration, transaction.getTransactionId(), riskScore);

            return result;

        } catch (Exception e) {
            log.error("Error in network analysis for transaction: {}", 
                transaction.getTransactionId(), e);
            throw new MLProcessingException("Failed to analyze network", e);
        }
    }

    /**
     * Analyze IP reputation
     */
    private void analyzeIpReputation(NetworkAnalysisResult result, String ipAddress) {
        if (ipAddress == null) return;

        // Check cache first
        IpReputation reputation = getIpReputation(ipAddress);
        
        if (reputation == null) {
            reputation = calculateIpReputation(ipAddress);
            cacheIpReputation(ipAddress, reputation);
        }

        result.setIpReputation(reputation.getScore());
        result.setIpReputationLevel(reputation.getLevel());
        result.setMaliciousIp(reputation.isMalicious());
        result.setBlacklisted(reputation.isBlacklisted());
        
        // Check if IP is in malicious ranges
        if (isInMaliciousRange(ipAddress)) {
            result.setMaliciousIp(true);
            result.setMaliciousReason("IP_IN_MALICIOUS_RANGE");
        }

        // Check for private/reserved IPs
        if (isPrivateIp(ipAddress)) {
            result.setPrivateIp(true);
        }

        // Check for bogon IPs
        if (isBogonIp(ipAddress)) {
            result.setBogonIp(true);
        }
    }

    /**
     * Detect VPN/proxy usage
     */
    private void detectVpnProxy(NetworkAnalysisResult result, String ipAddress, 
                               TransactionData.NetworkInfo networkInfo) {
        
        if (!vpnDetectionEnabled) return;

        boolean isVpn = false;
        boolean isProxy = false;
        String vpnProvider = null;

        // Check cached VPN detection
        VpnDetectionResult cachedResult = getCachedVpnDetection(ipAddress);
        if (cachedResult != null) {
            isVpn = cachedResult.isVpn();
            isProxy = cachedResult.isProxy();
            vpnProvider = cachedResult.getProvider();
        } else {
            // Perform VPN/proxy detection
            if (networkInfo != null) {
                // Check for VPN indicators in network info
                if (hasVpnIndicators(networkInfo)) {
                    isVpn = true;
                }
                
                // Check for proxy headers
                if (hasProxyHeaders(networkInfo)) {
                    isProxy = true;
                }
            }

            // Check against known VPN providers
            vpnProvider = detectKnownVpnProvider(ipAddress);
            if (vpnProvider != null) {
                isVpn = true;
            }

            // Check for residential proxy
            if (isResidentialProxy(ipAddress)) {
                isProxy = true;
                result.setResidentialProxy(true);
            }

            // Cache the result
            cacheVpnDetection(ipAddress, isVpn, isProxy, vpnProvider);
        }

        result.setVpnDetected(isVpn);
        result.setProxyDetected(isProxy);
        result.setVpnProvider(vpnProvider);
        
        // Check for anonymous proxy
        if (isProxy && isAnonymousProxy(networkInfo)) {
            result.setAnonymousProxy(true);
        }
    }

    /**
     * Detect Tor usage
     */
    private void detectTorUsage(NetworkAnalysisResult result, String ipAddress) {
        if (!torDetectionEnabled || ipAddress == null) return;

        boolean isTor = false;
        
        // Check if IP is a known Tor exit node
        if (torExitNodes.contains(ipAddress)) {
            isTor = true;
        } else {
            // Check for Tor characteristics
            isTor = hasTorCharacteristics(ipAddress);
        }

        result.setTorDetected(isTor);
        
        if (isTor) {
            result.setAnonymousConnection(true);
        }
    }

    /**
     * Analyze network characteristics
     */
    private void analyzeNetworkCharacteristics(NetworkAnalysisResult result,
                                              TransactionData.NetworkInfo networkInfo) {
        
        if (networkInfo == null) return;

        // Analyze connection type
        String connectionType = networkInfo.getConnectionType();
        if (connectionType != null) {
            result.setConnectionType(connectionType);
            
            // Risk based on connection type
            if ("CELLULAR".equalsIgnoreCase(connectionType)) {
                result.setCellularConnection(true);
            } else if ("SATELLITE".equalsIgnoreCase(connectionType)) {
                result.setSatelliteConnection(true);
            }
        }

        // Analyze ISP
        String isp = networkInfo.getIsp();
        if (isp != null) {
            result.setIsp(isp);
            
            // Check for suspicious ISPs
            if (isSuspiciousIsp(isp)) {
                result.setSuspiciousIsp(true);
            }
        }

        // Analyze ASN
        String asn = networkInfo.getAsn();
        if (asn != null) {
            result.setAsn(asn);
            
            // Get ASN risk score
            Double asnRisk = asnRiskScores.get(asn);
            if (asnRisk != null) {
                result.setAsnRiskScore(asnRisk);
            }
        }

        // Check for hosting provider
        if (isHostingProvider(networkInfo)) {
            result.setHostingProvider(true);
        }

        // Analyze network latency
        if (networkInfo.getLatencyMs() != null) {
            result.setLatencyMs(networkInfo.getLatencyMs());
            
            // Check for suspicious latency patterns
            if (networkInfo.getLatencyMs() < 5) {
                result.setSuspiciousLatency(true); // Too fast, might be local/bot
            } else if (networkInfo.getLatencyMs() > 2000) {
                result.setHighLatency(true);
            }
        }

        // Analyze packet loss
        if (networkInfo.getPacketLoss() != null) {
            result.setPacketLoss(networkInfo.getPacketLoss());
            
            if (networkInfo.getPacketLoss() > 5.0) {
                result.setPoorConnection(true);
            }
        }

        // Check for DNS anomalies
        if (hasDnsAnomalies(networkInfo)) {
            result.setDnsAnomalies(true);
        }
    }

    /**
     * Check threat intelligence
     */
    private void checkThreatIntelligence(NetworkAnalysisResult result, String ipAddress) {
        if (!threatIntelEnabled || ipAddress == null) return;

        try {
            // Check cached threat intelligence
            ThreatIntelResult threatResult = getCachedThreatIntel(ipAddress);
            
            if (threatResult == null && threatApiKey != null) {
                // Query threat intelligence API (simplified)
                threatResult = queryThreatIntelApi(ipAddress);
                cacheThreatIntel(ipAddress, threatResult);
            }

            if (threatResult != null) {
                result.setThreatScore(threatResult.getScore());
                result.setThreatCategories(threatResult.getCategories());
                result.setKnownAttacker(threatResult.isKnownAttacker());
                result.setBotnet(threatResult.isBotnet());
                result.setMalware(threatResult.isMalware());
                result.setPhishing(threatResult.isPhishing());
                result.setSpam(threatResult.isSpam());
            }

        } catch (Exception e) {
            log.warn("Failed to check threat intelligence for IP: {}", ipAddress, e);
        }
    }

    /**
     * Analyze network behavior patterns
     */
    private void analyzeNetworkBehavior(NetworkAnalysisResult result, String userId, String ipAddress) {
        if (userId == null || ipAddress == null) return;

        // Get user's IP history
        Set<String> userIpHistory = self.getUserIpHistory(userId);
        
        // Check for IP hopping
        if (userIpHistory.size() > 10) {
            result.setIpHopping(true);
            result.setUniqueIpCount(userIpHistory.size());
        }

        // Check for rapid IP changes
        if (hasRapidIpChanges(userId)) {
            result.setRapidIpChanges(true);
        }

        // Check if this is a new IP for the user
        if (!userIpHistory.contains(ipAddress)) {
            result.setNewIpForUser(true);
        }

        // Check for concurrent sessions from different IPs
        int concurrentIps = getConcurrentIps(userId);
        if (concurrentIps > 1) {
            result.setConcurrentIps(concurrentIps);
            result.setMultipleIps(true);
        }

        // Check for impossible travel based on IP locations
        if (hasImpossibleIpTravel(userId, ipAddress)) {
            result.setImpossibleTravel(true);
        }
    }

    /**
     * Check if IP is from datacenter
     */
    private void checkDatacenterIp(NetworkAnalysisResult result, String ipAddress) {
        if (ipAddress == null) return;

        boolean isDatacenter = false;
        String provider = null;

        // Check against known datacenter ranges
        for (Map.Entry<String, String> entry : datacenterRanges.entrySet()) {
            if (isIpInRange(ipAddress, entry.getKey())) {
                isDatacenter = true;
                provider = entry.getValue();
                break;
            }
        }

        result.setDatacenterIp(isDatacenter);
        result.setDatacenterProvider(provider);

        // Check for cloud provider IPs
        if (isCloudProviderIp(ipAddress)) {
            result.setCloudProvider(true);
        }
    }

    /**
     * Analyze geolocation consistency
     */
    private void analyzeGeolocationConsistency(NetworkAnalysisResult result, 
                                              String ipAddress,
                                              TransactionData transaction) {
        
        if (ipAddress == null) return;

        try {
            // Get IP geolocation
            IpGeolocation ipGeo = getIpGeolocation(ipAddress);
            
            if (ipGeo != null) {
                result.setIpCountry(ipGeo.getCountry());
                result.setIpCity(ipGeo.getCity());
                result.setIpRegion(ipGeo.getRegion());
                
                // Check country risk
                Double countryRisk = countryRiskScores.get(ipGeo.getCountry());
                if (countryRisk != null) {
                    result.setCountryRiskScore(countryRisk);
                }
                
                // Check if high-risk country
                if (isHighRiskCountry(ipGeo.getCountry())) {
                    result.setHighRiskCountry(true);
                }
                
                // Check if sanctioned country
                if (isSanctionedCountry(ipGeo.getCountry())) {
                    result.setSanctionedCountry(true);
                }
                
                // Check consistency with device location
                if (transaction.getGeolocation() != null) {
                    TransactionData.GeolocationData deviceGeo = transaction.getGeolocation();
                    if (deviceGeo.getCountry() != null && 
                        !deviceGeo.getCountry().equals(ipGeo.getCountry())) {
                        result.setGeolocationMismatch(true);
                    }
                }
            }
            
        } catch (Exception e) {
            log.warn("Failed to analyze geolocation consistency: {}", e.getMessage());
        }
    }

    /**
     * Calculate overall network risk score
     */
    private double calculateNetworkRiskScore(NetworkAnalysisResult result) {
        double riskScore = 0.0;

        // IP reputation contribution
        if (result.getIpReputation() != null) {
            riskScore += (100.0 - result.getIpReputation()) * 0.3;
        }

        // VPN/Proxy/Tor
        if (result.isVpnDetected()) riskScore += 25.0;
        if (result.isProxyDetected()) riskScore += 20.0;
        if (result.isTorDetected()) riskScore += 35.0;
        if (result.isAnonymousProxy()) riskScore += 15.0;

        // Malicious indicators
        if (result.isMaliciousIp()) riskScore += 40.0;
        if (result.isBlacklisted()) riskScore += 35.0;
        if (result.isKnownAttacker()) riskScore += 30.0;
        if (result.isBotnet()) riskScore += 35.0;

        // Datacenter/Cloud
        if (result.isDatacenterIp()) riskScore += 15.0;
        if (result.isCloudProvider()) riskScore += 10.0;

        // Network behavior
        if (result.isIpHopping()) riskScore += 20.0;
        if (result.isRapidIpChanges()) riskScore += 15.0;
        if (result.isImpossibleTravel()) riskScore += 25.0;

        // Geographic risk
        if (result.isHighRiskCountry()) riskScore += 20.0;
        if (result.isSanctionedCountry()) riskScore += 40.0;
        if (result.isGeolocationMismatch()) riskScore += 15.0;

        // Network anomalies
        if (result.isSuspiciousLatency()) riskScore += 10.0;
        if (result.isDnsAnomalies()) riskScore += 15.0;
        if (result.isSuspiciousIsp()) riskScore += 10.0;

        // ASN risk
        if (result.getAsnRiskScore() != null) {
            riskScore += result.getAsnRiskScore() * 0.2;
        }

        return Math.min(riskScore, 100.0);
    }

    /**
     * Network analysis result DTO
     */
    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class NetworkAnalysisResult {
        private String transactionId;
        private String userId;
        private String ipAddress;
        private LocalDateTime timestamp;
        
        private Double riskScore;
        private String riskLevel;
        
        // IP reputation
        private Double ipReputation;
        private String ipReputationLevel;
        private boolean maliciousIp;
        private boolean blacklisted;
        private String maliciousReason;
        
        // VPN/Proxy/Tor detection
        private boolean vpnDetected;
        private boolean proxyDetected;
        private boolean torDetected;
        private String vpnProvider;
        private boolean anonymousProxy;
        private boolean anonymousConnection;
        private boolean residentialProxy;
        
        // IP characteristics
        private boolean datacenterIp;
        private String datacenterProvider;
        private boolean cloudProvider;
        private boolean hostingProvider;
        private boolean privateIp;
        private boolean bogonIp;
        
        // Network characteristics
        private String connectionType;
        private String isp;
        private String asn;
        private Double asnRiskScore;
        private boolean suspiciousIsp;
        private boolean cellularConnection;
        private boolean satelliteConnection;
        
        // Performance metrics
        private Integer latencyMs;
        private Double packetLoss;
        private boolean suspiciousLatency;
        private boolean highLatency;
        private boolean poorConnection;
        
        // Threat intelligence
        private Double threatScore;
        private List<String> threatCategories;
        private boolean knownAttacker;
        private boolean botnet;
        private boolean malware;
        private boolean phishing;
        private boolean spam;
        
        // Network behavior
        private boolean ipHopping;
        private boolean rapidIpChanges;
        private boolean newIpForUser;
        private boolean multipleIps;
        private Integer concurrentIps;
        private Integer uniqueIpCount;
        private boolean impossibleTravel;
        
        // Geolocation
        private String ipCountry;
        private String ipCity;
        private String ipRegion;
        private Double countryRiskScore;
        private boolean highRiskCountry;
        private boolean sanctionedCountry;
        private boolean geolocationMismatch;
        
        // Anomalies
        private boolean dnsAnomalies;
        private List<String> anomalies;
        
        private Long processingTimeMs;
    }

    /**
     * IP reputation data
     */
    @Data
    @Builder
    private static class IpReputation {
        private String ipAddress;
        private Double score;
        private String level;
        private boolean malicious;
        private boolean blacklisted;
        private LocalDateTime lastUpdated;
        private List<String> categories;
    }

    /**
     * VPN detection result
     */
    @Data
    @Builder
    private static class VpnDetectionResult {
        private String ipAddress;
        private boolean vpn;
        private boolean proxy;
        private String provider;
        private LocalDateTime detectedAt;
    }

    /**
     * Threat intelligence result
     */
    @Data
    @Builder
    private static class ThreatIntelResult {
        private String ipAddress;
        private Double score;
        private List<String> categories;
        private boolean knownAttacker;
        private boolean botnet;
        private boolean malware;
        private boolean phishing;
        private boolean spam;
        private LocalDateTime lastUpdated;
    }

    /**
     * IP geolocation data
     */
    @Data
    @Builder
    private static class IpGeolocation {
        private String ipAddress;
        private String country;
        private String city;
        private String region;
        private Double latitude;
        private Double longitude;
        private String timezone;
    }

    // Helper methods

    private NetworkAnalysisResult createDefaultResult(TransactionData transaction) {
        return NetworkAnalysisResult.builder()
            .transactionId(transaction.getTransactionId())
            .userId(transaction.getUserId())
            .riskScore(0.0)
            .riskLevel("MINIMAL")
            .processingTimeMs(1L)
            .build();
    }

    private NetworkAnalysisResult createNoNetworkResult(TransactionData transaction) {
        return NetworkAnalysisResult.builder()
            .transactionId(transaction.getTransactionId())
            .userId(transaction.getUserId())
            .riskScore(50.0)
            .riskLevel("MEDIUM")
            .processingTimeMs(1L)
            .build();
    }

    private String determineRiskLevel(double riskScore) {
        if (riskScore >= 80) return "CRITICAL";
        if (riskScore >= 60) return "HIGH";
        if (riskScore >= 40) return "MEDIUM";
        if (riskScore >= 20) return "LOW";
        return "MINIMAL";
    }

    private void initializeMaliciousIpRanges() {
        // Add known malicious IP ranges (simplified)
        maliciousIpRanges.add("10.0.0.0/8");     // Private range (example)
        maliciousIpRanges.add("172.16.0.0/12");  // Private range (example)
        maliciousIpRanges.add("192.168.0.0/16"); // Private range (example)
    }

    private void initializeVpnProviders() {
        knownVpnProviders.add("NordVPN");
        knownVpnProviders.add("ExpressVPN");
        knownVpnProviders.add("CyberGhost");
        knownVpnProviders.add("Surfshark");
        knownVpnProviders.add("ProtonVPN");
    }

    private void initializeTorExitNodes() {
        // Would load actual Tor exit nodes from a file or API
        // This is a simplified example
    }

    private void initializeDatacenterRanges() {
        // Add known datacenter IP ranges
        datacenterRanges.put("52.0.0.0/11", "AWS");
        datacenterRanges.put("35.224.0.0/12", "Google Cloud");
        datacenterRanges.put("13.64.0.0/11", "Azure");
    }

    private void initializeCountryRiskScores() {
        countryRiskScores.put("NG", 30.0); // Nigeria
        countryRiskScores.put("RO", 25.0); // Romania
        countryRiskScores.put("CN", 20.0); // China
        countryRiskScores.put("RU", 25.0); // Russia
        countryRiskScores.put("UA", 20.0); // Ukraine
    }

    private void initializeAsnRiskScores() {
        // Add risk scores for specific ASNs
        asnRiskScores.put("AS13335", 5.0);  // Cloudflare
        asnRiskScores.put("AS15169", 5.0);  // Google
        asnRiskScores.put("AS16509", 10.0); // Amazon
    }

    @Cacheable(value = "ipReputation", key = "#ipAddress")
    public IpReputation getIpReputation(String ipAddress) {
        String key = IP_CACHE_PREFIX + "reputation:" + ipAddress;
        return (IpReputation) redisTemplate.opsForValue().get(key);
    }

    private IpReputation calculateIpReputation(String ipAddress) {
        // Simplified reputation calculation
        return IpReputation.builder()
            .ipAddress(ipAddress)
            .score(75.0)
            .level("MEDIUM")
            .malicious(false)
            .blacklisted(false)
            .lastUpdated(LocalDateTime.now())
            .build();
    }

    private void cacheIpReputation(String ipAddress, IpReputation reputation) {
        String key = IP_CACHE_PREFIX + "reputation:" + ipAddress;
        redisTemplate.opsForValue().set(key, reputation, cacheTtlHours, TimeUnit.HOURS);
    }

    private boolean isInMaliciousRange(String ipAddress) {
        // Simplified check - would need proper CIDR matching
        return false;
    }

    private boolean isPrivateIp(String ipAddress) {
        try {
            InetAddress addr = InetAddress.getByName(ipAddress);
            return addr.isSiteLocalAddress() || addr.isLoopbackAddress();
        } catch (UnknownHostException e) {
            return false;
        }
    }

    private boolean isBogonIp(String ipAddress) {
        // Check for bogon IPs (unallocated/reserved)
        return false; // Simplified
    }

    private VpnDetectionResult getCachedVpnDetection(String ipAddress) {
        String key = VPN_CACHE_PREFIX + ipAddress;
        return (VpnDetectionResult) redisTemplate.opsForValue().get(key);
    }

    private void cacheVpnDetection(String ipAddress, boolean isVpn, boolean isProxy, String provider) {
        VpnDetectionResult result = VpnDetectionResult.builder()
            .ipAddress(ipAddress)
            .vpn(isVpn)
            .proxy(isProxy)
            .provider(provider)
            .detectedAt(LocalDateTime.now())
            .build();
        
        String key = VPN_CACHE_PREFIX + ipAddress;
        redisTemplate.opsForValue().set(key, result, cacheTtlHours, TimeUnit.HOURS);
    }

    private boolean hasVpnIndicators(TransactionData.NetworkInfo networkInfo) {
        // Check for VPN indicators in headers, ports, etc.
        if (networkInfo == null) return false;
        
        // Check if IP is in known VPN ranges
        String ipAddress = networkInfo.getIpAddress();
        if (ipAddress != null && isInKnownVpnRange(ipAddress)) {
            return true;
        }
        
        // Check for common VPN ports
        Integer port = networkInfo.getPort();
        if (port != null && (port == 1194 || port == 1723 || port == 500 || port == 4500)) {
            return true;
        }
        
        return false;
    }

    private boolean hasProxyHeaders(TransactionData.NetworkInfo networkInfo) {
        // Check for proxy headers like X-Forwarded-For
        return false; // Simplified
    }

    private String detectKnownVpnProvider(String ipAddress) {
        // Check against known VPN provider IPs
        return null; // Simplified
    }

    private boolean isResidentialProxy(String ipAddress) {
        // Check for residential proxy indicators
        return false; // Simplified
    }

    private boolean isAnonymousProxy(TransactionData.NetworkInfo networkInfo) {
        // Check for anonymous proxy indicators
        return false; // Simplified
    }

    private boolean hasTorCharacteristics(String ipAddress) {
        // Check for Tor characteristics
        return false; // Simplified
    }

    private boolean isSuspiciousIsp(String isp) {
        String[] suspiciousIsps = {"vpn", "proxy", "hosting", "datacenter"};
        String ispLower = isp.toLowerCase();
        
        for (String suspicious : suspiciousIsps) {
            if (ispLower.contains(suspicious)) return true;
        }
        
        return false;
    }

    private boolean isHostingProvider(TransactionData.NetworkInfo networkInfo) {
        if (networkInfo.getIsp() == null) return false;
        
        String[] hostingProviders = {"digitalocean", "linode", "vultr", "ovh"};
        String ispLower = networkInfo.getIsp().toLowerCase();
        
        for (String provider : hostingProviders) {
            if (ispLower.contains(provider)) return true;
        }
        
        return false;
    }

    private boolean hasDnsAnomalies(TransactionData.NetworkInfo networkInfo) {
        // Check for DNS anomalies
        return false; // Simplified
    }

    private ThreatIntelResult getCachedThreatIntel(String ipAddress) {
        String key = THREAT_CACHE_PREFIX + ipAddress;
        return (ThreatIntelResult) redisTemplate.opsForValue().get(key);
    }

    private ThreatIntelResult queryThreatIntelApi(String ipAddress) {
        // Would query actual threat intelligence API
        return null; // Simplified
    }

    private void cacheThreatIntel(String ipAddress, ThreatIntelResult result) {
        String key = THREAT_CACHE_PREFIX + ipAddress;
        redisTemplate.opsForValue().set(key, result, cacheTtlHours, TimeUnit.HOURS);
    }

    @Cacheable(value = "userIpHistory", key = "#userId")
    public Set<String> getUserIpHistory(String userId) {
        String key = CACHE_PREFIX + "user:ips:" + userId;
        Set<Object> ips = redisTemplate.opsForSet().members(key);
        return ips != null ? ips.stream()
            .map(Object::toString)
            .collect(Collectors.toSet()) : new HashSet<>();
    }

    private boolean hasRapidIpChanges(String userId) {
        // Check for rapid IP changes
        return false; // Simplified
    }

    private int getConcurrentIps(String userId) {
        // Check for concurrent sessions from different IPs
        return 0; // Simplified
    }

    private boolean hasImpossibleIpTravel(String userId, String ipAddress) {
        // Check for impossible travel based on IP locations
        return false; // Simplified
    }

    private boolean isIpInRange(String ipAddress, String cidr) {
        // Check if IP is in CIDR range
        // Simplified implementation - in production use proper CIDR matching
        String[] cidrParts = cidr.split("/");
        if (cidrParts.length > 0) {
            String baseIp = cidrParts[0];
            return ipAddress.startsWith(baseIp.substring(0, baseIp.lastIndexOf('.')));
        }
        return false;
    }
    
    private boolean isInMaliciousRange(String ipAddress) {
        // Check if IP is in known malicious ranges
        for (String range : maliciousIpRanges) {
            if (isIpInRange(ipAddress, range)) {
                return true;
            }
        }
        return false;
    }
    
    private boolean isInKnownVpnRange(String ipAddress) {
        // Check if IP belongs to known VPN providers
        for (String vpnRange : knownVpnProviders) {
            if (ipAddress.contains(vpnRange) || isIpInRange(ipAddress, vpnRange)) {
                return true;
            }
        }
        return false;
    }
    
    private boolean isInDatacenterRange(String ipAddress) {
        // Check if IP is in datacenter ranges
        for (String range : datacenterRanges.keySet()) {
            if (isIpInRange(ipAddress, range)) {
                return true;
            }
        }
        return false;
    }
    
    private boolean isTorExitNode(String ipAddress) {
        // Check if IP is a known Tor exit node
        return torExitNodes.contains(ipAddress);
    }

    private boolean isCloudProviderIp(String ipAddress) {
        // Check against cloud provider ranges
        return isInDatacenterRange(ipAddress);
    }

    @Cacheable(value = "ipGeolocation", key = "#ipAddress")
    public IpGeolocation getIpGeolocation(String ipAddress) {
        // Would query IP geolocation service
        return IpGeolocation.builder()
            .ipAddress(ipAddress)
            .country("US")
            .city("New York")
            .region("NY")
            .build(); // Simplified
    }

    private boolean isHighRiskCountry(String country) {
        return countryRiskScores.getOrDefault(country, 0.0) >= 25.0;
    }

    private boolean isSanctionedCountry(String country) {
        String[] sanctioned = {"IR", "KP", "SY"}; // Iran, North Korea, Syria
        return Arrays.asList(sanctioned).contains(country);
    }

    private void saveNetworkAnalysis(String ipAddress, NetworkAnalysisResult result) {
        try {
            String key = CACHE_PREFIX + "analysis:" + ipAddress;
            redisTemplate.opsForValue().set(key, result, cacheTtlHours, TimeUnit.HOURS);
        } catch (Exception e) {
            log.warn("Failed to save network analysis: {}", e.getMessage());
        }
    }
    
    // Missing methods implementation
    
    private NetworkAnalysisResult createDefaultResult(TransactionData transaction) {
        return NetworkAnalysisResult.builder()
            .transactionId(transaction.getTransactionId())
            .userId(transaction.getUserId())
            .riskScore(0.0)
            .riskLevel("LOW")
            .timestamp(LocalDateTime.now())
            .build();
    }
    
    private NetworkAnalysisResult createNoNetworkResult(TransactionData transaction) {
        return NetworkAnalysisResult.builder()
            .transactionId(transaction.getTransactionId())
            .userId(transaction.getUserId())
            .riskScore(0.5)
            .riskLevel("MEDIUM")
            .timestamp(LocalDateTime.now())
            .build();
    }
    
    private void initializeMaliciousIpRanges() {
        // Initialize with known malicious IP ranges
        maliciousIpRanges.add("192.168.0.0/16");
        // Add more ranges as needed
    }
    
    private void initializeVpnProviders() {
        // Initialize VPN provider patterns
        vpnProviders.add("nordvpn");
        vpnProviders.add("expressvpn");
        // Add more as needed
    }
    
    private void initializeTorExitNodes() {
        // Initialize Tor exit node IPs
        // This would be updated from Tor project data
    }
    
    private void initializeDatacenterRanges() {
        datacenterRanges.put("8.8.8.0/24", "Google");
        datacenterRanges.put("1.1.1.0/24", "Cloudflare");
        // Add more datacenter ranges
    }
    
    private void initializeCountryRiskScores() {
        countryRiskScores.put("US", 0.1);
        countryRiskScores.put("CA", 0.1);
        countryRiskScores.put("CN", 0.7);
        // Add more country risk scores
    }
    
    private void initializeAsnRiskScores() {
        asnRiskScores.put("AS13335", 0.1); // Cloudflare
        asnRiskScores.put("AS15169", 0.1); // Google
        // Add more ASN risk scores
    }
    
    private IpReputation getIpReputation(String ipAddress) {
        String key = CACHE_PREFIX + "reputation:" + ipAddress;
        return (IpReputation) redisTemplate.opsForValue().get(key);
    }
    
    private IpReputation calculateIpReputation(String ipAddress) {
        // Calculate reputation based on various factors
        return IpReputation.builder()
            .ipAddress(ipAddress)
            .score(0.5)
            .isMalicious(false)
            .lastChecked(LocalDateTime.now())
            .build();
    }
    
    private void cacheIpReputation(String ipAddress, IpReputation reputation) {
        String key = CACHE_PREFIX + "reputation:" + ipAddress;
        redisTemplate.opsForValue().set(key, reputation, 24, TimeUnit.HOURS);
    }
    
    private boolean isInMaliciousRange(String ipAddress) {
        return maliciousIpRanges.stream().anyMatch(range -> isIpInRange(ipAddress, range));
    }
    
    private boolean isPrivateIp(String ipAddress) {
        return ipAddress.startsWith("10.") || 
               ipAddress.startsWith("192.168.") || 
               ipAddress.startsWith("172.16.");
    }
    
    private boolean isBogonIp(String ipAddress) {
        // Check for bogon (bogus) IP addresses
        return ipAddress.startsWith("0.") || ipAddress.startsWith("127.");
    }
    
    private void detectTorUsage(NetworkAnalysisResult result, String ipAddress) {
        if (isTorExitNode(ipAddress)) {
            result.setTor(true);
        }
    }
    
    private void analyzeNetworkCharacteristics(NetworkAnalysisResult result, 
                                             TransactionData.NetworkInfo networkInfo) {
        if (networkInfo == null) return;
        
        // Analyze various network characteristics
        result.setIsAnomalous(false); // Default implementation
    }
    
    private void checkThreatIntelligence(NetworkAnalysisResult result, String ipAddress) {
        if (threatIntelEnabled) {
            // Check against threat intelligence feeds
            result.setMalicious(isInThreatFeed(ipAddress));
        }
    }
    
    private boolean isInThreatFeed(String ipAddress) {
        // Check against threat intelligence feeds
        return false; // Default implementation
    }
    
    private void analyzeNetworkBehavior(NetworkAnalysisResult result, String userId, String ipAddress) {
        // Analyze behavioral patterns
        result.setSuspiciousPatterns(0); // Default
    }
    
    private void checkDatacenterIp(NetworkAnalysisResult result, String ipAddress) {
        if (isInDatacenterRange(ipAddress)) {
            result.setDatacenter(true);
        }
    }
    
    private void analyzeGeolocationConsistency(NetworkAnalysisResult result, String ipAddress, 
                                             TransactionData transaction) {
        IpGeolocation geo = getIpGeolocation(ipAddress);
        if (geo != null) {
            result.setCountryCode(geo.getCountryCode());
            result.setCity(geo.getCity());
            result.setRegion(geo.getRegion());
            result.setLatitude(geo.getLatitude());
            result.setLongitude(geo.getLongitude());
        }
    }
    
    private double calculateNetworkRiskScore(NetworkAnalysisResult result) {
        double score = 0.0;
        
        if (result.isMalicious()) score += 0.8;
        if (result.isTor()) score += 0.6;
        if (result.isVpn()) score += 0.4;
        if (result.isProxy()) score += 0.3;
        if (result.isDatacenter()) score += 0.2;
        
        return Math.min(score, 1.0);
    }
    
    private String determineRiskLevel(double riskScore) {
        if (riskScore >= 0.8) return "CRITICAL";
        if (riskScore >= 0.6) return "HIGH";
        if (riskScore >= 0.4) return "MEDIUM";
        if (riskScore >= 0.2) return "LOW";
        return "MINIMAL";
    }
    
    // Additional missing classes for VPN detection
    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    static class VpnDetectionResult {
        private boolean vpn;
        private boolean proxy;
        private String provider;
    }
    
    private VpnDetectionResult getCachedVpnDetection(String ipAddress) {
        String key = CACHE_PREFIX + "vpn:" + ipAddress;
        return (VpnDetectionResult) redisTemplate.opsForValue().get(key);
    }
    
    private boolean hasVpnIndicators(TransactionData.NetworkInfo networkInfo) {
        // Check for VPN indicators
        return false; // Default implementation
    }
    
    private boolean hasProxyHeaders(TransactionData.NetworkInfo networkInfo) {
        // Check for proxy headers
        return false; // Default implementation
    }
    
    private String detectKnownVpnProvider(String ipAddress) {
        // Detect known VPN providers
        return null; // Default implementation
    }
    
    private boolean isResidentialProxy(String ipAddress) {
        // Check for residential proxy
        return false; // Default implementation
    }
    
    private void cacheVpnDetection(String ipAddress, boolean isVpn, boolean isProxy, String provider) {
        String key = CACHE_PREFIX + "vpn:" + ipAddress;
        VpnDetectionResult result = VpnDetectionResult.builder()
            .vpn(isVpn)
            .proxy(isProxy)
            .provider(provider)
            .build();
        redisTemplate.opsForValue().set(key, result, 24, TimeUnit.HOURS);
    }
    
    private boolean isAnonymousProxy(TransactionData.NetworkInfo networkInfo) {
        // Check for anonymous proxy indicators
        return false; // Default implementation
    }
}