package com.waqiti.account.kafka.dlq;

import com.waqiti.account.entity.DlqRetryRecord;
import com.waqiti.account.entity.ManualReviewRecord;
import com.waqiti.account.entity.PermanentFailureRecord;
import com.waqiti.account.repository.DlqRetryRepository;
import com.waqiti.account.repository.ManualReviewRepository;
import com.waqiti.account.repository.PermanentFailureRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.time.LocalDateTime;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;
import static java.util.concurrent.TimeUnit.SECONDS;

/**
 * Integration tests for DLQ retry processor
 *
 * <p>Tests the complete DLQ retry flow with real database using Testcontainers.</p>
 *
 * @author Waqiti Platform Team
 */
@SpringBootTest
@Testcontainers
@ActiveProfiles("test")
class DlqRetryProcessorIntegrationTest {

    @Container
    static PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("postgres:15-alpine")
        .withDatabaseName("account_service_test")
        .withUsername("test")
        .withPassword("test");

    @DynamicPropertySource
    static void configureProperties(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", postgres::getJdbcUrl);
        registry.add("spring.datasource.username", postgres::getUsername);
        registry.add("spring.datasource.password", postgres::getPassword);
        registry.add("spring.jpa.hibernate.ddl-auto", () -> "create-drop");
        registry.add("dlq.retry.polling-interval-ms", () -> "1000");  // Fast polling for tests
        registry.add("dlq.alerts.enabled", () -> "false");  // Disable alerts in tests
    }

    @Autowired
    private DlqRetryRepository retryRepository;

    @Autowired
    private ManualReviewRepository manualReviewRepository;

    @Autowired
    private PermanentFailureRepository permanentFailureRepository;

    @Autowired
    private DlqRetryProcessor retryProcessor;

    @BeforeEach
    void setUp() {
        // Clean up all tables before each test
        retryRepository.deleteAll();
        manualReviewRepository.deleteAll();
        permanentFailureRepository.deleteAll();
    }

    @Test
    void shouldProcessPendingRetries() {
        // Given a pending retry that is due
        DlqRetryRecord retry = createPendingRetry(LocalDateTime.now().minusSeconds(1));
        retryRepository.save(retry);

        // When processor runs
        await().atMost(5, SECONDS).untilAsserted(() -> {
            // Then retry should be processed
            DlqRetryRecord updated = retryRepository.findById(retry.getId()).orElseThrow();
            assertThat(updated.getStatus()).isIn(
                DlqRetryRecord.RetryStatus.RETRYING,
                DlqRetryRecord.RetryStatus.PENDING,  // Rescheduled
                DlqRetryRecord.RetryStatus.SUCCESS
            );
        });
    }

    @Test
    void shouldNotProcessFutureRetries() {
        // Given a retry scheduled for the future
        DlqRetryRecord retry = createPendingRetry(LocalDateTime.now().plusHours(1));
        retryRepository.save(retry);

        // When some time passes
        try {
            Thread.sleep(2000);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }

        // Then retry should still be PENDING and not processed
        DlqRetryRecord unchanged = retryRepository.findById(retry.getId()).orElseThrow();
        assertThat(unchanged.getStatus()).isEqualTo(DlqRetryRecord.RetryStatus.PENDING);
        assertThat(unchanged.getRetryAttempt()).isEqualTo(0);
    }

    @Test
    void shouldEscalateToManualReviewAfterMaxRetries() {
        // Given a retry at max attempts
        DlqRetryRecord retry = createPendingRetry(LocalDateTime.now().minusSeconds(1));
        retry.setRetryAttempt(3);  // At max
        retry.setMaxRetryAttempts(3);
        retryRepository.save(retry);

        // When processor runs
        retryProcessor.processRetry(retry);

        // Then retry should be marked as FAILED
        DlqRetryRecord failed = retryRepository.findById(retry.getId()).orElseThrow();
        assertThat(failed.getStatus()).isEqualTo(DlqRetryRecord.RetryStatus.FAILED);

        // And manual review should be created
        await().atMost(3, SECONDS).untilAsserted(() -> {
            List<ManualReviewRecord> reviews = manualReviewRepository.findAll();
            assertThat(reviews).hasSize(1);
            assertThat(reviews.get(0).getOriginalTopic()).isEqualTo(retry.getOriginalTopic());
            assertThat(reviews.get(0).getPriority()).isEqualTo(ManualReviewRecord.ReviewPriority.HIGH);
            assertThat(reviews.get(0).getRetryAttempts()).isEqualTo(3);
        });
    }

    @Test
    void shouldIncrementRetryAttemptOnFailure() {
        // Given a retry on first attempt
        DlqRetryRecord retry = createPendingRetry(LocalDateTime.now().minusSeconds(1));
        retry.setRetryAttempt(0);
        retry.setMaxRetryAttempts(3);
        retryRepository.save(retry);

        // When processor runs and recovery fails (no handler implemented)
        retryProcessor.processRetry(retry);

        // Then retry attempt should be incremented
        await().atMost(3, SECONDS).untilAsserted(() -> {
            DlqRetryRecord updated = retryRepository.findById(retry.getId()).orElseThrow();
            assertThat(updated.getRetryAttempt()).isEqualTo(1);
            assertThat(updated.getStatus()).isEqualTo(DlqRetryRecord.RetryStatus.PENDING);
            assertThat(updated.getNextRetryAt()).isAfter(LocalDateTime.now());
        });
    }

    @Test
    void shouldApplyExponentialBackoff() {
        // Given retries at different attempt numbers
        DlqRetryRecord retry1 = createPendingRetry(LocalDateTime.now().minusSeconds(1));
        retry1.setRetryAttempt(0);
        retry1.setMaxRetryAttempts(3);

        DlqRetryRecord retry2 = createPendingRetry(LocalDateTime.now().minusSeconds(1));
        retry2.setRetryAttempt(1);
        retry2.setMaxRetryAttempts(3);

        retryRepository.saveAll(List.of(retry1, retry2));

        // When retries are processed
        retryProcessor.processRetry(retry1);
        retryProcessor.processRetry(retry2);

        // Then backoff should increase exponentially
        DlqRetryRecord updated1 = retryRepository.findById(retry1.getId()).orElseThrow();
        DlqRetryRecord updated2 = retryRepository.findById(retry2.getId()).orElseThrow();

        // Retry 2 should have longer backoff than retry 1
        assertThat(updated2.getBackoffDelayMs()).isGreaterThan(updated1.getBackoffDelayMs());
    }

    @Test
    void shouldCleanupSuccessfulRetries() {
        // Given old successful retries
        DlqRetryRecord oldSuccess = createPendingRetry(LocalDateTime.now().minusDays(2));
        oldSuccess.setStatus(DlqRetryRecord.RetryStatus.SUCCESS);
        oldSuccess.setUpdatedAt(LocalDateTime.now().minusDays(2));

        DlqRetryRecord recentSuccess = createPendingRetry(LocalDateTime.now().minusHours(1));
        recentSuccess.setStatus(DlqRetryRecord.RetryStatus.SUCCESS);

        retryRepository.saveAll(List.of(oldSuccess, recentSuccess));

        // When cleanup runs (with 24h retention)
        int deleted = retryRepository.deleteByUpdatedAtBeforeAndStatusIn(
            LocalDateTime.now().minusHours(24),
            List.of(DlqRetryRecord.RetryStatus.SUCCESS, DlqRetryRecord.RetryStatus.CANCELLED));

        // Then old success should be deleted
        assertThat(deleted).isEqualTo(1);

        // And recent success should remain
        List<DlqRetryRecord> remaining = retryRepository.findAll();
        assertThat(remaining).hasSize(1);
        assertThat(remaining.get(0).getId()).isEqualTo(recentSuccess.getId());
    }

    @Test
    void shouldMarkSlaBreachedReviews() {
        // Given a review past its SLA
        ManualReviewRecord review = ManualReviewRecord.builder()
            .originalTopic("test.topic")
            .originalPartition(0)
            .originalOffset(123L)
            .payload("{\"test\": \"data\"}")
            .failedAt(LocalDateTime.now().minusHours(2))
            .reviewReason("Test review")
            .priority(ManualReviewRecord.ReviewPriority.HIGH)  // 1hr SLA
            .status(ManualReviewRecord.ReviewStatus.PENDING)
            .handlerName("TestHandler")
            .build();

        review.onCreate();  // This sets slaDueAt
        review.setSlaDueAt(LocalDateTime.now().minusMinutes(10));  // Override to past
        manualReviewRepository.save(review);

        // When SLA check runs
        int breached = manualReviewRepository.markSlaBreached(LocalDateTime.now());

        // Then review should be marked as breached
        assertThat(breached).isEqualTo(1);

        ManualReviewRecord updated = manualReviewRepository.findById(review.getId()).orElseThrow();
        assertThat(updated.getSlaBreached()).isTrue();
    }

    @Test
    void shouldFindPendingRetriesByPriority() {
        // Given retries with different due times
        DlqRetryRecord retry1 = createPendingRetry(LocalDateTime.now().minusMinutes(10));
        DlqRetryRecord retry2 = createPendingRetry(LocalDateTime.now().minusMinutes(5));
        DlqRetryRecord retry3 = createPendingRetry(LocalDateTime.now().plusMinutes(5));  // Future

        retryRepository.saveAll(List.of(retry1, retry2, retry3));

        // When querying pending retries
        List<DlqRetryRecord> pending = retryRepository.findPendingRetries(LocalDateTime.now());

        // Then should return only due retries, ordered by nextRetryAt
        assertThat(pending).hasSize(2);
        assertThat(pending.get(0).getId()).isEqualTo(retry1.getId());
        assertThat(pending.get(1).getId()).isEqualTo(retry2.getId());
    }

    // ========== TEST HELPERS ==========

    private DlqRetryRecord createPendingRetry(LocalDateTime nextRetryAt) {
        DlqRetryRecord retry = DlqRetryRecord.builder()
            .originalTopic("test.topic")
            .originalPartition(0)
            .originalOffset(System.currentTimeMillis())
            .payload("{\"test\": \"payload\"}")
            .exceptionMessage("Test exception")
            .exceptionClass("TestException")
            .failedAt(LocalDateTime.now().minusHours(1))
            .failureReason("Test failure")
            .retryAttempt(0)
            .maxRetryAttempts(3)
            .nextRetryAt(nextRetryAt)
            .backoffDelayMs(5000L)
            .status(DlqRetryRecord.RetryStatus.PENDING)
            .handlerName("TestHandler")
            .correlationId("test-correlation-id")
            .build();

        retry.onCreate();
        return retry;
    }
}
