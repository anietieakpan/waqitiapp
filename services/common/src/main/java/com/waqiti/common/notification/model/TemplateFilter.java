package com.waqiti.common.notification.model;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

/**
 * Filter for searching notification templates
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class TemplateFilter {
    
    /**
     * Template name pattern
     */
    private String namePattern;
    
    /**
     * Template categories
     */
    private List<String> categories;
    
    /**
     * Template status
     */
    private NotificationTemplate.TemplateStatus status;
    
    /**
     * Channels to filter by
     */
    private List<NotificationChannel> channels;
    
    /**
     * Language codes
     */
    private List<String> languages;
    
    /**
     * Tags to filter by
     */
    private List<String> tags;
    
    /**
     * Author
     */
    private String author;
    
    /**
     * Include archived templates
     */
    @Builder.Default
    private boolean includeArchived = false;
    
    /**
     * Sort by field
     */
    private SortBy sortBy;
    
    /**
     * Sort order
     */
    @Builder.Default
    private SortOrder sortOrder = SortOrder.ASC;
    
    /**
     * Page number
     */
    @Builder.Default
    private int page = 0;
    
    /**
     * Page size
     */
    @Builder.Default
    private int pageSize = 20;
    
    public enum SortBy {
        NAME,
        CREATED_DATE,
        UPDATED_DATE,
        USAGE_COUNT,
        VERSION
    }
    
    public enum SortOrder {
        ASC,
        DESC
    }
}