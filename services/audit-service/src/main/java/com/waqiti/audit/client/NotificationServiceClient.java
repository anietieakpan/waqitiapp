package com.waqiti.audit.client;

import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;

/**
 * Feign client for notification service communication
 */
@FeignClient(name = "notification-service", path = "/api/v1/notifications")
public interface NotificationServiceClient {
    
    @PostMapping("/send")
    void sendNotification(@RequestBody NotificationRequest request);
    
    @PostMapping("/alert")
    void sendAlert(@RequestBody AlertRequest request);
    
    @PostMapping("/audit-alert")
    void sendAuditAlert(@RequestBody AuditAlertRequest request);
    
    /**
     * Notification request DTO
     */
    class NotificationRequest {
        private String recipientId;
        private String type;
        private String subject;
        private String message;
        private Object metadata;
        
        // Getters and setters
        public String getRecipientId() { return recipientId; }
        public void setRecipientId(String recipientId) { this.recipientId = recipientId; }
        public String getType() { return type; }
        public void setType(String type) { this.type = type; }
        public String getSubject() { return subject; }
        public void setSubject(String subject) { this.subject = subject; }
        public String getMessage() { return message; }
        public void setMessage(String message) { this.message = message; }
        public Object getMetadata() { return metadata; }
        public void setMetadata(Object metadata) { this.metadata = metadata; }
    }
    
    /**
     * Alert request DTO
     */
    class AlertRequest {
        private String level;
        private String title;
        private String description;
        private Object details;
        
        // Getters and setters
        public String getLevel() { return level; }
        public void setLevel(String level) { this.level = level; }
        public String getTitle() { return title; }
        public void setTitle(String title) { this.title = title; }
        public String getDescription() { return description; }
        public void setDescription(String description) { this.description = description; }
        public Object getDetails() { return details; }
        public void setDetails(Object details) { this.details = details; }
    }
    
    /**
     * Audit alert request DTO
     */
    class AuditAlertRequest {
        private String auditEventId;
        private String severity;
        private String message;
        private String[] recipients;
        private Object auditDetails;
        
        // Getters and setters
        public String getAuditEventId() { return auditEventId; }
        public void setAuditEventId(String auditEventId) { this.auditEventId = auditEventId; }
        public String getSeverity() { return severity; }
        public void setSeverity(String severity) { this.severity = severity; }
        public String getMessage() { return message; }
        public void setMessage(String message) { this.message = message; }
        public String[] getRecipients() { return recipients; }
        public void setRecipients(String[] recipients) { this.recipients = recipients; }
        public Object getAuditDetails() { return auditDetails; }
        public void setAuditDetails(Object auditDetails) { this.auditDetails = auditDetails; }
    }
}