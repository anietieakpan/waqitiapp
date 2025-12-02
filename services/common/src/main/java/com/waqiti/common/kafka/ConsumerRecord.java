package com.waqiti.common.kafka;

import java.time.LocalDateTime;
import java.time.Instant;
import java.util.Objects;
import java.util.Optional;

/**
 * Represents a single Kafka consumer record with comprehensive metadata
 * for the Waqiti platform messaging system.
 */
public class ConsumerRecord<K, V> {
    
    private final String topic;
    private final int partition;
    private final long offset;
    private final long timestamp;
    private final TimestampType timestampType;
    private final K key;
    private final V value;
    private final Headers headers;
    private final Optional<Integer> leaderEpoch;
    
    public ConsumerRecord(String topic, int partition, long offset, K key, V value) {
        this(topic, partition, offset, System.currentTimeMillis(), 
             TimestampType.CREATE_TIME, key, value, new Headers(), Optional.empty());
    }
    
    public ConsumerRecord(String topic, int partition, long offset, long timestamp, 
                         TimestampType timestampType, K key, V value) {
        this(topic, partition, offset, timestamp, timestampType, key, value, 
             new Headers(), Optional.empty());
    }
    
    public ConsumerRecord(String topic, int partition, long offset, long timestamp,
                         TimestampType timestampType, K key, V value, Headers headers,
                         Optional<Integer> leaderEpoch) {
        this.topic = Objects.requireNonNull(topic, "Topic cannot be null");
        this.partition = partition;
        this.offset = offset;
        this.timestamp = timestamp;
        this.timestampType = timestampType != null ? timestampType : TimestampType.NO_TIMESTAMP_TYPE;
        this.key = key;
        this.value = value;
        this.headers = headers != null ? headers : new Headers();
        this.leaderEpoch = leaderEpoch != null ? leaderEpoch : Optional.empty();
        
        // Validation
        if (partition < 0) {
            throw new IllegalArgumentException("Partition cannot be negative");
        }
        if (offset < 0) {
            throw new IllegalArgumentException("Offset cannot be negative");
        }
    }
    
    /**
     * Get the topic this record was received from
     */
    public String getTopic() {
        return topic;
    }
    
    /**
     * Get the partition from which this record was received
     */
    public int getPartition() {
        return partition;
    }
    
    /**
     * Get the offset of this record in the corresponding Kafka partition
     */
    public long getOffset() {
        return offset;
    }
    
    /**
     * Get the timestamp of this record
     */
    public long getTimestamp() {
        return timestamp;
    }
    
    /**
     * Get the timestamp type of this record
     */
    public TimestampType getTimestampType() {
        return timestampType;
    }
    
    /**
     * Get the key (or null if no key is specified)
     */
    public K getKey() {
        return key;
    }
    
    /**
     * Get the value
     */
    public V getValue() {
        return value;
    }
    
    /**
     * Get the headers
     */
    public Headers getHeaders() {
        return headers;
    }
    
    /**
     * Get the leader epoch of the record if available
     */
    public Optional<Integer> getLeaderEpoch() {
        return leaderEpoch;
    }
    
    /**
     * Get the timestamp as LocalDateTime
     */
    public LocalDateTime getTimestampAsLocalDateTime() {
        return LocalDateTime.ofInstant(Instant.ofEpochMilli(timestamp), 
                                     java.time.ZoneId.systemDefault());
    }
    
    /**
     * Check if this record has a key
     */
    public boolean hasKey() {
        return key != null;
    }
    
    /**
     * Check if this record has headers
     */
    public boolean hasHeaders() {
        return headers != null && !headers.isEmpty();
    }
    
    /**
     * Get the size of the key in bytes (approximate)
     */
    public int getKeySize() {
        if (key == null) {
            return 0;
        }
        
        if (key instanceof String) {
            return ((String) key).getBytes().length;
        } else if (key instanceof byte[]) {
            return ((byte[]) key).length;
        } else {
            // Approximate size for other types
            return key.toString().getBytes().length;
        }
    }
    
    /**
     * Get the size of the value in bytes (approximate)
     */
    public int getValueSize() {
        if (value == null) {
            return 0;
        }
        
        if (value instanceof String) {
            return ((String) value).getBytes().length;
        } else if (value instanceof byte[]) {
            return ((byte[]) value).length;
        } else {
            // Approximate size for other types
            return value.toString().getBytes().length;
        }
    }
    
    /**
     * Get the total approximate size of this record
     */
    public int getRecordSize() {
        return getKeySize() + getValueSize() + 
               topic.getBytes().length + 
               (hasHeaders() ? headers.estimateSize() : 0) + 
               64; // Approximate overhead for metadata
    }
    
    /**
     * Create a TopicPartition for this record
     */
    public TopicPartition getTopicPartition() {
        return new TopicPartition(topic, partition);
    }
    
    /**
     * Get detailed information about this record for debugging
     */
    public String getDetailedInfo() {
        StringBuilder info = new StringBuilder();
        info.append("ConsumerRecord Details:\n");
        info.append("  Topic: ").append(topic).append("\n");
        info.append("  Partition: ").append(partition).append("\n");
        info.append("  Offset: ").append(offset).append("\n");
        info.append("  Timestamp: ").append(timestamp).append(" (").append(getTimestampAsLocalDateTime()).append(")\n");
        info.append("  Timestamp Type: ").append(timestampType).append("\n");
        info.append("  Has Key: ").append(hasKey()).append("\n");
        if (hasKey()) {
            info.append("  Key Size: ").append(getKeySize()).append(" bytes\n");
        }
        info.append("  Value Size: ").append(getValueSize()).append(" bytes\n");
        info.append("  Headers Count: ").append(hasHeaders() ? headers.size() : 0).append("\n");
        info.append("  Leader Epoch: ").append(leaderEpoch.map(String::valueOf).orElse("N/A")).append("\n");
        info.append("  Record Size: ~").append(getRecordSize()).append(" bytes");
        
        return info.toString();
    }
    
    @Override
    public String toString() {
        return String.format("ConsumerRecord(topic=%s, partition=%d, offset=%d, timestamp=%d, key=%s, value=%s)",
                topic, partition, offset, timestamp, key, value);
    }
    
    @Override
    public boolean equals(Object obj) {
        if (this == obj) return true;
        if (obj == null || getClass() != obj.getClass()) return false;
        
        ConsumerRecord<?, ?> that = (ConsumerRecord<?, ?>) obj;
        return partition == that.partition &&
               offset == that.offset &&
               timestamp == that.timestamp &&
               Objects.equals(topic, that.topic) &&
               timestampType == that.timestampType &&
               Objects.equals(key, that.key) &&
               Objects.equals(value, that.value) &&
               Objects.equals(headers, that.headers) &&
               Objects.equals(leaderEpoch, that.leaderEpoch);
    }
    
    @Override
    public int hashCode() {
        return Objects.hash(topic, partition, offset, timestamp, timestampType, 
                          key, value, headers, leaderEpoch);
    }
    
    /**
     * Enum representing the timestamp type of a Kafka record
     */
    public enum TimestampType {
        NO_TIMESTAMP_TYPE(-1, "NoTimestampType"),
        CREATE_TIME(0, "CreateTime"),
        LOG_APPEND_TIME(1, "LogAppendTime");
        
        private final int id;
        private final String name;
        
        TimestampType(int id, String name) {
            this.id = id;
            this.name = name;
        }
        
        public int getId() {
            return id;
        }
        
        public String getName() {
            return name;
        }
        
        @Override
        public String toString() {
            return name;
        }
    }
}