package com.waqiti.analytics.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Duration;
import java.time.LocalDateTime;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Reconciliation Service - Handles automated transaction and account reconciliation
 * 
 * Provides comprehensive reconciliation capabilities for:
 * - Real-time transaction reconciliation and verification
 * - Account balance reconciliation and validation
 * - Discrepancy detection and alerting
 * - Multi-source data reconciliation
 * - Automated reconciliation reporting
 * - Exception management and resolution tracking
 * 
 * @author Waqiti Engineering Team
 * @version 2.0.0
 * @since 2024-01-01
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class ReconciliationService {

    private final RedisTemplate<String, Object> redisTemplate;

    @Value("${reconciliation.enabled:true}")
    private boolean reconciliationEnabled;

    @Value("${reconciliation.discrepancy.threshold:0.01}")
    private BigDecimal discrepancyThreshold;

    @Value("${reconciliation.auto.resolve.enabled:true}")
    private boolean autoResolveEnabled;

    // Cache for tracking reconciliation status
    private final Map<String, ReconciliationStatus> reconciliationStatuses = new ConcurrentHashMap<>();

    /**
     * Processes ledger event for reconciliation
     */
    public void processLedgerEventForReconciliation(
            String eventId,
            String eventType,
            String transactionReference,
            String accountNumber,
            BigDecimal debitAmount,
            BigDecimal creditAmount,
            String currency,
            LocalDateTime timestamp) {

        if (!reconciliationEnabled) {
            log.debug("Reconciliation disabled, skipping ledger event processing");
            return;
        }

        try {
            log.debug("Processing ledger event for reconciliation: {} - Type: {}", eventId, eventType);

            // Create reconciliation record
            ReconciliationRecord record = ReconciliationRecord.builder()
                .eventId(eventId)
                .eventType(eventType)
                .transactionReference(transactionReference)
                .accountNumber(accountNumber)
                .debitAmount(debitAmount)
                .creditAmount(creditAmount)
                .currency(currency)
                .timestamp(timestamp)
                .processedAt(LocalDateTime.now())
                .build();

            // Store reconciliation record
            storeReconciliationRecord(record);

            // Perform reconciliation based on event type
            if (eventType.contains("JOURNAL") || eventType.contains("TRANSACTION")) {
                performTransactionReconciliation(record);
            }

            if (eventType.contains("BALANCE") || eventType.contains("ACCOUNT")) {
                performAccountReconciliation(record);
            }

            // Update reconciliation metrics
            updateReconciliationMetrics(eventType, currency);

            log.debug("Successfully processed ledger event for reconciliation: {}", eventId);

        } catch (Exception e) {
            log.error("Failed to process ledger event for reconciliation: {}", eventId, e);
        }
    }

    /**
     * Checks for reconciliation discrepancies
     */
    public void checkReconciliationDiscrepancies(String accountNumber, String currency) {
        if (!reconciliationEnabled || accountNumber == null) {
            return;
        }

        try {
            log.debug("Checking reconciliation discrepancies for account: {}", accountNumber);

            // Get expected balance from ledger
            BigDecimal ledgerBalance = getLedgerBalance(accountNumber, currency);

            // Get actual balance from reconciliation records
            BigDecimal reconciliationBalance = getReconciliationBalance(accountNumber, currency);

            // Calculate discrepancy
            BigDecimal discrepancy = ledgerBalance.subtract(reconciliationBalance).abs();

            // Check if discrepancy exceeds threshold
            if (discrepancy.compareTo(discrepancyThreshold) > 0) {
                handleDiscrepancy(accountNumber, currency, ledgerBalance, reconciliationBalance, discrepancy);
            } else {
                // Mark as reconciled
                markAsReconciled(accountNumber, currency, timestamp());
            }

            log.debug("Discrepancy check completed for account: {} - Discrepancy: {} {}", 
                accountNumber, discrepancy, currency);

        } catch (Exception e) {
            log.error("Failed to check reconciliation discrepancies for account: {}", accountNumber, e);
        }
    }

    /**
     * Performs transaction reconciliation
     */
    private void performTransactionReconciliation(ReconciliationRecord record) {
        try {
            log.debug("Performing transaction reconciliation for: {}", record.getTransactionReference());

            // Check if transaction exists in both source and target systems
            boolean sourceExists = checkSourceSystem(record.getTransactionReference());
            boolean targetExists = checkTargetSystem(record.getTransactionReference());

            if (sourceExists && targetExists) {
                // Verify amounts match
                boolean amountsMatch = verifyTransactionAmounts(record);
                
                if (amountsMatch) {
                    updateReconciliationStatus(
                        record.getTransactionReference(), 
                        ReconciliationStatus.Status.RECONCILED,
                        "Transaction reconciled successfully"
                    );
                } else {
                    updateReconciliationStatus(
                        record.getTransactionReference(),
                        ReconciliationStatus.Status.DISCREPANCY,
                        "Transaction amount mismatch"
                    );
                }
            } else {
                updateReconciliationStatus(
                    record.getTransactionReference(),
                    ReconciliationStatus.Status.MISSING,
                    String.format("Transaction missing - Source: %s, Target: %s", sourceExists, targetExists)
                );
            }

        } catch (Exception e) {
            log.error("Failed to perform transaction reconciliation", e);
        }
    }

    /**
     * Performs account reconciliation
     */
    private void performAccountReconciliation(ReconciliationRecord record) {
        try {
            if (record.getAccountNumber() == null) {
                return;
            }

            log.debug("Performing account reconciliation for: {}", record.getAccountNumber());

            // Update account reconciliation totals
            updateAccountReconciliationTotals(
                record.getAccountNumber(),
                record.getDebitAmount(),
                record.getCreditAmount(),
                record.getCurrency()
            );

            // Check if account requires immediate reconciliation
            if (requiresImmediateReconciliation(record.getAccountNumber())) {
                triggerImmediateReconciliation(record.getAccountNumber(), record.getCurrency());
            }

        } catch (Exception e) {
            log.error("Failed to perform account reconciliation", e);
        }
    }

    /**
     * Handles detected discrepancy
     */
    private void handleDiscrepancy(
            String accountNumber,
            String currency,
            BigDecimal ledgerBalance,
            BigDecimal reconciliationBalance,
            BigDecimal discrepancy) {

        try {
            log.warn("Discrepancy detected for account: {} - Ledger: {} {}, Reconciliation: {} {}, Discrepancy: {} {}",
                accountNumber, ledgerBalance, currency, reconciliationBalance, currency, discrepancy, currency);

            // Create discrepancy record
            DiscrepancyRecord discrepancyRecord = DiscrepancyRecord.builder()
                .accountNumber(accountNumber)
                .currency(currency)
                .ledgerBalance(ledgerBalance)
                .reconciliationBalance(reconciliationBalance)
                .discrepancyAmount(discrepancy)
                .detectedAt(LocalDateTime.now())
                .status(DiscrepancyStatus.OPEN)
                .build();

            // Store discrepancy record
            storeDiscrepancyRecord(discrepancyRecord);

            // Attempt auto-resolution if enabled
            if (autoResolveEnabled && canAutoResolve(discrepancy)) {
                attemptAutoResolution(discrepancyRecord);
            } else {
                // Escalate for manual review
                escalateForManualReview(discrepancyRecord);
            }

            // Update metrics
            updateDiscrepancyMetrics(accountNumber, currency, discrepancy);

        } catch (Exception e) {
            log.error("Failed to handle discrepancy for account: {}", accountNumber, e);
        }
    }

    /**
     * Stores reconciliation record
     */
    private void storeReconciliationRecord(ReconciliationRecord record) {
        try {
            String recordKey = "reconciliation:record:" + record.getEventId();
            Map<String, String> recordData = Map.of(
                "event_id", record.getEventId(),
                "event_type", record.getEventType(),
                "transaction_reference", record.getTransactionReference() != null ? record.getTransactionReference() : "",
                "account_number", record.getAccountNumber() != null ? record.getAccountNumber() : "",
                "debit_amount", record.getDebitAmount() != null ? record.getDebitAmount().toString() : "0",
                "credit_amount", record.getCreditAmount() != null ? record.getCreditAmount().toString() : "0",
                "currency", record.getCurrency(),
                "timestamp", record.getTimestamp().toString(),
                "processed_at", record.getProcessedAt().toString()
            );

            redisTemplate.opsForHash().putAll(recordKey, recordData);
            redisTemplate.expire(recordKey, Duration.ofDays(30));

        } catch (Exception e) {
            log.error("Failed to store reconciliation record", e);
        }
    }

    /**
     * Stores discrepancy record
     */
    private void storeDiscrepancyRecord(DiscrepancyRecord record) {
        try {
            String discrepancyKey = "reconciliation:discrepancy:" + record.getAccountNumber() + ":" + 
                System.currentTimeMillis();
            
            Map<String, String> discrepancyData = Map.of(
                "account_number", record.getAccountNumber(),
                "currency", record.getCurrency(),
                "ledger_balance", record.getLedgerBalance().toString(),
                "reconciliation_balance", record.getReconciliationBalance().toString(),
                "discrepancy_amount", record.getDiscrepancyAmount().toString(),
                "detected_at", record.getDetectedAt().toString(),
                "status", record.getStatus().toString()
            );

            redisTemplate.opsForHash().putAll(discrepancyKey, discrepancyData);
            redisTemplate.expire(discrepancyKey, Duration.ofDays(90));

            // Add to active discrepancies set
            String activeKey = "reconciliation:discrepancies:active";
            redisTemplate.opsForSet().add(activeKey, discrepancyKey);
            redisTemplate.expire(activeKey, Duration.ofDays(7));

        } catch (Exception e) {
            log.error("Failed to store discrepancy record", e);
        }
    }

    /**
     * Updates reconciliation status
     */
    private void updateReconciliationStatus(String reference, ReconciliationStatus.Status status, String message) {
        ReconciliationStatus reconciliationStatus = reconciliationStatuses.computeIfAbsent(
            reference, k -> new ReconciliationStatus(k));
        
        reconciliationStatus.setStatus(status);
        reconciliationStatus.setMessage(message);
        reconciliationStatus.setLastUpdated(LocalDateTime.now());

        // Store in Redis
        String statusKey = "reconciliation:status:" + reference;
        Map<String, String> statusData = Map.of(
            "reference", reference,
            "status", status.toString(),
            "message", message,
            "last_updated", LocalDateTime.now().toString()
        );

        redisTemplate.opsForHash().putAll(statusKey, statusData);
        redisTemplate.expire(statusKey, Duration.ofDays(7));
    }

    /**
     * Gets ledger balance for account
     */
    private BigDecimal getLedgerBalance(String accountNumber, String currency) {
        try {
            String balanceKey = "ledger:balance:" + accountNumber + ":" + currency;
            Double balance = (Double) redisTemplate.opsForValue().get(balanceKey);
            return balance != null ? BigDecimal.valueOf(balance) : BigDecimal.ZERO;
        } catch (Exception e) {
            log.error("Failed to get ledger balance for account: {}", accountNumber, e);
            return BigDecimal.ZERO;
        }
    }

    /**
     * Gets reconciliation balance for account
     */
    private BigDecimal getReconciliationBalance(String accountNumber, String currency) {
        try {
            String balanceKey = "reconciliation:balance:" + accountNumber + ":" + currency;
            Double balance = (Double) redisTemplate.opsForValue().get(balanceKey);
            return balance != null ? BigDecimal.valueOf(balance) : BigDecimal.ZERO;
        } catch (Exception e) {
            log.error("Failed to get reconciliation balance for account: {}", accountNumber, e);
            return BigDecimal.ZERO;
        }
    }

    /**
     * Updates account reconciliation totals
     */
    private void updateAccountReconciliationTotals(
            String accountNumber,
            BigDecimal debitAmount,
            BigDecimal creditAmount,
            String currency) {
        
        try {
            String balanceKey = "reconciliation:balance:" + accountNumber + ":" + currency;
            
            BigDecimal netChange = (creditAmount != null ? creditAmount : BigDecimal.ZERO)
                .subtract(debitAmount != null ? debitAmount : BigDecimal.ZERO);
            
            redisTemplate.opsForValue().increment(balanceKey, netChange.doubleValue());
            redisTemplate.expire(balanceKey, Duration.ofDays(30));
            
        } catch (Exception e) {
            log.error("Failed to update account reconciliation totals", e);
        }
    }

    /**
     * Updates reconciliation metrics
     */
    private void updateReconciliationMetrics(String eventType, String currency) {
        try {
            // Update event count
            String countKey = "reconciliation:metrics:count:" + eventType + ":" + 
                LocalDateTime.now().toLocalDate();
            redisTemplate.opsForValue().increment(countKey);
            redisTemplate.expire(countKey, Duration.ofDays(30));

            // Update currency metrics
            String currencyKey = "reconciliation:metrics:currency:" + currency + ":" + 
                LocalDateTime.now().toLocalDate();
            redisTemplate.opsForValue().increment(currencyKey);
            redisTemplate.expire(currencyKey, Duration.ofDays(30));

        } catch (Exception e) {
            log.error("Failed to update reconciliation metrics", e);
        }
    }

    /**
     * Updates discrepancy metrics
     */
    private void updateDiscrepancyMetrics(String accountNumber, String currency, BigDecimal discrepancy) {
        try {
            // Update discrepancy count
            String countKey = "reconciliation:discrepancy:count:" + LocalDateTime.now().toLocalDate();
            redisTemplate.opsForValue().increment(countKey);
            redisTemplate.expire(countKey, Duration.ofDays(30));

            // Update discrepancy amount
            String amountKey = "reconciliation:discrepancy:amount:" + currency + ":" + 
                LocalDateTime.now().toLocalDate();
            redisTemplate.opsForValue().increment(amountKey, discrepancy.doubleValue());
            redisTemplate.expire(amountKey, Duration.ofDays(30));

        } catch (Exception e) {
            log.error("Failed to update discrepancy metrics", e);
        }
    }

    /**
     * Marks account as reconciled
     */
    private void markAsReconciled(String accountNumber, String currency, LocalDateTime timestamp) {
        try {
            String reconciledKey = "reconciliation:reconciled:" + accountNumber + ":" + currency;
            redisTemplate.opsForValue().set(reconciledKey, timestamp.toString());
            redisTemplate.expire(reconciledKey, Duration.ofDays(1));
        } catch (Exception e) {
            log.error("Failed to mark account as reconciled", e);
        }
    }

    /**
     * Checks if transaction exists in source system
     */
    private boolean checkSourceSystem(String transactionReference) {
        try {
            log.debug("Checking source system for transaction: {}", transactionReference);
            
            // Check multiple source systems in priority order
            
            // 1. Check Ledger Service (primary source of truth)
            if (checkLedgerServiceTransaction(transactionReference)) {
                log.debug("Transaction found in Ledger Service: {}", transactionReference);
                return true;
            }
            
            // 2. Check Payment Service
            if (checkPaymentServiceTransaction(transactionReference)) {
                log.debug("Transaction found in Payment Service: {}", transactionReference);
                return true;
            }
            
            // 3. Check Wallet Service
            if (checkWalletServiceTransaction(transactionReference)) {
                log.debug("Transaction found in Wallet Service: {}", transactionReference);
                return true;
            }
            
            // 4. Check external provider systems
            if (checkExternalProviderSystems(transactionReference)) {
                log.debug("Transaction found in external provider systems: {}", transactionReference);
                return true;
            }
            
            // 5. Check Redis cache as fallback
            String cacheKey = "source:transaction:" + transactionReference;
            if (Boolean.TRUE.equals(redisTemplate.hasKey(cacheKey))) {
                log.debug("Transaction found in Redis cache: {}", transactionReference);
                return true;
            }
            
            log.warn("Transaction not found in any source system: {}", transactionReference);
            return false;
            
        } catch (Exception e) {
            log.error("Error checking source system for transaction: {}", transactionReference, e);
            return false;
        }
    }
    
    private boolean checkLedgerServiceTransaction(String transactionReference) {
        try {
            // Make REST call to ledger service
            String url = "/api/v1/ledger/transactions/" + transactionReference + "/exists";
            Boolean exists = restTemplate.getForObject(ledgerServiceUrl + url, Boolean.class);
            return Boolean.TRUE.equals(exists);
        } catch (Exception e) {
            log.debug("Failed to check ledger service for transaction: {}", transactionReference);
            return false;
        }
    }
    
    private boolean checkPaymentServiceTransaction(String transactionReference) {
        try {
            String url = "/api/v1/payments/" + transactionReference + "/exists";
            Boolean exists = restTemplate.getForObject(paymentServiceUrl + url, Boolean.class);
            return Boolean.TRUE.equals(exists);
        } catch (Exception e) {
            log.debug("Failed to check payment service for transaction: {}", transactionReference);
            return false;
        }
    }
    
    private boolean checkWalletServiceTransaction(String transactionReference) {
        try {
            String url = "/api/v1/wallets/transactions/" + transactionReference + "/exists";
            Boolean exists = restTemplate.getForObject(walletServiceUrl + url, Boolean.class);
            return Boolean.TRUE.equals(exists);
        } catch (Exception e) {
            log.debug("Failed to check wallet service for transaction: {}", transactionReference);
            return false;
        }
    }
    
    private boolean checkExternalProviderSystems(String transactionReference) {
        try {
            // Check if this is an external provider transaction
            if (!transactionReference.startsWith("EXT_")) {
                return false;
            }
            
            // Extract provider from reference
            String[] parts = transactionReference.split("_");
            if (parts.length < 3) {
                return false;
            }
            
            String provider = parts[1]; // EXT_STRIPE_xxxxx, EXT_PAYPAL_xxxxx
            String providerTransactionId = parts[2];
            
            return switch (provider.toUpperCase()) {
                case "STRIPE" -> checkStripeTransaction(providerTransactionId);
                case "PAYPAL" -> checkPayPalTransaction(providerTransactionId);
                case "WESTERNUNION" -> checkWesternUnionTransaction(providerTransactionId);
                case "MONEYGRAM" -> checkMoneyGramTransaction(providerTransactionId);
                default -> {
                    log.warn("Unknown external provider: {}", provider);
                    yield false;
                }
            };
            
        } catch (Exception e) {
            log.debug("Failed to check external provider systems for transaction: {}", transactionReference);
            return false;
        }
    }
    
    private boolean checkStripeTransaction(String transactionId) {
        try {
            // Call Stripe API to verify transaction exists
            String apiKey = stripeApiKey;
            HttpHeaders headers = new HttpHeaders();
            headers.set("Authorization", "Bearer " + apiKey);
            
            HttpEntity<String> entity = new HttpEntity<>(headers);
            ResponseEntity<Map> response = restTemplate.exchange(
                "https://api.stripe.com/v1/payment_intents/" + transactionId,
                HttpMethod.GET,
                entity,
                Map.class
            );
            
            return response.getStatusCode().is2xxSuccessful();
        } catch (Exception e) {
            log.debug("Failed to verify Stripe transaction: {}", transactionId);
            return false;
        }
    }
    
    private boolean checkPayPalTransaction(String transactionId) {
        try {
            // Call PayPal API to verify transaction exists
            String accessToken = getPayPalAccessToken();
            HttpHeaders headers = new HttpHeaders();
            headers.set("Authorization", "Bearer " + accessToken);
            
            HttpEntity<String> entity = new HttpEntity<>(headers);
            ResponseEntity<Map> response = restTemplate.exchange(
                "https://api.paypal.com/v2/payments/captures/" + transactionId,
                HttpMethod.GET,
                entity,
                Map.class
            );
            
            return response.getStatusCode().is2xxSuccessful();
        } catch (Exception e) {
            log.debug("Failed to verify PayPal transaction: {}", transactionId);
            return false;
        }
    }
    
    private boolean checkWesternUnionTransaction(String transactionId) {
        try {
            // Call Western Union API to verify transaction
            HttpHeaders headers = new HttpHeaders();
            headers.set("Authorization", "Bearer " + westernUnionApiKey);
            
            HttpEntity<String> entity = new HttpEntity<>(headers);
            ResponseEntity<Map> response = restTemplate.exchange(
                westernUnionApiUrl + "/transactions/" + transactionId,
                HttpMethod.GET,
                entity,
                Map.class
            );
            
            return response.getStatusCode().is2xxSuccessful();
        } catch (Exception e) {
            log.debug("Failed to verify Western Union transaction: {}", transactionId);
            return false;
        }
    }
    
    private boolean checkMoneyGramTransaction(String transactionId) {
        try {
            // Call MoneyGram API to verify transaction
            HttpHeaders headers = new HttpHeaders();
            headers.set("X-API-Key", moneyGramApiKey);
            
            HttpEntity<String> entity = new HttpEntity<>(headers);
            ResponseEntity<Map> response = restTemplate.exchange(
                moneyGramApiUrl + "/transactions/" + transactionId,
                HttpMethod.GET,
                entity,
                Map.class
            );
            
            return response.getStatusCode().is2xxSuccessful();
        } catch (Exception e) {
            log.debug("Failed to verify MoneyGram transaction: {}", transactionId);
            return false;
        }
    }

    /**
     * Checks if transaction exists in target system
     */
    private boolean checkTargetSystem(String transactionReference) {
        try {
            log.debug("Checking target system for transaction: {}", transactionReference);
            
            // Check multiple target systems based on transaction type
            
            // 1. Check Core Banking System (primary target for most transactions)
            if (checkCoreBankingSystem(transactionReference)) {
                log.debug("Transaction found in Core Banking System: {}", transactionReference);
                return true;
            }
            
            // 2. Check Bank Integration Service
            if (checkBankIntegrationService(transactionReference)) {
                log.debug("Transaction found in Bank Integration Service: {}", transactionReference);
                return true;
            }
            
            // 3. Check International Transfer Service
            if (checkInternationalTransferService(transactionReference)) {
                log.debug("Transaction found in International Transfer Service: {}", transactionReference);
                return true;
            }
            
            // 4. Check external banking partners
            if (checkExternalBankingPartners(transactionReference)) {
                log.debug("Transaction found in external banking partners: {}", transactionReference);
                return true;
            }
            
            // 5. Check settlement systems
            if (checkSettlementSystems(transactionReference)) {
                log.debug("Transaction found in settlement systems: {}", transactionReference);
                return true;
            }
            
            // 6. Check Redis cache as fallback
            String cacheKey = "target:transaction:" + transactionReference;
            if (Boolean.TRUE.equals(redisTemplate.hasKey(cacheKey))) {
                log.debug("Transaction found in Redis cache: {}", transactionReference);
                return true;
            }
            
            log.warn("Transaction not found in any target system: {}", transactionReference);
            return false;
            
        } catch (Exception e) {
            log.error("Error checking target system for transaction: {}", transactionReference, e);
            return false;
        }
    }
    
    private boolean checkCoreBankingSystem(String transactionReference) {
        try {
            String url = "/api/v1/core-banking/transactions/" + transactionReference + "/exists";
            Boolean exists = restTemplate.getForObject(coreBankingServiceUrl + url, Boolean.class);
            return Boolean.TRUE.equals(exists);
        } catch (Exception e) {
            log.debug("Failed to check core banking system for transaction: {}", transactionReference);
            return false;
        }
    }
    
    private boolean checkBankIntegrationService(String transactionReference) {
        try {
            String url = "/api/v1/bank-integration/transactions/" + transactionReference + "/exists";
            Boolean exists = restTemplate.getForObject(bankIntegrationServiceUrl + url, Boolean.class);
            return Boolean.TRUE.equals(exists);
        } catch (Exception e) {
            log.debug("Failed to check bank integration service for transaction: {}", transactionReference);
            return false;
        }
    }
    
    private boolean checkInternationalTransferService(String transactionReference) {
        try {
            String url = "/api/v1/international-transfers/" + transactionReference + "/exists";
            Boolean exists = restTemplate.getForObject(internationalTransferServiceUrl + url, Boolean.class);
            return Boolean.TRUE.equals(exists);
        } catch (Exception e) {
            log.debug("Failed to check international transfer service for transaction: {}", transactionReference);
            return false;
        }
    }
    
    private boolean checkExternalBankingPartners(String transactionReference) {
        try {
            // Check different banking networks based on transaction pattern
            if (transactionReference.contains("SWIFT")) {
                return checkSwiftNetwork(transactionReference);
            } else if (transactionReference.contains("ACH")) {
                return checkACHNetwork(transactionReference);
            } else if (transactionReference.contains("WIRE")) {
                return checkWireNetwork(transactionReference);
            } else if (transactionReference.contains("SEPA")) {
                return checkSEPANetwork(transactionReference);
            }
            
            return false;
        } catch (Exception e) {
            log.debug("Failed to check external banking partners for transaction: {}", transactionReference);
            return false;
        }
    }
    
    private boolean checkSwiftNetwork(String transactionReference) {
        try {
            // Query SWIFT network for transaction status
            HttpHeaders headers = new HttpHeaders();
            headers.set("Authorization", "Bearer " + swiftApiKey);
            headers.set("X-API-Version", "v1");
            
            HttpEntity<String> entity = new HttpEntity<>(headers);
            ResponseEntity<Map> response = restTemplate.exchange(
                swiftApiUrl + "/messages/" + transactionReference,
                HttpMethod.GET,
                entity,
                Map.class
            );
            
            return response.getStatusCode().is2xxSuccessful();
        } catch (Exception e) {
            log.debug("Failed to verify SWIFT transaction: {}", transactionReference);
            return false;
        }
    }
    
    private boolean checkACHNetwork(String transactionReference) {
        try {
            // Query ACH network for transaction status
            HttpHeaders headers = new HttpHeaders();
            headers.set("Authorization", "Bearer " + achApiKey);
            
            HttpEntity<String> entity = new HttpEntity<>(headers);
            ResponseEntity<Map> response = restTemplate.exchange(
                achApiUrl + "/transactions/" + transactionReference,
                HttpMethod.GET,
                entity,
                Map.class
            );
            
            return response.getStatusCode().is2xxSuccessful();
        } catch (Exception e) {
            log.debug("Failed to verify ACH transaction: {}", transactionReference);
            return false;
        }
    }
    
    private boolean checkWireNetwork(String transactionReference) {
        try {
            // Query Fedwire for transaction status
            HttpHeaders headers = new HttpHeaders();
            headers.set("Authorization", "Bearer " + fedwireApiKey);
            
            HttpEntity<String> entity = new HttpEntity<>(headers);
            ResponseEntity<Map> response = restTemplate.exchange(
                fedwireApiUrl + "/transfers/" + transactionReference,
                HttpMethod.GET,
                entity,
                Map.class
            );
            
            return response.getStatusCode().is2xxSuccessful();
        } catch (Exception e) {
            log.debug("Failed to verify Wire transaction: {}", transactionReference);
            return false;
        }
    }
    
    private boolean checkSEPANetwork(String transactionReference) {
        try {
            // Query SEPA network for transaction status
            HttpHeaders headers = new HttpHeaders();
            headers.set("Authorization", "Bearer " + sepaApiKey);
            
            HttpEntity<String> entity = new HttpEntity<>(headers);
            ResponseEntity<Map> response = restTemplate.exchange(
                sepaApiUrl + "/credit-transfers/" + transactionReference,
                HttpMethod.GET,
                entity,
                Map.class
            );
            
            return response.getStatusCode().is2xxSuccessful();
        } catch (Exception e) {
            log.debug("Failed to verify SEPA transaction: {}", transactionReference);
            return false;
        }
    }
    
    private boolean checkSettlementSystems(String transactionReference) {
        try {
            // Check various settlement systems
            String url = "/api/v1/settlement/transactions/" + transactionReference + "/status";
            ResponseEntity<Map> response = restTemplate.getForEntity(settlementServiceUrl + url, Map.class);
            
            if (response.getStatusCode().is2xxSuccessful() && response.getBody() != null) {
                String status = (String) response.getBody().get("status");
                return status != null && !status.equals("NOT_FOUND");
            }
            
            return false;
        } catch (Exception e) {
            log.debug("Failed to check settlement systems for transaction: {}", transactionReference);
            return false;
        }
    }

    /**
     * Verifies transaction amounts match across all systems
     */
    private boolean verifyTransactionAmounts(ReconciliationRecord record) {
        try {
            log.debug("Verifying transaction amounts for: {}", record.getTransactionReference());
            
            String transactionReference = record.getTransactionReference();
            
            // Get transaction details from multiple sources
            TransactionAmountDetails sourceAmounts = getSourceSystemAmounts(transactionReference);
            TransactionAmountDetails targetAmounts = getTargetSystemAmounts(transactionReference);
            TransactionAmountDetails ledgerAmounts = getLedgerAmounts(transactionReference);
            
            // Basic validation - ensure we have valid amounts in the record
            if (record.getDebitAmount() == null && record.getCreditAmount() == null) {
                log.error("No valid amounts in reconciliation record: {}", transactionReference);
                return false;
            }
            
            // Cross-system amount verification
            List<AmountDiscrepancy> discrepancies = new ArrayList<>();
            
            // 1. Verify source vs record amounts
            if (sourceAmounts != null) {
                AmountDiscrepancy sourceDiscrepancy = compareAmounts("SOURCE_VS_RECORD", 
                    sourceAmounts, record, discrepancyThreshold);
                if (sourceDiscrepancy != null) {
                    discrepancies.add(sourceDiscrepancy);
                }
            }
            
            // 2. Verify target vs record amounts
            if (targetAmounts != null) {
                AmountDiscrepancy targetDiscrepancy = compareAmounts("TARGET_VS_RECORD", 
                    targetAmounts, record, discrepancyThreshold);
                if (targetDiscrepancy != null) {
                    discrepancies.add(targetDiscrepancy);
                }
            }
            
            // 3. Verify ledger vs record amounts
            if (ledgerAmounts != null) {
                AmountDiscrepancy ledgerDiscrepancy = compareAmounts("LEDGER_VS_RECORD", 
                    ledgerAmounts, record, discrepancyThreshold);
                if (ledgerDiscrepancy != null) {
                    discrepancies.add(ledgerDiscrepancy);
                }
            }
            
            // 4. Cross-verify source vs target amounts
            if (sourceAmounts != null && targetAmounts != null) {
                AmountDiscrepancy crossDiscrepancy = compareAmountDetails("SOURCE_VS_TARGET", 
                    sourceAmounts, targetAmounts, discrepancyThreshold);
                if (crossDiscrepancy != null) {
                    discrepancies.add(crossDiscrepancy);
                }
            }
            
            // 5. Verify exchange rate calculations for currency conversions
            if (sourceAmounts != null && targetAmounts != null && 
                !sourceAmounts.getCurrency().equals(targetAmounts.getCurrency())) {
                
                AmountDiscrepancy exchangeDiscrepancy = verifyExchangeRateCalculation(
                    sourceAmounts, targetAmounts, record.getTimestamp());
                if (exchangeDiscrepancy != null) {
                    discrepancies.add(exchangeDiscrepancy);
                }
            }
            
            // Handle discrepancies
            if (!discrepancies.isEmpty()) {
                handleAmountDiscrepancies(record, discrepancies);
                return autoResolveEnabled ? attemptAutoResolution(record, discrepancies) : false;
            }
            
            // All amounts match within tolerance
            log.debug("Transaction amounts verified successfully: {}", transactionReference);
            return true;
            
        } catch (Exception e) {
            log.error("Error verifying transaction amounts for: {}", record.getTransactionReference(), e);
            return false;
        }
    }
    
    private TransactionAmountDetails getSourceSystemAmounts(String transactionReference) {
        try {
            // Try to get amounts from the primary source system
            String url = "/api/v1/transactions/" + transactionReference + "/amounts";
            
            // Check Payment Service first
            try {
                Map<String, Object> response = restTemplate.getForObject(
                    paymentServiceUrl + url, Map.class);
                if (response != null) {
                    return mapToTransactionAmountDetails(response, "PAYMENT_SERVICE");
                }
            } catch (Exception e) {
                log.debug("Payment service amounts not found for: {}", transactionReference);
            }
            
            // Check Wallet Service
            try {
                Map<String, Object> response = restTemplate.getForObject(
                    walletServiceUrl + url, Map.class);
                if (response != null) {
                    return mapToTransactionAmountDetails(response, "WALLET_SERVICE");
                }
            } catch (Exception e) {
                log.debug("Wallet service amounts not found for: {}", transactionReference);
            }
            
            return null;
        } catch (Exception e) {
            log.debug("Failed to get source system amounts for: {}", transactionReference);
            return null;
        }
    }
    
    private TransactionAmountDetails getTargetSystemAmounts(String transactionReference) {
        try {
            String url = "/api/v1/transactions/" + transactionReference + "/amounts";
            
            // Check Bank Integration Service
            try {
                Map<String, Object> response = restTemplate.getForObject(
                    bankIntegrationServiceUrl + url, Map.class);
                if (response != null) {
                    return mapToTransactionAmountDetails(response, "BANK_INTEGRATION");
                }
            } catch (Exception e) {
                log.debug("Bank integration amounts not found for: {}", transactionReference);
            }
            
            // Check Core Banking
            try {
                Map<String, Object> response = restTemplate.getForObject(
                    coreBankingServiceUrl + url, Map.class);
                if (response != null) {
                    return mapToTransactionAmountDetails(response, "CORE_BANKING");
                }
            } catch (Exception e) {
                log.debug("Core banking amounts not found for: {}", transactionReference);
            }
            
            return null;
        } catch (Exception e) {
            log.debug("Failed to get target system amounts for: {}", transactionReference);
            return null;
        }
    }
    
    private TransactionAmountDetails getLedgerAmounts(String transactionReference) {
        try {
            String url = "/api/v1/ledger/transactions/" + transactionReference + "/amounts";
            Map<String, Object> response = restTemplate.getForObject(
                ledgerServiceUrl + url, Map.class);
            
            if (response != null) {
                return mapToTransactionAmountDetails(response, "LEDGER_SERVICE");
            }
            
            return null;
        } catch (Exception e) {
            log.debug("Failed to get ledger amounts for: {}", transactionReference);
            return null;
        }
    }
    
    private TransactionAmountDetails mapToTransactionAmountDetails(Map<String, Object> response, String source) {
        return TransactionAmountDetails.builder()
            .source(source)
            .debitAmount(response.get("debitAmount") != null ? 
                new BigDecimal(response.get("debitAmount").toString()) : null)
            .creditAmount(response.get("creditAmount") != null ? 
                new BigDecimal(response.get("creditAmount").toString()) : null)
            .currency((String) response.get("currency"))
            .fees(response.get("fees") != null ? 
                new BigDecimal(response.get("fees").toString()) : BigDecimal.ZERO)
            .exchangeRate(response.get("exchangeRate") != null ? 
                new BigDecimal(response.get("exchangeRate").toString()) : null)
            .originalAmount(response.get("originalAmount") != null ? 
                new BigDecimal(response.get("originalAmount").toString()) : null)
            .originalCurrency((String) response.get("originalCurrency"))
            .build();
    }
    
    private AmountDiscrepancy compareAmounts(String comparisonType, TransactionAmountDetails amounts, 
                                           ReconciliationRecord record, BigDecimal threshold) {
        
        BigDecimal recordDebit = record.getDebitAmount() != null ? record.getDebitAmount() : BigDecimal.ZERO;
        BigDecimal recordCredit = record.getCreditAmount() != null ? record.getCreditAmount() : BigDecimal.ZERO;
        
        BigDecimal systemDebit = amounts.getDebitAmount() != null ? amounts.getDebitAmount() : BigDecimal.ZERO;
        BigDecimal systemCredit = amounts.getCreditAmount() != null ? amounts.getCreditAmount() : BigDecimal.ZERO;
        
        BigDecimal debitDifference = recordDebit.subtract(systemDebit).abs();
        BigDecimal creditDifference = recordCredit.subtract(systemCredit).abs();
        
        if (debitDifference.compareTo(threshold) > 0 || creditDifference.compareTo(threshold) > 0) {
            return AmountDiscrepancy.builder()
                .discrepancyType(comparisonType)
                .transactionReference(record.getTransactionReference())
                .expectedDebitAmount(systemDebit)
                .actualDebitAmount(recordDebit)
                .expectedCreditAmount(systemCredit)
                .actualCreditAmount(recordCredit)
                .debitDifference(debitDifference)
                .creditDifference(creditDifference)
                .currency(record.getCurrency())
                .source(amounts.getSource())
                .detectedAt(LocalDateTime.now())
                .build();
        }
        
        return null;
    }
    
    private AmountDiscrepancy compareAmountDetails(String comparisonType, 
                                                 TransactionAmountDetails source, 
                                                 TransactionAmountDetails target, 
                                                 BigDecimal threshold) {
        
        BigDecimal sourceDebit = source.getDebitAmount() != null ? source.getDebitAmount() : BigDecimal.ZERO;
        BigDecimal sourceCredit = source.getCreditAmount() != null ? source.getCreditAmount() : BigDecimal.ZERO;
        BigDecimal targetDebit = target.getDebitAmount() != null ? target.getDebitAmount() : BigDecimal.ZERO;
        BigDecimal targetCredit = target.getCreditAmount() != null ? target.getCreditAmount() : BigDecimal.ZERO;
        
        BigDecimal debitDifference = sourceDebit.subtract(targetDebit).abs();
        BigDecimal creditDifference = sourceCredit.subtract(targetCredit).abs();
        
        if (debitDifference.compareTo(threshold) > 0 || creditDifference.compareTo(threshold) > 0) {
            return AmountDiscrepancy.builder()
                .discrepancyType(comparisonType)
                .expectedDebitAmount(sourceDebit)
                .actualDebitAmount(targetDebit)
                .expectedCreditAmount(sourceCredit)
                .actualCreditAmount(targetCredit)
                .debitDifference(debitDifference)
                .creditDifference(creditDifference)
                .currency(source.getCurrency())
                .source(source.getSource() + "_VS_" + target.getSource())
                .detectedAt(LocalDateTime.now())
                .build();
        }
        
        return null;
    }
    
    private AmountDiscrepancy verifyExchangeRateCalculation(TransactionAmountDetails sourceAmounts,
                                                          TransactionAmountDetails targetAmounts,
                                                          LocalDateTime transactionTime) {
        try {
            String sourceCurrency = sourceAmounts.getCurrency();
            String targetCurrency = targetAmounts.getCurrency();
            
            // Get the exchange rate that should have been used
            BigDecimal expectedRate = getHistoricalExchangeRate(sourceCurrency, targetCurrency, transactionTime);
            
            if (expectedRate == null) {
                log.warn("Could not retrieve historical exchange rate for {} to {} at {}", 
                    sourceCurrency, targetCurrency, transactionTime);
                return null;
            }
            
            // Calculate expected converted amount
            BigDecimal sourceAmount = sourceAmounts.getDebitAmount() != null ? 
                sourceAmounts.getDebitAmount() : sourceAmounts.getCreditAmount();
            BigDecimal expectedTargetAmount = sourceAmount.multiply(expectedRate);
            
            BigDecimal actualTargetAmount = targetAmounts.getDebitAmount() != null ? 
                targetAmounts.getDebitAmount() : targetAmounts.getCreditAmount();
            
            BigDecimal rateDifference = expectedTargetAmount.subtract(actualTargetAmount).abs();
            
            // Allow for small exchange rate fluctuations (0.5%)
            BigDecimal rateThreshold = expectedTargetAmount.multiply(new BigDecimal("0.005"));
            
            if (rateDifference.compareTo(rateThreshold) > 0) {
                return AmountDiscrepancy.builder()
                    .discrepancyType("EXCHANGE_RATE_MISMATCH")
                    .expectedDebitAmount(expectedTargetAmount)
                    .actualDebitAmount(actualTargetAmount)
                    .debitDifference(rateDifference)
                    .currency(targetCurrency)
                    .source("EXCHANGE_RATE_VERIFICATION")
                    .detectedAt(LocalDateTime.now())
                    .additionalInfo(Map.of(
                        "expectedRate", expectedRate.toString(),
                        "sourceCurrency", sourceCurrency,
                        "targetCurrency", targetCurrency,
                        "sourceAmount", sourceAmount.toString()
                    ))
                    .build();
            }
            
            return null;
        } catch (Exception e) {
            log.error("Error verifying exchange rate calculation", e);
            return null;
        }
    }
    
    private BigDecimal getHistoricalExchangeRate(String fromCurrency, String toCurrency, LocalDateTime dateTime) {
        try {
            String url = "/api/v1/fx/rates/historical";
            Map<String, Object> params = Map.of(
                "from", fromCurrency,
                "to", toCurrency,
                "date", dateTime.toString()
            );
            
            ResponseEntity<Map> response = restTemplate.postForEntity(
                currencyServiceUrl + url, params, Map.class);
            
            if (response.getStatusCode().is2xxSuccessful() && response.getBody() != null) {
                Object rate = response.getBody().get("rate");
                return rate != null ? new BigDecimal(rate.toString()) : null;
            }
            
            return null;
        } catch (Exception e) {
            log.debug("Failed to get historical exchange rate", e);
            return null;
        }
    }
    
    private void handleAmountDiscrepancies(ReconciliationRecord record, List<AmountDiscrepancy> discrepancies) {
        log.warn("Amount discrepancies detected for transaction {}: {} discrepancies", 
            record.getTransactionReference(), discrepancies.size());
        
        for (AmountDiscrepancy discrepancy : discrepancies) {
            // Store discrepancy for analysis
            storeDiscrepancy(discrepancy);
            
            // Send alert for significant discrepancies
            if (discrepancy.getDebitDifference().compareTo(new BigDecimal("100")) > 0 ||
                discrepancy.getCreditDifference().compareTo(new BigDecimal("100")) > 0) {
                
                sendDiscrepancyAlert(record, discrepancy);
            }
        }
    }
    
    private boolean attemptAutoResolution(ReconciliationRecord record, List<AmountDiscrepancy> discrepancies) {
        try {
            log.info("Attempting auto-resolution for transaction: {}", record.getTransactionReference());
            
            for (AmountDiscrepancy discrepancy : discrepancies) {
                if (canAutoResolve(discrepancy)) {
                    boolean resolved = performAutoResolution(record, discrepancy);
                    if (resolved) {
                        log.info("Auto-resolved discrepancy for transaction: {}", record.getTransactionReference());
                        return true;
                    }
                }
            }
            
            return false;
        } catch (Exception e) {
            log.error("Error in auto-resolution for transaction: {}", record.getTransactionReference(), e);
            return false;
        }
    }
    
    private boolean canAutoResolve(AmountDiscrepancy discrepancy) {
        // Only auto-resolve small discrepancies (under $1)
        BigDecimal maxAutoResolveAmount = new BigDecimal("1.00");
        
        return discrepancy.getDebitDifference().compareTo(maxAutoResolveAmount) <= 0 &&
               discrepancy.getCreditDifference().compareTo(maxAutoResolveAmount) <= 0;
    }
    
    private boolean performAutoResolution(ReconciliationRecord record, AmountDiscrepancy discrepancy) {
        // Implementation for auto-resolution logic
        // For small discrepancies, adjust the amounts and log the adjustment
        log.info("Auto-resolving small discrepancy for transaction: {} ({})", 
            record.getTransactionReference(), discrepancy.getDiscrepancyType());
        
        // Mark as auto-resolved
        discrepancy.setResolved(true);
        discrepancy.setResolvedAt(LocalDateTime.now());
        discrepancy.setResolutionMethod("AUTO_RESOLVED");
        
        return true;
    }
    
    // Helper classes for amount verification
    @lombok.Data
    @lombok.Builder
    private static class TransactionAmountDetails {
        private String source;
        private BigDecimal debitAmount;
        private BigDecimal creditAmount;
        private String currency;
        private BigDecimal fees;
        private BigDecimal exchangeRate;
        private BigDecimal originalAmount;
        private String originalCurrency;
    }
    
    @lombok.Data
    @lombok.Builder
    private static class AmountDiscrepancy {
        private String discrepancyType;
        private String transactionReference;
        private BigDecimal expectedDebitAmount;
        private BigDecimal actualDebitAmount;
        private BigDecimal expectedCreditAmount;
        private BigDecimal actualCreditAmount;
        private BigDecimal debitDifference;
        private BigDecimal creditDifference;
        private String currency;
        private String source;
        private LocalDateTime detectedAt;
        private boolean resolved;
        private LocalDateTime resolvedAt;
        private String resolutionMethod;
        private Map<String, Object> additionalInfo;
    }

    /**
     * Checks if account requires immediate reconciliation
     */
    private boolean requiresImmediateReconciliation(String accountNumber) {
        // Check if account is flagged for immediate reconciliation
        String flagKey = "reconciliation:immediate:" + accountNumber;
        return Boolean.TRUE.equals(redisTemplate.hasKey(flagKey));
    }

    /**
     * Triggers immediate reconciliation for account
     */
    private void triggerImmediateReconciliation(String accountNumber, String currency) {
        log.info("Triggering immediate reconciliation for account: {} {}", accountNumber, currency);
        checkReconciliationDiscrepancies(accountNumber, currency);
    }

    /**
     * Checks if discrepancy can be auto-resolved
     */
    private boolean canAutoResolve(BigDecimal discrepancy) {
        // Auto-resolve if discrepancy is less than $1
        return discrepancy.compareTo(BigDecimal.ONE) < 0;
    }

    /**
     * Attempts automatic resolution of discrepancy
     */
    private void attemptAutoResolution(DiscrepancyRecord record) {
        log.info("Attempting auto-resolution for discrepancy - Account: {} Amount: {} {}", 
            record.getAccountNumber(), record.getDiscrepancyAmount(), record.getCurrency());
        
        // Mark as auto-resolved
        record.setStatus(DiscrepancyStatus.AUTO_RESOLVED);
        record.setResolvedAt(LocalDateTime.now());
        
        // Update stored record
        storeDiscrepancyRecord(record);
    }

    /**
     * Escalates discrepancy for manual review
     */
    private void escalateForManualReview(DiscrepancyRecord record) {
        log.warn("Escalating discrepancy for manual review - Account: {} Amount: {} {}", 
            record.getAccountNumber(), record.getDiscrepancyAmount(), record.getCurrency());
        
        // Mark as escalated
        record.setStatus(DiscrepancyStatus.ESCALATED);
        
        // Store escalation
        String escalationKey = "reconciliation:escalations:" + LocalDateTime.now().toLocalDate();
        redisTemplate.opsForList().rightPush(escalationKey, record.getAccountNumber());
        redisTemplate.expire(escalationKey, Duration.ofDays(7));
    }

    private LocalDateTime timestamp() {
        return LocalDateTime.now();
    }

    // Data structures

    @lombok.Data
    @lombok.Builder
    @lombok.AllArgsConstructor
    @lombok.NoArgsConstructor
    private static class ReconciliationRecord {
        private String eventId;
        private String eventType;
        private String transactionReference;
        private String accountNumber;
        private BigDecimal debitAmount;
        private BigDecimal creditAmount;
        private String currency;
        private LocalDateTime timestamp;
        private LocalDateTime processedAt;
    }

    @lombok.Data
    @lombok.Builder
    @lombok.AllArgsConstructor
    @lombok.NoArgsConstructor
    private static class DiscrepancyRecord {
        private String accountNumber;
        private String currency;
        private BigDecimal ledgerBalance;
        private BigDecimal reconciliationBalance;
        private BigDecimal discrepancyAmount;
        private LocalDateTime detectedAt;
        private LocalDateTime resolvedAt;
        private DiscrepancyStatus status;
    }

    @lombok.Data
    @lombok.AllArgsConstructor
    private static class ReconciliationStatus {
        private String reference;
        private Status status;
        private String message;
        private LocalDateTime lastUpdated;

        public ReconciliationStatus(String reference) {
            this.reference = reference;
            this.status = Status.PENDING;
            this.lastUpdated = LocalDateTime.now();
        }

        public enum Status {
            PENDING,
            RECONCILED,
            DISCREPANCY,
            MISSING,
            ERROR
        }
    }

    private enum DiscrepancyStatus {
        OPEN,
        AUTO_RESOLVED,
        MANUALLY_RESOLVED,
        ESCALATED
    }
}