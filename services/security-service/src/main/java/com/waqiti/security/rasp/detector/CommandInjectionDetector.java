package com.waqiti.security.rasp.detector;

import com.waqiti.security.rasp.RaspRequestWrapper;
import com.waqiti.security.rasp.model.SecurityEvent;
import com.waqiti.security.rasp.model.ThreatLevel;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.util.Arrays;
import java.util.List;
import java.util.regex.Pattern;

/**
 * Command injection attack detector
 */
@Component
@Slf4j
public class CommandInjectionDetector implements AttackDetector {

    @Value("${rasp.detectors.command-injection.enabled:true}")
    private boolean enabled;

    // Command injection patterns
    private static final List<Pattern> COMMAND_INJECTION_PATTERNS = Arrays.asList(
        // Command chaining
        Pattern.compile(".*[;&|]+.*"),
        Pattern.compile(".*&&.*"),
        Pattern.compile(".*\\|\\|.*"),
        
        // Command substitution
        Pattern.compile(".*`.*`.*"),
        Pattern.compile(".*\\$\\(.*\\).*"),
        
        // Redirection operators
        Pattern.compile(".*>.*"),
        Pattern.compile(".*<.*"),
        Pattern.compile(".*>>.*"),
        
        // Common dangerous commands (Unix/Linux)
        Pattern.compile("(?i).*(^|\\s)(cat|ls|pwd|whoami|id|uname|ps|netstat|ifconfig|ping|wget|curl|nc|telnet|ssh|ftp)\\s.*"),
        Pattern.compile("(?i).*(^|\\s)(rm|mv|cp|chmod|chown|kill|sudo|su|passwd|mount|umount)\\s.*"),
        Pattern.compile("(?i).*(^|\\s)(echo|printf|head|tail|grep|awk|sed|sort|cut|tr)\\s.*"),
        Pattern.compile("(?i).*(^|\\s)(find|locate|which|whereis|type|file)\\s.*"),
        
        // Windows commands
        Pattern.compile("(?i).*(^|\\s)(dir|type|copy|del|ren|md|rd|cd|cls|exit)\\s.*"),
        Pattern.compile("(?i).*(^|\\s)(net|sc|tasklist|taskkill|systeminfo|ipconfig|ping)\\s.*"),
        Pattern.compile("(?i).*(^|\\s)(powershell|cmd|command|wmic|reg)\\s.*"),
        
        // Script interpreters
        Pattern.compile("(?i).*(^|\\s)(python|perl|ruby|php|node|java|bash|sh|zsh|csh|tcsh)\\s.*"),
        
        // Environment variables
        Pattern.compile(".*\\$[A-Za-z_][A-Za-z0-9_]*.*"),
        Pattern.compile(".*%[A-Za-z_][A-Za-z0-9_]*%.*"),
        
        // Special characters often used in command injection
        Pattern.compile(".*[\\n\\r].*"),
        Pattern.compile(".*\\x00.*"), // Null byte
        
        // Base64 encoded commands (common evasion)
        Pattern.compile(".*[A-Za-z0-9+/]{20,}={0,2}.*") // Potential base64
    );

    // Dangerous command keywords
    private static final List<String> DANGEROUS_COMMANDS = Arrays.asList(
        // Unix/Linux commands
        "cat", "ls", "pwd", "whoami", "id", "uname", "ps", "netstat", "ifconfig",
        "ping", "wget", "curl", "nc", "telnet", "ssh", "ftp", "rm", "mv", "cp",
        "chmod", "chown", "kill", "sudo", "su", "passwd", "mount", "umount",
        "echo", "printf", "head", "tail", "grep", "awk", "sed", "sort", "cut", "tr",
        "find", "locate", "which", "whereis", "type", "file",
        
        // Windows commands
        "dir", "type", "copy", "del", "ren", "md", "rd", "cd", "cls", "exit",
        "net", "sc", "tasklist", "taskkill", "systeminfo", "ipconfig",
        "powershell", "cmd", "command", "wmic", "reg",
        
        // Script interpreters
        "python", "perl", "ruby", "php", "node", "java", "bash", "sh", "zsh", "csh", "tcsh"
    );

    // Command injection operators
    private static final List<String> INJECTION_OPERATORS = Arrays.asList(
        ";", "&", "|", "&&", "||", "`", "$(", ">", "<", ">>", "<<", "2>", "2>>", "&>"
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

        // Check for command injection patterns
        for (Pattern pattern : COMMAND_INJECTION_PATTERNS) {
            if (pattern.matcher(combinedInput).matches()) {
                return createSecurityEvent(request, "COMMAND_INJECTION_PATTERN", 
                    "Detected command injection pattern: " + pattern.pattern(),
                    combinedInput, ThreatLevel.HIGH);
            }
        }

        // Check for dangerous commands combined with injection operators
        if (containsDangerousCommandWithOperator(combinedInput)) {
            return createSecurityEvent(request, "COMMAND_INJECTION_COMBO",
                "Dangerous command with injection operator detected",
                combinedInput, ThreatLevel.CRITICAL);
        }

        // Check for encoded command injection
        if (containsEncodedCommandInjection(combinedInput)) {
            return createSecurityEvent(request, "COMMAND_INJECTION_ENCODED",
                "Encoded command injection attempt detected",
                combinedInput, ThreatLevel.HIGH);
        }

        // Check for suspicious command sequences
        int commandCount = countSuspiciousCommands(combinedInput);
        if (commandCount >= 3) {
            return createSecurityEvent(request, "COMMAND_INJECTION_MULTIPLE",
                "Multiple suspicious commands detected (" + commandCount + ")",
                combinedInput, ThreatLevel.MEDIUM);
        }

        return null;
    }

    private boolean containsDangerousCommandWithOperator(String input) {
        String lowerInput = input.toLowerCase();
        
        // Check if input contains both dangerous commands and injection operators
        boolean hasCommand = false;
        boolean hasOperator = false;
        
        for (String command : DANGEROUS_COMMANDS) {
            if (lowerInput.contains(command)) {
                hasCommand = true;
                break;
            }
        }
        
        for (String operator : INJECTION_OPERATORS) {
            if (lowerInput.contains(operator)) {
                hasOperator = true;
                break;
            }
        }
        
        return hasCommand && hasOperator;
    }

    private boolean containsEncodedCommandInjection(String input) {
        // Check for URL-encoded command injection patterns
        String[] encodedPatterns = {
            "%3b", // ;
            "%26", // &
            "%7c", // |
            "%60", // `
            "%24%28", // $(
            "%3e", // >
            "%3c", // <
            "%0a", // newline
            "%0d", // carriage return
            "%00" // null byte
        };

        String lowerInput = input.toLowerCase();
        for (String pattern : encodedPatterns) {
            if (lowerInput.contains(pattern)) {
                return true;
            }
        }

        return false;
    }

    private int countSuspiciousCommands(String input) {
        String lowerInput = input.toLowerCase();
        int count = 0;
        
        for (String command : DANGEROUS_COMMANDS) {
            if (lowerInput.contains(command)) {
                count++;
            }
        }
        
        return count;
    }

    private SecurityEvent createSecurityEvent(RaspRequestWrapper request, String threatType, 
                                            String description, String payload, ThreatLevel level) {
        return SecurityEvent.builder()
            .threatType(threatType)
            .description(description)
            .attackPayload(payload.length() > 1000 ? payload.substring(0, 1000) + "..." : payload)
            .detectorName(getDetectorName())
            .threatLevel(level)
            .commandInjectionVector(payload)
            .requestSize(request.getRequestSize())
            .contentType(request.getContentType())
            .build();
    }

    @Override
    public String getDetectorName() {
        return "COMMAND_INJECTION_DETECTOR";
    }

    @Override
    public boolean isEnabled() {
        return enabled;
    }

    @Override
    public int getPriority() {
        return 8; // High priority
    }
}