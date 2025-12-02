package com.waqiti.gdpr.dto;

import com.waqiti.gdpr.domain.*;
import lombok.Builder;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@Builder
public class ConsentRecordDTO {
    private String id;
    private String userId;
    private ConsentPurpose purpose;
    private ConsentStatus status;
    private String consentVersion;
    private LocalDateTime grantedAt;
    private LocalDateTime withdrawnAt;
    private LocalDateTime expiresAt;
    private CollectionMethod collectionMethod;
    private String consentText;
    private LawfulBasis lawfulBasis;
    private String thirdParties;
    private Integer dataRetentionDays;
    private boolean isActive;
}