package com.waqiti.common.security.remediation;

import com.waqiti.common.security.secrets.SecureSecretsManager;
import com.waqiti.common.security.secrets.SecureString;
import com.waqiti.common.security.audit.SecurityAuditLogger;
import com.waqiti.common.security.scanner.CredentialScanner;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardOpenOption;
import java.util.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Enterprise-grade credential remediation service that automatically
 * replaces hardcoded credentials with secure vault references.
 * 
 * Features:
 * - Automatic detection and replacement
 * - Vault integration for secret storage
 * - Backup creation before modification
 * - Comprehensive audit logging
 * - Rollback capabilities
 * - CI/CD integration support
 */
@Slf4j
@Service
public class CredentialRemediationService {
    
    private final SecureSecretsManager secretsManager;
    private final SecurityAuditLogger auditLogger;
    private final CredentialScanner credentialScanner;
    
    private static final String BACKUP_SUFFIX = ".credential-backup";
    private static final String VAULT_PLACEHOLDER_PREFIX = "${vault.";
    private static final String VAULT_PLACEHOLDER_SUFFIX = "}";
    
    public CredentialRemediationService(SecureSecretsManager secretsManager,
                                       SecurityAuditLogger auditLogger,
                                       CredentialScanner credentialScanner) {
        this.secretsManager = secretsManager;
        this.auditLogger = auditLogger;
        this.credentialScanner = credentialScanner;
    }
    
    /**
     * Remediate all hardcoded credentials in the codebase
     */
    public RemediationResult remediateAllCredentials() {
        log.info("Starting comprehensive credential remediation");
        
        CredentialScanner.ScanResult scanResult = credentialScanner.performFullScan();
        
        if (!scanResult.hasViolations()) {
            log.info("No credentials found that require remediation");
            return RemediationResult.builder()
                    .totalViolations(0)
                    .remediatedViolations(0)
                    .failedRemediations(0)
                    .success(true)
                    .build();
        }
        
        RemediationResult.Builder resultBuilder = RemediationResult.builder();
        int totalViolations = 0;
        int remediatedCount = 0;
        int failedCount = 0;
        
        for (Map.Entry<String, List<CredentialScanner.CredentialViolation>> entry : 
             scanResult.getViolations().entrySet()) {
            
            String filePath = entry.getKey();
            List<CredentialScanner.CredentialViolation> violations = entry.getValue();
            
            totalViolations += violations.size();
            
            try {
                FileRemediationResult fileResult = remediateFile(filePath, violations);
                remediatedCount += fileResult.getRemediatedCount();
                failedCount += fileResult.getFailedCount();
                
            } catch (Exception e) {
                log.error("Failed to remediate file: {}", filePath, e);
                failedCount += violations.size();
            }
        }
        
        RemediationResult result = resultBuilder
                .totalViolations(totalViolations)
                .remediatedViolations(remediatedCount)
                .failedRemediations(failedCount)
                .success(failedCount == 0)
                .build();
        
        auditLogger.logCredentialRemediation(totalViolations, remediatedCount, failedCount);
        
        return result;
    }
    
    /**
     * Remediate credentials in a specific file
     */
    public FileRemediationResult remediateFile(String filePath, 
                                             List<CredentialScanner.CredentialViolation> violations) {
        
        log.info("Remediating credentials in file: {}", filePath);
        
        Path path = Paths.get(filePath);
        FileRemediationResult.Builder resultBuilder = FileRemediationResult.builder()
                .filePath(filePath);
        
        try {
            // Create backup
            createBackup(path);
            
            // Read file content
            List<String> lines = Files.readAllLines(path);
            boolean modified = false;
            int remediatedCount = 0;
            int failedCount = 0;
            
            // Process violations in reverse order to maintain line numbers
            violations.sort(Comparator.comparing(CredentialScanner.CredentialViolation::getLineNumber).reversed());
            
            for (CredentialScanner.CredentialViolation violation : violations) {
                try {
                    RemediationStrategy strategy = determineRemediationStrategy(violation);
                    String originalLine = lines.get(violation.getLineNumber() - 1);
                    String remediatedLine = strategy.remediate(originalLine, violation);
                    
                    if (!originalLine.equals(remediatedLine)) {
                        lines.set(violation.getLineNumber() - 1, remediatedLine);
                        modified = true;
                        remediatedCount++;
                        
                        auditLogger.logCredentialReplacement(
                                filePath, 
                                violation.getLineNumber(),
                                violation.getCredentialType().toString()
                        );
                    }
                    
                } catch (Exception e) {
                    log.error("Failed to remediate violation at line {}: {}", 
                            violation.getLineNumber(), e.getMessage());
                    failedCount++;
                }
            }
            
            // Write back if modified
            if (modified) {
                Files.write(path, lines, StandardOpenOption.WRITE, StandardOpenOption.TRUNCATE_EXISTING);
                log.info("Successfully remediated {} credentials in {}", remediatedCount, filePath);
            }
            
            return resultBuilder
                    .remediatedCount(remediatedCount)
                    .failedCount(failedCount)
                    .modified(modified)
                    .success(failedCount == 0)
                    .build();
            
        } catch (Exception e) {
            log.error("Failed to remediate file: {}", filePath, e);
            return resultBuilder
                    .remediatedCount(0)
                    .failedCount(violations.size())
                    .modified(false)
                    .success(false)
                    .errorMessage(e.getMessage())
                    .build();
        }
    }
    
    /**
     * Rollback remediation changes
     */
    public void rollbackRemediation(String filePath) {
        log.info("Rolling back credential remediation for file: {}", filePath);
        
        Path originalPath = Paths.get(filePath);
        Path backupPath = Paths.get(filePath + BACKUP_SUFFIX);
        
        try {
            if (Files.exists(backupPath)) {
                Files.copy(backupPath, originalPath, 
                          java.nio.file.StandardCopyOption.REPLACE_EXISTING);
                Files.delete(backupPath);
                
                auditLogger.logCredentialRollback(filePath);
                log.info("Successfully rolled back changes for: {}", filePath);
            } else {
                log.warn("No backup found for file: {}", filePath);
            }
            
        } catch (IOException e) {
            log.error("Failed to rollback remediation for file: {}", filePath, e);
            throw new RemediationException("Rollback failed", e);
        }
    }
    
    private void createBackup(Path filePath) throws IOException {
        Path backupPath = Paths.get(filePath.toString() + BACKUP_SUFFIX);
        Files.copy(filePath, backupPath, java.nio.file.StandardCopyOption.REPLACE_EXISTING);
        log.debug("Created backup: {}", backupPath);
    }
    
    private RemediationStrategy determineRemediationStrategy(CredentialScanner.CredentialViolation violation) {
        String fileName = Paths.get(violation.getFilePath()).getFileName().toString();
        
        if (fileName.endsWith(".yml") || fileName.endsWith(".yaml")) {
            return new YamlRemediationStrategy();
        } else if (fileName.endsWith(".properties")) {
            return new PropertiesRemediationStrategy();
        } else if (fileName.endsWith(".java")) {
            return new JavaRemediationStrategy();
        } else if (fileName.endsWith(".xml")) {
            return new XmlRemediationStrategy();
        } else if (fileName.endsWith(".json")) {
            return new JsonRemediationStrategy();
        } else {
            return new GenericRemediationStrategy();
        }
    }
    
    // Remediation strategies for different file types
    
    private interface RemediationStrategy {
        String remediate(String line, CredentialScanner.CredentialViolation violation);
    }
    
    private class YamlRemediationStrategy implements RemediationStrategy {
        @Override
        public String remediate(String line, CredentialScanner.CredentialViolation violation) {
            String secretPath = generateSecretPath(violation);
            
            // Store the actual credential in Vault
            storeCredentialInVault(secretPath, extractCredentialValue(line, violation));
            
            // Replace with Vault reference
            String vaultRef = VAULT_PLACEHOLDER_PREFIX + secretPath + VAULT_PLACEHOLDER_SUFFIX;
            
            // YAML pattern matching and replacement
            Pattern pattern = getPatternForCredentialType(violation.getCredentialType());
            Matcher matcher = pattern.matcher(line);
            
            if (matcher.find()) {
                String key = matcher.group(1);
                return line.replaceFirst(pattern.pattern(), 
                        key + ": " + vaultRef + " # Secured via Vault");
            }
            
            return line;
        }
    }
    
    private class PropertiesRemediationStrategy implements RemediationStrategy {
        @Override
        public String remediate(String line, CredentialScanner.CredentialViolation violation) {
            String secretPath = generateSecretPath(violation);
            
            // Store the actual credential in Vault
            storeCredentialInVault(secretPath, extractCredentialValue(line, violation));
            
            // Replace with Vault reference
            String vaultRef = VAULT_PLACEHOLDER_PREFIX + secretPath + VAULT_PLACEHOLDER_SUFFIX;
            
            // Properties pattern matching and replacement
            Pattern pattern = Pattern.compile("([^=]+)=(.+)");
            Matcher matcher = pattern.matcher(line);
            
            if (matcher.find()) {
                String key = matcher.group(1);
                return key + "=" + vaultRef + " # Secured via Vault";
            }
            
            return line;
        }
    }
    
    private class JavaRemediationStrategy implements RemediationStrategy {
        @Override
        public String remediate(String line, CredentialScanner.CredentialViolation violation) {
            // For Java files, we typically replace with @Value annotations
            String secretPath = generateSecretPath(violation);
            
            // Store the actual credential in Vault
            storeCredentialInVault(secretPath, extractCredentialValue(line, violation));
            
            // Generate Spring @Value annotation
            String valueAnnotation = "@Value(\"${" + secretPath + "}\")";
            
            // Replace the hardcoded credential with the @Value annotation
            return replaceCredentialWithAnnotation(line, violation, valueAnnotation);
        }
    }
    
    private class XmlRemediationStrategy implements RemediationStrategy {
        @Override
        public String remediate(String line, CredentialScanner.CredentialViolation violation) {
            String secretPath = generateSecretPath(violation);
            
            // Store the actual credential in Vault
            storeCredentialInVault(secretPath, extractCredentialValue(line, violation));
            
            // Replace with placeholder reference
            String placeholder = "${" + secretPath + "}";
            
            // XML pattern matching and replacement
            Pattern pattern = getPatternForCredentialType(violation.getCredentialType());
            Matcher matcher = pattern.matcher(line);
            
            if (matcher.find()) {
                return matcher.replaceFirst(placeholder);
            }
            
            return line;
        }
    }
    
    private class JsonRemediationStrategy implements RemediationStrategy {
        @Override
        public String remediate(String line, CredentialScanner.CredentialViolation violation) {
            String secretPath = generateSecretPath(violation);
            
            // Store the actual credential in Vault
            storeCredentialInVault(secretPath, extractCredentialValue(line, violation));
            
            // Replace with placeholder reference
            String placeholder = "${" + secretPath + "}";
            
            // JSON pattern matching and replacement
            Pattern pattern = Pattern.compile("(\"[^\"]*\"\\s*:\\s*)\"[^\"]*\"");
            Matcher matcher = pattern.matcher(line);
            
            if (matcher.find()) {
                String key = matcher.group(1);
                return matcher.replaceFirst(key + "\"" + placeholder + "\"");
            }
            
            return line;
        }
    }
    
    private class GenericRemediationStrategy implements RemediationStrategy {
        @Override
        public String remediate(String line, CredentialScanner.CredentialViolation violation) {
            // Generic replacement - add comment with recommendation
            return line + " # SECURITY: Replace hardcoded credential with secure configuration";
        }
    }
    
    private String generateSecretPath(CredentialScanner.CredentialViolation violation) {
        // Generate a meaningful path for the secret in Vault
        String fileName = Paths.get(violation.getFilePath()).getFileName().toString();
        String baseName = fileName.substring(0, fileName.lastIndexOf('.'));
        String credentialType = violation.getCredentialType().toString().toLowerCase();
        
        return String.format("waqiti/%s/%s", baseName, credentialType);
    }
    
    private void storeCredentialInVault(String secretPath, String credentialValue) {
        try {
            // Don't store if it's already a placeholder or example
            if (credentialValue.contains("${") || 
                credentialValue.contains("example") ||
                credentialValue.contains("placeholder") ||
                credentialValue.equals("your-secret-here")) {
                return;
            }
            
            Map<String, String> tags = Map.of(
                "source", "credential-remediation",
                "automated", "true",
                "timestamp", String.valueOf(System.currentTimeMillis())
            );
            
            secretsManager.createOrUpdateSecret(secretPath, credentialValue, tags);
            log.debug("Stored credential in Vault at path: {}", secretPath);
            
        } catch (Exception e) {
            log.error("Failed to store credential in Vault: {}", secretPath, e);
            throw new RemediationException("Failed to store credential in Vault", e);
        }
    }
    
    private String extractCredentialValue(String line, CredentialScanner.CredentialViolation violation) {
        // Extract the actual credential value from the line
        Pattern pattern = getPatternForCredentialType(violation.getCredentialType());
        Matcher matcher = pattern.matcher(line);
        
        if (matcher.find()) {
            // Try to get the credential value from capture groups
            int groupCount = matcher.groupCount();
            if (groupCount >= 2) {
                return matcher.group(2).replaceAll("[\"']", "");
            } else if (groupCount >= 1) {
                return matcher.group(1).replaceAll("[\"']", "");
            }
        }
        
        // Fallback: extract quoted string
        Pattern quotedPattern = Pattern.compile("[\"']([^\"']+)[\"']");
        Matcher quotedMatcher = quotedPattern.matcher(line);
        if (quotedMatcher.find()) {
            return quotedMatcher.group(1);
        }
        
        return "EXTRACTED_VALUE";
    }
    
    private Pattern getPatternForCredentialType(CredentialScanner.CredentialType type) {
        switch (type) {
            case PASSWORD:
                return Pattern.compile("(?i)(password|passwd|pwd)\\s*[=:]\\s*[\"']([^\"']+)[\"']");
            case API_KEY:
                return Pattern.compile("(?i)(api[_-]?key|apikey)\\s*[=:]\\s*[\"']([^\"']+)[\"']");
            case SECRET_KEY:
                return Pattern.compile("(?i)(secret[_-]?key|secretkey)\\s*[=:]\\s*[\"']([^\"']+)[\"']");
            case TOKEN:
                return Pattern.compile("(?i)(token|auth[_-]?token)\\s*[=:]\\s*[\"']([^\"']+)[\"']");
            case DATABASE_PASSWORD:
                return Pattern.compile("(?i)(db[_-]?password|database[_-]?password)\\s*[=:]\\s*[\"']([^\"']+)[\"']");
            default:
                return Pattern.compile("(?i)([a-zA-Z_-]+)\\s*[=:]\\s*[\"']([^\"']+)[\"']");
        }
    }
    
    private String replaceCredentialWithAnnotation(String line, CredentialScanner.CredentialViolation violation, String valueAnnotation) {
        Pattern pattern = getCredentialPattern(violation.getType());
        java.util.regex.Matcher matcher = pattern.matcher(line);
        
        if (matcher.find()) {
            String keyName = matcher.group(1);
            String originalValue = matcher.group(2);
            
            // For Java properties or field declarations
            if (line.contains("=") && line.contains(";")) {
                // Field declaration: private String password = "secret123";
                // Replace with: @Value("${vault.path}") private String password;
                String beforeEquals = line.substring(0, line.indexOf("=")).trim();
                return valueAnnotation + " " + beforeEquals + ";";
            } else if (line.contains("=")) {
                // Property assignment: config.password = "secret123"
                // Replace with: config.password = passwordFromVault
                String beforeEquals = line.substring(0, line.indexOf("=")).trim();
                String variableName = keyName.replaceAll("[^a-zA-Z0-9]", "").toLowerCase() + "FromVault";
                return beforeEquals + " = " + variableName + "; // Injected via " + valueAnnotation;
            } else {
                // Other cases - add comment with annotation
                return line.replace(originalValue, "/* REDACTED - Use " + valueAnnotation + " */");
            }
        }
        
        // Fallback - add comment
        return line + " /* Credential should be externalized using " + valueAnnotation + " */";
    }
    
    // Result classes
    
    @lombok.Data
    @lombok.Builder
    @lombok.NoArgsConstructor
    @lombok.AllArgsConstructor
    public static class RemediationResult {
        private int totalViolations;
        private int remediatedViolations;
        private int failedRemediations;
        private boolean success;
        private String errorMessage;
        private List<String> remediatedFiles;
        private List<String> failedFiles;
    }

    @lombok.Data
    @lombok.Builder
    @lombok.NoArgsConstructor
    @lombok.AllArgsConstructor
    public static class FileRemediationResult {
        private String filePath;
        private int remediatedCount;
        private int failedCount;
        private boolean modified;
        private boolean success;
        private String errorMessage;
    }
    
    public static class RemediationException extends RuntimeException {
        public RemediationException(String message, Throwable cause) {
            super(message, cause);
        }
    }
}