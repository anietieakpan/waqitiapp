package com.waqiti.rewards.client;

import com.waqiti.rewards.dto.PaymentDetailsDto;
import com.waqiti.rewards.dto.TransactionDetailsDto;
import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestHeader;

@FeignClient(
    name = "payment-service",
    url = "${services.payment.url:http://payment-service:8082}",
    configuration = PaymentServiceClientConfig.class,
    fallback = PaymentServiceClientFallback.class
)
public interface PaymentServiceClient {

    @GetMapping("/api/v1/payments/{paymentId}")
    PaymentDetailsDto getPaymentDetails(
        @PathVariable("paymentId") String paymentId,
        @RequestHeader("Authorization") String authorization
    );

    @GetMapping("/api/v1/transactions/{transactionId}")
    TransactionDetailsDto getTransactionDetails(
        @PathVariable("transactionId") String transactionId,
        @RequestHeader("Authorization") String authorization
    );

    @GetMapping("/api/v1/payments/reference/{referenceId}")
    PaymentDetailsDto getPaymentByReference(
        @PathVariable("referenceId") String referenceId,
        @RequestHeader("Authorization") String authorization
    );
}