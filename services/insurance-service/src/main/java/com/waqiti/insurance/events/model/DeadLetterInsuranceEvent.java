package com.waqiti.insurance.events.model;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.io.Serializable;
import java.time.Instant;

/**
 * Dead letter wrapper for failed insurance events
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class DeadLetterInsuranceEvent implements Serializable {
    
    private static final long serialVersionUID = 1L;
    
    private InsuranceEvent originalEvent;
    private String originalTopic;
    private String errorMessage;
    private Instant failureTimestamp;
    private int retryCount;
    private String dlqEventId;
}