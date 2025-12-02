package com.waqiti.payment.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * NACHA file generation result
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class NACHAFileResult {

    private String batchId;

    private String fileName;

    private String fileContent;

    private String fileHash;

    private long fileSize;

    private boolean encrypted;

    private String encryptionMethod;
}
