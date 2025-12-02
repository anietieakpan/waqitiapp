package com.waqiti.common.exception;

/**
 * Exception thrown when Kafka message processing fails
 * Used for handling errors in Kafka listeners and producers
 */
public class KafkaProcessingException extends WaqitiException {
    
    private final String topic;
    private final String partition;
    private final String offset;
    private final String consumerGroup;
    private final String messageKey;
    private final boolean retryable;
    
    public KafkaProcessingException(String message) {
        super(ErrorCode.MSG_DELIVERY_FAILED, message);
        this.topic = null;
        this.partition = null;
        this.offset = null;
        this.consumerGroup = null;
        this.messageKey = null;
        this.retryable = true;
    }
    
    public KafkaProcessingException(String message, Throwable cause) {
        super(ErrorCode.MSG_DELIVERY_FAILED, message, cause);
        this.topic = null;
        this.partition = null;
        this.offset = null;
        this.consumerGroup = null;
        this.messageKey = null;
        this.retryable = true;
    }
    
    public KafkaProcessingException(String message, String topic, String partition, String offset) {
        super(ErrorCode.MSG_DELIVERY_FAILED, message);
        this.topic = topic;
        this.partition = partition;
        this.offset = offset;
        this.consumerGroup = null;
        this.messageKey = null;
        this.retryable = true;
    }
    
    public KafkaProcessingException(String message, String topic, String partition, String offset, 
                                  String consumerGroup, String messageKey, boolean retryable) {
        super(ErrorCode.MSG_DELIVERY_FAILED, message);
        this.topic = topic;
        this.partition = partition;
        this.offset = offset;
        this.consumerGroup = consumerGroup;
        this.messageKey = messageKey;
        this.retryable = retryable;
    }
    
    public KafkaProcessingException(String message, Throwable cause, String topic, String partition, 
                                  String offset, String consumerGroup, String messageKey, boolean retryable) {
        super(ErrorCode.MSG_DELIVERY_FAILED, message, cause);
        this.topic = topic;
        this.partition = partition;
        this.offset = offset;
        this.consumerGroup = consumerGroup;
        this.messageKey = messageKey;
        this.retryable = retryable;
    }
    
    public String getTopic() {
        return topic;
    }
    
    public String getPartition() {
        return partition;
    }
    
    public String getOffset() {
        return offset;
    }
    
    public String getConsumerGroup() {
        return consumerGroup;
    }
    
    public String getMessageKey() {
        return messageKey;
    }
    
    public boolean isRetryable() {
        return retryable;
    }
    
    public String getKafkaContext() {
        StringBuilder context = new StringBuilder();
        if (topic != null) context.append("topic=").append(topic);
        if (partition != null) context.append(", partition=").append(partition);
        if (offset != null) context.append(", offset=").append(offset);
        if (consumerGroup != null) context.append(", group=").append(consumerGroup);
        if (messageKey != null) context.append(", key=").append(messageKey);
        return context.toString();
    }
    
    @Override
    public String getMessage() {
        String baseMessage = super.getMessage();
        String context = getKafkaContext();
        if (context.isEmpty()) {
            return baseMessage;
        }
        return baseMessage + " [" + context + "]";
    }
}