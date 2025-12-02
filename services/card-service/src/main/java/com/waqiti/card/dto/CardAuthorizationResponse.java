package com.waqiti.card.dto;

import com.waqiti.card.enums.AuthorizationStatus;
import com.waqiti.card.enums.DeclineReason;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.UUID;

/**
 * CardAuthorizationResponse DTO - Authorization response
 *
 * @author Waqiti Engineering Team
 * @version 2.0 (Consolidated)
 * @since 2025-11-09
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class CardAuthorizationResponse {
    private UUID id;
    private String authorizationId;
    private String authorizationCode;
    private AuthorizationStatus authorizationStatus;
    private BigDecimal authorizationAmount;
    private BigDecimal approvedAmount;
    private String currencyCode;
    private LocalDateTime authorizationDate;
    private LocalDateTime expiryDate;
    private BigDecimal riskScore;
    private String riskLevel;
    private Boolean fraudCheckPassed;
    private Boolean velocityCheckPassed;
    private Boolean limitCheckPassed;
    private DeclineReason declineReason;
    private String declineMessage;
    private String processorResponseCode;
    private String processorResponseMessage;
    private BigDecimal availableBalanceBefore;
    private BigDecimal availableBalanceAfter;
    private LocalDateTime createdAt;
}
