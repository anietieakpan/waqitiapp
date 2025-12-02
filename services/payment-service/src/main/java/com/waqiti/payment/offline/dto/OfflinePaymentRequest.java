package com.waqiti.payment.offline.dto;

import jakarta.validation.constraints.*;
import lombok.Builder;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@Data
@Builder
public class OfflinePaymentRequest {
    
    @NotBlank(message = "Recipient ID is required")
    private String recipientId;
    
    @NotNull(message = "Amount is required")
    @DecimalMin(value = "0.01", message = "Amount must be at least 0.01")
    @DecimalMax(value = "500.00", message = "Offline payment amount cannot exceed 500.00")
    private BigDecimal amount;
    
    @NotBlank(message = "Currency is required")
    @Size(min = 3, max = 3, message = "Currency must be 3 characters")
    private String currency;
    
    @Size(max = 500, message = "Description cannot exceed 500 characters")
    private String description;
    
    @NotBlank(message = "Device ID is required")
    private String deviceId;
    
    @NotNull(message = "Client timestamp is required")
    private LocalDateTime clientTimestamp;
    
    // Optional proximity transfer data
    private String bluetoothToken;
    private String nfcData;
    
    // Optional location data for fraud prevention
    private Double latitude;
    private Double longitude;
    
    // Security pin for offline verification
    private String offlinePin;
}