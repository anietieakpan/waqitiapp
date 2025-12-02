package com.waqiti.common.events;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.networknt.schema.JsonSchema;
import com.networknt.schema.JsonSchemaFactory;
import com.networknt.schema.SpecVersion;
import com.networknt.schema.ValidationMessage;
import lombok.Builder;
import lombok.Data;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import jakarta.annotation.PostConstruct;
import java.time.Instant;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Collectors;

/**
 * Schema Registry Service
 * Manages event schemas, versions, and compatibility checking
 * for the Waqiti event-driven architecture
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class SchemaRegistryService {

    private final ObjectMapper objectMapper;
    
    private final Map<String, Map<String, SchemaVersion>> schemaRegistry = new ConcurrentHashMap<>();
    private final Map<String, String> latestVersions = new ConcurrentHashMap<>();
    private final Map<String, CompatibilityMode> compatibilityModes = new ConcurrentHashMap<>();
    private final JsonSchemaFactory schemaFactory = JsonSchemaFactory.getInstance(SpecVersion.VersionFlag.V7);
    
    @PostConstruct
    public void initialize() {
        log.info("Initializing Schema Registry Service");
        
        // Register built-in schemas
        registerBuiltInSchemas();
        
        // Set default compatibility modes
        setDefaultCompatibilityModes();
        
        log.info("Schema Registry Service initialized with {} event schemas", schemaRegistry.size());
    }

    /**
     * Register a new schema version
     */
    public SchemaRegistrationResult registerSchema(SchemaRegistrationRequest request) {
        log.info("Registering schema: eventType={}, version={}", 
                request.getEventType(), request.getVersion());
        
        try {
            // Validate JSON schema format
            validateSchemaFormat(request.getSchemaDefinition());
            
            // Check compatibility with existing versions
            CompatibilityCheckResult compatibilityResult = checkCompatibility(
                    request.getEventType(), request.getSchemaDefinition());
            
            if (!compatibilityResult.isCompatible()) {
                return SchemaRegistrationResult.builder()
                        .success(false)
                        .message("Schema compatibility check failed: " + 
                                String.join(", ", compatibilityResult.getIssues()))
                        .build();
            }
            
            // Create schema version
            SchemaVersion schemaVersion = SchemaVersion.builder()
                    .eventType(request.getEventType())
                    .version(request.getVersion())
                    .schemaDefinition(request.getSchemaDefinition())
                    .description(request.getDescription())
                    .createdAt(Instant.now())
                    .createdBy(request.getCreatedBy())
                    .hash(calculateSchemaHash(request.getSchemaDefinition()))
                    .build();
            
            // Store schema version
            schemaRegistry.computeIfAbsent(request.getEventType(), k -> new HashMap<>())
                    .put(request.getVersion(), schemaVersion);
            
            // Update latest version
            updateLatestVersion(request.getEventType(), request.getVersion());
            
            log.info("Schema registered successfully: eventType={}, version={}", 
                    request.getEventType(), request.getVersion());
            
            return SchemaRegistrationResult.builder()
                    .success(true)
                    .schemaId(generateSchemaId(request.getEventType(), request.getVersion()))
                    .message("Schema registered successfully")
                    .build();
            
        } catch (Exception e) {
            log.error("Failed to register schema: eventType={}, version={}", 
                    request.getEventType(), request.getVersion(), e);
            
            return SchemaRegistrationResult.builder()
                    .success(false)
                    .message("Schema registration failed: " + e.getMessage())
                    .build();
        }
    }

    /**
     * Get schema by event type and version
     */
    public Optional<SchemaVersion> getSchema(String eventType, String version) {
        Map<String, SchemaVersion> eventSchemas = schemaRegistry.get(eventType);
        if (eventSchemas == null) {
            return Optional.empty();
        }
        
        return Optional.ofNullable(eventSchemas.get(version));
    }

    /**
     * Get latest schema version for event type
     */
    public Optional<SchemaVersion> getLatestSchema(String eventType) {
        String latestVersion = latestVersions.get(eventType);
        if (latestVersion == null) {
            return Optional.empty();
        }
        
        return getSchema(eventType, latestVersion);
    }

    /**
     * Get all schema versions for event type
     */
    public List<SchemaVersion> getAllSchemaVersions(String eventType) {
        Map<String, SchemaVersion> eventSchemas = schemaRegistry.get(eventType);
        if (eventSchemas == null) {
            return new ArrayList<>();
        }
        
        return new ArrayList<>(eventSchemas.values());
    }

    /**
     * Validate event data against schema
     */
    public ValidationResult validateEvent(String eventType, Object eventData) {
        return validateEvent(eventType, null, eventData);
    }

    /**
     * Validate event data against specific schema version
     */
    public ValidationResult validateEvent(String eventType, String version, Object eventData) {
        try {
            // Get schema
            Optional<SchemaVersion> schemaOpt = version != null ? 
                    getSchema(eventType, version) : getLatestSchema(eventType);
            
            if (schemaOpt.isEmpty()) {
                return ValidationResult.builder()
                        .valid(false)
                        .errors(List.of("No schema found for event type: " + eventType))
                        .build();
            }
            
            SchemaVersion schemaVersion = schemaOpt.get();
            
            // Parse schema
            JsonNode schemaNode = objectMapper.readTree(schemaVersion.getSchemaDefinition());
            JsonSchema jsonSchema = schemaFactory.getSchema(schemaNode);
            
            // Convert event data to JsonNode
            JsonNode eventNode = objectMapper.valueToTree(eventData);
            
            // Validate
            Set<ValidationMessage> validationMessages = jsonSchema.validate(eventNode);
            
            if (validationMessages.isEmpty()) {
                return ValidationResult.builder()
                        .valid(true)
                        .schemaVersion(schemaVersion.getVersion())
                        .build();
            }
            
            List<String> errors = validationMessages.stream()
                    .map(ValidationMessage::getMessage)
                    .collect(Collectors.toList());
            
            return ValidationResult.builder()
                    .valid(false)
                    .errors(errors)
                    .schemaVersion(schemaVersion.getVersion())
                    .build();
                    
        } catch (Exception e) {
            log.error("Schema validation failed: eventType={}", eventType, e);
            return ValidationResult.builder()
                    .valid(false)
                    .errors(List.of("Validation error: " + e.getMessage()))
                    .build();
        }
    }

    /**
     * Check schema compatibility
     */
    public CompatibilityCheckResult checkCompatibility(String eventType, String newSchemaDefinition) {
        CompatibilityMode mode = compatibilityModes.getOrDefault(eventType, CompatibilityMode.BACKWARD);
        
        // Get existing schema versions
        Map<String, SchemaVersion> existingSchemas = schemaRegistry.get(eventType);
        if (existingSchemas == null || existingSchemas.isEmpty()) {
            return CompatibilityCheckResult.builder()
                    .compatible(true)
                    .message("No existing schemas to check compatibility against")
                    .build();
        }
        
        // Get latest schema for compatibility check
        String latestVersion = latestVersions.get(eventType);
        SchemaVersion latestSchema = existingSchemas.get(latestVersion);
        
        if (latestSchema == null) {
            return CompatibilityCheckResult.builder()
                    .compatible(true)
                    .message("No latest schema found")
                    .build();
        }
        
        return checkCompatibility(latestSchema.getSchemaDefinition(), newSchemaDefinition, mode);
    }

    /**
     * Set compatibility mode for event type
     */
    public void setCompatibilityMode(String eventType, CompatibilityMode mode) {
        log.info("Setting compatibility mode for {}: {}", eventType, mode);
        compatibilityModes.put(eventType, mode);
    }

    /**
     * Get schema evolution history
     */
    public List<SchemaEvolution> getSchemaEvolution(String eventType) {
        Map<String, SchemaVersion> eventSchemas = schemaRegistry.get(eventType);
        if (eventSchemas == null) {
            return new ArrayList<>();
        }
        
        return eventSchemas.values().stream()
                .sorted((a, b) -> a.getCreatedAt().compareTo(b.getCreatedAt()))
                .map(schema -> SchemaEvolution.builder()
                        .version(schema.getVersion())
                        .description(schema.getDescription())
                        .createdAt(schema.getCreatedAt())
                        .createdBy(schema.getCreatedBy())
                        .changes(detectChanges(eventType, schema.getVersion()))
                        .build())
                .collect(Collectors.toList());
    }

    /**
     * Generate schema documentation
     */
    public SchemaDocumentation generateDocumentation(String eventType) {
        Optional<SchemaVersion> latestSchemaOpt = getLatestSchema(eventType);
        if (latestSchemaOpt.isEmpty()) {
            return null;
        }
        
        SchemaVersion latestSchema = latestSchemaOpt.get();
        
        try {
            JsonNode schemaNode = objectMapper.readTree(latestSchema.getSchemaDefinition());
            
            return SchemaDocumentation.builder()
                    .eventType(eventType)
                    .version(latestSchema.getVersion())
                    .description(latestSchema.getDescription())
                    .properties(extractProperties(schemaNode))
                    .examples(generateExamples(schemaNode))
                    .generatedAt(Instant.now())
                    .build();
                    
        } catch (Exception e) {
            log.error("Failed to generate documentation for {}", eventType, e);
            return null;
        }
    }

    // Private helper methods

    private void registerBuiltInSchemas() {
        // Payment Event Schema
        registerSchema(SchemaRegistrationRequest.builder()
                .eventType("payment.created")
                .version("1.0")
                .schemaDefinition("""
                    {
                        "$schema": "http://json-schema.org/draft-07/schema#",
                        "type": "object",
                        "properties": {
                            "paymentId": {
                                "type": "string",
                                "description": "Unique payment identifier"
                            },
                            "amount": {
                                "type": "number",
                                "minimum": 0,
                                "description": "Payment amount"
                            },
                            "currency": {
                                "type": "string",
                                "pattern": "^[A-Z]{3}$",
                                "description": "ISO 4217 currency code"
                            },
                            "fromUserId": {
                                "type": "string",
                                "description": "Sender user ID"
                            },
                            "toUserId": {
                                "type": "string",
                                "description": "Recipient user ID"
                            },
                            "status": {
                                "type": "string",
                                "enum": ["PENDING", "PROCESSING", "COMPLETED", "FAILED"],
                                "description": "Payment status"
                            },
                            "createdAt": {
                                "type": "string",
                                "format": "date-time",
                                "description": "Creation timestamp"
                            }
                        },
                        "required": ["paymentId", "amount", "currency", "fromUserId", "toUserId", "status", "createdAt"]
                    }
                    """)
                .description("Schema for payment creation events")
                .createdBy("system")
                .build());

        // User Event Schema
        registerSchema(SchemaRegistrationRequest.builder()
                .eventType("user.created")
                .version("1.0")
                .schemaDefinition("""
                    {
                        "$schema": "http://json-schema.org/draft-07/schema#",
                        "type": "object",
                        "properties": {
                            "userId": {
                                "type": "string",
                                "description": "Unique user identifier"
                            },
                            "email": {
                                "type": "string",
                                "format": "email",
                                "description": "User email address"
                            },
                            "firstName": {
                                "type": "string",
                                "minLength": 1,
                                "description": "User first name"
                            },
                            "lastName": {
                                "type": "string",
                                "minLength": 1,
                                "description": "User last name"
                            },
                            "phoneNumber": {
                                "type": "string",
                                "pattern": "^\\+?[1-9]\\d{1,14}$",
                                "description": "User phone number"
                            },
                            "country": {
                                "type": "string",
                                "pattern": "^[A-Z]{2}$",
                                "description": "ISO 3166-1 alpha-2 country code"
                            },
                            "createdAt": {
                                "type": "string",
                                "format": "date-time",
                                "description": "Account creation timestamp"
                            }
                        },
                        "required": ["userId", "email", "firstName", "lastName", "createdAt"]
                    }
                    """)
                .description("Schema for user creation events")
                .createdBy("system")
                .build());

        // Security Event Schema
        registerSchema(SchemaRegistrationRequest.builder()
                .eventType("security.login.failed")
                .version("1.0")
                .schemaDefinition("""
                    {
                        "$schema": "http://json-schema.org/draft-07/schema#",
                        "type": "object",
                        "properties": {
                            "userId": {
                                "type": "string",
                                "description": "User identifier (if known)"
                            },
                            "email": {
                                "type": "string",
                                "format": "email",
                                "description": "Email used in login attempt"
                            },
                            "ipAddress": {
                                "type": "string",
                                "description": "IP address of login attempt"
                            },
                            "userAgent": {
                                "type": "string",
                                "description": "User agent string"
                            },
                            "failureReason": {
                                "type": "string",
                                "enum": ["INVALID_CREDENTIALS", "ACCOUNT_LOCKED", "MFA_FAILED", "SUSPICIOUS_ACTIVITY"],
                                "description": "Reason for login failure"
                            },
                            "attemptedAt": {
                                "type": "string",
                                "format": "date-time",
                                "description": "Login attempt timestamp"
                            }
                        },
                        "required": ["email", "ipAddress", "failureReason", "attemptedAt"]
                    }
                    """)
                .description("Schema for failed login events")
                .createdBy("system")
                .build());
    }

    private void setDefaultCompatibilityModes() {
        compatibilityModes.put("payment.created", CompatibilityMode.BACKWARD);
        compatibilityModes.put("payment.completed", CompatibilityMode.BACKWARD);
        compatibilityModes.put("payment.failed", CompatibilityMode.BACKWARD);
        compatibilityModes.put("user.created", CompatibilityMode.FORWARD);
        compatibilityModes.put("user.verified", CompatibilityMode.FORWARD);
        compatibilityModes.put("security.login.failed", CompatibilityMode.FULL);
        compatibilityModes.put("security.fraud.detected", CompatibilityMode.FULL);
    }

    private void validateSchemaFormat(String schemaDefinition) throws Exception {
        JsonNode schemaNode = objectMapper.readTree(schemaDefinition);
        schemaFactory.getSchema(schemaNode); // This will throw if invalid
    }

    private CompatibilityCheckResult checkCompatibility(String oldSchema, String newSchema, CompatibilityMode mode) {
        try {
            JsonNode oldSchemaNode = objectMapper.readTree(oldSchema);
            JsonNode newSchemaNode = objectMapper.readTree(newSchema);
            
            List<String> issues = new ArrayList<>();
            
            switch (mode) {
                case BACKWARD:
                    issues.addAll(checkBackwardCompatibility(oldSchemaNode, newSchemaNode));
                    break;
                case FORWARD:
                    issues.addAll(checkForwardCompatibility(oldSchemaNode, newSchemaNode));
                    break;
                case FULL:
                    issues.addAll(checkBackwardCompatibility(oldSchemaNode, newSchemaNode));
                    issues.addAll(checkForwardCompatibility(oldSchemaNode, newSchemaNode));
                    break;
                case NONE:
                    // No compatibility checking
                    break;
            }
            
            return CompatibilityCheckResult.builder()
                    .compatible(issues.isEmpty())
                    .issues(issues)
                    .message(issues.isEmpty() ? "Schemas are compatible" : "Compatibility issues found")
                    .build();
                    
        } catch (Exception e) {
            return CompatibilityCheckResult.builder()
                    .compatible(false)
                    .issues(List.of("Error checking compatibility: " + e.getMessage()))
                    .build();
        }
    }

    private List<String> checkBackwardCompatibility(JsonNode oldSchema, JsonNode newSchema) {
        List<String> issues = new ArrayList<>();
        
        // Check if required fields were added (breaking change)
        JsonNode oldRequired = oldSchema.get("required");
        JsonNode newRequired = newSchema.get("required");
        
        if (newRequired != null && oldRequired != null) {
            Set<String> oldRequiredSet = new HashSet<>();
            oldRequired.forEach(node -> oldRequiredSet.add(node.asText()));
            
            newRequired.forEach(node -> {
                String field = node.asText();
                if (!oldRequiredSet.contains(field)) {
                    issues.add("New required field added: " + field);
                }
            });
        }
        
        return issues;
    }

    private List<String> checkForwardCompatibility(JsonNode oldSchema, JsonNode newSchema) {
        List<String> issues = new ArrayList<>();
        
        // Check if required fields were removed (breaking change for forward compatibility)
        JsonNode oldRequired = oldSchema.get("required");
        JsonNode newRequired = newSchema.get("required");
        
        if (oldRequired != null) {
            Set<String> newRequiredSet = new HashSet<>();
            if (newRequired != null) {
                newRequired.forEach(node -> newRequiredSet.add(node.asText()));
            }
            
            oldRequired.forEach(node -> {
                String field = node.asText();
                if (!newRequiredSet.contains(field)) {
                    issues.add("Required field removed: " + field);
                }
            });
        }
        
        return issues;
    }

    private void updateLatestVersion(String eventType, String version) {
        latestVersions.put(eventType, version);
    }

    private String calculateSchemaHash(String schemaDefinition) {
        return Integer.toHexString(schemaDefinition.hashCode());
    }

    private String generateSchemaId(String eventType, String version) {
        return eventType + ":" + version;
    }

    private List<String> detectChanges(String eventType, String version) {
        // Implementation would compare with previous version
        return Arrays.asList("Schema version " + version + " registered");
    }

    private Map<String, Object> extractProperties(JsonNode schemaNode) {
        Map<String, Object> properties = new HashMap<>();
        JsonNode propertiesNode = schemaNode.get("properties");
        
        if (propertiesNode != null) {
            propertiesNode.fieldNames().forEachRemaining(fieldName -> {
                JsonNode propertyNode = propertiesNode.get(fieldName);
                properties.put(fieldName, propertyNode);
            });
        }
        
        return properties;
    }

    private List<Object> generateExamples(JsonNode schemaNode) {
        List<Object> examples = new ArrayList<>();
        
        try {
            String type = schemaNode.has("type") ? schemaNode.get("type").asText() : "object";
            
            switch (type) {
                case "object":
                    examples.add(generateObjectExample(schemaNode));
                    break;
                case "array":
                    examples.add(generateArrayExample(schemaNode));
                    break;
                case "string":
                    examples.add(generateStringExample(schemaNode));
                    break;
                case "number":
                case "integer":
                    examples.add(generateNumberExample(schemaNode));
                    break;
                case "boolean":
                    examples.add(true);
                    examples.add(false);
                    break;
                default:
                    examples.add("Example for type: " + type);
            }
            
        } catch (Exception e) {
            log.warn("Failed to generate examples for schema", e);
            examples.add("Failed to generate example: " + e.getMessage());
        }
        
        return examples;
    }
    
    private Map<String, Object> generateObjectExample(JsonNode schemaNode) {
        Map<String, Object> example = new HashMap<>();
        
        if (schemaNode.has("properties")) {
            JsonNode properties = schemaNode.get("properties");
            properties.fieldNames().forEachRemaining(fieldName -> {
                JsonNode fieldSchema = properties.get(fieldName);
                example.put(fieldName, generateFieldExample(fieldSchema));
            });
        }
        
        return example;
    }
    
    private List<Object> generateArrayExample(JsonNode schemaNode) {
        List<Object> example = new ArrayList<>();
        
        if (schemaNode.has("items")) {
            JsonNode itemsSchema = schemaNode.get("items");
            example.add(generateFieldExample(itemsSchema));
            example.add(generateFieldExample(itemsSchema));
        }
        
        return example;
    }
    
    private Object generateStringExample(JsonNode schemaNode) {
        if (schemaNode.has("enum")) {
            JsonNode enumNode = schemaNode.get("enum");
            return enumNode.get(0).asText();
        }
        
        if (schemaNode.has("format")) {
            String format = schemaNode.get("format").asText();
            switch (format) {
                case "date":
                    return "2023-12-01";
                case "date-time":
                    return "2023-12-01T10:00:00Z";
                case "email":
                    return "user@example.com";
                case "uuid":
                    return "123e4567-e89b-12d3-a456-426614174000";
                default:
                    return "example-" + format;
            }
        }
        
        return "example-string";
    }
    
    private Object generateNumberExample(JsonNode schemaNode) {
        if (schemaNode.has("minimum")) {
            return schemaNode.get("minimum").asInt() + 1;
        }
        
        if (schemaNode.has("maximum")) {
            return schemaNode.get("maximum").asInt() - 1;
        }
        
        return schemaNode.get("type").asText().equals("integer") ? 42 : 42.5;
    }
    
    private Object generateFieldExample(JsonNode fieldSchema) {
        String type = fieldSchema.has("type") ? fieldSchema.get("type").asText() : "string";
        
        switch (type) {
            case "object":
                return generateObjectExample(fieldSchema);
            case "array":
                return generateArrayExample(fieldSchema);
            case "string":
                return generateStringExample(fieldSchema);
            case "number":
            case "integer":
                return generateNumberExample(fieldSchema);
            case "boolean":
                return true;
            default:
                return "example-value";
        }
    }

    // Enums and inner classes

    public enum CompatibilityMode {
        BACKWARD,   // New schema can read data written with old schema
        FORWARD,    // Old schema can read data written with new schema
        FULL,       // Both backward and forward compatible
        NONE        // No compatibility checking
    }

    @Data
    @Builder
    public static class SchemaRegistrationRequest {
        private String eventType;
        private String version;
        private String schemaDefinition;
        private String description;
        private String createdBy;
    }

    @Data
    @Builder
    public static class SchemaRegistrationResult {
        private boolean success;
        private String schemaId;
        private String message;
    }

    @Data
    @Builder
    public static class SchemaVersion {
        private String eventType;
        private String version;
        private String schemaDefinition;
        private String description;
        private Instant createdAt;
        private String createdBy;
        private String hash;
    }

    @Data
    @Builder
    public static class ValidationResult {
        private boolean valid;
        private List<String> errors;
        private List<String> warnings;
        private String schemaVersion;
    }

    @Data
    @Builder
    public static class CompatibilityCheckResult {
        private boolean compatible;
        private List<String> issues;
        private String message;
    }

    @Data
    @Builder
    public static class SchemaEvolution {
        private String version;
        private String description;
        private Instant createdAt;
        private String createdBy;
        private List<String> changes;
    }

    @Data
    @Builder
    public static class SchemaDocumentation {
        private String eventType;
        private String version;
        private String description;
        private Map<String, Object> properties;
        private List<Object> examples;
        private Instant generatedAt;
    }
}