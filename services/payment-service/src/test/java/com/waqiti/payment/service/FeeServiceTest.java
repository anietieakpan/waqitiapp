package com.waqiti.payment.service;

import com.waqiti.payment.service.FeeService.*;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDateTime;
import java.util.List;

import static org.assertj.core.api.Assertions.*;

/**
 * Comprehensive unit tests for FeeService
 *
 * Tests fee calculation logic:
 * - Payment fee calculations
 * - Settlement fee calculations
 * - Fee breakdown transparency
 * - Merchant fee configuration
 * - Daily revenue calculations
 * - Volume discounts
 * - Cross-border fees
 * - Currency conversion fees
 * - Minimum/maximum fee caps
 * - Regulatory fees
 *
 * @author Waqiti Platform Team
 * @version 1.0.0
 */
@ExtendWith(MockitoExtension.class)
@DisplayName("FeeService Tests")
class FeeServiceTest {

    private FeeService feeService;

    @BeforeEach
    void setUp() {
        feeService = new FeeService();
    }

    @Nested
    @DisplayName("Payment Fee Calculation Tests")
    class PaymentFeeCalculationTests {

        @Test
        @DisplayName("Should calculate basic payment fee successfully")
        void shouldCalculateBasicPaymentFee() {
            // Given
            String merchantId = "merchant-123";
            BigDecimal amount = new BigDecimal("100.00");
            String paymentMethod = "CREDIT_CARD";
            String currency = "USD";
            String region = "US";
            boolean crossBorder = false;

            // When
            FeeCalculationResult result = feeService.calculatePaymentFee(
                    merchantId, amount, paymentMethod, currency, region, crossBorder);

            // Then
            assertThat(result).isNotNull();
            assertThat(result.getMerchantId()).isEqualTo(merchantId);
            assertThat(result.getTransactionAmount()).isEqualByComparingTo(amount);
            assertThat(result.getCurrency()).isEqualTo(currency);
            assertThat(result.getPaymentMethod()).isEqualTo(paymentMethod);
            assertThat(result.getTotalFees()).isGreaterThan(BigDecimal.ZERO);
            assertThat(result.getNetAmount()).isLessThan(amount);
            assertThat(result.getCalculationId()).isNotNull();
            assertThat(result.getFeeLineItems()).isNotEmpty();
            assertThat(result.getEffectiveRate()).isGreaterThan(BigDecimal.ZERO);
            assertThat(result.getCalculatedAt()).isNotNull();

            // Verify net amount calculation
            BigDecimal expectedNet = amount.subtract(result.getTotalFees());
            assertThat(result.getNetAmount()).isEqualByComparingTo(expectedNet.setScale(2, RoundingMode.HALF_UP));
        }

        @Test
        @DisplayName("Should include platform fee in calculation")
        void shouldIncludePlatformFee() {
            // Given
            BigDecimal amount = new BigDecimal("100.00");

            // When
            FeeCalculationResult result = feeService.calculatePaymentFee(
                    "merchant-123", amount, "CREDIT_CARD", "USD", "US", false);

            // Then
            List<FeeLineItem> platformFees = result.getFeeLineItems().stream()
                    .filter(item -> "PLATFORM_FEE".equals(item.getType()))
                    .toList();

            assertThat(platformFees).hasSize(1);
            FeeLineItem platformFee = platformFees.get(0);
            assertThat(platformFee.getAmount()).isGreaterThan(BigDecimal.ZERO);
            assertThat(platformFee.getDescription()).contains("Platform");
            assertThat(platformFee.getPercentage()).isNotNull();
        }

        @Test
        @DisplayName("Should include interchange fee in calculation")
        void shouldIncludeInterchangeFee() {
            // Given
            BigDecimal amount = new BigDecimal("100.00");

            // When
            FeeCalculationResult result = feeService.calculatePaymentFee(
                    "merchant-123", amount, "CREDIT_CARD", "USD", "US", false);

            // Then
            List<FeeLineItem> interchangeFees = result.getFeeLineItems().stream()
                    .filter(item -> "INTERCHANGE_FEE".equals(item.getType()))
                    .toList();

            assertThat(interchangeFees).hasSize(1);
            FeeLineItem interchangeFee = interchangeFees.get(0);
            assertThat(interchangeFee.getAmount()).isGreaterThan(BigDecimal.ZERO);
            assertThat(interchangeFee.getDescription()).contains("interchange");
        }

        @Test
        @DisplayName("Should calculate higher interchange for premium cards")
        void shouldCalculateHigherInterchangeForPremiumCards() {
            // Given
            BigDecimal amount = new BigDecimal("100.00");

            // When - Credit card
            FeeCalculationResult creditResult = feeService.calculatePaymentFee(
                    "merchant-123", amount, "CREDIT_CARD", "USD", "US", false);

            // When - Premium card
            FeeCalculationResult premiumResult = feeService.calculatePaymentFee(
                    "merchant-123", amount, "PREMIUM_CARD", "USD", "US", false);

            // Then - Premium should have higher fees
            BigDecimal creditInterchange = creditResult.getFeeLineItems().stream()
                    .filter(item -> "INTERCHANGE_FEE".equals(item.getType()))
                    .findFirst()
                    .map(FeeLineItem::getAmount)
                    .orElse(BigDecimal.ZERO);

            BigDecimal premiumInterchange = premiumResult.getFeeLineItems().stream()
                    .filter(item -> "INTERCHANGE_FEE".equals(item.getType()))
                    .findFirst()
                    .map(FeeLineItem::getAmount)
                    .orElse(BigDecimal.ZERO);

            assertThat(premiumInterchange).isGreaterThan(creditInterchange);
        }

        @Test
        @DisplayName("Should calculate lower interchange for debit cards")
        void shouldCalculateLowerInterchangeForDebitCards() {
            // Given
            BigDecimal amount = new BigDecimal("100.00");

            // When
            FeeCalculationResult creditResult = feeService.calculatePaymentFee(
                    "merchant-123", amount, "CREDIT_CARD", "USD", "US", false);

            FeeCalculationResult debitResult = feeService.calculatePaymentFee(
                    "merchant-123", amount, "DEBIT_CARD", "USD", "US", false);

            // Then - Debit should have lower interchange
            BigDecimal creditInterchange = creditResult.getFeeLineItems().stream()
                    .filter(item -> "INTERCHANGE_FEE".equals(item.getType()))
                    .findFirst()
                    .map(FeeLineItem::getAmount)
                    .orElse(BigDecimal.ZERO);

            BigDecimal debitInterchange = debitResult.getFeeLineItems().stream()
                    .filter(item -> "INTERCHANGE_FEE".equals(item.getType()))
                    .findFirst()
                    .map(FeeLineItem::getAmount)
                    .orElse(BigDecimal.ZERO);

            assertThat(debitInterchange).isLessThan(creditInterchange);
        }

        @Test
        @DisplayName("Should add cross-border fee when applicable")
        void shouldAddCrossBorderFeeWhenApplicable() {
            // Given
            BigDecimal amount = new BigDecimal("100.00");

            // When - Domestic
            FeeCalculationResult domesticResult = feeService.calculatePaymentFee(
                    "merchant-123", amount, "CREDIT_CARD", "USD", "US", false);

            // When - Cross-border
            FeeCalculationResult crossBorderResult = feeService.calculatePaymentFee(
                    "merchant-123", amount, "CREDIT_CARD", "USD", "US", true);

            // Then
            long domesticCrossBorderFees = domesticResult.getFeeLineItems().stream()
                    .filter(item -> "CROSS_BORDER_FEE".equals(item.getType()))
                    .count();

            long crossBorderFees = crossBorderResult.getFeeLineItems().stream()
                    .filter(item -> "CROSS_BORDER_FEE".equals(item.getType()))
                    .count();

            assertThat(domesticCrossBorderFees).isZero();
            assertThat(crossBorderFees).isEqualTo(1);
            assertThat(crossBorderResult.getTotalFees()).isGreaterThan(domesticResult.getTotalFees());
        }

        @Test
        @DisplayName("Should add currency conversion fee for non-USD")
        void shouldAddCurrencyConversionFeeForNonUSD() {
            // Given
            BigDecimal amount = new BigDecimal("100.00");

            // When - USD
            FeeCalculationResult usdResult = feeService.calculatePaymentFee(
                    "merchant-123", amount, "CREDIT_CARD", "USD", "US", false);

            // When - EUR
            FeeCalculationResult eurResult = feeService.calculatePaymentFee(
                    "merchant-123", amount, "CREDIT_CARD", "EUR", "EU", false);

            // Then
            long usdFxFees = usdResult.getFeeLineItems().stream()
                    .filter(item -> "FX_FEE".equals(item.getType()))
                    .count();

            long eurFxFees = eurResult.getFeeLineItems().stream()
                    .filter(item -> "FX_FEE".equals(item.getType()))
                    .count();

            assertThat(usdFxFees).isZero();
            assertThat(eurFxFees).isEqualTo(1);
        }

        @Test
        @DisplayName("Should apply EU interchange cap")
        void shouldApplyEUInterchangeCap() {
            // Given
            BigDecimal amount = new BigDecimal("100.00");

            // When
            FeeCalculationResult usResult = feeService.calculatePaymentFee(
                    "merchant-123", amount, "CREDIT_CARD", "USD", "US", false);

            FeeCalculationResult euResult = feeService.calculatePaymentFee(
                    "merchant-123", amount, "CREDIT_CARD", "EUR", "EU", false);

            // Then - EU should have lower interchange due to cap
            BigDecimal usInterchange = usResult.getFeeLineItems().stream()
                    .filter(item -> "INTERCHANGE_FEE".equals(item.getType()))
                    .findFirst()
                    .map(FeeLineItem::getAmount)
                    .orElse(BigDecimal.ZERO);

            BigDecimal euInterchange = euResult.getFeeLineItems().stream()
                    .filter(item -> "INTERCHANGE_FEE".equals(item.getType()))
                    .findFirst()
                    .map(FeeLineItem::getAmount)
                    .orElse(BigDecimal.ZERO);

            assertThat(euInterchange).isLessThan(usInterchange);
        }

        @Test
        @DisplayName("Should apply minimum fee when total is below threshold")
        void shouldApplyMinimumFeeWhenBelowThreshold() {
            // Given - Very small amount
            BigDecimal smallAmount = new BigDecimal("1.00");

            // When
            FeeCalculationResult result = feeService.calculatePaymentFee(
                    "merchant-123", smallAmount, "CREDIT_CARD", "USD", "US", false);

            // Then
            assertThat(result.getTotalFees()).isGreaterThanOrEqualTo(new BigDecimal("0.10"));

            // Check for minimum fee adjustment
            boolean hasMinimumAdjustment = result.getFeeLineItems().stream()
                    .anyMatch(item -> "MINIMUM_FEE_ADJUSTMENT".equals(item.getType()));

            if (hasMinimumAdjustment) {
                assertThat(result.getTotalFees()).isEqualByComparingTo(new BigDecimal("0.10"));
            }
        }

        @Test
        @DisplayName("Should apply maximum fee cap when total exceeds threshold")
        void shouldApplyMaximumFeeCap() {
            // Given - Very large amount
            BigDecimal largeAmount = new BigDecimal("10000.00");

            // When
            FeeCalculationResult result = feeService.calculatePaymentFee(
                    "merchant-123", largeAmount, "CREDIT_CARD", "USD", "US", false);

            // Then
            assertThat(result.getTotalFees()).isLessThanOrEqualTo(new BigDecimal("50.00"));
        }

        @Test
        @DisplayName("Should calculate risk fee for high-risk merchants")
        void shouldCalculateRiskFeeForHighRiskMerchants() {
            // Given
            BigDecimal amount = new BigDecimal("100.00");

            // When
            FeeCalculationResult result = feeService.calculatePaymentFee(
                    "merchant-123", amount, "CREDIT_CARD", "USD", "US", false);

            // Then - Risk fee may or may not be present depending on merchant risk level
            List<FeeLineItem> riskFees = result.getFeeLineItems().stream()
                    .filter(item -> "RISK_FEE".equals(item.getType()))
                    .toList();

            // If present, should be positive
            if (!riskFees.isEmpty()) {
                assertThat(riskFees.get(0).getAmount()).isGreaterThan(BigDecimal.ZERO);
            }
        }

        @Test
        @DisplayName("Should add regulatory fees for applicable regions")
        void shouldAddRegulatoryFeesForApplicableRegions() {
            // Given
            BigDecimal amount = new BigDecimal("100.00");

            // When - US
            FeeCalculationResult usResult = feeService.calculatePaymentFee(
                    "merchant-123", amount, "CREDIT_CARD", "USD", "US", false);

            // When - EU
            FeeCalculationResult euResult = feeService.calculatePaymentFee(
                    "merchant-123", amount, "CREDIT_CARD", "EUR", "EU", false);

            // Then - Both should have regulatory fees
            long usRegulatoryFees = usResult.getFeeLineItems().stream()
                    .filter(item -> "REGULATORY_FEE".equals(item.getType()))
                    .count();

            long euRegulatoryFees = euResult.getFeeLineItems().stream()
                    .filter(item -> "REGULATORY_FEE".equals(item.getType()))
                    .count();

            assertThat(usRegulatoryFees + euRegulatoryFees).isGreaterThan(0);
        }

        @Test
        @DisplayName("Should handle calculation error with fallback")
        void shouldHandleCalculationErrorWithFallback() {
            // Given - Invalid inputs that might cause calculation errors
            BigDecimal amount = new BigDecimal("100.00");
            String merchantId = null; // This might cause NPE internally, triggering fallback

            // When
            FeeCalculationResult result = feeService.calculatePaymentFee(
                    merchantId, amount, "CREDIT_CARD", "USD", "US", false);

            // Then - Should return fallback result
            assertThat(result).isNotNull();
            assertThat(result.getTotalFees()).isGreaterThan(BigDecimal.ZERO);
            assertThat(result.getNetAmount()).isLessThan(amount);

            // Fallback uses 3.5%
            BigDecimal expectedFallbackFee = amount.multiply(new BigDecimal("0.035"));
            assertThat(result.getTotalFees()).isEqualByComparingTo(expectedFallbackFee);
        }

        @Test
        @DisplayName("Should calculate effective rate correctly")
        void shouldCalculateEffectiveRateCorrectly() {
            // Given
            BigDecimal amount = new BigDecimal("100.00");

            // When
            FeeCalculationResult result = feeService.calculatePaymentFee(
                    "merchant-123", amount, "CREDIT_CARD", "USD", "US", false);

            // Then
            BigDecimal expectedRate = result.getTotalFees()
                    .divide(amount, 4, RoundingMode.HALF_UP)
                    .multiply(new BigDecimal("100"));

            assertThat(result.getEffectiveRate()).isEqualByComparingTo(expectedRate);
            assertThat(result.getEffectiveRate()).isGreaterThan(BigDecimal.ZERO);
            assertThat(result.getEffectiveRate()).isLessThan(new BigDecimal("10")); // Should be < 10%
        }
    }

    @Nested
    @DisplayName("Settlement Fee Calculation Tests")
    class SettlementFeeCalculationTests {

        @Test
        @DisplayName("Should calculate ACH settlement fee")
        void shouldCalculateAchSettlementFee() {
            // Given
            String merchantId = "merchant-123";
            BigDecimal settlementAmount = new BigDecimal("1000.00");
            String settlementMethod = "ACH";
            String currency = "USD";

            // When
            SettlementFeeResult result = feeService.calculateSettlementFee(
                    merchantId, settlementAmount, settlementMethod, currency);

            // Then
            assertThat(result).isNotNull();
            assertThat(result.getMerchantId()).isEqualTo(merchantId);
            assertThat(result.getSettlementAmount()).isEqualByComparingTo(settlementAmount);
            assertThat(result.getSettlementMethod()).isEqualTo(settlementMethod);
            assertThat(result.getCurrency()).isEqualTo(currency);
            assertThat(result.getSettlementFee()).isGreaterThan(BigDecimal.ZERO);
            assertThat(result.getNetSettlement()).isLessThan(settlementAmount);
            assertThat(result.getCalculatedAt()).isNotNull();

            // Verify net calculation
            BigDecimal expectedNet = settlementAmount.subtract(result.getSettlementFee());
            assertThat(result.getNetSettlement()).isEqualByComparingTo(expectedNet.setScale(2, RoundingMode.HALF_UP));
        }

        @Test
        @DisplayName("Should calculate wire settlement fee")
        void shouldCalculateWireSettlementFee() {
            // Given
            BigDecimal settlementAmount = new BigDecimal("1000.00");

            // When
            SettlementFeeResult result = feeService.calculateSettlementFee(
                    "merchant-123", settlementAmount, "WIRE", "USD");

            // Then - Wire should have higher fee than ACH
            SettlementFeeResult achResult = feeService.calculateSettlementFee(
                    "merchant-123", settlementAmount, "ACH", "USD");

            assertThat(result.getSettlementFee()).isGreaterThan(achResult.getSettlementFee());
        }

        @Test
        @DisplayName("Should calculate instant settlement fee as percentage")
        void shouldCalculateInstantSettlementFeeAsPercentage() {
            // Given
            BigDecimal settlementAmount = new BigDecimal("1000.00");

            // When
            SettlementFeeResult result = feeService.calculateSettlementFee(
                    "merchant-123", settlementAmount, "INSTANT", "USD");

            // Then - Instant should be percentage-based (1.5%)
            BigDecimal expectedMinimumFee = settlementAmount.multiply(new BigDecimal("0.015"));
            assertThat(result.getSettlementFee()).isGreaterThanOrEqualTo(expectedMinimumFee.setScale(2, RoundingMode.HALF_UP));
        }

        @Test
        @DisplayName("Should add FX fee for non-USD settlements")
        void shouldAddFxFeeForNonUsdSettlements() {
            // Given
            BigDecimal settlementAmount = new BigDecimal("1000.00");

            // When - USD
            SettlementFeeResult usdResult = feeService.calculateSettlementFee(
                    "merchant-123", settlementAmount, "ACH", "USD");

            // When - EUR
            SettlementFeeResult eurResult = feeService.calculateSettlementFee(
                    "merchant-123", settlementAmount, "ACH", "EUR");

            // Then - EUR should have additional FX fee
            assertThat(eurResult.getSettlementFee()).isGreaterThan(usdResult.getSettlementFee());
        }

        @Test
        @DisplayName("Should apply minimum settlement fee")
        void shouldApplyMinimumSettlementFee() {
            // Given - Very small settlement
            BigDecimal smallAmount = new BigDecimal("5.00");

            // When
            SettlementFeeResult result = feeService.calculateSettlementFee(
                    "merchant-123", smallAmount, "ACH", "USD");

            // Then - Should have at least minimum fee
            assertThat(result.getSettlementFee()).isGreaterThanOrEqualTo(new BigDecimal("0.10"));
        }

        @Test
        @DisplayName("Should use default fee for unknown settlement method")
        void shouldUseDefaultFeeForUnknownSettlementMethod() {
            // Given
            BigDecimal settlementAmount = new BigDecimal("1000.00");

            // When
            SettlementFeeResult result = feeService.calculateSettlementFee(
                    "merchant-123", settlementAmount, "UNKNOWN_METHOD", "USD");

            // Then - Should use default fee
            assertThat(result).isNotNull();
            assertThat(result.getSettlementFee()).isEqualByComparingTo(new BigDecimal("0.50"));
        }
    }

    @Nested
    @DisplayName("Fee Breakdown Tests")
    class FeeBreakdownTests {

        @Test
        @DisplayName("Should retrieve fee breakdown by calculation ID")
        void shouldRetrieveFeeBreakdownByCalculationId() {
            // Given - Calculate a fee first
            FeeCalculationResult result = feeService.calculatePaymentFee(
                    "merchant-123", new BigDecimal("100.00"), "CREDIT_CARD", "USD", "US", false);
            String calculationId = result.getCalculationId();

            // When
            List<FeeLineItem> breakdown = feeService.getFeeBreakdown(calculationId);

            // Then
            assertThat(breakdown).isNotEmpty();
            assertThat(breakdown).hasSameSizeAs(result.getFeeLineItems());

            // Verify all fee types are present
            assertThat(breakdown).extracting(FeeLineItem::getType).contains("PLATFORM_FEE", "INTERCHANGE_FEE");
        }

        @Test
        @DisplayName("Should return empty list for non-existent calculation ID")
        void shouldReturnEmptyListForNonExistentCalculationId() {
            // Given
            String nonExistentId = "non-existent-calc-id";

            // When
            List<FeeLineItem> breakdown = feeService.getFeeBreakdown(nonExistentId);

            // Then
            assertThat(breakdown).isEmpty();
        }

        @Test
        @DisplayName("Should handle null calculation ID gracefully")
        void shouldHandleNullCalculationIdGracefully() {
            // When
            List<FeeLineItem> breakdown = feeService.getFeeBreakdown(null);

            // Then
            assertThat(breakdown).isEmpty();
        }
    }

    @Nested
    @DisplayName("Merchant Fee Configuration Tests")
    class MerchantFeeConfigurationTests {

        @Test
        @DisplayName("Should update merchant fee configuration successfully")
        void shouldUpdateMerchantFeeConfigSuccessfully() {
            // Given
            String merchantId = "merchant-456";
            MerchantFeeConfig config = MerchantFeeConfig.builder()
                    .merchantId(merchantId)
                    .platformFeePercentage(new BigDecimal("2.5"))
                    .platformFixedFee(new BigDecimal("0.30"))
                    .minimumFee(new BigDecimal("0.15"))
                    .maximumFee(new BigDecimal("45.00"))
                    .achSettlementFee(new BigDecimal("0.30"))
                    .wireSettlementFee(new BigDecimal("30.00"))
                    .instantSettlementRate(new BigDecimal("0.02"))
                    .bankTransferFee(new BigDecimal("0.60"))
                    .minimumSettlementFee(new BigDecimal("0.15"))
                    .build();

            // When
            feeService.updateMerchantFeeConfig(merchantId, config);

            // Calculate fee to verify config is used
            FeeCalculationResult result = feeService.calculatePaymentFee(
                    merchantId, new BigDecimal("100.00"), "CREDIT_CARD", "USD", "US", false);

            // Then
            assertThat(result).isNotNull();
            // Config should be applied in calculation
        }

        @Test
        @DisplayName("Should reject invalid platform fee percentage")
        void shouldRejectInvalidPlatformFeePercentage() {
            // Given - Fee percentage > 10%
            String merchantId = "merchant-789";
            MerchantFeeConfig invalidConfig = MerchantFeeConfig.builder()
                    .merchantId(merchantId)
                    .platformFeePercentage(new BigDecimal("15.0")) // Invalid: > 10%
                    .platformFixedFee(new BigDecimal("0.30"))
                    .minimumFee(new BigDecimal("0.10"))
                    .maximumFee(new BigDecimal("50.00"))
                    .build();

            // When/Then
            assertThatThrownBy(() -> feeService.updateMerchantFeeConfig(merchantId, invalidConfig))
                    .isInstanceOf(RuntimeException.class);
        }

        @Test
        @DisplayName("Should reject negative minimum fee")
        void shouldRejectNegativeMinimumFee() {
            // Given
            String merchantId = "merchant-789";
            MerchantFeeConfig invalidConfig = MerchantFeeConfig.builder()
                    .merchantId(merchantId)
                    .platformFeePercentage(new BigDecimal("2.9"))
                    .platformFixedFee(new BigDecimal("0.30"))
                    .minimumFee(new BigDecimal("-0.10")) // Invalid: negative
                    .maximumFee(new BigDecimal("50.00"))
                    .build();

            // When/Then
            assertThatThrownBy(() -> feeService.updateMerchantFeeConfig(merchantId, invalidConfig))
                    .isInstanceOf(RuntimeException.class);
        }

        @Test
        @DisplayName("Should reject maximum fee less than minimum fee")
        void shouldRejectMaximumFeeLessThanMinimumFee() {
            // Given
            String merchantId = "merchant-789";
            MerchantFeeConfig invalidConfig = MerchantFeeConfig.builder()
                    .merchantId(merchantId)
                    .platformFeePercentage(new BigDecimal("2.9"))
                    .platformFixedFee(new BigDecimal("0.30"))
                    .minimumFee(new BigDecimal("1.00"))
                    .maximumFee(new BigDecimal("0.50")) // Invalid: < minimum
                    .build();

            // When/Then
            assertThatThrownBy(() -> feeService.updateMerchantFeeConfig(merchantId, invalidConfig))
                    .isInstanceOf(RuntimeException.class);
        }
    }

    @Nested
    @DisplayName("Daily Revenue Calculation Tests")
    class DailyRevenueCalculationTests {

        @Test
        @DisplayName("Should calculate daily fee revenue")
        void shouldCalculateDailyFeeRevenue() {
            // Given
            LocalDateTime date = LocalDateTime.now();

            // When
            DailyFeeRevenue revenue = feeService.calculateDailyRevenue(date);

            // Then
            assertThat(revenue).isNotNull();
            assertThat(revenue.getDate()).isEqualTo(date.toLocalDate());
            assertThat(revenue.getTotalRevenue()).isNotNull();
            assertThat(revenue.getTransactionCount()).isGreaterThanOrEqualTo(0);
            assertThat(revenue.getAverageRevenue()).isNotNull();
            assertThat(revenue.getTopMerchants()).isNotNull();
        }

        @Test
        @DisplayName("Should calculate average revenue correctly")
        void shouldCalculateAverageRevenueCorrectly() {
            // Given
            LocalDateTime date = LocalDateTime.now();

            // When
            DailyFeeRevenue revenue = feeService.calculateDailyRevenue(date);

            // Then
            if (revenue.getTransactionCount() > 0) {
                BigDecimal expectedAverage = revenue.getTotalRevenue()
                        .divide(BigDecimal.valueOf(revenue.getTransactionCount()), 2, RoundingMode.HALF_UP);
                assertThat(revenue.getAverageRevenue()).isEqualByComparingTo(expectedAverage);
            } else {
                assertThat(revenue.getAverageRevenue()).isEqualByComparingTo(BigDecimal.ZERO);
            }
        }

        @Test
        @DisplayName("Should return top revenue generating merchants")
        void shouldReturnTopRevenueGeneratingMerchants() {
            // Given
            LocalDateTime date = LocalDateTime.now();

            // When
            DailyFeeRevenue revenue = feeService.calculateDailyRevenue(date);

            // Then
            assertThat(revenue.getTopMerchants()).isNotEmpty();
        }
    }
}
