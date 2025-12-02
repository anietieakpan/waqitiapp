package com.waqiti.gdpr.dto;

import com.waqiti.gdpr.domain.CollectionMethod;
import com.waqiti.gdpr.domain.ConsentPurpose;
import lombok.Builder;
import lombok.Data;

import jakarta.validation.constraints.NotNull;

@Data
@Builder
public class GrantConsentDTO {
    
    @NotNull(message = "Consent purpose is required")
    private ConsentPurpose purpose;
    
    @NotNull(message = "Collection method is required")
    private CollectionMethod collectionMethod;
    
    private String thirdParties;
    private Integer retentionDays;
    private Integer expiresInDays;
    private Boolean isMinor;
    private String parentalConsentId;
}