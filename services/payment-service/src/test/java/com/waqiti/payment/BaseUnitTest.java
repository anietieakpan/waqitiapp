package com.waqiti.payment;

import com.waqiti.payment.config.TestConfig;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.junit.jupiter.SpringExtension;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDateTime;
import java.util.UUID;

/**
 * Base class for all unit tests in payment-service
 *
 * PRODUCTION READINESS FIX:
 * Comprehensive test suite implementation to achieve 80% code coverage
 *
 * Provides:
 * - Mockito integration for mocking dependencies
 * - Test configuration setup
 * - Common test utilities and helpers
 * - Spring Boot test context (lightweight for fast tests)
 *
 * @author Waqiti Engineering Team
 * @since 1.0.0
 */
@ExtendWith({SpringExtension.class, MockitoExtension.class})
@SpringBootTest(classes = TestConfig.class, webEnvironment = SpringBootTest.WebEnvironment.NONE)
@ActiveProfiles("test")
public abstract class BaseUnitTest {

    @BeforeEach
    public void baseSetUp() {
        // Common setup for all unit tests
        // Mocks are automatically reset by MockitoExtension
    }

    // ================================================================
    // TEST DATA BUILDERS
    // ================================================================

    /**
     * Create a test UUID with predictable value for assertions
     * Uses sequential UUIDs for easy debugging
     */
    protected UUID testUUID(int sequence) {
        return UUID.fromString(String.format("00000000-0000-0000-0000-%012d", sequence));
    }

    /**
     * Create a test BigDecimal for monetary values
     * Always uses HALF_EVEN rounding (banker's rounding) and scale=4
     */
    protected BigDecimal testAmount(String value) {
        return new BigDecimal(value).setScale(4, RoundingMode.HALF_EVEN);
    }

    /**
     * Create a test timestamp with predictable value
     */
    protected LocalDateTime testTimestamp() {
        return LocalDateTime.of(2025, 1, 1, 12, 0, 0);
    }

    /**
     * Create a test email address
     */
    protected String testEmail(String prefix) {
        return prefix + "@test.waqiti.com";
    }

    /**
     * Create a test idempotency key
     */
    protected String testIdempotencyKey() {
        return "test-idempotency-" + UUID.randomUUID().toString();
    }
}
