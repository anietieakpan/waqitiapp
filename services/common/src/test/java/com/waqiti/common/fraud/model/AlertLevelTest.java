package com.waqiti.common.fraud.model;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.ValueSource;

import java.time.LocalDateTime;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.*;

/**
 * Comprehensive test suite for AlertLevel enum
 *
 * Coverage Target: 100% line coverage, 100% branch coverage
 * Thread Safety: Verified through concurrent access tests
 *
 * @author Waqiti Test Suite
 * @version 1.0.0
 */
@DisplayName("AlertLevel Comprehensive Test Suite")
class AlertLevelTest {

    @Nested
    @DisplayName("Enum Constants Tests")
    class EnumConstantsTests {

        @Test
        @DisplayName("Should have exactly 5 alert levels")
        void shouldHaveExactlyFiveLevels() {
            assertThat(AlertLevel.values()).hasSize(5);
        }

        @Test
        @DisplayName("Should have all required alert levels")
        void shouldHaveAllRequiredLevels() {
            assertThat(AlertLevel.values())
                .extracting(AlertLevel::name)
                .containsExactlyInAnyOrder("CRITICAL", "HIGH", "MEDIUM", "LOW", "INFO");
        }

        @Test
        @DisplayName("CRITICAL should have highest priority (lowest number)")
        void criticalShouldHaveHighestPriority() {
            assertThat(AlertLevel.CRITICAL.getPriority())
                .isEqualTo(1)
                .isLessThan(AlertLevel.HIGH.getPriority())
                .isLessThan(AlertLevel.MEDIUM.getPriority())
                .isLessThan(AlertLevel.LOW.getPriority())
                .isLessThan(AlertLevel.INFO.getPriority());
        }

        @Test
        @DisplayName("INFO should have lowest priority (highest number)")
        void infoShouldHaveLowestPriority() {
            assertThat(AlertLevel.INFO.getPriority())
                .isEqualTo(5)
                .isGreaterThan(AlertLevel.CRITICAL.getPriority())
                .isGreaterThan(AlertLevel.HIGH.getPriority())
                .isGreaterThan(AlertLevel.MEDIUM.getPriority())
                .isGreaterThan(AlertLevel.LOW.getPriority());
        }
    }

    @Nested
    @DisplayName("SLA Requirements Tests")
    class SlaRequirementsTests {

        @Test
        @DisplayName("CRITICAL should have 15-minute SLA")
        void criticalShouldHave15MinuteSla() {
            assertThat(AlertLevel.CRITICAL.getSlaMinutes()).isEqualTo(15);
        }

        @Test
        @DisplayName("HIGH should have 1-hour SLA")
        void highShouldHave1HourSla() {
            assertThat(AlertLevel.HIGH.getSlaMinutes()).isEqualTo(60);
        }

        @Test
        @DisplayName("MEDIUM should have 4-hour SLA")
        void mediumShouldHave4HourSla() {
            assertThat(AlertLevel.MEDIUM.getSlaMinutes()).isEqualTo(240);
        }

        @Test
        @DisplayName("LOW should have 24-hour SLA")
        void lowShouldHave24HourSla() {
            assertThat(AlertLevel.LOW.getSlaMinutes()).isEqualTo(1440);
        }

        @Test
        @DisplayName("INFO should have 3-day SLA")
        void infoShouldHave3DaySla() {
            assertThat(AlertLevel.INFO.getSlaMinutes()).isEqualTo(4320);
        }

        @Test
        @DisplayName("SLA should be in ascending order (stricter for higher priority)")
        void slaShouldBeInAscendingOrder() {
            assertThat(AlertLevel.CRITICAL.getSlaMinutes())
                .isLessThan(AlertLevel.HIGH.getSlaMinutes())
                .isLessThan(AlertLevel.MEDIUM.getSlaMinutes())
                .isLessThan(AlertLevel.LOW.getSlaMinutes())
                .isLessThan(AlertLevel.INFO.getSlaMinutes());
        }
    }

    @Nested
    @DisplayName("Risk Score Threshold Tests")
    class RiskScoreThresholdTests {

        @ParameterizedTest
        @CsvSource({
            "0.90, CRITICAL",
            "0.95, CRITICAL",
            "1.00, CRITICAL",
            "0.70, HIGH",
            "0.80, HIGH",
            "0.50, MEDIUM",
            "0.60, MEDIUM",
            "0.30, LOW",
            "0.40, LOW",
            "0.10, INFO",
            "0.00, INFO"
        })
        @DisplayName("Should map risk scores correctly to alert levels")
        void shouldMapRiskScoresToAlertLevels(double score, AlertLevel expected) {
            assertThat(AlertLevel.fromRiskScore(score)).isEqualTo(expected);
        }

        @ParameterizedTest
        @CsvSource({
            "90, CRITICAL",
            "95, CRITICAL",
            "100, CRITICAL",
            "70, HIGH",
            "80, HIGH",
            "50, MEDIUM",
            "60, MEDIUM",
            "30, LOW",
            "40, LOW",
            "10, INFO",
            "0, INFO"
        })
        @DisplayName("Should map fraud score percentages correctly")
        void shouldMapFraudScorePercentages(double percentage, AlertLevel expected) {
            assertThat(AlertLevel.fromFraudScore(percentage)).isEqualTo(expected);
        }
    }

    @Nested
    @DisplayName("Priority Comparison Tests")
    class PriorityComparisonTests {

        @Test
        @DisplayName("CRITICAL should be higher priority than HIGH")
        void criticalShouldBeHigherThanHigh() {
            assertThat(AlertLevel.CRITICAL.isHigherPriorityThan(AlertLevel.HIGH)).isTrue();
            assertThat(AlertLevel.HIGH.isHigherPriorityThan(AlertLevel.CRITICAL)).isFalse();
        }

        @Test
        @DisplayName("HIGH should be higher priority than MEDIUM")
        void highShouldBeHigherThanMedium() {
            assertThat(AlertLevel.HIGH.isHigherPriorityThan(AlertLevel.MEDIUM)).isTrue();
            assertThat(AlertLevel.MEDIUM.isHigherPriorityThan(AlertLevel.HIGH)).isFalse();
        }

        @Test
        @DisplayName("Same level should not be higher priority than itself")
        void sameLevelShouldNotBeHigherThanItself() {
            assertThat(AlertLevel.MEDIUM.isHigherPriorityThan(AlertLevel.MEDIUM)).isFalse();
        }

        @Test
        @DisplayName("isAtLeastPriority should work correctly")
        void isAtLeastPriorityShouldWorkCorrectly() {
            assertThat(AlertLevel.CRITICAL.isAtLeastPriority(AlertLevel.CRITICAL)).isTrue();
            assertThat(AlertLevel.CRITICAL.isAtLeastPriority(AlertLevel.HIGH)).isTrue();
            assertThat(AlertLevel.HIGH.isAtLeastPriority(AlertLevel.CRITICAL)).isFalse();
        }
    }

    @Nested
    @DisplayName("Notification and Investigation Requirements Tests")
    class NotificationRequirementsTests {

        @Test
        @DisplayName("CRITICAL and HIGH should require notification")
        void criticalAndHighShouldRequireNotification() {
            assertThat(AlertLevel.CRITICAL.requiresNotification()).isTrue();
            assertThat(AlertLevel.HIGH.requiresNotification()).isTrue();
        }

        @Test
        @DisplayName("MEDIUM, LOW, and INFO should not require notification")
        void lowerLevelsShouldNotRequireNotification() {
            assertThat(AlertLevel.MEDIUM.requiresNotification()).isFalse();
            assertThat(AlertLevel.LOW.requiresNotification()).isFalse();
            assertThat(AlertLevel.INFO.requiresNotification()).isFalse();
        }

        @Test
        @DisplayName("CRITICAL, HIGH, and MEDIUM should require investigation")
        void topThreeLevelsShouldRequireInvestigation() {
            assertThat(AlertLevel.CRITICAL.requiresInvestigation()).isTrue();
            assertThat(AlertLevel.HIGH.requiresInvestigation()).isTrue();
            assertThat(AlertLevel.MEDIUM.requiresInvestigation()).isTrue();
        }

        @Test
        @DisplayName("LOW and INFO should not require investigation")
        void lowAndInfoShouldNotRequireInvestigation() {
            assertThat(AlertLevel.LOW.requiresInvestigation()).isFalse();
            assertThat(AlertLevel.INFO.requiresInvestigation()).isFalse();
        }

        @Test
        @DisplayName("Only CRITICAL and HIGH should require immediate action")
        void onlyCriticalAndHighRequireImmediateAction() {
            assertThat(AlertLevel.CRITICAL.requiresImmediateAction()).isTrue();
            assertThat(AlertLevel.HIGH.requiresImmediateAction()).isTrue();
            assertThat(AlertLevel.MEDIUM.requiresImmediateAction()).isFalse();
            assertThat(AlertLevel.LOW.requiresImmediateAction()).isFalse();
            assertThat(AlertLevel.INFO.requiresImmediateAction()).isFalse();
        }
    }

    @Nested
    @DisplayName("Recommended Action Tests")
    class RecommendedActionTests {

        @Test
        @DisplayName("CRITICAL should recommend blocking transaction")
        void criticalShouldRecommendBlocking() {
            assertThat(AlertLevel.CRITICAL.getRecommendedAction())
                .isEqualTo("BLOCK_TRANSACTION_AND_NOTIFY");
        }

        @Test
        @DisplayName("HIGH should recommend additional verification")
        void highShouldRecommendAdditionalVerification() {
            assertThat(AlertLevel.HIGH.getRecommendedAction())
                .isEqualTo("REQUIRE_ADDITIONAL_VERIFICATION");
        }

        @Test
        @DisplayName("MEDIUM should recommend flagging for review")
        void mediumShouldRecommendFlagging() {
            assertThat(AlertLevel.MEDIUM.getRecommendedAction())
                .isEqualTo("FLAG_FOR_REVIEW");
        }

        @Test
        @DisplayName("LOW should recommend logging and monitoring")
        void lowShouldRecommendLogging() {
            assertThat(AlertLevel.LOW.getRecommendedAction())
                .isEqualTo("LOG_AND_MONITOR");
        }

        @Test
        @DisplayName("INFO should recommend logging only")
        void infoShouldRecommendLogOnly() {
            assertThat(AlertLevel.INFO.getRecommendedAction())
                .isEqualTo("LOG_ONLY");
        }
    }

    @Nested
    @DisplayName("UI Display Tests")
    class UiDisplayTests {

        @Test
        @DisplayName("Each level should have a unique color")
        void eachLevelShouldHaveUniqueColor() {
            assertThat(AlertLevel.values())
                .extracting(AlertLevel::getColor)
                .doesNotHaveDuplicates()
                .allMatch(color -> color.matches("^#[0-9A-F]{6}$"));
        }

        @Test
        @DisplayName("Display names should be human-readable")
        void displayNamesShouldBeHumanReadable() {
            assertThat(AlertLevel.CRITICAL.getDisplayName()).isEqualTo("Critical");
            assertThat(AlertLevel.HIGH.getDisplayName()).isEqualTo("High");
            assertThat(AlertLevel.MEDIUM.getDisplayName()).isEqualTo("Medium");
            assertThat(AlertLevel.LOW.getDisplayName()).isEqualTo("Low");
            assertThat(AlertLevel.INFO.getDisplayName()).isEqualTo("Info");
        }

        @Test
        @DisplayName("toString should return display name")
        void toStringShouldReturnDisplayName() {
            assertThat(AlertLevel.CRITICAL.toString()).isEqualTo("Critical");
            assertThat(AlertLevel.HIGH.toString()).isEqualTo("High");
        }
    }

    @Nested
    @DisplayName("Conversion Methods Tests")
    class ConversionMethodsTests {

        @ParameterizedTest
        @ValueSource(strings = {"CRITICAL", "critical", "Critical", "  CRITICAL  "})
        @DisplayName("fromString should handle various formats")
        void fromStringShouldHandleVariousFormats(String input) {
            assertThat(AlertLevel.fromString(input)).isEqualTo(AlertLevel.CRITICAL);
        }

        @ParameterizedTest
        @ValueSource(strings = {"", "   ", "INVALID", "UNKNOWN"})
        @DisplayName("fromString should return MEDIUM for invalid input")
        void fromStringShouldReturnDefaultForInvalid(String input) {
            assertThat(AlertLevel.fromString(input)).isEqualTo(AlertLevel.MEDIUM);
        }

        @Test
        @DisplayName("fromString should return MEDIUM for null")
        void fromStringShouldReturnDefaultForNull() {
            assertThat(AlertLevel.fromString(null)).isEqualTo(AlertLevel.MEDIUM);
        }

        @ParameterizedTest
        @CsvSource({"1, CRITICAL", "2, HIGH", "3, MEDIUM", "4, LOW", "5, INFO"})
        @DisplayName("fromPriority should map correctly")
        void fromPriorityShouldMapCorrectly(int priority, AlertLevel expected) {
            assertThat(AlertLevel.fromPriority(priority)).isEqualTo(expected);
        }

        @ParameterizedTest
        @ValueSource(ints = {0, 6, 10, -1, 999})
        @DisplayName("fromPriority should return MEDIUM for invalid priority")
        void fromPriorityShouldReturnDefaultForInvalid(int priority) {
            assertThat(AlertLevel.fromPriority(priority)).isEqualTo(AlertLevel.MEDIUM);
        }
    }

    @Nested
    @DisplayName("SLA Deadline and Breach Tests")
    class SlaDeadlineTests {

        @Test
        @DisplayName("getSlaDeadline should return future timestamp")
        void getSlaDeadlineShouldReturnFuture() {
            LocalDateTime deadline = AlertLevel.CRITICAL.getSlaDeadline();
            assertThat(deadline).isAfter(LocalDateTime.now());
        }

        @Test
        @DisplayName("isSlaBreached should return false for recent alert")
        void isSlaBreachedShouldReturnFalseForRecentAlert() {
            LocalDateTime createdAt = LocalDateTime.now().minusMinutes(5);
            assertThat(AlertLevel.CRITICAL.isSlaBreached(createdAt)).isFalse();
        }

        @Test
        @DisplayName("isSlaBreached should return true for old alert")
        void isSlaBreachedShouldReturnTrueForOldAlert() {
            LocalDateTime createdAt = LocalDateTime.now().minusMinutes(30);
            assertThat(AlertLevel.CRITICAL.isSlaBreached(createdAt)).isTrue();
        }

        @Test
        @DisplayName("isSlaBreached should return false for null createdAt")
        void isSlaBreachedShouldReturnFalseForNull() {
            assertThat(AlertLevel.CRITICAL.isSlaBreached(null)).isFalse();
        }

        @Test
        @DisplayName("getMinutesUntilSlaBreach should return positive for recent alert")
        void getMinutesUntilSlaBreachShouldReturnPositive() {
            LocalDateTime createdAt = LocalDateTime.now().minusMinutes(5);
            long remaining = AlertLevel.CRITICAL.getMinutesUntilSlaBreach(createdAt);
            assertThat(remaining).isPositive().isLessThanOrEqualTo(10);
        }

        @Test
        @DisplayName("getMinutesUntilSlaBreach should return negative for breached alert")
        void getMinutesUntilSlaBreachShouldReturnNegative() {
            LocalDateTime createdAt = LocalDateTime.now().minusMinutes(30);
            long remaining = AlertLevel.CRITICAL.getMinutesUntilSlaBreach(createdAt);
            assertThat(remaining).isNegative();
        }

        @Test
        @DisplayName("getMinutesUntilSlaBreach should return SLA for null")
        void getMinutesUntilSlaBreachShouldReturnSlaForNull() {
            long remaining = AlertLevel.CRITICAL.getMinutesUntilSlaBreach(null);
            assertThat(remaining).isEqualTo(AlertLevel.CRITICAL.getSlaMinutes());
        }
    }

    @Nested
    @DisplayName("Thread Safety Tests")
    class ThreadSafetyTests {

        @Test
        @DisplayName("Enum should be safely accessed from multiple threads")
        void enumShouldBeSafelyAccessedConcurrently() throws InterruptedException {
            int threadCount = 100;
            CountDownLatch latch = new CountDownLatch(threadCount);
            AtomicInteger successCount = new AtomicInteger(0);

            for (int i = 0; i < threadCount; i++) {
                new Thread(() -> {
                    try {
                        // Read operations
                        AlertLevel level = AlertLevel.fromRiskScore(0.95);
                        assertThat(level).isEqualTo(AlertLevel.CRITICAL);
                        assertThat(level.getRecommendedAction()).isNotNull();
                        assertThat(level.requiresImmediateAction()).isTrue();
                        successCount.incrementAndGet();
                    } finally {
                        latch.countDown();
                    }
                }).start();
            }

            latch.await();
            assertThat(successCount.get()).isEqualTo(threadCount);
        }

        @Test
        @DisplayName("Enum values should maintain identity across threads")
        void enumValuesShouldMaintainIdentity() throws InterruptedException {
            int threadCount = 50;
            CountDownLatch latch = new CountDownLatch(threadCount);

            for (int i = 0; i < threadCount; i++) {
                new Thread(() -> {
                    try {
                        AlertLevel critical1 = AlertLevel.CRITICAL;
                        AlertLevel critical2 = AlertLevel.valueOf("CRITICAL");
                        assertThat(critical1).isSameAs(critical2);
                    } finally {
                        latch.countDown();
                    }
                }).start();
            }

            latch.await();
        }
    }

    @Nested
    @DisplayName("Edge Cases and Boundary Tests")
    class EdgeCasesTests {

        @Test
        @DisplayName("Should handle boundary risk score of exactly 0.9")
        void shouldHandleBoundaryRiskScore() {
            assertThat(AlertLevel.fromRiskScore(0.90)).isEqualTo(AlertLevel.CRITICAL);
            assertThat(AlertLevel.fromRiskScore(0.899999)).isEqualTo(AlertLevel.HIGH);
        }

        @Test
        @DisplayName("Should handle risk score above 1.0")
        void shouldHandleRiskScoreAboveOne() {
            assertThat(AlertLevel.fromRiskScore(1.5)).isEqualTo(AlertLevel.CRITICAL);
        }

        @Test
        @DisplayName("Should handle negative risk score")
        void shouldHandleNegativeRiskScore() {
            assertThat(AlertLevel.fromRiskScore(-0.5)).isEqualTo(AlertLevel.INFO);
        }
    }
}
