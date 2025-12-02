package com.waqiti.support.dto.gdpr;

import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.AllArgsConstructor;
import lombok.Builder;

import java.time.LocalDateTime;

/**
 * Chat session export DTO for GDPR data export.
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class ChatSessionExportDTO {

    private String sessionId;
    private LocalDateTime startTime;
    private LocalDateTime endTime;
    private int messageCount;
    private String status;
    private LocalDateTime exportedAt;
}
