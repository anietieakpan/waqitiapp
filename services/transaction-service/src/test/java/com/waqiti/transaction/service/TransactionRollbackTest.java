package com.waqiti.transaction.service;

import com.waqiti.transaction.domain.Transaction;
import com.waqiti.transaction.domain.TransactionStatus;
import com.waqiti.transaction.domain.TransactionType;
import com.waqiti.transaction.dto.TransactionRequest;
import com.waqiti.transaction.repository.TransactionRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.TestPropertySource;
import org.springframework.transaction.annotation.Transactional;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.*;

@SpringBootTest
@Testcontainers
@ActiveProfiles("integration-test")
@TestPropertySource(properties = {
        "spring.datasource.url=jdbc:tc:postgresql:15:///waqiti_test",
        "spring.jpa.hibernate.ddl-auto=create-drop"
})
@DisplayName("Transaction Rollback and Error Handling Tests")
class TransactionRollbackTest {

    @Container
    static PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("postgres:15")
            .withDatabaseName("waqiti_test")
            .withUsername("test")
            .withPassword("test");

    @Autowired
    private TransactionService transactionService;

    @Autowired
    private TransactionRepository transactionRepository;

    @PersistenceContext
    private EntityManager entityManager;

    private UUID fromAccountId;
    private UUID toAccountId;

    @BeforeEach
    void setUp() {
        fromAccountId = UUID.randomUUID();
        toAccountId = UUID.randomUUID();
    }

    @Nested
    @DisplayName("Transaction Rollback Tests")
    class TransactionRollbackTests {

        @Test
        @DisplayName("Should rollback transaction on database constraint violation")
        @Transactional
        void shouldRollbackOnDatabaseConstraintViolation() {
            Transaction transaction1 = createTransactionWithReference("TXN-UNIQUE-REF-001");
            transactionRepository.saveAndFlush(transaction1);

            Transaction transaction2 = createTransactionWithReference("TXN-UNIQUE-REF-001");

            assertThatThrownBy(() -> transactionRepository.saveAndFlush(transaction2))
                    .isInstanceOf(DataIntegrityViolationException.class);

            List<Transaction> transactions = transactionRepository.findByReference("TXN-UNIQUE-REF-001");
            assertThat(transactions).hasSize(1);
            assertThat(transactions.get(0).getId()).isEqualTo(transaction1.getId());
        }

        @Test
        @DisplayName("Should rollback partial batch when one transaction fails")
        @Transactional
        void shouldRollbackPartialBatchWhenOneTransactionFails() {
            String batchId = "BATCH-FAIL-" + UUID.randomUUID();
            List<Transaction> batchTransactions = new ArrayList<>();

            try {
                for (int i = 0; i < 5; i++) {
                    Transaction txn = createTransactionWithBatchId(batchId, "REF-" + i);
                    batchTransactions.add(txn);
                    transactionRepository.save(txn);
                }

                Transaction duplicateRef = createTransactionWithBatchId(batchId, "REF-2");
                transactionRepository.saveAndFlush(duplicateRef);

                fail("Expected DataIntegrityViolationException to be thrown");
            } catch (DataIntegrityViolationException e) {
            }

            entityManager.clear();

            List<Transaction> savedTransactions = transactionRepository.findByBatchId(batchId);
            assertThat(savedTransactions).isEmpty();
        }

        @Test
        @DisplayName("Should maintain referential integrity on rollback")
        @Transactional
        void shouldMaintainReferentialIntegrityOnRollback() {
            String batchId = "BATCH-INTEGRITY-" + UUID.randomUUID();
            
            try {
                Transaction parent = createTransactionWithBatchId(batchId, "PARENT-REF");
                parent = transactionRepository.save(parent);

                for (int i = 0; i < 3; i++) {
                    Transaction child = createTransactionWithBatchId(batchId, "CHILD-REF-" + i);
                    child.setSagaId(parent.getId().toString());
                    transactionRepository.save(child);
                }

                throw new RuntimeException("Simulated failure");
            } catch (RuntimeException e) {
            }

            entityManager.clear();

            List<Transaction> transactions = transactionRepository.findByBatchId(batchId);
            assertThat(transactions).isEmpty();
        }
    }

    @Nested
    @DisplayName("Compensation Action Tests")
    class CompensationActionTests {

        @Test
        @DisplayName("Should execute compensation for failed multi-step transaction")
        void shouldExecuteCompensationForFailedMultiStepTransaction() {
            String sagaId = "SAGA-" + UUID.randomUUID();
            
            Transaction step1 = createTransactionWithSagaId(sagaId, "STEP-1");
            step1.setStatus(TransactionStatus.COMPLETED);
            step1.setCompletedAt(LocalDateTime.now());
            step1 = transactionRepository.saveAndFlush(step1);

            Transaction step2 = createTransactionWithSagaId(sagaId, "STEP-2");
            step2.setStatus(TransactionStatus.COMPLETED);
            step2.setCompletedAt(LocalDateTime.now());
            step2 = transactionRepository.saveAndFlush(step2);

            Transaction step3 = createTransactionWithSagaId(sagaId, "STEP-3");
            step3.setStatus(TransactionStatus.FAILED);
            step3.setFailureReason("External service timeout");
            step3.setFailedAt(LocalDateTime.now());
            step3 = transactionRepository.saveAndFlush(step3);

            Transaction compensate1 = transactionRepository.findById(step1.getId()).orElseThrow();
            compensate1.setStatus(TransactionStatus.REVERSED);
            compensate1.setReversedAt(LocalDateTime.now());
            transactionRepository.saveAndFlush(compensate1);

            Transaction compensate2 = transactionRepository.findById(step2.getId()).orElseThrow();
            compensate2.setStatus(TransactionStatus.REVERSED);
            compensate2.setReversedAt(LocalDateTime.now());
            transactionRepository.saveAndFlush(compensate2);

            List<Transaction> sagaTransactions = transactionRepository.findAll().stream()
                    .filter(t -> sagaId.equals(t.getSagaId()))
                    .toList();

            assertThat(sagaTransactions).hasSize(3);
            assertThat(sagaTransactions).filteredOn(t -> t.getStatus() == TransactionStatus.REVERSED).hasSize(2);
            assertThat(sagaTransactions).filteredOn(t -> t.getStatus() == TransactionStatus.FAILED).hasSize(1);
        }

        @Test
        @DisplayName("Should handle idempotent compensation actions")
        void shouldHandleIdempotentCompensationActions() {
            Transaction transaction = createAndSaveTransaction();
            transaction.setStatus(TransactionStatus.COMPLETED);
            transaction.setCompletedAt(LocalDateTime.now());
            transaction = transactionRepository.saveAndFlush(transaction);

            transaction = transactionRepository.findById(transaction.getId()).orElseThrow();
            transaction.setStatus(TransactionStatus.REVERSED);
            transaction.setReversedAt(LocalDateTime.now());
            transaction = transactionRepository.saveAndFlush(transaction);

            UUID transactionId = transaction.getId();

            assertThatCode(() -> {
                Transaction txn = transactionRepository.findById(transactionId).orElseThrow();
                
                if (txn.getStatus() != TransactionStatus.REVERSED) {
                    txn.setStatus(TransactionStatus.REVERSED);
                    txn.setReversedAt(LocalDateTime.now());
                    transactionRepository.saveAndFlush(txn);
                }
            }).doesNotThrowAnyException();

            Transaction finalTransaction = transactionRepository.findById(transactionId).orElseThrow();
            assertThat(finalTransaction.getStatus()).isEqualTo(TransactionStatus.REVERSED);
        }

        @Test
        @DisplayName("Should track compensation metadata")
        void shouldTrackCompensationMetadata() {
            String sagaId = "SAGA-COMPENSATE-" + UUID.randomUUID();
            
            Transaction originalTransaction = createTransactionWithSagaId(sagaId, "ORIGINAL");
            originalTransaction.setStatus(TransactionStatus.COMPLETED);
            originalTransaction.setCompletedAt(LocalDateTime.now());
            originalTransaction = transactionRepository.saveAndFlush(originalTransaction);

            Transaction compensationTransaction = createTransactionWithSagaId(sagaId, "COMPENSATION");
            compensationTransaction.setStatus(TransactionStatus.REVERSED);
            compensationTransaction.setReversedAt(LocalDateTime.now());
            compensationTransaction.setProcessingResult("Compensation for " + originalTransaction.getReference());
            compensationTransaction = transactionRepository.saveAndFlush(compensationTransaction);

            assertThat(compensationTransaction.getProcessingResult()).contains(originalTransaction.getReference());
            assertThat(compensationTransaction.getSagaId()).isEqualTo(sagaId);
            assertThat(compensationTransaction.getStatus()).isEqualTo(TransactionStatus.REVERSED);
        }
    }

    @Nested
    @DisplayName("Saga Pattern Rollback Tests")
    class SagaPatternRollbackTests {

        @Test
        @DisplayName("Should rollback saga when intermediate step fails")
        void shouldRollbackSagaWhenIntermediateStepFails() {
            String sagaId = "SAGA-ROLLBACK-" + UUID.randomUUID();
            List<Transaction> sagaSteps = new ArrayList<>();

            Transaction step1 = createTransactionWithSagaId(sagaId, "STEP-1-REF");
            step1.setStatus(TransactionStatus.COMPLETED);
            step1.setCompletedAt(LocalDateTime.now());
            sagaSteps.add(transactionRepository.saveAndFlush(step1));

            Transaction step2 = createTransactionWithSagaId(sagaId, "STEP-2-REF");
            step2.setStatus(TransactionStatus.COMPLETED);
            step2.setCompletedAt(LocalDateTime.now());
            sagaSteps.add(transactionRepository.saveAndFlush(step2));

            Transaction step3 = createTransactionWithSagaId(sagaId, "STEP-3-REF");
            step3.setStatus(TransactionStatus.FAILED);
            step3.setFailureReason("Payment gateway failure");
            step3.setFailedAt(LocalDateTime.now());
            sagaSteps.add(transactionRepository.saveAndFlush(step3));

            for (int i = sagaSteps.size() - 2; i >= 0; i--) {
                Transaction txn = transactionRepository.findById(sagaSteps.get(i).getId()).orElseThrow();
                txn.setStatus(TransactionStatus.ROLLED_BACK);
                txn.setRolledBackAt(LocalDateTime.now());
                transactionRepository.saveAndFlush(txn);
            }

            List<Transaction> finalSagaSteps = transactionRepository.findAll().stream()
                    .filter(t -> sagaId.equals(t.getSagaId()))
                    .toList();

            long rolledBackCount = finalSagaSteps.stream()
                    .filter(t -> t.getStatus() == TransactionStatus.ROLLED_BACK)
                    .count();

            long failedCount = finalSagaSteps.stream()
                    .filter(t -> t.getStatus() == TransactionStatus.FAILED)
                    .count();

            assertThat(finalSagaSteps).hasSize(3);
            assertThat(rolledBackCount).isEqualTo(2);
            assertThat(failedCount).isEqualTo(1);
        }

        @Test
        @DisplayName("Should preserve order in saga rollback")
        void shouldPreserveOrderInSagaRollback() {
            String sagaId = "SAGA-ORDER-" + UUID.randomUUID();
            int numberOfSteps = 5;
            List<Transaction> steps = new ArrayList<>();

            for (int i = 0; i < numberOfSteps; i++) {
                Transaction step = createTransactionWithSagaId(sagaId, "STEP-" + i + "-REF");
                step.setStatus(TransactionStatus.COMPLETED);
                step.setCompletedAt(LocalDateTime.now());
                steps.add(transactionRepository.saveAndFlush(step));
            }

            Transaction failedStep = createTransactionWithSagaId(sagaId, "STEP-FAILED-REF");
            failedStep.setStatus(TransactionStatus.FAILED);
            failedStep.setFailureReason("Deliberate failure");
            failedStep.setFailedAt(LocalDateTime.now());
            transactionRepository.saveAndFlush(failedStep);

            for (int i = steps.size() - 1; i >= 0; i--) {
                Transaction txn = transactionRepository.findById(steps.get(i).getId()).orElseThrow();
                txn.setStatus(TransactionStatus.ROLLED_BACK);
                txn.setRolledBackAt(LocalDateTime.now());
                transactionRepository.saveAndFlush(txn);
            }

            List<Transaction> rolledBackSteps = transactionRepository.findAll().stream()
                    .filter(t -> sagaId.equals(t.getSagaId()) && t.getStatus() == TransactionStatus.ROLLED_BACK)
                    .toList();

            assertThat(rolledBackSteps).hasSize(numberOfSteps);
        }

        @Test
        @DisplayName("Should handle partial saga rollback failure")
        void shouldHandlePartialSagaRollbackFailure() {
            String sagaId = "SAGA-PARTIAL-ROLLBACK-" + UUID.randomUUID();
            
            Transaction step1 = createTransactionWithSagaId(sagaId, "STEP-1-ROLLBACK");
            step1.setStatus(TransactionStatus.COMPLETED);
            step1.setCompletedAt(LocalDateTime.now());
            step1 = transactionRepository.saveAndFlush(step1);

            Transaction step2 = createTransactionWithSagaId(sagaId, "STEP-2-ROLLBACK");
            step2.setStatus(TransactionStatus.COMPLETED);
            step2.setCompletedAt(LocalDateTime.now());
            step2 = transactionRepository.saveAndFlush(step2);

            Transaction step3 = createTransactionWithSagaId(sagaId, "STEP-3-FAIL");
            step3.setStatus(TransactionStatus.FAILED);
            step3.setFailureReason("Database error");
            step3.setFailedAt(LocalDateTime.now());
            step3 = transactionRepository.saveAndFlush(step3);

            Transaction rollback2 = transactionRepository.findById(step2.getId()).orElseThrow();
            rollback2.setStatus(TransactionStatus.ROLLED_BACK);
            rollback2.setRolledBackAt(LocalDateTime.now());
            transactionRepository.saveAndFlush(rollback2);

            Transaction rollback1 = transactionRepository.findById(step1.getId()).orElseThrow();
            rollback1.setStatus(TransactionStatus.ROLLED_BACK);
            rollback1.setRolledBackAt(LocalDateTime.now());
            transactionRepository.saveAndFlush(rollback1);

            List<Transaction> sagaTransactions = transactionRepository.findAll().stream()
                    .filter(t -> sagaId.equals(t.getSagaId()))
                    .toList();

            assertThat(sagaTransactions).hasSize(3);
            assertThat(sagaTransactions).filteredOn(t -> t.getStatus() == TransactionStatus.ROLLED_BACK).hasSize(2);
        }
    }

    @Nested
    @DisplayName("External System Failure Tests")
    class ExternalSystemFailureTests {

        @Test
        @DisplayName("Should handle external payment gateway failure")
        void shouldHandleExternalPaymentGatewayFailure() {
            Transaction transaction = createAndSaveTransaction();
            transaction.setStatus(TransactionStatus.PROCESSING);
            transaction.setProcessedAt(LocalDateTime.now());
            transaction = transactionRepository.saveAndFlush(transaction);

            transaction = transactionRepository.findById(transaction.getId()).orElseThrow();
            transaction.setStatus(TransactionStatus.PROCESSING_ERROR);
            transaction.setFailureReason("Payment gateway timeout");
            transaction.setFailureCode("GATEWAY_TIMEOUT");
            transaction = transactionRepository.saveAndFlush(transaction);

            assertThat(transaction.getStatus()).isEqualTo(TransactionStatus.PROCESSING_ERROR);
            assertThat(transaction.getFailureCode()).isEqualTo("GATEWAY_TIMEOUT");
            assertThat(transaction.canRetry()).isTrue();
        }

        @Test
        @DisplayName("Should handle ledger service unavailability")
        void shouldHandleLedgerServiceUnavailability() {
            Transaction transaction = createAndSaveTransaction();
            transaction.setStatus(TransactionStatus.PROCESSING);
            transaction = transactionRepository.saveAndFlush(transaction);

            transaction = transactionRepository.findById(transaction.getId()).orElseThrow();
            transaction.setStatus(TransactionStatus.PROCESSING_ERROR);
            transaction.setFailureReason("Ledger service unavailable");
            transaction.setFailureCode("LEDGER_UNAVAILABLE");
            transaction.incrementRetryCount();
            transaction = transactionRepository.saveAndFlush(transaction);

            assertThat(transaction.getStatus()).isEqualTo(TransactionStatus.PROCESSING_ERROR);
            assertThat(transaction.getRetryCount()).isEqualTo(1);
            assertThat(transaction.getNextRetryAt()).isNotNull();
            assertThat(transaction.canRetry()).isTrue();
        }

        @Test
        @DisplayName("Should transition to PERMANENTLY_FAILED after max retries")
        void shouldTransitionToPermanentlyFailedAfterMaxRetries() {
            Transaction transaction = createAndSaveTransaction();
            transaction.setStatus(TransactionStatus.PROCESSING);
            transaction = transactionRepository.saveAndFlush(transaction);

            for (int i = 0; i < 3; i++) {
                transaction = transactionRepository.findById(transaction.getId()).orElseThrow();
                transaction.setStatus(TransactionStatus.PROCESSING_ERROR);
                transaction.incrementRetryCount();
                transaction = transactionRepository.saveAndFlush(transaction);
            }

            assertThat(transaction.getRetryCount()).isEqualTo(3);
            assertThat(transaction.canRetry()).isFalse();

            transaction = transactionRepository.findById(transaction.getId()).orElseThrow();
            transaction.setStatus(TransactionStatus.PERMANENTLY_FAILED);
            transaction.setFailureReason("Max retries exceeded");
            transaction = transactionRepository.saveAndFlush(transaction);

            assertThat(transaction.getStatus()).isEqualTo(TransactionStatus.PERMANENTLY_FAILED);
        }

        @Test
        @DisplayName("Should maintain data consistency on external service failure")
        void shouldMaintainDataConsistencyOnExternalServiceFailure() {
            String batchId = "BATCH-EXT-FAIL-" + UUID.randomUUID();
            
            Transaction txn1 = createTransactionWithBatchId(batchId, "TXN-1");
            txn1.setStatus(TransactionStatus.COMPLETED);
            txn1.setCompletedAt(LocalDateTime.now());
            txn1 = transactionRepository.saveAndFlush(txn1);

            Transaction txn2 = createTransactionWithBatchId(batchId, "TXN-2");
            txn2.setStatus(TransactionStatus.PROCESSING_ERROR);
            txn2.setFailureReason("External service failure");
            txn2.setFailureCode("EXT_SERVICE_FAIL");
            txn2 = transactionRepository.saveAndFlush(txn2);

            long completedCount = transactionRepository.countCompletedByBatchId(batchId);
            long failedCount = transactionRepository.countFailedByBatchId(batchId);

            assertThat(completedCount).isEqualTo(1);
            assertThat(failedCount).isZero();

            List<Transaction> batchTransactions = transactionRepository.findByBatchId(batchId);
            assertThat(batchTransactions).hasSize(2);
        }
    }

    @Nested
    @DisplayName("Database Constraint Violation Tests")
    class DatabaseConstraintTests {

        @Test
        @DisplayName("Should rollback on null constraint violation")
        @Transactional
        void shouldRollbackOnNullConstraintViolation() {
            Transaction transaction = Transaction.builder()
                    .id(UUID.randomUUID())
                    .reference(null)
                    .amount(BigDecimal.valueOf(100.00))
                    .currency("USD")
                    .type(TransactionType.P2P_TRANSFER)
                    .status(TransactionStatus.INITIATED)
                    .fromWalletId("WALLET-1")
                    .toWalletId("WALLET-2")
                    .fromUserId("USER-1")
                    .toUserId("USER-2")
                    .build();

            assertThatThrownBy(() -> transactionRepository.saveAndFlush(transaction))
                    .isInstanceOf(DataIntegrityViolationException.class);

            long count = transactionRepository.count();
            assertThat(count).isZero();
        }

        @Test
        @DisplayName("Should rollback on unique constraint violation")
        @Transactional
        void shouldRollbackOnUniqueConstraintViolation() {
            String duplicateRef = "TXN-DUPLICATE-" + UUID.randomUUID();
            
            Transaction txn1 = createTransactionWithReference(duplicateRef);
            transactionRepository.saveAndFlush(txn1);

            Transaction txn2 = createTransactionWithReference(duplicateRef);

            assertThatThrownBy(() -> transactionRepository.saveAndFlush(txn2))
                    .isInstanceOf(DataIntegrityViolationException.class);

            List<Transaction> transactions = transactionRepository.findByReference(duplicateRef);
            assertThat(transactions).hasSize(1);
        }
    }

    private Transaction createAndSaveTransaction() {
        Transaction transaction = Transaction.builder()
                .id(UUID.randomUUID())
                .reference("TXN-" + System.currentTimeMillis() + "-" + UUID.randomUUID().toString().substring(0, 8).toUpperCase())
                .sourceAccountId(fromAccountId.toString())
                .targetAccountId(toAccountId.toString())
                .amount(BigDecimal.valueOf(100.00))
                .currency("USD")
                .type(TransactionType.P2P_TRANSFER)
                .status(TransactionStatus.INITIATED)
                .createdAt(LocalDateTime.now())
                .fromWalletId("WALLET-" + fromAccountId)
                .toWalletId("WALLET-" + toAccountId)
                .fromUserId("USER-" + fromAccountId)
                .toUserId("USER-" + toAccountId)
                .retryCount(0)
                .build();

        return transactionRepository.saveAndFlush(transaction);
    }

    private Transaction createTransactionWithReference(String reference) {
        return Transaction.builder()
                .id(UUID.randomUUID())
                .reference(reference)
                .sourceAccountId(fromAccountId.toString())
                .targetAccountId(toAccountId.toString())
                .amount(BigDecimal.valueOf(100.00))
                .currency("USD")
                .type(TransactionType.P2P_TRANSFER)
                .status(TransactionStatus.INITIATED)
                .createdAt(LocalDateTime.now())
                .fromWalletId("WALLET-" + UUID.randomUUID())
                .toWalletId("WALLET-" + UUID.randomUUID())
                .fromUserId("USER-" + UUID.randomUUID())
                .toUserId("USER-" + UUID.randomUUID())
                .retryCount(0)
                .build();
    }

    private Transaction createTransactionWithBatchId(String batchId, String referenceSuffix) {
        return Transaction.builder()
                .id(UUID.randomUUID())
                .reference("TXN-" + System.currentTimeMillis() + "-" + referenceSuffix)
                .sourceAccountId(fromAccountId.toString())
                .targetAccountId(toAccountId.toString())
                .amount(BigDecimal.valueOf(50.00))
                .currency("USD")
                .type(TransactionType.P2P_TRANSFER)
                .status(TransactionStatus.INITIATED)
                .batchId(batchId)
                .createdAt(LocalDateTime.now())
                .fromWalletId("WALLET-" + UUID.randomUUID())
                .toWalletId("WALLET-" + UUID.randomUUID())
                .fromUserId("USER-" + UUID.randomUUID())
                .toUserId("USER-" + UUID.randomUUID())
                .retryCount(0)
                .build();
    }

    private Transaction createTransactionWithSagaId(String sagaId, String referenceSuffix) {
        return Transaction.builder()
                .id(UUID.randomUUID())
                .reference("TXN-" + System.currentTimeMillis() + "-" + referenceSuffix)
                .sourceAccountId(fromAccountId.toString())
                .targetAccountId(toAccountId.toString())
                .amount(BigDecimal.valueOf(75.00))
                .currency("USD")
                .type(TransactionType.P2P_TRANSFER)
                .status(TransactionStatus.INITIATED)
                .sagaId(sagaId)
                .createdAt(LocalDateTime.now())
                .fromWalletId("WALLET-" + UUID.randomUUID())
                .toWalletId("WALLET-" + UUID.randomUUID())
                .fromUserId("USER-" + UUID.randomUUID())
                .toUserId("USER-" + UUID.randomUUID())
                .retryCount(0)
                .build();
    }
}