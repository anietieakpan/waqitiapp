package com.waqiti.wallet.integration.card;

import com.waqiti.wallet.dto.*;
import com.waqiti.wallet.domain.PaymentMethod;
import com.waqiti.common.resilience.PaymentResilience;
import com.waqiti.common.exception.ServiceException;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;

import java.math.BigDecimal;
import java.util.Map;
import java.util.UUID;

/**
 * Card processing service for handling credit/debit card operations in wallet
 * 
 * @author Principal Software Engineer
 * @since 1.0.0
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class CardProcessingService {
    
    private final RestTemplate restTemplate;
    
    @Value("${card.processor.base-url:https://api.stripe.com}")
    private String cardProcessorBaseUrl;
    
    @Value("${card.processor.api-key:}")
    private String apiKey;
    
    @Value("${card.processor.timeout:30000}")
    private int timeoutMs;
    
    @Value("${card.processing.fee.percentage:0.029}")
    private BigDecimal processingFeePercentage;
    
    @Value("${card.processing.fee.fixed:0.30}")
    private BigDecimal processingFeeFixed;
    
    /**
     * Process deposit from credit/debit card
     */
    @PaymentResilience
    public DepositProcessingResult processDeposit(DepositRequest request) {
        log.info("Processing card deposit: wallet={}, amount={}, method={}", 
            request.getWalletId(), request.getAmount(), request.getPaymentMethod());
        
        try {
            // Validate payment method
            if (!isValidCardPaymentMethod(request.getPaymentMethod())) {
                return DepositProcessingResult.failed("INVALID_PAYMENT_METHOD", 
                    "Invalid payment method for card deposit");
            }
            
            // Calculate fees
            BigDecimal totalFees = calculateProcessingFees(request.getAmount());
            BigDecimal netAmount = request.getAmount().subtract(totalFees);
            
            log.info("Card deposit fees: total={}, net={}", totalFees, netAmount);
            
            // Create payment intent with card processor
            CardPaymentRequest paymentRequest = CardPaymentRequest.builder()
                .amount(request.getAmount().multiply(new BigDecimal("100")).intValue()) // Convert to cents
                .currency(request.getCurrency().toString().toLowerCase())
                .paymentMethodId(request.getPaymentMethodId())
                .description(request.getDescription())
                .reference(request.getReference())
                .metadata(request.getMetadata())
                .build();
            
            CardPaymentResponse paymentResponse = processCardPayment(paymentRequest);
            
            if (paymentResponse.isSuccessful()) {
                log.info("Card deposit processed successfully: {}", paymentResponse.getTransactionId());
                return DepositProcessingResult.successful(paymentResponse.getTransactionId(), netAmount);
            } else {
                log.warn("Card deposit failed: {} - {}", paymentResponse.getErrorCode(), paymentResponse.getErrorMessage());
                return DepositProcessingResult.failed(paymentResponse.getErrorCode(), paymentResponse.getErrorMessage());
            }
            
        } catch (Exception e) {
            log.error("Error processing card deposit", e);
            return DepositProcessingResult.failed("PROCESSING_ERROR", e.getMessage());
        }
    }
    
    /**
     * Process withdrawal to credit/debit card
     */
    @PaymentResilience
    public WithdrawProcessingResult processWithdrawal(WithdrawRequest request) {
        log.info("Processing card withdrawal: wallet={}, amount={}, method={}", 
            request.getWalletId(), request.getAmount(), request.getPaymentMethod());
        
        try {
            // Validate payment method
            if (!isValidCardPaymentMethod(request.getPaymentMethod())) {
                return WithdrawProcessingResult.failed("INVALID_PAYMENT_METHOD", 
                    "Invalid payment method for card withdrawal");
            }
            
            // Note: Card withdrawals are typically refunds to the original payment method
            if (request.getPaymentMethod() == PaymentMethod.CREDIT_CARD) {
                return processCardRefund(request);
            } else {
                return processDebitCardWithdrawal(request);
            }
            
        } catch (Exception e) {
            log.error("Error processing card withdrawal", e);
            return WithdrawProcessingResult.failed("PROCESSING_ERROR", e.getMessage());
        }
    }
    
    /**
     * Tokenize card for future use
     */
    @PaymentResilience
    public CardTokenizationResult tokenizeCard(CardTokenizationRequest request) {
        log.info("Tokenizing card for user: {}", request.getUserId());
        
        try {
            CardTokenRequest tokenRequest = CardTokenRequest.builder()
                .cardNumber(request.getCardNumber())
                .expiryMonth(request.getExpiryMonth())
                .expiryYear(request.getExpiryYear())
                .cvc(request.getCvc())
                .cardholderName(request.getCardholderName())
                .build();
            
            CardTokenResponse tokenResponse = callCardProcessorApi("/tokens", tokenRequest, CardTokenResponse.class);
            
            if (tokenResponse.isSuccessful()) {
                return CardTokenizationResult.successful(tokenResponse.getTokenId(), tokenResponse.getCardFingerprint());
            } else {
                return CardTokenizationResult.failed(tokenResponse.getErrorCode(), tokenResponse.getErrorMessage());
            }
            
        } catch (Exception e) {
            log.error("Error tokenizing card", e);
            return CardTokenizationResult.failed("TOKENIZATION_ERROR", e.getMessage());
        }
    }
    
    private boolean isValidCardPaymentMethod(PaymentMethod method) {
        return method == PaymentMethod.CREDIT_CARD || method == PaymentMethod.DEBIT_CARD;
    }
    
    private BigDecimal calculateProcessingFees(BigDecimal amount) {
        BigDecimal percentageFee = amount.multiply(processingFeePercentage);
        return percentageFee.add(processingFeeFixed);
    }
    
    private CardPaymentResponse processCardPayment(CardPaymentRequest request) {
        try {
            return callCardProcessorApi("/charges", request, CardPaymentResponse.class);
        } catch (Exception e) {
            log.error("Error processing card payment", e);
            return CardPaymentResponse.failed("PAYMENT_FAILED", e.getMessage());
        }
    }
    
    private WithdrawProcessingResult processCardRefund(WithdrawRequest request) {
        try {
            CardRefundRequest refundRequest = CardRefundRequest.builder()
                .amount(request.getAmount().multiply(new BigDecimal("100")).intValue())
                .paymentMethodId(request.getPaymentMethodId())
                .reason("requested_by_customer")
                .reference(request.getReference())
                .build();
            
            CardRefundResponse refundResponse = callCardProcessorApi("/refunds", refundRequest, CardRefundResponse.class);
            
            if (refundResponse.isSuccessful()) {
                return WithdrawProcessingResult.successful(refundResponse.getRefundId());
            } else {
                return WithdrawProcessingResult.failed(refundResponse.getErrorCode(), refundResponse.getErrorMessage());
            }
            
        } catch (Exception e) {
            log.error("Error processing card refund", e);
            return WithdrawProcessingResult.failed("REFUND_ERROR", e.getMessage());
        }
    }
    
    private WithdrawProcessingResult processDebitCardWithdrawal(WithdrawRequest request) {
        try {
            // For debit cards, we can potentially do instant transfers
            CardTransferRequest transferRequest = CardTransferRequest.builder()
                .amount(request.getAmount().multiply(new BigDecimal("100")).intValue())
                .destination(request.getPaymentMethodId())
                .currency(request.getCurrency().toString().toLowerCase())
                .reference(request.getReference())
                .build();
            
            CardTransferResponse transferResponse = callCardProcessorApi("/transfers", transferRequest, CardTransferResponse.class);
            
            if (transferResponse.isSuccessful()) {
                return WithdrawProcessingResult.successful(transferResponse.getTransferId());
            } else {
                return WithdrawProcessingResult.failed(transferResponse.getErrorCode(), transferResponse.getErrorMessage());
            }
            
        } catch (Exception e) {
            log.error("Error processing debit card withdrawal", e);
            return WithdrawProcessingResult.failed("TRANSFER_ERROR", e.getMessage());
        }
    }
    
    private <T> T callCardProcessorApi(String endpoint, Object request, Class<T> responseType) {
        try {
            String url = cardProcessorBaseUrl + endpoint;
            // In real implementation, would set Authorization header with API key
            return restTemplate.postForObject(url, request, responseType);
        } catch (Exception e) {
            log.error("Error calling card processor API: {}", endpoint, e);
            throw new ServiceException("Card processor API call failed", e);
        }
    }
    
    // Request/Response DTOs
    public static class CardPaymentRequest {
        private int amount;
        private String currency;
        private String paymentMethodId;
        private String description;
        private String reference;
        private Map<String, Object> metadata;
        
        public static CardPaymentRequestBuilder builder() {
            return new CardPaymentRequestBuilder();
        }
        
        public static class CardPaymentRequestBuilder {
            private CardPaymentRequest request = new CardPaymentRequest();
            
            public CardPaymentRequestBuilder amount(int amount) {
                request.amount = amount;
                return this;
            }
            
            public CardPaymentRequestBuilder currency(String currency) {
                request.currency = currency;
                return this;
            }
            
            public CardPaymentRequestBuilder paymentMethodId(String paymentMethodId) {
                request.paymentMethodId = paymentMethodId;
                return this;
            }
            
            public CardPaymentRequestBuilder description(String description) {
                request.description = description;
                return this;
            }
            
            public CardPaymentRequestBuilder reference(String reference) {
                request.reference = reference;
                return this;
            }
            
            public CardPaymentRequestBuilder metadata(Map<String, Object> metadata) {
                request.metadata = metadata;
                return this;
            }
            
            public CardPaymentRequest build() {
                return request;
            }
        }
        
        public int getAmount() { return amount; }
        public String getCurrency() { return currency; }
        public String getPaymentMethodId() { return paymentMethodId; }
        public String getDescription() { return description; }
        public String getReference() { return reference; }
        public Map<String, Object> getMetadata() { return metadata; }
    }
    
    public static class CardPaymentResponse {
        private boolean successful;
        private String transactionId;
        private String errorCode;
        private String errorMessage;
        private String status;
        
        public static CardPaymentResponse failed(String errorCode, String errorMessage) {
            CardPaymentResponse response = new CardPaymentResponse();
            response.successful = false;
            response.errorCode = errorCode;
            response.errorMessage = errorMessage;
            return response;
        }
        
        public boolean isSuccessful() { return successful; }
        public String getTransactionId() { return transactionId; }
        public String getErrorCode() { return errorCode; }
        public String getErrorMessage() { return errorMessage; }
        public String getStatus() { return status; }
    }
    
    public static class CardRefundRequest {
        private int amount;
        private String paymentMethodId;
        private String reason;
        private String reference;
        
        public static CardRefundRequestBuilder builder() {
            return new CardRefundRequestBuilder();
        }
        
        public static class CardRefundRequestBuilder {
            private CardRefundRequest request = new CardRefundRequest();
            
            public CardRefundRequestBuilder amount(int amount) {
                request.amount = amount;
                return this;
            }
            
            public CardRefundRequestBuilder paymentMethodId(String paymentMethodId) {
                request.paymentMethodId = paymentMethodId;
                return this;
            }
            
            public CardRefundRequestBuilder reason(String reason) {
                request.reason = reason;
                return this;
            }
            
            public CardRefundRequestBuilder reference(String reference) {
                request.reference = reference;
                return this;
            }
            
            public CardRefundRequest build() {
                return request;
            }
        }
        
        public int getAmount() { return amount; }
        public String getPaymentMethodId() { return paymentMethodId; }
        public String getReason() { return reason; }
        public String getReference() { return reference; }
    }
    
    public static class CardRefundResponse {
        private boolean successful;
        private String refundId;
        private String errorCode;
        private String errorMessage;
        
        public boolean isSuccessful() { return successful; }
        public String getRefundId() { return refundId; }
        public String getErrorCode() { return errorCode; }
        public String getErrorMessage() { return errorMessage; }
    }
    
    public static class CardTransferRequest {
        private int amount;
        private String destination;
        private String currency;
        private String reference;
        
        public static CardTransferRequestBuilder builder() {
            return new CardTransferRequestBuilder();
        }
        
        public static class CardTransferRequestBuilder {
            private CardTransferRequest request = new CardTransferRequest();
            
            public CardTransferRequestBuilder amount(int amount) {
                request.amount = amount;
                return this;
            }
            
            public CardTransferRequestBuilder destination(String destination) {
                request.destination = destination;
                return this;
            }
            
            public CardTransferRequestBuilder currency(String currency) {
                request.currency = currency;
                return this;
            }
            
            public CardTransferRequestBuilder reference(String reference) {
                request.reference = reference;
                return this;
            }
            
            public CardTransferRequest build() {
                return request;
            }
        }
        
        public int getAmount() { return amount; }
        public String getDestination() { return destination; }
        public String getCurrency() { return currency; }
        public String getReference() { return reference; }
    }
    
    public static class CardTransferResponse {
        private boolean successful;
        private String transferId;
        private String errorCode;
        private String errorMessage;
        
        public boolean isSuccessful() { return successful; }
        public String getTransferId() { return transferId; }
        public String getErrorCode() { return errorCode; }
        public String getErrorMessage() { return errorMessage; }
    }
    
    public static class CardTokenRequest {
        private String cardNumber;
        private int expiryMonth;
        private int expiryYear;
        private String cvc;
        private String cardholderName;
        
        public static CardTokenRequestBuilder builder() {
            return new CardTokenRequestBuilder();
        }
        
        public static class CardTokenRequestBuilder {
            private CardTokenRequest request = new CardTokenRequest();
            
            public CardTokenRequestBuilder cardNumber(String cardNumber) {
                request.cardNumber = cardNumber;
                return this;
            }
            
            public CardTokenRequestBuilder expiryMonth(int expiryMonth) {
                request.expiryMonth = expiryMonth;
                return this;
            }
            
            public CardTokenRequestBuilder expiryYear(int expiryYear) {
                request.expiryYear = expiryYear;
                return this;
            }
            
            public CardTokenRequestBuilder cvc(String cvc) {
                request.cvc = cvc;
                return this;
            }
            
            public CardTokenRequestBuilder cardholderName(String cardholderName) {
                request.cardholderName = cardholderName;
                return this;
            }
            
            public CardTokenRequest build() {
                return request;
            }
        }
        
        public String getCardNumber() { return cardNumber; }
        public int getExpiryMonth() { return expiryMonth; }
        public int getExpiryYear() { return expiryYear; }
        public String getCvc() { return cvc; }
        public String getCardholderName() { return cardholderName; }
    }
    
    public static class CardTokenResponse {
        private boolean successful;
        private String tokenId;
        private String cardFingerprint;
        private String errorCode;
        private String errorMessage;
        
        public boolean isSuccessful() { return successful; }
        public String getTokenId() { return tokenId; }
        public String getCardFingerprint() { return cardFingerprint; }
        public String getErrorCode() { return errorCode; }
        public String getErrorMessage() { return errorMessage; }
    }
}