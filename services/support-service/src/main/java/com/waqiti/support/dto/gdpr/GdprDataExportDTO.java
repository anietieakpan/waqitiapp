package com.waqiti.support.dto.gdpr;

import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.AllArgsConstructor;
import lombok.Builder;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

/**
 * GDPR Data Export DTO (Article 15 - Right to Access)
 *
 * Contains all user data in machine-readable format for GDPR compliance.
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class GdprDataExportDTO {

    // Metadata
    private String userId;
    private LocalDateTime exportDate;
    private String requestedBy;
    private int dataRetentionDays;

    // Support tickets
    private List<TicketExportDTO> tickets;
    private int totalTickets;
    private int activeTickets;
    private int deletedTickets;

    // Chat sessions
    private List<ChatSessionExportDTO> chatSessions;
    private int totalChatSessions;

    // User preferences
    private Map<String, String> userPreferences;

    // Processing information
    private String dataProcessingPurpose;
    private String dataController;
    private String dataProtectionOfficer;
    private List<String> dataRecipients;

    // User rights information
    private String rightToRectification;
    private String rightToErasure;
    private String rightToRestriction;
    private String rightToObject;
    private String rightToDataPortability;

    public GdprDataExportDTO(String userId, LocalDateTime exportDate, String requestedBy) {
        this.userId = userId;
        this.exportDate = exportDate;
        this.requestedBy = requestedBy;

        // Default GDPR information
        this.dataProcessingPurpose = "Customer support and service improvement";
        this.dataController = "Waqiti Financial Platform";
        this.dataProtectionOfficer = "dpo@example.com";
        this.dataRecipients = List.of("Support agents", "Analytics systems");

        // Rights information
        this.rightToRectification = "You have the right to request correction of inaccurate data. Contact support@example.com";
        this.rightToErasure = "You have the right to request deletion of your data. This export will be retained for 90 days.";
        this.rightToRestriction = "You have the right to request restriction of processing. Contact dpo@example.com";
        this.rightToObject = "You have the right to object to data processing. Contact dpo@example.com";
        this.rightToDataPortability = "This export provides your data in machine-readable JSON format.";
    }
}
