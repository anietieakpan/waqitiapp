package com.waqiti.common.kyc.model;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

/**
 * Onfido document model for KYC verification
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class OnfidoDocument {
    
    private String id;
    private String href;
    private String type;
    private String side;
    private String issuingCountry;
    private String fileName;
    private String fileType;
    private int fileSize;
    private LocalDateTime createdAt;
    private String downloadHref;
    
    /**
     * Document types
     */
    public enum DocumentSide {
        FRONT,
        BACK,
        SINGLE
    }
    
    /**
     * Check if document is valid
     */
    public boolean isValid() {
        return id != null && type != null && createdAt != null;
    }
    
    /**
     * Check if document has both sides
     */
    public boolean requiresBothSides() {
        return "driving_licence".equals(type) || "national_identity_card".equals(type);
    }
}