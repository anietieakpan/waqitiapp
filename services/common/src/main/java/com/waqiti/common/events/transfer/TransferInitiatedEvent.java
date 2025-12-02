package com.waqiti.common.events.transfer;

import com.fasterxml.jackson.annotation.JsonFormat;
import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import java.io.Serializable;
import java.math.BigDecimal;
import java.time.LocalDateTime;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class TransferInitiatedEvent implements Serializable {

    private static final long serialVersionUID = 1L;

    @NotBlank(message = "Event ID is required")
    @JsonProperty("event_id")
    private String eventId;

    @NotBlank(message = "Correlation ID is required")
    @JsonProperty("correlation_id")
    private String correlationId;

    @NotNull(message = "Timestamp is required")
    @JsonProperty("timestamp")
    @JsonFormat(pattern = "yyyy-MM-dd'T'HH:mm:ss.SSS")
    private LocalDateTime timestamp;

    @JsonProperty("event_version")
    private String eventVersion = "1.0";

    @NotBlank(message = "Event source is required")
    @JsonProperty("source")
    private String source;

    @NotBlank(message = "Transfer ID is required")
    @JsonProperty("transfer_id")
    private String transferId;

    @NotBlank(message = "User ID is required")
    @JsonProperty("user_id")
    private String userId;

    @NotBlank(message = "Transfer type is required")
    @JsonProperty("transfer_type")
    private String transferType;

    @NotNull(message = "Amount is required")
    @JsonProperty("amount")
    private BigDecimal amount;

    @NotBlank(message = "Currency is required")
    @JsonProperty("currency")
    private String currency;

    @JsonProperty("source_account")
    private String sourceAccount;

    @JsonProperty("destination_account")
    private String destinationAccount;

    @NotNull(message = "Initiated at timestamp is required")
    @JsonProperty("initiated_at")
    @JsonFormat(pattern = "yyyy-MM-dd'T'HH:mm:ss.SSS")
    private LocalDateTime initiatedAt;

    @JsonProperty("estimated_completion")
    @JsonFormat(pattern = "yyyy-MM-dd'T'HH:mm:ss.SSS")
    private LocalDateTime estimatedCompletion;

    @JsonProperty("transfer_method")
    private String transferMethod;

    @JsonProperty("transfer_speed")
    private String transferSpeed;

    @JsonProperty("transfer_fee")
    private BigDecimal transferFee;
}