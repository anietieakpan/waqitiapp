package com.waqiti.wallet.kafka;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.waqiti.common.idempotency.IdempotencyService;
import com.waqiti.common.kafka.dlq.UniversalDLQHandler;
import com.waqiti.common.locking.DistributedLockService;
import com.waqiti.common.metrics.MetricsCollector;
import com.waqiti.wallet.domain.CompensationRecord;
import com.waqiti.wallet.domain.CompensationStatus;
import com.waqiti.wallet.domain.Wallet;
import com.waqiti.wallet.domain.TransactionType;
import com.waqiti.wallet.repository.CompensationRecordRepository;
import com.waqiti.wallet.repository.WalletRepository;
import com.waqiti.wallet.service.WalletTransactionService;
import com.waqiti.wallet.service.WalletAuditService;
import com.waqiti.wallet.service.WalletNotificationService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.kafka.support.Acknowledgment;

import java.math.BigDecimal;
import java.time.Duration;
import java.time.LocalDateTime;
import java.util.*;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/**
 * Comprehensive unit tests for WalletCompensationConsumer
 *
 * Tests cover:
 * - Happy path scenarios (CREATED, COMPLETED, FAILED actions)
 * - Idempotency (duplicate event handling)
 * - Distributed locking
 * - Error handling and fallbacks
 * - Audit trail creation
 * - Notifications
 * - Event publishing to accounting
 * - DLQ handling
 *
 * @author Waqiti Platform Team
 * @since 2025-10-19
 */
@ExtendWith(MockitoExtension.class)
@DisplayName("WalletCompensationConsumer Tests")
class WalletCompensationConsumerTest {

    @Mock
    private WalletRepository walletRepository;

    @Mock
    private CompensationRecordRepository compensationRecordRepository;

    @Mock
    private WalletTransactionService transactionService;

    @Mock
    private WalletAuditService auditService;

    @Mock
    private WalletNotificationService notificationService;

    @Mock
    private IdempotencyService idempotencyService;

    @Mock
    private DistributedLockService lockService;

    @Mock
    private MetricsCollector metricsCollector;

    @Mock
    private ObjectMapper objectMapper;

    @Mock
    private UniversalDLQHandler dlqHandler;

    @Mock
    private KafkaTemplate<String, Object> kafkaTemplate;

    @Mock
    private Acknowledgment acknowledgment;

    @InjectMocks
    private WalletCompensationConsumer consumer;

    private UUID walletId;
    private UUID compensationId;
    private UUID userId;
    private String lockId;
    private Map<String, Object> testEvent;
    private Wallet testWallet;
    private CompensationRecord testCompensation;

    @BeforeEach
    void setUp() {
        walletId = UUID.randomUUID();
        compensationId = UUID.randomUUID();
        userId = UUID.randomUUID();
        lockId = "lock-" + UUID.randomUUID();

        // Create test wallet
        testWallet = new Wallet();
        testWallet.setId(walletId);
        testWallet.setUserId(userId);
        testWallet.setBalance(new BigDecimal("100.00"));
        testWallet.setCurrency("USD");
        testWallet.setVersion(1L);

        // Create test compensation record
        testCompensation = CompensationRecord.builder()
            .id(compensationId)
            .paymentId("payment-123")
            .walletId(walletId.toString())
            .amount(new BigDecimal("25.00"))
            .compensationType("WALLET_CREDIT")
            .failureReason("Payment failed - network error")
            .status(CompensationStatus.PENDING)
            .createdAt(LocalDateTime.now())
            .build();

        // Create test event
        testEvent = new HashMap<>();
        testEvent.put("compensationId", compensationId.toString());
        testEvent.put("action", "CREATED");
        testEvent.put("walletId", walletId.toString());
        testEvent.put("amount", "25.00");
        testEvent.put("compensationType", "WALLET_CREDIT");
    }

    @Test
    @DisplayName("Should successfully process WALLET_CREDIT compensation")
    void shouldProcessWalletCreditCompensation() {
        // Arrange
        when(idempotencyService.tryAcquire(anyString(), any(Duration.class))).thenReturn(true);
        when(compensationRecordRepository.findById(compensationId)).thenReturn(Optional.of(testCompensation));
        when(lockService.acquireLock(anyString(), any(Duration.class))).thenReturn(lockId);
        when(walletRepository.findById(walletId)).thenReturn(Optional.of(testWallet));

        BigDecimal expectedNewBalance = new BigDecimal("125.00"); // 100 + 25

        // Act
        consumer.handleWalletCompensationEvent(testEvent, "key-1", 0, 100L, acknowledgment);

        // Assert
        verify(walletRepository).save(argThat(wallet ->
            wallet.getBalance().compareTo(expectedNewBalance) == 0));

        verify(transactionService).createTransaction(
            eq(walletId),
            eq(new BigDecimal("25.00")),
            eq(TransactionType.COMPENSATION_CREDIT),
            anyString(),
            eq("payment-123")
        );

        verify(auditService).logCompensationExecution(
            eq(walletId),
            eq(userId),
            eq(compensationId),
            eq("payment-123"),
            eq("WALLET_CREDIT"),
            eq(new BigDecimal("25.00")),
            eq(new BigDecimal("100.00")),
            eq(expectedNewBalance),
            anyString()
        );

        verify(notificationService).sendCompensationNotification(
            eq(userId),
            eq("WALLET_CREDIT"),
            eq(new BigDecimal("25.00")),
            eq("USD"),
            anyString(),
            eq("payment-123")
        );

        verify(kafkaTemplate).send(eq("accounting.compensation.events"), anyString(), any());
        verify(lockService).releaseLock(anyString(), eq(lockId));
        verify(acknowledgment).acknowledge();
    }

    @Test
    @DisplayName("Should successfully process WALLET_DEBIT compensation")
    void shouldProcessWalletDebitCompensation() {
        // Arrange
        testEvent.put("action", "CREATED");
        testCompensation.setCompensationType("WALLET_DEBIT");
        testEvent.put("compensationType", "WALLET_DEBIT");

        when(idempotencyService.tryAcquire(anyString(), any(Duration.class))).thenReturn(true);
        when(compensationRecordRepository.findById(compensationId)).thenReturn(Optional.of(testCompensation));
        when(lockService.acquireLock(anyString(), any(Duration.class))).thenReturn(lockId);
        when(walletRepository.findById(walletId)).thenReturn(Optional.of(testWallet));

        BigDecimal expectedNewBalance = new BigDecimal("75.00"); // 100 - 25

        // Act
        consumer.handleWalletCompensationEvent(testEvent, "key-1", 0, 100L, acknowledgment);

        // Assert
        verify(walletRepository).save(argThat(wallet ->
            wallet.getBalance().compareTo(expectedNewBalance) == 0));

        verify(transactionService).createTransaction(
            eq(walletId),
            eq(new BigDecimal("25.00").negate()),
            eq(TransactionType.COMPENSATION_DEBIT),
            anyString(),
            eq("payment-123")
        );

        verify(acknowledgment).acknowledge();
    }

    @Test
    @DisplayName("Should skip duplicate events (idempotency)")
    void shouldSkipDuplicateEvents() {
        // Arrange
        when(idempotencyService.tryAcquire(anyString(), any(Duration.class))).thenReturn(false);

        // Act
        consumer.handleWalletCompensationEvent(testEvent, "key-1", 0, 100L, acknowledgment);

        // Assert
        verify(idempotencyService).tryAcquire(anyString(), any(Duration.class));
        verify(compensationRecordRepository, never()).findById(any());
        verify(walletRepository, never()).save(any());
        verify(acknowledgment).acknowledge(); // Still acknowledge to prevent reprocessing
    }

    @Test
    @DisplayName("Should handle insufficient balance for WALLET_DEBIT")
    void shouldHandleInsufficientBalanceForDebit() {
        // Arrange
        testWallet.setBalance(new BigDecimal("10.00")); // Less than compensation amount
        testCompensation.setCompensationType("WALLET_DEBIT");
        testEvent.put("compensationType", "WALLET_DEBIT");

        when(idempotencyService.tryAcquire(anyString(), any(Duration.class))).thenReturn(true);
        when(compensationRecordRepository.findById(compensationId)).thenReturn(Optional.of(testCompensation));
        when(lockService.acquireLock(anyString(), any(Duration.class))).thenReturn(lockId);
        when(walletRepository.findById(walletId)).thenReturn(Optional.of(testWallet));

        // Act
        consumer.handleWalletCompensationEvent(testEvent, "key-1", 0, 100L, acknowledgment);

        // Assert
        verify(dlqHandler).sendToDLQ(eq("wallet-compensation-events"), eq(testEvent), any(), anyString());
        verify(walletRepository, never()).save(any());
        verify(acknowledgment).acknowledge();
    }

    @Test
    @DisplayName("Should acquire and release distributed lock")
    void shouldAcquireAndReleaseLock() {
        // Arrange
        when(idempotencyService.tryAcquire(anyString(), any(Duration.class))).thenReturn(true);
        when(compensationRecordRepository.findById(compensationId)).thenReturn(Optional.of(testCompensation));
        when(lockService.acquireLock(anyString(), any(Duration.class))).thenReturn(lockId);
        when(walletRepository.findById(walletId)).thenReturn(Optional.of(testWallet));

        // Act
        consumer.handleWalletCompensationEvent(testEvent, "key-1", 0, 100L, acknowledgment);

        // Assert
        ArgumentCaptor<String> lockKeyCaptor = ArgumentCaptor.forClass(String.class);
        verify(lockService).acquireLock(lockKeyCaptor.capture(), eq(Duration.ofMinutes(5)));
        assertThat(lockKeyCaptor.getValue()).startsWith("compensation:wallet:");

        verify(lockService).releaseLock(lockKeyCaptor.getValue(), lockId);
    }

    @Test
    @DisplayName("Should fail if lock cannot be acquired")
    void shouldFailIfLockCannotBeAcquired() {
        // Arrange
        when(idempotencyService.tryAcquire(anyString(), any(Duration.class))).thenReturn(true);
        when(compensationRecordRepository.findById(compensationId)).thenReturn(Optional.of(testCompensation));
        when(lockService.acquireLock(anyString(), any(Duration.class))).thenReturn(null); // Lock failed

        // Act
        consumer.handleWalletCompensationEvent(testEvent, "key-1", 0, 100L, acknowledgment);

        // Assert
        verify(dlqHandler).sendToDLQ(eq("wallet-compensation-events"), eq(testEvent), any(), anyString());
        verify(walletRepository, never()).save(any());
    }

    @Test
    @DisplayName("Should process COMPLETED action")
    void shouldProcessCompletedAction() {
        // Arrange
        testEvent.put("action", "COMPLETED");
        testCompensation.setStatus(CompensationStatus.IN_PROGRESS);

        when(idempotencyService.tryAcquire(anyString(), any(Duration.class))).thenReturn(true);
        when(compensationRecordRepository.findById(compensationId)).thenReturn(Optional.of(testCompensation));
        when(lockService.acquireLock(anyString(), any(Duration.class))).thenReturn(lockId);
        when(walletRepository.findById(walletId)).thenReturn(Optional.of(testWallet));

        // Act
        consumer.handleWalletCompensationEvent(testEvent, "key-1", 0, 100L, acknowledgment);

        // Assert
        verify(compensationRecordRepository).save(argThat(record ->
            record.getStatus() == CompensationStatus.COMPLETED &&
            record.getCompletedAt() != null
        ));

        verify(auditService).logCompensationCompleted(
            eq(walletId),
            eq(userId),
            eq(compensationId),
            eq("payment-123"),
            eq("WALLET_CREDIT"),
            eq(new BigDecimal("25.00"))
        );

        verify(kafkaTemplate).send(eq("accounting.compensation.events"), anyString(),
            argThat(event -> {
                @SuppressWarnings("unchecked")
                Map<String, Object> eventMap = (Map<String, Object>) event;
                return "COMPENSATION_COMPLETED".equals(eventMap.get("eventType"));
            })
        );
    }

    @Test
    @DisplayName("Should process FAILED action and alert ops team")
    void shouldProcessFailedAction() {
        // Arrange
        testEvent.put("action", "FAILED");
        testEvent.put("failureReason", "External system timeout");

        when(idempotencyService.tryAcquire(anyString(), any(Duration.class))).thenReturn(true);
        when(compensationRecordRepository.findById(compensationId)).thenReturn(Optional.of(testCompensation));
        when(lockService.acquireLock(anyString(), any(Duration.class))).thenReturn(lockId);
        when(walletRepository.findById(walletId)).thenReturn(Optional.of(testWallet));

        // Act
        consumer.handleWalletCompensationEvent(testEvent, "key-1", 0, 100L, acknowledgment);

        // Assert
        verify(compensationRecordRepository).save(argThat(record ->
            record.getStatus() == CompensationStatus.FAILED &&
            "External system timeout".equals(record.getFailureReason())
        ));

        // Verify alerts sent to PagerDuty and Slack
        verify(kafkaTemplate, times(2)).send(anyString(), anyString(), any());
    }

    @Test
    @DisplayName("Should send event to DLQ on critical error")
    void shouldSendToDLQOnCriticalError() {
        // Arrange
        when(idempotencyService.tryAcquire(anyString(), any(Duration.class))).thenReturn(true);
        when(compensationRecordRepository.findById(compensationId)).thenThrow(new RuntimeException("Database error"));

        // Act
        consumer.handleWalletCompensationEvent(testEvent, "key-1", 0, 100L, acknowledgment);

        // Assert
        verify(dlqHandler).sendToDLQ(
            eq("wallet-compensation-events"),
            eq(testEvent),
            any(RuntimeException.class),
            anyString()
        );
        verify(acknowledgment).acknowledge();
    }

    @Test
    @DisplayName("Should validate event data")
    void shouldValidateEventData() {
        // Arrange - Invalid event (missing compensationId)
        Map<String, Object> invalidEvent = new HashMap<>();
        invalidEvent.put("action", "CREATED");
        invalidEvent.put("walletId", walletId.toString());

        when(idempotencyService.tryAcquire(anyString(), any(Duration.class))).thenReturn(true);

        // Act
        consumer.handleWalletCompensationEvent(invalidEvent, "key-1", 0, 100L, acknowledgment);

        // Assert
        verify(dlqHandler).sendToDLQ(
            eq("wallet-compensation-events"),
            eq(invalidEvent),
            any(),
            anyString()
        );
        verify(acknowledgment).acknowledge();
    }

    @Test
    @DisplayName("Should record metrics for successful processing")
    void shouldRecordMetricsForSuccessfulProcessing() {
        // Arrange
        when(idempotencyService.tryAcquire(anyString(), any(Duration.class))).thenReturn(true);
        when(compensationRecordRepository.findById(compensationId)).thenReturn(Optional.of(testCompensation));
        when(lockService.acquireLock(anyString(), any(Duration.class))).thenReturn(lockId);
        when(walletRepository.findById(walletId)).thenReturn(Optional.of(testWallet));

        // Act
        consumer.handleWalletCompensationEvent(testEvent, "key-1", 0, 100L, acknowledgment);

        // Assert
        verify(metricsCollector).incrementCounter("wallet.compensation.event.received");
        verify(metricsCollector).incrementCounter("wallet.compensation.processed.success");
        verify(metricsCollector).recordHistogram(eq("wallet.compensation.processing.duration.ms"), anyLong());
        verify(metricsCollector).incrementCounter("wallet.compensation.created.processed");
    }

    @Test
    @DisplayName("Should create audit trail for compliance")
    void shouldCreateAuditTrailForCompliance() {
        // Arrange
        when(idempotencyService.tryAcquire(anyString(), any(Duration.class))).thenReturn(true);
        when(compensationRecordRepository.findById(compensationId)).thenReturn(Optional.of(testCompensation));
        when(lockService.acquireLock(anyString(), any(Duration.class))).thenReturn(lockId);
        when(walletRepository.findById(walletId)).thenReturn(Optional.of(testWallet));

        // Act
        consumer.handleWalletCompensationEvent(testEvent, "key-1", 0, 100L, acknowledgment);

        // Assert
        verify(auditService).logCompensationExecution(
            eq(walletId),
            eq(userId),
            eq(compensationId),
            eq("payment-123"),
            eq("WALLET_CREDIT"),
            eq(new BigDecimal("25.00")),
            any(BigDecimal.class),  // previousBalance
            any(BigDecimal.class),  // newBalance
            anyString()             // failureReason
        );
    }

    @Test
    @DisplayName("Should publish accounting event after compensation")
    void shouldPublishAccountingEvent() {
        // Arrange
        when(idempotencyService.tryAcquire(anyString(), any(Duration.class))).thenReturn(true);
        when(compensationRecordRepository.findById(compensationId)).thenReturn(Optional.of(testCompensation));
        when(lockService.acquireLock(anyString(), any(Duration.class))).thenReturn(lockId);
        when(walletRepository.findById(walletId)).thenReturn(Optional.of(testWallet));

        // Act
        consumer.handleWalletCompensationEvent(testEvent, "key-1", 0, 100L, acknowledgment);

        // Assert
        ArgumentCaptor<Map<String, Object>> eventCaptor = ArgumentCaptor.forClass(Map.class);
        verify(kafkaTemplate).send(eq("accounting.compensation.events"), anyString(), eventCaptor.capture());

        Map<String, Object> publishedEvent = eventCaptor.getValue();
        assertThat(publishedEvent).containsEntry("eventType", "COMPENSATION_EXECUTED");
        assertThat(publishedEvent).containsKey("compensationId");
        assertThat(publishedEvent).containsKey("walletId");
        assertThat(publishedEvent).containsKey("amount");
    }
}
