package com.waqiti.payment.dto;

import com.fasterxml.jackson.annotation.JsonInclude;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID; /**
 * Response for split payment operations
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@JsonInclude(JsonInclude.Include.NON_NULL)
public class SplitPaymentResponse {
    private UUID id;
    private UUID organizerId;
    private String title;
    private String description;
    private BigDecimal totalAmount;
    private String currency;
    private String status;
    private LocalDateTime expiryDate;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
    
    // Additional calculated fields
    private BigDecimal paidAmount;
    private BigDecimal remainingAmount;
    private BigDecimal completionPercentage;
    
    // Added user information (populated from User service)
    private String organizerName;
    
    // List of participants
    private List<SplitPaymentParticipantResponse> participants;
}
