package com.waqiti.kyc.migration;

import com.waqiti.kyc.domain.KYCVerification;
import lombok.Data;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

@Data
public class LegacyKYCData {
    
    private String userId;
    private KYCVerification.KYCStatus status;
    private KYCVerification.VerificationLevel level;
    private LocalDateTime verifiedAt;
    private String provider;
    private String sessionId;
    private Integer attemptCount;
    private String rejectionReason;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
    private List<Map<String, Object>> documents = new ArrayList<>();
    private Map<String, Object> metadata;
    
    public boolean isEmpty() {
        return status == null && level == null && documents.isEmpty();
    }
}