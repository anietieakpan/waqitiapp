package com.waqiti.payment.dto;

import com.waqiti.payment.domain.PaymentMethod;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import jakarta.validation.constraints.NotNull;
import java.time.LocalDate;
import java.util.Map;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class CreatePaymentMethodRequest {
    
    @NotNull(message = "Method type is required")
    private PaymentMethod.PaymentMethodType methodType;
    
    @NotNull(message = "Provider is required")
    private String provider;
    
    private String displayName;
    
    @NotNull(message = "Payment details are required")
    private PaymentDetails details;
    
    private boolean setAsDefault;
    
    private Map<String, Object> metadata;
    
    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    @Builder
    public static class PaymentDetails {
        // For bank account
        private String accountNumber;
        private String routingNumber;
        private String accountType; // CHECKING, SAVINGS
        
        // For cards
        private String cardNumber;
        private String cardholderName;
        private String expiryMonth;
        private String expiryYear;
        private String cvv;
        private String billingZipCode;
        
        // For digital wallet
        private String walletId;
        private String walletProvider; // PAYPAL, APPLE_PAY, GOOGLE_PAY
        
        // For cryptocurrency
        private String walletAddress;
        private String cryptoType; // BTC, ETH, etc.
        private String network; // mainnet, testnet
    }
}