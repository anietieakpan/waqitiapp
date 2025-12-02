package com.waqiti.common.notification.model;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.Instant;
import java.util.List;
import java.util.Map;

/**
 * Notification template definition
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class NotificationTemplate {
    
    /**
     * Template ID
     */
    private String templateId;
    
    /**
     * Template name
     */
    private String templateName;
    
    /**
     * Template version
     */
    private String version;
    
    /**
     * Template category
     */
    private String category;
    
    /**
     * Template status
     */
    private TemplateStatus status;
    
    /**
     * Channel templates
     */
    private Map<NotificationChannel, ChannelTemplate> channelTemplates;
    
    /**
     * Required variables
     */
    private List<TemplateVariable> requiredVariables;
    
    /**
     * Optional variables
     */
    private List<TemplateVariable> optionalVariables;
    
    /**
     * Default values
     */
    private Map<String, Object> defaultValues;
    
    /**
     * Template metadata
     */
    private TemplateMetadata metadata;
    
    /**
     * Created timestamp
     */
    private Instant createdAt;
    
    /**
     * Updated timestamp
     */
    private Instant updatedAt;
    
    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class ChannelTemplate {
        private String subject;
        private String body;
        private String htmlBody;
        private Map<String, String> headers;
        private Map<String, Object> channelSpecificSettings;
        private List<String> attachmentTemplates;
    }
    
    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class TemplateVariable {
        private String name;
        private String type;
        private String description;
        private boolean required;
        private String defaultValue;
        private String format;
        private List<String> allowedValues;
        private String validation;
    }
    
    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class TemplateMetadata {
        private String description;
        private String author;
        private List<String> tags;
        private String language;
        private Map<String, String> customMetadata;
        private boolean archived;
        private String archivedReason;
    }
    
    public enum TemplateStatus {
        DRAFT,
        ACTIVE,
        INACTIVE,
        ARCHIVED,
        PENDING_APPROVAL,
        REJECTED
    }
}