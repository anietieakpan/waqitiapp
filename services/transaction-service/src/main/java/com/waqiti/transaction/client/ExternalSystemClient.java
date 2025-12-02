package com.waqiti.transaction.client;

import com.waqiti.transaction.dto.*;
import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.*;

import java.util.ArrayList;
import java.util.List;

@FeignClient(
    name = "external-system-service",
    path = "/api/external",
    fallback = ExternalSystemClientFallback.class,
    configuration = ExternalSystemClientConfiguration.class
)
public interface ExternalSystemClient {

    @PostMapping("/transfer")
    ExternalTransferResponse processExternalTransfer(@RequestBody ExternalTransferRequest request);

    @PostMapping("/validate")
    ExternalValidationResponse validateExternalAccount(@RequestBody ExternalValidationRequest request);

    @GetMapping("/transfer/{transferId}/status")
    String getTransferStatus(@PathVariable String transferId);

    @PostMapping("/transfer/{transferId}/cancel")
    void cancelExternalTransfer(@PathVariable String transferId);
    
    default List<ExternalTransactionRecord> getTransactionRecords(String batchId, ReconciliationConfig config) {
        // Default implementation that would be overridden by actual implementation
        return new ArrayList<>();
    }
}