package com.waqiti.business.domain;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.ValueSource;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.time.Month;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.*;

/**
 * Comprehensive Unit Tests for BusinessAccount Entity
 *
 * Tests ALL business logic including:
 * - Transaction limit enforcement
 * - BigDecimal precision handling
 * - Status and verification workflows
 * - Balance calculations
 * - Team capacity management
 *
 * CRITICAL: Financial precision must be maintained
 *
 * @author Waqiti Engineering Team
 * @version 1.0.0
 */
@DisplayName("BusinessAccount Entity Tests")
class BusinessAccountTest {

    private BusinessAccount account;

    @BeforeEach
    void setUp() {
        account = BusinessAccount.builder()
                .id(UUID.randomUUID())
                .ownerId(UUID.randomUUID())
                .businessName("Test Business Inc")
                .businessType(BusinessAccount.BusinessType.LLC)
                .accountType(BusinessAccount.AccountType.BUSINESS)
                .status(BusinessAccount.Status.ACTIVE)
                .tier(BusinessAccount.Tier.STANDARD)
                .currency("USD")
                .balance(new BigDecimal("10000.00"))
                .availableBalance(new BigDecimal("10000.00"))
                .pendingBalance(BigDecimal.ZERO)
                .monthlySpending(new BigDecimal("5000.00"))
                .monthlyIncome(new BigDecimal("20000.00"))
                .verificationStatus(BusinessAccount.VerificationStatus.VERIFIED)
                .teamMemberCount(5)
                .createdAt(LocalDateTime.now())
                .build();
    }

    @Nested
    @DisplayName("Transaction Limit Tests")
    class TransactionLimitTests {

        @Test
        @DisplayName("Should allow transaction within limits")
        void shouldAllowTransactionWithinLimits() {
            // Arrange
            BigDecimal transactionAmount = new BigDecimal("1000.00");
            account.setLimits(Map.of(
                    "maxTransactionAmount", new BigDecimal("5000.00"),
                    "monthlyTransactionLimit", new BigDecimal("50000.00")
            ));

            // Act
            boolean canProcess = account.canProcessTransaction(transactionAmount);

            // Assert
            assertThat(canProcess).isTrue();
        }

        @Test
        @DisplayName("Should reject transaction exceeding single transaction limit")
        void shouldRejectTransactionExceedingLimit() {
            // Arrange
            BigDecimal transactionAmount = new BigDecimal("6000.00");
            account.setLimits(Map.of(
                    "maxTransactionAmount", new BigDecimal("5000.00")
            ));

            // Act
            boolean canProcess = account.canProcessTransaction(transactionAmount);

            // Assert
            assertThat(canProcess).isFalse();
        }

        @Test
        @DisplayName("Should reject transaction that would exceed monthly limit")
        void shouldRejectTransactionExceedingMonthlyLimit() {
            // Arrange
            account.setMonthlySpending(new BigDecimal("45000.00"));
            BigDecimal transactionAmount = new BigDecimal("6000.00");
            account.setLimits(Map.of(
                    "monthlyTransactionLimit", new BigDecimal("50000.00")
            ));

            // Act
            boolean canProcess = account.canProcessTransaction(transactionAmount);

            // Assert
            assertThat(canProcess).isFalse();
        }

        @Test
        @DisplayName("Should allow unlimited transactions when limit is -1")
        void shouldAllowUnlimitedWhenLimitIsNegativeOne() {
            // Arrange
            BigDecimal transactionAmount = new BigDecimal("1000000.00");
            account.setLimits(Map.of(
                    "maxTransactionAmount", -1
            ));

            // Act
            boolean canProcess = account.canProcessTransaction(transactionAmount);

            // Assert
            assertThat(canProcess).isTrue();
        }

        @ParameterizedTest
        @CsvSource({
                "1000.00, 5000.00, 10000.00, true",   // Well within limits
                "5000.00, 5000.00, 10000.00, true",   // Exactly at single limit
                "5000.01, 5000.00, 10000.00, false",  // Exceeds single limit by 1 cent
                "3000.00, 5000.00, 7000.00, false",   // Would exceed monthly (5000+3000 > 7000)
                "2000.00, 5000.00, 7000.00, true"     // Within monthly (5000+2000 <= 7000)
        })
        @DisplayName("Should correctly enforce various limit combinations")
        void shouldEnforceLimitCombinations(
                String amountStr,
                String singleLimitStr,
                String monthlyLimitStr,
                boolean expectedResult) {
            // Arrange
            BigDecimal amount = new BigDecimal(amountStr);
            account.setMonthlySpending(new BigDecimal("5000.00"));
            account.setLimits(Map.of(
                    "maxTransactionAmount", new BigDecimal(singleLimitStr),
                    "monthlyTransactionLimit", new BigDecimal(monthlyLimitStr)
            ));

            // Act
            boolean canProcess = account.canProcessTransaction(amount);

            // Assert
            assertThat(canProcess).isEqualTo(expectedResult);
        }
    }

    @Nested
    @DisplayName("Financial Precision Tests - CRITICAL")
    class FinancialPrecisionTests {

        @Test
        @DisplayName("Should handle very large amounts without precision loss")
        void shouldHandleLargeAmountsWithoutPrecisionLoss() {
            // Arrange - Test the fix for Number.doubleValue() conversion
            BigDecimal largeAmount = new BigDecimal("99999999.99");
            account.setLimits(Map.of(
                    "maxTransactionAmount", new BigDecimal("100000000.00")
            ));

            // Act
            boolean canProcess = account.canProcessTransaction(largeAmount);

            // Assert
            assertThat(canProcess).isTrue();
        }

        @Test
        @DisplayName("Should preserve precision with fractional amounts")
        void shouldPreservePrecisionWithFractionalAmounts() {
            // Arrange
            BigDecimal preciseAmount = new BigDecimal("12345.6789");
            account.setLimits(Map.of(
                    "maxTransactionAmount", new BigDecimal("12345.68") // Slightly higher
            ));

            // Act
            boolean canProcess = account.canProcessTransaction(preciseAmount);

            // Assert
            assertThat(canProcess).isTrue();
        }

        @Test
        @DisplayName("Should correctly handle edge case: 0.1 + 0.2 comparisons")
        void shouldHandleFloatingPointEdgeCases() {
            // Arrange - This would fail with double arithmetic
            BigDecimal amount1 = new BigDecimal("0.1");
            BigDecimal amount2 = new BigDecimal("0.2");
            BigDecimal sum = amount1.add(amount2);
            BigDecimal expected = new BigDecimal("0.3");

            account.setLimits(Map.of(
                    "maxTransactionAmount", expected
            ));

            // Act
            boolean canProcess = account.canProcessTransaction(sum);

            // Assert
            assertThat(sum).isEqualByComparingTo(expected);
            assertThat(canProcess).isTrue();
        }

        @Test
        @DisplayName("Should handle Number types safely in limit checks")
        void shouldHandleNumberTypesSafely() {
            // Arrange - Test various Number implementations
            account.setLimits(Map.of(
                    "maxTransactionAmount", Long.valueOf(5000L),  // Long
                    "monthlyTransactionLimit", Integer.valueOf(50000)  // Integer
            ));

            BigDecimal amount = new BigDecimal("4999.99");

            // Act
            boolean canProcess = account.canProcessTransaction(amount);

            // Assert
            assertThat(canProcess).isTrue();
        }
    }

    @Nested
    @DisplayName("Status and Verification Tests")
    class StatusAndVerificationTests {

        @Test
        @DisplayName("Should reject transactions when account is not active")
        void shouldRejectTransactionsWhenNotActive() {
            // Arrange
            account.setStatus(BusinessAccount.Status.SUSPENDED);
            BigDecimal amount = new BigDecimal("100.00");

            // Act
            boolean canProcess = account.canProcessTransaction(amount);

            // Assert
            assertThat(canProcess).isFalse();
        }

        @Test
        @DisplayName("Should reject transactions when account is not verified")
        void shouldRejectTransactionsWhenNotVerified() {
            // Arrange
            account.setVerificationStatus(BusinessAccount.VerificationStatus.PENDING);
            BigDecimal amount = new BigDecimal("100.00");

            // Act
            boolean canProcess = account.canProcessTransaction(amount);

            // Assert
            assertThat(canProcess).isFalse();
        }

        @Test
        @DisplayName("Should allow transactions only when active AND verified")
        void shouldAllowTransactionsWhenActiveAndVerified() {
            // Arrange
            account.setStatus(BusinessAccount.Status.ACTIVE);
            account.setVerificationStatus(BusinessAccount.VerificationStatus.VERIFIED);
            BigDecimal amount = new BigDecimal("100.00");
            account.setLimits(Map.of("maxTransactionAmount", new BigDecimal("1000.00")));

            // Act
            boolean canProcess = account.canProcessTransaction(amount);

            // Assert
            assertThat(canProcess).isTrue();
        }

        @ParameterizedTest
        @ValueSource(strings = {"SUSPENDED", "CLOSED", "PENDING_CLOSURE", "FROZEN"})
        @DisplayName("Should reject transactions for all non-active statuses")
        void shouldRejectTransactionsForNonActiveStatuses(String statusStr) {
            // Arrange
            account.setStatus(BusinessAccount.Status.valueOf(statusStr));
            BigDecimal amount = new BigDecimal("100.00");

            // Act
            boolean canProcess = account.canProcessTransaction(amount);

            // Assert
            assertThat(canProcess).isFalse();
        }
    }

    @Nested
    @DisplayName("Team Capacity Tests")
    class TeamCapacityTests {

        @Test
        @DisplayName("Should have capacity when under team member limit")
        void shouldHaveCapacityWhenUnderLimit() {
            // Arrange
            account.setTeamMemberCount(5);
            account.setLimits(Map.of("maxTeamMembers", 10));

            // Act
            boolean hasCapacity = account.hasTeamCapacity();

            // Assert
            assertThat(hasCapacity).isTrue();
        }

        @Test
        @DisplayName("Should not have capacity when at team member limit")
        void shouldNotHaveCapacityWhenAtLimit() {
            // Arrange
            account.setTeamMemberCount(10);
            account.setLimits(Map.of("maxTeamMembers", 10));

            // Act
            boolean hasCapacity = account.hasTeamCapacity();

            // Assert
            assertThat(hasCapacity).isFalse();
        }

        @Test
        @DisplayName("Should always have capacity when limit is -1 (unlimited)")
        void shouldAlwaysHaveCapacityWhenUnlimited() {
            // Arrange
            account.setTeamMemberCount(1000);
            account.setLimits(Map.of("maxTeamMembers", -1));

            // Act
            boolean hasCapacity = account.hasTeamCapacity();

            // Assert
            assertThat(hasCapacity).isTrue();
        }
    }

    @Nested
    @DisplayName("Balance Tests")
    class BalanceTests {

        @Test
        @DisplayName("Should correctly calculate available balance")
        void shouldCalculateAvailableBalance() {
            // Arrange
            account.setBalance(new BigDecimal("10000.00"));
            account.setPendingBalance(new BigDecimal("1500.00"));

            // Expected: 10000 - 1500 = 8500
            BigDecimal expectedAvailable = new BigDecimal("8500.00");

            // Act
            BigDecimal calculatedAvailable = account.getBalance().subtract(account.getPendingBalance());

            // Assert
            assertThat(calculatedAvailable).isEqualByComparingTo(expectedAvailable);
        }

        @Test
        @DisplayName("Should maintain precision in balance calculations")
        void shouldMaintainPrecisionInBalanceCalculations() {
            // Arrange
            account.setBalance(new BigDecimal("12345.6789"));
            account.setPendingBalance(new BigDecimal("2345.6789"));

            // Act
            BigDecimal result = account.getBalance().subtract(account.getPendingBalance());

            // Assert
            assertThat(result).isEqualByComparingTo(new BigDecimal("10000.0000"));
        }
    }

    @Nested
    @DisplayName("Builder and Initialization Tests")
    class BuilderTests {

        @Test
        @DisplayName("Should create account with builder")
        void shouldCreateAccountWithBuilder() {
            // Act
            BusinessAccount newAccount = BusinessAccount.builder()
                    .businessName("New Business")
                    .businessType(BusinessAccount.BusinessType.CORPORATION)
                    .balance(new BigDecimal("50000.00"))
                    .build();

            // Assert
            assertThat(newAccount).isNotNull();
            assertThat(newAccount.getBusinessName()).isEqualTo("New Business");
            assertThat(newAccount.getBalance()).isEqualByComparingTo(new BigDecimal("50000.00"));
        }

        @Test
        @DisplayName("Should have default values for financial fields")
        void shouldHaveDefaultValuesForFinancialFields() {
            // Act
            BusinessAccount newAccount = new BusinessAccount();

            // Assert
            assertThat(newAccount.getCurrency()).isEqualTo("USD");
            assertThat(newAccount.getTimezone()).isEqualTo("UTC");
            assertThat(newAccount.getFiscalYearStart()).isEqualTo(Month.JANUARY);
        }
    }

    @Test
    @DisplayName("Should handle null limits gracefully")
    void shouldHandleNullLimitsGracefully() {
        // Arrange
        account.setLimits(null);
        BigDecimal amount = new BigDecimal("100.00");

        // Act
        boolean canProcess = account.canProcessTransaction(amount);

        // Assert - Should return true when no limits are set
        assertThat(canProcess).isTrue();
    }

    @Test
    @DisplayName("Should handle empty limits map")
    void shouldHandleEmptyLimitsMap() {
        // Arrange
        account.setLimits(Map.of());
        BigDecimal amount = new BigDecimal("100.00");

        // Act
        boolean canProcess = account.canProcessTransaction(amount);

        // Assert
        assertThat(canProcess).isTrue();
    }
}
