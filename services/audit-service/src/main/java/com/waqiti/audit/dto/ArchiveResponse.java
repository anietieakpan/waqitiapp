package com.waqiti.audit.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;
import java.util.UUID;

/**
 * Archive Response DTO
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ArchiveResponse {
    private UUID archiveId;
    private Long recordsArchived;
    private String archiveLocation;
    private Long archiveSize;
    private LocalDateTime archivedAt;
    private String status;
    private String message;
}
