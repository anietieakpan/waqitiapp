package com.waqiti.common.security;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.fasterxml.jackson.databind.node.TextNode;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.jsoup.Jsoup;
import org.jsoup.safety.Safelist;
import org.owasp.encoder.Encode;
import org.springframework.stereotype.Service;

import java.util.*;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

/**
 * Comprehensive XSS Protection Service
 * 
 * Provides multiple layers of protection against Cross-Site Scripting attacks:
 * - Input validation and sanitization
 * - Output encoding for different contexts
 * - Content Security Policy generation
 * - Rich text sanitization
 * - JSON sanitization
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class XSSProtectionService {

    private final ObjectMapper objectMapper;
    
    // Dangerous patterns that might indicate XSS attempts
    private static final List<Pattern> XSS_PATTERNS = Arrays.asList(
        Pattern.compile("(<script[^>]*>.*?</script>)", Pattern.CASE_INSENSITIVE | Pattern.MULTILINE | Pattern.DOTALL),
        Pattern.compile("(javascript:.*)", Pattern.CASE_INSENSITIVE),
        Pattern.compile("(on\\w+\\s*=)", Pattern.CASE_INSENSITIVE),
        Pattern.compile("(<iframe[^>]*>.*?</iframe>)", Pattern.CASE_INSENSITIVE | Pattern.MULTILINE | Pattern.DOTALL),
        Pattern.compile("(<object[^>]*>.*?</object>)", Pattern.CASE_INSENSITIVE | Pattern.MULTILINE | Pattern.DOTALL),
        Pattern.compile("(<embed[^>]*>.*?</embed>)", Pattern.CASE_INSENSITIVE | Pattern.MULTILINE | Pattern.DOTALL),
        Pattern.compile("(eval\\s*\\()", Pattern.CASE_INSENSITIVE),
        Pattern.compile("(expression\\s*\\()", Pattern.CASE_INSENSITIVE),
        Pattern.compile("(vbscript:)", Pattern.CASE_INSENSITIVE),
        Pattern.compile("(data:text/html)", Pattern.CASE_INSENSITIVE),
        Pattern.compile("(<svg[^>]*>.*?</svg>)", Pattern.CASE_INSENSITIVE | Pattern.MULTILINE | Pattern.DOTALL)
    );
    
    // SQL Injection patterns
    private static final List<Pattern> SQL_PATTERNS = Arrays.asList(
        Pattern.compile("(\\bUNION\\b.*\\bSELECT\\b)", Pattern.CASE_INSENSITIVE),
        Pattern.compile("(\\bDROP\\b.*\\bTABLE\\b)", Pattern.CASE_INSENSITIVE),
        Pattern.compile("(\\bINSERT\\b.*\\bINTO\\b)", Pattern.CASE_INSENSITIVE),
        Pattern.compile("(\\bDELETE\\b.*\\bFROM\\b)", Pattern.CASE_INSENSITIVE),
        Pattern.compile("(\\bUPDATE\\b.*\\bSET\\b)", Pattern.CASE_INSENSITIVE),
        Pattern.compile("(\\bEXEC\\b|\\bEXECUTE\\b)", Pattern.CASE_INSENSITIVE),
        Pattern.compile("(--|#|/\\*|\\*/)", Pattern.CASE_INSENSITIVE),
        Pattern.compile("(\\bOR\\b\\s+[\\w']+=\\s*[\\w']+)", Pattern.CASE_INSENSITIVE),
        Pattern.compile("(\\bAND\\b\\s+[\\w']+=\\s*[\\w']+)", Pattern.CASE_INSENSITIVE)
    );
    
    // Command injection patterns
    private static final List<Pattern> COMMAND_PATTERNS = Arrays.asList(
        Pattern.compile("([;&|`$])"),
        Pattern.compile("(\\$\\(.*\\))"),
        Pattern.compile("(\\bsh\\b|\\bbash\\b|\\bcmd\\b|\\bpowershell\\b)", Pattern.CASE_INSENSITIVE),
        Pattern.compile("(>|>>|<|<<)"),
        Pattern.compile("(\\|\\|)"),
        Pattern.compile("(&&)")
    );

    /**
     * Sanitize input for general text fields
     */
    public String sanitizeInput(String input) {
        if (input == null || input.isEmpty()) {
            return input;
        }

        // Check for malicious patterns
        if (containsMaliciousPattern(input)) {
            log.warn("Malicious pattern detected in input, blocking: {}", truncateForLogging(input));
            throw new XSSValidationException("Invalid input detected");
        }

        // Basic HTML entity encoding
        String sanitized = Encode.forHtml(input);
        
        // Additional cleaning
        sanitized = sanitized.replaceAll("[\u0000-\u001F\u007F-\u009F]", ""); // Remove control characters
        sanitized = sanitized.trim();
        
        return sanitized;
    }

    /**
     * Sanitize rich text content (allows some HTML tags)
     */
    public String sanitizeRichText(String html) {
        if (html == null || html.isEmpty()) {
            return html;
        }

        // Use JSoup with a strict whitelist
        Safelist safelist = Safelist.basic()
            .addTags("p", "br", "strong", "em", "u", "ul", "ol", "li", "blockquote")
            .addAttributes("a", "href")
            .addProtocols("a", "href", "https", "http")
            .removeAttributes("a", "target"); // Remove target to prevent clickjacking

        String cleaned = Jsoup.clean(html, safelist);
        
        // Additional validation
        if (containsMaliciousPattern(cleaned)) {
            log.warn("Malicious pattern found after HTML cleaning");
            throw new XSSValidationException("Invalid HTML content");
        }

        return cleaned;
    }

    /**
     * Sanitize JSON input recursively
     */
    public String sanitizeJson(String json) {
        if (json == null || json.isEmpty()) {
            return json;
        }

        try {
            JsonNode root = objectMapper.readTree(json);
            JsonNode sanitized = sanitizeJsonNode(root);
            return objectMapper.writeValueAsString(sanitized);
        } catch (Exception e) {
            log.error("Failed to sanitize JSON", e);
            throw new XSSValidationException("Invalid JSON input");
        }
    }

    /**
     * Encode output for HTML context
     */
    public String encodeForHTML(String input) {
        return Encode.forHtml(input);
    }

    /**
     * Encode output for HTML attribute context
     */
    public String encodeForHTMLAttribute(String input) {
        return Encode.forHtmlAttribute(input);
    }

    /**
     * Encode output for JavaScript context
     */
    public String encodeForJavaScript(String input) {
        return Encode.forJavaScript(input);
    }

    /**
     * Encode output for CSS context
     */
    public String encodeForCSS(String input) {
        return Encode.forCssString(input);
    }

    /**
     * Encode output for URL context
     */
    public String encodeForURL(String input) {
        return Encode.forUriComponent(input);
    }

    /**
     * Validate and sanitize email addresses
     */
    public String sanitizeEmail(String email) {
        if (email == null || email.isEmpty()) {
            return email;
        }

        // Basic email validation pattern
        String emailPattern = "^[A-Za-z0-9+_.-]+@[A-Za-z0-9.-]+\\.[A-Za-z]{2,}$";
        if (!email.matches(emailPattern)) {
            throw new XSSValidationException("Invalid email format");
        }

        // Additional sanitization
        email = email.toLowerCase().trim();
        
        // Check for malicious patterns in email
        if (containsMaliciousPattern(email)) {
            throw new XSSValidationException("Invalid email content");
        }

        return email;
    }

    /**
     * Validate and sanitize phone numbers
     */
    public String sanitizePhoneNumber(String phone) {
        if (phone == null || phone.isEmpty()) {
            return phone;
        }

        // Remove all non-numeric characters except + and -
        String sanitized = phone.replaceAll("[^0-9+\\-() ]", "");
        
        // Validate length (international numbers can be up to 15 digits)
        if (sanitized.replaceAll("[^0-9]", "").length() > 15) {
            throw new XSSValidationException("Invalid phone number");
        }

        return sanitized;
    }

    /**
     * Validate and sanitize URLs
     */
    public String sanitizeURL(String url) {
        if (url == null || url.isEmpty()) {
            return url;
        }

        // Check for javascript: and data: protocols
        String lowerUrl = url.toLowerCase().trim();
        if (lowerUrl.startsWith("javascript:") || 
            lowerUrl.startsWith("data:") || 
            lowerUrl.startsWith("vbscript:")) {
            throw new XSSValidationException("Invalid URL protocol");
        }

        // Validate URL format
        try {
            new java.net.URL(url);
        } catch (Exception e) {
            throw new XSSValidationException("Invalid URL format");
        }

        // Encode URL parameters
        return Encode.forUriComponent(url);
    }

    /**
     * Sanitize file names
     */
    public String sanitizeFileName(String fileName) {
        if (fileName == null || fileName.isEmpty()) {
            return fileName;
        }

        // Remove path traversal attempts
        fileName = fileName.replaceAll("\\.\\./", "");
        fileName = fileName.replaceAll("\\.\\.\\\\", "");
        
        // Remove special characters that could be problematic
        fileName = fileName.replaceAll("[^a-zA-Z0-9._-]", "_");
        
        // Limit length
        if (fileName.length() > 255) {
            fileName = fileName.substring(0, 255);
        }

        return fileName;
    }

    /**
     * Generate Content Security Policy header
     */
    public String generateCSPHeader(CSPConfig config) {
        StringBuilder csp = new StringBuilder();
        
        // Default source
        csp.append("default-src ").append(config.getDefaultSrc()).append("; ");
        
        // Script source
        if (config.getScriptSrc() != null) {
            csp.append("script-src ").append(config.getScriptSrc());
            if (config.isAllowInlineScripts()) {
                csp.append(" 'unsafe-inline'");
            }
            if (config.getNonce() != null) {
                csp.append(" 'nonce-").append(config.getNonce()).append("'");
            }
            csp.append("; ");
        }
        
        // Style source
        if (config.getStyleSrc() != null) {
            csp.append("style-src ").append(config.getStyleSrc());
            if (config.isAllowInlineStyles()) {
                csp.append(" 'unsafe-inline'");
            }
            csp.append("; ");
        }
        
        // Image source
        if (config.getImgSrc() != null) {
            csp.append("img-src ").append(config.getImgSrc()).append("; ");
        }
        
        // Connect source (for AJAX, WebSocket, etc.)
        if (config.getConnectSrc() != null) {
            csp.append("connect-src ").append(config.getConnectSrc()).append("; ");
        }
        
        // Frame ancestors (clickjacking protection)
        csp.append("frame-ancestors 'none'; ");
        
        // Form action
        if (config.getFormAction() != null) {
            csp.append("form-action ").append(config.getFormAction()).append("; ");
        }
        
        // Upgrade insecure requests
        if (config.isUpgradeInsecureRequests()) {
            csp.append("upgrade-insecure-requests; ");
        }
        
        // Block all mixed content
        if (config.isBlockAllMixedContent()) {
            csp.append("block-all-mixed-content; ");
        }

        return csp.toString().trim();
    }

    /**
     * Validate input against a whitelist of allowed characters
     */
    public boolean validateAgainstWhitelist(String input, String allowedCharacters) {
        if (input == null || input.isEmpty()) {
            return true;
        }

        for (char c : input.toCharArray()) {
            if (allowedCharacters.indexOf(c) == -1) {
                return false;
            }
        }
        return true;
    }

    /**
     * Sanitize SQL identifiers (table names, column names)
     */
    public String sanitizeSQLIdentifier(String identifier) {
        if (identifier == null || identifier.isEmpty()) {
            return identifier;
        }

        // Allow only alphanumeric and underscore
        if (!identifier.matches("^[a-zA-Z_][a-zA-Z0-9_]*$")) {
            throw new XSSValidationException("Invalid SQL identifier");
        }

        // Check against SQL injection patterns
        for (Pattern pattern : SQL_PATTERNS) {
            if (pattern.matcher(identifier).find()) {
                throw new XSSValidationException("Potential SQL injection detected");
            }
        }

        return identifier;
    }

    // Private helper methods

    private boolean containsMaliciousPattern(String input) {
        String lowerInput = input.toLowerCase();
        
        // Check XSS patterns
        for (Pattern pattern : XSS_PATTERNS) {
            if (pattern.matcher(input).find()) {
                log.debug("XSS pattern detected: {}", pattern.pattern());
                return true;
            }
        }
        
        // Check SQL injection patterns
        for (Pattern pattern : SQL_PATTERNS) {
            if (pattern.matcher(input).find()) {
                log.debug("SQL injection pattern detected: {}", pattern.pattern());
                return true;
            }
        }
        
        // Check command injection patterns
        for (Pattern pattern : COMMAND_PATTERNS) {
            if (pattern.matcher(input).find()) {
                log.debug("Command injection pattern detected: {}", pattern.pattern());
                return true;
            }
        }
        
        return false;
    }

    private JsonNode sanitizeJsonNode(JsonNode node) {
        if (node.isTextual()) {
            String sanitized = sanitizeInput(node.asText());
            return new TextNode(sanitized);
        } else if (node.isObject()) {
            ObjectNode sanitized = objectMapper.createObjectNode();
            node.fields().forEachRemaining(entry -> {
                String key = sanitizeInput(entry.getKey());
                JsonNode value = sanitizeJsonNode(entry.getValue());
                sanitized.set(key, value);
            });
            return sanitized;
        } else if (node.isArray()) {
            var sanitized = objectMapper.createArrayNode();
            node.forEach(element -> sanitized.add(sanitizeJsonNode(element)));
            return sanitized;
        }
        return node;
    }

    private String truncateForLogging(String input) {
        if (input.length() > 100) {
            return input.substring(0, 100) + "...";
        }
        return input;
    }

    /**
     * XSS Validation Exception
     */
    public static class XSSValidationException extends RuntimeException {
        public XSSValidationException(String message) {
            super(message);
        }
    }

    /**
     * CSP Configuration
     */
    public static class CSPConfig {
        private String defaultSrc = "'self'";
        private String scriptSrc = "'self'";
        private String styleSrc = "'self'";
        private String imgSrc = "'self' data: https:";
        private String connectSrc = "'self'";
        private String formAction = "'self'";
        private boolean allowInlineScripts = false;
        private boolean allowInlineStyles = false;
        private String nonce;
        private boolean upgradeInsecureRequests = true;
        private boolean blockAllMixedContent = true;

        // Getters
        public String getDefaultSrc() { return defaultSrc; }
        public String getScriptSrc() { return scriptSrc; }
        public String getStyleSrc() { return styleSrc; }
        public String getImgSrc() { return imgSrc; }
        public String getConnectSrc() { return connectSrc; }
        public String getFormAction() { return formAction; }
        public boolean isAllowInlineScripts() { return allowInlineScripts; }
        public boolean isAllowInlineStyles() { return allowInlineStyles; }
        public String getNonce() { return nonce; }
        public boolean isUpgradeInsecureRequests() { return upgradeInsecureRequests; }
        public boolean isBlockAllMixedContent() { return blockAllMixedContent; }

        // Builder pattern setters
        public CSPConfig setDefaultSrc(String defaultSrc) {
            this.defaultSrc = defaultSrc;
            return this;
        }
        public CSPConfig setScriptSrc(String scriptSrc) {
            this.scriptSrc = scriptSrc;
            return this;
        }
        public CSPConfig setStyleSrc(String styleSrc) {
            this.styleSrc = styleSrc;
            return this;
        }
        public CSPConfig setImgSrc(String imgSrc) {
            this.imgSrc = imgSrc;
            return this;
        }
        public CSPConfig setConnectSrc(String connectSrc) {
            this.connectSrc = connectSrc;
            return this;
        }
        public CSPConfig setFormAction(String formAction) {
            this.formAction = formAction;
            return this;
        }
        public CSPConfig setAllowInlineScripts(boolean allow) {
            this.allowInlineScripts = allow;
            return this;
        }
        public CSPConfig setAllowInlineStyles(boolean allow) {
            this.allowInlineStyles = allow;
            return this;
        }
        public CSPConfig setNonce(String nonce) {
            this.nonce = nonce;
            return this;
        }
        public CSPConfig setUpgradeInsecureRequests(boolean upgrade) {
            this.upgradeInsecureRequests = upgrade;
            return this;
        }
        public CSPConfig setBlockAllMixedContent(boolean block) {
            this.blockAllMixedContent = block;
            return this;
        }
    }
}