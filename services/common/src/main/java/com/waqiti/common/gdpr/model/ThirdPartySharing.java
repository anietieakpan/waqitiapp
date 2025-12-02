package com.waqiti.common.gdpr.model;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

/**
 * Third Party Data Sharing Record
 *
 * GDPR Articles 13-14: Information about third party recipients
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ThirdPartySharing {

    private UUID sharingId;
    private UUID userId;
    private String thirdPartyName;
    private String thirdPartyCategory; // SERVICE_PROVIDER, PARTNER, AFFILIATE, etc.
    private List<String> sharedDataCategories;
    private String purpose;
    private String legalBasis;
    private LocalDateTime sharedAt;
    private Boolean userConsented;
    private String consentId;
    private Boolean isActive;
}
