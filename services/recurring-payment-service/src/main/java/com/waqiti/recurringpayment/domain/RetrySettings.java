package com.waqiti.recurringpayment.domain;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.Set; /**
 * Retry settings for failed payments.
 */
@Embeddable
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class RetrySettings {
    
    @Column(name = "retry_enabled")
    @Builder.Default
    private boolean enabled = true;
    
    @Column(name = "max_retry_attempts")
    @Builder.Default
    private Integer maxAttempts = 3;
    
    @Column(name = "retry_delay_minutes")
    @Builder.Default
    private Integer retryDelayMinutes = 30;
    
    @Column(name = "exponential_backoff")
    @Builder.Default
    private boolean exponentialBackoff = true;
    
    @Column(name = "backoff_multiplier")
    @Builder.Default
    private Double backoffMultiplier = 2.0;
    
    @ElementCollection(fetch = FetchType.LAZY)
    @CollectionTable(name = "scheduled_payment_retry_on_errors",
                    joinColumns = @JoinColumn(name = "payment_id"))
    @Column(name = "error_code")
    private Set<String> retryOnErrorCodes;
}
