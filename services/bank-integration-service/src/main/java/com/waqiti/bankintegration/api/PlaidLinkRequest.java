package com.waqiti.bankintegration.api;

@lombok.Data
@lombok.Builder
public class PlaidLinkRequest {
    private String userId;
    private String clientId;
    private String secret;
    private String publicToken;
}
