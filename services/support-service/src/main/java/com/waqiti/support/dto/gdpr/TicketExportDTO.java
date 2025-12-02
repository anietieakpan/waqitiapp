package com.waqiti.support.dto.gdpr;

import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.AllArgsConstructor;
import lombok.Builder;

import java.time.LocalDateTime;

/**
 * Ticket export DTO for GDPR data export.
 * Contains essential ticket information without sensitive agent data.
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class TicketExportDTO {

    private String ticketNumber;
    private String subject;
    private String description;
    private String status;
    private String priority;
    private String category;

    private LocalDateTime createdAt;
    private LocalDateTime resolvedAt;
    private LocalDateTime closedAt;

    private int messageCount;
    private Integer satisfactionScore;

    // Soft delete information
    private boolean deleted;
    private LocalDateTime deletedAt;
    private String deletionReason;
}
