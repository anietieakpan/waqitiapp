package com.waqiti.common.fraud.scoring;

import com.waqiti.common.fraud.FraudContext;
import java.time.LocalDateTime;
import java.util.Map;

/**
 * Interface for fraud scoring components in the Waqiti fraud detection system
 * 
 * Defines the contract for different scoring components that contribute
 * to the overall fraud risk assessment. Each component provides a specific
 * type of analysis (ML models, rule engines, anomaly detectors, etc.).
 */
public interface ScoringComponent {

    /**
     * Calculate component-specific fraud score for the given context
     * 
     * @param context The fraud analysis context containing transaction and user data
     * @return ComponentScore containing the calculated score and metadata
     */
    ComponentScore calculateScore(FraudContext context);

    /**
     * Get the unique identifier for this scoring component
     * 
     * @return Component identifier string
     */
    String getComponentId();

    /**
     * Get the display name of this scoring component
     * 
     * @return Human-readable component name
     */
    String getComponentName();

    /**
     * Get the weight/importance of this component in overall scoring
     * 
     * @return Weight value between 0.0 and 1.0
     */
    double getComponentWeight();

    /**
     * Get the version of this scoring component
     * 
     * @return Version string
     */
    String getVersion();

    /**
     * Check if this component is currently enabled and healthy
     * 
     * @return true if component is operational, false otherwise
     */
    boolean isEnabled();

    /**
     * Get the last time this component was updated or calibrated
     * 
     * @return Last update timestamp
     */
    LocalDateTime getLastUpdated();

    /**
     * Validate if the component can process the given context
     * 
     * @param context The fraud analysis context
     * @return ValidationResult indicating if processing is possible
     */
    ValidationResult validateContext(FraudContext context);

    /**
     * Get component-specific configuration and metadata
     * 
     * @return Configuration map
     */
    Map<String, Object> getConfiguration();

    /**
     * Component score result
     */
    class ComponentScore {
        private final String componentId;
        private final double score;
        private final double confidence;
        private final String explanation;
        private final Map<String, Object> metadata;
        private final LocalDateTime calculatedAt;

        public ComponentScore(String componentId, double score, double confidence, 
                            String explanation, Map<String, Object> metadata) {
            this.componentId = componentId;
            this.score = score;
            this.confidence = confidence;
            this.explanation = explanation;
            this.metadata = metadata;
            this.calculatedAt = LocalDateTime.now();
        }

        public String getComponentId() { return componentId; }
        public double getScore() { return score; }
        public double getConfidence() { return confidence; }
        public String getExplanation() { return explanation; }
        public Map<String, Object> getMetadata() { return metadata; }
        public LocalDateTime getCalculatedAt() { return calculatedAt; }
        
        public double getWeightedScore() {
            return score * confidence;
        }
    }

    /**
     * Context validation result
     */
    class ValidationResult {
        private final boolean valid;
        private final String reason;
        private final Map<String, String> missingFields;

        public ValidationResult(boolean valid, String reason, Map<String, String> missingFields) {
            this.valid = valid;
            this.reason = reason;
            this.missingFields = missingFields;
        }

        public static ValidationResult valid() {
            return new ValidationResult(true, null, null);
        }

        public static ValidationResult invalid(String reason) {
            return new ValidationResult(false, reason, null);
        }

        public static ValidationResult invalid(String reason, Map<String, String> missingFields) {
            return new ValidationResult(false, reason, missingFields);
        }

        public boolean isValid() { return valid; }
        public String getReason() { return reason; }
        public Map<String, String> getMissingFields() { return missingFields; }
    }

    /**
     * Component type enumeration
     */
    enum ComponentType {
        MACHINE_LEARNING("ML Model", "Machine learning-based fraud detection"),
        RULE_ENGINE("Rules", "Rule-based fraud detection"),
        ANOMALY_DETECTOR("Anomaly", "Statistical anomaly detection"),
        BEHAVIORAL_ANALYZER("Behavior", "Behavioral pattern analysis"),
        VELOCITY_CHECKER("Velocity", "Transaction velocity analysis"),
        BLACKLIST_CHECKER("Blacklist", "Blacklist and reputation checking"),
        GEOGRAPHIC_ANALYZER("Geography", "Geographic risk analysis"),
        DEVICE_ANALYZER("Device", "Device fingerprinting and analysis"),
        NETWORK_ANALYZER("Network", "Network and social graph analysis");

        private final String displayName;
        private final String description;

        ComponentType(String displayName, String description) {
            this.displayName = displayName;
            this.description = description;
        }

        public String getDisplayName() { return displayName; }
        public String getDescription() { return description; }
    }

    /**
     * Get the type of this scoring component
     * 
     * @return ComponentType enum value
     */
    default ComponentType getComponentType() {
        return ComponentType.RULE_ENGINE; // Default implementation
    }

    /**
     * Get component health status
     * 
     * @return Health check result
     */
    default HealthStatus getHealthStatus() {
        return isEnabled() ? HealthStatus.HEALTHY : HealthStatus.DISABLED;
    }

    /**
     * Component health status
     */
    enum HealthStatus {
        HEALTHY("Component is operational"),
        DEGRADED("Component is operational but with reduced performance"),
        UNHEALTHY("Component has issues but may still function"),
        DISABLED("Component is disabled"),
        FAILED("Component has failed and is not operational");

        private final String description;

        HealthStatus(String description) {
            this.description = description;
        }

        public String getDescription() { return description; }
    }

    /**
     * Component priority level for scoring
     */
    enum Priority {
        CRITICAL(1.0, "Critical for fraud detection"),
        HIGH(0.8, "High importance"),
        MEDIUM(0.6, "Medium importance"),
        LOW(0.4, "Low importance"),
        OPTIONAL(0.2, "Optional enhancement");

        private final double multiplier;
        private final String description;

        Priority(double multiplier, String description) {
            this.multiplier = multiplier;
            this.description = description;
        }

        public double getMultiplier() { return multiplier; }
        public String getDescription() { return description; }
    }

    /**
     * Get the priority of this component
     * 
     * @return Priority level
     */
    default Priority getPriority() {
        return Priority.MEDIUM; // Default implementation
    }
}