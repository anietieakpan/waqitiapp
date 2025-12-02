package com.waqiti.customer.events.model;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.io.Serializable;
import java.time.Instant;

/**
 * Dead letter wrapper for failed customer events
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class DeadLetterCustomerEvent implements Serializable {
    
    private static final long serialVersionUID = 1L;
    
    private CustomerEvent originalEvent;
    private String originalTopic;
    private String errorMessage;
    private Instant failureTimestamp;
    private int retryCount;
    private String dlqEventId;
    
    public DeadLetterCustomerEvent(CustomerEvent originalEvent, String originalTopic, 
                               String errorMessage, Instant failureTimestamp, int retryCount) {
        this.originalEvent = originalEvent;
        this.originalTopic = originalTopic;
        this.errorMessage = errorMessage;
        this.failureTimestamp = failureTimestamp;
        this.retryCount = retryCount;
        this.dlqEventId = "dlq-" + originalEvent.getEventId();
    }
}