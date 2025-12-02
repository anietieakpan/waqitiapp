package com.waqiti.common.saga;

import lombok.Data;
import lombok.Builder;
import lombok.NoArgsConstructor;
import lombok.AllArgsConstructor;

import java.time.Duration;
import java.util.*;

/**
 * Represents a single step in a saga workflow.
 * Each step encapsulates a business operation that can be executed and compensated.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class SagaStep {
    
    /**
     * Unique identifier for this step within the saga
     */
    private String stepId;
    
    /**
     * Type/category of this step (e.g., "payment", "inventory", "notification")
     */
    private String stepType;
    
    /**
     * Human-readable name for this step
     */
    private String name;
    
    /**
     * Service name for this step
     */
    private String serviceName;
    
    /**
     * Action name for this step
     */
    private String action;
    
    /**
     * Compensation action name for this step
     */
    private String compensationAction;
    
    /**
     * Step name (alias for name)
     */
    private String stepName;
    
    /**
     * Description of what this step accomplishes
     */
    private String description;
    
    /**
     * Service endpoint to execute this step
     */
    private String serviceEndpoint;
    
    /**
     * HTTP method for the service call
     */
    @Builder.Default
    private String httpMethod = "POST";
    
    /**
     * Parameters to pass to the service
     */
    @Builder.Default
    private Map<String, Object> parameters = new HashMap<>();
    
    /**
     * Headers to include in the service call
     */
    @Builder.Default
    private Map<String, String> headers = new HashMap<>();
    
    /**
     * List of step IDs that must complete before this step can execute
     */
    @Builder.Default
    private List<String> dependencies = new ArrayList<>();
    
    /**
     * Compensation endpoint to call if this step needs to be undone
     */
    private String compensationEndpoint;
    
    /**
     * HTTP method for compensation
     */
    @Builder.Default
    private String compensationHttpMethod = "POST";
    
    /**
     * Parameters for compensation
     */
    @Builder.Default
    private Map<String, Object> compensationParameters = new HashMap<>();
    
    /**
     * Whether this step can be executed in parallel with other steps
     */
    @Builder.Default
    private boolean parallelizable = true;
    
    /**
     * Whether this step can run in parallel (alias)
     */
    @Builder.Default
    private boolean canRunInParallel = true;
    
    /**
     * Whether to use circuit breaker for this step
     */
    @Builder.Default
    private boolean useCircuitBreaker = true;
    
    /**
     * Whether this step is idempotent (safe to retry)
     */
    @Builder.Default
    private boolean idempotent = true;
    
    /**
     * Whether this step is critical (saga fails if this step fails)
     */
    @Builder.Default
    private boolean critical = true;
    
    /**
     * Maximum number of retry attempts for this step
     */
    @Builder.Default
    private int maxRetries = 3;
    
    /**
     * Timeout for this step in seconds
     */
    @Builder.Default
    private int timeoutSeconds = 300; // 5 minutes
    
    /**
     * Timeout duration for this step
     */
    private Duration timeout;
    
    /**
     * Retry backoff strategy
     */
    @Builder.Default
    private RetryStrategy retryStrategy = RetryStrategy.EXPONENTIAL_BACKOFF;

    /**
     * Tags for categorization/filtering (REMOVED - duplicate field, use Set<String> tags below)
     */
    // @Builder.Default
    // private List<String> tags = new ArrayList<>();

    /**
     * Whether this step is optional (saga continues if fails)
     */
    @Builder.Default
    private boolean optional = false;

    /**
     * Initial retry delay in milliseconds
     */
    @Builder.Default
    private long retryDelayMs = 1000;
    
    /**
     * Maximum retry delay in milliseconds
     */
    @Builder.Default
    private long maxRetryDelayMs = 30000;
    
    /**
     * Priority for execution order (higher numbers execute first)
     */
    @Builder.Default
    private int priority = 0;
    
    /**
     * Expected response status codes indicating success
     */
    @Builder.Default
    private Set<Integer> successStatusCodes = Set.of(200, 201, 202, 204);
    
    /**
     * Custom properties for this step
     */
    @Builder.Default
    private Map<String, Object> properties = new HashMap<>();
    
    /**
     * Tags for categorizing this step
     */
    @Builder.Default
    private Set<String> tags = new HashSet<>();
    
    /**
     * Validation rules for step input
     */
    @Builder.Default
    private List<ValidationRule> validationRules = new ArrayList<>();
    
    /**
     * Expected output schema for validation
     */
    private String outputSchema;

    /**
     * Custom builder methods for fluent API
     * Note: These extend Lombok's generated SagaStepBuilder
     */
    public static class SagaStepBuilder {
        /**
         * Add a single tag
         */
        public SagaStepBuilder withTag(String tag) {
            if (tag != null && !tag.isEmpty()) {
                if (this.tags$value == null) {
                    this.tags$value = new HashSet<>();
                    this.tags$set = true;
                }
                this.tags$value.add(tag);
            }
            return this;
        }

        /**
         * Set compensation with endpoint and parameters
         */
        public SagaStepBuilder withCompensation(String endpoint, Map<String, String> params) {
            this.compensationEndpoint(endpoint);
            if (params != null && !params.isEmpty()) {
                Map<String, Object> objParams = new HashMap<>(params);
                this.compensationParameters(objParams);
            }
            return this;
        }

        /**
         * Mark step as optional
         */
        public SagaStepBuilder asOptional() {
            this.optional(true);
            this.critical(false);
            return this;
        }
    }

    /**
     * Add a dependency
     */
    public SagaStep addDependency(String stepId) {
        this.dependencies.add(stepId);
        return this;
    }
    
    /**
     * Add multiple dependencies
     */
    public SagaStep addDependencies(String... stepIds) {
        this.dependencies.addAll(Arrays.asList(stepIds));
        return this;
    }
    
    /**
     * Add a parameter
     */
    public SagaStep addParameter(String key, Object value) {
        this.parameters.put(key, value);
        return this;
    }
    
    /**
     * Add a header
     */
    public SagaStep addHeader(String key, String value) {
        this.headers.put(key, value);
        return this;
    }
    
    /**
     * Set compensation details
     */
    public SagaStep withCompensation(String endpoint, Map<String, Object> parameters) {
        this.compensationEndpoint = endpoint;
        this.compensationParameters = parameters != null ? parameters : new HashMap<>();
        return this;
    }
    
    /**
     * Set compensation details with HTTP method
     */
    public SagaStep withCompensation(String endpoint, String httpMethod, Map<String, Object> parameters) {
        this.compensationEndpoint = endpoint;
        this.compensationHttpMethod = httpMethod;
        this.compensationParameters = parameters != null ? parameters : new HashMap<>();
        return this;
    }
    
    /**
     * Set retry configuration
     */
    public SagaStep withRetry(int maxRetries, RetryStrategy strategy, long delayMs) {
        this.maxRetries = maxRetries;
        this.retryStrategy = strategy;
        this.retryDelayMs = delayMs;
        return this;
    }
    
    /**
     * Set timeout
     */
    public SagaStep withTimeout(int timeoutSeconds) {
        this.timeoutSeconds = timeoutSeconds;
        return this;
    }
    
    /**
     * Set timeout duration
     */
    public SagaStep timeout(Duration timeout) {
        this.timeout = timeout;
        if (timeout != null) {
            this.timeoutSeconds = (int) timeout.getSeconds();
        }
        return this;
    }
    
    /**
     * Get timeout in seconds, preferring Duration if set
     */
    public int getTimeoutSeconds() {
        if (timeout != null) {
            return (int) timeout.getSeconds();
        }
        return timeoutSeconds;
    }
    
    /**
     * Check if this step is critical
     */
    public boolean isCritical() {
        return critical;
    }
    
    /**
     * Check if circuit breaker should be used
     */
    public boolean isUseCircuitBreaker() {
        return useCircuitBreaker;
    }
    
    /**
     * Set priority
     */
    public SagaStep withPriority(int priority) {
        this.priority = priority;
        return this;
    }
    
    /**
     * Mark as non-critical
     */
    public SagaStep asOptional() {
        this.critical = false;
        return this;
    }
    
    /**
     * Mark as non-parallelizable
     */
    public SagaStep asSequential() {
        this.parallelizable = false;
        return this;
    }
    
    /**
     * Mark as non-idempotent
     */
    public SagaStep asNonIdempotent() {
        this.idempotent = false;
        return this;
    }
    
    /**
     * Add custom property
     */
    public SagaStep withProperty(String key, Object value) {
        this.properties.put(key, value);
        return this;
    }
    
    /**
     * Add tag
     */
    public SagaStep withTag(String tag) {
        this.tags.add(tag);
        return this;
    }
    
    /**
     * Add validation rule
     */
    public SagaStep withValidation(ValidationRule rule) {
        this.validationRules.add(rule);
        return this;
    }
    
    /**
     * Set expected success status codes
     */
    public SagaStep withSuccessStatusCodes(Integer... statusCodes) {
        this.successStatusCodes = Set.of(statusCodes);
        return this;
    }
    
    /**
     * Calculate retry delay based on attempt number
     */
    public long calculateRetryDelay(int attemptNumber) {
        switch (retryStrategy) {
            case FIXED_DELAY:
                return retryDelayMs;
                
            case LINEAR_BACKOFF:
                return Math.min(retryDelayMs * attemptNumber, maxRetryDelayMs);
                
            case EXPONENTIAL_BACKOFF:
                long delay = (long) (retryDelayMs * Math.pow(2, attemptNumber - 1));
                return Math.min(delay, maxRetryDelayMs);
                
            case FIBONACCI_BACKOFF:
                return Math.min(fibonacciDelay(attemptNumber) * retryDelayMs, maxRetryDelayMs);
                
            default:
                return retryDelayMs;
        }
    }
    
    /**
     * Check if a status code indicates success
     */
    public boolean isSuccessStatusCode(int statusCode) {
        return successStatusCodes.contains(statusCode);
    }
    
    /**
     * Validate the step configuration
     */
    public void validate() {
        if (stepId == null || stepId.trim().isEmpty()) {
            throw new IllegalArgumentException("Step ID is required");
        }
        
        if (stepType == null || stepType.trim().isEmpty()) {
            throw new IllegalArgumentException("Step type is required");
        }
        
        if (serviceEndpoint == null || serviceEndpoint.trim().isEmpty()) {
            throw new IllegalArgumentException("Service endpoint is required");
        }
        
        if (timeoutSeconds <= 0) {
            throw new IllegalArgumentException("Timeout must be positive");
        }
        
        if (maxRetries < 0) {
            throw new IllegalArgumentException("Max retries cannot be negative");
        }
        
        if (retryDelayMs < 0) {
            throw new IllegalArgumentException("Retry delay cannot be negative");
        }
        
        if (maxRetryDelayMs < retryDelayMs) {
            throw new IllegalArgumentException("Max retry delay cannot be less than retry delay");
        }
        
        // Validate compensation endpoint if provided
        if (compensationEndpoint != null && compensationEndpoint.trim().isEmpty()) {
            throw new IllegalArgumentException("Compensation endpoint cannot be empty if provided");
        }
        
        // Validate dependencies don't include self
        if (dependencies.contains(stepId)) {
            throw new IllegalArgumentException("Step cannot depend on itself");
        }
    }
    
    /**
     * Create a copy of this step
     */
    public SagaStep copy() {
        return SagaStep.builder()
            .stepId(this.stepId)
            .stepType(this.stepType)
            .name(this.name)
            .description(this.description)
            .serviceEndpoint(this.serviceEndpoint)
            .httpMethod(this.httpMethod)
            .parameters(new HashMap<>(this.parameters))
            .headers(new HashMap<>(this.headers))
            .dependencies(new ArrayList<>(this.dependencies))
            .compensationEndpoint(this.compensationEndpoint)
            .compensationHttpMethod(this.compensationHttpMethod)
            .compensationParameters(new HashMap<>(this.compensationParameters))
            .parallelizable(this.parallelizable)
            .idempotent(this.idempotent)
            .critical(this.critical)
            .maxRetries(this.maxRetries)
            .timeoutSeconds(this.timeoutSeconds)
            .retryStrategy(this.retryStrategy)
            .retryDelayMs(this.retryDelayMs)
            .maxRetryDelayMs(this.maxRetryDelayMs)
            .priority(this.priority)
            .successStatusCodes(new HashSet<>(this.successStatusCodes))
            .properties(new HashMap<>(this.properties))
            .tags(new HashSet<>(this.tags))
            .validationRules(new ArrayList<>(this.validationRules))
            .outputSchema(this.outputSchema)
            .build();
    }
    
    // Private helper methods
    
    private long fibonacciDelay(int n) {
        if (n <= 1) return 1;
        if (n == 2) return 1;
        
        long a = 1, b = 1;
        for (int i = 3; i <= n; i++) {
            long temp = a + b;
            a = b;
            b = temp;
        }
        return b;
    }
}

/**
 * Retry strategies for failed steps
 */
enum RetryStrategy {
    /**
     * Fixed delay between retries
     */
    FIXED_DELAY,
    
    /**
     * Linear increase in delay
     */
    LINEAR_BACKOFF,
    
    /**
     * Exponential increase in delay
     */
    EXPONENTIAL_BACKOFF,
    
    /**
     * Fibonacci sequence for delay
     */
    FIBONACCI_BACKOFF
}

/**
 * Validation rule for step input/output
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
class ValidationRule {
    private String field;
    private String rule;
    private Object expectedValue;
    private String errorMessage;
}