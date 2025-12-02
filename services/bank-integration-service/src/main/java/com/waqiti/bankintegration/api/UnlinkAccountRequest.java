package com.waqiti.bankintegration.api;

@lombok.Data
@lombok.Builder
public class UnlinkAccountRequest {
    private String userId;
    private String reason;
}
