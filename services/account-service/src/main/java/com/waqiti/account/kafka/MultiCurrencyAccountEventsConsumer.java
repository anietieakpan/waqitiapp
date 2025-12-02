package com.waqiti.account.kafka;

import com.waqiti.common.events.MultiCurrencyAccountEvent;
import com.waqiti.account.domain.MultiCurrencyAccount;
import com.waqiti.account.domain.CurrencyBalance;
import com.waqiti.account.repository.MultiCurrencyAccountRepository;
import com.waqiti.account.repository.CurrencyBalanceRepository;
import com.waqiti.account.service.AccountManagementService;
import com.waqiti.account.service.BalanceService;
import com.waqiti.account.service.CurrencyService;
import com.waqiti.account.metrics.AccountMetricsService;
import com.waqiti.common.audit.AuditService;
import com.waqiti.common.notification.NotificationService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.kafka.annotation.RetryableTopic;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.kafka.retrytopic.TopicSuffixingStrategy;
import org.springframework.kafka.support.Acknowledgment;
import org.springframework.kafka.support.KafkaHeaders;
import org.springframework.messaging.handler.annotation.Header;
import org.springframework.messaging.handler.annotation.Payload;
import org.springframework.retry.annotation.Backoff;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Isolation;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.time.Instant;
import java.util.*;

@Component
@Slf4j
@RequiredArgsConstructor
public class MultiCurrencyAccountEventsConsumer {
    
    private final MultiCurrencyAccountRepository accountRepository;
    private final CurrencyBalanceRepository balanceRepository;
    private final AccountManagementService accountManagementService;
    private final BalanceService balanceService;
    private final CurrencyService currencyService;
    private final AccountMetricsService metricsService;
    private final AuditService auditService;
    private final NotificationService notificationService;
    private final KafkaTemplate<String, Object> kafkaTemplate;
    
    private static final int MAX_CURRENCIES_PER_ACCOUNT = 20;
    private static final BigDecimal MIN_BALANCE_TO_MAINTAIN = new BigDecimal("0.01");
    
    @KafkaListener(
        topics = {"multi-currency-account-events", "currency-wallet-events", "multi-currency-balance-events"},
        groupId = "account-multi-currency-service-group",
        containerFactory = "criticalFinancialKafkaListenerContainerFactory",
        concurrency = "5"
    )
    @RetryableTopic(
        attempts = "4",
        backoff = @Backoff(delay = 2000, multiplier = 2.0, maxDelay = 20000),
        dltStrategy = org.springframework.kafka.retrytopic.DltStrategy.FAIL_ON_ERROR,
        topicSuffixingStrategy = TopicSuffixingStrategy.SUFFIX_WITH_INDEX_VALUE
    )
    @Transactional(isolation = Isolation.SERIALIZABLE, timeout = 90)
    public void handleMultiCurrencyAccountEvent(
            @Payload MultiCurrencyAccountEvent event,
            @Header(KafkaHeaders.RECEIVED_PARTITION) int partition,
            @Header(KafkaHeaders.OFFSET) long offset,
            Acknowledgment acknowledgment) {
        
        String correlationId = String.format("mca-%s-p%d-o%d", 
            event.getAccountId(), partition, offset);
        
        log.info("Processing multi-currency account event: accountId={}, type={}, currency={}", 
            event.getAccountId(), event.getEventType(), event.getCurrency());
        
        try {
            switch (event.getEventType()) {
                case ACCOUNT_CREATED:
                    processAccountCreated(event, correlationId);
                    break;
                case CURRENCY_ADDED:
                    processCurrencyAdded(event, correlationId);
                    break;
                case CURRENCY_REMOVED:
                    processCurrencyRemoved(event, correlationId);
                    break;
                case BALANCE_UPDATED:
                    processBalanceUpdated(event, correlationId);
                    break;
                case PRIMARY_CURRENCY_CHANGED:
                    processPrimaryCurrencyChanged(event, correlationId);
                    break;
                case AUTO_CONVERSION_ENABLED:
                    processAutoConversionEnabled(event, correlationId);
                    break;
                case AUTO_CONVERSION_DISABLED:
                    processAutoConversionDisabled(event, correlationId);
                    break;
                case BALANCE_THRESHOLD_REACHED:
                    processBalanceThresholdReached(event, correlationId);
                    break;
                case CONSOLIDATED_BALANCE_CALCULATED:
                    processConsolidatedBalanceCalculated(event, correlationId);
                    break;
                case CURRENCY_SUSPENDED:
                    processCurrencySuspended(event, correlationId);
                    break;
                case CURRENCY_REACTIVATED:
                    processCurrencyReactivated(event, correlationId);
                    break;
                default:
                    log.warn("Unknown multi-currency account event type: {}", event.getEventType());
                    break;
            }
            
            auditService.logAccountEvent(
                "MULTI_CURRENCY_ACCOUNT_EVENT_PROCESSED",
                event.getAccountId(),
                Map.of(
                    "eventType", event.getEventType(),
                    "userId", event.getUserId(),
                    "currency", event.getCurrency() != null ? event.getCurrency() : "N/A",
                    "correlationId", correlationId,
                    "timestamp", Instant.now()
                )
            );
            
            acknowledgment.acknowledge();
            
        } catch (Exception e) {
            log.error("Failed to process multi-currency account event: {}", e.getMessage(), e);
            kafkaTemplate.send("multi-currency-account-events-dlq", Map.of(
                "originalEvent", event,
                "error", e.getMessage(),
                "correlationId", correlationId,
                "timestamp", Instant.now()
            ));
            acknowledgment.acknowledge();
        }
    }
    
    private void processAccountCreated(MultiCurrencyAccountEvent event, String correlationId) {
        log.info("Multi-currency account created: accountId={}, user={}, baseCurrency={}", 
            event.getAccountId(), event.getUserId(), event.getBaseCurrency());
        
        MultiCurrencyAccount account = MultiCurrencyAccount.builder()
            .id(event.getAccountId())
            .userId(event.getUserId())
            .baseCurrency(event.getBaseCurrency())
            .primaryCurrency(event.getBaseCurrency())
            .accountType(event.getAccountType())
            .createdAt(LocalDateTime.now())
            .status("ACTIVE")
            .autoConversionEnabled(false)
            .totalCurrencies(1)
            .correlationId(correlationId)
            .build();
        
        accountRepository.save(account);
        
        CurrencyBalance baseBalance = CurrencyBalance.builder()
            .id(UUID.randomUUID().toString())
            .accountId(event.getAccountId())
            .currency(event.getBaseCurrency())
            .balance(BigDecimal.ZERO)
            .availableBalance(BigDecimal.ZERO)
            .pendingBalance(BigDecimal.ZERO)
            .isPrimary(true)
            .isActive(true)
            .createdAt(LocalDateTime.now())
            .lastUpdated(LocalDateTime.now())
            .build();
        
        balanceRepository.save(baseBalance);
        
        notificationService.sendNotification(
            event.getUserId(),
            "Multi-Currency Account Created",
            String.format("Your multi-currency account has been created with base currency %s. " +
                "You can now add up to %d currencies.",
                event.getBaseCurrency(), MAX_CURRENCIES_PER_ACCOUNT),
            correlationId
        );
        
        metricsService.recordMultiCurrencyAccountCreated(event.getAccountType());
    }
    
    private void processCurrencyAdded(MultiCurrencyAccountEvent event, String correlationId) {
        log.info("Currency added to account: accountId={}, currency={}", 
            event.getAccountId(), event.getCurrency());
        
        MultiCurrencyAccount account = accountRepository.findById(event.getAccountId())
            .orElseThrow();
        
        if (account.getTotalCurrencies() >= MAX_CURRENCIES_PER_ACCOUNT) {
            log.error("Maximum currencies exceeded: accountId={}, current={}", 
                event.getAccountId(), account.getTotalCurrencies());
            return;
        }
        
        boolean currencySupported = currencyService.isCurrencySupported(event.getCurrency());
        if (!currencySupported) {
            log.error("Currency not supported: {}", event.getCurrency());
            return;
        }
        
        CurrencyBalance balance = CurrencyBalance.builder()
            .id(UUID.randomUUID().toString())
            .accountId(event.getAccountId())
            .currency(event.getCurrency())
            .balance(BigDecimal.ZERO)
            .availableBalance(BigDecimal.ZERO)
            .pendingBalance(BigDecimal.ZERO)
            .isPrimary(false)
            .isActive(true)
            .createdAt(LocalDateTime.now())
            .lastUpdated(LocalDateTime.now())
            .build();
        
        balanceRepository.save(balance);
        
        account.setTotalCurrencies(account.getTotalCurrencies() + 1);
        account.setLastModified(LocalDateTime.now());
        accountRepository.save(account);
        
        notificationService.sendNotification(
            event.getUserId(),
            "Currency Added",
            String.format("Currency %s has been added to your account. You can now hold and transact in this currency.",
                event.getCurrency()),
            correlationId
        );
        
        metricsService.recordCurrencyAdded(event.getCurrency());
    }
    
    private void processCurrencyRemoved(MultiCurrencyAccountEvent event, String correlationId) {
        log.info("Currency removed from account: accountId={}, currency={}", 
            event.getAccountId(), event.getCurrency());
        
        MultiCurrencyAccount account = accountRepository.findById(event.getAccountId())
            .orElseThrow();
        
        CurrencyBalance balance = balanceRepository
            .findByAccountIdAndCurrency(event.getAccountId(), event.getCurrency())
            .orElseThrow();
        
        if (balance.getBalance().compareTo(MIN_BALANCE_TO_MAINTAIN) > 0) {
            log.error("Cannot remove currency with positive balance: accountId={}, currency={}, balance={}", 
                event.getAccountId(), event.getCurrency(), balance.getBalance());
            return;
        }
        
        if (balance.getIsPrimary()) {
            log.error("Cannot remove primary currency: accountId={}, currency={}", 
                event.getAccountId(), event.getCurrency());
            return;
        }
        
        balance.setIsActive(false);
        balance.setRemovedAt(LocalDateTime.now());
        balanceRepository.save(balance);
        
        account.setTotalCurrencies(account.getTotalCurrencies() - 1);
        account.setLastModified(LocalDateTime.now());
        accountRepository.save(account);
        
        metricsService.recordCurrencyRemoved(event.getCurrency());
    }
    
    private void processBalanceUpdated(MultiCurrencyAccountEvent event, String correlationId) {
        log.debug("Balance updated: accountId={}, currency={}, newBalance={}", 
            event.getAccountId(), event.getCurrency(), event.getNewBalance());
        
        CurrencyBalance balance = balanceRepository
            .findByAccountIdAndCurrency(event.getAccountId(), event.getCurrency())
            .orElseThrow();
        
        BigDecimal previousBalance = balance.getBalance();
        balance.setBalance(event.getNewBalance());
        balance.setAvailableBalance(event.getAvailableBalance());
        balance.setPendingBalance(event.getPendingBalance());
        balance.setLastUpdated(LocalDateTime.now());
        balanceRepository.save(balance);
        
        if (event.getNewBalance().compareTo(event.getMinBalanceThreshold() != null ? 
            event.getMinBalanceThreshold() : BigDecimal.ZERO) < 0) {
            balanceService.triggerLowBalanceAlert(event.getAccountId(), event.getCurrency());
        }
        
        MultiCurrencyAccount account = accountRepository.findById(event.getAccountId())
            .orElseThrow();
        account.setLastBalanceUpdate(LocalDateTime.now());
        accountRepository.save(account);
        
        metricsService.recordBalanceUpdate(event.getCurrency(), previousBalance, event.getNewBalance());
    }
    
    private void processPrimaryCurrencyChanged(MultiCurrencyAccountEvent event, String correlationId) {
        log.info("Primary currency changed: accountId={}, oldCurrency={}, newCurrency={}", 
            event.getAccountId(), event.getOldPrimaryCurrency(), event.getNewPrimaryCurrency());
        
        MultiCurrencyAccount account = accountRepository.findById(event.getAccountId())
            .orElseThrow();
        
        CurrencyBalance oldPrimary = balanceRepository
            .findByAccountIdAndCurrency(event.getAccountId(), event.getOldPrimaryCurrency())
            .orElseThrow();
        oldPrimary.setIsPrimary(false);
        balanceRepository.save(oldPrimary);
        
        CurrencyBalance newPrimary = balanceRepository
            .findByAccountIdAndCurrency(event.getAccountId(), event.getNewPrimaryCurrency())
            .orElseThrow();
        newPrimary.setIsPrimary(true);
        balanceRepository.save(newPrimary);
        
        account.setPrimaryCurrency(event.getNewPrimaryCurrency());
        account.setLastModified(LocalDateTime.now());
        accountRepository.save(account);
        
        notificationService.sendNotification(
            event.getUserId(),
            "Primary Currency Changed",
            String.format("Your primary currency has been changed from %s to %s.",
                event.getOldPrimaryCurrency(), event.getNewPrimaryCurrency()),
            correlationId
        );
        
        metricsService.recordPrimaryCurrencyChanged(event.getOldPrimaryCurrency(), event.getNewPrimaryCurrency());
    }
    
    private void processAutoConversionEnabled(MultiCurrencyAccountEvent event, String correlationId) {
        log.info("Auto-conversion enabled: accountId={}, targetCurrency={}, threshold={}", 
            event.getAccountId(), event.getAutoConversionTargetCurrency(), event.getAutoConversionThreshold());
        
        MultiCurrencyAccount account = accountRepository.findById(event.getAccountId())
            .orElseThrow();
        
        account.setAutoConversionEnabled(true);
        account.setAutoConversionTargetCurrency(event.getAutoConversionTargetCurrency());
        account.setAutoConversionThreshold(event.getAutoConversionThreshold());
        account.setLastModified(LocalDateTime.now());
        accountRepository.save(account);
        
        notificationService.sendNotification(
            event.getUserId(),
            "Auto-Conversion Enabled",
            String.format("Auto-conversion to %s is now enabled when balances exceed %s.",
                event.getAutoConversionTargetCurrency(), event.getAutoConversionThreshold()),
            correlationId
        );
        
        metricsService.recordAutoConversionEnabled();
    }
    
    private void processAutoConversionDisabled(MultiCurrencyAccountEvent event, String correlationId) {
        log.info("Auto-conversion disabled: accountId={}", event.getAccountId());
        
        MultiCurrencyAccount account = accountRepository.findById(event.getAccountId())
            .orElseThrow();
        
        account.setAutoConversionEnabled(false);
        account.setAutoConversionTargetCurrency(null);
        account.setAutoConversionThreshold(null);
        account.setLastModified(LocalDateTime.now());
        accountRepository.save(account);
        
        metricsService.recordAutoConversionDisabled();
    }
    
    private void processBalanceThresholdReached(MultiCurrencyAccountEvent event, String correlationId) {
        log.info("Balance threshold reached: accountId={}, currency={}, balance={}, threshold={}", 
            event.getAccountId(), event.getCurrency(), event.getCurrentBalance(), event.getThreshold());
        
        notificationService.sendNotification(
            event.getUserId(),
            "Balance Threshold Alert",
            String.format("Your %s balance (%s) has reached the threshold of %s.",
                event.getCurrency(), event.getCurrentBalance(), event.getThreshold()),
            correlationId
        );
        
        MultiCurrencyAccount account = accountRepository.findById(event.getAccountId())
            .orElseThrow();
        
        if (account.getAutoConversionEnabled() && 
            event.getCurrency().equals(account.getAutoConversionTargetCurrency())) {
            accountManagementService.triggerAutoConversion(event.getAccountId(), event.getCurrency());
        }
        
        metricsService.recordBalanceThresholdReached(event.getCurrency());
    }
    
    private void processConsolidatedBalanceCalculated(MultiCurrencyAccountEvent event, String correlationId) {
        log.info("Consolidated balance calculated: accountId={}, baseCurrency={}, totalValue={}", 
            event.getAccountId(), event.getBaseCurrency(), event.getConsolidatedBalance());
        
        MultiCurrencyAccount account = accountRepository.findById(event.getAccountId())
            .orElseThrow();
        
        account.setConsolidatedBalance(event.getConsolidatedBalance());
        account.setConsolidatedBalanceCurrency(event.getBaseCurrency());
        account.setLastConsolidationDate(LocalDateTime.now());
        accountRepository.save(account);
        
        metricsService.recordConsolidatedBalanceCalculated(event.getConsolidatedBalance());
    }
    
    private void processCurrencySuspended(MultiCurrencyAccountEvent event, String correlationId) {
        log.warn("Currency suspended: accountId={}, currency={}, reason={}", 
            event.getAccountId(), event.getCurrency(), event.getSuspensionReason());
        
        CurrencyBalance balance = balanceRepository
            .findByAccountIdAndCurrency(event.getAccountId(), event.getCurrency())
            .orElseThrow();
        
        balance.setIsActive(false);
        balance.setSuspended(true);
        balance.setSuspendedAt(LocalDateTime.now());
        balance.setSuspensionReason(event.getSuspensionReason());
        balanceRepository.save(balance);
        
        notificationService.sendNotification(
            event.getUserId(),
            "Currency Suspended",
            String.format("Your %s currency has been suspended. Reason: %s. Contact support for assistance.",
                event.getCurrency(), event.getSuspensionReason()),
            correlationId
        );
        
        metricsService.recordCurrencySuspended(event.getCurrency(), event.getSuspensionReason());
    }
    
    private void processCurrencyReactivated(MultiCurrencyAccountEvent event, String correlationId) {
        log.info("Currency reactivated: accountId={}, currency={}", 
            event.getAccountId(), event.getCurrency());
        
        CurrencyBalance balance = balanceRepository
            .findByAccountIdAndCurrency(event.getAccountId(), event.getCurrency())
            .orElseThrow();
        
        balance.setIsActive(true);
        balance.setSuspended(false);
        balance.setReactivatedAt(LocalDateTime.now());
        balanceRepository.save(balance);
        
        notificationService.sendNotification(
            event.getUserId(),
            "Currency Reactivated",
            String.format("Your %s currency has been reactivated. You can now transact in this currency.",
                event.getCurrency()),
            correlationId
        );
        
        metricsService.recordCurrencyReactivated(event.getCurrency());
    }
}