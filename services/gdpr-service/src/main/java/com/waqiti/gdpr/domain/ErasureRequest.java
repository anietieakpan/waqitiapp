package com.waqiti.gdpr.domain;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.Map;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ErasureRequest {
    private String userId;
    private Map<String, String> verificationData;
    private String reason;
    private boolean fullErasure;
}