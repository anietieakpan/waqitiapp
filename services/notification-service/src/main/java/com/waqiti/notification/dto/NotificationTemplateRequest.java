package com.waqiti.notification.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor; /**
 * Request to create or update a notification template
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class NotificationTemplateRequest {
    @NotBlank(message = "Code is required")
    @Size(max = 100, message = "Code cannot exceed 100 characters")
    private String code;

    @NotBlank(message = "Name is required")
    @Size(max = 100, message = "Name cannot exceed 100 characters")
    private String name;

    @NotBlank(message = "Category is required")
    @Size(max = 100, message = "Category cannot exceed 100 characters")
    private String category;

    @NotBlank(message = "Title template is required")
    @Size(max = 200, message = "Title template cannot exceed 200 characters")
    private String titleTemplate;

    @NotBlank(message = "Message template is required")
    @Size(max = 2000, message = "Message template cannot exceed 2000 characters")
    private String messageTemplate;

    @Size(max = 2000, message = "Email subject template cannot exceed 2000 characters")
    private String emailSubjectTemplate;

    @Size(max = 5000, message = "Email body template cannot exceed 5000 characters")
    private String emailBodyTemplate;

    @Size(max = 200, message = "SMS template cannot exceed 200 characters")
    private String smsTemplate;

    @Size(max = 500, message = "Action URL template cannot exceed 500 characters")
    private String actionUrlTemplate;

    private boolean enabled;
}
