package com.waqiti.notification.dto;

import com.fasterxml.jackson.annotation.JsonInclude;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;
import java.util.UUID; /**
 * Response for notification template operations
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@JsonInclude(JsonInclude.Include.NON_NULL)
public class NotificationTemplateResponse {
    private UUID id;
    private String code;
    private String name;
    private String category;
    private String titleTemplate;
    private String messageTemplate;
    private String emailSubjectTemplate;
    private String emailBodyTemplate;
    private String smsTemplate;
    private String actionUrlTemplate;
    private boolean enabled;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
}
