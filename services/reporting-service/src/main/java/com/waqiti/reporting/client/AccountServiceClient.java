package com.waqiti.reporting.client;

import com.waqiti.reporting.dto.AccountDetails;
import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;

import java.util.UUID;

@FeignClient(
    name = "account-service", 
    path = "/api/v1/accounts",
    fallback = AccountServiceClientFallback.class
)
public interface AccountServiceClient {
    
    @GetMapping("/{accountId}")
    AccountDetails getAccountDetails(@PathVariable UUID accountId);
}