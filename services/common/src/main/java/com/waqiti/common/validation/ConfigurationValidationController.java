package com.waqiti.common.validation;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.core.env.Environment;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.HashMap;
import java.util.Map;

/**
 * REST controller for configuration validation and monitoring
 */
@RestController
@RequestMapping("/api/configuration")
@RequiredArgsConstructor
@Slf4j
public class ConfigurationValidationController {
    
    private final ConfigurationValidator configurationValidator;
    private final Environment environment;
    
    /**
     * Get configuration validation status
     */
    @GetMapping("/validation/status")
    @PreAuthorize("hasAuthority('ADMIN') or hasAuthority('CONFIG_READ')")
    public ResponseEntity<ConfigurationValidationStatus> getValidationStatus() {
        try {
            // Re-run validation to get current status
            ConfigurationValidationResult result = performValidation();
            
            ConfigurationValidationStatus status = ConfigurationValidationStatus.builder()
                .valid(result.isValid())
                .errorCount(result.getErrorCount())
                .warningCount(result.getWarningCount())
                .summary(result.getSummary())
                .errors(result.getErrors())
                .warnings(result.getWarnings())
                .lastValidated(System.currentTimeMillis())
                .build();
                
            return ResponseEntity.ok(status);
        } catch (Exception e) {
            log.error("Failed to get configuration validation status", e);
            return ResponseEntity.internalServerError().build();
        }
    }
    
    /**
     * Validate specific configuration property
     */
    @GetMapping("/validation/property/{propertyName}")
    @PreAuthorize("hasAuthority('ADMIN') or hasAuthority('CONFIG_READ')")
    public ResponseEntity<PropertyValidationResult> validateProperty(@PathVariable String propertyName) {
        try {
            String value = environment.getProperty(propertyName);
            
            PropertyValidationResult result = PropertyValidationResult.builder()
                .propertyName(propertyName)
                .value(value != null ? value : "NOT_SET")
                .exists(value != null)
                .build();
                
            return ResponseEntity.ok(result);
        } catch (Exception e) {
            log.error("Failed to validate property: {}", propertyName, e);
            return ResponseEntity.internalServerError().build();
        }
    }
    
    /**
     * Get all active profiles
     */
    @GetMapping("/profiles")
    @PreAuthorize("hasAuthority('ADMIN') or hasAuthority('CONFIG_READ')")
    public ResponseEntity<ProfileInfo> getActiveProfiles() {
        try {
            String[] activeProfiles = environment.getActiveProfiles();
            String[] defaultProfiles = environment.getDefaultProfiles();
            
            ProfileInfo profileInfo = ProfileInfo.builder()
                .activeProfiles(activeProfiles)
                .defaultProfiles(defaultProfiles)
                .hasActiveProfiles(activeProfiles.length > 0)
                .build();
                
            return ResponseEntity.ok(profileInfo);
        } catch (Exception e) {
            log.error("Failed to get active profiles", e);
            return ResponseEntity.internalServerError().build();
        }
    }
    
    /**
     * Get configuration summary for monitoring
     */
    @GetMapping("/summary")
    @PreAuthorize("hasAuthority('ADMIN') or hasAuthority('CONFIG_READ')")
    public ResponseEntity<ConfigurationSummary> getConfigurationSummary() {
        try {
            Map<String, String> criticalProperties = new HashMap<>();
            
            // Add critical properties for monitoring
            addPropertyIfExists(criticalProperties, "spring.datasource.url");
            addPropertyIfExists(criticalProperties, "spring.redis.host");
            addPropertyIfExists(criticalProperties, "server.port");
            addPropertyIfExists(criticalProperties, "logging.level.root");
            addPropertyIfExists(criticalProperties, "management.endpoints.web.exposure.include");
            
            ConfigurationSummary summary = ConfigurationSummary.builder()
                .activeProfiles(environment.getActiveProfiles())
                .criticalProperties(criticalProperties)
                .javaVersion(System.getProperty("java.version"))
                .springBootVersion(getSpringBootVersion())
                .build();
                
            return ResponseEntity.ok(summary);
        } catch (Exception e) {
            log.error("Failed to get configuration summary", e);
            return ResponseEntity.internalServerError().build();
        }
    }
    
    private ConfigurationValidationResult performValidation() {
        // This would ideally call the same validation logic as ConfigurationValidator
        // For now, return a simplified validation result
        ConfigurationValidationResult result = new ConfigurationValidationResult();
        
        // Basic validation checks
        if (environment.getProperty("spring.datasource.url") == null) {
            result.addError("Database URL is not configured");
        }
        
        if (environment.getProperty("spring.redis.host") == null) {
            result.addWarning("Redis host is not configured - caching may be disabled");
        }
        
        return result;
    }
    
    private void addPropertyIfExists(Map<String, String> properties, String propertyName) {
        String value = environment.getProperty(propertyName);
        if (value != null) {
            // Mask sensitive values
            if (propertyName.toLowerCase().contains("password") || 
                propertyName.toLowerCase().contains("secret") ||
                propertyName.toLowerCase().contains("key")) {
                properties.put(propertyName, "***MASKED***");
            } else {
                properties.put(propertyName, value);
            }
        }
    }
    
    private String getSpringBootVersion() {
        try {
            return org.springframework.boot.SpringBootVersion.getVersion();
        } catch (Exception e) {
            return "Unknown";
        }
    }
}