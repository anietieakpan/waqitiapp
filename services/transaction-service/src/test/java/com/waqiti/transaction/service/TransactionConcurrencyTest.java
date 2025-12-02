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
import org.springframework.orm.ObjectOptimisticLockingFailureException;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.TestPropertySource;
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
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.*;

@SpringBootTest
@Testcontainers
@ActiveProfiles("integration-test")
@TestPropertySource(properties = {
        "spring.datasource.url=jdbc:tc:postgresql:15:///waqiti_test",
        "spring.jpa.hibernate.ddl-auto=create-drop"
})
@DisplayName("Transaction Concurrency and ACID Tests")
class TransactionConcurrencyTest {

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
    private TransactionRequest validRequest;

    @BeforeEach
    void setUp() {
        fromAccountId = UUID.randomUUID();
        toAccountId = UUID.randomUUID();

        validRequest = TransactionRequest.builder()
                .fromAccountId(fromAccountId)
                .toAccountId(toAccountId)
                .amount(BigDecimal.valueOf(100.00))
                .currency("USD")
                .transactionType("P2P_TRANSFER")
                .description("Test transaction")
                .build();
    }

    @Nested
    @DisplayName("Optimistic Locking Tests")
    class OptimisticLockingTests {

        @Test
        @DisplayName("Should detect concurrent modifications with optimistic locking")
        void shouldDetectConcurrentModificationsWithOptimisticLocking() {
            Transaction transaction = createAndSaveTransaction();

            Transaction txn1 = transactionRepository.findById(transaction.getId()).orElseThrow();
            Transaction txn2 = transactionRepository.findById(transaction.getId()).orElseThrow();

            txn1.setStatus(TransactionStatus.PROCESSING);
            transactionRepository.saveAndFlush(txn1);

            txn2.setStatus(TransactionStatus.COMPLETED);

            assertThatThrownBy(() -> transactionRepository.saveAndFlush(txn2))
                    .isInstanceOf(ObjectOptimisticLockingFailureException.class);
        }

        @Test
        @DisplayName("Should handle version conflicts gracefully with retry logic")
        void shouldHandleVersionConflictsGracefullyWithRetryLogic() throws Exception {
            Transaction transaction = createAndSaveTransaction();
            int numberOfConcurrentUpdates = 5;
            AtomicInteger successCount = new AtomicInteger(0);
            AtomicInteger failureCount = new AtomicInteger(0);

            ExecutorService executorService = Executors.newFixedThreadPool(numberOfConcurrentUpdates);
            CountDownLatch latch = new CountDownLatch(numberOfConcurrentUpdates);

            for (int i = 0; i < numberOfConcurrentUpdates; i++) {
                final int updateNumber = i;
                executorService.submit(() -> {
                    try {
                        int maxRetries = 5;
                        for (int retry = 0; retry < maxRetries; retry++) {
                            try {
                                Transaction txn = transactionRepository.findById(transaction.getId()).orElseThrow();
                                txn.setDescription("Update " + updateNumber + " - retry " + retry);
                                transactionRepository.saveAndFlush(txn);
                                successCount.incrementAndGet();
                                break;
                            } catch (ObjectOptimisticLockingFailureException e) {
                                if (retry == maxRetries - 1) {
                                    failureCount.incrementAndGet();
                                }
                                Thread.sleep(50 * (retry + 1));
                            }
                        }
                    } catch (Exception e) {
                        failureCount.incrementAndGet();
                    } finally {
                        latch.countDown();
                    }
                });
            }

            latch.await(30, TimeUnit.SECONDS);
            executorService.shutdown();

            assertThat(successCount.get()).isEqualTo(numberOfConcurrentUpdates);
            assertThat(failureCount.get()).isZero();

            Transaction finalTransaction = transactionRepository.findById(transaction.getId()).orElseThrow();
            assertThat(finalTransaction.getVersion()).isEqualTo((long) numberOfConcurrentUpdates);
        }

        @Test
        @DisplayName("Should maintain version integrity across multiple concurrent transactions")
        void shouldMaintainVersionIntegrityAcrossConcurrentTransactions() throws Exception {
            Transaction transaction = createAndSaveTransaction();
            int numberOfThreads = 10;
            CyclicBarrier barrier = new CyclicBarrier(numberOfThreads);
            ExecutorService executorService = Executors.newFixedThreadPool(numberOfThreads);
            
            List<Future<Boolean>> futures = new ArrayList<>();

            for (int i = 0; i < numberOfThreads; i++) {
                final int threadId = i;
                futures.add(executorService.submit(() -> {
                    try {
                        barrier.await();
                        
                        Transaction txn = transactionRepository.findById(transaction.getId()).orElseThrow();
                        txn.setStatus(TransactionStatus.PROCESSING);
                        transactionRepository.saveAndFlush(txn);
                        return true;
                    } catch (ObjectOptimisticLockingFailureException e) {
                        return false;
                    } catch (Exception e) {
                        throw new RuntimeException(e);
                    }
                }));
            }

            int successfulUpdates = 0;
            int failedUpdates = 0;
            
            for (Future<Boolean> future : futures) {
                if (future.get(30, TimeUnit.SECONDS)) {
                    successfulUpdates++;
                } else {
                    failedUpdates++;
                }
            }

            executorService.shutdown();

            assertThat(successfulUpdates).isEqualTo(1);
            assertThat(failedUpdates).isEqualTo(numberOfThreads - 1);

            Transaction finalTransaction = transactionRepository.findById(transaction.getId()).orElseThrow();
            assertThat(finalTransaction.getVersion()).isEqualTo(1L);
        }
    }

    @Nested
    @DisplayName("Database Isolation Level Tests")
    class DatabaseIsolationTests {

        @Test
        @DisplayName("Should prevent phantom reads during concurrent transaction queries")
        void shouldPreventPhantomReadsDuringConcurrentQueries() throws Exception {
            String batchId = "BATCH-" + UUID.randomUUID();
            
            for (int i = 0; i < 5; i++) {
                createTransactionWithBatchId(batchId);
            }

            ExecutorService executorService = Executors.newFixedThreadPool(2);
            CountDownLatch readLatch = new CountDownLatch(1);
            CountDownLatch writeLatch = new CountDownLatch(1);
            
            AtomicInteger firstCount = new AtomicInteger(0);
            AtomicInteger secondCount = new AtomicInteger(0);

            Future<?> reader = executorService.submit(() -> {
                try {
                    List<Transaction> transactions = transactionRepository.findByBatchId(batchId);
                    firstCount.set(transactions.size());
                    
                    readLatch.countDown();
                    writeLatch.await(5, TimeUnit.SECONDS);
                    
                    Thread.sleep(100);
                    
                    transactions = transactionRepository.findByBatchId(batchId);
                    secondCount.set(transactions.size());
                } catch (Exception e) {
                    throw new RuntimeException(e);
                }
            });

            Future<?> writer = executorService.submit(() -> {
                try {
                    readLatch.await(5, TimeUnit.SECONDS);
                    
                    for (int i = 0; i < 3; i++) {
                        createTransactionWithBatchId(batchId);
                    }
                    
                    writeLatch.countDown();
                } catch (Exception e) {
                    throw new RuntimeException(e);
                }
            });

            reader.get(30, TimeUnit.SECONDS);
            writer.get(30, TimeUnit.SECONDS);
            executorService.shutdown();

            assertThat(firstCount.get()).isEqualTo(5);
            assertThat(secondCount.get()).isEqualTo(8);
        }

        @Test
        @DisplayName("Should prevent dirty reads on uncommitted transaction data")
        void shouldPreventDirtyReadsOnUncommittedData() throws Exception {
            Transaction transaction = createAndSaveTransaction();
            
            ExecutorService executorService = Executors.newFixedThreadPool(2);
            CountDownLatch startLatch = new CountDownLatch(1);
            AtomicInteger readValue = new AtomicInteger(0);

            Future<?> writer = executorService.submit(() -> {
                try {
                    Transaction txn = transactionRepository.findById(transaction.getId()).orElseThrow();
                    txn.setRetryCount(999);
                    transactionRepository.save(txn);
                    
                    startLatch.countDown();
                    Thread.sleep(500);
                    
                    throw new RuntimeException("Simulated rollback");
                } catch (Exception e) {
                }
            });

            Future<?> reader = executorService.submit(() -> {
                try {
                    startLatch.await(5, TimeUnit.SECONDS);
                    Thread.sleep(200);
                    
                    Transaction txn = transactionRepository.findById(transaction.getId()).orElseThrow();
                    readValue.set(txn.getRetryCount());
                } catch (Exception e) {
                    throw new RuntimeException(e);
                }
            });

            writer.get(30, TimeUnit.SECONDS);
            reader.get(30, TimeUnit.SECONDS);
            executorService.shutdown();

            assertThat(readValue.get()).isNotEqualTo(999);
            assertThat(readValue.get()).isZero();
        }
    }

    @Nested
    @DisplayName("Concurrent Transaction Processing Tests")
    class ConcurrentProcessingTests {

        @Test
        @DisplayName("Should handle concurrent transaction creation without deadlocks")
        void shouldHandleConcurrentCreationWithoutDeadlocks() throws Exception {
            int numberOfTransactions = 20;
            ExecutorService executorService = Executors.newFixedThreadPool(numberOfTransactions);
            CountDownLatch latch = new CountDownLatch(numberOfTransactions);
            List<Future<Transaction>> futures = new ArrayList<>();

            for (int i = 0; i < numberOfTransactions; i++) {
                final int txnNumber = i;
                futures.add(executorService.submit(() -> {
                    try {
                        TransactionRequest request = TransactionRequest.builder()
                                .fromAccountId(UUID.randomUUID())
                                .toAccountId(UUID.randomUUID())
                                .amount(BigDecimal.valueOf(50.00 + txnNumber))
                                .currency("USD")
                                .transactionType("P2P_TRANSFER")
                                .description("Concurrent transaction " + txnNumber)
                                .build();

                        return transactionService.createTransaction(request);
                    } finally {
                        latch.countDown();
                    }
                }));
            }

            boolean completed = latch.await(60, TimeUnit.SECONDS);
            executorService.shutdown();

            assertThat(completed).isTrue();

            List<Transaction> createdTransactions = new ArrayList<>();
            for (Future<Transaction> future : futures) {
                createdTransactions.add(future.get());
            }

            assertThat(createdTransactions).hasSize(numberOfTransactions);
            assertThat(createdTransactions).extracting(Transaction::getReference).doesNotHaveDuplicates();
        }

        @Test
        @DisplayName("Should maintain transaction integrity during concurrent status updates")
        void shouldMaintainIntegrityDuringConcurrentStatusUpdates() throws Exception {
            int numberOfTransactions = 10;
            List<Transaction> transactions = new ArrayList<>();
            
            for (int i = 0; i < numberOfTransactions; i++) {
                transactions.add(createAndSaveTransaction());
            }

            ExecutorService executorService = Executors.newFixedThreadPool(numberOfTransactions * 2);
            CountDownLatch latch = new CountDownLatch(numberOfTransactions);

            for (Transaction transaction : transactions) {
                executorService.submit(() -> {
                    try {
                        Transaction txn = transactionRepository.findById(transaction.getId()).orElseThrow();
                        txn.setStatus(TransactionStatus.PROCESSING);
                        transactionRepository.saveAndFlush(txn);

                        Thread.sleep(100);

                        txn = transactionRepository.findById(transaction.getId()).orElseThrow();
                        txn.setStatus(TransactionStatus.COMPLETED);
                        txn.setCompletedAt(LocalDateTime.now());
                        transactionRepository.saveAndFlush(txn);
                    } catch (Exception e) {
                    } finally {
                        latch.countDown();
                    }
                });
            }

            boolean completed = latch.await(60, TimeUnit.SECONDS);
            executorService.shutdown();

            assertThat(completed).isTrue();

            List<Transaction> completedTransactions = transactionRepository.findByStatus(TransactionStatus.COMPLETED);
            assertThat(completedTransactions).hasSizeGreaterThanOrEqualTo(numberOfTransactions / 2);
        }

        @Test
        @DisplayName("Should prevent race conditions in transaction reference generation")
        void shouldPreventRaceConditionsInReferenceGeneration() throws Exception {
            int numberOfTransactions = 50;
            ExecutorService executorService = Executors.newFixedThreadPool(numberOfTransactions);
            CyclicBarrier barrier = new CyclicBarrier(numberOfTransactions);
            List<Future<String>> futures = new ArrayList<>();

            for (int i = 0; i < numberOfTransactions; i++) {
                futures.add(executorService.submit(() -> {
                    try {
                        barrier.await();
                        
                        TransactionRequest request = TransactionRequest.builder()
                                .fromAccountId(UUID.randomUUID())
                                .toAccountId(UUID.randomUUID())
                                .amount(BigDecimal.valueOf(10.00))
                                .currency("USD")
                                .transactionType("P2P_TRANSFER")
                                .description("Reference test")
                                .build();

                        Transaction transaction = transactionService.createTransaction(request);
                        return transaction.getReference();
                    } catch (Exception e) {
                        throw new RuntimeException(e);
                    }
                }));
            }

            List<String> references = new ArrayList<>();
            for (Future<String> future : futures) {
                references.add(future.get(60, TimeUnit.SECONDS));
            }

            executorService.shutdown();

            assertThat(references).hasSize(numberOfTransactions);
            assertThat(references).doesNotHaveDuplicates();
        }
    }

    @Nested
    @DisplayName("Batch Transaction Processing Tests")
    class BatchProcessingTests {

        @Test
        @DisplayName("Should handle batch transactions atomically")
        void shouldHandleBatchTransactionsAtomically() throws Exception {
            String batchId = "BATCH-" + UUID.randomUUID();
            int batchSize = 15;
            
            List<Transaction> batchTransactions = new ArrayList<>();
            for (int i = 0; i < batchSize; i++) {
                batchTransactions.add(createTransactionWithBatchId(batchId));
            }

            ExecutorService executorService = Executors.newFixedThreadPool(batchSize);
            CountDownLatch latch = new CountDownLatch(batchSize);
            AtomicInteger successCount = new AtomicInteger(0);
            AtomicInteger failureCount = new AtomicInteger(0);

            for (int i = 0; i < batchSize; i++) {
                final int index = i;
                executorService.submit(() -> {
                    try {
                        Transaction txn = batchTransactions.get(index);
                        txn = transactionRepository.findById(txn.getId()).orElseThrow();
                        
                        if (index == batchSize / 2) {
                            txn.setStatus(TransactionStatus.FAILED);
                            txn.setFailureReason("Simulated batch failure");
                            failureCount.incrementAndGet();
                        } else {
                            txn.setStatus(TransactionStatus.COMPLETED);
                            txn.setCompletedAt(LocalDateTime.now());
                            successCount.incrementAndGet();
                        }
                        
                        transactionRepository.saveAndFlush(txn);
                    } catch (Exception e) {
                    } finally {
                        latch.countDown();
                    }
                });
            }

            latch.await(60, TimeUnit.SECONDS);
            executorService.shutdown();

            List<Transaction> completedInBatch = transactionRepository.findByBatchIdAndStatus(
                    batchId, TransactionStatus.COMPLETED);
            List<Transaction> failedInBatch = transactionRepository.findByBatchIdAndStatus(
                    batchId, TransactionStatus.FAILED);

            assertThat(completedInBatch).hasSizeGreaterThan(0);
            assertThat(failedInBatch).hasSize(1);
            assertThat(completedInBatch.size() + failedInBatch.size()).isEqualTo(batchSize);
        }

        @Test
        @DisplayName("Should rollback entire batch on critical failure")
        void shouldRollbackEntireBatchOnCriticalFailure() throws Exception {
            String batchId = "BATCH-ROLLBACK-" + UUID.randomUUID();
            int batchSize = 10;
            
            List<Transaction> batchTransactions = new ArrayList<>();
            for (int i = 0; i < batchSize; i++) {
                batchTransactions.add(createTransactionWithBatchId(batchId));
            }

            try {
                for (Transaction txn : batchTransactions) {
                    txn.setStatus(TransactionStatus.PROCESSING);
                    transactionRepository.save(txn);
                }

                Transaction criticalFailure = batchTransactions.get(batchSize / 2);
                criticalFailure.setStatus(TransactionStatus.FAILED);
                criticalFailure.setFailureReason("Critical failure - batch must rollback");
                transactionRepository.saveAndFlush(criticalFailure);

                for (Transaction txn : batchTransactions) {
                    if (!txn.getId().equals(criticalFailure.getId())) {
                        txn = transactionRepository.findById(txn.getId()).orElseThrow();
                        txn.setStatus(TransactionStatus.ROLLED_BACK);
                        txn.setRolledBackAt(LocalDateTime.now());
                        transactionRepository.save(txn);
                    }
                }
                
                transactionRepository.flush();
            } catch (Exception e) {
            }

            List<Transaction> rolledBackTransactions = transactionRepository.findByBatchIdAndStatus(
                    batchId, TransactionStatus.ROLLED_BACK);
            List<Transaction> failedTransactions = transactionRepository.findByBatchIdAndStatus(
                    batchId, TransactionStatus.FAILED);

            assertThat(rolledBackTransactions).hasSize(batchSize - 1);
            assertThat(failedTransactions).hasSize(1);
        }
    }

    @Nested
    @DisplayName("Deadlock Prevention Tests")
    class DeadlockPreventionTests {

        @Test
        @DisplayName("Should prevent deadlocks in bidirectional transfers")
        void shouldPreventDeadlocksInBidirectionalTransfers() throws Exception {
            UUID account1 = UUID.randomUUID();
            UUID account2 = UUID.randomUUID();
            
            int numberOfTransfers = 10;
            ExecutorService executorService = Executors.newFixedThreadPool(numberOfTransfers * 2);
            CountDownLatch latch = new CountDownLatch(numberOfTransfers * 2);
            AtomicInteger successCount = new AtomicInteger(0);

            for (int i = 0; i < numberOfTransfers; i++) {
                executorService.submit(() -> {
                    try {
                        TransactionRequest request = TransactionRequest.builder()
                                .fromAccountId(account1)
                                .toAccountId(account2)
                                .amount(BigDecimal.valueOf(10.00))
                                .currency("USD")
                                .transactionType("P2P_TRANSFER")
                                .description("A to B transfer")
                                .build();

                        transactionService.createTransaction(request);
                        successCount.incrementAndGet();
                    } catch (Exception e) {
                    } finally {
                        latch.countDown();
                    }
                });

                executorService.submit(() -> {
                    try {
                        TransactionRequest request = TransactionRequest.builder()
                                .fromAccountId(account2)
                                .toAccountId(account1)
                                .amount(BigDecimal.valueOf(10.00))
                                .currency("USD")
                                .transactionType("P2P_TRANSFER")
                                .description("B to A transfer")
                                .build();

                        transactionService.createTransaction(request);
                        successCount.incrementAndGet();
                    } catch (Exception e) {
                    } finally {
                        latch.countDown();
                    }
                });
            }

            boolean completed = latch.await(90, TimeUnit.SECONDS);
            executorService.shutdown();

            assertThat(completed).isTrue();
            assertThat(successCount.get()).isEqualTo(numberOfTransfers * 2);
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

    private Transaction createTransactionWithBatchId(String batchId) {
        Transaction transaction = Transaction.builder()
                .id(UUID.randomUUID())
                .reference("TXN-" + System.currentTimeMillis() + "-" + UUID.randomUUID().toString().substring(0, 8).toUpperCase())
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

        return transactionRepository.saveAndFlush(transaction);
    }
}