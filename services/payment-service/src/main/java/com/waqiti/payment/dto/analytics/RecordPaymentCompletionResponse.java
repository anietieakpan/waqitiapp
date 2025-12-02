package com.waqiti.payment.dto.analytics;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.io.Serializable;
import java.util.UUID;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class RecordPaymentCompletionResponse implements Serializable {
    
    private static final long serialVersionUID = 1L;
    
    private UUID paymentId;
    private boolean recorded;
    private String message;
    private boolean requiresAsyncProcessing;
}