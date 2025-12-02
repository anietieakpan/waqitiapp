package com.waqiti.common.kafka;

/**
 * Exception thrown when Kafka Avro deserialization fails.
 * Provides comprehensive error context for debugging and monitoring.
 */
public class KafkaAvroDeserializationException extends RuntimeException {
    
    private static final long serialVersionUID = 1L;
    
    private final String topic;
    private final int partition;
    private final long offset;
    private final String schemaId;
    private final byte[] rawData;
    
    public KafkaAvroDeserializationException(String message) {
        super(message);
        this.topic = null;
        this.partition = -1;
        this.offset = -1L;
        this.schemaId = null;
        this.rawData = null;
    }
    
    public KafkaAvroDeserializationException(String message, Throwable cause) {
        super(message, cause);
        this.topic = null;
        this.partition = -1;
        this.offset = -1L;
        this.schemaId = null;
        this.rawData = null;
    }
    
    public KafkaAvroDeserializationException(String message, String topic, int partition, long offset) {
        super(message);
        this.topic = topic;
        this.partition = partition;
        this.offset = offset;
        this.schemaId = null;
        this.rawData = null;
    }
    
    public KafkaAvroDeserializationException(String message, Throwable cause, String topic, 
            int partition, long offset, String schemaId, byte[] rawData) {
        super(message, cause);
        this.topic = topic;
        this.partition = partition;
        this.offset = offset;
        this.schemaId = schemaId;
        this.rawData = rawData != null ? rawData.clone() : null;
    }
    
    /**
     * Get the Kafka topic where deserialization failed
     */
    public String getTopic() {
        return topic;
    }
    
    /**
     * Get the Kafka partition where deserialization failed
     */
    public int getPartition() {
        return partition;
    }
    
    /**
     * Get the Kafka offset where deserialization failed
     */
    public long getOffset() {
        return offset;
    }
    
    /**
     * Get the Avro schema ID that failed to deserialize
     */
    public String getSchemaId() {
        return schemaId;
    }
    
    /**
     * Get the raw data that failed to deserialize
     */
    public byte[] getRawData() {
        return rawData != null ? rawData.clone() : null;
    }
    
    /**
     * Check if this exception has Kafka context information
     */
    public boolean hasKafkaContext() {
        return topic != null && partition >= 0 && offset >= 0;
    }
    
    /**
     * Get detailed error information for logging and debugging
     */
    public String getDetailedErrorInfo() {
        StringBuilder info = new StringBuilder();
        info.append("Kafka Avro Deserialization Error: ").append(getMessage()).append("\n");
        
        if (hasKafkaContext()) {
            info.append("Topic: ").append(topic).append("\n");
            info.append("Partition: ").append(partition).append("\n");
            info.append("Offset: ").append(offset).append("\n");
        }
        
        if (schemaId != null) {
            info.append("Schema ID: ").append(schemaId).append("\n");
        }
        
        if (rawData != null) {
            info.append("Raw Data Length: ").append(rawData.length).append(" bytes\n");
            info.append("Raw Data Preview: ").append(getDataPreview()).append("\n");
        }
        
        if (getCause() != null) {
            info.append("Root Cause: ").append(getCause().getClass().getSimpleName()).append(": ");
            info.append(getCause().getMessage()).append("\n");
        }
        
        return info.toString();
    }
    
    /**
     * Get a preview of the raw data for debugging (first 50 bytes)
     */
    private String getDataPreview() {
        if (rawData == null || rawData.length == 0) {
            return "No data";
        }
        
        int previewLength = Math.min(50, rawData.length);
        StringBuilder preview = new StringBuilder();
        
        for (int i = 0; i < previewLength; i++) {
            preview.append(String.format("%02X ", rawData[i]));
        }
        
        if (rawData.length > previewLength) {
            preview.append("...");
        }
        
        return preview.toString().trim();
    }
    
    @Override
    public String toString() {
        StringBuilder sb = new StringBuilder();
        sb.append(getClass().getSimpleName()).append(": ").append(getMessage());
        
        if (hasKafkaContext()) {
            sb.append(" [Topic: ").append(topic)
              .append(", Partition: ").append(partition)
              .append(", Offset: ").append(offset).append("]");
        }
        
        if (schemaId != null) {
            sb.append(" [Schema ID: ").append(schemaId).append("]");
        }
        
        return sb.toString();
    }
}