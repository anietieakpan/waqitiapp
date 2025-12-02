package com.waqiti.common.security;

import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.io.BufferedReader;
import java.io.File;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.*;
import java.util.concurrent.TimeUnit;
import java.util.regex.Pattern;

/**
 * CRITICAL SECURITY FIX - SecureSystemUtils
 * Secure replacement for Runtime.exec() and ProcessBuilder with input sanitization
 * and strict command validation to prevent RCE vulnerabilities
 */
@Component
@Slf4j
public class SecureSystemUtils {
    
    // Whitelist of allowed commands and their safe patterns
    private static final Map<String, Pattern> ALLOWED_COMMANDS = Map.of(
        "ls", Pattern.compile("^ls\\s+(/[a-zA-Z0-9/_.-]+)$"),
        "wmic", Pattern.compile("^wmic\\s+/namespace:\\\\\\\\root\\\\cimv2\\\\security\\\\microsofttpm\\s+path\\s+win32_tpm\\s+get\\s+IsActivated_InitialValue$"),
        "python", Pattern.compile("^python[0-9.]*\\s+[a-zA-Z0-9/_.-]+\\.py(\\s+[a-zA-Z0-9/_.-]+)*$"),
        "python3", Pattern.compile("^python3[0-9.]*\\s+[a-zA-Z0-9/_.-]+\\.py(\\s+[a-zA-Z0-9/_.-]+)*$")
    );
    
    // Maximum execution time for commands
    private static final int MAX_EXECUTION_TIME_SECONDS = 30;
    
    /**
     * Secure TPM check for Windows systems
     */
    public boolean isWindowsTPMAvailable() {
        try {
            // Use JNA or direct file system checks instead of Runtime.exec
            return checkWindowsTPMSecure();
        } catch (Exception e) {
            log.warn("TPM check failed safely: {}", e.getMessage());
            return false;
        }
    }
    
    /**
     * Secure TPM check for Linux systems
     */
    public boolean isLinuxTPMAvailable() {
        try {
            // Use direct file system check instead of Runtime.exec
            Path tpmDevice = Paths.get("/dev/tpm0");
            return Files.exists(tpmDevice) && Files.isReadable(tpmDevice);
        } catch (Exception e) {
            log.warn("TPM check failed safely: {}", e.getMessage());
            return false;
        }
    }
    
    /**
     * Secure Python execution for ML models with strict validation
     */
    public SecureExecutionResult executePythonScript(String scriptPath, List<String> arguments) {
        try {
            // Validate script path
            if (!isValidPythonScript(scriptPath)) {
                return SecureExecutionResult.error("Invalid script path: " + scriptPath);
            }
            
            // Validate arguments
            List<String> sanitizedArgs = sanitizeArguments(arguments);
            
            // Build secure command
            List<String> command = new ArrayList<>();
            command.add(getPythonExecutable());
            command.add(scriptPath);
            command.addAll(sanitizedArgs);
            
            return executeSecureCommand(command);
            
        } catch (Exception e) {
            log.error("Secure Python execution failed: {}", e.getMessage());
            return SecureExecutionResult.error("Execution failed: " + e.getMessage());
        }
    }
    
    /**
     * Secure command execution with strict validation and sandboxing
     */
    public SecureExecutionResult executeSecureCommand(List<String> command) {
        if (command == null || command.isEmpty()) {
            return SecureExecutionResult.error("Empty command");
        }
        
        try {
            // Validate command against whitelist
            if (!isCommandAllowed(command)) {
                log.error("SECURITY: Blocked potentially dangerous command: {}", command);
                return SecureExecutionResult.error("Command not allowed");
            }
            
            // Create secure ProcessBuilder
            ProcessBuilder pb = new ProcessBuilder(command);
            
            // Security hardening
            pb.environment().clear(); // Clear all environment variables
            pb.environment().put("PATH", getSecurePath()); // Minimal PATH
            pb.directory(getTempDirectory()); // Secure working directory
            pb.redirectErrorStream(true);
            
            log.info("Executing secure command: {}", String.join(" ", command));
            
            Process process = pb.start();
            
            // Capture output with size limits
            StringBuilder output = new StringBuilder();
            try (BufferedReader reader = new BufferedReader(
                    new InputStreamReader(process.getInputStream(), StandardCharsets.UTF_8))) {
                
                String line;
                int lineCount = 0;
                while ((line = reader.readLine()) != null && lineCount < 100) {
                    if (output.length() + line.length() > 10000) { // 10KB limit
                        break;
                    }
                    output.append(line).append("\n");
                    lineCount++;
                }
            }
            
            // Wait for completion with timeout
            boolean finished = process.waitFor(MAX_EXECUTION_TIME_SECONDS, TimeUnit.SECONDS);
            
            if (!finished) {
                process.destroyForcibly();
                return SecureExecutionResult.error("Command timed out");
            }
            
            int exitCode = process.exitValue();
            String outputStr = output.toString();
            
            log.debug("Command completed: exitCode={}, outputLength={}", exitCode, outputStr.length());
            
            return new SecureExecutionResult(exitCode == 0, exitCode, outputStr, null);
            
        } catch (Exception e) {
            log.error("Secure command execution failed: {}", e.getMessage());
            return SecureExecutionResult.error("Execution failed: " + e.getMessage());
        }
    }
    
    /**
     * Check if command is in the allowed whitelist
     */
    private boolean isCommandAllowed(List<String> command) {
        if (command.isEmpty()) return false;
        
        String fullCommand = String.join(" ", command);
        String baseCommand = command.get(0);
        
        // Check if base command is allowed
        if (!ALLOWED_COMMANDS.containsKey(baseCommand)) {
            return false;
        }
        
        // Validate against pattern
        Pattern pattern = ALLOWED_COMMANDS.get(baseCommand);
        return pattern.matcher(fullCommand).matches();
    }
    
    /**
     * Validate Python script path
     */
    private boolean isValidPythonScript(String scriptPath) {
        if (scriptPath == null || scriptPath.trim().isEmpty()) {
            return false;
        }
        
        Path path = Paths.get(scriptPath);
        
        // Must be absolute path
        if (!path.isAbsolute()) {
            return false;
        }
        
        // Must exist and be readable
        if (!Files.exists(path) || !Files.isReadable(path)) {
            return false;
        }
        
        // Must have .py extension
        if (!scriptPath.endsWith(".py")) {
            return false;
        }
        
        // Must be in allowed directories
        String allowedDir = System.getProperty("waqiti.ml.scripts.dir", "/opt/waqiti/ml");
        if (!scriptPath.startsWith(allowedDir)) {
            return false;
        }
        
        return true;
    }
    
    /**
     * Sanitize command arguments
     */
    private List<String> sanitizeArguments(List<String> arguments) {
        if (arguments == null) return new ArrayList<>();
        
        return arguments.stream()
            .filter(Objects::nonNull)
            .map(String::trim)
            .filter(arg -> !arg.isEmpty())
            .filter(this::isValidArgument)
            .toList();
    }
    
    /**
     * Validate individual argument
     */
    private boolean isValidArgument(String argument) {
        // Block shell metacharacters
        if (argument.contains(";") || argument.contains("|") || 
            argument.contains("&") || argument.contains("`") ||
            argument.contains("$") || argument.contains("(") ||
            argument.contains(")") || argument.contains("<") ||
            argument.contains(">") || argument.contains("*") ||
            argument.contains("?") || argument.contains("[") ||
            argument.contains("]") || argument.contains("{") ||
            argument.contains("}")) {
            return false;
        }
        
        // Must contain only safe characters
        return argument.matches("^[a-zA-Z0-9._/-]+$");
    }
    
    /**
     * Get secure Python executable path
     */
    private String getPythonExecutable() {
        String pythonPath = System.getProperty("waqiti.python.executable", "/usr/bin/python3");
        
        // Validate Python executable exists and is secure
        Path path = Paths.get(pythonPath);
        if (Files.exists(path) && Files.isExecutable(path)) {
            return pythonPath;
        }
        
        throw new SecurityException("Python executable not found or not secure: " + pythonPath);
    }
    
    /**
     * Get secure PATH environment variable
     */
    private String getSecurePath() {
        return "/usr/bin:/bin";
    }
    
    /**
     * Get secure temporary directory
     */
    private File getTempDirectory() {
        String tempDir = System.getProperty("waqiti.secure.temp.dir", "/tmp/waqiti-secure");
        return new File(tempDir);
    }
    
    /**
     * Secure Windows TPM check using alternative methods
     */
    private boolean checkWindowsTPMSecure() {
        try {
            // Try WMI through Java instead of Runtime.exec
            // This would require additional dependencies but is much safer
            log.debug("Checking Windows TPM using secure method");
            
            // Alternative: Check registry or use JNA
            // For now, return false as we cannot safely execute the command
            return false;
            
        } catch (Exception e) {
            log.warn("Secure Windows TPM check failed: {}", e.getMessage());
            return false;
        }
    }
    
    /**
     * Result of secure command execution
     */
    public static class SecureExecutionResult {
        private final boolean success;
        private final int exitCode;
        private final String output;
        private final String error;
        
        public SecureExecutionResult(boolean success, int exitCode, String output, String error) {
            this.success = success;
            this.exitCode = exitCode;
            this.output = output;
            this.error = error;
        }
        
        public static SecureExecutionResult error(String errorMessage) {
            return new SecureExecutionResult(false, -1, null, errorMessage);
        }
        
        public boolean isSuccess() { return success; }
        public int getExitCode() { return exitCode; }
        public String getOutput() { return output; }
        public String getError() { return error; }
    }
}