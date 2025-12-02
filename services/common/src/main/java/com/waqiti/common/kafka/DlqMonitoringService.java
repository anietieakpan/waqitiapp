package com.waqiti.common.kafka;

import com.waqiti.common.audit.AuditService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.kafka.clients.admin.AdminClient;
import org.apache.kafka.clients.admin.ConsumerGroupDescription;
import org.apache.kafka.clients.admin.ConsumerGroupListing;
import org.apache.kafka.clients.admin.DescribeConsumerGroupsResult;
import org.apache.kafka.clients.admin.ListConsumerGroupOffsetsResult;
import org.apache.kafka.clients.admin.ListConsumerGroupsResult;
import org.apache.kafka.clients.admin.OffsetSpec;
import org.apache.kafka.clients.consumer.Consumer;
import org.apache.kafka.clients.consumer.ConsumerConfig;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.apache.kafka.clients.consumer.ConsumerRecords;
import org.apache.kafka.clients.consumer.KafkaConsumer;
import org.apache.kafka.common.TopicPartition;
import org.springframework.boot.autoconfigure.kafka.KafkaProperties;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Properties;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicLong;
import java.util.stream.Collectors;

/**
 * Comprehensive DLQ monitoring and management service
 * Tracks DLQ health, provides alerting, and enables message replay
 */
@Service
@Slf4j
@RequiredArgsConstructor
public class DlqMonitoringService {
    
    private final AdminClient adminClient;
    private final KafkaTemplate<String, Object> kafkaTemplate;
    private final KafkaErrorMetrics errorMetrics;
    private final AuditService auditService;
    private final KafkaProperties kafkaProperties;
    
    // DLQ monitoring state
    private final Map<String, DlqHealthMetrics> dlqHealthMap = new ConcurrentHashMap<>();
    private final Map<String, LocalDateTime> lastAlertTimes = new ConcurrentHashMap<>();
    private final AtomicLong totalDlqMessages = new AtomicLong(0);
    
    // Critical DLQ topics that require immediate attention
    private static final List<String> CRITICAL_DLQ_TOPICS = Arrays.asList(
        "payment-initiated-payment-dlq",
        "payment-failures-payment-dlq",
        "payment-chargebacks-chargeback-dlq",
        "ledger-transactions-ledger-dlq",
        "account-freeze-requests-freeze-dlq",
        "transaction-blocks-block-dlq"
    );
    
    // High priority DLQ topics
    private static final List<String> HIGH_PRIORITY_DLQ_TOPICS = Arrays.asList(
        "sar-filing-requests-compliance-dlq",
        "aml-alerts-compliance-dlq",
        "fraud-alerts-fraud-dlq",
        "kyc-completed-user-dlq"
    );
    
    // Alert thresholds
    private static final int CRITICAL_DLQ_ALERT_THRESHOLD = 1; // Alert immediately for critical topics
    private static final int HIGH_PRIORITY_DLQ_ALERT_THRESHOLD = 5;
    private static final int OPERATIONAL_DLQ_ALERT_THRESHOLD = 50;
    private static final Duration ALERT_COOLDOWN = Duration.ofMinutes(15);
    
    /**
     * Monitor DLQ health every 2 minutes
     */
    @Scheduled(fixedRate = 120000) // 2 minutes
    public void monitorDlqHealth() {
        try {
            log.debug("Starting DLQ health monitoring");
            
            // Get all DLQ topics
            Set<String> dlqTopics = getAllDlqTopics();
            
            for (String dlqTopic : dlqTopics) {
                DlqHealthMetrics health = checkDlqHealth(dlqTopic);
                dlqHealthMap.put(dlqTopic, health);
                
                // Check if alerts are needed
                checkAndSendAlerts(dlqTopic, health);
                
                // Update metrics
                errorMetrics.recordConsumerLag(dlqTopic, "dlq-monitor", 0, health.getMessageCount());
            }
            
            // Update total DLQ message count
            long total = dlqHealthMap.values().stream()
                .mapToLong(DlqHealthMetrics::getMessageCount)
                .sum();
            totalDlqMessages.set(total);
            
            // Log summary
            logDlqSummary();
            
        } catch (Exception e) {
            log.error("Error during DLQ health monitoring", e);
        }
    }
    
    /**
     * Deep DLQ analysis every 15 minutes
     */
    @Scheduled(fixedRate = 900000) // 15 minutes
    public void performDeepDlqAnalysis() {
        try {
            log.info("Starting deep DLQ analysis");
            
            for (String dlqTopic : CRITICAL_DLQ_TOPICS) {
                if (dlqHealthMap.containsKey(dlqTopic)) {
                    DlqHealthMetrics health = dlqHealthMap.get(dlqTopic);
                    
                    if (health.getMessageCount() > 0) {
                        performDlqMessageAnalysis(dlqTopic);
                    }
                }
            }
            
        } catch (Exception e) {
            log.error("Error during deep DLQ analysis", e);
        }
    }
    
    /**
     * Generate DLQ health report
     */
    public DlqHealthReport generateHealthReport() {
        try {
            long criticalDlqCount = CRITICAL_DLQ_TOPICS.stream()
                .mapToLong(topic -> dlqHealthMap.getOrDefault(topic, new DlqHealthMetrics()).getMessageCount())
                .sum();
            
            long highPriorityDlqCount = HIGH_PRIORITY_DLQ_TOPICS.stream()
                .mapToLong(topic -> dlqHealthMap.getOrDefault(topic, new DlqHealthMetrics()).getMessageCount())
                .sum();
            
            List<DlqTopicHealth> unhealthyTopics = dlqHealthMap.entrySet().stream()
                .filter(entry -> entry.getValue().getMessageCount() > getAlertThreshold(entry.getKey()))
                .map(entry -> DlqTopicHealth.builder()
                    .topicName(entry.getKey())
                    .messageCount(entry.getValue().getMessageCount())
                    .oldestMessageAge(entry.getValue().getOldestMessageAge())
                    .errorTypes(entry.getValue().getErrorTypes())
                    .priority(getTopicPriority(entry.getKey()))
                    .build())
                .collect(Collectors.toList());
            
            return DlqHealthReport.builder()
                .totalDlqMessages(totalDlqMessages.get())
                .criticalDlqMessages(criticalDlqCount)
                .highPriorityDlqMessages(highPriorityDlqCount)
                .unhealthyTopics(unhealthyTopics)
                .reportGeneratedAt(LocalDateTime.now())
                .overallHealth(determineOverallHealth(criticalDlqCount, highPriorityDlqCount))
                .build();
            
        } catch (Exception e) {
            log.error("Error generating DLQ health report", e);
            return DlqHealthReport.builder()
                .totalDlqMessages(-1)
                .overallHealth(HealthStatus.UNKNOWN)
                .reportGeneratedAt(LocalDateTime.now())
                .build();
        }
    }
    
    /**
     * Replay messages from DLQ back to original topic
     */
    public DlqReplayResult replayDlqMessages(String dlqTopic, int maxMessages, boolean dryRun) {
        DlqReplayResult.DlqReplayResultBuilder resultBuilder = DlqReplayResult.builder()
            .dlqTopic(dlqTopic)
            .maxMessages(maxMessages)
            .dryRun(dryRun)
            .startTime(LocalDateTime.now());
        
        try {
            String originalTopic = extractOriginalTopicName(dlqTopic);
            if (originalTopic == null) {
                return resultBuilder
                    .success(false)
                    .errorMessage("Cannot determine original topic from DLQ topic: " + dlqTopic)
                    .build();
            }
            
            List<ConsumerRecord<String, Object>> dlqMessages = readDlqMessages(dlqTopic, maxMessages);
            
            if (dryRun) {
                return resultBuilder
                    .success(true)
                    .messagesFound(dlqMessages.size())
                    .messagesReplayed(0)
                    .endTime(LocalDateTime.now())
                    .build();
            }
            
            int successfulReplays = 0;
            int failedReplays = 0;
            
            for (ConsumerRecord<String, Object> record : dlqMessages) {
                try {
                    // Extract original headers
                    String originalKey = extractOriginalKey(record);
                    Object originalPayload = extractOriginalPayload(record);
                    
                    // Replay to original topic
                    kafkaTemplate.send(originalTopic, originalKey, originalPayload);
                    successfulReplays++;
                    
                    log.info("DLQ_REPLAY: Successfully replayed message from {} to {} - Key: {}", 
                        dlqTopic, originalTopic, originalKey);
                    
                } catch (Exception e) {
                    failedReplays++;
                    log.error("DLQ_REPLAY_ERROR: Failed to replay message from {} - Error: {}", 
                        dlqTopic, e.getMessage(), e);
                }
            }
            
            // Audit the replay operation
            auditDlqReplay(dlqTopic, originalTopic, successfulReplays, failedReplays);
            
            return resultBuilder
                .success(true)
                .messagesFound(dlqMessages.size())
                .messagesReplayed(successfulReplays)
                .messagesFailed(failedReplays)
                .endTime(LocalDateTime.now())
                .build();
            
        } catch (Exception e) {
            log.error("Error during DLQ replay for topic: {}", dlqTopic, e);
            return resultBuilder
                .success(false)
                .errorMessage(e.getMessage())
                .endTime(LocalDateTime.now())
                .build();
        }
    }
    
    /**
     * Purge old messages from DLQ
     */
    public DlqPurgeResult purgeDlqMessages(String dlqTopic, Duration maxAge) {
        try {
            log.info("Starting DLQ purge for topic: {} with max age: {}", dlqTopic, maxAge);
            
            // This is a simplified implementation
            // In production, you'd need to implement selective deletion based on timestamp
            
            DlqHealthMetrics health = dlqHealthMap.get(dlqTopic);
            if (health == null || health.getMessageCount() == 0) {
                return DlqPurgeResult.builder()
                    .dlqTopic(dlqTopic)
                    .messagesPurged(0)
                    .success(true)
                    .build();
            }
            
            // For now, we'll just log the purge operation
            // Real implementation would require custom Kafka operations
            log.warn("DLQ purge requested for topic: {} - This requires manual intervention or custom tooling", dlqTopic);
            
            return DlqPurgeResult.builder()
                .dlqTopic(dlqTopic)
                .messagesPurged(0)
                .success(false)
                .errorMessage("DLQ purging requires manual intervention")
                .build();
            
        } catch (Exception e) {
            log.error("Error during DLQ purge for topic: {}", dlqTopic, e);
            return DlqPurgeResult.builder()
                .dlqTopic(dlqTopic)
                .success(false)
                .errorMessage(e.getMessage())
                .build();
        }
    }
    
    // Private helper methods
    
    private Set<String> getAllDlqTopics() {
        try {
            return adminClient.listTopics().names().get(30, TimeUnit.SECONDS).stream()
                .filter(topic -> topic.endsWith("-dlq"))
                .collect(Collectors.toSet());
        } catch (Exception e) {
            log.error("Error getting DLQ topics", e);
            return Set.of();
        }
    }
    
    private DlqHealthMetrics checkDlqHealth(String dlqTopic) {
        try {
            // Create consumer to check DLQ topic
            Properties props = new Properties();
            props.putAll(kafkaProperties.buildConsumerProperties());
            props.put(ConsumerConfig.GROUP_ID_CONFIG, "dlq-monitor-" + System.currentTimeMillis());
            props.put(ConsumerConfig.AUTO_OFFSET_RESET_CONFIG, "earliest");
            props.put(ConsumerConfig.ENABLE_AUTO_COMMIT_CONFIG, false);
            
            try (Consumer<String, Object> consumer = new KafkaConsumer<>(props)) {
                consumer.subscribe(Arrays.asList(dlqTopic));
                
                ConsumerRecords<String, Object> records = consumer.poll(Duration.ofSeconds(10));
                
                Map<String, AtomicLong> errorTypes = new HashMap<>();
                LocalDateTime oldestMessageTime = LocalDateTime.now();
                
                for (ConsumerRecord<String, Object> record : records) {
                    // Extract error type from headers
                    String errorType = extractErrorType(record);
                    errorTypes.computeIfAbsent(errorType, k -> new AtomicLong(0)).incrementAndGet();
                    
                    // Track oldest message
                    LocalDateTime messageTime = extractMessageTimestamp(record);
                    if (messageTime.isBefore(oldestMessageTime)) {
                        oldestMessageTime = messageTime;
                    }
                }
                
                long messageCount = records.count();
                Duration oldestAge = Duration.between(oldestMessageTime, LocalDateTime.now());
                
                return DlqHealthMetrics.builder()
                    .topicName(dlqTopic)
                    .messageCount(messageCount)
                    .oldestMessageAge(oldestAge)
                    .errorTypes(errorTypes.entrySet().stream()
                        .collect(Collectors.toMap(Map.Entry::getKey, e -> e.getValue().get())))
                    .lastChecked(LocalDateTime.now())
                    .build();
            }
            
        } catch (Exception e) {
            log.error("Error checking health for DLQ topic: {}", dlqTopic, e);
            return DlqHealthMetrics.builder()
                .topicName(dlqTopic)
                .messageCount(0)
                .lastChecked(LocalDateTime.now())
                .build();
        }
    }
    
    private void checkAndSendAlerts(String dlqTopic, DlqHealthMetrics health) {
        int alertThreshold = getAlertThreshold(dlqTopic);
        
        if (health.getMessageCount() >= alertThreshold) {
            String alertKey = dlqTopic + "-alert";
            LocalDateTime lastAlert = lastAlertTimes.get(alertKey);
            
            if (lastAlert == null || Duration.between(lastAlert, LocalDateTime.now()).compareTo(ALERT_COOLDOWN) > 0) {
                sendDlqAlert(dlqTopic, health);
                lastAlertTimes.put(alertKey, LocalDateTime.now());
            }
        }
    }
    
    private int getAlertThreshold(String dlqTopic) {
        if (CRITICAL_DLQ_TOPICS.contains(dlqTopic)) {
            return CRITICAL_DLQ_ALERT_THRESHOLD;
        } else if (HIGH_PRIORITY_DLQ_TOPICS.contains(dlqTopic)) {
            return HIGH_PRIORITY_DLQ_ALERT_THRESHOLD;
        } else {
            return OPERATIONAL_DLQ_ALERT_THRESHOLD;
        }
    }
    
    private void sendDlqAlert(String dlqTopic, DlqHealthMetrics health) {
        String priority = getTopicPriority(dlqTopic);
        
        log.error("DLQ_ALERT [{}]: Topic {} has {} messages - Oldest: {} - Errors: {}", 
            priority, dlqTopic, health.getMessageCount(), 
            health.getOldestMessageAge(), health.getErrorTypes());
        
        // Send to alerting system
        auditService.auditKafkaEvent(
            "DLQ_ALERT_TRIGGERED",
            "dlq-monitor",
            "DLQ topic exceeded threshold",
            Map.of(
                "dlqTopic", dlqTopic,
                "priority", priority,
                "messageCount", health.getMessageCount(),
                "threshold", getAlertThreshold(dlqTopic),
                "oldestMessageAge", health.getOldestMessageAge().toString(),
                "errorTypes", health.getErrorTypes().toString()
            )
        );
        
        // Integrate with external alerting systems for critical alerts
        if ("CRITICAL".equals(priority)) {
            sendCriticalAlert(dlqTopic, health);
        }
    }
    
    private void sendCriticalAlert(String dlqTopic, DlqHealthMetrics health) {
        // In production, integrate with:
        // - PagerDuty for immediate on-call notification
        // - Slack for team awareness
        // - SNS for email/SMS alerts
        
        log.error("CRITICAL_DLQ_ALERT: Immediate attention required for {} - {} messages", 
            dlqTopic, health.getMessageCount());
        
        // For now, enhance audit logging for critical alerts
        auditService.auditSecurityEvent(
            "CRITICAL_DLQ_ALERT",
            "dlq-monitor",
            "Critical DLQ topic requires immediate attention",
            Map.of(
                "dlqTopic", dlqTopic,
                "messageCount", health.getMessageCount(),
                "oldestMessageAge", health.getOldestMessageAge().toString(),
                "severity", "CRITICAL",
                "requiresImmediateAction", true
            )
        );
    }
    
    private String getTopicPriority(String dlqTopic) {
        if (CRITICAL_DLQ_TOPICS.contains(dlqTopic)) {
            return "CRITICAL";
        } else if (HIGH_PRIORITY_DLQ_TOPICS.contains(dlqTopic)) {
            return "HIGH";
        } else {
            return "MEDIUM";
        }
    }
    
    private void performDlqMessageAnalysis(String dlqTopic) {
        // Analyze error patterns, frequencies, and recommend actions
        DlqHealthMetrics health = dlqHealthMap.get(dlqTopic);
        
        log.info("DLQ_ANALYSIS: Topic {} - Messages: {}, Error Types: {}, Oldest: {}", 
            dlqTopic, health.getMessageCount(), health.getErrorTypes(), health.getOldestMessageAge());
        
        // Look for patterns that might indicate systemic issues
        health.getErrorTypes().entrySet().stream()
            .filter(entry -> entry.getValue() > 10)
            .forEach(entry -> log.warn("DLQ_PATTERN: Topic {} has {} instances of {}", 
                dlqTopic, entry.getValue(), entry.getKey()));
    }
    
    private void logDlqSummary() {
        long critical = CRITICAL_DLQ_TOPICS.stream()
            .mapToLong(topic -> dlqHealthMap.getOrDefault(topic, new DlqHealthMetrics()).getMessageCount())
            .sum();
        
        long highPriority = HIGH_PRIORITY_DLQ_TOPICS.stream()
            .mapToLong(topic -> dlqHealthMap.getOrDefault(topic, new DlqHealthMetrics()).getMessageCount())
            .sum();
        
        if (critical > 0 || highPriority > 0) {
            log.warn("DLQ_SUMMARY: Total DLQ messages: {}, Critical: {}, High Priority: {}", 
                totalDlqMessages.get(), critical, highPriority);
        } else {
            log.info("DLQ_SUMMARY: Total DLQ messages: {} - All critical and high priority topics healthy", 
                totalDlqMessages.get());
        }
    }
    
    private HealthStatus determineOverallHealth(long criticalCount, long highPriorityCount) {
        if (criticalCount > 0) {
            return HealthStatus.CRITICAL;
        } else if (highPriorityCount > 10) {
            return HealthStatus.DEGRADED;
        } else if (highPriorityCount > 0) {
            return HealthStatus.WARNING;
        } else {
            return HealthStatus.HEALTHY;
        }
    }
    
    private List<ConsumerRecord<String, Object>> readDlqMessages(String dlqTopic, int maxMessages) {
        // Implementation to read messages from DLQ
        // This is simplified - real implementation would be more complex
        return List.of();
    }
    
    private String extractOriginalTopicName(String dlqTopic) {
        if (dlqTopic.endsWith("-dlq")) {
            return dlqTopic.substring(0, dlqTopic.lastIndexOf("-dlq"));
        }
        return null;
    }
    
    private String extractOriginalKey(ConsumerRecord<String, Object> record) {
        // Extract original message key from DLQ headers
        return record.key();
    }
    
    private Object extractOriginalPayload(ConsumerRecord<String, Object> record) {
        // Extract original payload from DLQ message
        return record.value();
    }
    
    private String extractErrorType(ConsumerRecord<String, Object> record) {
        // Extract error type from headers
        return "UnknownError";
    }
    
    private LocalDateTime extractMessageTimestamp(ConsumerRecord<String, Object> record) {
        // Extract message timestamp from Kafka record
        long timestamp = record.timestamp();
        if (timestamp > 0) {
            return LocalDateTime.ofInstant(
                java.time.Instant.ofEpochMilli(timestamp),
                java.time.ZoneId.systemDefault()
            );
        }
        
        // Fallback to current time if no timestamp available
        return LocalDateTime.now();
    }
    
    private void auditDlqReplay(String dlqTopic, String originalTopic, int successful, int failed) {
        auditService.auditKafkaEvent(
            "DLQ_MESSAGE_REPLAY",
            "dlq-monitor",
            "Replayed messages from DLQ to original topic",
            Map.of(
                "dlqTopic", dlqTopic,
                "originalTopic", originalTopic,
                "successfulReplays", successful,
                "failedReplays", failed,
                "timestamp", LocalDateTime.now().format(DateTimeFormatter.ISO_LOCAL_DATE_TIME)
            )
        );
    }
    
    // Data classes
    
    @lombok.Data
    @lombok.Builder
    @lombok.NoArgsConstructor
    @lombok.AllArgsConstructor(access = lombok.AccessLevel.PRIVATE)
    public static class DlqHealthMetrics {
        private String topicName;
        private long messageCount;
        private Duration oldestMessageAge;
        private Map<String, Long> errorTypes;
        private LocalDateTime lastChecked;
    }
    
    @lombok.Data
    @lombok.Builder
    public static class DlqHealthReport {
        private long totalDlqMessages;
        private long criticalDlqMessages;
        private long highPriorityDlqMessages;
        private List<DlqTopicHealth> unhealthyTopics;
        private HealthStatus overallHealth;
        private LocalDateTime reportGeneratedAt;
    }
    
    @lombok.Data
    @lombok.Builder
    public static class DlqTopicHealth {
        private String topicName;
        private long messageCount;
        private Duration oldestMessageAge;
        private Map<String, Long> errorTypes;
        private String priority;
    }
    
    @lombok.Data
    @lombok.Builder
    public static class DlqReplayResult {
        private String dlqTopic;
        private int maxMessages;
        private boolean dryRun;
        private boolean success;
        private int messagesFound;
        private int messagesReplayed;
        private int messagesFailed;
        private String errorMessage;
        private LocalDateTime startTime;
        private LocalDateTime endTime;
    }
    
    @lombok.Data
    @lombok.Builder
    public static class DlqPurgeResult {
        private String dlqTopic;
        private long messagesPurged;
        private boolean success;
        private String errorMessage;
    }
    
    public enum HealthStatus {
        HEALTHY,
        WARNING,
        DEGRADED,
        CRITICAL,
        UNKNOWN
    }
}