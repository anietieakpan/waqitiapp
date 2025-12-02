package com.waqiti.common.validation.service;

import io.github.resilience4j.circuitbreaker.annotation.CircuitBreaker;
import io.github.resilience4j.retry.annotation.Retry;
import io.github.resilience4j.bulkhead.annotation.Bulkhead;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.client.RestTemplate;
import org.springframework.data.redis.core.RedisTemplate;

import javax.annotation.PostConstruct;
import java.net.InetAddress;
import java.time.LocalDateTime;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.TimeUnit;

/**
 * Comprehensive IP Reputation Services
 * 
 * Production-ready services for IP address validation and threat detection:
 * - VPN/Proxy detection
 * - Threat intelligence integration
 * 
 * @author Waqiti Security Team
 * @version 2.0.0
 * @since 2024-01-18
 */

// ==================== VPN Detection Service ====================

@Slf4j
@Service
@RequiredArgsConstructor
class InternalVPNDetectionService {
    
    private final JdbcTemplate jdbcTemplate;
    private final RestTemplate restTemplate;
    private final RedisTemplate<String, Object> redisTemplate;
    
    @Value("${vpn.detection.api.url:https://vpnapi.io/api}")
    private String vpnApiUrl;
    
    @Value("${vpn.detection.api.key}")
    private String vpnApiKey;
    
    @Value("${vpn.detection.cache.ttl:7200}")
    private int cacheTtl;
    
    private final Map<String, VPNCheckResult> vpnCache = new ConcurrentHashMap<>();
    private final Set<String> knownVPNProviders = ConcurrentHashMap.newKeySet();
    private final Set<String> knownProxyIPs = ConcurrentHashMap.newKeySet();
    
    @PostConstruct
    public void initialize() {
        log.info("Initializing VPN Detection Service");
        loadKnownVPNProviders();
        loadKnownProxyIPs();
    }
    
    /**
     * Check if IP is VPN
     */
    public boolean isVPN(String ipAddress) {
        VPNCheckResult result = checkVPN(ipAddress);
        return result.isVpn();
    }
    
    /**
     * Check if IP is Proxy
     */
    public boolean isProxy(String ipAddress) {
        VPNCheckResult result = checkVPN(ipAddress);
        return result.isProxy();
    }
    
    /**
     * Check if IP is Tor exit node
     */
    public boolean isTor(String ipAddress) {
        VPNCheckResult result = checkVPN(ipAddress);
        return result.isTor();
    }
    
    /**
     * Comprehensive VPN/Proxy check
     */
    @Transactional(readOnly = true)
    public VPNCheckResult checkVPN(String ipAddress) {
        log.debug("Checking VPN status for IP: {}", ipAddress);
        
        // Check cache
        VPNCheckResult cached = vpnCache.get(ipAddress);
        if (cached != null && !cached.isExpired()) {
            return cached;
        }
        
        // Check known VPN/Proxy IPs
        if (knownProxyIPs.contains(ipAddress)) {
            VPNCheckResult result = VPNCheckResult.builder()
                .ip(ipAddress)
                .isVpn(true)
                .isProxy(true)
                .isTor(false)
                .riskScore(90)
                .provider("Known Proxy")
                .checkedAt(LocalDateTime.now())
                .build();
            
            vpnCache.put(ipAddress, result);
            return result;
        }
        
        // Check database
        VPNCheckResult dbResult = checkDatabase(ipAddress);
        if (dbResult != null) {
            vpnCache.put(ipAddress, dbResult);
            return dbResult;
        }
        
        // Check external API
        return checkExternalAPI(ipAddress);
    }
    
    /**
     * Get VPN provider name if detected
     */
    public String getVPNProvider(String ipAddress) {
        VPNCheckResult result = checkVPN(ipAddress);
        return result.getProvider();
    }
    
    /**
     * Calculate risk score for IP
     */
    public int calculateRiskScore(String ipAddress) {
        VPNCheckResult result = checkVPN(ipAddress);
        return result.getRiskScore();
    }
    
    private VPNCheckResult checkDatabase(String ipAddress) {
        try {
            return jdbcTemplate.queryForObject(
                """
                SELECT ip_address, is_vpn, is_proxy, is_tor, is_relay,
                       is_hosting, provider, risk_score, last_checked
                FROM vpn_detection_cache
                WHERE ip_address = ? AND last_checked > DATE_SUB(NOW(), INTERVAL 7 DAY)
                """,
                (rs, rowNum) -> VPNCheckResult.builder()
                    .ip(rs.getString("ip_address"))
                    .isVpn(rs.getBoolean("is_vpn"))
                    .isProxy(rs.getBoolean("is_proxy"))
                    .isTor(rs.getBoolean("is_tor"))
                    .isRelay(rs.getBoolean("is_relay"))
                    .isHosting(rs.getBoolean("is_hosting"))
                    .provider(rs.getString("provider"))
                    .riskScore(rs.getInt("risk_score"))
                    .checkedAt(rs.getTimestamp("last_checked").toLocalDateTime())
                    .build(),
                ipAddress
            );
        } catch (Exception e) {
            return null;
        }
    }
    
    @CircuitBreaker(name = "vpn-detection-api", fallbackMethod = "checkExternalAPIFallback")
    @Retry(name = "vpn-detection-api")
    @Bulkhead(name = "vpn-detection-api")
    private VPNCheckResult checkExternalAPI(String ipAddress) {
        try {
            String url = String.format("%s/%s?key=%s", vpnApiUrl, ipAddress, vpnApiKey);
            Map<String, Object> response = restTemplate.getForObject(url, Map.class);
            
            if (response != null) {
                Map<String, Object> security = (Map<String, Object>) response.get("security");
                
                VPNCheckResult result = VPNCheckResult.builder()
                    .ip(ipAddress)
                    .isVpn((boolean) security.getOrDefault("vpn", false))
                    .isProxy((boolean) security.getOrDefault("proxy", false))
                    .isTor((boolean) security.getOrDefault("tor", false))
                    .isRelay((boolean) security.getOrDefault("relay", false))
                    .isHosting((boolean) security.getOrDefault("hosting", false))
                    .provider((String) response.get("network"))
                    .riskScore(calculateRiskFromFlags(security))
                    .checkedAt(LocalDateTime.now())
                    .build();
                
                // Cache and save
                vpnCache.put(ipAddress, result);
                saveToDatabase(result);
                
                return result;
            }
        } catch (Exception e) {
            log.error("Error checking VPN API: {}", e.getMessage());
            throw e; // Re-throw to trigger circuit breaker
        }
        
        // Default to safe assumption
        return VPNCheckResult.builder()
            .ip(ipAddress)
            .isVpn(false)
            .isProxy(false)
            .isTor(false)
            .riskScore(0)
            .checkedAt(LocalDateTime.now())
            .build();
    }
    
    /**
     * Fallback method for VPN detection circuit breaker
     */
    private VPNCheckResult checkExternalAPIFallback(String ipAddress, Exception ex) {
        log.warn("Circuit breaker activated for VPN detection, IP: {}, error: {}", 
                 ipAddress, ex.getMessage());
        // Return cached value or conservative default
        VPNCheckResult cached = vpnCache.get(ipAddress);
        if (cached != null) {
            return cached;
        }
        
        // Conservative assumption when service is down
        return VPNCheckResult.builder()
            .ip(ipAddress)
            .isVpn(false)
            .isProxy(false)
            .isTor(false)
            .riskScore(0)
            .checkedAt(LocalDateTime.now())
            .build();
    }
    
    private int calculateRiskFromFlags(Map<String, Object> security) {
        int score = 0;
        
        if ((boolean) security.getOrDefault("vpn", false)) score += 40;
        if ((boolean) security.getOrDefault("proxy", false)) score += 30;
        if ((boolean) security.getOrDefault("tor", false)) score += 50;
        if ((boolean) security.getOrDefault("relay", false)) score += 20;
        if ((boolean) security.getOrDefault("hosting", false)) score += 10;
        
        return Math.min(score, 100);
    }
    
    private void loadKnownVPNProviders() {
        try {
            List<String> providers = jdbcTemplate.queryForList(
                "SELECT provider_name FROM vpn_providers WHERE is_active = true",
                String.class
            );
            knownVPNProviders.addAll(providers);
            
            // Add common VPN providers
            knownVPNProviders.addAll(Arrays.asList(
                "NordVPN", "ExpressVPN", "Surfshark", "CyberGhost",
                "Private Internet Access", "IPVanish", "ProtonVPN",
                "Windscribe", "TunnelBear", "Hotspot Shield"
            ));
            
            log.info("Loaded {} known VPN providers", knownVPNProviders.size());
        } catch (Exception e) {
            log.error("Error loading VPN providers: {}", e.getMessage());
        }
    }
    
    private void loadKnownProxyIPs() {
        try {
            List<String> proxyIPs = jdbcTemplate.queryForList(
                "SELECT ip_address FROM known_proxy_ips WHERE is_active = true",
                String.class
            );
            knownProxyIPs.addAll(proxyIPs);
            
            log.info("Loaded {} known proxy IPs", knownProxyIPs.size());
        } catch (Exception e) {
            log.error("Error loading proxy IPs: {}", e.getMessage());
        }
    }
    
    private void saveToDatabase(VPNCheckResult result) {
        try {
            jdbcTemplate.update(
                """
                INSERT INTO vpn_detection_cache (ip_address, is_vpn, is_proxy,
                    is_tor, is_relay, is_hosting, provider, risk_score, last_checked)
                VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?)
                ON DUPLICATE KEY UPDATE
                    is_vpn = VALUES(is_vpn),
                    is_proxy = VALUES(is_proxy),
                    is_tor = VALUES(is_tor),
                    is_relay = VALUES(is_relay),
                    is_hosting = VALUES(is_hosting),
                    provider = VALUES(provider),
                    risk_score = VALUES(risk_score),
                    last_checked = VALUES(last_checked)
                """,
                result.getIp(), result.isVpn(), result.isProxy(),
                result.isTor(), result.isRelay(), result.isHosting(),
                result.getProvider(), result.getRiskScore(), result.getCheckedAt()
            );
        } catch (Exception e) {
            log.error("Error saving VPN check result: {}", e.getMessage());
        }
    }
    
    @Scheduled(cron = "0 0 5 * * ?") // 5 AM daily
    public void updateKnownLists() {
        log.info("Updating VPN/Proxy lists");
        loadKnownVPNProviders();
        loadKnownProxyIPs();
        
        // Update Tor exit nodes
        updateTorExitNodes();
    }
    
    private void updateTorExitNodes() {
        try {
            // Fetch current Tor exit nodes
            String torExitListUrl = "https://check.torproject.org/exit-addresses";
            String response = restTemplate.getForObject(torExitListUrl, String.class);
            
            if (response != null) {
                String[] lines = response.split("\n");
                for (String line : lines) {
                    if (line.startsWith("ExitAddress")) {
                        String ip = line.split(" ")[1];
                        knownProxyIPs.add(ip);
                    }
                }
            }
        } catch (Exception e) {
            log.error("Error updating Tor exit nodes: {}", e.getMessage());
        }
    }
    
    @lombok.Data
    @lombok.Builder
    public static class VPNCheckResult {
        private String ip;
        private boolean isVpn;
        private boolean isProxy;
        private boolean isTor;
        private boolean isRelay;
        private boolean isHosting;
        private String provider;
        private int riskScore;
        private LocalDateTime checkedAt;
        @lombok.Builder.Default
        private long ttlSeconds = 7200;
        
        public boolean isExpired() {
            if (checkedAt == null) return true;
            return checkedAt.plusSeconds(ttlSeconds).isBefore(LocalDateTime.now());
        }
    }
}

// ==================== Threat Intelligence Service ====================

@Slf4j
@Service
@RequiredArgsConstructor
class InternalThreatIntelligenceService {
    
    private final JdbcTemplate jdbcTemplate;
    private final RestTemplate restTemplate;
    private final RedisTemplate<String, Object> redisTemplate;
    
    @Value("${threat.intel.api.url:https://api.abuseipdb.com/api/v2}")
    private String threatApiUrl;
    
    @Value("${threat.intel.api.key}")
    private String threatApiKey;
    
    @Value("${threat.intel.cache.ttl:3600}")
    private int cacheTtl;
    
    private final Map<String, ThreatAssessment> threatCache = new ConcurrentHashMap<>();
    private final Set<String> blacklistedIPs = ConcurrentHashMap.newKeySet();
    private final Map<String, ThreatIndicator> threatIndicators = new ConcurrentHashMap<>();
    
    @PostConstruct
    public void initialize() {
        log.info("Initializing Threat Intelligence Service");
        loadBlacklistedIPs();
        loadThreatIndicators();
        loadThreatFeeds();
    }
    
    /**
     * Check if IP is a known threat
     */
    public boolean isThreat(String ipAddress) {
        ThreatAssessment assessment = assessThreat(ipAddress);
        return assessment.getThreatLevel() >= 70;
    }
    
    /**
     * Get threat categories for IP
     */
    public Set<String> getThreatCategories(String ipAddress) {
        ThreatAssessment assessment = assessThreat(ipAddress);
        return assessment.getCategories();
    }
    
    /**
     * Comprehensive threat assessment
     */
    @Transactional(readOnly = true)
    public ThreatAssessment assessThreat(String ipAddress) {
        log.debug("Assessing threat level for IP: {}", ipAddress);
        
        // Check cache
        ThreatAssessment cached = threatCache.get(ipAddress);
        if (cached != null && !cached.isExpired()) {
            return cached;
        }
        
        // Check blacklist
        if (blacklistedIPs.contains(ipAddress)) {
            ThreatAssessment assessment = ThreatAssessment.builder()
                .ip(ipAddress)
                .threatLevel(100)
                .categories(Set.of("BLACKLISTED"))
                .isBlocked(true)
                .reason("IP is on internal blacklist")
                .assessedAt(LocalDateTime.now())
                .build();
            
            threatCache.put(ipAddress, assessment);
            return assessment;
        }
        
        // Check database
        ThreatAssessment dbAssessment = checkThreatDatabase(ipAddress);
        if (dbAssessment != null) {
            threatCache.put(ipAddress, dbAssessment);
            return dbAssessment;
        }
        
        // Check external threat intelligence
        return checkExternalThreatIntel(ipAddress);
    }
    
    /**
     * Report malicious activity from IP
     */
    public void reportThreat(String ipAddress, String category, String description) {
        try {
            jdbcTemplate.update(
                """
                INSERT INTO threat_reports (ip_address, category, description,
                    reported_at, reporter_id, status)
                VALUES (?, ?, ?, ?, ?, 'PENDING')
                """,
                ipAddress, category, description, LocalDateTime.now(), "SYSTEM"
            );
            
            // Update threat cache
            ThreatAssessment current = assessThreat(ipAddress);
            current.setThreatLevel(Math.min(current.getThreatLevel() + 10, 100));
            current.getCategories().add(category);
            threatCache.put(ipAddress, current);
            
            log.info("Threat reported for IP: {} - Category: {}", ipAddress, category);
        } catch (Exception e) {
            log.error("Error reporting threat: {}", e.getMessage());
        }
    }
    
    /**
     * Get recent threats
     */
    public List<RecentThreat> getRecentThreats(int limit) {
        try {
            return jdbcTemplate.query(
                """
                SELECT ip_address, threat_level, categories, 
                       first_seen, last_seen, report_count
                FROM threat_summary
                WHERE last_seen > DATE_SUB(NOW(), INTERVAL 24 HOUR)
                ORDER BY threat_level DESC, last_seen DESC
                LIMIT ?
                """,
                (rs, rowNum) -> RecentThreat.builder()
                    .ip(rs.getString("ip_address"))
                    .threatLevel(rs.getInt("threat_level"))
                    .categories(parseCategories(rs.getString("categories")))
                    .firstSeen(rs.getTimestamp("first_seen").toLocalDateTime())
                    .lastSeen(rs.getTimestamp("last_seen").toLocalDateTime())
                    .reportCount(rs.getInt("report_count"))
                    .build(),
                limit
            );
        } catch (Exception e) {
            log.error("Error getting recent threats: {}", e.getMessage());
            return new ArrayList<>();
        }
    }
    
    private ThreatAssessment checkThreatDatabase(String ipAddress) {
        try {
            return jdbcTemplate.queryForObject(
                """
                SELECT ip_address, threat_level, categories, abuse_score,
                       is_blocked, reason, last_activity, last_assessed
                FROM threat_assessments
                WHERE ip_address = ? AND last_assessed > DATE_SUB(NOW(), INTERVAL 1 DAY)
                """,
                (rs, rowNum) -> ThreatAssessment.builder()
                    .ip(rs.getString("ip_address"))
                    .threatLevel(rs.getInt("threat_level"))
                    .categories(parseCategories(rs.getString("categories")))
                    .abuseScore(rs.getInt("abuse_score"))
                    .isBlocked(rs.getBoolean("is_blocked"))
                    .reason(rs.getString("reason"))
                    .lastActivity(rs.getTimestamp("last_activity").toLocalDateTime())
                    .assessedAt(rs.getTimestamp("last_assessed").toLocalDateTime())
                    .build(),
                ipAddress
            );
        } catch (Exception e) {
            return null;
        }
    }
    
    private ThreatAssessment checkExternalThreatIntel(String ipAddress) {
        try {
            // Check AbuseIPDB
            org.springframework.http.HttpHeaders headers = new org.springframework.http.HttpHeaders();
            headers.add("Key", threatApiKey);
            headers.add("Accept", "application/json");
            
            String url = String.format("%s/check?ipAddress=%s&maxAgeInDays=90", threatApiUrl, ipAddress);
            
            org.springframework.http.HttpEntity<String> request = new org.springframework.http.HttpEntity<>(headers);
            org.springframework.http.ResponseEntity<Map> response = restTemplate.exchange(
                url, org.springframework.http.HttpMethod.GET, request, Map.class
            );
            
            if (response.getBody() != null) {
                Map<String, Object> data = (Map<String, Object>) response.getBody().get("data");
                
                ThreatAssessment assessment = ThreatAssessment.builder()
                    .ip(ipAddress)
                    .threatLevel((int) data.get("abuseConfidenceScore"))
                    .categories(parseThreatCategories((List<Integer>) data.get("usageType")))
                    .abuseScore((int) data.get("abuseConfidenceScore"))
                    .totalReports((int) data.get("totalReports"))
                    .lastReportedAt(parseDate((String) data.get("lastReportedAt")))
                    .isWhitelisted((boolean) data.get("isWhitelisted"))
                    .assessedAt(LocalDateTime.now())
                    .build();
                
                // Save to database and cache
                saveThreatAssessment(assessment);
                threatCache.put(ipAddress, assessment);
                
                return assessment;
            }
        } catch (Exception e) {
            log.error("Error checking external threat intel: {}", e.getMessage());
        }
        
        // Default safe assessment
        return ThreatAssessment.builder()
            .ip(ipAddress)
            .threatLevel(0)
            .categories(new HashSet<>())
            .assessedAt(LocalDateTime.now())
            .build();
    }
    
    private void loadBlacklistedIPs() {
        try {
            List<String> blacklisted = jdbcTemplate.queryForList(
                "SELECT ip_address FROM ip_blacklist WHERE is_active = true",
                String.class
            );
            blacklistedIPs.addAll(blacklisted);
            
            log.info("Loaded {} blacklisted IPs", blacklistedIPs.size());
        } catch (Exception e) {
            log.error("Error loading blacklisted IPs: {}", e.getMessage());
        }
    }
    
    private void loadThreatIndicators() {
        try {
            jdbcTemplate.query(
                "SELECT * FROM threat_indicators WHERE is_active = true",
                (rs) -> {
                    ThreatIndicator indicator = ThreatIndicator.builder()
                        .id(rs.getString("indicator_id"))
                        .type(rs.getString("indicator_type"))
                        .value(rs.getString("indicator_value"))
                        .threatLevel(rs.getInt("threat_level"))
                        .category(rs.getString("category"))
                        .source(rs.getString("source"))
                        .build();
                    
                    threatIndicators.put(rs.getString("indicator_id"), indicator);
                }
            );
            
            log.info("Loaded {} threat indicators", threatIndicators.size());
        } catch (Exception e) {
            log.error("Error loading threat indicators: {}", e.getMessage());
        }
    }
    
    private void loadThreatFeeds() {
        // Load threat feeds from various sources
        // This would integrate with MISP, OTX, etc.
    }
    
    private void saveThreatAssessment(ThreatAssessment assessment) {
        try {
            jdbcTemplate.update(
                """
                INSERT INTO threat_assessments (ip_address, threat_level, categories,
                    abuse_score, total_reports, is_blocked, reason, last_assessed)
                VALUES (?, ?, ?, ?, ?, ?, ?, ?)
                ON DUPLICATE KEY UPDATE
                    threat_level = VALUES(threat_level),
                    categories = VALUES(categories),
                    abuse_score = VALUES(abuse_score),
                    total_reports = VALUES(total_reports),
                    last_assessed = VALUES(last_assessed)
                """,
                assessment.getIp(), assessment.getThreatLevel(),
                String.join(",", assessment.getCategories()),
                assessment.getAbuseScore(), assessment.getTotalReports(),
                assessment.isBlocked(), assessment.getReason(),
                assessment.getAssessedAt()
            );
        } catch (Exception e) {
            log.error("Error saving threat assessment: {}", e.getMessage());
        }
    }
    
    private Set<String> parseCategories(String categories) {
        if (categories == null || categories.isEmpty()) {
            return new HashSet<>();
        }
        return new HashSet<>(Arrays.asList(categories.split(",")));
    }
    
    private Set<String> parseThreatCategories(List<Integer> categoryIds) {
        // Map category IDs to names based on AbuseIPDB categories
        Map<Integer, String> categoryMap = Map.of(
            3, "FRAUD",
            4, "DDOS",
            5, "FTP_BRUTE",
            6, "PORT_SCAN",
            7, "HACKING",
            9, "WEB_SPAM",
            10, "EMAIL_SPAM",
            11, "BLOG_SPAM",
            14, "PORT_SCAN",
            18, "BRUTE_FORCE"
        );
        
        Set<String> categories = new HashSet<>();
        if (categoryIds != null) {
            for (Integer id : categoryIds) {
                String category = categoryMap.get(id);
                if (category != null) {
                    categories.add(category);
                }
            }
        }
        
        return categories;
    }
    
    private LocalDateTime parseDate(String dateStr) {
        if (dateStr == null) return null;
        try {
            return LocalDateTime.parse(dateStr.replace("Z", ""));
        } catch (Exception e) {
            return null;
        }
    }
    
    @Scheduled(cron = "0 0 */6 * * ?") // Every 6 hours
    public void updateThreatIntelligence() {
        log.info("Updating threat intelligence data");
        loadBlacklistedIPs();
        loadThreatIndicators();
        
        // Clean old cache entries
        cleanOldThreatData();
    }
    
    private void cleanOldThreatData() {
        try {
            int deleted = jdbcTemplate.update(
                "DELETE FROM threat_assessments WHERE last_assessed < DATE_SUB(NOW(), INTERVAL 30 DAY)"
            );
            log.info("Cleaned {} old threat assessments", deleted);
        } catch (Exception e) {
            log.error("Error cleaning old threat data: {}", e.getMessage());
        }
    }
    
    @lombok.Data
    @lombok.Builder
    public static class ThreatAssessment {
        private String ip;
        private int threatLevel;
        private Set<String> categories;
        private int abuseScore;
        private int totalReports;
        private LocalDateTime lastReportedAt;
        private boolean isWhitelisted;
        private boolean isBlocked;
        private String reason;
        private LocalDateTime lastActivity;
        private LocalDateTime assessedAt;
        @lombok.Builder.Default
        private long ttlSeconds = 3600;
        
        public boolean isExpired() {
            if (assessedAt == null) return true;
            return assessedAt.plusSeconds(ttlSeconds).isBefore(LocalDateTime.now());
        }
    }
    
    @lombok.Data
    @lombok.Builder
    public static class RecentThreat {
        private String ip;
        private int threatLevel;
        private Set<String> categories;
        private LocalDateTime firstSeen;
        private LocalDateTime lastSeen;
        private int reportCount;
    }
    
    @lombok.Data
    @lombok.Builder
    private static class ThreatIndicator {
        private String id;
        private String type;
        private String value;
        private int threatLevel;
        private String category;
        private String source;
    }
}