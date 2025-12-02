package com.waqiti.gdpr.domain;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ErasureResult {
    private String erasureId;
    private String userId;
    private ErasureStatus status;
    private String reason;
    private List<String> retainedCategories;
    private ErasureCertificate certificate;

    public static ErasureResult complete(String erasureId, String userId, ErasureCertificate certificate) {
        return ErasureResult.builder()
            .erasureId(erasureId)
            .userId(userId)
            .status(ErasureStatus.COMPLETED)
            .certificate(certificate)
            .build();
    }

    public static ErasureResult partial(String erasureId, String userId, String reason, List<String> retainedCategories) {
        return ErasureResult.builder()
            .erasureId(erasureId)
            .userId(userId)
            .status(ErasureStatus.PARTIAL)
            .reason(reason)
            .retainedCategories(retainedCategories)
            .build();
    }
}