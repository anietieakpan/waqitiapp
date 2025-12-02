package com.waqiti.payment.ach;

import lombok.extern.slf4j.Slf4j;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.stereotype.Service;

import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.Month;
import java.time.temporal.TemporalAdjusters;
import java.util.HashSet;
import java.util.Set;

/**
 * Federal Holiday Service
 *
 * Determines US Federal Reserve Bank holidays for ACH processing.
 * ACH transactions cannot settle on weekends or federal holidays.
 *
 * Federal Holidays Observed by Federal Reserve:
 * 1. New Year's Day - January 1
 * 2. Martin Luther King Jr. Day - Third Monday in January
 * 3. Presidents' Day - Third Monday in February
 * 4. Memorial Day - Last Monday in May
 * 5. Juneteenth - June 19
 * 6. Independence Day - July 4
 * 7. Labor Day - First Monday in September
 * 8. Columbus Day - Second Monday in October
 * 9. Veterans Day - November 11
 * 10. Thanksgiving Day - Fourth Thursday in November
 * 11. Christmas Day - December 25
 *
 * If holiday falls on Saturday, observed on Friday.
 * If holiday falls on Sunday, observed on Monday.
 *
 * @author Waqiti Payment Team
 * @version 2.0
 * @since 2025-10-16
 */
@Slf4j
@Service
public class FederalHolidayService {

    /**
     * Checks if given date is a Federal Reserve Bank holiday
     *
     * @param date Date to check
     * @return true if date is a federal holiday
     */
    @Cacheable(value = "federalHolidays", key = "#date")
    public boolean isFederalHoliday(LocalDate date) {
        if (date == null) {
            return false;
        }

        Set<LocalDate> holidays = getFederalHolidays(date.getYear());
        return holidays.contains(date);
    }

    /**
     * Gets all federal holidays for a given year
     *
     * @param year Year to get holidays for
     * @return Set of federal holiday dates
     */
    public Set<LocalDate> getFederalHolidays(int year) {
        Set<LocalDate> holidays = new HashSet<>();

        // 1. New Year's Day (January 1)
        holidays.add(observedDate(LocalDate.of(year, Month.JANUARY, 1)));

        // 2. Martin Luther King Jr. Day (Third Monday in January)
        holidays.add(LocalDate.of(year, Month.JANUARY, 1)
                .with(TemporalAdjusters.dayOfWeekInMonth(3, DayOfWeek.MONDAY)));

        // 3. Presidents' Day (Third Monday in February)
        holidays.add(LocalDate.of(year, Month.FEBRUARY, 1)
                .with(TemporalAdjusters.dayOfWeekInMonth(3, DayOfWeek.MONDAY)));

        // 4. Memorial Day (Last Monday in May)
        holidays.add(LocalDate.of(year, Month.MAY, 1)
                .with(TemporalAdjusters.lastInMonth(DayOfWeek.MONDAY)));

        // 5. Juneteenth (June 19) - Federal holiday since 2021
        if (year >= 2021) {
            holidays.add(observedDate(LocalDate.of(year, Month.JUNE, 19)));
        }

        // 6. Independence Day (July 4)
        holidays.add(observedDate(LocalDate.of(year, Month.JULY, 4)));

        // 7. Labor Day (First Monday in September)
        holidays.add(LocalDate.of(year, Month.SEPTEMBER, 1)
                .with(TemporalAdjusters.firstInMonth(DayOfWeek.MONDAY)));

        // 8. Columbus Day (Second Monday in October)
        holidays.add(LocalDate.of(year, Month.OCTOBER, 1)
                .with(TemporalAdjusters.dayOfWeekInMonth(2, DayOfWeek.MONDAY)));

        // 9. Veterans Day (November 11)
        holidays.add(observedDate(LocalDate.of(year, Month.NOVEMBER, 11)));

        // 10. Thanksgiving Day (Fourth Thursday in November)
        holidays.add(LocalDate.of(year, Month.NOVEMBER, 1)
                .with(TemporalAdjusters.dayOfWeekInMonth(4, DayOfWeek.THURSDAY)));

        // 11. Christmas Day (December 25)
        holidays.add(observedDate(LocalDate.of(year, Month.DECEMBER, 25)));

        log.debug("Federal holidays for {}: {}", year, holidays.size());
        return holidays;
    }

    /**
     * Adjusts holiday date based on weekend observation rules
     *
     * If holiday falls on Saturday, observed on Friday.
     * If holiday falls on Sunday, observed on Monday.
     *
     * @param date Actual holiday date
     * @return Observed holiday date
     */
    private LocalDate observedDate(LocalDate date) {
        DayOfWeek dayOfWeek = date.getDayOfWeek();

        if (dayOfWeek == DayOfWeek.SATURDAY) {
            return date.minusDays(1); // Observe on Friday
        } else if (dayOfWeek == DayOfWeek.SUNDAY) {
            return date.plusDays(1); // Observe on Monday
        }

        return date;
    }

    /**
     * Gets next business day (skips weekends and federal holidays)
     *
     * @param fromDate Starting date
     * @return Next business day
     */
    public LocalDate getNextBusinessDay(LocalDate fromDate) {
        LocalDate nextDay = fromDate.plusDays(1);

        while (isWeekend(nextDay) || isFederalHoliday(nextDay)) {
            nextDay = nextDay.plusDays(1);
        }

        return nextDay;
    }

    /**
     * Gets previous business day (skips weekends and federal holidays)
     *
     * @param fromDate Starting date
     * @return Previous business day
     */
    public LocalDate getPreviousBusinessDay(LocalDate fromDate) {
        LocalDate prevDay = fromDate.minusDays(1);

        while (isWeekend(prevDay) || isFederalHoliday(prevDay)) {
            prevDay = prevDay.minusDays(1);
        }

        return prevDay;
    }

    /**
     * Checks if date is a weekend (Saturday or Sunday)
     *
     * @param date Date to check
     * @return true if weekend
     */
    public boolean isWeekend(LocalDate date) {
        DayOfWeek dayOfWeek = date.getDayOfWeek();
        return dayOfWeek == DayOfWeek.SATURDAY || dayOfWeek == DayOfWeek.SUNDAY;
    }

    /**
     * Checks if date is a business day (not weekend, not federal holiday)
     *
     * @param date Date to check
     * @return true if business day
     */
    public boolean isBusinessDay(LocalDate date) {
        return !isWeekend(date) && !isFederalHoliday(date);
    }

    /**
     * Calculates number of business days between two dates (inclusive)
     *
     * @param startDate Start date
     * @param endDate End date
     * @return Number of business days
     */
    public int countBusinessDays(LocalDate startDate, LocalDate endDate) {
        int businessDays = 0;
        LocalDate currentDate = startDate;

        while (!currentDate.isAfter(endDate)) {
            if (isBusinessDay(currentDate)) {
                businessDays++;
            }
            currentDate = currentDate.plusDays(1);
        }

        return businessDays;
    }

    /**
     * Adds business days to a date
     *
     * @param fromDate Starting date
     * @param businessDays Number of business days to add
     * @return Resulting date
     */
    public LocalDate addBusinessDays(LocalDate fromDate, int businessDays) {
        LocalDate result = fromDate;
        int daysAdded = 0;

        while (daysAdded < businessDays) {
            result = result.plusDays(1);
            if (isBusinessDay(result)) {
                daysAdded++;
            }
        }

        return result;
    }
}
