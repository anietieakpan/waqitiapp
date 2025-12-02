package com.waqiti.webhook.entity;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import jakarta.persistence.*;
import java.time.LocalDateTime;
import java.util.Map;

/**
 * Webhook delivery attempt record
 */
@Entity
@Table(name = "webhook_deliveries", indexes = {
    @Index(name = "idx_delivery_webhook", columnList = "webhook_id"),
    @Index(name = "idx_delivery_attempted", columnList = "attempted_at"),
    @Index(name = "idx_delivery_successful", columnList = "successful")
})
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class WebhookDelivery {
    
    @Id
    @Column(name = "id", nullable = false, length = 36)
    private String id;
    
    @Column(name = "webhook_id", nullable = false, length = 36)
    private String webhookId;
    
    @Column(name = "attempt_number", nullable = false)
    private int attemptNumber;
    
    @Column(name = "attempted_at", nullable = false)
    private LocalDateTime attemptedAt;
    
    @Column(name = "response_code")
    private Integer responseCode;
    
    @Column(name = "response_body", length = 2000)
    private String responseBody;
    
    @ElementCollection
    @CollectionTable(name = "delivery_response_headers", joinColumns = @JoinColumn(name = "delivery_id"))
    @MapKeyColumn(name = "header_name")
    @Column(name = "header_value")
    private Map<String, String> responseHeaders;
    
    @Column(name = "delivery_time_ms")
    private Long deliveryTime;
    
    @Column(name = "successful", nullable = false)
    private boolean successful;
    
    @Column(name = "error_message", length = 1000)
    private String errorMessage;
    
    @Column(name = "error_type", length = 100)
    private String errorType;
    
    @Column(name = "retry_after_seconds")
    private Integer retryAfterSeconds;
    
    @Version
    @Column(name = "version")
    private Long version;
    
    /**
     * Check if delivery was successful
     */
    public boolean isSuccessful() {
        return successful && responseCode != null && responseCode >= 200 && responseCode < 300;
    }
    
    /**
     * Check if delivery indicates permanent failure
     */
    public boolean isPermanentFailure() {
        return responseCode != null && (
            responseCode == 400 || // Bad Request
            responseCode == 401 || // Unauthorized
            responseCode == 403 || // Forbidden
            responseCode == 404 || // Not Found
            responseCode == 405 || // Method Not Allowed
            responseCode == 410    // Gone
        );
    }
    
    /**
     * Check if delivery indicates temporary failure
     */
    public boolean isTemporaryFailure() {
        return !successful && !isPermanentFailure();
    }
    
    /**
     * Get retry delay hint from response
     */
    public Integer getRetryDelayHint() {
        // Check Retry-After header
        if (responseHeaders != null && responseHeaders.containsKey("Retry-After")) {
            try {
                return Integer.parseInt(responseHeaders.get("Retry-After"));
            } catch (NumberFormatException e) {
                // Ignore parsing error
            }
        }
        
        return retryAfterSeconds;
    }
}