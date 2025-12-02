package com.waqiti.dispute.dto;

import lombok.Builder;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * File Upload Result DTO
 *
 * Contains metadata about a successfully uploaded file
 *
 * @author Waqiti Production Team
 * @version 2.0.0-PRODUCTION
 */
@Data
@Builder
public class FileUploadResult {
    private String fileId;
    private String originalFilename;
    private String secureFilename;
    private String storagePath;
    private String mimeType;
    private long fileSizeBytes;
    private String fileHash;
    private String uploadedBy;
    private String disputeId;
    private LocalDateTime uploadedAt;
    private boolean encrypted;
    private boolean virusScanned;
}
