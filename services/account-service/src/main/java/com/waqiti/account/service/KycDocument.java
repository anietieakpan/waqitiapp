package com.waqiti.account.service;

import java.util.Map;

@lombok.Data
@lombok.Builder
public class KycDocument {
    private String documentType;
    private String documentId;
    private byte[] documentData;
    private String mimeType;
    private Map<String, String> metadata;
}
