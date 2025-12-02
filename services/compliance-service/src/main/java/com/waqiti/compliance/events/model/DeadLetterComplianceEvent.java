package com.waqiti.compliance.events.model;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.io.Serializable;
import java.time.Instant;

/**
 * Dead letter compliance event for failed compliance events
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class DeadLetterComplianceEvent implements Serializable {
    
    private static final long serialVersionUID = 1L;
    
    private ComplianceDomainEvent originalEvent;
    private String originalTopic;
    private String errorMessage;
    private String errorStackTrace;
    private Instant failureTimestamp;
    private Integer retryCount;
    private String failureReason;
    private String processingNode;
    
    public void incrementRetryCount() {
        this.retryCount = (this.retryCount == null ? 0 : this.retryCount) + 1;
    }
}