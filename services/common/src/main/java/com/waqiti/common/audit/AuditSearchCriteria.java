package com.waqiti.common.audit;

import lombok.Builder;
import lombok.Data;
import lombok.AllArgsConstructor;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;
import java.util.List;

/**
 * Search criteria for audit log queries
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class AuditSearchCriteria {

    private String userId;  // Single user ID for simple queries
    private List<String> userIds;
    private List<String> transactionIds;
    private List<String> eventTypes;
    private List<String> severityLevels;
    private LocalDateTime startTime;
    private LocalDateTime endTime;
    private String ipAddress;
    private String sessionId;
    private String correlationId;
    private String sourceService;
    private int pageNumber;
    private int pageSize;
    private String sortBy;
    private SortDirection sortDirection;

    // Additional fields for controller compatibility
    private LocalDateTime startDate;
    private LocalDateTime endDate;
    private String severity;
    private String resourceType;
    private Boolean success;
    
    /**
     * Sort direction
     */
    public enum SortDirection {
        ASC,
        DESC
    }
    
    /**
     * Create search criteria for user
     */
    public static AuditSearchCriteria forUser(String userId) {
        return AuditSearchCriteria.builder()
                .userIds(List.of(userId))
                .sortBy("timestamp")
                .sortDirection(SortDirection.DESC)
                .pageNumber(0)
                .pageSize(50)
                .build();
    }
    
    /**
     * Create search criteria for transaction
     */
    public static AuditSearchCriteria forTransaction(String transactionId) {
        return AuditSearchCriteria.builder()
                .transactionIds(List.of(transactionId))
                .sortBy("timestamp")
                .sortDirection(SortDirection.ASC)
                .pageNumber(0)
                .pageSize(100)
                .build();
    }
    
    /**
     * Create search criteria for time range
     */
    public static AuditSearchCriteria forTimeRange(LocalDateTime startTime, LocalDateTime endTime) {
        return AuditSearchCriteria.builder()
                .startTime(startTime)
                .endTime(endTime)
                .sortBy("timestamp")
                .sortDirection(SortDirection.DESC)
                .pageNumber(0)
                .pageSize(100)
                .build();
    }
    
    /**
     * Add time range filter
     */
    public AuditSearchCriteria withTimeRange(LocalDateTime startTime, LocalDateTime endTime) {
        this.startTime = startTime;
        this.endTime = endTime;
        return this;
    }
    
    /**
     * Add event type filter
     */
    public AuditSearchCriteria withEventTypes(List<String> eventTypes) {
        this.eventTypes = eventTypes;
        return this;
    }
    
    /**
     * Add pagination
     */
    public AuditSearchCriteria withPagination(int pageNumber, int pageSize) {
        this.pageNumber = pageNumber;
        this.pageSize = pageSize;
        return this;
    }
    
    /**
     * Get single user ID (for backward compatibility)
     */
    public String getUserId() {
        if (userId != null) return userId;
        return userIds != null && !userIds.isEmpty() ? userIds.get(0) : null;
    }
    
    /**
     * Get single transaction ID (for backward compatibility)
     */
    public String getTransactionId() {
        return transactionIds != null && !transactionIds.isEmpty() ? transactionIds.get(0) : null;
    }
    
    /**
     * Get start date (for backward compatibility)
     */
    public LocalDateTime getStartDate() {
        return startTime;
    }
    
    /**
     * Get end date (for backward compatibility)
     */
    public LocalDateTime getEndDate() {
        return endTime;
    }
    
    /**
     * Get audit type (for backward compatibility)
     */
    public String getType() {
        return eventTypes != null && !eventTypes.isEmpty() ? eventTypes.get(0) : null;
    }

    /**
     * Set event type (for builder compatibility)
     */
    public AuditSearchCriteria eventType(String eventType) {
        if (eventType != null) {
            this.eventTypes = List.of(eventType);
        }
        return this;
    }

    /**
     * Custom builder that supports eventType() method
     */
    public static class AuditSearchCriteriaBuilder {
        public AuditSearchCriteriaBuilder eventType(String eventType) {
            if (eventType != null) {
                this.eventTypes = List.of(eventType);
            }
            return this;
        }
    }
}