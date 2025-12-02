package com.waqiti.common.validation.service;

import io.github.resilience4j.circuitbreaker.annotation.CircuitBreaker;
import io.github.resilience4j.retry.annotation.Retry;
import io.github.resilience4j.bulkhead.annotation.Bulkhead;
import lombok.Builder;
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
import java.time.LocalDateTime;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.regex.Pattern;

/**
 * Comprehensive Email Validation Services
 * 
 * Production-ready services for email validation including:
 * - Disposable email detection
 * - Email verification and deliverability checking
 * - Domain reputation assessment
 * 
 * @author Waqiti Platform Team
 * @version 2.0.0
 * @since 2024-01-18
 */

// ==================== Disposable Email Service ====================

@Slf4j
@Service
@RequiredArgsConstructor
class InternalDisposableEmailService {
    
    private final JdbcTemplate jdbcTemplate;
    private final RestTemplate restTemplate;
    private final RedisTemplate<String, Object> redisTemplate;
    
    @Value("${email.disposable.api.url:https://api.disposable.email/check}")
    private String disposableApiUrl;
    
    @Value("${email.disposable.api.key}")
    private String apiKey;
    
    @Value("${email.disposable.cache.ttl:86400}")
    private int cacheTtl;
    
    private final Set<String> disposableDomains = ConcurrentHashMap.newKeySet();
    private final Map<String, Boolean> domainCache = new ConcurrentHashMap<>();
    
    @PostConstruct
    public void initialize() {
        log.info("Initializing Disposable Email Service");
        loadDisposableDomains();
        schedulePeriodicUpdate();
    }
    
    /**
     * Check if email domain is disposable
     */
    @Transactional(readOnly = true)
    public boolean isDisposable(String domain) {
        String normalized = domain.toLowerCase().trim();
        
        // Check cache
        if (domainCache.containsKey(normalized)) {
            return domainCache.get(normalized);
        }
        
        // Check in-memory set
        if (disposableDomains.contains(normalized)) {
            domainCache.put(normalized, true);
            return true;
        }
        
        // Check database
        try {
            Integer count = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM disposable_email_domains WHERE domain = ? AND is_active = true",
                Integer.class, normalized
            );
            
            boolean isDisposable = count != null && count > 0;
            domainCache.put(normalized, isDisposable);
            return isDisposable;
            
        } catch (Exception e) {
            log.error("Error checking disposable domain in database: {}", e.getMessage());
        }
        
        // Check external API
        return checkExternalApi(normalized);
    }
    
    /**
     * Get all known disposable domains
     */
    public Set<String> getAllDisposableDomains() {
        return new HashSet<>(disposableDomains);
    }
    
    /**
     * Add new disposable domain
     */
    public void addDisposableDomain(String domain) {
        String normalized = domain.toLowerCase().trim();
        disposableDomains.add(normalized);
        domainCache.put(normalized, true);
        
        try {
            jdbcTemplate.update(
                "INSERT INTO disposable_email_domains (domain, is_active, added_date) VALUES (?, true, NOW()) " +
                "ON DUPLICATE KEY UPDATE is_active = true",
                normalized
            );
        } catch (Exception e) {
            log.error("Error adding disposable domain: {}", e.getMessage());
        }
    }
    
    private void loadDisposableDomains() {
        try {
            // Load from database
            List<String> domains = jdbcTemplate.queryForList(
                "SELECT domain FROM disposable_email_domains WHERE is_active = true",
                String.class
            );
            disposableDomains.addAll(domains);
            
            // Load common disposable domains
            loadCommonDisposableDomains();
            
            log.info("Loaded {} disposable email domains", disposableDomains.size());
            
        } catch (Exception e) {
            log.error("Error loading disposable domains: {}", e.getMessage());
            loadCommonDisposableDomains(); // Fallback
        }
    }
    
    private void loadCommonDisposableDomains() {
        // Common disposable email domains
        disposableDomains.addAll(Arrays.asList(
            "mailinator.com", "guerrillamail.com", "10minutemail.com",
            "tempmail.com", "throwaway.email", "yopmail.com",
            "fakeinbox.com", "trashmail.com", "maildrop.cc",
            "dispostable.com", "temp-mail.org", "temporaryemail.net"
        ));
    }
    
    @CircuitBreaker(name = "disposable-email-api", fallbackMethod = "checkExternalApiFallback")
    @Retry(name = "disposable-email-api")
    @Bulkhead(name = "disposable-email-api")
    private boolean checkExternalApi(String domain) {
        try {
            Map<String, String> params = Map.of("domain", domain, "key", apiKey);
            Map response = restTemplate.getForObject(disposableApiUrl, Map.class, params);
            
            if (response != null && response.containsKey("disposable")) {
                boolean isDisposable = (boolean) response.get("disposable");
                domainCache.put(domain, isDisposable);
                
                if (isDisposable) {
                    addDisposableDomain(domain);
                }
                
                return isDisposable;
            }
        } catch (Exception e) {
            log.error("Error checking external disposable API: {}", e.getMessage());
            throw e; // Re-throw to trigger circuit breaker
        }
        
        return false;
    }
    
    /**
     * Fallback method for external API circuit breaker
     */
    private boolean checkExternalApiFallback(String domain, Exception ex) {
        log.warn("Circuit breaker activated for disposable email check, domain: {}, error: {}", 
                 domain, ex.getMessage());
        // Return cached value or conservative default
        return domainCache.getOrDefault(domain, false);
    }
    
    @Scheduled(cron = "0 0 3 * * ?") // 3 AM daily
    public void updateDisposableDomains() {
        log.info("Updating disposable domains list");
        loadDisposableDomains();
    }
    
    private void schedulePeriodicUpdate() {
        // Scheduled via @Scheduled annotation
    }
}

// ==================== Email Verification Service ====================

@Slf4j
@Service
@RequiredArgsConstructor
class InternalEmailVerificationService {
    
    private final JdbcTemplate jdbcTemplate;
    private final RestTemplate restTemplate;
    
    @Value("${email.verification.api.url:https://api.email-verify.com/verify}")
    private String verificationApiUrl;
    
    @Value("${email.verification.api.key}")
    private String apiKey;
    
    @Value("${email.verification.timeout:5000}")
    private int verificationTimeout;
    
    private static final Pattern EMAIL_PATTERN = Pattern.compile(
        "^[A-Z0-9._%+-]+@[A-Z0-9.-]+\\.[A-Z]{2,}$",
        Pattern.CASE_INSENSITIVE
    );
    
    /**
     * Verify email address deliverability
     */
    public EmailVerificationResult verifyEmail(String email) {
        log.debug("Verifying email: {}", email);
        
        // Basic format validation
        if (!isValidFormat(email)) {
            return EmailVerificationResult.builder()
                .email(email)
                .isValid(false)
                .reason("Invalid email format")
                .verifiedAt(LocalDateTime.now())
                .build();
        }
        
        String domain = extractDomain(email);
        
        // Check MX records
        boolean hasMX = checkMXRecords(domain);
        if (!hasMX) {
            return EmailVerificationResult.builder()
                .email(email)
                .isValid(false)
                .reason("No MX records found for domain")
                .verifiedAt(LocalDateTime.now())
                .build();
        }
        
        // Check with external verification service
        return performFullVerification(email);
    }
    
    /**
     * Validate email format
     */
    public boolean isValidFormat(String email) {
        if (email == null || email.isEmpty()) {
            return false;
        }
        return EMAIL_PATTERN.matcher(email).matches();
    }
    
    /**
     * Extract domain from email
     */
    public String extractDomain(String email) {
        int atIndex = email.lastIndexOf('@');
        return atIndex > 0 ? email.substring(atIndex + 1) : "";
    }
    
    /**
     * Check MX records for domain
     */
    public boolean checkMXRecords(String domain) {
        try {
            // In production, use DNS lookup libraries
            // For now, check against known domains
            Set<String> knownDomains = Set.of(
                "gmail.com", "yahoo.com", "outlook.com", "hotmail.com",
                "icloud.com", "protonmail.com", "aol.com"
            );
            
            if (knownDomains.contains(domain.toLowerCase())) {
                return true;
            }
            
            // Check database for cached MX status
            Integer count = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM domain_mx_records WHERE domain = ? AND has_mx = true",
                Integer.class, domain
            );
            
            return count != null && count > 0;
            
        } catch (Exception e) {
            log.error("Error checking MX records: {}", e.getMessage());
            return false;
        }
    }
    
    @CircuitBreaker(name = "email-verification-api", fallbackMethod = "performFullVerificationFallback")
    @Retry(name = "email-verification-api")
    @Bulkhead(name = "email-verification-api")
    private EmailVerificationResult performFullVerification(String email) {
        try {
            Map<String, String> params = Map.of("email", email, "key", apiKey);
            Map response = restTemplate.getForObject(verificationApiUrl, Map.class, params);
            
            if (response != null) {
                return EmailVerificationResult.builder()
                    .email(email)
                    .isValid((boolean) response.getOrDefault("deliverable", false))
                    .isDisposable((boolean) response.getOrDefault("disposable", false))
                    .isFree((boolean) response.getOrDefault("free", false))
                    .isRole((boolean) response.getOrDefault("role", false))
                    .score(((Number) response.getOrDefault("score", 0)).doubleValue())
                    .reason((String) response.get("reason"))
                    .verifiedAt(LocalDateTime.now())
                    .build();
            }
        } catch (Exception e) {
            log.error("Error performing email verification: {}", e.getMessage());
            throw e; // Re-throw to trigger circuit breaker
        }
        
        // Default response for no data
        return EmailVerificationResult.builder()
            .email(email)
            .isValid(true)
            .reason("No verification data available")
            .verifiedAt(LocalDateTime.now())
            .build();
    }
    
    /**
     * Fallback method for email verification circuit breaker
     */
    private EmailVerificationResult performFullVerificationFallback(String email, Exception ex) {
        log.warn("Circuit breaker activated for email verification, email: {}, error: {}", 
                 email, ex.getMessage());
        // Return safe default when circuit is open
        return EmailVerificationResult.builder()
            .email(email)
            .isValid(true) // Assume valid if can't verify
            .reason("Verification service temporarily unavailable")
            .verifiedAt(LocalDateTime.now())
            .build();
    }
    
    @lombok.Data
    @lombok.Builder
    public static class EmailVerificationResult {
        private String email;
        private boolean isValid;
        private boolean isDisposable;
        private boolean isFree;
        private boolean isRole;
        private double score;
        private String reason;
        private LocalDateTime verifiedAt;
    }
}

// ==================== Domain Reputation Service ====================

@Slf4j
@Service
@RequiredArgsConstructor
class InternalDomainReputationService {
    
    private final JdbcTemplate jdbcTemplate;
    private final RestTemplate restTemplate;
    private final RedisTemplate<String, Object> redisTemplate;
    
    @Value("${domain.reputation.api.url:https://api.reputation.com/check}")
    private String reputationApiUrl;
    
    @Value("${domain.reputation.api.key}")
    private String apiKey;
    
    private final Map<String, DomainReputation> reputationCache = new ConcurrentHashMap<>();
    private final Set<String> blacklistedDomains = ConcurrentHashMap.newKeySet();
    private final Set<String> whitelistedDomains = ConcurrentHashMap.newKeySet();
    
    @PostConstruct
    public void initialize() {
        log.info("Initializing Domain Reputation Service");
        loadDomainLists();
    }
    
    /**
     * Check domain reputation
     */
    public DomainReputation checkReputation(String domain) {
        String normalized = domain.toLowerCase().trim();
        
        // Check cache
        if (reputationCache.containsKey(normalized)) {
            DomainReputation cached = reputationCache.get(normalized);
            if (!cached.isExpired()) {
                return cached;
            }
        }
        
        // Check whitelist/blacklist
        if (whitelistedDomains.contains(normalized)) {
            return DomainReputation.builder()
                .domain(normalized)
                .score(100.0)
                .category("TRUSTED")
                .isBlacklisted(false)
                .isWhitelisted(true)
                .checkedAt(LocalDateTime.now())
                .build();
        }
        
        if (blacklistedDomains.contains(normalized)) {
            return DomainReputation.builder()
                .domain(normalized)
                .score(0.0)
                .category("BLACKLISTED")
                .isBlacklisted(true)
                .isWhitelisted(false)
                .checkedAt(LocalDateTime.now())
                .build();
        }
        
        // Check database
        DomainReputation dbReputation = checkDatabase(normalized);
        if (dbReputation != null) {
            reputationCache.put(normalized, dbReputation);
            return dbReputation;
        }
        
        // Check external API
        return checkExternalReputation(normalized);
    }
    
    /**
     * Check if domain is high risk
     */
    public boolean isHighRisk(String domain) {
        DomainReputation reputation = checkReputation(domain);
        return reputation.getScore() < 30 || reputation.isBlacklisted();
    }
    
    /**
     * Get domain age
     */
    public DomainAge getDomainAge(String domain) {
        try {
            Map<String, Object> result = jdbcTemplate.queryForMap(
                "SELECT created_date, updated_date, expires_date FROM domain_whois WHERE domain = ?",
                domain
            );
            
            return DomainAge.builder()
                .domain(domain)
                .createdDate((LocalDateTime) result.get("created_date"))
                .updatedDate((LocalDateTime) result.get("updated_date"))
                .expiresDate((LocalDateTime) result.get("expires_date"))
                .ageInDays(calculateAge((LocalDateTime) result.get("created_date")))
                .build();
            
        } catch (Exception e) {
            log.error("Error getting domain age: {}", e.getMessage());
            return null;
        }
    }
    
    private DomainReputation checkDatabase(String domain) {
        try {
            return jdbcTemplate.queryForObject(
                """
                SELECT domain, reputation_score, category, is_blacklisted,
                       is_whitelisted, spam_score, phishing_score, malware_score,
                       last_checked
                FROM domain_reputation
                WHERE domain = ? AND last_checked > DATE_SUB(NOW(), INTERVAL 7 DAY)
                """,
                (rs, rowNum) -> DomainReputation.builder()
                    .domain(rs.getString("domain"))
                    .score(rs.getDouble("reputation_score"))
                    .category(rs.getString("category"))
                    .isBlacklisted(rs.getBoolean("is_blacklisted"))
                    .isWhitelisted(rs.getBoolean("is_whitelisted"))
                    .spamScore(rs.getDouble("spam_score"))
                    .phishingScore(rs.getDouble("phishing_score"))
                    .malwareScore(rs.getDouble("malware_score"))
                    .checkedAt(rs.getTimestamp("last_checked").toLocalDateTime())
                    .build(),
                domain
            );
        } catch (Exception e) {
            return null;
        }
    }
    
    private DomainReputation checkExternalReputation(String domain) {
        try {
            Map<String, String> params = Map.of("domain", domain, "key", apiKey);
            Map response = restTemplate.getForObject(reputationApiUrl, Map.class, params);
            
            if (response != null) {
                DomainReputation reputation = DomainReputation.builder()
                    .domain(domain)
                    .score(((Number) response.getOrDefault("score", 50)).doubleValue())
                    .category((String) response.getOrDefault("category", "UNKNOWN"))
                    .isBlacklisted((boolean) response.getOrDefault("blacklisted", false))
                    .spamScore(((Number) response.getOrDefault("spam_score", 0)).doubleValue())
                    .phishingScore(((Number) response.getOrDefault("phishing_score", 0)).doubleValue())
                    .malwareScore(((Number) response.getOrDefault("malware_score", 0)).doubleValue())
                    .checkedAt(LocalDateTime.now())
                    .build();
                
                // Cache result
                reputationCache.put(domain, reputation);
                saveToDatabase(reputation);
                
                return reputation;
            }
        } catch (Exception e) {
            log.error("Error checking external reputation: {}", e.getMessage());
        }
        
        // Default neutral reputation
        return DomainReputation.builder()
            .domain(domain)
            .score(50.0)
            .category("UNKNOWN")
            .checkedAt(LocalDateTime.now())
            .build();
    }
    
    private void loadDomainLists() {
        try {
            // Load blacklisted domains
            List<String> blacklisted = jdbcTemplate.queryForList(
                "SELECT domain FROM domain_blacklist WHERE is_active = true",
                String.class
            );
            blacklistedDomains.addAll(blacklisted);
            
            // Load whitelisted domains
            List<String> whitelisted = jdbcTemplate.queryForList(
                "SELECT domain FROM domain_whitelist WHERE is_active = true",
                String.class
            );
            whitelistedDomains.addAll(whitelisted);
            
            // Add known good domains
            whitelistedDomains.addAll(Arrays.asList(
                "gmail.com", "yahoo.com", "outlook.com", "hotmail.com",
                "icloud.com", "protonmail.com", "fastmail.com"
            ));
            
            log.info("Loaded {} blacklisted and {} whitelisted domains",
                blacklistedDomains.size(), whitelistedDomains.size());
            
        } catch (Exception e) {
            log.error("Error loading domain lists: {}", e.getMessage());
        }
    }
    
    private void saveToDatabase(DomainReputation reputation) {
        try {
            jdbcTemplate.update(
                """
                INSERT INTO domain_reputation (domain, reputation_score, category,
                    is_blacklisted, is_whitelisted, spam_score, phishing_score,
                    malware_score, last_checked)
                VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?)
                ON DUPLICATE KEY UPDATE
                    reputation_score = VALUES(reputation_score),
                    category = VALUES(category),
                    is_blacklisted = VALUES(is_blacklisted),
                    spam_score = VALUES(spam_score),
                    phishing_score = VALUES(phishing_score),
                    malware_score = VALUES(malware_score),
                    last_checked = VALUES(last_checked)
                """,
                reputation.getDomain(), reputation.getScore(), reputation.getCategory(),
                reputation.isBlacklisted(), reputation.isWhitelisted(),
                reputation.getSpamScore(), reputation.getPhishingScore(),
                reputation.getMalwareScore(), reputation.getCheckedAt()
            );
        } catch (Exception e) {
            log.error("Error saving domain reputation: {}", e.getMessage());
        }
    }
    
    private long calculateAge(LocalDateTime createdDate) {
        if (createdDate == null) return 0;
        return java.time.Duration.between(createdDate, LocalDateTime.now()).toDays();
    }
    
    @lombok.Data
    @lombok.Builder
    public static class DomainReputation {
        private String domain;
        private double score;
        private String category;
        private boolean isBlacklisted;
        private boolean isWhitelisted;
        private double spamScore;
        private double phishingScore;
        private double malwareScore;
        private LocalDateTime checkedAt;
        @lombok.Builder.Default
        private long ttlSeconds = 86400; // 24 hours
        
        public boolean isExpired() {
            return checkedAt.plusSeconds(ttlSeconds).isBefore(LocalDateTime.now());
        }
    }
    
    @lombok.Data
    @lombok.Builder
    public static class DomainAge {
        private String domain;
        private LocalDateTime createdDate;
        private LocalDateTime updatedDate;
        private LocalDateTime expiresDate;
        private long ageInDays;
    }
}