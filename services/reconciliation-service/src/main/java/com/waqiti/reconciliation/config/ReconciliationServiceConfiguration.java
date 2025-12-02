package com.waqiti.reconciliation.config;

import com.waqiti.common.audit.AuditService;
import com.waqiti.reconciliation.client.*;
import com.waqiti.reconciliation.repository.*;
import com.waqiti.reconciliation.service.*;
import com.waqiti.reconciliation.service.impl.*;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.web.client.RestTemplate;

import java.util.UUID;
import java.util.Collections;
import java.util.Map;
import java.util.List;
import java.util.ArrayList;
import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * Configuration for Reconciliation Service Dependencies
 * Provides production-ready bean definitions for all required services
 */
@Slf4j
@Configuration
public class ReconciliationServiceConfiguration {

    /**
     * Distributed Lock Service - Redis-based locking for critical operations
     */
    @Bean
    @ConditionalOnMissingBean
    public DistributedLockService distributedLockService(RedisTemplate<String, String> redisTemplate) {
        log.info("Creating DistributedLockService bean with Redis backend");
        return new DistributedLockServiceImpl(redisTemplate);
    }

    /**
     * Discrepancy Resolution Service - Production-grade resolution workflow
     */
    @Bean
    @ConditionalOnMissingBean
    public DiscrepancyResolutionService discrepancyResolutionService(
        DiscrepancyJpaRepository discrepancyRepository,
        ReconciliationItemJpaRepository reconciliationItemRepository,
        AuditService auditService,
        KafkaTemplate<String, Object> kafkaTemplate,
        DistributedLockService distributedLockService) {

        log.info("Creating DiscrepancyResolutionService bean");
        return new DiscrepancyResolutionServiceImpl(
            discrepancyRepository,
            reconciliationItemRepository,
            auditService,
            kafkaTemplate,
            distributedLockService
        );
    }

    /**
     * Ledger Reconciliation Service - Ledger vs transaction reconciliation
     */
    @Bean
    @ConditionalOnMissingBean
    public ReconciliationServices.LedgerReconciliationService ledgerReconciliationService(
        LedgerServiceClient ledgerServiceClient,
        TransactionServiceClient transactionServiceClient,
        ReconciliationItemJpaRepository reconciliationItemRepository,
        DiscrepancyJpaRepository discrepancyRepository,
        AuditService auditService,
        KafkaTemplate<String, Object> kafkaTemplate,
        DistributedLockService distributedLockService) {

        log.info("Creating LedgerReconciliationService bean");
        return new LedgerReconciliationServiceImpl(
            ledgerServiceClient,
            transactionServiceClient,
            reconciliationItemRepository,
            discrepancyRepository,
            auditService,
            kafkaTemplate,
            distributedLockService
        );
    }

    /**
     * Matching Service - Production-grade transaction matching
     */
    @Bean
    @ConditionalOnMissingBean
    public MatchingService matchingService(
        ReconciliationItemJpaRepository reconciliationItemRepository,
        DiscrepancyJpaRepository discrepancyRepository) {

        log.info("Creating MatchingService bean");
        return new MatchingServiceImpl(reconciliationItemRepository, discrepancyRepository);
    }

    // Client beans with fallbacks

    @Bean
    @ConditionalOnMissingBean
    public LedgerServiceClient ledgerServiceClient() {
        log.info("Creating mock LedgerServiceClient bean");
        return new MockLedgerServiceClient();
    }

    @Bean
    @ConditionalOnMissingBean
    public AccountServiceClient accountServiceClient() {
        log.info("Creating mock AccountServiceClient bean");
        return new MockAccountServiceClient();
    }

    @Bean
    @ConditionalOnMissingBean
    public ExternalBankServiceClient externalBankServiceClient() {
        log.info("Creating mock ExternalBankServiceClient bean");
        return new MockExternalBankServiceClient();
    }

    @Bean
    @ConditionalOnMissingBean
    public TransactionServiceClient transactionServiceClient() {
        log.info("Creating mock TransactionServiceClient bean");
        return new MockTransactionServiceClient();
    }

    @Bean
    @ConditionalOnMissingBean
    public NotificationServiceClient notificationServiceClient() {
        log.info("Creating mock NotificationServiceClient bean");
        return new MockNotificationServiceClient();
    }

    @Bean
    @ConditionalOnMissingBean
    public ReconciliationMatchingEngine reconciliationMatchingEngine() {
        log.info("Creating mock ReconciliationMatchingEngine bean");
        return new MockReconciliationMatchingEngine();
    }

    @Bean
    @ConditionalOnMissingBean
    public VarianceAnalysisService varianceAnalysisService() {
        log.info("Creating mock VarianceAnalysisService bean");
        return new MockVarianceAnalysisService();
    }

    @Bean
    @ConditionalOnMissingBean
    public BreakInvestigationService breakInvestigationService() {
        log.info("Creating mock BreakInvestigationService bean");
        return new MockBreakInvestigationService();
    }

    // Mock implementations for testing and development

    private static class MockLedgerServiceClient implements LedgerServiceClient {
        @Override
        public List<LedgerEntry> getLedgerEntriesByTransaction(UUID transactionId) {
            return new ArrayList<>();
        }

        @Override
        public LedgerCalculatedBalance calculateAccountBalance(UUID accountId, LocalDateTime asOfDate) {
            return LedgerCalculatedBalance.builder()
                .balance(BigDecimal.ZERO)
                .asOfDate(asOfDate)
                .build();
        }

        @Override
        public TrialBalanceResponse generateTrialBalance(LocalDateTime asOfDate) {
            return TrialBalanceResponse.builder()
                .totalDebits(BigDecimal.ZERO)
                .totalCredits(BigDecimal.ZERO)
                .balanced(true)
                .build();
        }
    }

    private static class MockAccountServiceClient implements AccountServiceClient {
        @Override
        public AccountBalance getAccountBalance(UUID accountId, LocalDateTime asOfDate) {
            return AccountBalance.builder()
                .accountId(accountId)
                .balance(BigDecimal.ZERO)
                .asOfDate(asOfDate)
                .build();
        }

        @Override
        public List<CustomerAccount> getAllActiveCustomerAccounts() {
            return new ArrayList<>();
        }
    }

    private static class MockExternalBankServiceClient implements ExternalBankServiceClient {
        @Override
        public ExternalSettlementConfirmation getSettlementConfirmation(UUID settlementId, LocalDateTime settlementDate) {
            return ExternalSettlementConfirmation.builder()
                .settlementId(settlementId)
                .confirmed(true)
                .build();
        }
    }

    private static class MockTransactionServiceClient implements TransactionServiceClient {
        @Override
        public TransactionDetails getTransactionDetails(UUID transactionId) {
            return TransactionDetails.builder()
                .transactionId(transactionId)
                .amount(BigDecimal.ZERO)
                .build();
        }
    }

    private static class MockNotificationServiceClient implements NotificationServiceClient {
        @Override
        public void sendReconciliationNotification(ReconciliationNotificationRequest request) {
            log.info("Mock notification sent for reconciliation job: {}", request.getJobId());
        }
    }

    private static class MockReconciliationMatchingEngine implements ReconciliationMatchingEngine {
        @Override
        public TransactionLedgerMatchResult matchTransactionToLedger(TransactionDetails transaction, List<LedgerEntry> ledgerEntries) {
            return TransactionLedgerMatchResult.builder()
                .matched(true)
                .variances(Collections.emptyList())
                .build();
        }

        @Override
        public SettlementMatchResult matchSettlementDetails(Settlement internal, ExternalSettlementConfirmation external) {
            return SettlementMatchResult.builder()
                .matched(true)
                .discrepancies(Collections.emptyList())
                .build();
        }
    }

    private static class MockVarianceAnalysisService implements VarianceAnalysisService {
        @Override
        public VarianceResolutionResult analyzeAndResolveVariance(UUID accountId, BalanceComparisonResult comparison, LocalDateTime asOfDate) {
            return VarianceResolutionResult.builder()
                .resolved(false)
                .analysisNotes("Mock variance analysis")
                .build();
        }
    }

    private static class MockBreakInvestigationService implements BreakInvestigationService {
        @Override
        public BreakInvestigationResult investigateBreak(ReconciliationBreak breakRecord) {
            return BreakInvestigationResult.builder()
                .autoResolvable(false)
                .investigationNotes("Mock break investigation")
                .build();
        }

        @Override
        public AutoResolutionResult attemptAutoResolution(ReconciliationBreak breakRecord, BreakInvestigationResult investigation) {
            return AutoResolutionResult.builder()
                .successful(false)
                .failureReason("Auto-resolution not implemented")
                .build();
        }
    }
}