package com.waqiti.bankintegration.api;

import java.util.Map;

@lombok.Data
@lombok.Builder
public class InstantVerificationRequest {
    private String userId;
    private String bankId;
    private Map<String, Object> credentials;
}
