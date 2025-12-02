package com.waqiti.analytics.repository;

import com.waqiti.analytics.entity.DlqMessage;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.jdbc.AutoConfigureTestDatabase;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Integration Tests for DlqMessageRepository
 *
 * Uses TestContainers to test against real PostgreSQL database.
 * Verifies custom queries, indexes, and database constraints.
 *
 * @author Waqiti Analytics Team
 * @version 1.0.0-PRODUCTION
 * @since 2025-11-15
 */
@DataJpaTest
@Testcontainers
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@DisplayName("DlqMessageRepository Integration Tests")
class DlqMessageRepositoryTest {

    @Container
    static PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("postgres:15-alpine")
        .withDatabaseName("test_analytics")
        .withUsername("test")
        .withPassword("test");

    @Autowired
    private DlqMessageRepository repository;

    @BeforeEach
    void setUp() {
        repository.deleteAll();
    }

    @Test
    @DisplayName("Should save and retrieve DLQ message with all fields")
    void shouldSaveAndRetrieveDlqMessage() {
        // Given
        DlqMessage message = createTestMessage("test.topic", DlqMessage.DlqStatus.PENDING_REVIEW);

        // When
        DlqMessage saved = repository.save(message);
        DlqMessage retrieved = repository.findById(saved.getId()).orElseThrow();

        // Then
        assertThat(retrieved).isNotNull();
        assertThat(retrieved.getId()).isEqualTo(saved.getId());
        assertThat(retrieved.getOriginalTopic()).isEqualTo("test.topic");
        assertThat(retrieved.getStatus()).isEqualTo(DlqMessage.DlqStatus.PENDING_REVIEW);
        assertThat(retrieved.getRetryCount()).isEqualTo(0);
        assertThat(retrieved.getVersion()).isEqualTo(0L);
    }

    @Test
    @DisplayName("Should find messages eligible for retry")
    void shouldFindEligibleForRetry() {
        // Given
        DlqMessage eligible1 = createTestMessage("topic1", DlqMessage.DlqStatus.PENDING_REVIEW);
        eligible1.setRetryCount(1);
        eligible1.setMaxRetryAttempts(3);
        eligible1.setSeverity(DlqMessage.Severity.HIGH);
        repository.save(eligible1);

        DlqMessage eligible2 = createTestMessage("topic2", DlqMessage.DlqStatus.PENDING_REVIEW);
        eligible2.setRetryCount(0);
        eligible2.setMaxRetryAttempts(3);
        eligible2.setSeverity(DlqMessage.Severity.MEDIUM);
        repository.save(eligible2);

        // Not eligible - max retries reached
        DlqMessage maxedOut = createTestMessage("topic3", DlqMessage.DlqStatus.PENDING_REVIEW);
        maxedOut.setRetryCount(3);
        maxedOut.setMaxRetryAttempts(3);
        repository.save(maxedOut);

        // Not eligible - wrong status
        DlqMessage recovered = createTestMessage("topic4", DlqMessage.DlqStatus.RECOVERED);
        recovered.setRetryCount(1);
        recovered.setMaxRetryAttempts(3);
        repository.save(recovered);

        // When
        List<DlqMessage> eligible = repository.findEligibleForRetry();

        // Then
        assertThat(eligible).hasSize(2);
        assertThat(eligible).extracting(DlqMessage::getOriginalTopic)
            .containsExactlyInAnyOrder("topic1", "topic2");

        // Verify ordering: HIGH severity should come first
        assertThat(eligible.get(0).getSeverity()).isEqualTo(DlqMessage.Severity.HIGH);
    }

    @Test
    @DisplayName("Should find stale pending messages")
    void shouldFindStalePendingMessages() {
        // Given
        LocalDateTime oldTimestamp = LocalDateTime.now().minusHours(48);
        LocalDateTime recentTimestamp = LocalDateTime.now().minusHours(12);

        DlqMessage stale = createTestMessage("stale.topic", DlqMessage.DlqStatus.PENDING_REVIEW);
        stale.setReceivedAt(oldTimestamp);
        repository.save(stale);

        DlqMessage recent = createTestMessage("recent.topic", DlqMessage.DlqStatus.PENDING_REVIEW);
        recent.setReceivedAt(recentTimestamp);
        repository.save(recent);

        // When
        LocalDateTime threshold = LocalDateTime.now().minusHours(24);
        List<DlqMessage> staleMessages = repository.findStalePendingMessages(threshold);

        // Then
        assertThat(staleMessages).hasSize(1);
        assertThat(staleMessages.get(0).getOriginalTopic()).isEqualTo("stale.topic");
    }

    @Test
    @DisplayName("Should count messages by status")
    void shouldCountByStatus() {
        // Given
        repository.save(createTestMessage("topic1", DlqMessage.DlqStatus.PENDING_REVIEW));
        repository.save(createTestMessage("topic2", DlqMessage.DlqStatus.PENDING_REVIEW));
        repository.save(createTestMessage("topic3", DlqMessage.DlqStatus.RECOVERED));
        repository.save(createTestMessage("topic4", DlqMessage.DlqStatus.FAILED));

        // When
        long pendingCount = repository.countByStatus(DlqMessage.DlqStatus.PENDING_REVIEW);
        long recoveredCount = repository.countByStatus(DlqMessage.DlqStatus.RECOVERED);
        long failedCount = repository.countByStatus(DlqMessage.DlqStatus.FAILED);

        // Then
        assertThat(pendingCount).isEqualTo(2);
        assertThat(recoveredCount).isEqualTo(1);
        assertThat(failedCount).isEqualTo(1);
    }

    @Test
    @DisplayName("Should find by correlation ID")
    void shouldFindByCorrelationId() {
        // Given
        String correlationId = "test-correlation-123";
        DlqMessage message = createTestMessage("test.topic", DlqMessage.DlqStatus.PENDING_REVIEW);
        message.setCorrelationId(correlationId);
        repository.save(message);

        // When
        DlqMessage found = repository.findByCorrelationId(correlationId).orElse(null);

        // Then
        assertThat(found).isNotNull();
        assertThat(found.getCorrelationId()).isEqualTo(correlationId);
    }

    @Test
    @DisplayName("Should find failed messages not yet alerted")
    void shouldFindFailedMessagesNotAlerted() {
        // Given
        DlqMessage notAlerted = createTestMessage("topic1", DlqMessage.DlqStatus.FAILED);
        notAlerted.setAlerted(false);
        repository.save(notAlerted);

        DlqMessage alerted = createTestMessage("topic2", DlqMessage.DlqStatus.FAILED);
        alerted.setAlerted(true);
        repository.save(alerted);

        DlqMessage pending = createTestMessage("topic3", DlqMessage.DlqStatus.PENDING_REVIEW);
        pending.setAlerted(false);
        repository.save(pending);

        // When
        List<DlqMessage> failedNotAlerted = repository.findFailedMessagesNotAlerted();

        // Then
        assertThat(failedNotAlerted).hasSize(1);
        assertThat(failedNotAlerted.get(0).getOriginalTopic()).isEqualTo("topic1");
    }

    @Test
    @DisplayName("Should find recent failures by topic")
    void shouldFindRecentFailuresByTopic() {
        // Given
        String topic = "analytics.events";
        LocalDateTime recent = LocalDateTime.now().minusHours(2);
        LocalDateTime old = LocalDateTime.now().minusHours(26);

        DlqMessage recentFailure = createTestMessage(topic, DlqMessage.DlqStatus.FAILED);
        recentFailure.setReceivedAt(recent);
        repository.save(recentFailure);

        DlqMessage oldFailure = createTestMessage(topic, DlqMessage.DlqStatus.FAILED);
        oldFailure.setReceivedAt(old);
        repository.save(oldFailure);

        DlqMessage differentTopic = createTestMessage("other.topic", DlqMessage.DlqStatus.FAILED);
        differentTopic.setReceivedAt(recent);
        repository.save(differentTopic);

        // When
        LocalDateTime since = LocalDateTime.now().minusHours(24);
        List<DlqMessage> recentFailures = repository.findRecentFailuresByTopic(topic, since);

        // Then
        assertThat(recentFailures).hasSize(1);
        assertThat(recentFailures.get(0).getOriginalTopic()).isEqualTo(topic);
    }

    @Test
    @DisplayName("Should handle optimistic locking")
    void shouldHandleOptimisticLocking() {
        // Given
        DlqMessage message = createTestMessage("test.topic", DlqMessage.DlqStatus.PENDING_REVIEW);
        DlqMessage saved = repository.save(message);

        // When
        saved.incrementRetryCount();
        repository.save(saved);

        DlqMessage updated = repository.findById(saved.getId()).orElseThrow();

        // Then
        assertThat(updated.getVersion()).isEqualTo(1L);
        assertThat(updated.getRetryCount()).isEqualTo(1);
    }

    @Test
    @DisplayName("Should get status statistics")
    void shouldGetStatusStatistics() {
        // Given
        repository.save(createTestMessage("topic1", DlqMessage.DlqStatus.PENDING_REVIEW));
        repository.save(createTestMessage("topic2", DlqMessage.DlqStatus.PENDING_REVIEW));
        repository.save(createTestMessage("topic3", DlqMessage.DlqStatus.RECOVERED));
        repository.save(createTestMessage("topic4", DlqMessage.DlqStatus.FAILED));

        // When
        List<Object[]> stats = repository.getStatusStatistics();

        // Then
        assertThat(stats).isNotEmpty();
        assertThat(stats).hasSizeGreaterThanOrEqualTo(3);
    }

    // Helper method to create test DLQ message
    private DlqMessage createTestMessage(String topic, DlqMessage.DlqStatus status) {
        return DlqMessage.builder()
            .originalTopic(topic)
            .dlqTopic(topic + ".dlq")
            .messageKey(UUID.randomUUID().toString())
            .messageValue("{\"test\":\"data\"}")
            .correlationId(UUID.randomUUID().toString())
            .status(status)
            .severity(DlqMessage.Severity.MEDIUM)
            .failureReason("Test failure reason")
            .retryCount(0)
            .maxRetryAttempts(3)
            .build();
    }
}
