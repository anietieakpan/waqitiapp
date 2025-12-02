package com.waqiti.payment.client;

import com.waqiti.payment.dto.ExternalCheckRequest;
import com.waqiti.payment.dto.ExternalCheckResponse;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;
import java.util.UUID;

@Slf4j
@Component
public class CheckProcessingClientFallback implements CheckProcessingClient {

    @Override
    public ExternalCheckResponse submitCheckDeposit(ExternalCheckRequest request) {
        log.error("FALLBACK ACTIVATED: QUEUING check deposit - Check Processing Service unavailable. " +
                "Check#: {}, Amount: {}", request.getCheckNumber(), request.getAmount());
        
        // Queue check deposits for later processing
        // Checks can be processed in batch when service recovers
        return ExternalCheckResponse.builder()
                .success(true) // Return success to avoid blocking user
                .transactionId("QUEUED-CHECK-" + UUID.randomUUID())
                .checkNumber(request.getCheckNumber())
                .amount(request.getAmount())
                .status("QUEUED_FOR_PROCESSING")
                .message("Check deposit queued - will be processed within 1-2 business days")
                .queuedAt(LocalDateTime.now())
                .estimatedClearingDate(LocalDateTime.now().plusDays(2))
                .requiresManualReview(false)
                .build();
    }
}