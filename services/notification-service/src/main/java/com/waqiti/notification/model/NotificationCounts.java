package com.waqiti.notification.model;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;
import java.util.Map;

/**
 * Model representing notification counts for a user
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class NotificationCounts {
    
    private String userId;
    private int totalUnread;
    private int totalRead;
    private int totalDismissed;
    private int totalExpired;
    
    // Counts by category
    private int transactionalUnread;
    private int marketingUnread;
    private int systemUnread;
    private int securityUnread;
    private int promotionalUnread;
    
    // Counts by priority
    private int urgentUnread;
    private int highUnread;
    private int normalUnread;
    private int lowUnread;
    
    // Time-based counts
    private int todayUnread;
    private int weekUnread;
    private int monthUnread;
    
    // Last update information
    private LocalDateTime lastUpdated;
    private LocalDateTime lastNotificationAt;
    
    // Additional metadata
    private Map<String, Integer> customCounts;
    
    public int getTotalNotifications() {
        return totalUnread + totalRead + totalDismissed + totalExpired;
    }
    
    public int getUnreadByCategory(String category) {
        switch (category.toLowerCase()) {
            case "transactional": return transactionalUnread;
            case "marketing": return marketingUnread;
            case "system": return systemUnread;
            case "security": return securityUnread;
            case "promotional": return promotionalUnread;
            default: return 0;
        }
    }
    
    public int getUnreadByPriority(String priority) {
        switch (priority.toLowerCase()) {
            case "urgent": return urgentUnread;
            case "high": return highUnread;
            case "normal": return normalUnread;
            case "low": return lowUnread;
            default: return 0;
        }
    }
    
    public boolean hasUnreadNotifications() {
        return totalUnread > 0;
    }
    
    public boolean hasHighPriorityUnread() {
        return urgentUnread > 0 || highUnread > 0;
    }
    
    public double getReadRate() {
        int total = getTotalNotifications();
        if (total == 0) return 0.0;
        return (double) totalRead / total;
    }
    
    public void incrementUnread(String category, String priority) {
        totalUnread++;
        todayUnread++;
        weekUnread++;
        monthUnread++;
        
        // Update category counts
        switch (category.toLowerCase()) {
            case "transactional": transactionalUnread++; break;
            case "marketing": marketingUnread++; break;
            case "system": systemUnread++; break;
            case "security": securityUnread++; break;
            case "promotional": promotionalUnread++; break;
        }
        
        // Update priority counts
        switch (priority.toLowerCase()) {
            case "urgent": urgentUnread++; break;
            case "high": highUnread++; break;
            case "normal": normalUnread++; break;
            case "low": lowUnread++; break;
        }
        
        lastUpdated = LocalDateTime.now();
        lastNotificationAt = LocalDateTime.now();
    }
    
    public void markAsRead(String category, String priority) {
        if (totalUnread > 0) {
            totalUnread--;
            totalRead++;
            
            // Update category counts
            switch (category.toLowerCase()) {
                case "transactional": 
                    if (transactionalUnread > 0) transactionalUnread--; 
                    break;
                case "marketing": 
                    if (marketingUnread > 0) marketingUnread--; 
                    break;
                case "system": 
                    if (systemUnread > 0) systemUnread--; 
                    break;
                case "security": 
                    if (securityUnread > 0) securityUnread--; 
                    break;
                case "promotional": 
                    if (promotionalUnread > 0) promotionalUnread--; 
                    break;
            }
            
            // Update priority counts
            switch (priority.toLowerCase()) {
                case "urgent": 
                    if (urgentUnread > 0) urgentUnread--; 
                    break;
                case "high": 
                    if (highUnread > 0) highUnread--; 
                    break;
                case "normal": 
                    if (normalUnread > 0) normalUnread--; 
                    break;
                case "low": 
                    if (lowUnread > 0) lowUnread--; 
                    break;
            }
            
            lastUpdated = LocalDateTime.now();
        }
    }
    
    public void markAsDismissed() {
        if (totalUnread > 0) {
            totalUnread--;
        }
        totalDismissed++;
        lastUpdated = LocalDateTime.now();
    }
    
    public void reset() {
        totalUnread = 0;
        totalRead = 0;
        totalDismissed = 0;
        totalExpired = 0;
        
        transactionalUnread = 0;
        marketingUnread = 0;
        systemUnread = 0;
        securityUnread = 0;
        promotionalUnread = 0;
        
        urgentUnread = 0;
        highUnread = 0;
        normalUnread = 0;
        lowUnread = 0;
        
        todayUnread = 0;
        weekUnread = 0;
        monthUnread = 0;
        
        lastUpdated = LocalDateTime.now();
        
        if (customCounts != null) {
            customCounts.clear();
        }
    }
}