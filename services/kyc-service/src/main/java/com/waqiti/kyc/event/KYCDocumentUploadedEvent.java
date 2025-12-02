package com.waqiti.kyc.event;

import com.waqiti.common.event.DomainEvent;
import lombok.Builder;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.time.LocalDateTime;

/**
 * Event published when a document is uploaded for KYC
 */
@Data
@Builder
@EqualsAndHashCode(callSuper = true)
public class KYCDocumentUploadedEvent extends DomainEvent {
    private String documentId;
    private String applicantId;
    private String documentType;
    private LocalDateTime uploadedAt;
    
    @Override
    public String getEventType() {
        return "kyc.document.uploaded";
    }
}