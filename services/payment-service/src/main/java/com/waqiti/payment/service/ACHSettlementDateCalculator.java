package com.waqiti.payment.service;

import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.HashSet;
import java.util.Set;

/**
 * NACHA-COMPLIANT ACH SETTLEMENT DATE CALCULATOR
 *
 * Calculates accurate ACH settlement dates following NACHA (National Automated Clearing House Association) rules.
 * This service addresses CRITICAL P0 issue identified in forensic audit where settlement dates were not calculated.
 *
 * ACH Settlement Rules:
 * - Standard ACH: T+2 business days
 * - Same-Day ACH: Same day if before cutoff (2:45 PM ET for first window, 4:45 PM ET for second window)
 * - Business days exclude weekends and federal banking holidays
 * - Cutoff time: 5:00 PM ET for standard ACH
 *
 * @author Waqiti Payment Team
 * @since 1.0.0
 * @compliance NACHA Operating Rules
 */
@Slf4j
@Service
public class ACHSettlementDateCalculator {

    // NACHA standard settlement window
    private static final int STANDARD_SETTLEMENT_DAYS = 2;
    
    // Same-day ACH cutoff times (Eastern Time)
    private static final LocalTime SAME_DAY_CUTOFF_FIRST = LocalTime.of(14, 45); // 2:45 PM ET
    private static final LocalTime SAME_DAY_CUTOFF_SECOND = LocalTime.of(16, 45); // 4:45 PM ET
    private static final LocalTime STANDARD_CUTOFF = LocalTime.of(17, 0); // 5:00 PM ET

    // Federal banking holidays for 2025-2026 (should be loaded from config/database in production)
    private static final Set<LocalDate> FEDERAL_HOLIDAYS = initializeFederalHolidays();

    /**
     * Calculate estimated settlement date for a standard ACH transfer.
     * Uses T+2 business day rule.
     *
     * @param initiationDateTime the date/time when ACH transfer was initiated
     * @return estimated settlement date
     */
    public LocalDateTime calculateStandardACHSettlement(LocalDateTime initiationDateTime) {
        log.debug("Calculating standard ACH settlement date for initiation: {}", initiationDateTime);
        
        LocalDate settlementDate = initiationDateTime.toLocalDate();
        int businessDaysToAdd = STANDARD_SETTLEMENT_DAYS;

        // If initiated after cutoff time, start counting from next business day
        if (initiationDateTime.toLocalTime().isAfter(STANDARD_CUTOFF)) {
            log.debug("Initiated after cutoff time ({}), starting from next business day", STANDARD_CUTOFF);
            businessDaysToAdd++;
        }

        // Add business days
        settlementDate = addBusinessDays(settlementDate, businessDaysToAdd);

        LocalDateTime result = settlementDate.atTime(9, 0); // Settlement typically processes at 9 AM
        log.info("Standard ACH settlement date calculated: {} (initiated: {})", result, initiationDateTime);
        
        return result;
    }

    /**
     * Calculate estimated settlement date for same-day ACH transfer.
     *
     * @param initiationDateTime the date/time when ACH transfer was initiated
     * @return estimated settlement date (same day if before cutoff, otherwise next business day)
     */
    public LocalDateTime calculateSameDayACHSettlement(LocalDateTime initiationDateTime) {
        log.debug("Calculating same-day ACH settlement date for initiation: {}", initiationDateTime);
        
        LocalTime initiationTime = initiationDateTime.toLocalTime();
        LocalDate initiationDate = initiationDateTime.toLocalDate();

        // Check if today is a business day
        if (!isBusinessDay(initiationDate)) {
            LocalDate nextBusinessDay = addBusinessDays(initiationDate, 1);
            LocalDateTime result = nextBusinessDay.atTime(9, 0);
            log.info("Same-day ACH initiated on non-business day, settlement moved to next business day: {}", result);
            return result;
        }

        // Check same-day ACH cutoff windows
        if (initiationTime.isBefore(SAME_DAY_CUTOFF_FIRST)) {
            // First window - settles same day
            LocalDateTime result = initiationDate.atTime(17, 0); // Settles by 5 PM
            log.info("Same-day ACH within first cutoff window, settlement: {}", result);
            return result;
        } else if (initiationTime.isBefore(SAME_DAY_CUTOFF_SECOND)) {
            // Second window - settles same day
            LocalDateTime result = initiationDate.atTime(18, 30); // Settles by 6:30 PM
            log.info("Same-day ACH within second cutoff window, settlement: {}", result);
            return result;
        } else {
            // After cutoff - next business day
            LocalDate nextBusinessDay = addBusinessDays(initiationDate, 1);
            LocalDateTime result = nextBusinessDay.atTime(9, 0);
            log.info("Same-day ACH after cutoff, settlement moved to next business day: {}", result);
            return result;
        }
    }

    /**
     * Add specified number of business days to a date, skipping weekends and federal holidays.
     *
     * @param startDate the starting date
     * @param businessDays number of business days to add
     * @return resulting date after adding business days
     */
    public LocalDate addBusinessDays(LocalDate startDate, int businessDays) {
        LocalDate currentDate = startDate;
        int daysAdded = 0;

        while (daysAdded < businessDays) {
            currentDate = currentDate.plusDays(1);
            
            if (isBusinessDay(currentDate)) {
                daysAdded++;
            } else {
                log.debug("Skipping non-business day: {}", currentDate);
            }
        }

        return currentDate;
    }

    /**
     * Check if a date is a business day (not weekend, not federal holiday).
     *
     * @param date the date to check
     * @return true if business day, false otherwise
     */
    public boolean isBusinessDay(LocalDate date) {
        // Check if weekend
        if (date.getDayOfWeek() == DayOfWeek.SATURDAY || date.getDayOfWeek() == DayOfWeek.SUNDAY) {
            return false;
        }

        // Check if federal holiday
        if (FEDERAL_HOLIDAYS.contains(date)) {
            log.debug("Date {} is a federal banking holiday", date);
            return false;
        }

        return true;
    }

    /**
     * Calculate the number of business days between two dates.
     *
     * @param startDate start date (inclusive)
     * @param endDate end date (exclusive)
     * @return number of business days between the dates
     */
    public int calculateBusinessDaysBetween(LocalDate startDate, LocalDate endDate) {
        int businessDays = 0;
        LocalDate currentDate = startDate;

        while (currentDate.isBefore(endDate)) {
            if (isBusinessDay(currentDate)) {
                businessDays++;
            }
            currentDate = currentDate.plusDays(1);
        }

        return businessDays;
    }

    /**
     * Initialize federal banking holidays.
     * In production, this should be loaded from configuration or database.
     *
     * @return set of federal holiday dates
     */
    private static Set<LocalDate> initializeFederalHolidays() {
        Set<LocalDate> holidays = new HashSet<>();

        // 2025 Federal Banking Holidays
        holidays.add(LocalDate.of(2025, 1, 1));   // New Year's Day
        holidays.add(LocalDate.of(2025, 1, 20));  // Martin Luther King Jr. Day
        holidays.add(LocalDate.of(2025, 2, 17));  // Presidents' Day
        holidays.add(LocalDate.of(2025, 5, 26));  // Memorial Day
        holidays.add(LocalDate.of(2025, 6, 19));  // Juneteenth
        holidays.add(LocalDate.of(2025, 7, 4));   // Independence Day
        holidays.add(LocalDate.of(2025, 9, 1));   // Labor Day
        holidays.add(LocalDate.of(2025, 10, 13)); // Columbus Day
        holidays.add(LocalDate.of(2025, 11, 11)); // Veterans Day
        holidays.add(LocalDate.of(2025, 11, 27)); // Thanksgiving
        holidays.add(LocalDate.of(2025, 12, 25)); // Christmas

        // 2026 Federal Banking Holidays
        holidays.add(LocalDate.of(2026, 1, 1));   // New Year's Day
        holidays.add(LocalDate.of(2026, 1, 19));  // Martin Luther King Jr. Day
        holidays.add(LocalDate.of(2026, 2, 16));  // Presidents' Day
        holidays.add(LocalDate.of(2026, 5, 25));  // Memorial Day
        holidays.add(LocalDate.of(2026, 6, 19));  // Juneteenth
        holidays.add(LocalDate.of(2026, 7, 3));   // Independence Day (observed Friday)
        holidays.add(LocalDate.of(2026, 9, 7));   // Labor Day
        holidays.add(LocalDate.of(2026, 10, 12)); // Columbus Day
        holidays.add(LocalDate.of(2026, 11, 11)); // Veterans Day
        holidays.add(LocalDate.of(2026, 11, 26)); // Thanksgiving
        holidays.add(LocalDate.of(2026, 12, 25)); // Christmas

        return holidays;
    }

    /**
     * Get all configured federal banking holidays.
     *
     * @return set of federal holiday dates
     */
    public Set<LocalDate> getFederalHolidays() {
        return new HashSet<>(FEDERAL_HOLIDAYS);
    }
}
