package com.waqiti.notification.model;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;
import java.util.List;

/**
 * Email template model for database persistence
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class EmailTemplate {
    
    private String id;
    private String name;
    private String description;
    private String subject;
    private String htmlContent;
    private String textContent;
    private String category;
    private String locale;
    private List<String> variables;
    private boolean active;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
    private String createdBy;
    private String updatedBy;
    private int version;
    private String previewUrl;
    
    public boolean isExpired() {
        return !active || (updatedAt != null && updatedAt.isBefore(LocalDateTime.now().minusDays(30)));
    }
    
    public boolean hasVariables() {
        return variables != null && !variables.isEmpty();
    }
    
    public String getDisplayName() {
        return name != null ? name : id;
    }
    
    public String getCategoryDisplay() {
        if (category == null) return "General";
        
        switch (category.toLowerCase()) {
            case "transactional": return "Transactional";
            case "marketing": return "Marketing";
            case "system": return "System";
            case "alert": return "Alert";
            default: return category;
        }
    }
    
    public String getLocaleDisplay() {
        if (locale == null) return "Default";
        
        switch (locale.toLowerCase()) {
            case "en-us": return "English (US)";
            case "en-gb": return "English (UK)";
            case "es": return "Spanish";
            case "fr": return "French";
            case "de": return "German";
            default: return locale;
        }
    }
}