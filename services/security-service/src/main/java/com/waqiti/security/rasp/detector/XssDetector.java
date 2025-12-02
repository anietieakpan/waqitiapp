package com.waqiti.security.rasp.detector;

import com.waqiti.security.rasp.RaspRequestWrapper;
import com.waqiti.security.rasp.model.SecurityEvent;
import com.waqiti.security.rasp.model.ThreatLevel;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.util.regex.Pattern;
import java.util.Arrays;
import java.util.List;
import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;

/**
 * Cross-Site Scripting (XSS) attack detector
 */
@Component
@Slf4j
public class XssDetector implements AttackDetector {

    @Value("${rasp.detectors.xss.enabled:true}")
    private boolean enabled;

    // XSS attack patterns
    private static final List<Pattern> XSS_PATTERNS = Arrays.asList(
        // Basic script tags
        Pattern.compile("(?i).*<\\s*script[^>]*>.*"),
        Pattern.compile("(?i).*</\\s*script\\s*>.*"),
        
        // Event handlers
        Pattern.compile("(?i).*\\bon\\w+\\s*=.*"),
        Pattern.compile("(?i).*\\bonload\\s*=.*"),
        Pattern.compile("(?i).*\\bonclick\\s*=.*"),
        Pattern.compile("(?i).*\\bonmouseover\\s*=.*"),
        Pattern.compile("(?i).*\\bonerror\\s*=.*"),
        
        // JavaScript protocols
        Pattern.compile("(?i).*javascript\\s*:.*"),
        Pattern.compile("(?i).*vbscript\\s*:.*"),
        Pattern.compile("(?i).*data\\s*:.*text/html.*"),
        
        // HTML entities and encodings
        Pattern.compile(".*&#x?[0-9a-f]+;.*"),
        Pattern.compile(".*%3[cC].*%3[eE].*"), // < and > encoded
        
        // Common XSS vectors
        Pattern.compile("(?i).*<\\s*iframe[^>]*>.*"),
        Pattern.compile("(?i).*<\\s*object[^>]*>.*"),
        Pattern.compile("(?i).*<\\s*embed[^>]*>.*"),
        Pattern.compile("(?i).*<\\s*form[^>]*>.*"),
        Pattern.compile("(?i).*<\\s*input[^>]*>.*"),
        Pattern.compile("(?i).*<\\s*img[^>]*onerror.*"),
        Pattern.compile("(?i).*<\\s*svg[^>]*>.*"),
        
        // Advanced XSS techniques
        Pattern.compile("(?i).*eval\\s*\\(.*"),
        Pattern.compile("(?i).*settimeout\\s*\\(.*"),
        Pattern.compile("(?i).*setinterval\\s*\\(.*"),
        Pattern.compile("(?i).*document\\.write.*"),
        Pattern.compile("(?i).*document\\.cookie.*"),
        Pattern.compile("(?i).*window\\.location.*"),
        Pattern.compile("(?i).*alert\\s*\\(.*"),
        Pattern.compile("(?i).*confirm\\s*\\(.*"),
        Pattern.compile("(?i).*prompt\\s*\\(.*"),
        
        // CSS-based XSS
        Pattern.compile("(?i).*expression\\s*\\(.*"),
        Pattern.compile("(?i).*@import.*"),
        Pattern.compile("(?i).*binding\\s*:.*"),
        
        // DOM-based XSS patterns
        Pattern.compile("(?i).*innerhtml.*"),
        Pattern.compile("(?i).*outerhtml.*"),
        Pattern.compile("(?i).*createelement.*"),
        
        // Filter evasion techniques
        Pattern.compile("(?i).*\\x3c.*\\x3e.*"), // Hex encoded < >
        Pattern.compile("(?i).*\\u003c.*\\u003e.*"), // Unicode encoded < >
        Pattern.compile("(?i).*fromcharcode.*"),
        
        // Attribute-based XSS
        Pattern.compile("(?i).*style\\s*=.*expression.*"),
        Pattern.compile("(?i).*href\\s*=.*javascript.*"),
        Pattern.compile("(?i).*src\\s*=.*javascript.*")
    );

    // Dangerous HTML tags
    private static final List<String> DANGEROUS_TAGS = Arrays.asList(
        "script", "iframe", "object", "embed", "form", "meta", "link", "style",
        "svg", "math", "details", "template", "slot"
    );

    // Dangerous attributes
    private static final List<String> DANGEROUS_ATTRIBUTES = Arrays.asList(
        "onload", "onclick", "onmouseover", "onerror", "onsubmit", "onchange",
        "onfocus", "onblur", "onkeyup", "onkeydown", "onkeypress", "onselect",
        "onreset", "onabort", "ondragstart", "ondrop", "onpaste"
    );

    @Override
    public SecurityEvent detectThreat(RaspRequestWrapper request) {
        if (!enabled) {
            return null;
        }

        String body = request.getBody();
        String queryString = request.getQueryString();
        String allParams = request.getAllParametersAsString();
        String uri = request.getRequestURI();

        // Combine all input sources for analysis
        String combinedInput = String.join(" ", 
            body != null ? body : "",
            queryString != null ? queryString : "",
            allParams != null ? allParams : "",
            uri != null ? uri : ""
        );

        if (combinedInput.trim().isEmpty()) {
            return null;
        }

        // Decode URL-encoded content for better detection
        String decodedInput = urlDecode(combinedInput);
        
        // Check for XSS patterns
        for (Pattern pattern : XSS_PATTERNS) {
            if (pattern.matcher(decodedInput).matches()) {
                return createSecurityEvent(request, "XSS_PATTERN", 
                    "Detected XSS pattern: " + pattern.pattern(),
                    decodedInput, ThreatLevel.HIGH);
            }
        }

        // Check for dangerous HTML tags
        for (String tag : DANGEROUS_TAGS) {
            if (decodedInput.toLowerCase().contains("<" + tag) || 
                decodedInput.toLowerCase().contains("</" + tag)) {
                return createSecurityEvent(request, "XSS_DANGEROUS_TAG",
                    "Dangerous HTML tag detected: " + tag,
                    decodedInput, ThreatLevel.MEDIUM);
            }
        }

        // Check for dangerous attributes
        for (String attr : DANGEROUS_ATTRIBUTES) {
            if (decodedInput.toLowerCase().contains(attr + "=")) {
                return createSecurityEvent(request, "XSS_DANGEROUS_ATTRIBUTE",
                    "Dangerous HTML attribute detected: " + attr,
                    decodedInput, ThreatLevel.MEDIUM);
            }
        }

        // Check for encoded XSS attempts
        if (containsEncodedXss(combinedInput)) {
            return createSecurityEvent(request, "XSS_ENCODED",
                "Encoded XSS attempt detected",
                combinedInput, ThreatLevel.HIGH);
        }

        // Check for polyglot XSS (works in multiple contexts)
        if (isPolyglotXss(decodedInput)) {
            return createSecurityEvent(request, "XSS_POLYGLOT",
                "Polyglot XSS attack detected",
                decodedInput, ThreatLevel.CRITICAL);
        }

        return null;
    }

    private String urlDecode(String input) {
        try {
            // Decode multiple times to catch multiple encoding layers
            String decoded = input;
            for (int i = 0; i < 3; i++) {
                String newDecoded = URLDecoder.decode(decoded, StandardCharsets.UTF_8);
                if (newDecoded.equals(decoded)) {
                    break;
                }
                decoded = newDecoded;
            }
            return decoded;
        } catch (Exception e) {
            return input; // Return original if decoding fails
        }
    }

    private boolean containsEncodedXss(String input) {
        // Check for various XSS encoding patterns
        String[] encodedPatterns = {
            "%3C%73%63%72%69%70%74%3E", // <script>
            "%3C%2F%73%63%72%69%70%74%3E", // </script>
            "&#60;script&#62;", // <script> in HTML entities
            "&lt;script&gt;", // <script> in HTML entities
            "%6A%61%76%61%73%63%72%69%70%74", // javascript
            "\\x3cscript\\x3e", // <script> in hex
            "\\u003cscript\\u003e", // <script> in unicode
            "%22%3E%3Cscript%3E", // "><script>
            "%27%3E%3Cscript%3E" // '><script>
        };

        String lowerInput = input.toLowerCase();
        for (String pattern : encodedPatterns) {
            if (lowerInput.contains(pattern.toLowerCase())) {
                return true;
            }
        }

        return false;
    }

    private boolean isPolyglotXss(String input) {
        // Detect polyglot XSS payloads that work in multiple contexts
        String[] polyglotPatterns = {
            "jaVasCript:/*-/*`/*\\`/*'/*\"/**/(/* */oNcliCk=alert() )//",
            "';alert(String.fromCharCode(88,83,83))//';alert(String.fromCharCode(88,83,83))//\"",
            "\"><script>alert('XSS')</script>",
            "javascript:alert('XSS')",
            "'><svg/onload=alert(/XSS/)>",
            "\"autofocus onfocus=alert(1)//"
        };

        String lowerInput = input.toLowerCase();
        for (String pattern : polyglotPatterns) {
            if (lowerInput.contains(pattern.toLowerCase())) {
                return true;
            }
        }

        return false;
    }

    private SecurityEvent createSecurityEvent(RaspRequestWrapper request, String threatType, 
                                            String description, String payload, ThreatLevel level) {
        return SecurityEvent.builder()
            .threatType(threatType)
            .description(description)
            .attackPayload(payload.length() > 1000 ? payload.substring(0, 1000) + "..." : payload)
            .detectorName(getDetectorName())
            .threatLevel(level)
            .xssVector(payload)
            .requestSize(request.getRequestSize())
            .contentType(request.getContentType())
            .build();
    }

    @Override
    public String getDetectorName() {
        return "XSS_DETECTOR";
    }

    @Override
    public boolean isEnabled() {
        return enabled;
    }

    @Override
    public int getPriority() {
        return 9; // High priority
    }
}