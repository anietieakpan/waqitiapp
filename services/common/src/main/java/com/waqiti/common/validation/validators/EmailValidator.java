package com.waqiti.common.validation.validators;

import com.waqiti.common.validation.constraints.ValidEmail;
import com.waqiti.common.validation.constraints.ValidEmail.*;
import jakarta.validation.ConstraintValidator;
import jakarta.validation.ConstraintValidatorContext;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.stereotype.Component;

import javax.naming.Context;
import javax.naming.NamingException;
import javax.naming.directory.Attribute;
import javax.naming.directory.Attributes;
import javax.naming.directory.InitialDirContext;
import java.net.IDN;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Production-ready email validator with comprehensive validation features
 * including DNS verification, disposable email detection, and typo suggestions
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class EmailValidator implements ConstraintValidator<ValidEmail, String> {
    
    // RFC 5322 compliant email regex (simplified for readability)
    private static final Pattern BASIC_EMAIL_PATTERN = Pattern.compile(
        "^[A-Za-z0-9+_.-]+@([A-Za-z0-9.-]+\\.[A-Za-z]{2,})$"
    );
    
    // Strict RFC 5322 pattern
    private static final Pattern RFC5322_PATTERN = Pattern.compile(
        "^[a-zA-Z0-9_!#$%&'*+/=?`{|}~^.-]+@[a-zA-Z0-9.-]+$"
    );
    
    // Pattern with quoted strings support
    private static final Pattern QUOTED_EMAIL_PATTERN = Pattern.compile(
        "^\"[^\"]+\"@[a-zA-Z0-9.-]+\\.[a-zA-Z]{2,}$"
    );
    
    // IP domain pattern
    private static final Pattern IP_DOMAIN_PATTERN = Pattern.compile(
        "^[^@]+@\\[(\\d{1,3}\\.){3}\\d{1,3}\\]$"
    );
    
    private static final Map<String, Set<String>> DOMAIN_TYPOS = new ConcurrentHashMap<>();
    private static final Set<String> DISPOSABLE_DOMAINS = ConcurrentHashMap.newKeySet();
    private static final Set<String> SPAM_DOMAINS = ConcurrentHashMap.newKeySet();
    private static final Set<String> FREE_EMAIL_PROVIDERS = ConcurrentHashMap.newKeySet();
    private static final Set<String> BUSINESS_DOMAINS = ConcurrentHashMap.newKeySet();
    private static final Map<String, Boolean> DNS_CACHE = new ConcurrentHashMap<>();
    
    private ValidEmail annotation;
    private EmailValidationProvider provider;
    
    private final EmailVerificationService emailVerificationService;
    private final DisposableEmailService disposableEmailService;
    private final com.waqiti.common.validation.cache.EmailValidationCacheService emailValidationCacheService;
    
    static {
        initializeDisposableDomains();
        initializeSpamDomains();
        initializeFreeProviders();
        initializeTypoMappings();
        initializeBusinessDomains();
    }
    
    @Override
    public void initialize(ValidEmail constraintAnnotation) {
        this.annotation = constraintAnnotation;
        
        // Initialize custom provider if specified
        if (constraintAnnotation.provider() != DefaultEmailValidationProvider.class) {
            try {
                this.provider = constraintAnnotation.provider().getDeclaredConstructor().newInstance();
            } catch (Exception e) {
                log.error("Failed to initialize custom email validation provider", e);
                this.provider = new DefaultEmailValidationProvider();
            }
        } else {
            this.provider = new DefaultEmailValidationProvider();
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
        
        String normalizedEmail = normalizeEmail(value);
        
        // Check length constraints
        if (normalizedEmail.length() < annotation.minLength() || 
            normalizedEmail.length() > annotation.maxLength()) {
            addConstraintViolation(context, 
                String.format("Email length must be between %d and %d characters", 
                    annotation.minLength(), annotation.maxLength()));
            return false;
        }
        
        // Validate format based on mode and options
        if (!isValidFormat(normalizedEmail)) {
            String suggestion = suggestCorrection(normalizedEmail);
            if (suggestion != null && annotation.suggestTypoCorrection()) {
                addConstraintViolation(context, 
                    String.format("Invalid email format. Did you mean: %s?", suggestion));
            } else {
                addConstraintViolation(context, "Invalid email format");
            }
            return false;
        }
        
        // Extract parts
        EmailParts parts = parseEmail(normalizedEmail);
        if (parts == null) {
            addConstraintViolation(context, "Unable to parse email address");
            return false;
        }
        
        // Check allowed domains
        if (annotation.allowedDomains().length > 0) {
            if (!isInAllowedDomains(parts.domain, annotation.allowedDomains(), annotation.allowSubdomains())) {
                addConstraintViolation(context, "Email domain not in allowed list: " + parts.domain);
                return false;
            }
        }
        
        // Check blocked domains
        if (annotation.blockedDomains().length > 0) {
            if (isInBlockedDomains(parts.domain, annotation.blockedDomains())) {
                addConstraintViolation(context, "Email domain is blocked: " + parts.domain);
                return false;
            }
        }
        
        // Check disposable emails
        if (annotation.blockDisposable() && isDisposableEmail(parts.domain)) {
            addConstraintViolation(context, "Disposable email addresses are not allowed");
            return false;
        }
        
        // Check spam domains
        if (annotation.blockSpamDomains() && isSpamDomain(parts.domain)) {
            addConstraintViolation(context, "Email domain is known for spam");
            return false;
        }
        
        // Mode-specific validation
        if (!validateByMode(parts, context)) {
            return false;
        }
        
        // DNS verification
        if (annotation.verifyDomain() && !verifyDomainDNS(parts.domain)) {
            addConstraintViolation(context, "Email domain does not exist or has no MX records");
            return false;
        }
        
        // Use custom provider if available
        if (provider != null && !(provider instanceof DefaultEmailValidationProvider)) {
            return provider.isValid(normalizedEmail, annotation);
        }
        
        return true;
    }
    
    private void addConstraintViolation(ConstraintValidatorContext context, String message) {
        context.disableDefaultConstraintViolation();
        context.buildConstraintViolationWithTemplate(message)
               .addConstraintViolation();
    }
    
    private String normalizeEmail(String email) {
        String normalized = email.trim().toLowerCase();
        
        // Handle IDN domains
        if (annotation.allowIDN()) {
            int atIndex = normalized.lastIndexOf('@');
            if (atIndex > 0) {
                String localPart = normalized.substring(0, atIndex);
                String domain = normalized.substring(atIndex + 1);
                try {
                    domain = IDN.toASCII(domain);
                    normalized = localPart + "@" + domain;
                } catch (IllegalArgumentException e) {
                    log.debug("Failed to convert IDN domain: {}", domain, e);
                }
            }
        }
        
        return normalized;
    }
    
    private boolean isValidFormat(String email) {
        // Check for IP domain first if allowed
        if (IP_DOMAIN_PATTERN.matcher(email).matches()) {
            return annotation.allowIpDomain();
        }
        
        // Check for quoted string if allowed
        if (QUOTED_EMAIL_PATTERN.matcher(email).matches()) {
            return annotation.allowQuotedString();
        }
        
        // Use appropriate pattern based on strictness
        Pattern pattern = annotation.strictRFC5322() ? RFC5322_PATTERN : BASIC_EMAIL_PATTERN;
        return pattern.matcher(email).matches();
    }
    
    private EmailParts parseEmail(String email) {
        int atIndex = email.lastIndexOf('@');
        if (atIndex <= 0 || atIndex >= email.length() - 1) {
            return null;
        }
        
        String localPart = email.substring(0, atIndex);
        String domain = email.substring(atIndex + 1);
        
        // Validate local part
        if (!annotation.allowSpecialCharacters()) {
            if (!localPart.matches("^[a-zA-Z0-9._-]+$")) {
                return null;
            }
        }
        
        return new EmailParts(localPart, domain, email);
    }
    
    private boolean isInAllowedDomains(String domain, String[] allowedDomains, boolean allowSubdomains) {
        for (String allowed : allowedDomains) {
            if (domain.equalsIgnoreCase(allowed)) {
                return true;
            }
            if (allowSubdomains && domain.endsWith("." + allowed.toLowerCase())) {
                return true;
            }
        }
        return false;
    }
    
    private boolean isInBlockedDomains(String domain, String[] blockedDomains) {
        for (String blocked : blockedDomains) {
            if (domain.equalsIgnoreCase(blocked) || domain.endsWith("." + blocked.toLowerCase())) {
                return true;
            }
        }
        return false;
    }
    
    private boolean isDisposableEmail(String domain) {
        return emailValidationCacheService.isDisposableEmail(domain);
    }
    
    private boolean isSpamDomain(String domain) {
        return emailValidationCacheService.isSpamDomain(domain);
    }
    
    private boolean validateByMode(EmailParts parts, ConstraintValidatorContext context) {
        switch (annotation.mode()) {
            case BUSINESS:
                if (FREE_EMAIL_PROVIDERS.contains(parts.domain.toLowerCase())) {
                    addConstraintViolation(context, "Business email required (no free email providers)");
                    return false;
                }
                break;
                
            case CORPORATE:
                if (!BUSINESS_DOMAINS.contains(parts.domain.toLowerCase()) &&
                    !parts.domain.matches("^[a-zA-Z0-9.-]+\\.(com|org|net|biz|co\\.[a-z]{2})$")) {
                    addConstraintViolation(context, "Corporate email domain required");
                    return false;
                }
                break;
                
            case EDUCATIONAL:
                if (!parts.domain.endsWith(".edu") && !parts.domain.endsWith(".ac.uk") &&
                    !parts.domain.endsWith(".edu.au") && !parts.domain.endsWith(".ac.jp")) {
                    addConstraintViolation(context, "Educational email domain required (.edu)");
                    return false;
                }
                break;
                
            case GOVERNMENT:
                if (!parts.domain.endsWith(".gov") && !parts.domain.endsWith(".mil") &&
                    !parts.domain.endsWith(".gov.uk") && !parts.domain.endsWith(".gov.au")) {
                    addConstraintViolation(context, "Government email domain required (.gov)");
                    return false;
                }
                break;
                
            case STRICT:
                // Additional strict validations
                if (parts.localPart.startsWith(".") || parts.localPart.endsWith(".") ||
                    parts.localPart.contains("..")) {
                    addConstraintViolation(context, "Invalid local part format (consecutive dots or leading/trailing dots)");
                    return false;
                }
                break;
        }
        return true;
    }
    
    private boolean verifyDomainDNS(String domain) {
        return emailValidationCacheService.verifyDomainDNS(domain);
    }
    
    private boolean performDNSLookup(String domain) {
        try {
            Hashtable<String, String> env = new Hashtable<>();
            env.put(Context.INITIAL_CONTEXT_FACTORY, "com.sun.jndi.dns.DnsContextFactory");
            env.put(Context.PROVIDER_URL, "dns:");
            env.put("com.sun.jndi.dns.timeout.initial", "3000");
            env.put("com.sun.jndi.dns.timeout.retries", "1");
            
            InitialDirContext ctx = new InitialDirContext(env);
            Attributes attrs = ctx.getAttributes(domain, new String[]{"MX", "A"});
            
            Attribute mxAttr = attrs.get("MX");
            Attribute aAttr = attrs.get("A");
            
            boolean hasMX = mxAttr != null && mxAttr.size() > 0;
            boolean hasA = aAttr != null && aAttr.size() > 0;
            
            ctx.close();
            
            return hasMX || hasA;
        } catch (NamingException e) {
            log.debug("DNS validation failed for domain: {}", domain, e);
            return false;
        }
    }
    
    private String suggestCorrection(String email) {
        if (!annotation.suggestTypoCorrection()) {
            return null;
        }
        
        int atIndex = email.lastIndexOf('@');
        if (atIndex <= 0) {
            return null;
        }
        
        String domain = email.substring(atIndex + 1).toLowerCase();
        
        // Check for common typos
        for (Map.Entry<String, Set<String>> entry : DOMAIN_TYPOS.entrySet()) {
            if (entry.getValue().contains(domain)) {
                return email.substring(0, atIndex + 1) + entry.getKey();
            }
        }
        
        // Check for close matches using Levenshtein distance
        String closestMatch = findClosestDomain(domain);
        if (closestMatch != null) {
            return email.substring(0, atIndex + 1) + closestMatch;
        }
        
        return null;
    }
    
    private String findClosestDomain(String domain) {
        int minDistance = Integer.MAX_VALUE;
        String closest = null;
        
        Set<String> commonDomains = new HashSet<>();
        commonDomains.addAll(FREE_EMAIL_PROVIDERS);
        commonDomains.addAll(BUSINESS_DOMAINS);
        
        for (String common : commonDomains) {
            int distance = levenshteinDistance(domain, common);
            if (distance < minDistance && distance <= 2) { // Max 2 character difference
                minDistance = distance;
                closest = common;
            }
        }
        
        return closest;
    }
    
    private int levenshteinDistance(String s1, String s2) {
        int[][] dp = new int[s1.length() + 1][s2.length() + 1];
        
        for (int i = 0; i <= s1.length(); i++) {
            dp[i][0] = i;
        }
        
        for (int j = 0; j <= s2.length(); j++) {
            dp[0][j] = j;
        }
        
        for (int i = 1; i <= s1.length(); i++) {
            for (int j = 1; j <= s2.length(); j++) {
                int cost = s1.charAt(i - 1) == s2.charAt(j - 1) ? 0 : 1;
                dp[i][j] = Math.min(Math.min(
                    dp[i - 1][j] + 1,      // deletion
                    dp[i][j - 1] + 1),     // insertion
                    dp[i - 1][j - 1] + cost // substitution
                );
            }
        }
        
        return dp[s1.length()][s2.length()];
    }
    
    private static void initializeDisposableDomains() {
        DISPOSABLE_DOMAINS.addAll(Arrays.asList(
            "10minutemail.com", "guerrillamail.com", "mailinator.com", "temp-mail.org",
            "throwaway.email", "yopmail.com", "tempmail.com", "trashmail.com",
            "fakeinbox.com", "sharklasers.com", "guerrillamailblock.com",
            "dispostable.com", "mailcatch.com", "temporaryemail.net",
            "throwawaymail.com", "tempinbox.com", "disposableemailaddresses.com",
            "mailnesia.com", "tempmailaddress.com", "emailondeck.com",
            "getnada.com", "inboxbear.com", "mailpoof.com", "mintemail.com",
            "mt2015.com", "throwawayemailaddress.com", "tmpmail.net",
            "tmpeml.info", "tempsky.com", "t.odmail.cn"
        ));
    }
    
    private static void initializeSpamDomains() {
        SPAM_DOMAINS.addAll(Arrays.asList(
            "spamgourmet.com", "spamex.com", "spam4.me", "spamavert.com",
            "spamfree24.org", "spamhereplease.com", "spamhole.com",
            "spamify.com", "spammotel.com", "spamslicer.com"
        ));
    }
    
    private static void initializeFreeProviders() {
        FREE_EMAIL_PROVIDERS.addAll(Arrays.asList(
            "gmail.com", "yahoo.com", "hotmail.com", "outlook.com",
            "aol.com", "mail.com", "protonmail.com", "zoho.com",
            "icloud.com", "yandex.com", "mail.ru", "gmx.com",
            "fastmail.com", "tutanota.com", "pm.me", "proton.me",
            "yahoo.co.uk", "gmail.co.uk", "hotmail.co.uk",
            "yahoo.fr", "gmail.fr", "hotmail.fr", "orange.fr",
            "yahoo.de", "gmail.de", "gmx.de", "web.de",
            "qq.com", "163.com", "126.com", "sina.com",
            "naver.com", "daum.net", "kakao.com"
        ));
    }
    
    private static void initializeTypoMappings() {
        // Common Gmail typos
        DOMAIN_TYPOS.put("gmail.com", Set.of(
            "gmial.com", "gmai.com", "gmali.com", "gmil.com",
            "gmaill.com", "gmailcom", "gmail.co", "gmail.cm"
        ));
        
        // Common Yahoo typos
        DOMAIN_TYPOS.put("yahoo.com", Set.of(
            "yaho.com", "yahooo.com", "yahoo.co", "yahoo.cm",
            "yhoo.com", "yhaoo.com", "yahoo.con"
        ));
        
        // Common Hotmail typos
        DOMAIN_TYPOS.put("hotmail.com", Set.of(
            "hotmial.com", "hotmai.com", "hotmali.com", "hotmil.com",
            "hotmaill.com", "hotmailcom", "hotmail.co", "hotmail.cm"
        ));
        
        // Common Outlook typos
        DOMAIN_TYPOS.put("outlook.com", Set.of(
            "outlok.com", "outloo.com", "outlook.co", "outlook.cm",
            "outlookcom", "outloook.com"
        ));
    }
    
    private static void initializeBusinessDomains() {
        // Known business/corporate domains
        BUSINESS_DOMAINS.addAll(Arrays.asList(
            "microsoft.com", "apple.com", "google.com", "amazon.com",
            "facebook.com", "meta.com", "netflix.com", "adobe.com",
            "salesforce.com", "oracle.com", "ibm.com", "intel.com",
            "cisco.com", "dell.com", "hp.com", "lenovo.com",
            "samsung.com", "sony.com", "lg.com", "panasonic.com"
        ));
    }
    
    /**
     * Email parts holder
     */
    private static class EmailParts {
        final String localPart;
        final String domain;
        final String fullEmail;
        
        EmailParts(String localPart, String domain, String fullEmail) {
            this.localPart = localPart;
            this.domain = domain;
            this.fullEmail = fullEmail;
        }
    }
    
    /**
     * Optional external email verification service
     */
    public interface EmailVerificationService {
        boolean verifyDomain(String domain);
        boolean verifyEmail(String email);
        EmailInfo getEmailInfo(String email);
    }
    
    /**
     * Optional external disposable email service
     */
    public interface DisposableEmailService {
        boolean isDisposable(String domain);
        Set<String> getDisposableDomains();
    }
}