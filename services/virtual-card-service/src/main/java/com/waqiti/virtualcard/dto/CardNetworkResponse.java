package com.waqiti.virtualcard.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.Map;

/**
 * Response DTO for card network transactions
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class CardNetworkResponse {
    
    private String transactionId;
    
    private String authorizationCode;
    
    private String responseCode;
    
    private String responseMessage;
    
    private boolean approved;
    
    private String declineReason;
    
    private BigDecimal amount;
    
    private String currency;
    
    private BigDecimal authorizedAmount;
    
    private String balanceAmount;
    
    private String networkReferenceId;
    
    private String retrievalReferenceNumber;
    
    private String systemTraceAuditNumber;
    
    private String batchNumber;
    
    private String settlementDate;
    
    private String networkId;
    
    private String processorId;
    
    private String issuerResponseCode;
    
    private String issuerResponseMessage;
    
    private String terminalId;
    
    private String merchantId;
    
    private String acquirerId;
    
    private LocalDateTime transactionTimestamp;
    
    private LocalDateTime responseTimestamp;
    
    private String cryptogram;
    
    private String cavv;
    
    private String eci;
    
    private String acsTransactionId;
    
    private String dsTransactionId;
    
    private String threeDSVersion;
    
    private Integer riskScore;
    
    private String riskAssessment;
    
    private BigDecimal interchangeFee;
    
    private BigDecimal networkFee;
    
    private BigDecimal processingFee;
    
    private String feeStructure;
    
    private Map<String, String> additionalFields;
    
    private String errorCode;
    
    private String errorMessage;
    
    private boolean partialApproval;
    
    private BigDecimal partialAmount;
    
    private String standinReason;
    
    private boolean fallbackToMagstripe;
    
    private String velocityResponse;
    
    private String fraudResponse;
    
    private String avsResponse;
    
    private String cvvResponse;
    
    /**
     * Creates an approved transaction response
     */
    public static CardNetworkResponse approved(String transactionId, String authCode, BigDecimal amount) {
        return CardNetworkResponse.builder()
            .transactionId(transactionId)
            .authorizationCode(authCode)
            .responseCode("00")
            .responseMessage("Approved")
            .approved(true)
            .amount(amount)
            .authorizedAmount(amount)
            .transactionTimestamp(LocalDateTime.now())
            .responseTimestamp(LocalDateTime.now())
            .build();
    }
    
    /**
     * Creates a declined transaction response
     */
    public static CardNetworkResponse declined(String responseCode, String message) {
        return CardNetworkResponse.builder()
            .responseCode(responseCode)
            .responseMessage(message)
            .approved(false)
            .declineReason(message)
            .transactionTimestamp(LocalDateTime.now())
            .responseTimestamp(LocalDateTime.now())
            .build();
    }
    
    /**
     * Creates a partial approval response
     */
    public static CardNetworkResponse partialApproval(String transactionId, String authCode, 
                                                     BigDecimal requestedAmount, BigDecimal approvedAmount) {
        return CardNetworkResponse.builder()
            .transactionId(transactionId)
            .authorizationCode(authCode)
            .responseCode("10")
            .responseMessage("Partial Approval")
            .approved(true)
            .partialApproval(true)
            .amount(requestedAmount)
            .authorizedAmount(approvedAmount)
            .partialAmount(approvedAmount)
            .transactionTimestamp(LocalDateTime.now())
            .responseTimestamp(LocalDateTime.now())
            .build();
    }
    
    /**
     * Creates an error response
     */
    public static CardNetworkResponse error(String errorCode, String errorMessage) {
        return CardNetworkResponse.builder()
            .approved(false)
            .errorCode(errorCode)
            .errorMessage(errorMessage)
            .responseTimestamp(LocalDateTime.now())
            .build();
    }
    
    /**
     * Get the main response message
     */
    public String getMessage() {
        if (responseMessage != null) {
            return responseMessage;
        }
        if (errorMessage != null) {
            return errorMessage;
        }
        if (declineReason != null) {
            return declineReason;
        }
        return "Unknown response";
    }
}