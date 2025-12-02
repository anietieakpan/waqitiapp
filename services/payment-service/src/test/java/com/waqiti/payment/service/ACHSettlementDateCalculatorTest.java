package com.waqiti.payment.service;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Comprehensive test suite for ACH Settlement Date Calculator.
 * Tests NACHA compliance and business day calculations.
 */
@DisplayName("ACH Settlement Date Calculator Tests")
class ACHSettlementDateCalculatorTest {

    private ACHSettlementDateCalculator calculator;

    @BeforeEach
    void setUp() {
        calculator = new ACHSettlementDateCalculator();
    }

    @Test
    @DisplayName("Should calculate standard ACH settlement as T+2 business days")
    void testStandardACHSettlement_NormalBusinessDay() {
        // Monday at 10 AM -> should settle Wednesday
        LocalDateTime initiationDate = LocalDateTime.of(2025, 11, 3, 10, 0); // Monday
        LocalDateTime settlement = calculator.calculateStandardACHSettlement(initiationDate);

        assertThat(settlement.toLocalDate()).isEqualTo(LocalDate.of(2025, 11, 5)); // Wednesday
    }

    @Test
    @DisplayName("Should skip weekends when calculating settlement date")
    void testStandardACHSettlement_SkipWeekend() {
        // Thursday at 10 AM -> should settle Monday (skip weekend)
        LocalDateTime initiationDate = LocalDateTime.of(2025, 11, 6, 10, 0); // Thursday
        LocalDateTime settlement = calculator.calculateStandardACHSettlement(initiationDate);

        assertThat(settlement.toLocalDate()).isEqualTo(LocalDate.of(2025, 11, 10)); // Monday
    }

    @Test
    @DisplayName("Should skip federal holidays when calculating settlement date")
    void testStandardACHSettlement_SkipHoliday() {
        // Nov 26 (Wed) at 10 AM -> should settle Dec 1 (Mon) skipping Thanksgiving
        LocalDateTime initiationDate = LocalDateTime.of(2025, 11, 26, 10, 0);
        LocalDateTime settlement = calculator.calculateStandardACHSettlement(initiationDate);

        // Should skip Thanksgiving (Nov 27) and weekend, settle on Dec 1
        assertThat(settlement.toLocalDate()).isEqualTo(LocalDate.of(2025, 12, 1));
    }

    @Test
    @DisplayName("Should add extra day if initiated after cutoff time")
    void testStandardACHSettlement_AfterCutoff() {
        // Monday at 6 PM (after 5 PM cutoff) -> should settle Thursday
        LocalDateTime initiationDate = LocalDateTime.of(2025, 11, 3, 18, 0);
        LocalDateTime settlement = calculator.calculateStandardACHSettlement(initiationDate);

        assertThat(settlement.toLocalDate()).isEqualTo(LocalDate.of(2025, 11, 6)); // Thursday (T+3)
    }

    @Test
    @DisplayName("Should process same-day ACH if before first cutoff")
    void testSameDayACH_BeforeFirstCutoff() {
        // Monday at 2 PM (before 2:45 PM cutoff) -> should settle same day
        LocalDateTime initiationDate = LocalDateTime.of(2025, 11, 3, 14, 0);
        LocalDateTime settlement = calculator.calculateSameDayACHSettlement(initiationDate);

        assertThat(settlement.toLocalDate()).isEqualTo(LocalDate.of(2025, 11, 3)); // Same day
        assertThat(settlement.toLocalTime()).isEqualTo(LocalTime.of(17, 0)); // 5 PM settlement
    }

    @Test
    @DisplayName("Should process same-day ACH if before second cutoff")
    void testSameDayACH_BeforeSecondCutoff() {
        // Monday at 4 PM (before 4:45 PM cutoff) -> should settle same day
        LocalDateTime initiationDate = LocalDateTime.of(2025, 11, 3, 16, 0);
        LocalDateTime settlement = calculator.calculateSameDayACHSettlement(initiationDate);

        assertThat(settlement.toLocalDate()).isEqualTo(LocalDate.of(2025, 11, 3)); // Same day
        assertThat(settlement.toLocalTime()).isEqualTo(LocalTime.of(18, 30)); // 6:30 PM settlement
    }

    @Test
    @DisplayName("Should move to next business day if same-day ACH after cutoff")
    void testSameDayACH_AfterCutoff() {
        // Monday at 5 PM (after 4:45 PM cutoff) -> should settle Tuesday
        LocalDateTime initiationDate = LocalDateTime.of(2025, 11, 3, 17, 0);
        LocalDateTime settlement = calculator.calculateSameDayACHSettlement(initiationDate);

        assertThat(settlement.toLocalDate()).isEqualTo(LocalDate.of(2025, 11, 4)); // Next business day
    }

    @Test
    @DisplayName("Should move same-day ACH to next business day if initiated on weekend")
    void testSameDayACH_OnWeekend() {
        // Saturday at 10 AM -> should settle Monday
        LocalDateTime initiationDate = LocalDateTime.of(2025, 11, 8, 10, 0); // Saturday
        LocalDateTime settlement = calculator.calculateSameDayACHSettlement(initiationDate);

        assertThat(settlement.toLocalDate()).isEqualTo(LocalDate.of(2025, 11, 10)); // Monday
    }

    @ParameterizedTest
    @DisplayName("Should correctly identify business days")
    @CsvSource({
        "2025-11-03, true",  // Monday
        "2025-11-04, true",  // Tuesday
        "2025-11-08, false", // Saturday
        "2025-11-09, false", // Sunday
        "2025-11-11, false", // Veterans Day
        "2025-11-27, false", // Thanksgiving
        "2025-12-25, false"  // Christmas
    })
    void testIsBusinessDay(String dateStr, boolean expected) {
        LocalDate date = LocalDate.parse(dateStr);
        assertThat(calculator.isBusinessDay(date)).isEqualTo(expected);
    }

    @Test
    @DisplayName("Should calculate correct number of business days between dates")
    void testCalculateBusinessDaysBetween() {
        // Nov 3 (Mon) to Nov 10 (Mon) = 5 business days
        LocalDate start = LocalDate.of(2025, 11, 3);
        LocalDate end = LocalDate.of(2025, 11, 10);

        int businessDays = calculator.calculateBusinessDaysBetween(start, end);

        assertThat(businessDays).isEqualTo(5);
    }

    @Test
    @DisplayName("Should skip weekend when adding business days")
    void testAddBusinessDays_SkipWeekend() {
        LocalDate start = LocalDate.of(2025, 11, 7); // Friday
        LocalDate result = calculator.addBusinessDays(start, 2);

        assertThat(result).isEqualTo(LocalDate.of(2025, 11, 11)); // Tuesday (skip Sat, Sun, Veterans Day Mon)
    }

    @Test
    @DisplayName("Should have federal holidays configured")
    void testFederalHolidaysConfigured() {
        assertThat(calculator.getFederalHolidays()).isNotEmpty();
        assertThat(calculator.getFederalHolidays()).contains(
            LocalDate.of(2025, 12, 25), // Christmas
            LocalDate.of(2025, 11, 27), // Thanksgiving
            LocalDate.of(2025, 7, 4)    // Independence Day
        );
    }

    @Test
    @DisplayName("Should handle year-end settlement correctly")
    void testYearEndSettlement() {
        // Dec 30 (Tue) at 10 AM -> should settle Jan 2 (Fri) skipping New Year
        LocalDateTime initiationDate = LocalDateTime.of(2025, 12, 30, 10, 0);
        LocalDateTime settlement = calculator.calculateStandardACHSettlement(initiationDate);

        // Skip Dec 31 (Wed), Jan 1 (Thu - New Year), settle Jan 2 (Fri)
        assertThat(settlement.toLocalDate()).isEqualTo(LocalDate.of(2026, 1, 2));
    }
}
