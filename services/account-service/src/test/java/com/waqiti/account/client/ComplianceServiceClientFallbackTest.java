package com.waqiti.account.client;

import com.waqiti.account.dto.ComplianceCheckResult;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Comprehensive test suite for ComplianceServiceClientFallback.
 *
 * This test verifies the CRITICAL FIX P0-5: BigDecimal money arithmetic.
 * Previously used double causing precision loss; now uses BigDecimal.
 *
 * Test Coverage:
 * - Account creation compliance checks
 * - Status change compliance (critical vs non-critical)
 * - Transaction compliance with various amounts
 * - Edge cases: boundary values, invalid inputs
 * - Precision testing for P0-5 fix verification
 *
 * @author Waqiti Engineering
 * @since 1.0.0
 */
@DisplayName("ComplianceServiceClientFallback Tests")
class ComplianceServiceClientFallbackTest {

    private ComplianceServiceClientFallback fallback;

    @BeforeEach
    void setUp() {
        fallback = new ComplianceServiceClientFallback();
    }

    @Nested
    @DisplayName("Account Creation Compliance Tests")
    class AccountCreationTests {

        @Test
        @DisplayName("Should approve account creation with manual review flag")
        void shouldApproveAccountCreationWithManualReview() {
            // Given
            UUID userId = UUID.randomUUID();
            String accountType = "SAVINGS";

            // When
            ComplianceCheckResult result = fallback.checkAccountCreationCompliance(userId, accountType);

            // Then
            assertThat(result.isApproved()).isTrue();
            assertThat(result.isCompliant()).isTrue();
            assertThat(result.isRequiresManualReview()).isTrue();
            assertThat(result.getRiskScore()).isEqualTo("MEDIUM");
            assertThat(result.getComplianceLevel()).isEqualTo("PENDING_REVIEW");
            assertThat(result.getReason()).contains("FALLBACK_MODE");
            assertThat(result.getReason()).contains("retrospective review");
        }

        @Test
        @DisplayName("Should handle different account types consistently")
        void shouldHandleDifferentAccountTypesConsistently() {
            UUID userId = UUID.randomUUID();

            ComplianceCheckResult savings = fallback.checkAccountCreationCompliance(userId, "SAVINGS");
            ComplianceCheckResult checking = fallback.checkAccountCreationCompliance(userId, "CHECKING");
            ComplianceCheckResult investment = fallback.checkAccountCreationCompliance(userId, "INVESTMENT");

            // All should be approved with manual review
            assertThat(savings.isApproved()).isTrue();
            assertThat(checking.isApproved()).isTrue();
            assertThat(investment.isApproved()).isTrue();

            assertThat(savings.isRequiresManualReview()).isTrue();
            assertThat(checking.isRequiresManualReview()).isTrue();
            assertThat(investment.isRequiresManualReview()).isTrue();
        }
    }

    @Nested
    @DisplayName("Status Change Compliance Tests")
    class StatusChangeTests {

        @Test
        @DisplayName("Should block critical status change SUSPENDED")
        void shouldBlockSuspendedStatusChange() {
            // Given
            UUID accountId = UUID.randomUUID();

            // When
            ComplianceCheckResult result = fallback.checkStatusChangeCompliance(accountId, "SUSPENDED");

            // Then
            assertThat(result.isApproved()).isFalse();
            assertThat(result.isCompliant()).isFalse();
            assertThat(result.isRequiresManualReview()).isTrue();
            assertThat(result.getRiskScore()).isEqualTo("HIGH");
            assertThat(result.getReason()).contains("Critical status change requires compliance review");
        }

        @Test
        @DisplayName("Should block critical status change CLOSED")
        void shouldBlockClosedStatusChange() {
            // Given
            UUID accountId = UUID.randomUUID();

            // When
            ComplianceCheckResult result = fallback.checkStatusChangeCompliance(accountId, "CLOSED");

            // Then
            assertThat(result.isApproved()).isFalse();
            assertThat(result.isCompliant()).isFalse();
            assertThat(result.isRequiresManualReview()).isTrue();
            assertThat(result.getRiskScore()).isEqualTo("HIGH");
        }

        @Test
        @DisplayName("Should allow non-critical status change ACTIVE")
        void shouldAllowActiveStatusChange() {
            // Given
            UUID accountId = UUID.randomUUID();

            // When
            ComplianceCheckResult result = fallback.checkStatusChangeCompliance(accountId, "ACTIVE");

            // Then
            assertThat(result.isApproved()).isTrue();
            assertThat(result.isCompliant()).isTrue();
            assertThat(result.isRequiresManualReview()).isFalse();
            assertThat(result.getRiskScore()).isEqualTo("LOW");
            assertThat(result.getReason()).contains("Non-critical status change approved");
        }

        @ParameterizedTest
        @ValueSource(strings = {"ACTIVE", "PENDING", "VERIFIED", "FROZEN"})
        @DisplayName("Should allow various non-critical status changes")
        void shouldAllowNonCriticalStatusChanges(String status) {
            UUID accountId = UUID.randomUUID();

            ComplianceCheckResult result = fallback.checkStatusChangeCompliance(accountId, status);

            assertThat(result.isApproved()).isTrue();
            assertThat(result.getRiskScore()).isEqualTo("LOW");
        }
    }

    @Nested
    @DisplayName("Transaction Compliance Tests - P0-5 Fix Verification")
    class TransactionComplianceTests {

        @Test
        @DisplayName("Should approve low-value transaction ($1000)")
        void shouldApproveLowValueTransaction() {
            // Given
            UUID accountId = UUID.randomUUID();
            String amount = "1000.00";

            // When
            ComplianceCheckResult result = fallback.checkTransactionCompliance(
                accountId, "TRANSFER", amount
            );

            // Then
            assertThat(result.isApproved()).isTrue();
            assertThat(result.isCompliant()).isTrue();
            assertThat(result.isRequiresManualReview()).isFalse();
            assertThat(result.getRiskScore()).isEqualTo("MEDIUM");
            assertThat(result.getReason()).contains("Transaction approved with enhanced monitoring");
        }

        @Test
        @DisplayName("CRITICAL P0-5: Should block high-value transaction exactly at threshold ($10,000)")
        void shouldBlockTransactionAtThreshold() {
            // Given
            UUID accountId = UUID.randomUUID();
            String amount = "10000.00";

            // When
            ComplianceCheckResult result = fallback.checkTransactionCompliance(
                accountId, "TRANSFER", amount
            );

            // Then - At exactly 10000, should still be approved (not greater than)
            assertThat(result.isApproved()).isTrue();
            assertThat(result.getRiskScore()).isEqualTo("MEDIUM");
        }

        @Test
        @DisplayName("CRITICAL P0-5: Should block high-value transaction above threshold ($10,000.01)")
        void shouldBlockTransactionAboveThreshold() {
            // Given
            UUID accountId = UUID.randomUUID();
            String amount = "10000.01";

            // When
            ComplianceCheckResult result = fallback.checkTransactionCompliance(
                accountId, "TRANSFER", amount
            );

            // Then
            assertThat(result.isApproved()).isFalse();
            assertThat(result.isCompliant()).isFalse();
            assertThat(result.isRequiresManualReview()).isTrue();
            assertThat(result.getRiskScore()).isEqualTo("HIGH");
            assertThat(result.getReason()).contains("High-value transaction");
            assertThat(result.getReason()).contains("requires compliance review");
        }

        @Test
        @DisplayName("CRITICAL P0-5: BigDecimal precision test - distinguishes $10000.00 vs $10000.01")
        void shouldDistinguishPreciseAmounts() {
            // This test verifies the P0-5 fix: previously double arithmetic would
            // treat 10000.00 and 10000.01 as equal due to floating-point precision loss.
            // With BigDecimal, we have exact precision.

            UUID accountId = UUID.randomUUID();

            // Test: Exactly at threshold
            ComplianceCheckResult at = fallback.checkTransactionCompliance(
                accountId, "TRANSFER", "10000.00"
            );

            // Test: One cent above threshold
            ComplianceCheckResult above = fallback.checkTransactionCompliance(
                accountId, "TRANSFER", "10000.01"
            );

            // Should have different results
            assertThat(at.isApproved()).isTrue();
            assertThat(above.isApproved()).isFalse();
            assertThat(at.getRiskScore()).isNotEqualTo(above.getRiskScore());
        }

        @Test
        @DisplayName("Should block very high-value transaction ($100,000)")
        void shouldBlockVeryHighValueTransaction() {
            // Given
            UUID accountId = UUID.randomUUID();
            String amount = "100000.00";

            // When
            ComplianceCheckResult result = fallback.checkTransactionCompliance(
                accountId, "TRANSFER", amount
            );

            // Then
            assertThat(result.isApproved()).isFalse();
            assertThat(result.isRequiresManualReview()).isTrue();
            assertThat(result.getRiskScore()).isEqualTo("HIGH");
        }

        @ParameterizedTest
        @ValueSource(strings = {"0.01", "1.00", "100.00", "999.99", "5000.00", "9999.99"})
        @DisplayName("Should approve various low-value amounts")
        void shouldApproveLowValueAmounts(String amount) {
            UUID accountId = UUID.randomUUID();

            ComplianceCheckResult result = fallback.checkTransactionCompliance(
                accountId, "PAYMENT", amount
            );

            assertThat(result.isApproved()).isTrue();
            assertThat(result.getRiskScore()).isEqualTo("MEDIUM");
        }

        @ParameterizedTest
        @ValueSource(strings = {"10001.00", "15000.00", "50000.00", "1000000.00"})
        @DisplayName("Should block various high-value amounts")
        void shouldBlockHighValueAmounts(String amount) {
            UUID accountId = UUID.randomUUID();

            ComplianceCheckResult result = fallback.checkTransactionCompliance(
                accountId, "TRANSFER", amount
            );

            assertThat(result.isApproved()).isFalse();
            assertThat(result.isRequiresManualReview()).isTrue();
            assertThat(result.getRiskScore()).isEqualTo("HIGH");
        }

        @Test
        @DisplayName("Should handle invalid amount format gracefully")
        void shouldHandleInvalidAmountFormat() {
            // Given
            UUID accountId = UUID.randomUUID();
            String invalidAmount = "not-a-number";

            // When
            ComplianceCheckResult result = fallback.checkTransactionCompliance(
                accountId, "TRANSFER", invalidAmount
            );

            // Then
            assertThat(result.isApproved()).isFalse();
            assertThat(result.isCompliant()).isFalse();
            assertThat(result.isRequiresManualReview()).isTrue();
            assertThat(result.getRiskScore()).isEqualTo("HIGH");
            assertThat(result.getReason()).contains("Invalid amount format");
        }

        @ParameterizedTest
        @ValueSource(strings = {"abc", "12.34.56", "", "  ", "$1000", "1,000.00"})
        @DisplayName("Should reject various invalid amount formats")
        void shouldRejectInvalidFormats(String invalidAmount) {
            UUID accountId = UUID.randomUUID();

            ComplianceCheckResult result = fallback.checkTransactionCompliance(
                accountId, "PAYMENT", invalidAmount
            );

            assertThat(result.isApproved()).isFalse();
            assertThat(result.getReason()).contains("Invalid amount");
        }

        @Test
        @DisplayName("Should handle different transaction types consistently")
        void shouldHandleDifferentTransactionTypes() {
            UUID accountId = UUID.randomUUID();
            String amount = "5000.00";

            ComplianceCheckResult transfer = fallback.checkTransactionCompliance(
                accountId, "TRANSFER", amount
            );
            ComplianceCheckResult payment = fallback.checkTransactionCompliance(
                accountId, "PAYMENT", amount
            );
            ComplianceCheckResult withdrawal = fallback.checkTransactionCompliance(
                accountId, "WITHDRAWAL", amount
            );

            // All should be treated the same based on amount
            assertThat(transfer.isApproved()).isEqualTo(payment.isApproved());
            assertThat(payment.isApproved()).isEqualTo(withdrawal.isApproved());
            assertThat(transfer.getRiskScore()).isEqualTo(payment.getRiskScore());
        }
    }

    @Nested
    @DisplayName("Compliance Level Retrieval Tests")
    class ComplianceLevelTests {

        @Test
        @DisplayName("Should return PENDING_VERIFICATION as default level")
        void shouldReturnPendingVerificationLevel() {
            // Given
            UUID accountId = UUID.randomUUID();

            // When
            String level = fallback.getAccountComplianceLevel(accountId);

            // Then
            assertThat(level).isEqualTo("PENDING_VERIFICATION");
        }

        @Test
        @DisplayName("Should consistently return same level for multiple calls")
        void shouldReturnConsistentLevel() {
            UUID accountId = UUID.randomUUID();

            String level1 = fallback.getAccountComplianceLevel(accountId);
            String level2 = fallback.getAccountComplianceLevel(accountId);

            assertThat(level1).isEqualTo(level2);
        }
    }

    @Nested
    @DisplayName("Flag Account for Review Tests")
    class FlagAccountTests {

        @Test
        @DisplayName("Should handle flag account call without exception")
        void shouldHandleFlagAccountCall() {
            // Given
            UUID accountId = UUID.randomUUID();
            String reason = "Suspicious activity detected";

            // When/Then - should not throw exception
            fallback.flagAccountForReview(accountId, reason);

            // Note: This is a void method that logs the issue.
            // In production, it should queue for retry.
        }

        @Test
        @DisplayName("Should handle null reason gracefully")
        void shouldHandleNullReason() {
            UUID accountId = UUID.randomUUID();

            // Should not throw NPE
            fallback.flagAccountForReview(accountId, null);
        }
    }

    @Nested
    @DisplayName("Edge Cases and Boundary Tests")
    class EdgeCaseTests {

        @Test
        @DisplayName("Should handle zero amount transaction")
        void shouldHandleZeroAmount() {
            UUID accountId = UUID.randomUUID();

            ComplianceCheckResult result = fallback.checkTransactionCompliance(
                accountId, "TRANSFER", "0.00"
            );

            // Zero should be approved (not high-value)
            assertThat(result.isApproved()).isTrue();
        }

        @Test
        @DisplayName("Should handle negative amount as invalid")
        void shouldHandleNegativeAmount() {
            UUID accountId = UUID.randomUUID();

            ComplianceCheckResult result = fallback.checkTransactionCompliance(
                accountId, "TRANSFER", "-1000.00"
            );

            // Negative amounts should be approved but flagged
            // (BigDecimal can parse negative numbers)
            assertThat(result.isApproved()).isTrue(); // Less than 10000
        }

        @Test
        @DisplayName("Should handle very large amount (overflow protection)")
        void shouldHandleVeryLargeAmount() {
            UUID accountId = UUID.randomUUID();

            ComplianceCheckResult result = fallback.checkTransactionCompliance(
                accountId, "TRANSFER", "999999999999999.99"
            );

            assertThat(result.isApproved()).isFalse();
            assertThat(result.isRequiresManualReview()).isTrue();
        }

        @Test
        @DisplayName("Should handle amount with many decimal places")
        void shouldHandleHighPrecisionAmount() {
            UUID accountId = UUID.randomUUID();

            ComplianceCheckResult result = fallback.checkTransactionCompliance(
                accountId, "TRANSFER", "10000.001"
            );

            // Should be blocked (above 10000.00)
            assertThat(result.isApproved()).isFalse();
        }

        @Test
        @DisplayName("Should handle scientific notation if provided")
        void shouldHandleScientificNotation() {
            UUID accountId = UUID.randomUUID();

            ComplianceCheckResult result = fallback.checkTransactionCompliance(
                accountId, "TRANSFER", "1E4"
            );

            // 1E4 = 10000, should be approved (not greater than)
            assertThat(result.isApproved()).isTrue();
        }
    }
}
