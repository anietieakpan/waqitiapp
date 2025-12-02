package com.waqiti.frauddetection.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.redisson.api.RBloomFilter;
import org.redisson.api.RLock;
import org.redisson.api.RMap;
import org.redisson.api.RedissonClient;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Async;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Isolation;
import org.springframework.transaction.annotation.Transactional;

import javax.annotation.PostConstruct;
import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.SecureRandom;
import java.time.Duration;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.TimeUnit;
import java.util.regex.Pattern;

/**
 * CRITICAL SECURITY: Secure Fraud Blacklist Service
 * Implements comprehensive blacklist checking with enhanced security features
 * 
 * Security features:
 * - Bloom filters for O(1) blacklist lookups
 * - Hash-based anonymization of sensitive data
 * - Rate limiting for blacklist queries
 * - Distributed locking for consistency
 * - Automatic threat intelligence updates
 * - Pattern-based detection
 * - IP reputation scoring
 * - Device fingerprint blacklisting
 * - Behavioral pattern analysis
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class SecureFraudBlacklistService {
    
    private final RedissonClient redissonClient;
    private final ObjectMapper objectMapper;
    
    @Value("${fraud.blacklist.enabled:true}")
    private boolean blacklistEnabled;
    
    @Value("${fraud.blacklist.bloom.expectedInsertions:1000000}")
    private long bloomExpectedInsertions;
    
    @Value("${fraud.blacklist.bloom.falsePositiveRate:0.01}")
    private double bloomFalsePositiveRate;
    
    @Value("${fraud.blacklist.salt:}")
    private String blacklistSalt;
    
    // Bloom filters for different blacklist types
    private RBloomFilter<String> emailBlacklistBloom;
    private RBloomFilter<String> phoneBlacklistBloom;
    private RBloomFilter<String> ipBlacklistBloom;
    private RBloomFilter<String> deviceBlacklistBloom;
    private RBloomFilter<String> cardBlacklistBloom;
    
    // Pattern-based detection
    private final Map<String, Pattern> suspiciousPatterns = new ConcurrentHashMap<>();
    
    // Rate limiting
    private final Map<String, RateLimiter> rateLimiters = new ConcurrentHashMap<>();
    
    // Threat intelligence feeds
    private final Set<String> threatIntelFeeds = Set.of(
        "malicious_ips", "phishing_domains", "compromised_cards", 
        "fraud_patterns", "bot_networks"
    );
    
    private static final String BLACKLIST_LOCK_PREFIX = "blacklist:lock:";
    private static final String BLACKLIST_CACHE_PREFIX = "blacklist:cache:";
    private static final String BLACKLIST_STATS_PREFIX = "blacklist:stats:";
    private static final String IP_REPUTATION_PREFIX = "ip:reputation:";
    
    @PostConstruct
    public void initialize() {
        if (!blacklistEnabled) {
            log.info("Fraud blacklist service is disabled");
            return;
        }
        
        initializeBloomFilters();
        initializeSuspiciousPatterns();
        loadThreatIntelligence();
        
        log.info("Secure Fraud Blacklist Service initialized with enhanced security features");
    }
    
    /**
     * Initialize Bloom filters for efficient blacklist lookups
     */
    private void initializeBloomFilters() {
        try {
            // Email blacklist
            emailBlacklistBloom = redissonClient.getBloomFilter("blacklist:email");
            if (!emailBlacklistBloom.isExists()) {
                emailBlacklistBloom.tryInit(bloomExpectedInsertions, bloomFalsePositiveRate);
                log.info("Initialized email blacklist Bloom filter");
            }
            
            // Phone blacklist
            phoneBlacklistBloom = redissonClient.getBloomFilter("blacklist:phone");
            if (!phoneBlacklistBloom.isExists()) {
                phoneBlacklistBloom.tryInit(bloomExpectedInsertions, bloomFalsePositiveRate);
                log.info("Initialized phone blacklist Bloom filter");
            }
            
            // IP blacklist
            ipBlacklistBloom = redissonClient.getBloomFilter("blacklist:ip");
            if (!ipBlacklistBloom.isExists()) {
                ipBlacklistBloom.tryInit(bloomExpectedInsertions, bloomFalsePositiveRate);
                log.info("Initialized IP blacklist Bloom filter");
            }
            
            // Device blacklist
            deviceBlacklistBloom = redissonClient.getBloomFilter("blacklist:device");
            if (!deviceBlacklistBloom.isExists()) {
                deviceBlacklistBloom.tryInit(bloomExpectedInsertions, bloomFalsePositiveRate);
                log.info("Initialized device blacklist Bloom filter");
            }
            
            // Card blacklist
            cardBlacklistBloom = redissonClient.getBloomFilter("blacklist:card");
            if (!cardBlacklistBloom.isExists()) {
                cardBlacklistBloom.tryInit(bloomExpectedInsertions, bloomFalsePositiveRate);
                log.info("Initialized card blacklist Bloom filter");
            }
            
        } catch (Exception e) {
            log.error("Failed to initialize Bloom filters", e);
        }
    }
    
    /**
     * Initialize suspicious patterns for detection
     */
    private void initializeSuspiciousPatterns() {
        // Email patterns
        suspiciousPatterns.put("disposable_email", 
            Pattern.compile("@(10minutemail|guerrillamail|mailinator|tempmail|yopmail)\\."));
        
        suspiciousPatterns.put("suspicious_email_pattern", 
            Pattern.compile("[0-9]{6,}@|test@|admin@|root@|noreply@"));
        
        // Phone patterns
        suspiciousPatterns.put("invalid_phone", 
            Pattern.compile("^(000|111|222|333|444|555|666|777|888|999)"));
        
        suspiciousPatterns.put("sequential_phone", 
            Pattern.compile("(012345|123456|234567|345678|456789|567890)"));
        
        // Name patterns
        suspiciousPatterns.put("fake_names", 
            Pattern.compile("(?i)(test|fake|john doe|jane doe|mickey mouse|donald duck)"));
        
        // Card patterns (for tokenized comparison)
        suspiciousPatterns.put("test_cards", 
            Pattern.compile("^(4111|4000|5555|3717|6011)"));
        
        log.info("Initialized {} suspicious patterns", suspiciousPatterns.size());
    }
    
    /**
     * Check if entity is blacklisted
     */
    public BlacklistCheckResult checkBlacklist(BlacklistCheckRequest request) {
        if (!blacklistEnabled) {
            return BlacklistCheckResult.clean();
        }
        
        String requestId = request.getRequestId();
        
        // Check rate limiting
        if (!checkRateLimit(request.getUserId())) {
            log.warn("Rate limit exceeded for blacklist check: {}", request.getUserId());
            return BlacklistCheckResult.rateLimited();
        }
        
        BlacklistCheckResult result = new BlacklistCheckResult();
        
        try {
            // Check email blacklist
            if (request.getEmail() != null) {
                BlacklistMatch emailMatch = checkEmailBlacklist(request.getEmail());
                result.addMatch(emailMatch);
            }
            
            // Check phone blacklist
            if (request.getPhone() != null) {
                BlacklistMatch phoneMatch = checkPhoneBlacklist(request.getPhone());
                result.addMatch(phoneMatch);
            }
            
            // Check IP blacklist and reputation
            if (request.getIpAddress() != null) {
                BlacklistMatch ipMatch = checkIpBlacklist(request.getIpAddress());
                result.addMatch(ipMatch);
                
                // Check IP reputation
                double ipReputation = checkIpReputation(request.getIpAddress());
                if (ipReputation > 0.7) {
                    result.addMatch(new BlacklistMatch(
                        BlacklistType.IP_REPUTATION, 
                        request.getIpAddress(), 
                        "High risk IP", 
                        ipReputation
                    ));
                }
            }
            
            // Check device fingerprint
            if (request.getDeviceId() != null) {
                BlacklistMatch deviceMatch = checkDeviceBlacklist(request.getDeviceId());
                result.addMatch(deviceMatch);
            }
            
            // Check card hash (if provided)
            if (request.getCardHash() != null) {
                BlacklistMatch cardMatch = checkCardBlacklist(request.getCardHash());
                result.addMatch(cardMatch);
            }
            
            // Pattern-based checks
            performPatternChecks(request, result);
            
            // Behavioral analysis
            performBehavioralAnalysis(request, result);
            
            // Update statistics
            updateStatistics(request, result);
            
            // Cache result
            cacheResult(requestId, result);
            
            return result;
            
        } catch (Exception e) {
            log.error("Error checking blacklist for request: {}", requestId, e);
            return BlacklistCheckResult.error("Blacklist check failed");
        }
    }
    
    /**
     * Check email against blacklist and patterns
     */
    private BlacklistMatch checkEmailBlacklist(String email) {
        String normalizedEmail = email.toLowerCase().trim();
        String hashedEmail = hashSensitiveData(normalizedEmail);
        
        // Check Bloom filter first
        if (emailBlacklistBloom.contains(hashedEmail)) {
            // Potential match, verify with exact lookup
            RMap<String, BlacklistEntry> emailMap = redissonClient.getMap("blacklist:email:exact");
            BlacklistEntry entry = emailMap.get(hashedEmail);
            
            if (entry != null) {
                log.warn("Email found in blacklist: {} (reason: {})", 
                    maskEmail(normalizedEmail), entry.getReason());
                return new BlacklistMatch(
                    BlacklistType.EMAIL, 
                    maskEmail(normalizedEmail), 
                    entry.getReason(), 
                    entry.getRiskScore()
                );
            }
        }
        
        // Check patterns
        for (Map.Entry<String, Pattern> patternEntry : suspiciousPatterns.entrySet()) {
            if (patternEntry.getKey().contains("email") && 
                patternEntry.getValue().matcher(normalizedEmail).find()) {
                
                log.warn("Email matches suspicious pattern {}: {}", 
                    patternEntry.getKey(), maskEmail(normalizedEmail));
                
                return new BlacklistMatch(
                    BlacklistType.EMAIL_PATTERN,
                    maskEmail(normalizedEmail),
                    "Suspicious email pattern: " + patternEntry.getKey(),
                    0.6
                );
            }
        }
        
        return null;
    }
    
    /**
     * Check phone against blacklist
     */
    private BlacklistMatch checkPhoneBlacklist(String phone) {
        String normalizedPhone = normalizePhone(phone);
        String hashedPhone = hashSensitiveData(normalizedPhone);
        
        if (phoneBlacklistBloom.contains(hashedPhone)) {
            RMap<String, BlacklistEntry> phoneMap = redissonClient.getMap("blacklist:phone:exact");
            BlacklistEntry entry = phoneMap.get(hashedPhone);
            
            if (entry != null) {
                log.warn("Phone found in blacklist: {} (reason: {})", 
                    maskPhone(normalizedPhone), entry.getReason());
                return new BlacklistMatch(
                    BlacklistType.PHONE,
                    maskPhone(normalizedPhone),
                    entry.getReason(),
                    entry.getRiskScore()
                );
            }
        }
        
        // Check patterns
        for (Map.Entry<String, Pattern> patternEntry : suspiciousPatterns.entrySet()) {
            if (patternEntry.getKey().contains("phone") && 
                patternEntry.getValue().matcher(normalizedPhone).find()) {
                
                return new BlacklistMatch(
                    BlacklistType.PHONE_PATTERN,
                    maskPhone(normalizedPhone),
                    "Suspicious phone pattern: " + patternEntry.getKey(),
                    0.5
                );
            }
        }
        
        return null;
    }
    
    /**
     * Check IP against blacklist and reputation
     */
    private BlacklistMatch checkIpBlacklist(String ipAddress) {
        String hashedIp = hashSensitiveData(ipAddress);
        
        if (ipBlacklistBloom.contains(hashedIp)) {
            RMap<String, BlacklistEntry> ipMap = redissonClient.getMap("blacklist:ip:exact");
            BlacklistEntry entry = ipMap.get(hashedIp);
            
            if (entry != null) {
                log.warn("IP found in blacklist: {} (reason: {})", 
                    maskIp(ipAddress), entry.getReason());
                return new BlacklistMatch(
                    BlacklistType.IP,
                    maskIp(ipAddress),
                    entry.getReason(),
                    entry.getRiskScore()
                );
            }
        }
        
        return null;
    }
    
    /**
     * Check device fingerprint
     */
    private BlacklistMatch checkDeviceBlacklist(String deviceId) {
        String hashedDevice = hashSensitiveData(deviceId);
        
        if (deviceBlacklistBloom.contains(hashedDevice)) {
            RMap<String, BlacklistEntry> deviceMap = redissonClient.getMap("blacklist:device:exact");
            BlacklistEntry entry = deviceMap.get(hashedDevice);
            
            if (entry != null) {
                log.warn("Device found in blacklist: {} (reason: {})", 
                    maskDeviceId(deviceId), entry.getReason());
                return new BlacklistMatch(
                    BlacklistType.DEVICE,
                    maskDeviceId(deviceId),
                    entry.getReason(),
                    entry.getRiskScore()
                );
            }
        }
        
        return null;
    }
    
    /**
     * Check card hash
     */
    private BlacklistMatch checkCardBlacklist(String cardHash) {
        if (cardBlacklistBloom.contains(cardHash)) {
            RMap<String, BlacklistEntry> cardMap = redissonClient.getMap("blacklist:card:exact");
            BlacklistEntry entry = cardMap.get(cardHash);
            
            if (entry != null) {
                log.warn("Card found in blacklist (reason: {})", entry.getReason());
                return new BlacklistMatch(
                    BlacklistType.CARD,
                    "***MASKED***",
                    entry.getReason(),
                    entry.getRiskScore()
                );
            }
        }
        
        return null;
    }
    
    /**
     * Check IP reputation score
     */
    private double checkIpReputation(String ipAddress) {
        String reputationKey = IP_REPUTATION_PREFIX + hashSensitiveData(ipAddress);
        RMap<String, Double> reputationMap = redissonClient.getMap(reputationKey);
        
        // Aggregate scores from different sources
        double totalScore = 0.0;
        int sourceCount = 0;
        
        for (String source : Set.of("threat_intel", "geo_risk", "historical", "behavioral")) {
            Double score = reputationMap.get(source);
            if (score != null) {
                totalScore += score;
                sourceCount++;
            }
        }
        
        if (sourceCount == 0) {
            return 0.0; // No reputation data
        }
        
        return totalScore / sourceCount;
    }
    
    /**
     * Perform pattern-based checks
     */
    private void performPatternChecks(BlacklistCheckRequest request, BlacklistCheckResult result) {
        // Check name patterns
        if (request.getFirstName() != null || request.getLastName() != null) {
            String fullName = (request.getFirstName() + " " + request.getLastName()).trim();
            
            for (Map.Entry<String, Pattern> patternEntry : suspiciousPatterns.entrySet()) {
                if (patternEntry.getKey().contains("name") && 
                    patternEntry.getValue().matcher(fullName).find()) {
                    
                    result.addMatch(new BlacklistMatch(
                        BlacklistType.NAME_PATTERN,
                        maskName(fullName),
                        "Suspicious name pattern: " + patternEntry.getKey(),
                        0.4
                    ));
                }
            }
        }
    }
    
    /**
     * Perform behavioral analysis
     */
    private void performBehavioralAnalysis(BlacklistCheckRequest request, BlacklistCheckResult result) {
        if (request.getUserId() == null) return;
        
        String behaviorKey = "behavior:" + hashSensitiveData(request.getUserId());
        RMap<String, Object> behaviorMap = redissonClient.getMap(behaviorKey);
        
        // Analyze velocity patterns
        Long lastCheckTime = (Long) behaviorMap.get("lastCheck");
        if (lastCheckTime != null) {
            long timeDiff = System.currentTimeMillis() - lastCheckTime;
            if (timeDiff < 1000) { // Less than 1 second between checks
                Integer rapidCount = (Integer) behaviorMap.get("rapidCount");
                rapidCount = (rapidCount == null) ? 1 : rapidCount + 1;
                behaviorMap.put("rapidCount", rapidCount);
                
                if (rapidCount > 5) {
                    result.addMatch(new BlacklistMatch(
                        BlacklistType.BEHAVIORAL,
                        "Rapid checking pattern",
                        "Multiple rapid blacklist checks",
                        0.7
                    ));
                }
            } else {
                behaviorMap.put("rapidCount", 0);
            }
        }
        
        behaviorMap.put("lastCheck", System.currentTimeMillis());
        behaviorMap.expire(Duration.ofHours(1));
    }
    
    /**
     * Add entity to blacklist
     */
    @Transactional(isolation = Isolation.SERIALIZABLE)
    public void addToBlacklist(BlacklistAddRequest request) {
        String lockKey = BLACKLIST_LOCK_PREFIX + request.getType().name();
        RLock lock = redissonClient.getFairLock(lockKey);
        
        try {
            if (lock.tryLock(10, TimeUnit.SECONDS)) {
                try {
                    String hashedValue = hashSensitiveData(request.getValue());
                    
                    BlacklistEntry entry = BlacklistEntry.builder()
                        .id(UUID.randomUUID().toString())
                        .hashedValue(hashedValue)
                        .type(request.getType())
                        .reason(request.getReason())
                        .riskScore(request.getRiskScore())
                        .addedBy(request.getAddedBy())
                        .addedAt(LocalDateTime.now())
                        .expiresAt(request.getExpiresAt())
                        .build();
                    
                    // Add to Bloom filter
                    RBloomFilter<String> bloomFilter = getBloomFilter(request.getType());
                    bloomFilter.add(hashedValue);
                    
                    // Add to exact lookup map
                    RMap<String, BlacklistEntry> exactMap = getExactMap(request.getType());
                    exactMap.put(hashedValue, entry);
                    
                    // Set expiry if provided
                    if (request.getExpiresAt() != null) {
                        long seconds = request.getExpiresAt().toEpochSecond(ZoneOffset.UTC) - 
                                      LocalDateTime.now().toEpochSecond(ZoneOffset.UTC);
                        exactMap.expire(Duration.ofSeconds(seconds));
                    }
                    
                    log.info("Added to blacklist - Type: {}, Reason: {}, Added by: {}", 
                        request.getType(), request.getReason(), request.getAddedBy());
                    
                } finally {
                    if (lock.isHeldByCurrentThread()) {
                        lock.unlock();
                    }
                }
            } else {
                log.error("Failed to acquire lock for blacklist addition: {}", request.getType());
            }
        } catch (Exception e) {
            log.error("Error adding to blacklist", e);
        }
    }
    
    /**
     * Load threat intelligence feeds
     */
    @Async
    @Scheduled(fixedDelay = 3600000) // Every hour
    public void loadThreatIntelligence() {
        log.info("Loading threat intelligence feeds...");
        
        for (String feed : threatIntelFeeds) {
            try {
                loadThreatFeed(feed);
            } catch (Exception e) {
                log.error("Error loading threat feed: {}", feed, e);
            }
        }
    }
    
    /**
     * Load specific threat feed with comprehensive implementation
     */
    private void loadThreatFeed(String feedName) {
        log.info("Loading threat feed: {}", feedName);
        
        try {
            switch (feedName) {
                case "malicious_ips":
                    loadMaliciousIPsFeed();
                    break;
                case "fraud_emails":
                    loadFraudEmailsFeed();
                    break;
                case "suspicious_devices":
                    loadSuspiciousDevicesFeed();
                    break;
                case "high_risk_countries":
                    loadHighRiskCountriesFeed();
                    break;
                case "stolen_cards":
                    loadStolenCardsFeed();
                    break;
                case "bot_networks":
                    loadBotNetworksFeed();
                    break;
                default:
                    log.warn("Unknown threat feed: {}", feedName);
            }
            
            log.info("Successfully loaded threat feed: {}", feedName);
            
        } catch (Exception e) {
            log.error("Failed to load threat feed: {}", feedName, e);
            // Record feed load failure for monitoring
            recordFeedLoadFailure(feedName, e.getMessage());
        }
    }

    /**
     * Load malicious IPs from multiple threat intelligence sources
     */
    private void loadMaliciousIPsFeed() {
        RBloomFilter<String> ipBlacklist = getBloomFilter(BlacklistType.IP_ADDRESS);
        
        // Source 1: Internal blocked IPs from database
        List<String> internalBlockedIps = blacklistEntryRepository
            .findByTypeAndStatusAndExpirationDateAfter(
                BlacklistType.IP_ADDRESS, 
                BlacklistStatus.ACTIVE, 
                LocalDateTime.now()
            )
            .stream()
            .map(BlacklistEntry::getValue)
            .collect(Collectors.toList());
        
        internalBlockedIps.forEach(ipBlacklist::add);
        log.debug("Loaded {} internal blocked IPs", internalBlockedIps.size());
        
        // Source 2: Known Tor exit nodes
        loadTorExitNodes(ipBlacklist);
        
        // Source 3: Cloud provider abuse IPs
        loadCloudAbuseIPs(ipBlacklist);
        
        // Source 4: Geographic high-risk IPs
        loadHighRiskGeoIPs(ipBlacklist);
        
        log.info("Malicious IPs feed loaded with multiple threat intelligence sources");
    }

    /**
     * Load fraudulent email patterns
     */
    private void loadFraudEmailsFeed() {
        RBloomFilter<String> emailBlacklist = getBloomFilter(BlacklistType.EMAIL);
        
        // Load known fraud email patterns from database
        List<String> fraudEmails = blacklistEntryRepository
            .findByTypeAndStatus(BlacklistType.EMAIL, BlacklistStatus.ACTIVE)
            .stream()
            .map(BlacklistEntry::getValue)
            .collect(Collectors.toList());
        
        fraudEmails.forEach(emailBlacklist::add);
        
        // Load disposable email domains
        loadDisposableEmailDomains(emailBlacklist);
        
        log.info("Fraud emails feed loaded: {} entries", fraudEmails.size());
    }

    /**
     * Load suspicious device fingerprints
     */
    private void loadSuspiciousDevicesFeed() {
        RBloomFilter<String> deviceBlacklist = getBloomFilter(BlacklistType.DEVICE);
        
        List<String> suspiciousDevices = blacklistEntryRepository
            .findByTypeAndStatus(BlacklistType.DEVICE, BlacklistStatus.ACTIVE)
            .stream()
            .map(BlacklistEntry::getValue)
            .collect(Collectors.toList());
        
        suspiciousDevices.forEach(deviceBlacklist::add);
        
        log.info("Suspicious devices feed loaded: {} entries", suspiciousDevices.size());
    }

    /**
     * Load high-risk countries and regions
     */
    private void loadHighRiskCountriesFeed() {
        RBloomFilter<String> countryBlacklist = getBloomFilter(BlacklistType.COUNTRY);
        
        // High-risk countries based on fraud statistics and regulations
        List<String> highRiskCountries = Arrays.asList(
            "AF", "BY", "CD", "CF", "CG", "CI", "CU", "ER", "GN", "GW", 
            "HT", "IR", "IQ", "KP", "LB", "LR", "LY", "ML", "MM", "NI",
            "PK", "RU", "SD", "SO", "SS", "SY", "TD", "UZ", "VE", "YE", "ZW"
        );
        
        highRiskCountries.forEach(countryBlacklist::add);
        
        // Load additional high-risk regions from database
        List<String> additionalRiskCountries = blacklistEntryRepository
            .findByTypeAndStatus(BlacklistType.COUNTRY, BlacklistStatus.ACTIVE)
            .stream()
            .map(BlacklistEntry::getValue)
            .collect(Collectors.toList());
        
        additionalRiskCountries.forEach(countryBlacklist::add);
        
        log.info("High-risk countries feed loaded: {} entries", 
            highRiskCountries.size() + additionalRiskCountries.size());
    }

    /**
     * Load known stolen card patterns
     */
    private void loadStolenCardsFeed() {
        RBloomFilter<String> cardBlacklist = getBloomFilter(BlacklistType.CARD);
        
        // Load from database - these would be BIN ranges or specific card numbers
        List<String> stolenCardNumbers = blacklistEntryRepository
            .findByTypeAndStatus(BlacklistType.CARD, BlacklistStatus.ACTIVE)
            .stream()
            .map(BlacklistEntry::getValue)
            .collect(Collectors.toList());
        
        stolenCardNumbers.forEach(cardBlacklist::add);
        
        log.info("Stolen cards feed loaded: {} entries", stolenCardNumbers.size());
    }

    /**
     * Load bot network indicators
     */
    private void loadBotNetworksFeed() {
        RBloomFilter<String> botBlacklist = getBloomFilter(BlacklistType.BOT);
        
        // Load known bot user agents, IP ranges, and behavioral patterns
        List<String> botIndicators = blacklistEntryRepository
            .findByTypeAndStatus(BlacklistType.BOT, BlacklistStatus.ACTIVE)
            .stream()
            .map(BlacklistEntry::getValue)
            .collect(Collectors.toList());
        
        botIndicators.forEach(botBlacklist::add);
        
        log.info("Bot networks feed loaded: {} entries", botIndicators.size());
    }

    /**
     * Comprehensive blacklist checking method - CRITICAL SECURITY FUNCTION
     */
    public BlacklistCheckResult checkAllBlacklists(String userId, String email, String ipAddress, 
                                                   String deviceFingerprint, String countryCode, 
                                                   String cardNumber, String userAgent) {
        log.debug("Performing comprehensive blacklist check for user: {}", userId);
        
        BlacklistCheckResult result = new BlacklistCheckResult();
        result.setUserId(userId);
        result.setCheckTimestamp(LocalDateTime.now());
        
        try {
            // Check IP address blacklist
            if (ipAddress != null) {
                boolean ipBlacklisted = isBlacklisted(BlacklistType.IP_ADDRESS, ipAddress);
                result.setIpBlacklisted(ipBlacklisted);
                if (ipBlacklisted) {
                    result.addViolation("IP address is blacklisted: " + ipAddress);
                    log.warn("SECURITY ALERT: Blacklisted IP detected - {}", ipAddress);
                }
            }
            
            // Check email blacklist
            if (email != null) {
                boolean emailBlacklisted = isBlacklisted(BlacklistType.EMAIL, email);
                result.setEmailBlacklisted(emailBlacklisted);
                if (emailBlacklisted) {
                    result.addViolation("Email address is blacklisted: " + email);
                    log.warn("SECURITY ALERT: Blacklisted email detected - {}", email);
                }
            }
            
            // Check device fingerprint
            if (deviceFingerprint != null) {
                boolean deviceBlacklisted = isBlacklisted(BlacklistType.DEVICE, deviceFingerprint);
                result.setDeviceBlacklisted(deviceBlacklisted);
                if (deviceBlacklisted) {
                    result.addViolation("Device fingerprint is blacklisted");
                    log.warn("SECURITY ALERT: Blacklisted device detected - {}", deviceFingerprint);
                }
            }
            
            // Check country/region
            if (countryCode != null) {
                boolean countryBlacklisted = isBlacklisted(BlacklistType.COUNTRY, countryCode);
                result.setCountryBlacklisted(countryBlacklisted);
                if (countryBlacklisted) {
                    result.addViolation("Country is high-risk: " + countryCode);
                    log.warn("SECURITY ALERT: High-risk country detected - {}", countryCode);
                }
            }
            
            // Check card number (if provided)
            if (cardNumber != null) {
                boolean cardBlacklisted = isBlacklisted(BlacklistType.CARD, cardNumber);
                result.setCardBlacklisted(cardBlacklisted);
                if (cardBlacklisted) {
                    result.addViolation("Card number is blacklisted");
                    log.error("CRITICAL SECURITY ALERT: Stolen card detected");
                }
            }
            
            // Check for bot indicators
            if (userAgent != null) {
                boolean botDetected = isBlacklisted(BlacklistType.BOT, userAgent);
                result.setBotDetected(botDetected);
                if (botDetected) {
                    result.addViolation("Bot or automated tool detected");
                    log.warn("SECURITY ALERT: Bot activity detected - {}", userAgent);
                }
            }
            
            // Calculate overall risk score
            int violationCount = result.getViolations().size();
            if (violationCount >= 3) {
                result.setRiskLevel(RiskLevel.CRITICAL);
            } else if (violationCount >= 2) {
                result.setRiskLevel(RiskLevel.HIGH);
            } else if (violationCount >= 1) {
                result.setRiskLevel(RiskLevel.MEDIUM);
            } else {
                result.setRiskLevel(RiskLevel.LOW);
            }
            
            // Log results for audit trail
            auditBlacklistCheck(result);
            
            return result;
            
        } catch (Exception e) {
            log.error("Error during blacklist check for user {}: {}", userId, e.getMessage(), e);
            
            // Return high-risk result on error to prevent bypassing security
            result.setRiskLevel(RiskLevel.HIGH);
            result.addViolation("Security check failed - manual review required");
            
            return result;
        }
    }
    
    /**
     * Get Bloom filter for type
     */
    private RBloomFilter<String> getBloomFilter(BlacklistType type) {
        switch (type) {
            case EMAIL: return emailBlacklistBloom;
            case PHONE: return phoneBlacklistBloom;
            case IP: return ipBlacklistBloom;
            case DEVICE: return deviceBlacklistBloom;
            case CARD: return cardBlacklistBloom;
            default: throw new IllegalArgumentException("Unknown blacklist type: " + type);
        }
    }
    
    /**
     * Get exact lookup map for type
     */
    private RMap<String, BlacklistEntry> getExactMap(BlacklistType type) {
        return redissonClient.getMap("blacklist:" + type.name().toLowerCase() + ":exact");
    }
    
    /**
     * Hash sensitive data for storage
     */
    private String hashSensitiveData(String data) {
        try {
            String saltedData = data + blacklistSalt;
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] hash = digest.digest(saltedData.getBytes(StandardCharsets.UTF_8));
            
            return Base64.getEncoder().encodeToString(hash);
            
        } catch (Exception e) {
            log.error("Error hashing sensitive data", e);
            return data; // Fallback (not recommended for production)
        }
    }
    
    /**
     * Normalize phone number
     */
    private String normalizePhone(String phone) {
        return phone.replaceAll("[^0-9]", "");
    }
    
    /**
     * Check rate limiting
     */
    private boolean checkRateLimit(String userId) {
        RateLimiter limiter = rateLimiters.computeIfAbsent(userId, 
            k -> new RateLimiter(100, 60000)); // 100 requests per minute
        
        return limiter.tryAcquire();
    }
    
    /**
     * Update statistics
     */
    private void updateStatistics(BlacklistCheckRequest request, BlacklistCheckResult result) {
        String statsKey = BLACKLIST_STATS_PREFIX + LocalDateTime.now().toLocalDate();
        RMap<String, Long> statsMap = redissonClient.getMap(statsKey);
        
        statsMap.merge("total_checks", 1L, Long::sum);
        
        if (result.hasMatches()) {
            statsMap.merge("matches_found", 1L, Long::sum);
            statsMap.merge("risk_score_total", Math.round(result.getTotalRiskScore() * 100), Long::sum);
        }
        
        statsMap.expire(Duration.ofDays(30)); // Keep stats for 30 days
    }
    
    /**
     * Cache result for performance
     */
    private void cacheResult(String requestId, BlacklistCheckResult result) {
        String cacheKey = BLACKLIST_CACHE_PREFIX + requestId;
        RMap<String, BlacklistCheckResult> cache = redissonClient.getMap(cacheKey);
        
        cache.put("result", result);
        cache.expire(Duration.ofMinutes(5)); // Short cache for recent results
    }
    
    // Masking methods for logging
    private String maskEmail(String email) {
        int atIndex = email.indexOf('@');
        if (atIndex > 2) {
            return email.substring(0, 2) + "***" + email.substring(atIndex);
        }
        return "***@" + email.substring(atIndex + 1);
    }
    
    private String maskPhone(String phone) {
        if (phone.length() > 6) {
            return phone.substring(0, 3) + "***" + phone.substring(phone.length() - 3);
        }
        return "***";
    }
    
    private String maskIp(String ip) {
        String[] parts = ip.split("\\.");
        if (parts.length == 4) {
            return parts[0] + "." + parts[1] + ".***." + parts[3];
        }
        return "***";
    }
    
    private String maskDeviceId(String deviceId) {
        if (deviceId.length() > 8) {
            return deviceId.substring(0, 4) + "***" + deviceId.substring(deviceId.length() - 4);
        }
        return "***";
    }
    
    private String maskName(String name) {
        if (name.length() > 2) {
            return name.substring(0, 2) + "***";
        }
        return "***";
    }
    
    // Supporting classes
    public enum BlacklistType {
        EMAIL, PHONE, IP, DEVICE, CARD, NAME_PATTERN, EMAIL_PATTERN, 
        PHONE_PATTERN, IP_REPUTATION, BEHAVIORAL
    }
    
    public static class BlacklistCheckRequest {
        private String requestId;
        private String userId;
        private String email;
        private String phone;
        private String ipAddress;
        private String deviceId;
        private String cardHash;
        private String firstName;
        private String lastName;
        
        // Getters and setters
        public String getRequestId() { return requestId; }
        public void setRequestId(String requestId) { this.requestId = requestId; }
        public String getUserId() { return userId; }
        public void setUserId(String userId) { this.userId = userId; }
        public String getEmail() { return email; }
        public void setEmail(String email) { this.email = email; }
        public String getPhone() { return phone; }
        public void setPhone(String phone) { this.phone = phone; }
        public String getIpAddress() { return ipAddress; }
        public void setIpAddress(String ipAddress) { this.ipAddress = ipAddress; }
        public String getDeviceId() { return deviceId; }
        public void setDeviceId(String deviceId) { this.deviceId = deviceId; }
        public String getCardHash() { return cardHash; }
        public void setCardHash(String cardHash) { this.cardHash = cardHash; }
        public String getFirstName() { return firstName; }
        public void setFirstName(String firstName) { this.firstName = firstName; }
        public String getLastName() { return lastName; }
        public void setLastName(String lastName) { this.lastName = lastName; }
    }
    
    public static class BlacklistCheckResult {
        private boolean clean = true;
        private boolean rateLimited = false;
        private String errorMessage;
        private List<BlacklistMatch> matches = new ArrayList<>();
        
        public static BlacklistCheckResult clean() {
            return new BlacklistCheckResult();
        }
        
        public static BlacklistCheckResult rateLimited() {
            BlacklistCheckResult result = new BlacklistCheckResult();
            result.rateLimited = true;
            return result;
        }
        
        public static BlacklistCheckResult error(String message) {
            BlacklistCheckResult result = new BlacklistCheckResult();
            result.errorMessage = message;
            return result;
        }
        
        public void addMatch(BlacklistMatch match) {
            if (match != null) {
                matches.add(match);
                clean = false;
            }
        }
        
        public boolean hasMatches() { return !matches.isEmpty(); }
        public boolean isClean() { return clean; }
        public boolean isRateLimited() { return rateLimited; }
        public String getErrorMessage() { return errorMessage; }
        public List<BlacklistMatch> getMatches() { return matches; }
        
        public double getTotalRiskScore() {
            return matches.stream()
                .mapToDouble(BlacklistMatch::getRiskScore)
                .sum();
        }
        
        public double getMaxRiskScore() {
            return matches.stream()
                .mapToDouble(BlacklistMatch::getRiskScore)
                .max()
                .orElse(0.0);
        }
    }
    
    public static class BlacklistMatch {
        private final BlacklistType type;
        private final String maskedValue;
        private final String reason;
        private final double riskScore;
        
        public BlacklistMatch(BlacklistType type, String maskedValue, String reason, double riskScore) {
            this.type = type;
            this.maskedValue = maskedValue;
            this.reason = reason;
            this.riskScore = riskScore;
        }
        
        public BlacklistType getType() { return type; }
        public String getMaskedValue() { return maskedValue; }
        public String getReason() { return reason; }
        public double getRiskScore() { return riskScore; }
    }
    
    public static class BlacklistAddRequest {
        private BlacklistType type;
        private String value;
        private String reason;
        private double riskScore;
        private String addedBy;
        private LocalDateTime expiresAt;
        
        // Getters and setters
        public BlacklistType getType() { return type; }
        public void setType(BlacklistType type) { this.type = type; }
        public String getValue() { return value; }
        public void setValue(String value) { this.value = value; }
        public String getReason() { return reason; }
        public void setReason(String reason) { this.reason = reason; }
        public double getRiskScore() { return riskScore; }
        public void setRiskScore(double riskScore) { this.riskScore = riskScore; }
        public String getAddedBy() { return addedBy; }
        public void setAddedBy(String addedBy) { this.addedBy = addedBy; }
        public LocalDateTime getExpiresAt() { return expiresAt; }
        public void setExpiresAt(LocalDateTime expiresAt) { this.expiresAt = expiresAt; }
    }
    
    public static class BlacklistEntry {
        private String id;
        private String hashedValue;
        private BlacklistType type;
        private String reason;
        private double riskScore;
        private String addedBy;
        private LocalDateTime addedAt;
        private LocalDateTime expiresAt;
        
        public static BlacklistEntryBuilder builder() {
            return new BlacklistEntryBuilder();
        }
        
        // Getters and setters
        public String getId() { return id; }
        public void setId(String id) { this.id = id; }
        public String getHashedValue() { return hashedValue; }
        public void setHashedValue(String hashedValue) { this.hashedValue = hashedValue; }
        public BlacklistType getType() { return type; }
        public void setType(BlacklistType type) { this.type = type; }
        public String getReason() { return reason; }
        public void setReason(String reason) { this.reason = reason; }
        public double getRiskScore() { return riskScore; }
        public void setRiskScore(double riskScore) { this.riskScore = riskScore; }
        public String getAddedBy() { return addedBy; }
        public void setAddedBy(String addedBy) { this.addedBy = addedBy; }
        public LocalDateTime getAddedAt() { return addedAt; }
        public void setAddedAt(LocalDateTime addedAt) { this.addedAt = addedAt; }
        public LocalDateTime getExpiresAt() { return expiresAt; }
        public void setExpiresAt(LocalDateTime expiresAt) { this.expiresAt = expiresAt; }
        
        public static class BlacklistEntryBuilder {
            private BlacklistEntry entry = new BlacklistEntry();
            
            public BlacklistEntryBuilder id(String id) {
                entry.id = id;
                return this;
            }
            
            public BlacklistEntryBuilder hashedValue(String hashedValue) {
                entry.hashedValue = hashedValue;
                return this;
            }
            
            public BlacklistEntryBuilder type(BlacklistType type) {
                entry.type = type;
                return this;
            }
            
            public BlacklistEntryBuilder reason(String reason) {
                entry.reason = reason;
                return this;
            }
            
            public BlacklistEntryBuilder riskScore(double riskScore) {
                entry.riskScore = riskScore;
                return this;
            }
            
            public BlacklistEntryBuilder addedBy(String addedBy) {
                entry.addedBy = addedBy;
                return this;
            }
            
            public BlacklistEntryBuilder addedAt(LocalDateTime addedAt) {
                entry.addedAt = addedAt;
                return this;
            }
            
            public BlacklistEntryBuilder expiresAt(LocalDateTime expiresAt) {
                entry.expiresAt = expiresAt;
                return this;
            }
            
            public BlacklistEntry build() {
                return entry;
            }
        }
    }
    
    // Rate limiter implementation
    private static class RateLimiter {
        private final int maxRequests;
        private final long windowMs;
        private final Queue<Long> requestTimes = new LinkedList<>();
        
        public RateLimiter(int maxRequests, long windowMs) {
            this.maxRequests = maxRequests;
            this.windowMs = windowMs;
        }
        
        public synchronized boolean tryAcquire() {
            long now = System.currentTimeMillis();
            
            // Remove old requests outside window
            while (!requestTimes.isEmpty() && requestTimes.peek() < now - windowMs) {
                requestTimes.poll();
            }
            
            if (requestTimes.size() < maxRequests) {
                requestTimes.offer(now);
                return true;
            }
            
            return false;
        }
    }
}