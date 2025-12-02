package com.waqiti.common.validation.service;

import com.waqiti.common.validation.model.EmailValidationResult;
import lombok.extern.slf4j.Slf4j;
import lombok.RequiredArgsConstructor;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import javax.naming.NamingEnumeration;
import javax.naming.NamingException;
import javax.naming.directory.Attribute;
import javax.naming.directory.Attributes;
import javax.naming.directory.DirContext;
import javax.naming.directory.InitialDirContext;
import java.time.LocalDateTime;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.regex.Pattern;

/**
 * Default implementation of EmailVerificationService.
 * Provides comprehensive email verification with caching, database persistence,
 * and integration with multiple verification methods.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class DefaultEmailVerificationService implements EmailVerificationService {
    
    private static final Pattern EMAIL_PATTERN = Pattern.compile(
        "^[A-Za-z0-9+_.-]+@[A-Za-z0-9.-]+\\.[A-Za-z]{2,}$"
    );
    
    private static final Set<String> DISPOSABLE_DOMAINS = Set.of(
        "tempmail.com", "throwaway.email", "guerrillamail.com",
        "mailinator.com", "10minutemail.com", "trashmail.com",
        "fakeinbox.com", "yopmail.com", "mailcatch.com"
    );
    
    private static final Set<String> FREE_PROVIDERS = Set.of(
        "gmail.com", "yahoo.com", "hotmail.com", "outlook.com",
        "aol.com", "protonmail.com", "icloud.com", "mail.com",
        "yandex.com", "gmx.com"
    );
    
    private static final Set<String> ROLE_PREFIXES = Set.of(
        "admin", "info", "support", "sales", "contact",
        "help", "noreply", "no-reply", "webmaster", "postmaster",
        "abuse", "billing", "legal", "security", "privacy"
    );
    
    private final Map<String, EmailValidationResult> cache = new ConcurrentHashMap<>();
    private final Map<String, Boolean> mxCache = new ConcurrentHashMap<>();
    private final Map<String, Double> reputationCache = new ConcurrentHashMap<>();
    
    private final JdbcTemplate jdbcTemplate;
    
    
    @Override
    @Transactional(readOnly = true)
    public EmailValidationResult verifyEmail(String email) {
        log.debug("Verifying email: {}", email);
        
        // Check cache first
        EmailValidationResult cached = cache.get(email);
        if (cached != null && cached.getValidatedAt().isAfter(LocalDateTime.now().minusHours(24))) {
            log.debug("Returning cached result for: {}", email);
            return cached;
        }
        
        EmailValidationResult.EmailValidationResultBuilder resultBuilder = EmailValidationResult.builder()
            .email(email)
            .validatedAt(LocalDateTime.now());
        
        // Syntax validation
        boolean validSyntax = isValidSyntax(email);
        resultBuilder.syntaxValid(validSyntax);
        
        if (!validSyntax) {
            resultBuilder
                .overallValid(false)
                .confidence(1.0)
                .riskScore(1.0)
                .validationMessages(List.of("Invalid email syntax"));
            
            EmailValidationResult result = resultBuilder.build();
            cache.put(email, result);
            return result;
        }
        
        // Extract domain
        String domain = email.substring(email.indexOf('@') + 1).toLowerCase();
        resultBuilder.domain(domain);
        
        // Domain validation
        boolean domainExists = domainExists(email);
        resultBuilder.domainExists(domainExists);
        
        // MX Records check
        boolean hasMX = hasMXRecords(email);
        resultBuilder.hasMXRecords(hasMX);
        
        // Disposable check
        boolean isDisposable = isDisposable(email);
        resultBuilder.isDisposable(isDisposable);
        
        // Role account check
        boolean isRole = isRoleAccount(email);
        resultBuilder.isRoleAccount(isRole);
        
        // Free provider check
        boolean isFree = isFreeProvider(email);
        resultBuilder.isFreeProvider(isFree);
        
        // Corporate check
        boolean isCorporate = isCorporate(email);
        resultBuilder.isCorporate(isCorporate);
        
        // Reputation score
        double reputation = getDomainReputation(email);
        resultBuilder.domainReputation(reputation);
        
        // Risk score calculation
        double riskScore = calculateRiskScore(isDisposable, isRole, !hasMX, reputation);
        resultBuilder.riskScore(riskScore);
        
        // Overall validity
        boolean overallValid = validSyntax && domainExists && hasMX && !isDisposable;
        resultBuilder.overallValid(overallValid);
        
        // Confidence calculation
        double confidence = calculateConfidence(domainExists, hasMX, reputation);
        resultBuilder.confidence(confidence);
        
        // Build validation messages
        List<String> messages = new ArrayList<>();
        if (!domainExists) messages.add("Domain does not exist");
        if (!hasMX) messages.add("No MX records found");
        if (isDisposable) messages.add("Disposable email detected");
        if (isRole) messages.add("Role-based email account");
        if (riskScore > 0.7) messages.add("High risk email");
        
        resultBuilder.validationMessages(messages);
        
        // Build result
        EmailValidationResult result = resultBuilder.build();
        
        // Cache result
        cache.put(email, result);
        
        // Persist to database if available
        persistValidationResult(result);
        
        log.info("Email verification completed for {}: valid={}, risk={}", 
                email, overallValid, riskScore);
        
        return result;
    }
    
    @Override
    public boolean isValidSyntax(String email) {
        if (email == null || email.trim().isEmpty()) {
            return false;
        }
        
        // Basic pattern matching
        if (!EMAIL_PATTERN.matcher(email.toLowerCase()).matches()) {
            return false;
        }
        
        // Additional validation rules
        String[] parts = email.split("@");
        if (parts.length != 2) {
            return false;
        }
        
        String localPart = parts[0];
        String domain = parts[1];
        
        // Local part validation
        if (localPart.isEmpty() || localPart.length() > 64) {
            return false;
        }
        if (localPart.startsWith(".") || localPart.endsWith(".")) {
            return false;
        }
        if (localPart.contains("..")) {
            return false;
        }
        
        // Domain validation
        if (domain.isEmpty() || domain.length() > 255) {
            return false;
        }
        if (domain.startsWith(".") || domain.endsWith(".")) {
            return false;
        }
        if (domain.contains("..")) {
            return false;
        }
        
        return true;
    }
    
    @Override
    public boolean hasMXRecords(String email) {
        if (!isValidSyntax(email)) {
            return false;
        }
        
        String domain = email.substring(email.indexOf('@') + 1);
        
        // Check cache
        Boolean cached = mxCache.get(domain);
        if (cached != null) {
            return cached;
        }
        
        try {
            // Perform DNS lookup for MX records
            Hashtable<String, String> env = new Hashtable<>();
            env.put("java.naming.factory.initial", "com.sun.jndi.dns.DnsContextFactory");
            DirContext ctx = new InitialDirContext(env);
            
            Attributes attrs = ctx.getAttributes(domain, new String[]{"MX"});
            Attribute mx = attrs.get("MX");
            
            boolean hasMX = (mx != null && mx.size() > 0);
            
            // Cache result
            mxCache.put(domain, hasMX);
            
            return hasMX;
            
        } catch (NamingException e) {
            log.debug("No MX records found for domain: {}", domain);
            mxCache.put(domain, false);
            return false;
        }
    }
    
    @Override
    public boolean isDisposable(String email) {
        if (!isValidSyntax(email)) {
            return false;
        }
        
        String domain = email.substring(email.indexOf('@') + 1).toLowerCase();
        
        // Check against known disposable domains
        if (DISPOSABLE_DOMAINS.contains(domain)) {
            return true;
        }
        
        // Check database if available
        if (jdbcTemplate != null) {
            try {
                Integer count = jdbcTemplate.queryForObject(
                    "SELECT COUNT(*) FROM disposable_domains WHERE domain = ?",
                    Integer.class, domain
                );
                return count != null && count > 0;
            } catch (Exception e) {
                log.debug("Database check for disposable domain failed: {}", e.getMessage());
            }
        }
        
        return false;
    }
    
    @Override
    public boolean domainExists(String email) {
        if (!isValidSyntax(email)) {
            return false;
        }
        
        String domain = email.substring(email.indexOf('@') + 1);
        
        try {
            // Try to resolve the domain
            Hashtable<String, String> env = new Hashtable<>();
            env.put("java.naming.factory.initial", "com.sun.jndi.dns.DnsContextFactory");
            DirContext ctx = new InitialDirContext(env);
            
            // Check for A or AAAA records
            Attributes attrs = ctx.getAttributes(domain, new String[]{"A", "AAAA"});
            
            return attrs.size() > 0;
            
        } catch (NamingException e) {
            log.debug("Domain does not exist: {}", domain);
            return false;
        }
    }
    
    @Override
    public double getDomainReputation(String email) {
        if (!isValidSyntax(email)) {
            return 0.0;
        }
        
        String domain = email.substring(email.indexOf('@') + 1).toLowerCase();
        
        // Check cache
        Double cached = reputationCache.get(domain);
        if (cached != null) {
            return cached;
        }
        
        double reputation = 0.5; // Default neutral reputation
        
        // Known good domains
        if (FREE_PROVIDERS.contains(domain)) {
            reputation = 0.7;
        }
        
        // Corporate domains typically have better reputation
        if (isCorporate(email)) {
            reputation = 0.8;
        }
        
        // Disposable domains have poor reputation
        if (DISPOSABLE_DOMAINS.contains(domain)) {
            reputation = 0.1;
        }
        
        // Check database if available
        if (jdbcTemplate != null) {
            try {
                Double dbReputation = jdbcTemplate.queryForObject(
                    "SELECT reputation_score FROM domain_reputation WHERE domain = ?",
                    Double.class, domain
                );
                if (dbReputation != null) {
                    reputation = dbReputation;
                }
            } catch (Exception e) {
                log.debug("No reputation data in database for domain: {}", domain);
            }
        }
        
        // Cache result
        reputationCache.put(domain, reputation);
        
        return reputation;
    }
    
    @Override
    public boolean isBlacklisted(String email) {
        if (jdbcTemplate != null) {
            try {
                Integer count = jdbcTemplate.queryForObject(
                    "SELECT COUNT(*) FROM email_blacklist WHERE email = ? OR domain = ?",
                    Integer.class, email, email.substring(email.indexOf('@') + 1)
                );
                return count != null && count > 0;
            } catch (Exception e) {
                log.debug("Blacklist check failed: {}", e.getMessage());
            }
        }
        return false;
    }
    
    @Override
    public boolean isRoleAccount(String email) {
        if (!isValidSyntax(email)) {
            return false;
        }
        
        String localPart = email.substring(0, email.indexOf('@')).toLowerCase();
        
        for (String prefix : ROLE_PREFIXES) {
            if (localPart.equals(prefix) || localPart.startsWith(prefix + ".") || 
                localPart.startsWith(prefix + "-") || localPart.startsWith(prefix + "_")) {
                return true;
            }
        }
        
        return false;
    }
    
    @Override
    public boolean isFreeProvider(String email) {
        if (!isValidSyntax(email)) {
            return false;
        }
        
        String domain = email.substring(email.indexOf('@') + 1).toLowerCase();
        return FREE_PROVIDERS.contains(domain);
    }
    
    @Override
    public boolean isCorporate(String email) {
        if (!isValidSyntax(email)) {
            return false;
        }
        
        // Not a free provider and not disposable
        return !isFreeProvider(email) && !isDisposable(email) && hasMXRecords(email);
    }
    
    @Override
    public boolean verifyViaSMTP(String email) {
        // SMTP verification is complex and often blocked
        // This is a placeholder for actual SMTP verification
        log.debug("SMTP verification not implemented for: {}", email);
        return true; // Default to true as many servers block SMTP verification
    }
    
    @Override
    public double getRiskScore(String email) {
        EmailValidationResult result = verifyEmail(email);
        return result.getRiskScore();
    }
    
    private double calculateRiskScore(boolean isDisposable, boolean isRole, 
                                      boolean noMX, double reputation) {
        double risk = 0.0;
        
        if (isDisposable) risk += 0.4;
        if (isRole) risk += 0.2;
        if (noMX) risk += 0.3;
        risk += (1.0 - reputation) * 0.3;
        
        return Math.min(risk, 1.0);
    }
    
    private double calculateConfidence(boolean domainExists, boolean hasMX, double reputation) {
        double confidence = 0.5;
        
        if (domainExists) confidence += 0.2;
        if (hasMX) confidence += 0.2;
        confidence += reputation * 0.1;
        
        return Math.min(confidence, 1.0);
    }
    
    private void persistValidationResult(EmailValidationResult result) {
        if (jdbcTemplate != null) {
            try {
                jdbcTemplate.update(
                    "INSERT INTO email_validation_results (email, domain, valid, risk_score, " +
                    "confidence, is_disposable, has_mx, validated_at) " +
                    "VALUES (?, ?, ?, ?, ?, ?, ?, ?) " +
                    "ON CONFLICT (email) DO UPDATE SET " +
                    "valid = EXCLUDED.valid, risk_score = EXCLUDED.risk_score, " +
                    "confidence = EXCLUDED.confidence, validated_at = EXCLUDED.validated_at",
                    result.getEmail(), result.getDomain(), result.isOverallValid(),
                    result.getRiskScore(), result.getConfidence(), result.isDisposable(),
                    result.isHasMXRecords(), result.getValidatedAt()
                );
            } catch (Exception e) {
                log.debug("Failed to persist validation result: {}", e.getMessage());
            }
        }
    }
}