package com.waqiti.notification.domain;

import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.LocalDateTime;
import java.util.UUID;

@Entity
@Table(name = "notification_templates")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class NotificationTemplate {
    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(nullable = false, unique = true)
    private String code;

    @Column(nullable = false, length = 100)
    private String name;

    @Column(nullable = false, length = 100)
    private String category;

    @Column(nullable = false, length = 200)
    private String titleTemplate;

    @Column(nullable = false, length = 2000)
    private String messageTemplate;

    @Column(length = 2000)
    private String emailSubjectTemplate;

    @Column(length = 5000)
    private String emailBodyTemplate;

    @Column(length = 200)
    private String smsTemplate;

    @Column(name = "action_url_template")
    private String actionUrlTemplate;

    @Column(nullable = false)
    private boolean enabled;

    @Column(name = "created_at", nullable = false)
    private LocalDateTime createdAt;

    @Column(name = "updated_at", nullable = false)
    private LocalDateTime updatedAt;

    // Audit fields
    @Setter
    @Column(name = "created_by")
    private String createdBy;

    @Setter
    @Column(name = "updated_by")
    private String updatedBy;

    /**
     * Creates a new notification template
     */
    public static NotificationTemplate create(String code, String name, String category,
                                              String titleTemplate, String messageTemplate) {
        NotificationTemplate template = new NotificationTemplate();
        template.code = code;
        template.name = name;
        template.category = category;
        template.titleTemplate = titleTemplate;
        template.messageTemplate = messageTemplate;
        template.enabled = true;
        template.createdAt = LocalDateTime.now();
        template.updatedAt = LocalDateTime.now();
        return template;
    }

    /**
     * Sets the email templates
     */
    public void setEmailTemplates(String emailSubjectTemplate, String emailBodyTemplate) {
        this.emailSubjectTemplate = emailSubjectTemplate;
        this.emailBodyTemplate = emailBodyTemplate;
        this.updatedAt = LocalDateTime.now();
    }

    /**
     * Sets the SMS template
     */
    public void setSmsTemplate(String smsTemplate) {
        this.smsTemplate = smsTemplate;
        this.updatedAt = LocalDateTime.now();
    }

    /**
     * Sets the action URL template
     */
    public void setActionUrlTemplate(String actionUrlTemplate) {
        this.actionUrlTemplate = actionUrlTemplate;
        this.updatedAt = LocalDateTime.now();
    }

    /**
     * Enables or disables the template
     */
    public void setEnabled(boolean enabled) {
        this.enabled = enabled;
        this.updatedAt = LocalDateTime.now();
    }

    /**
     * Updates the template content
     */
    public void updateContent(String titleTemplate, String messageTemplate) {
        this.titleTemplate = titleTemplate;
        this.messageTemplate = messageTemplate;
        this.updatedAt = LocalDateTime.now();
    }
}