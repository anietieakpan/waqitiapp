package com.waqiti.security.audit;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.actuate.health.Health;
import org.springframework.boot.actuate.health.HealthIndicator;
import org.springframework.core.env.Environment;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import java.util.*;
import java.util.regex.Pattern;

/**
 * Security auditor to detect hardcoded secrets and insecure configurations
 */
@Service
@Slf4j
@RequiredArgsConstructor
public class ConfigurationSecurityAuditor implements HealthIndicator {

    private final Environment environment;
    
    // Patterns to detect hardcoded secrets
    private static final List<Pattern> INSECURE_PATTERNS = Arrays.asList(
        Pattern.compile(".*password\\s*=\\s*['\"]?test.*", Pattern.CASE_INSENSITIVE),
        Pattern.compile(".*secret\\s*=\\s*['\"]?test.*", Pattern.CASE_INSENSITIVE),
        Pattern.compile(".*key\\s*=\\s*['\"]?(sk_test_|pk_test_).*", Pattern.CASE_INSENSITIVE),
        Pattern.compile(".*api[_-]?key\\s*=\\s*['\"]?demo.*", Pattern.CASE_INSENSITIVE),
        Pattern.compile(".*token\\s*=\\s*['\"]?placeholder.*", Pattern.CASE_INSENSITIVE)
    );
    
    // Required security properties
    private static final List<String> REQUIRED_SECURITY_PROPS = Arrays.asList(
        "spring.datasource.password",
        "security.secrets.vault.token",
        "security.secrets.encryption.master-key"
    );
    
    private final Map<String, SecurityViolation> detectedViolations = new HashMap<>();

    /**
     * Audit configuration security every hour
     */
    @Scheduled(fixedRate = 3600000) // Every hour
    public void auditConfigurationSecurity() {
        log.info("Starting configuration security audit...");
        
        detectedViolations.clear();
        
        // Check for hardcoded secrets
        auditHardcodedSecrets();
        
        // Check for missing required security properties
        auditRequiredSecurityProperties();
        
        // Check for insecure defaults
        auditInsecureDefaults();
        
        if (!detectedViolations.isEmpty()) {
            log.error("SECURITY AUDIT FAILED: {} violations detected", detectedViolations.size());
            detectedViolations.forEach((key, violation) -> 
                log.error("Security violation [{}]: {}", violation.getSeverity(), violation.getDescription()));
        } else {
            log.info("Configuration security audit passed - no violations detected");
        }
    }

    private void auditHardcodedSecrets() {
        // Check environment properties
        for (String propertyName : getAllPropertyNames()) {
            String value = environment.getProperty(propertyName);
            if (value != null) {
                for (Pattern pattern : INSECURE_PATTERNS) {
                    String checkString = propertyName + "=" + value;
                    if (pattern.matcher(checkString).matches()) {
                        detectedViolations.put(propertyName, new SecurityViolation(
                            SecuritySeverity.CRITICAL,
                            "Hardcoded secret detected in property: " + propertyName,
                            "Remove hardcoded value and use environment variable or Vault"
                        ));
                    }
                }
            }
        }
    }

    private void auditRequiredSecurityProperties() {
        for (String requiredProp : REQUIRED_SECURITY_PROPS) {
            String value = environment.getProperty(requiredProp);
            if (value == null || value.trim().isEmpty() || value.startsWith("${")) {
                detectedViolations.put(requiredProp, new SecurityViolation(
                    SecuritySeverity.HIGH,
                    "Required security property missing or not resolved: " + requiredProp,
                    "Ensure property is set via environment variable or Vault"
                ));
            }
        }
    }

    private void auditInsecureDefaults() {
        // Check for insecure default values
        Map<String, String> insecureDefaults = Map.of(
            "server.port", "8080", // Should be configurable
            "spring.datasource.username", "root",
            "spring.datasource.username", "admin",
            "management.endpoints.web.exposure.include", "*" // Too permissive
        );
        
        insecureDefaults.forEach((property, insecureValue) -> {
            String currentValue = environment.getProperty(property);
            if (insecureValue.equals(currentValue)) {
                detectedViolations.put(property, new SecurityViolation(
                    SecuritySeverity.MEDIUM,
                    "Insecure default value for: " + property,
                    "Change default value to a secure alternative"
                ));
            }
        });
    }

    @Override
    public Health health() {
        if (detectedViolations.isEmpty()) {
            return Health.up()
                .withDetail("status", "No security violations detected")
                .withDetail("lastAudit", new Date())
                .build();
        } else {
            Map<String, Object> details = new HashMap<>();
            details.put("violationCount", detectedViolations.size());
            details.put("criticalViolations", detectedViolations.values().stream()
                .filter(v -> v.getSeverity() == SecuritySeverity.CRITICAL)
                .count());
            details.put("violations", detectedViolations);
            
            return Health.down()
                .withDetails(details)
                .build();
        }
    }

    private Set<String> getAllPropertyNames() {
        // This is a simplified implementation
        // In practice, you'd iterate through all property sources
        Set<String> propertyNames = new HashSet<>();
        
        // Add known property patterns to check
        propertyNames.addAll(Arrays.asList(
            "spring.datasource.password",
            "payment.providers.stripe.secret-key",
            "payment.providers.stripe.public-key",
            "payment.providers.paypal.client-secret",
            "payment.providers.plaid.secret",
            "security.secrets.vault.token",
            "security.secrets.encryption.master-key"
        ));
        
        return propertyNames;
    }

    public Map<String, SecurityViolation> getDetectedViolations() {
        return Collections.unmodifiableMap(detectedViolations);
    }

    /**
     * Security violation model
     */
    public static class SecurityViolation {
        private final SecuritySeverity severity;
        private final String description;
        private final String remediation;
        private final Date detectedAt;

        public SecurityViolation(SecuritySeverity severity, String description, String remediation) {
            this.severity = severity;
            this.description = description;
            this.remediation = remediation;
            this.detectedAt = new Date();
        }

        public SecuritySeverity getSeverity() { return severity; }
        public String getDescription() { return description; }
        public String getRemediation() { return remediation; }
        public Date getDetectedAt() { return detectedAt; }
    }

    public enum SecuritySeverity {
        LOW, MEDIUM, HIGH, CRITICAL
    }
}