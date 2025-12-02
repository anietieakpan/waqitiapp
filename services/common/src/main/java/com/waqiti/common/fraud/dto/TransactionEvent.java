package com.waqiti.common.fraud.dto;

import lombok.Builder;
import lombok.Data;
import com.fasterxml.jackson.annotation.JsonFormat;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import java.math.BigDecimal;
import java.time.LocalDateTime;

@Data
@Builder
public class TransactionEvent {
    @NotBlank(message = "Transaction ID is required")
    private String transactionId;

    @NotBlank(message = "User ID is required")
    private String userId;

    @NotNull(message = "Amount is required")
    private BigDecimal amount;
    private String currency;
    private String transactionType;
    private String merchantId;
    private String merchantName;
    private String merchantCategory;
    private String ipAddress;
    private String deviceId;
    private String userAgent;
    private Location location;

    @JsonFormat(pattern = "yyyy-MM-dd'T'HH:mm:ss")
    private LocalDateTime timestamp;
}