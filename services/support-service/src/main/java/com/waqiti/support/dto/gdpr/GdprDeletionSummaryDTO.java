package com.waqiti.support.dto.gdpr;

import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.AllArgsConstructor;
import lombok.Builder;

import java.time.LocalDateTime;

/**
 * GDPR Deletion Summary DTO (Article 17 - Right to Erasure)
 *
 * Summarizes what data was deleted for a user.
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class GdprDeletionSummaryDTO {

    private String userId;
    private LocalDateTime deletionDate;
    private LocalDateTime retentionUntil;
    private String reason;
    private String deletedBy;

    // Deletion counts
    private int ticketsDeleted;
    private int messagesDeleted;
    private int attachmentsDeleted;
    private int chatSessionsDeleted;
    private int preferencesDeleted;

    private int totalRecordsDeleted;

    // Status
    private String status; // SOFT_DELETED, PERMANENTLY_DELETED
    private String notes;

    public int getTotalRecordsDeleted() {
        return ticketsDeleted + messagesDeleted + attachmentsDeleted +
               chatSessionsDeleted + preferencesDeleted;
    }
}
