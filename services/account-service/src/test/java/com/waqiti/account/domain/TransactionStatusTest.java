package com.waqiti.account.domain;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.EnumSource;
import org.junit.jupiter.params.provider.MethodSource;

import java.util.Arrays;
import java.util.stream.Stream;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.*;

/**
 * Unit tests for TransactionStatus enum
 *
 * @author Waqiti Platform Team
 */
class TransactionStatusTest {

    @Test
    void shouldHaveAllExpectedStatuses() {
        // Given expected statuses
        var expectedStatuses = Arrays.asList(
            "INITIATED", "PENDING", "PROCESSING", "COMPLETED",
            "FAILED", "CANCELLED", "REVERSED", "ON_HOLD", "TIMEOUT"
        );

        // When getting all status names
        var actualStatuses = Arrays.stream(TransactionStatus.values())
            .map(Enum::name)
            .toList();

        // Then all expected statuses should exist
        assertThat(actualStatuses).containsExactlyInAnyOrderElementsOf(expectedStatuses);
    }

    @ParameterizedTest
    @EnumSource(value = TransactionStatus.class, names = {"COMPLETED", "FAILED", "CANCELLED", "REVERSED", "TIMEOUT"})
    void shouldIdentifyTerminalStatuses(TransactionStatus status) {
        // Then terminal statuses should be identified correctly
        assertTrue(status.isTerminal(), status + " should be terminal");
    }

    @ParameterizedTest
    @EnumSource(value = TransactionStatus.class, names = {"INITIATED", "PENDING", "PROCESSING", "ON_HOLD"})
    void shouldIdentifyNonTerminalStatuses(TransactionStatus status) {
        // Then non-terminal statuses should be identified correctly
        assertFalse(status.isTerminal(), status + " should not be terminal");
    }

    @ParameterizedTest
    @EnumSource(value = TransactionStatus.class, names = {"INITIATED", "PENDING", "ON_HOLD"})
    void shouldIdentifyCancellableStatuses(TransactionStatus status) {
        // Then cancellable statuses should be identified correctly
        assertTrue(status.isCancellable(), status + " should be cancellable");
    }

    @ParameterizedTest
    @EnumSource(value = TransactionStatus.class, names = {"PROCESSING", "COMPLETED", "FAILED", "CANCELLED", "REVERSED", "TIMEOUT"})
    void shouldIdentifyNonCancellableStatuses(TransactionStatus status) {
        // Then non-cancellable statuses should be identified correctly
        assertFalse(status.isCancellable(), status + " should not be cancellable");
    }

    @ParameterizedTest
    @EnumSource(value = TransactionStatus.class, names = {"INITIATED", "PENDING", "PROCESSING", "ON_HOLD"})
    void shouldIdentifyActiveStatuses(TransactionStatus status) {
        // Then active statuses should be identified correctly
        assertTrue(status.isActive(), status + " should be active");
    }

    @ParameterizedTest
    @EnumSource(value = TransactionStatus.class, names = {"COMPLETED", "FAILED", "CANCELLED", "REVERSED", "TIMEOUT"})
    void shouldIdentifyInactiveStatuses(TransactionStatus status) {
        // Then inactive statuses should be identified correctly
        assertFalse(status.isActive(), status + " should not be active");
    }

    @ParameterizedTest
    @MethodSource("provideValidTransitions")
    void shouldAllowValidStatusTransitions(TransactionStatus from, TransactionStatus to) {
        // When checking valid transition
        boolean canTransition = from.canTransitionTo(to);

        // Then transition should be allowed
        assertTrue(canTransition,
            String.format("Should allow transition from %s to %s", from, to));
    }

    @ParameterizedTest
    @MethodSource("provideInvalidTransitions")
    void shouldRejectInvalidStatusTransitions(TransactionStatus from, TransactionStatus to) {
        // When checking invalid transition
        boolean canTransition = from.canTransitionTo(to);

        // Then transition should be rejected
        assertFalse(canTransition,
            String.format("Should reject transition from %s to %s", from, to));
    }

    @Test
    void shouldNotAllowTransitionFromTerminalStatus() {
        // Given a terminal status
        TransactionStatus terminal = TransactionStatus.COMPLETED;

        // When trying to transition to any other status
        for (TransactionStatus target : TransactionStatus.values()) {
            if (target != terminal) {
                // Then transition should not be allowed
                assertFalse(terminal.canTransitionTo(target),
                    "Terminal status should not transition to " + target);
            }
        }
    }

    @Test
    void shouldAllowSelfTransition() {
        // Given any status
        for (TransactionStatus status : TransactionStatus.values()) {
            // When checking self-transition
            boolean canTransition = status.canTransitionTo(status);

            // Then self-transition should be allowed
            assertTrue(canTransition, status + " should allow self-transition");
        }
    }

    @Test
    void shouldHaveNonNullDescriptions() {
        // When checking all statuses
        for (TransactionStatus status : TransactionStatus.values()) {
            // Then description should not be null or empty
            assertNotNull(status.getDescription(), status + " should have description");
            assertFalse(status.getDescription().isBlank(), status + " description should not be blank");
        }
    }

    @Test
    void initiatedShouldTransitionToPendingOrProcessing() {
        // Given INITIATED status
        TransactionStatus initiated = TransactionStatus.INITIATED;

        // Then should transition to PENDING, PROCESSING, or cancellation states
        assertTrue(initiated.canTransitionTo(TransactionStatus.PENDING));
        assertTrue(initiated.canTransitionTo(TransactionStatus.PROCESSING));
        assertTrue(initiated.canTransitionTo(TransactionStatus.CANCELLED));
        assertTrue(initiated.canTransitionTo(TransactionStatus.FAILED));
    }

    @Test
    void processingCannotBeCancelled() {
        // Given PROCESSING status
        TransactionStatus processing = TransactionStatus.PROCESSING;

        // Then should not be cancellable
        assertFalse(processing.isCancellable());
        assertFalse(processing.canTransitionTo(TransactionStatus.CANCELLED));
    }

    @Test
    void completedIsTerminalAndNotCancellable() {
        // Given COMPLETED status
        TransactionStatus completed = TransactionStatus.COMPLETED;

        // Then should be terminal and not cancellable
        assertTrue(completed.isTerminal());
        assertFalse(completed.isCancellable());
        assertFalse(completed.isActive());
    }

    // ========== TEST DATA PROVIDERS ==========

    private static Stream<Arguments> provideValidTransitions() {
        return Stream.of(
            // INITIATED transitions
            Arguments.of(TransactionStatus.INITIATED, TransactionStatus.PENDING),
            Arguments.of(TransactionStatus.INITIATED, TransactionStatus.PROCESSING),
            Arguments.of(TransactionStatus.INITIATED, TransactionStatus.CANCELLED),
            Arguments.of(TransactionStatus.INITIATED, TransactionStatus.FAILED),

            // PENDING transitions
            Arguments.of(TransactionStatus.PENDING, TransactionStatus.PROCESSING),
            Arguments.of(TransactionStatus.PENDING, TransactionStatus.ON_HOLD),
            Arguments.of(TransactionStatus.PENDING, TransactionStatus.CANCELLED),
            Arguments.of(TransactionStatus.PENDING, TransactionStatus.FAILED),
            Arguments.of(TransactionStatus.PENDING, TransactionStatus.TIMEOUT),

            // PROCESSING transitions
            Arguments.of(TransactionStatus.PROCESSING, TransactionStatus.COMPLETED),
            Arguments.of(TransactionStatus.PROCESSING, TransactionStatus.FAILED),
            Arguments.of(TransactionStatus.PROCESSING, TransactionStatus.TIMEOUT),

            // ON_HOLD transitions
            Arguments.of(TransactionStatus.ON_HOLD, TransactionStatus.PENDING),
            Arguments.of(TransactionStatus.ON_HOLD, TransactionStatus.PROCESSING),
            Arguments.of(TransactionStatus.ON_HOLD, TransactionStatus.CANCELLED),
            Arguments.of(TransactionStatus.ON_HOLD, TransactionStatus.FAILED),

            // COMPLETED can be reversed
            Arguments.of(TransactionStatus.COMPLETED, TransactionStatus.REVERSED),

            // Self-transitions
            Arguments.of(TransactionStatus.INITIATED, TransactionStatus.INITIATED),
            Arguments.of(TransactionStatus.PENDING, TransactionStatus.PENDING),
            Arguments.of(TransactionStatus.PROCESSING, TransactionStatus.PROCESSING)
        );
    }

    private static Stream<Arguments> provideInvalidTransitions() {
        return Stream.of(
            // Cannot transition from terminal statuses (except COMPLETED → REVERSED)
            Arguments.of(TransactionStatus.FAILED, TransactionStatus.COMPLETED),
            Arguments.of(TransactionStatus.FAILED, TransactionStatus.PROCESSING),
            Arguments.of(TransactionStatus.CANCELLED, TransactionStatus.COMPLETED),
            Arguments.of(TransactionStatus.CANCELLED, TransactionStatus.PROCESSING),
            Arguments.of(TransactionStatus.TIMEOUT, TransactionStatus.COMPLETED),
            Arguments.of(TransactionStatus.REVERSED, TransactionStatus.COMPLETED),

            // Cannot go backwards from PROCESSING
            Arguments.of(TransactionStatus.PROCESSING, TransactionStatus.INITIATED),
            Arguments.of(TransactionStatus.PROCESSING, TransactionStatus.PENDING),
            Arguments.of(TransactionStatus.PROCESSING, TransactionStatus.CANCELLED),

            // Cannot go to COMPLETED from non-PROCESSING states
            Arguments.of(TransactionStatus.INITIATED, TransactionStatus.COMPLETED),
            Arguments.of(TransactionStatus.PENDING, TransactionStatus.COMPLETED),
            Arguments.of(TransactionStatus.ON_HOLD, TransactionStatus.COMPLETED),

            // Cannot reverse non-COMPLETED transactions
            Arguments.of(TransactionStatus.INITIATED, TransactionStatus.REVERSED),
            Arguments.of(TransactionStatus.PENDING, TransactionStatus.REVERSED),
            Arguments.of(TransactionStatus.PROCESSING, TransactionStatus.REVERSED),
            Arguments.of(TransactionStatus.FAILED, TransactionStatus.REVERSED)
        );
    }
}
