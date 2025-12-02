package com.waqiti/gdpr/dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;
import java.util.List;

/**
 * DTO for user communications history in GDPR export
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class UserCommunicationsDataDTO {

    private String userId;
    private List<EmailCommunicationDTO> emails;
    private List<SmsCommunicationDTO> smsMessages;
    private List<PushNotificationDTO> pushNotifications;
    private CommunicationSummaryDTO summary;

    // Data retrieval metadata
    private boolean dataRetrievalFailed;
    private String failureReason;
    private boolean requiresManualReview;
    private LocalDateTime retrievedAt;

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class EmailCommunicationDTO {
        private String emailId;
        private String subject;
        private String category;
        private LocalDateTime sentAt;
        private String status;
        private boolean opened;
        private LocalDateTime openedAt;
        private boolean clicked;
        private LocalDateTime clickedAt;
    }

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class SmsCommunicationDTO {
        private String smsId;
        private String messageType;
        private LocalDateTime sentAt;
        private String status;
        private String category;
    }

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class PushNotificationDTO {
        private String notificationId;
        private String title;
        private String category;
        private LocalDateTime sentAt;
        private String status;
        private boolean delivered;
        private boolean opened;
    }

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class CommunicationSummaryDTO {
        private Integer totalEmails;
        private Integer totalSms;
        private Integer totalPushNotifications;
        private LocalDateTime firstCommunication;
        private LocalDateTime lastCommunication;
    }
}
