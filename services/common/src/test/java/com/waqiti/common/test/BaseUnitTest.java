package com.waqiti.common.test;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.context.ActiveProfiles;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneId;

/**
 * Base class for unit tests in the Waqiti fintech application.
 *
 * Provides common setup and utilities for isolated unit testing with:
 * - Mockito integration for mocking dependencies
 * - Fixed clock for deterministic time-based tests
 * - Test lifecycle hooks
 * - Common test utilities
 *
 * Usage:
 * <pre>
 * {@code
 * class PaymentServiceTest extends BaseUnitTest {
 *     @Mock
 *     private PaymentRepository paymentRepository;
 *
 *     @InjectMocks
 *     private PaymentService paymentService;
 *
 *     @Test
 *     void shouldProcessPayment() {
 *         // Test implementation
 *     }
 * }
 * }
 * </pre>
 *
 * @author Waqiti Engineering Team
 * @version 1.0
 * @since 2024-10-19
 */
@ExtendWith(MockitoExtension.class)
@ActiveProfiles("test")
public abstract class BaseUnitTest {

    /**
     * Fixed clock for deterministic time-based testing.
     * Set to a specific instant to ensure reproducible tests.
     */
    protected Clock fixedClock;

    /**
     * Fixed instant for testing (2024-10-19 12:00:00 UTC)
     */
    protected Instant fixedInstant;

    /**
     * Test execution start time for performance tracking
     */
    private long startTime;

    /**
     * Set up test fixtures before each test.
     * Initializes the fixed clock and test utilities.
     */
    @BeforeEach
    public void setUpBaseTest() {
        startTime = System.currentTimeMillis();

        // Initialize fixed clock for deterministic time-based tests
        fixedInstant = Instant.parse("2024-10-19T12:00:00Z");
        fixedClock = Clock.fixed(fixedInstant, ZoneId.of("UTC"));

        // Additional setup hook for subclasses
        setUp();
    }

    /**
     * Clean up after each test.
     * Tracks test execution time and performs cleanup.
     */
    @AfterEach
    public void tearDownBaseTest() {
        long duration = System.currentTimeMillis() - startTime;
        if (duration > 1000) {
            System.out.println("WARNING: Unit test took " + duration + "ms - consider optimization");
        }

        // Additional teardown hook for subclasses
        tearDown();
    }

    /**
     * Hook for subclasses to perform additional setup.
     * Override this method to add custom setup logic.
     */
    protected void setUp() {
        // Override in subclasses if needed
    }

    /**
     * Hook for subclasses to perform additional teardown.
     * Override this method to add custom cleanup logic.
     */
    protected void tearDown() {
        // Override in subclasses if needed
    }

    /**
     * Get the current time from the fixed clock.
     *
     * @return Current instant from fixed clock
     */
    protected Instant now() {
        return fixedClock.instant();
    }

    /**
     * Advance the fixed clock by specified seconds.
     * Useful for testing time-dependent behavior.
     *
     * @param seconds Seconds to advance
     */
    protected void advanceClockBySeconds(long seconds) {
        fixedInstant = fixedInstant.plusSeconds(seconds);
        fixedClock = Clock.fixed(fixedInstant, ZoneId.of("UTC"));
    }

    /**
     * Advance the fixed clock by specified minutes.
     *
     * @param minutes Minutes to advance
     */
    protected void advanceClockByMinutes(long minutes) {
        advanceClockBySeconds(minutes * 60);
    }

    /**
     * Advance the fixed clock by specified hours.
     *
     * @param hours Hours to advance
     */
    protected void advanceClockByHours(long hours) {
        advanceClockByMinutes(hours * 60);
    }

    /**
     * Advance the fixed clock by specified days.
     *
     * @param days Days to advance
     */
    protected void advanceClockByDays(long days) {
        advanceClockByHours(days * 24);
    }

    /**
     * Reset the clock to the initial fixed time.
     */
    protected void resetClock() {
        fixedInstant = Instant.parse("2024-10-19T12:00:00Z");
        fixedClock = Clock.fixed(fixedInstant, ZoneId.of("UTC"));
    }

    /**
     * Sleep for a short duration in tests.
     * Use sparingly - prefer deterministic testing over sleep.
     *
     * @param millis Milliseconds to sleep
     */
    protected void sleepMillis(long millis) {
        try {
            Thread.sleep(millis);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new RuntimeException("Test interrupted", e);
        }
    }

    /**
     * Print a test message for debugging.
     *
     * @param message Message to print
     */
    protected void log(String message) {
        System.out.println("[TEST] " + message);
    }

    /**
     * Print a test message with format arguments.
     *
     * @param format Format string
     * @param args Format arguments
     */
    protected void log(String format, Object... args) {
        System.out.println("[TEST] " + String.format(format, args));
    }
}
