package com.waqiti.transaction.client;

import com.waqiti.transaction.dto.*;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.Collections;
import java.util.List;

@Slf4j
@Component
public class ExternalSystemClientFallback implements ExternalSystemClient {

    @Override
    public ExternalTransferResponse processExternalTransfer(ExternalTransferRequest request) {
        log.error("FALLBACK ACTIVATED: BLOCKING external transfer - External System unavailable. " +
                "Amount: {}, Destination: {}", request.getAmount(), request.getDestinationAccount());
        
        // CRITICAL: Block all external transfers when service unavailable
        return ExternalTransferResponse.builder()
                .success(false)
                .transferId(null)
                .status("BLOCKED")
                .message("External transfer temporarily unavailable - transaction blocked for safety")
                .errorCode("EXTERNAL_SYSTEM_UNAVAILABLE")
                .requiresRetry(true)
                .build();
    }

    @Override
    public ExternalValidationResponse validateExternalAccount(ExternalValidationRequest request) {
        log.warn("FALLBACK ACTIVATED: Cannot validate external account - External System unavailable. " +
                "Account: {}", request.getAccountNumber());
        
        // Cannot validate - return invalid for safety
        return ExternalValidationResponse.builder()
                .valid(false)
                .accountNumber(request.getAccountNumber())
                .status("UNAVAILABLE")
                .message("External account validation temporarily unavailable")
                .errorCode("VALIDATION_SERVICE_UNAVAILABLE")
                .build();
    }

    @Override
    public String getTransferStatus(String transferId) {
        log.warn("FALLBACK ACTIVATED: Cannot retrieve transfer status - External System unavailable. " +
                "TransferId: {}", transferId);
        
        return "STATUS_UNAVAILABLE";
    }

    @Override
    public void cancelExternalTransfer(String transferId) {
        log.error("FALLBACK ACTIVATED: Cannot cancel external transfer - External System unavailable. " +
                "TransferId: {} - MANUAL INTERVENTION REQUIRED", transferId);
        
        // Log for manual intervention
        log.error("====== MANUAL CANCELLATION REQUIRED ======");
        log.error("Transfer ID: {}", transferId);
        log.error("System: External System");
        log.error("Action Required: Manual cancellation");
        log.error("=========================================");
    }

    @Override
    public List<ExternalTransactionRecord> getTransactionRecords(String batchId, ReconciliationConfig config) {
        log.error("FALLBACK ACTIVATED: Cannot retrieve transaction records - External System unavailable. " +
                "BatchId: {}", batchId);
        
        // Return empty list - reconciliation will need to handle missing data
        return Collections.emptyList();
    }
}