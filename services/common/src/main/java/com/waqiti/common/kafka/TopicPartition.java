package com.waqiti.common.kafka;

import java.util.Objects;

/**
 * Represents a Kafka topic partition identifier for the Waqiti platform.
 * This class encapsulates the topic name and partition number combination.
 */
public class TopicPartition implements Comparable<TopicPartition> {
    
    private final String topic;
    private final int partition;
    private final int hashCode;
    
    /**
     * Create a new TopicPartition
     * 
     * @param topic The topic name
     * @param partition The partition number
     */
    public TopicPartition(String topic, int partition) {
        this.topic = Objects.requireNonNull(topic, "Topic cannot be null");
        this.partition = partition;
        
        // Validation
        if (topic.trim().isEmpty()) {
            throw new IllegalArgumentException("Topic cannot be empty");
        }
        if (partition < 0) {
            throw new IllegalArgumentException("Partition cannot be negative: " + partition);
        }
        
        // Pre-calculate hash code for performance
        this.hashCode = Objects.hash(topic, partition);
    }
    
    /**
     * Get the topic name
     */
    public String getTopic() {
        return topic;
    }
    
    /**
     * Get the partition number
     */
    public int getPartition() {
        return partition;
    }
    
    /**
     * Create a string representation in the format "topic-partition"
     */
    public String getTopicPartitionString() {
        return topic + "-" + partition;
    }
    
    /**
     * Check if this TopicPartition belongs to the specified topic
     */
    public boolean belongsToTopic(String topicName) {
        return topic.equals(topicName);
    }
    
    /**
     * Compare TopicPartition instances for sorting
     * First by topic name alphabetically, then by partition number
     */
    @Override
    public int compareTo(TopicPartition other) {
        if (other == null) {
            return 1;
        }
        
        int topicComparison = this.topic.compareTo(other.topic);
        if (topicComparison != 0) {
            return topicComparison;
        }
        
        return Integer.compare(this.partition, other.partition);
    }
    
    @Override
    public boolean equals(Object obj) {
        if (this == obj) {
            return true;
        }
        if (obj == null || getClass() != obj.getClass()) {
            return false;
        }
        
        TopicPartition that = (TopicPartition) obj;
        return partition == that.partition && Objects.equals(topic, that.topic);
    }
    
    @Override
    public int hashCode() {
        return hashCode; // Use pre-calculated hash code
    }
    
    @Override
    public String toString() {
        return topic + "-" + partition;
    }
    
    /**
     * Parse a TopicPartition from string format "topic-partition"
     * 
     * @param topicPartitionString The string in format "topic-partition"
     * @return TopicPartition instance
     * @throws IllegalArgumentException if the string format is invalid
     */
    public static TopicPartition fromString(String topicPartitionString) {
        Objects.requireNonNull(topicPartitionString, "TopicPartition string cannot be null");
        
        int lastDashIndex = topicPartitionString.lastIndexOf('-');
        if (lastDashIndex <= 0 || lastDashIndex >= topicPartitionString.length() - 1) {
            throw new IllegalArgumentException("Invalid TopicPartition string format: " + topicPartitionString);
        }
        
        String topic = topicPartitionString.substring(0, lastDashIndex);
        String partitionStr = topicPartitionString.substring(lastDashIndex + 1);
        
        try {
            int partition = Integer.parseInt(partitionStr);
            return new TopicPartition(topic, partition);
        } catch (NumberFormatException e) {
            throw new IllegalArgumentException("Invalid partition number in TopicPartition string: " + topicPartitionString, e);
        }
    }
    
    /**
     * Create a detailed string representation for debugging
     */
    public String toDetailedString() {
        return String.format("TopicPartition{topic='%s', partition=%d}", topic, partition);
    }
}