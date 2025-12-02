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

/**
 * SQL Injection attack detector
 */
@Component
@Slf4j
public class SqlInjectionDetector implements AttackDetector {

    @Value("${rasp.detectors.sql-injection.enabled:true}")
    private boolean enabled;

    // SQL injection patterns
    private static final List<Pattern> SQL_INJECTION_PATTERNS = Arrays.asList(
        // Union-based injections
        Pattern.compile("(?i).*\\bunion\\b.*\\bselect\\b.*"),
        Pattern.compile("(?i).*\\bunion\\b.*\\ball\\b.*\\bselect\\b.*"),
        
        // Boolean-based blind injections
        Pattern.compile("(?i).*\\band\\b.*\\b1\\s*=\\s*1\\b.*"),
        Pattern.compile("(?i).*\\bor\\b.*\\b1\\s*=\\s*1\\b.*"),
        Pattern.compile("(?i).*'\\s*or\\s*'1'\\s*=\\s*'1.*"),
        Pattern.compile("(?i).*\"\\s*or\\s*\"1\"\\s*=\\s*\"1.*"),
        
        // Time-based blind injections
        Pattern.compile("(?i).*\\bwaitfor\\b.*\\bdelay\\b.*"),
        Pattern.compile("(?i).*\\bsleep\\s*\\(.*\\).*"),
        Pattern.compile("(?i).*\\bbenchmark\\s*\\(.*"),
        
        // Error-based injections
        Pattern.compile("(?i).*\\bextractvalue\\s*\\(.*"),
        Pattern.compile("(?i).*\\bupdatexml\\s*\\(.*"),
        Pattern.compile("(?i).*\\bcast\\s*\\(.*\\bas\\b.*"),
        
        // Stacked queries
        Pattern.compile("(?i).*;\\s*(select|insert|update|delete|drop|create)\\b.*"),
        
        // Comment-based evasion
        Pattern.compile("(?i).*/\\*.*\\*/.*"),
        Pattern.compile("(?i).*--.*"),
        Pattern.compile("(?i).*#.*"),
        
        // Function-based injections
        Pattern.compile("(?i).*\\bload_file\\s*\\(.*"),
        Pattern.compile("(?i).*\\binto\\b.*\\boutfile\\b.*"),
        Pattern.compile("(?i).*\\bchar\\s*\\(.*"),
        Pattern.compile("(?i).*\\bconcat\\s*\\(.*"),
        
        // Database-specific patterns
        Pattern.compile("(?i).*\\bxp_cmdshell\\b.*"), // SQL Server
        Pattern.compile("(?i).*\\bsp_executesql\\b.*"), // SQL Server
        Pattern.compile("(?i).*\\binformation_schema\\b.*"), // MySQL/PostgreSQL
        Pattern.compile("(?i).*\\bpg_sleep\\s*\\(.*"), // PostgreSQL
        Pattern.compile("(?i).*\\boracle\\b.*\\bdual\\b.*"), // Oracle
        
        // Encoded patterns
        Pattern.compile(".*%27.*%6f%72.*%27.*"), // 'or' encoded
        Pattern.compile(".*%22.*%6f%72.*%22.*"), // "or" encoded
        Pattern.compile(".*%3b.*%73%65%6c%65%63%74.*"), // ;select encoded
        
        // Advanced evasion techniques
        Pattern.compile("(?i).*\\bselect\\b.*\\bfrom\\b.*\\bwhere\\b.*"),
        Pattern.compile("(?i).*\\bdrop\\b.*\\btable\\b.*"),
        Pattern.compile("(?i).*\\bdelete\\b.*\\bfrom\\b.*"),
        Pattern.compile("(?i).*\\binsert\\b.*\\binto\\b.*"),
        Pattern.compile("(?i).*\\bupdate\\b.*\\bset\\b.*")
    );

    // SQL keywords that are suspicious in user input
    private static final List<String> SUSPICIOUS_SQL_KEYWORDS = Arrays.asList(
        "select", "union", "insert", "update", "delete", "drop", "create", "alter",
        "exec", "execute", "sp_", "xp_", "information_schema", "sys", "msdb",
        "waitfor", "delay", "sleep", "benchmark", "load_file", "outfile"
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
        ).toLowerCase();

        if (combinedInput.trim().isEmpty()) {
            return null;
        }

        // Check for SQL injection patterns
        for (Pattern pattern : SQL_INJECTION_PATTERNS) {
            if (pattern.matcher(combinedInput).matches()) {
                return createSecurityEvent(request, "SQL_INJECTION_PATTERN", 
                    "Detected SQL injection pattern: " + pattern.pattern(),
                    combinedInput, ThreatLevel.HIGH);
            }
        }

        // Check for suspicious keyword combinations
        int suspiciousKeywordCount = 0;
        for (String keyword : SUSPICIOUS_SQL_KEYWORDS) {
            if (combinedInput.contains(keyword)) {
                suspiciousKeywordCount++;
            }
        }

        if (suspiciousKeywordCount >= 3) {
            return createSecurityEvent(request, "SQL_INJECTION_KEYWORDS",
                "Multiple suspicious SQL keywords detected (" + suspiciousKeywordCount + ")",
                combinedInput, ThreatLevel.MEDIUM);
        }

        // Check for encoded SQL injection attempts
        if (containsEncodedSqlInjection(combinedInput)) {
            return createSecurityEvent(request, "SQL_INJECTION_ENCODED",
                "Encoded SQL injection attempt detected",
                combinedInput, ThreatLevel.HIGH);
        }

        return null;
    }

    private boolean containsEncodedSqlInjection(String input) {
        // Check for URL-encoded SQL injection patterns
        String[] encodedPatterns = {
            "%27%20or%20", // ' or 
            "%22%20or%20", // " or 
            "%3b%73%65%6c%65%63%74", // ;select
            "%27%29%3b", // ');
            "%3c%73%63%72%69%70%74", // <script
            "%75%6e%69%6f%6e", // union
            "%64%72%6f%70" // drop
        };

        for (String pattern : encodedPatterns) {
            if (input.contains(pattern)) {
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
            .sqlInjectionVector(payload)
            .requestSize(request.getRequestSize())
            .contentType(request.getContentType())
            .build();
    }

    @Override
    public String getDetectorName() {
        return "SQL_INJECTION_DETECTOR";
    }

    @Override
    public boolean isEnabled() {
        return enabled;
    }

    @Override
    public int getPriority() {
        return 10; // High priority
    }
}