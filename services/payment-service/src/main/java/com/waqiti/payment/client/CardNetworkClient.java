package com.waqiti.payment.client;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.web.reactive.function.client.WebClient;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import reactor.core.publisher.Mono;

import java.math.BigDecimal;
import java.time.Duration;
import java.util.Map;

/**
 * Client for interacting with card network APIs (Visa Direct, Mastercard Send, etc.)
 * Handles instant deposit transactions through card networks
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class CardNetworkClient {
    
    private final WebClient.Builder webClientBuilder;
    
    private static final Duration DEFAULT_TIMEOUT = Duration.ofSeconds(30);
    
    /**
     * Process Visa Direct instant deposit
     */
    public Mono<VisaDirectResponse> processVisaDirect(VisaDirectRequest request) {
        log.info("Processing Visa Direct request for amount: {}", request.getAmount());
        
        return webClientBuilder
                .baseUrl("https://sandbox.api.visa.com/visadirect")
                .build()
                .post()
                .uri("/fundstransfer/v1/pushfundstransactions")
                .header(HttpHeaders.CONTENT_TYPE, MediaType.APPLICATION_JSON_VALUE)
                .header("X-Client-Transaction-ID", request.getTransactionId())
                .bodyValue(request)
                .retrieve()
                .bodyToMono(VisaDirectResponse.class)
                .timeout(DEFAULT_TIMEOUT)
                .doOnSuccess(response -> log.info("Visa Direct processed successfully: {}", response.getTransactionId()))
                .doOnError(error -> log.error("Visa Direct processing failed", error));
    }
    
    /**
     * Process Mastercard Send instant deposit
     */
    public Mono<MastercardSendResponse> processMastercardSend(MastercardSendRequest request) {
        log.info("Processing Mastercard Send request for amount: {}", request.getAmount());
        
        return webClientBuilder
                .baseUrl("https://sandbox.api.mastercard.com/send")
                .build()
                .post()
                .uri("/v1/partners/transfers")
                .header(HttpHeaders.CONTENT_TYPE, MediaType.APPLICATION_JSON_VALUE)
                .header("X-Partner-ID", "WAQITI")
                .bodyValue(request)
                .retrieve()
                .bodyToMono(MastercardSendResponse.class)
                .timeout(DEFAULT_TIMEOUT)
                .doOnSuccess(response -> log.info("Mastercard Send processed successfully: {}", response.getTransferId()))
                .doOnError(error -> log.error("Mastercard Send processing failed", error));
    }
    
    /**
     * Validate card for instant deposit eligibility
     */
    public Mono<CardValidationResponse> validateCard(String cardNumber) {
        log.info("Validating card for instant deposit eligibility");
        
        // In production, this would call the actual card network API
        return Mono.just(CardValidationResponse.builder()
                .isEligible(true)
                .cardType("DEBIT")
                .network(detectCardNetwork(cardNumber))
                .instantDepositEnabled(true)
                .maxAmount(BigDecimal.valueOf(5000))
                .build());
    }
    
    /**
     * Get instant deposit status from card network
     */
    public Mono<InstantDepositStatusResponse> getDepositStatus(String networkTransactionId, String network) {
        log.info("Getting deposit status for transaction: {} from network: {}", networkTransactionId, network);
        
        // In production, this would call the appropriate network API
        return Mono.just(InstantDepositStatusResponse.builder()
                .transactionId(networkTransactionId)
                .status("COMPLETED")
                .network(network)
                .completedAt(java.time.LocalDateTime.now())
                .build());
    }
    
    private String detectCardNetwork(String cardNumber) {
        if (cardNumber.startsWith("4")) return "VISA";
        if (cardNumber.startsWith("5")) return "MASTERCARD";
        if (cardNumber.startsWith("3")) return "AMEX";
        return "UNKNOWN";
    }
    
    // Request/Response DTOs
    
    @lombok.Data
    @lombok.Builder
    public static class VisaDirectRequest {
        private String transactionId;
        private BigDecimal amount;
        private String currency;
        private DebitCardDetails senderCard;
        private String recipientName;
        private String recipientAccountNumber;
    }
    
    @lombok.Data
    @lombok.Builder
    public static class VisaDirectResponse {
        private String transactionId;
        private String networkTransactionId;
        private String status;
        private String approvalCode;
        private String responseCode;
        private String responseMessage;
    }
    
    @lombok.Data
    @lombok.Builder
    public static class MastercardSendRequest {
        private String transferId;
        private BigDecimal amount;
        private String currency;
        private DebitCardDetails fundingCard;
        private String recipientName;
        private String recipientAccountNumber;
    }
    
    @lombok.Data
    @lombok.Builder
    public static class MastercardSendResponse {
        private String transferId;
        private String networkReferenceNumber;
        private String status;
        private String approvalCode;
        private Map<String, Object> additionalData;
    }
    
    @lombok.Data
    @lombok.Builder
    public static class DebitCardDetails {
        private String cardNumber;
        private String expiryMonth;
        private String expiryYear;
        private String cvv;
        private String cardholderName;
        private String billingZipCode;
    }
    
    @lombok.Data
    @lombok.Builder
    public static class CardValidationResponse {
        private boolean isEligible;
        private String cardType;
        private String network;
        private boolean instantDepositEnabled;
        private BigDecimal maxAmount;
    }
    
    @lombok.Data
    @lombok.Builder
    public static class InstantDepositStatusResponse {
        private String transactionId;
        private String status;
        private String network;
        private java.time.LocalDateTime completedAt;
    }
}