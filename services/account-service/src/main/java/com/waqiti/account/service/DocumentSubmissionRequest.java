package com.waqiti.account.service;

import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

@lombok.Data
@lombok.Builder
public class DocumentSubmissionRequest {
    private UUID userId;
    private List<KycDocument> documents;
    private LocalDateTime submittedAt;
    private String source;
}
