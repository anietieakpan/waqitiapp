package com.waqiti.reporting.client;

import com.waqiti.reporting.dto.AccountDetails;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.UUID;

@Slf4j
@Component
public class AccountServiceClientFallback implements AccountServiceClient {

    @Override
    public AccountDetails getAccountDetails(UUID accountId) {
        log.error("FALLBACK ACTIVATED: Cannot retrieve account details - Account Service unavailable. AccountId: {}",
                accountId);

        return AccountDetails.builder()
                .accountId(accountId)
                .accountStatus("UNAVAILABLE")
                .build();
    }
}