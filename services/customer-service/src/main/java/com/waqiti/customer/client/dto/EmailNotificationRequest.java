package com.waqiti.customer.client.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import java.util.List;
import java.util.Map;

/**
 * Request DTO for sending email notifications to notification-service.
 *
 * @author Waqiti Engineering Team
 * @version 1.0
 * @since 2025-11-20
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class EmailNotificationRequest {

    /**
     * Recipient email address
     */
    @NotBlank(message = "To email is required")
    @Email(message = "To email must be valid")
    private String to;

    /**
     * CC email addresses
     */
    private List<String> cc;

    /**
     * BCC email addresses
     */
    private List<String> bcc;

    /**
     * Email subject
     */
    @NotBlank(message = "Subject is required")
    private String subject;

    /**
     * Email body (plain text or HTML)
     */
    @NotBlank(message = "Body is required")
    private String body;

    /**
     * Whether body is HTML
     */
    private Boolean isHtml;

    /**
     * Reply-to email address
     */
    @Email(message = "Reply-to email must be valid")
    private String replyTo;

    /**
     * Email template ID (if using template)
     */
    private String templateId;

    /**
     * Template parameters
     */
    private Map<String, Object> templateParameters;

    /**
     * Attachments (file names and URLs)
     */
    private Map<String, String> attachments;

    /**
     * Priority level
     */
    private String priority;

    /**
     * Additional headers
     */
    private Map<String, String> headers;
}
