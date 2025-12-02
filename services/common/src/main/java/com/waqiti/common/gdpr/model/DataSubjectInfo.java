package com.waqiti.common.gdpr.model;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Data Subject Information
 *
 * Summary of personal data held for a data subject
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class DataSubjectInfo {

    private UUID userId;
    private String email;
    private Map<String, Object> personalData;
    private List<String> dataCategories;
    private List<ConsentRecord> consents;
    private List<ProcessingActivity> processingActivities;
    private List<ThirdPartySharing> thirdPartySharing;
    private RetentionInfo retentionInfo;
}
