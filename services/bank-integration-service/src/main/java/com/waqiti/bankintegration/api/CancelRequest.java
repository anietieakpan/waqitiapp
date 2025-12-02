package com.waqiti.bankintegration.api;

@lombok.Data
@lombok.Builder
public class CancelRequest {
    private String reason;
    private String apiKey;
}
