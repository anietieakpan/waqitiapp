package com.waqiti.common.messaging.analysis;

import com.waqiti.common.messaging.model.MessageFailureStats;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Collectors;

/**
 * CRITICAL PRODUCTION FIX - FailureAnalysisService
 * Analyzes message failure patterns for dead letter queue optimization
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class FailureAnalysisService {
    
    private final Map<String, MessageFailureStats> failureStats = new ConcurrentHashMap<>();
    
    /**
     * Record a message failure for analysis
     */
    public void recordFailure(String messageType, Throwable error, Map<String, Object> messageContext) {
        String errorType = error.getClass().getSimpleName();
        
        failureStats.compute(messageType, (key, stats) -> {
            if (stats == null) {
                stats = MessageFailureStats.builder()
                        .messageType(messageType)
                        .build();
            }
            stats.recordFailure(errorType, error.getMessage(), messageContext);
            return stats;
        });
        
        log.debug("Recorded failure for message type {} with error {}", messageType, errorType);
    }
    
    /**
     * Identify failure patterns
     */
    public List<FailurePattern> identifyPatterns(String messageType) {
        MessageFailureStats stats = failureStats.get(messageType);
        if (stats == null) {
            return List.of();
        }
        
        return stats.getErrorCounts().entrySet().stream()
            .filter(entry -> entry.getValue() > 5) // Threshold for pattern
            .map(entry -> {
                FailurePattern pattern = new FailurePattern();
                pattern.setPatternId(messageType + "_" + entry.getKey());
                pattern.setDescription("Repeated " + entry.getKey() + " failures for " + messageType);
                pattern.setSeverity(determineSeverityEnum(entry.getValue()));
                pattern.setAffectedTopic(messageType);
                pattern.setFailureType(entry.getKey());
                pattern.setOccurrenceCount(entry.getValue());
                pattern.setFirstOccurrence(stats.getFirstFailure());
                pattern.setLastOccurrence(stats.getLastFailure());
                pattern.setRecommendedAction(generateRecommendation(entry.getKey()));
                return pattern;
            })
            .collect(Collectors.toList());
    }
    
    /**
     * Get failure statistics
     */
    public MessageFailureStats getFailureStats(String messageType) {
        return failureStats.getOrDefault(messageType, MessageFailureStats.builder()
                .messageType(messageType)
                .build());
    }
    
    /**
     * Check if message type is problematic
     */
    public boolean isProblematicMessageType(String messageType) {
        MessageFailureStats stats = failureStats.get(messageType);
        return stats != null && stats.getTotalFailures() > 10;
    }
    
    /**
     * Analyze failure patterns from provided stats map
     */
    public List<FailurePattern> analyzeFailurePatterns(Map<String, MessageFailureStats> providedStats) {
        return providedStats.entrySet().stream()
            .flatMap(entry -> identifyPatterns(entry.getKey()).stream())
            .collect(Collectors.toList());
    }
    
    /**
     * Analyze recovery failure
     */
    public void analyzeRecoveryFailure(com.waqiti.common.messaging.model.DeadLetterMessage dlqMessage) {
        log.debug("Analyzing recovery failure for message: {}", dlqMessage.getId());
        recordFailure(dlqMessage.getOriginalTopic(), 
                     new RuntimeException(dlqMessage.getFailureReason()), 
                     Map.of("messageId", dlqMessage.getId()));
    }
    
    private FailureSeverity determineSeverityEnum(int occurrences) {
        if (occurrences > 50) return FailureSeverity.CRITICAL;
        if (occurrences > 20) return FailureSeverity.HIGH;
        if (occurrences > 10) return FailureSeverity.MEDIUM;
        return FailureSeverity.LOW;
    }
    
    private String generateRecommendation(String errorType) {
        return switch (errorType) {
            case "TimeoutException" -> "Increase timeout configuration or check downstream service health";
            case "ConnectException" -> "Verify network connectivity and service availability";
            case "SerializationException" -> "Check message format compatibility";
            case "SecurityException" -> "Review authentication and authorization configuration";
            default -> "Review error logs and consider circuit breaker implementation";
        };
    }
}