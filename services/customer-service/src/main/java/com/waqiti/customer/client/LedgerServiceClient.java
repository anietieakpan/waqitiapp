package com.waqiti.customer.client;

import com.waqiti.customer.client.dto.PendingTransactionResponse;
import com.waqiti.customer.client.fallback.LedgerServiceClientFallback;
import com.waqiti.customer.config.FeignClientConfig;
import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;

import java.math.BigDecimal;
import java.util.List;

/**
 * Feign client for inter-service communication with ledger-service.
 * Provides methods to retrieve pending transactions and ledger information.
 *
 * @author Waqiti Engineering Team
 * @version 1.0
 * @since 2025-11-20
 */
@FeignClient(
    name = "ledger-service",
    configuration = FeignClientConfig.class,
    fallback = LedgerServiceClientFallback.class
)
public interface LedgerServiceClient {

    /**
     * Retrieves all pending transactions for an account.
     *
     * @param accountId The unique account identifier
     * @return List of pending transactions
     */
    @GetMapping("/api/v1/ledger/account/{accountId}/pending-transactions")
    List<PendingTransactionResponse> getPendingTransactions(@PathVariable("accountId") String accountId);

    /**
     * Retrieves total pending debits for an account.
     *
     * @param accountId The unique account identifier
     * @return Total pending debit amount
     */
    @GetMapping("/api/v1/ledger/account/{accountId}/pending-debits")
    BigDecimal getPendingDebits(@PathVariable("accountId") String accountId);

    /**
     * Retrieves total pending credits for an account.
     *
     * @param accountId The unique account identifier
     * @return Total pending credit amount
     */
    @GetMapping("/api/v1/ledger/account/{accountId}/pending-credits")
    BigDecimal getPendingCredits(@PathVariable("accountId") String accountId);

    /**
     * Freezes ledger entries for an account, preventing new transactions.
     *
     * @param accountId The unique account identifier
     */
    @PostMapping("/api/v1/ledger/account/{accountId}/freeze")
    void freezeLedger(@PathVariable("accountId") String accountId);
}
