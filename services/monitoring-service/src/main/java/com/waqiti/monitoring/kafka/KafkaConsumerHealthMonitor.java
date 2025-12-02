package com.waqiti.monitoring.kafka;

import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Tags;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.kafka.clients.admin.*;
import org.apache.kafka.clients.consumer.ConsumerConfig;
import org.apache.kafka.common.TopicPartition;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.kafka.core.KafkaAdmin;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import javax.annotation.PostConstruct;
import java.util.*;
import java.util.concurrent.ExecutionException;
import java.util.stream.Collectors;

/**
 * Monitors Kafka consumer health and lag
 * CRITICAL: Detects orphaned events and consumer failures
 * 
 * This component was added to prevent the data loss issues
 * discovered during the production readiness audit
 * 
 * @author Waqiti Engineering
 * @since 1.0.0
 */
@Component
@Slf4j
@RequiredArgsConstructor
public class KafkaConsumerHealthMonitor {
    
    private final KafkaAdmin kafkaAdmin;
    private final MeterRegistry meterRegistry;
    private final AlertService alertService;
    
    @Value("${kafka.bootstrap-servers}")
    private String bootstrapServers;
    
    @Value("${monitoring.kafka.lag-threshold:1000}")
    private long lagThreshold;
    
    @Value("${monitoring.kafka.orphan-check-enabled:true}")
    private boolean orphanCheckEnabled;
    
    private AdminClient adminClient;
    
    // Critical topics that must have consumers
    private static final Map<String, Set<String>> REQUIRED_CONSUMERS = Map.of(
        "payment-initiated-events", Set.of("ledger-service-group"),
        "wallet-debited-events", Set.of("reconciliation-service-group"),
        "fraud-detection-completed-events", Set.of("payment-service-group"),
        "transaction-reversed-events", Set.of("wallet-service-group"),
        "merchant-settlement-events", Set.of("accounting-service-group"),
        "reconciliation-failed-events", Set.of("monitoring-service-group"),
        "crypto-transaction-mined-events", Set.of("wallet-service-group"),
        "kyc-verification-completed-events", Set.of("user-service-group"),
        "compliance-alert-events", Set.of("audit-service-group"),
        "ledger-entry-created-events", Set.of("reporting-service-group"),
        "international-transfer-initiated-events", Set.of("compliance-service-group")
    );
    
    @PostConstruct
    public void init() {
        Properties props = new Properties();
        props.put(ConsumerConfig.BOOTSTRAP_SERVERS_CONFIG, bootstrapServers);
        this.adminClient = AdminClient.create(props);
        
        log.info("Kafka consumer health monitor initialized. Monitoring {} critical topics", 
            REQUIRED_CONSUMERS.size());
    }
    
    /**
     * Check consumer lag every 30 seconds
     */
    @Scheduled(fixedDelay = 30000, initialDelay = 60000)
    public void checkConsumerLag() {
        log.debug("Starting consumer lag check");
        
        try {
            // Get all consumer groups
            ListConsumerGroupsResult groupsResult = adminClient.listConsumerGroups();
            Collection<ConsumerGroupListing> groups = groupsResult.all().get();
            
            for (ConsumerGroupListing group : groups) {
                checkGroupLag(group.groupId());
            }
            
        } catch (Exception e) {
            log.error("Failed to check consumer lag", e);
            meterRegistry.counter("kafka.monitoring.errors", "type", "lag_check").increment();
        }
    }
    
    /**
     * Check for orphaned events (topics without consumers) every minute
     */
    @Scheduled(fixedDelay = 60000, initialDelay = 120000)
    public void checkOrphanedEvents() {
        if (!orphanCheckEnabled) {
            return;
        }
        
        log.info("Checking for orphaned events");
        
        try {
            // Get all consumer groups
            Set<String> activeGroups = adminClient.listConsumerGroups()
                .all().get().stream()
                .map(ConsumerGroupListing::groupId)
                .collect(Collectors.toSet());
            
            // Check each required topic-consumer mapping
            List<OrphanedTopic> orphanedTopics = new ArrayList<>();
            
            for (Map.Entry<String, Set<String>> entry : REQUIRED_CONSUMERS.entrySet()) {
                String topic = entry.getKey();
                Set<String> requiredGroups = entry.getValue();
                
                for (String requiredGroup : requiredGroups) {
                    if (!activeGroups.contains(requiredGroup)) {
                        orphanedTopics.add(new OrphanedTopic(topic, requiredGroup));
                        
                        // Record metric
                        meterRegistry.gauge("kafka.orphaned.topics", 
                            Tags.of("topic", topic, "group", requiredGroup), 1);
                        
                        log.error("ORPHANED TOPIC DETECTED: topic={} has no consumer group={}",
                            topic, requiredGroup);
                    } else {
                        // Check if consumer is actually subscribed to the topic
                        if (!isGroupSubscribedToTopic(requiredGroup, topic)) {
                            orphanedTopics.add(new OrphanedTopic(topic, requiredGroup));
                            
                            log.error("CONSUMER NOT SUBSCRIBED: group={} not subscribed to topic={}",
                                requiredGroup, topic);
                        }
                    }
                }
            }
            
            // Alert if orphaned topics found
            if (!orphanedTopics.isEmpty()) {
                String message = String.format(
                    "CRITICAL: %d orphaned Kafka topics detected! Events being lost: %s",
                    orphanedTopics.size(),
                    orphanedTopics.stream()
                        .map(o -> o.topic + " (missing: " + o.missingGroup + ")")
                        .collect(Collectors.joining(", "))
                );
                
                alertService.sendCriticalAlert("ORPHANED_KAFKA_TOPICS", message);
                
                // Page on-call for critical data loss scenario
                if (orphanedTopics.size() > 5) {
                    alertService.pageOnCall("Multiple orphaned Kafka topics causing data loss!");
                }
            } else {
                log.info("All required Kafka consumers are active");
                meterRegistry.gauge("kafka.orphaned.topics.total", 0);
            }
            
        } catch (Exception e) {
            log.error("Failed to check for orphaned events", e);
            meterRegistry.counter("kafka.monitoring.errors", "type", "orphan_check").increment();
        }
    }
    
    /**
     * Check lag for a specific consumer group
     */
    private void checkGroupLag(String groupId) {
        try {
            // Get consumer group offsets
            ListConsumerGroupOffsetsResult offsetsResult = 
                adminClient.listConsumerGroupOffsets(groupId);
            Map<TopicPartition, OffsetAndMetadata> offsets = offsetsResult.partitionsToOffsetAndMetadata().get();
            
            if (offsets.isEmpty()) {
                return;
            }
            
            // Get end offsets for topics
            Set<TopicPartition> topicPartitions = offsets.keySet();
            Map<TopicPartition, Long> endOffsets = getEndOffsets(topicPartitions);
            
            // Calculate lag
            long totalLag = 0;
            Map<String, Long> topicLags = new HashMap<>();
            
            for (Map.Entry<TopicPartition, OffsetAndMetadata> entry : offsets.entrySet()) {
                TopicPartition tp = entry.getKey();
                long currentOffset = entry.getValue().offset();
                long endOffset = endOffsets.getOrDefault(tp, currentOffset);
                long lag = endOffset - currentOffset;
                
                totalLag += lag;
                topicLags.merge(tp.topic(), lag, Long::sum);
                
                // Record partition-level metrics
                meterRegistry.gauge("kafka.consumer.lag",
                    Tags.of(
                        "group", groupId,
                        "topic", tp.topic(),
                        "partition", String.valueOf(tp.partition())
                    ),
                    lag
                );
            }
            
            // Record group-level metrics
            meterRegistry.gauge("kafka.consumer.lag.total",
                Tags.of("group", groupId),
                totalLag
            );
            
            // Alert on high lag
            if (totalLag > lagThreshold) {
                log.warn("High lag detected for consumer group {}: total lag = {}", groupId, totalLag);
                
                // Find topics with highest lag
                String highLagTopics = topicLags.entrySet().stream()
                    .filter(e -> e.getValue() > 100)
                    .map(e -> e.getKey() + ":" + e.getValue())
                    .collect(Collectors.joining(", "));
                
                if (totalLag > lagThreshold * 10) {
                    // Critical lag - possible consumer failure
                    alertService.sendCriticalAlert(
                        "CRITICAL_CONSUMER_LAG",
                        String.format("Consumer group %s has critical lag: %d messages. Topics: %s",
                            groupId, totalLag, highLagTopics)
                    );
                } else {
                    // Warning level
                    alertService.sendAlert(
                        "HIGH_CONSUMER_LAG",
                        String.format("Consumer group %s has high lag: %d messages. Topics: %s",
                            groupId, totalLag, highLagTopics),
                        AlertService.Priority.MEDIUM
                    );
                }
            }
            
        } catch (Exception e) {
            log.error("Failed to check lag for group: {}", groupId, e);
        }
    }
    
    /**
     * Check if a consumer group is subscribed to a specific topic
     */
    private boolean isGroupSubscribedToTopic(String groupId, String topic) {
        try {
            DescribeConsumerGroupsResult result = 
                adminClient.describeConsumerGroups(Collections.singleton(groupId));
            ConsumerGroupDescription description = result.all().get().get(groupId);
            
            return description.members().stream()
                .flatMap(member -> member.assignment().topicPartitions().stream())
                .anyMatch(tp -> tp.topic().equals(topic));
                
        } catch (Exception e) {
            log.error("Failed to check group subscription: group={}, topic={}", groupId, topic, e);
            return false;
        }
    }
    
    /**
     * Get end offsets for topic partitions
     */
    private Map<TopicPartition, Long> getEndOffsets(Set<TopicPartition> partitions) {
        try {
            Map<TopicPartition, OffsetSpec> offsetSpecMap = partitions.stream()
                .collect(Collectors.toMap(tp -> tp, tp -> OffsetSpec.latest()));
            
            ListOffsetsResult result = adminClient.listOffsets(offsetSpecMap);
            Map<TopicPartition, Long> endOffsets = new HashMap<>();
            
            for (Map.Entry<TopicPartition, ListOffsetsResult.ListOffsetsResultInfo> entry : 
                    result.all().get().entrySet()) {
                endOffsets.put(entry.getKey(), entry.getValue().offset());
            }
            
            return endOffsets;
        } catch (Exception e) {
            log.error("Failed to get end offsets", e);
            return Collections.emptyMap();
        }
    }
    
    /**
     * Health check endpoint data
     */
    public KafkaHealthStatus getHealthStatus() {
        try {
            // Get cluster info
            DescribeClusterResult clusterResult = adminClient.describeCluster();
            Collection<Node> nodes = clusterResult.nodes().get();
            String clusterId = clusterResult.clusterId().get();
            
            // Get consumer groups
            Collection<ConsumerGroupListing> groups = 
                adminClient.listConsumerGroups().all().get();
            
            // Check for orphaned topics
            List<String> orphaned = checkOrphanedTopicsQuick();
            
            return KafkaHealthStatus.builder()
                .healthy(!orphaned.isEmpty())
                .clusterSize(nodes.size())
                .clusterId(clusterId)
                .activeConsumerGroups(groups.size())
                .orphanedTopics(orphaned)
                .build();
                
        } catch (Exception e) {
            log.error("Failed to get Kafka health status", e);
            return KafkaHealthStatus.builder()
                .healthy(false)
                .error(e.getMessage())
                .build();
        }
    }
    
    private List<String> checkOrphanedTopicsQuick() {
        List<String> orphaned = new ArrayList<>();
        try {
            Set<String> activeGroups = adminClient.listConsumerGroups()
                .all().get().stream()
                .map(ConsumerGroupListing::groupId)
                .collect(Collectors.toSet());
            
            for (Map.Entry<String, Set<String>> entry : REQUIRED_CONSUMERS.entrySet()) {
                for (String group : entry.getValue()) {
                    if (!activeGroups.contains(group)) {
                        orphaned.add(entry.getKey());
                        break;
                    }
                }
            }
        } catch (Exception e) {
            log.error("Quick orphan check failed", e);
        }
        return orphaned;
    }
    
    @lombok.Data
    @lombok.AllArgsConstructor
    private static class OrphanedTopic {
        private String topic;
        private String missingGroup;
    }
    
    @lombok.Data
    @lombok.Builder
    public static class KafkaHealthStatus {
        private boolean healthy;
        private int clusterSize;
        private String clusterId;
        private int activeConsumerGroups;
        private List<String> orphanedTopics;
        private String error;
    }
}