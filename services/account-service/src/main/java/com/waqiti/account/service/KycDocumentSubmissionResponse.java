package com.waqiti.account.service;

import java.util.List;
import java.util.UUID;

@lombok.Data
@lombok.Builder
public class KycDocumentSubmissionResponse {
    private UUID submissionId;
    private String status;
    private List<String> acceptedDocuments;
    private List<String> rejectedDocuments;
    private String message;
}
