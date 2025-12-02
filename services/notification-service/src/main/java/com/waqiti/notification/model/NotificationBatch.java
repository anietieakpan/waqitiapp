package com.waqiti.notification.model;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * Model representing a batch of notifications for efficient delivery
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class NotificationBatch {
    
    private String id;
    private String templateId;
    private String category;
    private int maxSize;
    private List<Map<String, Object>> recipients;
    private LocalDateTime createdAt;
    private LocalDateTime scheduledTime;
    private boolean ready;
    private String status;
    
    public NotificationBatch(String id, String templateId, String category) {
        this.id = id;
        this.templateId = templateId;
        this.category = category;
        this.maxSize = 100;
        this.recipients = new ArrayList<>();
        this.createdAt = LocalDateTime.now();
        this.ready = false;
        this.status = "PENDING";
    }
    
    public void addRecipient(String email, Map<String, Object> data) {
        if (recipients.size() < maxSize) {
            Map<String, Object> recipient = new java.util.HashMap<>(data);
            recipient.put("email", email);
            recipients.add(recipient);
        }
    }
    
    public int getRecipientCount() {
        return recipients.size();
    }
    
    public boolean shouldProcess() {
        return recipients.size() >= maxSize || ready ||
               (scheduledTime != null && LocalDateTime.now().isAfter(scheduledTime));
    }
    
    public Map<String, Map<String, Object>> getRecipients() {
        Map<String, Map<String, Object>> recipientMap = new java.util.HashMap<>();
        for (Map<String, Object> recipient : recipients) {
            String email = (String) recipient.get("email");
            if (email != null) {
                recipientMap.put(email, recipient);
            }
        }
        return recipientMap;
    }
    
    public List<com.waqiti.notification.event.InAppNotificationEvent> getNotifications() {
        // Conversion method for compatibility
        return new ArrayList<>();
    }
    
    public int getSize() {
        return recipients.size();
    }
    
    public boolean isReady() {
        return ready || shouldProcess();
    }
    
    public String getBatchId() {
        return id;
    }
}