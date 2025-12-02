package com.waqiti.rewards.client;

import com.waqiti.rewards.dto.PaymentDetailsDto;
import com.waqiti.rewards.dto.TransactionDetailsDto;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

@Slf4j
@Component
public class PaymentServiceClientFallback implements PaymentServiceClient {

    @Override
    public PaymentDetailsDto getPaymentDetails(String paymentId, String authorization) {
        log.warn("FALLBACK ACTIVATED: Cannot retrieve payment details - Payment Service unavailable. PaymentId: {}", 
                paymentId);
        
        return PaymentDetailsDto.builder()
                .paymentId(paymentId)
                .status("UNAVAILABLE")
                .message("Payment details temporarily unavailable - reward calculation may be delayed")
                .isStale(true)
                .build();
    }

    @Override
    public TransactionDetailsDto getTransactionDetails(String transactionId, String authorization) {
        log.warn("FALLBACK ACTIVATED: Cannot retrieve transaction details - Payment Service unavailable. TransactionId: {}", 
                transactionId);
        
        return TransactionDetailsDto.builder()
                .transactionId(transactionId)
                .status("UNAVAILABLE")
                .message("Transaction details temporarily unavailable - reward calculation may be delayed")
                .isStale(true)
                .build();
    }

    @Override
    public PaymentDetailsDto getPaymentByReference(String referenceId, String authorization) {
        log.warn("FALLBACK ACTIVATED: Cannot retrieve payment by reference - Payment Service unavailable. ReferenceId: {}", 
                referenceId);
        
        return PaymentDetailsDto.builder()
                .referenceId(referenceId)
                .status("UNAVAILABLE")
                .message("Payment reference lookup temporarily unavailable")
                .isStale(true)
                .build();
    }
}