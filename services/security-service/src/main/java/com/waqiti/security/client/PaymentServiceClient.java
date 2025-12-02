package com.waqiti.security.client;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;
import org.springframework.web.reactive.function.client.WebClient;
import org.springframework.web.reactive.function.client.WebClientResponseException;
import reactor.core.publisher.Mono;
import reactor.util.retry.Retry;

import java.math.BigDecimal;
import java.time.Duration;

@Slf4j
@Component
@RequiredArgsConstructor
public class PaymentServiceClient {

    private final WebClient.Builder webClientBuilder;

    @Value("${services.payment-service.url:http://payment-service:8080}")
    private String paymentServiceUrl;

    @Value("${services.payment-service.timeout:5000}")
    private int timeoutMillis;

    @Cacheable(value = "paymentDetails", key = "#paymentId", unless = "#result == null")
    public PaymentDetailsResponse getPaymentDetails(String paymentId) {
        log.debug("Fetching payment details: paymentId={}", paymentId);
        
        try {
            return webClientBuilder.build()
                .get()
                .uri(paymentServiceUrl + "/api/payments/{paymentId}", paymentId)
                .retrieve()
                .bodyToMono(PaymentDetailsResponse.class)
                .timeout(Duration.ofMillis(timeoutMillis))
                .retryWhen(Retry.backoff(2, Duration.ofMillis(100)))
                .block();
                
        } catch (WebClientResponseException.NotFound e) {
            log.warn("Payment not found: paymentId={}", paymentId);
            return null;
        } catch (Exception e) {
            log.error("Failed to fetch payment details: paymentId={}", paymentId, e);
            return null;
        }
    }

    public Boolean checkPaymentAccess(String userId, String paymentId) {
        log.debug("Checking payment access: userId={}, paymentId={}", userId, paymentId);
        
        try {
            PaymentDetailsResponse payment = getPaymentDetails(paymentId);
            
            if (payment == null) {
                return false;
            }
            
            return userId.equals(payment.getSenderId()) || userId.equals(payment.getReceiverId());
            
        } catch (Exception e) {
            log.error("Failed to check payment access: userId={}, paymentId={}", 
                userId, paymentId, e);
            return false;
        }
    }

    public static class PaymentDetailsResponse {
        private String id;
        private String senderId;
        private String receiverId;
        private BigDecimal amount;
        private String currency;
        private String status;
        private String type;

        public String getId() { return id; }
        public void setId(String id) { this.id = id; }
        
        public String getSenderId() { return senderId; }
        public void setSenderId(String senderId) { this.senderId = senderId; }
        
        public String getReceiverId() { return receiverId; }
        public void setReceiverId(String receiverId) { this.receiverId = receiverId; }
        
        public BigDecimal getAmount() { return amount; }
        public void setAmount(BigDecimal amount) { this.amount = amount; }
        
        public String getCurrency() { return currency; }
        public void setCurrency(String currency) { this.currency = currency; }
        
        public String getStatus() { return status; }
        public void setStatus(String status) { this.status = status; }
        
        public String getType() { return type; }
        public void setType(String type) { this.type = type; }
    }
}