package com.waqiti.notification.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.*;

/**
 * Service for handling unsubscribe requests and opt-out management
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class UnsubscribeService {
    
    private final NotificationPreferencesService preferencesService;
    
    // Global suppression list (in production, this would be persistent storage)
    private final Set<String> globalSuppressionList = new HashSet<>();
    private final Map<String, UnsubscribeRecord> unsubscribeRecords = new HashMap<>();
    
    /**
     * Process unsubscribe request from various sources
     */
    @Transactional
    public UnsubscribeResult processUnsubscribe(UnsubscribeRequest request) {
        try {
            log.info("Processing unsubscribe request for {} via {}", 
                request.getIdentifier(), request.getSource());
            
            UnsubscribeResult.UnsubscribeResultBuilder result = UnsubscribeResult.builder();
            
            // Validate request
            if (!isValidUnsubscribeRequest(request)) {
                return result
                    .success(false)
                    .message("Invalid unsubscribe request")
                    .build();
            }
            
            // Find user by identifier
            String userId = findUserByIdentifier(request.getIdentifier(), request.getIdentifierType());
            if (userId == null) {
                return result
                    .success(false)
                    .message("User not found")
                    .build();
            }
            
            // Process based on scope
            switch (request.getScope()) {
                case ALL:
                    unsubscribeFromAll(userId, request);
                    break;
                case CHANNEL:
                    unsubscribeFromChannel(userId, request.getChannel(), request);
                    break;
                case TYPE:
                    unsubscribeFromType(userId, request.getNotificationType(), request);
                    break;
                case SPECIFIC:
                    unsubscribeFromSpecificSource(userId, request.getSpecificSource(), request);
                    break;
                default:
                    return result
                        .success(false)
                        .message("Invalid unsubscribe scope")
                        .build();
            }
            
            // Record the unsubscribe
            recordUnsubscribe(userId, request);
            
            // Add to suppression list if applicable
            if (request.getScope() == UnsubscribeScope.ALL || 
                request.getScope() == UnsubscribeScope.CHANNEL) {
                addToSuppressionList(request.getIdentifier(), request.getScope(), request.getChannel());
            }
            
            // Send confirmation if requested
            if (request.isSendConfirmation()) {
                sendUnsubscribeConfirmation(userId, request);
            }
            
            log.info("Successfully processed unsubscribe for user {} with scope {}", 
                userId, request.getScope());
            
            return result
                .success(true)
                .userId(userId)
                .message("Unsubscribe processed successfully")
                .effectiveDate(LocalDateTime.now())
                .build();
                
        } catch (Exception e) {
            log.error("Error processing unsubscribe request: {}", e.getMessage(), e);
            return UnsubscribeResult.builder()
                .success(false)
                .message("Error processing unsubscribe: " + e.getMessage())
                .build();
        }
    }
    
    /**
     * Process resubscribe request
     */
    @Transactional
    public ResubscribeResult processResubscribe(ResubscribeRequest request) {
        try {
            log.info("Processing resubscribe request for {}", request.getIdentifier());
            
            // Validate request
            if (!isValidResubscribeRequest(request)) {
                return ResubscribeResult.builder()
                    .success(false)
                    .message("Invalid resubscribe request")
                    .build();
            }
            
            String userId = findUserByIdentifier(request.getIdentifier(), request.getIdentifierType());
            if (userId == null) {
                return ResubscribeResult.builder()
                    .success(false)
                    .message("User not found")
                    .build();
            }
            
            // Process resubscribe
            switch (request.getScope()) {
                case ALL:
                    resubscribeToAll(userId, request);
                    break;
                case CHANNEL:
                    resubscribeToChannel(userId, request.getChannel(), request);
                    break;
                case TYPE:
                    resubscribeToType(userId, request.getNotificationType(), request);
                    break;
                case SPECIFIC:
                    resubscribeToSpecificSource(userId, request.getSpecificSource(), request);
                    break;
                default:
                    return ResubscribeResult.builder()
                        .success(false)
                        .message("Invalid resubscribe scope")
                        .build();
            }
            
            // Remove from suppression list if applicable
            removeFromSuppressionList(request.getIdentifier());
            
            // Send confirmation
            if (request.isSendConfirmation()) {
                sendResubscribeConfirmation(userId, request);
            }
            
            log.info("Successfully processed resubscribe for user {} with scope {}", 
                userId, request.getScope());
            
            return ResubscribeResult.builder()
                .success(true)
                .userId(userId)
                .message("Resubscribe processed successfully")
                .effectiveDate(LocalDateTime.now())
                .build();
                
        } catch (Exception e) {
            log.error("Error processing resubscribe request: {}", e.getMessage(), e);
            return ResubscribeResult.builder()
                .success(false)
                .message("Error processing resubscribe: " + e.getMessage())
                .build();
        }
    }
    
    /**
     * Check if identifier is suppressed
     */
    public boolean isSuppressed(String identifier, String channel, String notificationType) {
        try {
            // Check global suppression
            if (globalSuppressionList.contains(identifier.toLowerCase().trim())) {
                return true;
            }
            
            // Check channel-specific suppression
            String channelKey = identifier.toLowerCase().trim() + ":" + channel.toLowerCase();
            if (globalSuppressionList.contains(channelKey)) {
                return true;
            }
            
            // Check unsubscribe records
            UnsubscribeRecord record = unsubscribeRecords.get(identifier.toLowerCase().trim());
            if (record != null) {
                return record.isSuppressionActive(channel, notificationType);
            }
            
            return false;
            
        } catch (Exception e) {
            log.error("Error checking suppression status: {}", e.getMessage());
            // Default to not suppressed to avoid blocking legitimate notifications
            return false;
        }
    }
    
    /**
     * Get unsubscribe statistics
     */
    public UnsubscribeStatistics getUnsubscribeStatistics() {
        try {
            UnsubscribeStatistics stats = new UnsubscribeStatistics();
            
            // Total unsubscribes
            stats.setTotalUnsubscribes(unsubscribeRecords.size());
            
            // Unsubscribes by channel
            Map<String, Integer> byChannel = new HashMap<>();
            Map<String, Integer> byType = new HashMap<>();
            Map<String, Integer> byReason = new HashMap<>();
            
            unsubscribeRecords.values().forEach(record -> {
                // Count by channel
                record.getChannels().forEach(channel -> 
                    byChannel.merge(channel, 1, Integer::sum));
                
                // Count by type
                record.getNotificationTypes().forEach(type -> 
                    byType.merge(type, 1, Integer::sum));
                
                // Count by reason
                if (record.getReason() != null) {
                    byReason.merge(record.getReason(), 1, Integer::sum);
                }
            });
            
            stats.setUnsubscribesByChannel(byChannel);
            stats.setUnsubscribesByType(byType);
            stats.setUnsubscribesByReason(byReason);
            
            // Recent unsubscribes (last 30 days)
            long recentCount = unsubscribeRecords.values().stream()
                .filter(record -> record.getUnsubscribedAt().isAfter(LocalDateTime.now().minusDays(30)))
                .count();
            stats.setRecentUnsubscribes((int) recentCount);
            
            return stats;
            
        } catch (Exception e) {
            log.error("Error generating unsubscribe statistics: {}", e.getMessage());
            return new UnsubscribeStatistics();
        }
    }
    
    // Private helper methods
    
    private boolean isValidUnsubscribeRequest(UnsubscribeRequest request) {
        return request.getIdentifier() != null && 
               !request.getIdentifier().trim().isEmpty() &&
               request.getIdentifierType() != null &&
               request.getScope() != null;
    }
    
    private boolean isValidResubscribeRequest(ResubscribeRequest request) {
        return request.getIdentifier() != null && 
               !request.getIdentifier().trim().isEmpty() &&
               request.getIdentifierType() != null &&
               request.getScope() != null;
    }
    
    private String findUserByIdentifier(String identifier, IdentifierType type) {
        try {
            // In production, this would query the user database
            switch (type) {
                case EMAIL:
                    // return userRepository.findByEmail(identifier).map(User::getId).orElse(null);
                    break;
                case PHONE:
                    // return userRepository.findByPhoneNumber(identifier).map(User::getId).orElse(null);
                    break;
                case USER_ID:
                    return identifier;
            }
            
            // For now, simulate user lookup
            return "user_" + identifier.hashCode();
            
        } catch (Exception e) {
            log.error("Error finding user by identifier: {}", e.getMessage());
            return null;
        }
    }
    
    private void unsubscribeFromAll(String userId, UnsubscribeRequest request) {
        preferencesService.disableAllNotifications(userId, request.getReason());
        log.info("Unsubscribed user {} from all notifications", userId);
    }
    
    private void unsubscribeFromChannel(String userId, String channel, UnsubscribeRequest request) {
        preferencesService.disableChannelNotifications(userId, channel, request.getReason());
        log.info("Unsubscribed user {} from {} notifications", userId, channel);
    }
    
    private void unsubscribeFromType(String userId, String notificationType, UnsubscribeRequest request) {
        preferencesService.disableNotificationType(userId, notificationType, request.getReason());
        log.info("Unsubscribed user {} from {} notification type", userId, notificationType);
    }
    
    private void unsubscribeFromSpecificSource(String userId, String source, UnsubscribeRequest request) {
        preferencesService.disableSourceNotifications(userId, source, request.getReason());
        log.info("Unsubscribed user {} from {} source notifications", userId, source);
    }
    
    private void resubscribeToAll(String userId, ResubscribeRequest request) {
        preferencesService.enableAllNotifications(userId);
        log.info("Resubscribed user {} to all notifications", userId);
    }
    
    private void resubscribeToChannel(String userId, String channel, ResubscribeRequest request) {
        preferencesService.enableChannelNotifications(userId, channel);
        log.info("Resubscribed user {} to {} notifications", userId, channel);
    }
    
    private void resubscribeToType(String userId, String notificationType, ResubscribeRequest request) {
        preferencesService.enableNotificationType(userId, notificationType);
        log.info("Resubscribed user {} to {} notification type", userId, notificationType);
    }
    
    private void resubscribeToSpecificSource(String userId, String source, ResubscribeRequest request) {
        preferencesService.enableSourceNotifications(userId, source);
        log.info("Resubscribed user {} to {} source notifications", userId, source);
    }
    
    private void recordUnsubscribe(String userId, UnsubscribeRequest request) {
        try {
            String key = request.getIdentifier().toLowerCase().trim();
            UnsubscribeRecord record = unsubscribeRecords.getOrDefault(key, new UnsubscribeRecord());
            
            record.setIdentifier(key);
            record.setUserId(userId);
            record.setUnsubscribedAt(LocalDateTime.now());
            record.setReason(request.getReason());
            record.setSource(request.getSource());
            record.setScope(request.getScope());
            
            // Add channels and types based on scope
            switch (request.getScope()) {
                case ALL:
                    record.getChannels().addAll(List.of("EMAIL", "SMS", "PUSH", "IN_APP"));
                    break;
                case CHANNEL:
                    record.getChannels().add(request.getChannel());
                    break;
                case TYPE:
                    record.getNotificationTypes().add(request.getNotificationType());
                    break;
                case SPECIFIC:
                    record.getSpecificSources().add(request.getSpecificSource());
                    break;
            }
            
            unsubscribeRecords.put(key, record);
            
        } catch (Exception e) {
            log.error("Error recording unsubscribe: {}", e.getMessage());
        }
    }
    
    private void addToSuppressionList(String identifier, UnsubscribeScope scope, String channel) {
        try {
            String key = identifier.toLowerCase().trim();
            
            if (scope == UnsubscribeScope.ALL) {
                globalSuppressionList.add(key);
            } else if (scope == UnsubscribeScope.CHANNEL && channel != null) {
                globalSuppressionList.add(key + ":" + channel.toLowerCase());
            }
            
        } catch (Exception e) {
            log.error("Error adding to suppression list: {}", e.getMessage());
        }
    }
    
    private void removeFromSuppressionList(String identifier) {
        try {
            String key = identifier.toLowerCase().trim();
            globalSuppressionList.remove(key);
            
            // Remove channel-specific entries
            globalSuppressionList.removeIf(entry -> entry.startsWith(key + ":"));
            
        } catch (Exception e) {
            log.error("Error removing from suppression list: {}", e.getMessage());
        }
    }
    
    private void sendUnsubscribeConfirmation(String userId, UnsubscribeRequest request) {
        try {
            // In production, this would send a confirmation notification
            log.info("Unsubscribe confirmation would be sent to user {}", userId);
            
        } catch (Exception e) {
            log.error("Error sending unsubscribe confirmation: {}", e.getMessage());
        }
    }
    
    private void sendResubscribeConfirmation(String userId, ResubscribeRequest request) {
        try {
            // In production, this would send a confirmation notification
            log.info("Resubscribe confirmation would be sent to user {}", userId);
            
        } catch (Exception e) {
            log.error("Error sending resubscribe confirmation: {}", e.getMessage());
        }
    }
    
    // Data classes
    
    public enum IdentifierType {
        EMAIL, PHONE, USER_ID
    }
    
    public enum UnsubscribeScope {
        ALL, CHANNEL, TYPE, SPECIFIC
    }
    
    @lombok.Builder
    @lombok.Data
    public static class UnsubscribeRequest {
        private String identifier;
        private IdentifierType identifierType;
        private UnsubscribeScope scope;
        private String channel;
        private String notificationType;
        private String specificSource;
        private String reason;
        private String source;
        private boolean sendConfirmation;
    }
    
    @lombok.Builder
    @lombok.Data
    public static class ResubscribeRequest {
        private String identifier;
        private IdentifierType identifierType;
        private UnsubscribeScope scope;
        private String channel;
        private String notificationType;
        private String specificSource;
        private boolean sendConfirmation;
    }
    
    @lombok.Builder
    @lombok.Data
    public static class UnsubscribeResult {
        private boolean success;
        private String userId;
        private String message;
        private LocalDateTime effectiveDate;
    }
    
    @lombok.Builder
    @lombok.Data
    public static class ResubscribeResult {
        private boolean success;
        private String userId;
        private String message;
        private LocalDateTime effectiveDate;
    }
    
    @lombok.Data
    public static class UnsubscribeRecord {
        private String identifier;
        private String userId;
        private LocalDateTime unsubscribedAt;
        private String reason;
        private String source;
        private UnsubscribeScope scope;
        private Set<String> channels = new HashSet<>();
        private Set<String> notificationTypes = new HashSet<>();
        private Set<String> specificSources = new HashSet<>();
        
        public boolean isSuppressionActive(String channel, String notificationType) {
            if (scope == UnsubscribeScope.ALL) {
                return true;
            }
            
            if (scope == UnsubscribeScope.CHANNEL && channels.contains(channel)) {
                return true;
            }
            
            if (scope == UnsubscribeScope.TYPE && notificationTypes.contains(notificationType)) {
                return true;
            }
            
            return false;
        }
    }
    
    @lombok.Data
    public static class UnsubscribeStatistics {
        private int totalUnsubscribes;
        private int recentUnsubscribes;
        private Map<String, Integer> unsubscribesByChannel = new HashMap<>();
        private Map<String, Integer> unsubscribesByType = new HashMap<>();
        private Map<String, Integer> unsubscribesByReason = new HashMap<>();
    }
}