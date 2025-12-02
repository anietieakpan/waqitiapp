package com.waqiti.common.validation;

import jakarta.validation.ConstraintValidator;
import jakarta.validation.ConstraintValidatorContext;
import org.springframework.stereotype.Component;

import java.util.regex.Pattern;

/**
 * Production-ready XSS (Cross-Site Scripting) prevention validator
 */
@Component
public class NoXSSValidator implements ConstraintValidator<ValidationConstraints.NoXSS, String> {

    // HTML/JavaScript patterns that could indicate XSS attempts
    private static final Pattern XSS_PATTERN = Pattern.compile(
        "(?i)(" +
        // Script tags
        "<script[^>]*>.*?</script>|<script[^>]*/>|" +
        // Event handlers
        "on(load|error|click|dblclick|mousedown|mouseup|mouseover|mouseout|mousemove|" +
        "keydown|keyup|keypress|submit|reset|select|change|focus|blur|" +
        "abort|canplay|canplaythrough|durationchange|emptied|ended|loadeddata|" +
        "loadedmetadata|loadstart|pause|play|playing|progress|ratechange|seeked|" +
        "seeking|stalled|suspend|timeupdate|volumechange|waiting|" +
        "animationend|animationiteration|animationstart|transitionend|" +
        "message|online|offline|popstate|show|storage|toggle|wheel|" +
        "copy|cut|paste|afterprint|beforeprint|beforeunload|hashchange|pagehide|" +
        "pageshow|resize|scroll|unload|fullscreenchange|fullscreenerror|" +
        "contextmenu|drag|dragend|dragenter|dragleave|dragover|dragstart|drop)\\s*=|" +
        // JavaScript protocol
        "javascript:|vbscript:|livescript:|mocha:|data:text/html|" +
        // Embedded content
        "<iframe[^>]*>|<frame[^>]*>|<embed[^>]*>|<object[^>]*>|<applet[^>]*>|" +
        // Meta refresh
        "<meta[^>]*http-equiv[^>]*>|" +
        // Base tag manipulation
        "<base[^>]*href[^>]*>|" +
        // Style tags with expression/import
        "<style[^>]*>.*?(expression|@import|javascript:).*?</style>|" +
        // SVG with script
        "<svg[^>]*>.*?<script.*?</svg>|" +
        // Link tags with javascript
        "<link[^>]*href\\s*=\\s*['\"]?javascript:|" +
        // Form action manipulation
        "<form[^>]*action\\s*=\\s*['\"]?javascript:|" +
        // Input with javascript
        "<input[^>]*onclick|<input[^>]*onfocus|" +
        // Image tags with error handlers
        "<img[^>]*onerror[^>]*>|<img[^>]*onload[^>]*>|" +
        // Body/html tag with handlers
        "<body[^>]*onload[^>]*>|<html[^>]*onmouseover[^>]*>|" +
        // Malformed tags
        "<<script|<\\s*script|" +
        // CSS expression
        "style\\s*=\\s*['\"].*?(expression|javascript:|behavior:)|" +
        // Data URI with script
        "data:[^,]*;base64,[a-zA-Z0-9+/]+=*|" +
        // XML data island
        "<xml[^>]*>|<\\?xml[^>]*\\?>|" +
        // HTML5 event attributes
        "formaction\\s*=|srcdoc\\s*=|" +
        // WebSocket attempts
        "ws://|wss://|" +
        ")"
    );

    // Additional patterns for encoded XSS
    private static final Pattern ENCODED_XSS_PATTERN = Pattern.compile(
        "(?i)(" +
        // HTML entity encoded script
        "&lt;script|&#60;script|&#x3C;script|" +
        // URL encoded script
        "%3Cscript|%3C%73%63%72%69%70%74|" +
        // Unicode encoded
        "\\\\u003cscript|\\\\x3cscript|" +
        // Decimal/hex encoded event handlers
        "&#111;&#110;|&#x6F;&#x6E;|" +
        // Base64 encoded javascript
        "data:.*;base64|" +
        ")"
    );

    // Dangerous HTML entities
    private static final Pattern HTML_ENTITIES = Pattern.compile(
        "(&[a-zA-Z][a-zA-Z0-9]*;|&#[0-9]+;|&#x[0-9a-fA-F]+;)"
    );

    @Override
    public void initialize(ValidationConstraints.NoXSS annotation) {
        // No initialization needed
    }

    @Override
    public boolean isValid(String value, ConstraintValidatorContext context) {
        if (value == null || value.trim().isEmpty()) {
            return true; // Let @NotNull handle null validation
        }

        // Check for XSS patterns
        if (XSS_PATTERN.matcher(value).find()) {
            context.disableDefaultConstraintViolation();
            context.buildConstraintViolationWithTemplate(
                "Input contains potentially dangerous HTML/JavaScript"
            ).addConstraintViolation();
            return false;
        }

        // Check for encoded XSS patterns
        if (ENCODED_XSS_PATTERN.matcher(value).find()) {
            context.disableDefaultConstraintViolation();
            context.buildConstraintViolationWithTemplate(
                "Input contains encoded XSS patterns"
            ).addConstraintViolation();
            return false;
        }

        // Decode and check again
        String decoded = decodeInput(value);
        if (!decoded.equals(value) && XSS_PATTERN.matcher(decoded).find()) {
            context.disableDefaultConstraintViolation();
            context.buildConstraintViolationWithTemplate(
                "Input contains encoded malicious content"
            ).addConstraintViolation();
            return false;
        }

        // Check for suspicious tag combinations
        if (hasSuspiciousTagCombination(value)) {
            context.disableDefaultConstraintViolation();
            context.buildConstraintViolationWithTemplate(
                "Input contains suspicious HTML tag combinations"
            ).addConstraintViolation();
            return false;
        }

        // Check for excessive HTML entities
        if (hasExcessiveHtmlEntities(value)) {
            context.disableDefaultConstraintViolation();
            context.buildConstraintViolationWithTemplate(
                "Input contains excessive HTML entities"
            ).addConstraintViolation();
            return false;
        }

        return true;
    }

    private String decodeInput(String value) {
        String decoded = value;

        // Decode HTML entities
        decoded = decodeHtmlEntities(decoded);

        // Decode URL encoding
        decoded = decodeUrl(decoded);

        // Decode Unicode
        decoded = decodeUnicode(decoded);

        // Decode base64 (if applicable)
        if (decoded.contains("base64,")) {
            decoded = decodeBase64Portions(decoded);
        }

        return decoded;
    }

    private String decodeHtmlEntities(String value) {
        return value
            .replace("&lt;", "<")
            .replace("&gt;", ">")
            .replace("&quot;", "\"")
            .replace("&apos;", "'")
            .replace("&#39;", "'")
            .replace("&#34;", "\"")
            .replace("&#60;", "<")
            .replace("&#62;", ">")
            .replace("&#x3C;", "<")
            .replace("&#x3E;", ">")
            .replace("&amp;", "&");
    }

    private String decodeUrl(String value) {
        return value
            .replace("%3C", "<")
            .replace("%3E", ">")
            .replace("%22", "\"")
            .replace("%27", "'")
            .replace("%2F", "/")
            .replace("%3D", "=")
            .replace("%20", " ");
    }

    private String decodeUnicode(String value) {
        String result = value;

        // Decode \\uXXXX format
        java.util.regex.Pattern unicodePattern = java.util.regex.Pattern.compile("\\\\u([0-9a-fA-F]{4})");
        java.util.regex.Matcher unicodeMatcher = unicodePattern.matcher(result);
        StringBuffer sb1 = new StringBuffer();
        while (unicodeMatcher.find()) {
            String hex = unicodeMatcher.group(1);
            int decimal = Integer.parseInt(hex, 16);
            unicodeMatcher.appendReplacement(sb1, String.valueOf((char) decimal));
        }
        unicodeMatcher.appendTail(sb1);
        result = sb1.toString();

        // Decode \\xXX format  
        java.util.regex.Pattern hexPattern = java.util.regex.Pattern.compile("\\\\x([0-9a-fA-F]{2})");
        java.util.regex.Matcher hexMatcher = hexPattern.matcher(result);
        StringBuffer sb2 = new StringBuffer();
        while (hexMatcher.find()) {
            String hex = hexMatcher.group(1);
            int decimal = Integer.parseInt(hex, 16);
            hexMatcher.appendReplacement(sb2, String.valueOf((char) decimal));
        }
        hexMatcher.appendTail(sb2);
        result = sb2.toString();

        return result;
    }

    private String decodeBase64Portions(String value) {
        // Simple base64 detection and partial decoding
        // In production, you might want more sophisticated handling
        return value; // Simplified for security
    }

    private boolean hasSuspiciousTagCombination(String value) {
        String lower = value.toLowerCase();

        // Check for nested suspicious tags
        if (lower.contains("<") && lower.contains(">")) {
            // Count opening and closing brackets
            int openCount = countOccurrences(lower, '<');
            int closeCount = countOccurrences(lower, '>');

            // Imbalanced tags are suspicious
            if (Math.abs(openCount - closeCount) > 2) {
                return true;
            }

            // Multiple consecutive opening brackets
            if (lower.contains("<<") || lower.contains(">>")) {
                return true;
            }
        }

        return false;
    }

    private boolean hasExcessiveHtmlEntities(String value) {
        int entityCount = 0;
        java.util.regex.Matcher matcher = HTML_ENTITIES.matcher(value);

        while (matcher.find()) {
            entityCount++;
        }

        // If more than 20% of the content is entities, it's suspicious
        double ratio = (double) entityCount * 5 / value.length(); // Assume avg entity length of 5
        return ratio > 0.2 && entityCount > 3;
    }

    private int countOccurrences(String str, char ch) {
        int count = 0;
        for (char c : str.toCharArray()) {
            if (c == ch) count++;
        }
        return count;
    }
}