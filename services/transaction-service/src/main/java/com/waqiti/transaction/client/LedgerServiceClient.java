package com.waqiti.transaction.client;

import com.waqiti.transaction.dto.LedgerEntryRequest;
import com.waqiti.transaction.dto.LedgerEntryResponse;
import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;

@FeignClient(
    name = "ledger-service",
    path = "/api/ledger",
    fallback = LedgerServiceClientFallback.class,
    configuration = LedgerServiceClientConfiguration.class
)
public interface LedgerServiceClient {

    @PostMapping("/entries")
    LedgerEntryResponse createLedgerEntry(@RequestBody LedgerEntryRequest request);

    @PostMapping("/reverse")
    LedgerEntryResponse reverseLedgerEntry(@RequestBody LedgerEntryRequest request);
}