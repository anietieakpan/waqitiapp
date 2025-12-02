package com.waqiti.billpayment.events;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.io.Serializable;
import java.time.Instant;

/**
 * Dead Letter Bill Payment Event - Wraps failed events for DLQ processing
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class DeadLetterBillPaymentEvent implements Serializable {

    private static final long serialVersionUID = 1L;

    @JsonProperty("original_event")
    private Object originalEvent;

    @JsonProperty("original_topic")
    private String originalTopic;

    @JsonProperty("error_message")
    private String errorMessage;

    @JsonProperty("error_stack_trace")
    private String errorStackTrace;

    @JsonProperty("failure_timestamp")
    private Instant failureTimestamp;

    @JsonProperty("retry_count")
    private Integer retryCount;

    @JsonProperty("max_retries")
    private Integer maxRetries;

    @JsonProperty("can_retry")
    private Boolean canRetry;

    @JsonProperty("dlq_reason")
    private String dlqReason;
}
