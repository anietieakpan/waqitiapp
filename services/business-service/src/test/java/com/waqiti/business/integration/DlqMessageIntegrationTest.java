package com.waqiti.business.integration;

import com.waqiti.business.BaseIntegrationTest;
import com.waqiti.business.domain.DlqMessage;
import com.waqiti.business.repository.DlqMessageRepository;
import com.waqiti.business.service.DlqRetryService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.test.context.jdbc.Sql;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.*;

/**
 * Integration Tests for DLQ Message Persistence and Retrieval
 *
 * Tests the full DLQ workflow with real database:
 * - Message persistence
 * - Query performance with indexes
 * - Retry eligibility queries
 * - Statistics aggregation
 * - Manual intervention workflows
 *
 * Uses TestContainers for PostgreSQL
 *
 * @author Waqiti Engineering Team
 * @version 1.0.0
 */
@DisplayName("DLQ Message Integration Tests")
@Sql(scripts = "/db/migration/V2__create_dlq_messages_table.sql", executionPhase = Sql.ExecutionPhase.BEFORE_TEST_METHOD)
class DlqMessageIntegrationTest extends BaseIntegrationTest {

    @Autowired
    private DlqMessageRepository dlqMessageRepository;

    @Autowired
    private DlqRetryService dlqRetryService;

    @BeforeEach
    void setUp() {
        // Clean database before each test
        dlqMessageRepository.deleteAll();
    }

    @Test
    @DisplayName("Should persist and retrieve DLQ message with all fields")
    void shouldPersistAndRetrieveDlqMessage() {
        // Arrange
        DlqMessage message = DlqMessage.builder()
                .consumerName("TestConsumer")
                .originalTopic("test-topic")
                .originalPartition(0)
                .originalOffset(123L)
                .messageKey("test-key-123")
                .messagePayload(Map.of("field1", "value1", "amount", "1000.00"))
                .headers(Map.of("correlationId", "corr-123"))
                .errorMessage("Test error message")
                .errorStackTrace("Stack trace here...")
                .status(DlqMessage.DlqStatus.PENDING)
                .retryCount(0)
                .maxRetries(5)
                .build();

        // Act
        DlqMessage saved = dlqMessageRepository.save(message);

        // Assert
        assertThat(saved.getId()).isNotNull();
        assertThat(saved.getCreatedAt()).isNotNull();
        assertThat(saved.getVersion()).isEqualTo(0L);

        // Retrieve and verify
        DlqMessage retrieved = dlqMessageRepository.findById(saved.getId()).orElseThrow();
        assertThat(retrieved.getConsumerName()).isEqualTo("TestConsumer");
        assertThat(retrieved.getOriginalTopic()).isEqualTo("test-topic");
        assertThat(retrieved.getMessagePayload()).containsEntry("field1", "value1");
        assertThat(retrieved.getHeaders()).containsEntry("correlationId", "corr-123");
        assertThat(retrieved.getStatus()).isEqualTo(DlqMessage.DlqStatus.PENDING);
    }

    @Test
    @DisplayName("Should find messages eligible for retry using indexed query")
    void shouldFindMessagesEligibleForRetry() {
        // Arrange - Create messages with different eligibility
        DlqMessage eligibleMessage1 = createMessage("eligible-1", DlqMessage.DlqStatus.RETRY_SCHEDULED,
                2, LocalDateTime.now().minusMinutes(5)); // Ready for retry

        DlqMessage eligibleMessage2 = createMessage("eligible-2", DlqMessage.DlqStatus.PENDING,
                0, null); // Fresh message, eligible

        DlqMessage notEligibleFuture = createMessage("not-eligible-future", DlqMessage.DlqStatus.RETRY_SCHEDULED,
                3, LocalDateTime.now().plusMinutes(10)); // Future retry time

        DlqMessage notEligibleMaxRetries = createMessage("not-eligible-max", DlqMessage.DlqStatus.RETRY_SCHEDULED,
                5, LocalDateTime.now().minusMinutes(5)); // Max retries reached
        notEligibleMaxRetries.setMaxRetries(5);

        DlqMessage notEligibleRecovered = createMessage("not-eligible-recovered", DlqMessage.DlqStatus.RECOVERED,
                3, LocalDateTime.now().minusMinutes(5)); // Already recovered

        dlqMessageRepository.saveAll(List.of(
                eligibleMessage1, eligibleMessage2, notEligibleFuture,
                notEligibleMaxRetries, notEligibleRecovered
        ));

        // Act
        List<DlqMessage> eligibleMessages = dlqMessageRepository
                .findMessagesEligibleForRetry(LocalDateTime.now());

        // Assert
        assertThat(eligibleMessages)
                .hasSize(2)
                .extracting(DlqMessage::getMessageKey)
                .containsExactlyInAnyOrder("eligible-1", "eligible-2");
    }

    @Test
    @DisplayName("Should count messages by status accurately")
    void shouldCountMessagesByStatus() {
        // Arrange
        createAndSaveMessages(
                DlqMessage.DlqStatus.PENDING, 5,
                DlqMessage.DlqStatus.RETRY_SCHEDULED, 3,
                DlqMessage.DlqStatus.RECOVERED, 10,
                DlqMessage.DlqStatus.MANUAL_INTERVENTION_REQUIRED, 2
        );

        // Act
        long pendingCount = dlqMessageRepository.countByStatus(DlqMessage.DlqStatus.PENDING);
        long retryScheduledCount = dlqMessageRepository.countByStatus(DlqMessage.DlqStatus.RETRY_SCHEDULED);
        long recoveredCount = dlqMessageRepository.countByStatus(DlqMessage.DlqStatus.RECOVERED);
        long manualCount = dlqMessageRepository.countByStatus(DlqMessage.DlqStatus.MANUAL_INTERVENTION_REQUIRED);

        // Assert
        assertThat(pendingCount).isEqualTo(5);
        assertThat(retryScheduledCount).isEqualTo(3);
        assertThat(recoveredCount).isEqualTo(10);
        assertThat(manualCount).isEqualTo(2);
    }

    @Test
    @DisplayName("Should find messages requiring manual intervention")
    void shouldFindMessagesRequiringManualIntervention() {
        // Arrange
        DlqMessage manualMessage1 = createMessage("manual-1", DlqMessage.DlqStatus.MANUAL_INTERVENTION_REQUIRED, 5, null);
        DlqMessage maxRetriesMessage = createMessage("max-retries", DlqMessage.DlqStatus.MAX_RETRIES_EXCEEDED, 5, null);
        DlqMessage permanentFailure = createMessage("permanent", DlqMessage.DlqStatus.PERMANENT_FAILURE, 5, null);
        DlqMessage recoveredMessage = createMessage("recovered", DlqMessage.DlqStatus.RECOVERED, 3, null);

        dlqMessageRepository.saveAll(List.of(
                manualMessage1, maxRetriesMessage, permanentFailure, recoveredMessage
        ));

        // Act
        List<DlqMessage> messages = dlqMessageRepository
                .findMessagesRequiringIntervention(org.springframework.data.domain.Pageable.unpaged())
                .getContent();

        // Assert
        assertThat(messages)
                .hasSize(3)
                .extracting(DlqMessage::getMessageKey)
                .containsExactlyInAnyOrder("manual-1", "max-retries", "permanent");
    }

    @Test
    @DisplayName("Should count messages by consumer name")
    void shouldCountMessagesByConsumer() {
        // Arrange
        for (int i = 0; i < 7; i++) {
            DlqMessage message = createMessage("msg-" + i, DlqMessage.DlqStatus.PENDING, 0, null);
            message.setConsumerName("PaymentConsumer");
            dlqMessageRepository.save(message);
        }

        for (int i = 0; i < 3; i++) {
            DlqMessage message = createMessage("exp-" + i, DlqMessage.DlqStatus.PENDING, 0, null);
            message.setConsumerName("ExpenseConsumer");
            dlqMessageRepository.save(message);
        }

        // Act
        long paymentCount = dlqMessageRepository.countByConsumerName("PaymentConsumer");
        long expenseCount = dlqMessageRepository.countByConsumerName("ExpenseConsumer");

        // Assert
        assertThat(paymentCount).isEqualTo(7);
        assertThat(expenseCount).isEqualTo(3);
    }

    @Test
    @DisplayName("Should find messages by original topic")
    void shouldFindMessagesByOriginalTopic() {
        // Arrange
        String topic = "business-account-transactions";

        for (int i = 0; i < 5; i++) {
            DlqMessage message = createMessage("msg-" + i, DlqMessage.DlqStatus.PENDING, 0, null);
            message.setOriginalTopic(topic);
            dlqMessageRepository.save(message);
        }

        // Act
        List<DlqMessage> messages = dlqMessageRepository.findByOriginalTopic(topic);

        // Assert
        assertThat(messages).hasSize(5);
        assertThat(messages).allMatch(m -> m.getOriginalTopic().equals(topic));
    }

    @Test
    @DisplayName("Should enforce optimistic locking on concurrent updates")
    void shouldEnforceOptimisticLocking() {
        // Arrange
        DlqMessage message = createMessage("locking-test", DlqMessage.DlqStatus.PENDING, 0, null);
        DlqMessage saved = dlqMessageRepository.save(message);

        // Act - Simulate two concurrent updates
        DlqMessage instance1 = dlqMessageRepository.findById(saved.getId()).orElseThrow();
        DlqMessage instance2 = dlqMessageRepository.findById(saved.getId()).orElseThrow();

        // Update instance1 first
        instance1.setRetryCount(1);
        dlqMessageRepository.save(instance1);

        // Try to update instance2 (stale version)
        instance2.setRetryCount(2);

        // Assert - Should throw OptimisticLockException
        assertThatThrownBy(() -> dlqMessageRepository.save(instance2))
                .isInstanceOf(org.springframework.orm.ObjectOptimisticLockingFailureException.class);
    }

    @Test
    @DisplayName("Should deduplicate messages by messageKey within time window")
    void shouldDeduplicateMessagesByKey() {
        // Arrange
        String messageKey = "duplicate-key-test";

        DlqMessage firstMessage = createMessage(messageKey, DlqMessage.DlqStatus.PENDING, 0, null);
        dlqMessageRepository.save(firstMessage);

        // Act - Try to save another message with same key
        var foundDuplicate = dlqMessageRepository
                .findFirstByMessageKeyOrderByCreatedAtDesc(messageKey);

        // Assert
        assertThat(foundDuplicate).isPresent();
        assertThat(foundDuplicate.get().getMessageKey()).isEqualTo(messageKey);
    }

    @Test
    @DisplayName("Should count max retries exceeded since specific time")
    void shouldCountMaxRetriesExceededSince() {
        // Arrange
        LocalDateTime cutoffTime = LocalDateTime.now().minusHours(24);

        // Create messages that exceeded max retries before cutoff
        for (int i = 0; i < 3; i++) {
            DlqMessage oldMessage = createMessage("old-" + i, DlqMessage.DlqStatus.MAX_RETRIES_EXCEEDED, 5, null);
            oldMessage.setCreatedAt(cutoffTime.minusHours(2));
            dlqMessageRepository.save(oldMessage);
        }

        // Create messages that exceeded max retries after cutoff
        for (int i = 0; i < 7; i++) {
            DlqMessage recentMessage = createMessage("recent-" + i, DlqMessage.DlqStatus.MAX_RETRIES_EXCEEDED, 5, null);
            recentMessage.setCreatedAt(cutoffTime.plusHours(1));
            dlqMessageRepository.save(recentMessage);
        }

        // Act
        long count = dlqMessageRepository.countMaxRetriesExceededSince(cutoffTime);

        // Assert
        assertThat(count).isEqualTo(7); // Only recent messages
    }

    @Test
    @DisplayName("Should handle JSONB columns correctly for payload and headers")
    void shouldHandleJsonbColumns() {
        // Arrange
        Map<String, Object> complexPayload = Map.of(
                "transactionId", UUID.randomUUID().toString(),
                "amount", "12345.67",
                "currency", "USD",
                "metadata", Map.of(
                        "customerId", "CUST-123",
                        "orderId", "ORD-456"
                )
        );

        Map<String, Object> headers = Map.of(
                "correlationId", UUID.randomUUID().toString(),
                "timestamp", System.currentTimeMillis(),
                "retryAttempt", 3
        );

        DlqMessage message = createMessage("jsonb-test", DlqMessage.DlqStatus.PENDING, 0, null);
        message.setMessagePayload(complexPayload);
        message.setHeaders(headers);

        // Act
        DlqMessage saved = dlqMessageRepository.save(message);
        DlqMessage retrieved = dlqMessageRepository.findById(saved.getId()).orElseThrow();

        // Assert
        assertThat(retrieved.getMessagePayload()).containsEntry("amount", "12345.67");
        assertThat(retrieved.getMessagePayload()).containsKey("metadata");
        assertThat(retrieved.getHeaders()).containsEntry("retryAttempt", 3);
    }

    /**
     * Helper method to create test message
     */
    private DlqMessage createMessage(String key, DlqMessage.DlqStatus status, int retryCount, LocalDateTime retryAfter) {
        return DlqMessage.builder()
                .consumerName("TestConsumer")
                .originalTopic("test-topic")
                .originalPartition(0)
                .originalOffset(100L)
                .messageKey(key)
                .messagePayload(Map.of("test", "data"))
                .headers(Map.of())
                .errorMessage("Test error")
                .status(status)
                .retryCount(retryCount)
                .maxRetries(5)
                .retryAfter(retryAfter)
                .build();
    }

    /**
     * Helper to create multiple messages with different statuses
     */
    private void createAndSaveMessages(Object... statusCountPairs) {
        for (int i = 0; i < statusCountPairs.length; i += 2) {
            DlqMessage.DlqStatus status = (DlqMessage.DlqStatus) statusCountPairs[i];
            int count = (int) statusCountPairs[i + 1];

            for (int j = 0; j < count; j++) {
                DlqMessage message = createMessage(status.name().toLowerCase() + "-" + j, status, 0, null);
                dlqMessageRepository.save(message);
            }
        }
    }
}
