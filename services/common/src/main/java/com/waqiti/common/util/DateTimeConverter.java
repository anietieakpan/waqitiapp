package com.waqiti.common.util;

import java.time.*;
import java.util.Date;

/**
 * Date/Time Conversion Utility
 *
 * Provides consistent conversion between different temporal types
 * used across the Waqiti platform.
 *
 * @author Waqiti Platform Engineering
 * @version 1.0.0
 * @since 2025-10-17
 */
public final class DateTimeConverter {

    private static final ZoneId DEFAULT_ZONE = ZoneOffset.UTC;
    private static final ZoneId SYSTEM_ZONE = ZoneId.systemDefault();

    private DateTimeConverter() {
        throw new UnsupportedOperationException("Utility class");
    }

    // ========== Instant to LocalDateTime Conversions ==========

    /**
     * Converts Instant to LocalDateTime using UTC timezone
     */
    public static LocalDateTime toLocalDateTime(Instant instant) {
        if (instant == null) {
            return null;
        }
        return LocalDateTime.ofInstant(instant, DEFAULT_ZONE);
    }

    /**
     * Converts Instant to LocalDateTime using system default timezone
     */
    public static LocalDateTime toLocalDateTimeSystemZone(Instant instant) {
        if (instant == null) {
            return null;
        }
        return LocalDateTime.ofInstant(instant, SYSTEM_ZONE);
    }

    /**
     * Converts Instant to LocalDateTime using specified timezone
     */
    public static LocalDateTime toLocalDateTime(Instant instant, ZoneId zoneId) {
        if (instant == null) {
            return null;
        }
        return LocalDateTime.ofInstant(instant, zoneId != null ? zoneId : DEFAULT_ZONE);
    }

    // ========== LocalDateTime to Instant Conversions ==========

    /**
     * Converts LocalDateTime to Instant assuming UTC timezone
     */
    public static Instant toInstant(LocalDateTime localDateTime) {
        if (localDateTime == null) {
            return null;
        }
        return localDateTime.toInstant(ZoneOffset.UTC);
    }

    /**
     * Converts LocalDateTime to Instant using system default timezone
     */
    public static Instant toInstantSystemZone(LocalDateTime localDateTime) {
        if (localDateTime == null) {
            return null;
        }
        return localDateTime.atZone(SYSTEM_ZONE).toInstant();
    }

    /**
     * Converts LocalDateTime to Instant using specified timezone
     */
    public static Instant toInstant(LocalDateTime localDateTime, ZoneId zoneId) {
        if (localDateTime == null) {
            return null;
        }
        return localDateTime.atZone(zoneId != null ? zoneId : DEFAULT_ZONE).toInstant();
    }

    // ========== Instant to LocalDate Conversions ==========

    /**
     * Converts Instant to LocalDate using UTC timezone
     */
    public static LocalDate toLocalDate(Instant instant) {
        if (instant == null) {
            return null;
        }
        return instant.atZone(DEFAULT_ZONE).toLocalDate();
    }

    /**
     * Converts Instant to LocalDate using specified timezone
     */
    public static LocalDate toLocalDate(Instant instant, ZoneId zoneId) {
        if (instant == null) {
            return null;
        }
        return instant.atZone(zoneId != null ? zoneId : DEFAULT_ZONE).toLocalDate();
    }

    // ========== LocalDate to Instant Conversions ==========

    /**
     * Converts LocalDate to Instant at start of day in UTC
     */
    public static Instant toInstantStartOfDay(LocalDate localDate) {
        if (localDate == null) {
            return null;
        }
        return localDate.atStartOfDay(DEFAULT_ZONE).toInstant();
    }

    /**
     * Converts LocalDate to Instant at end of day in UTC
     */
    public static Instant toInstantEndOfDay(LocalDate localDate) {
        if (localDate == null) {
            return null;
        }
        return localDate.atTime(LocalTime.MAX).toInstant(ZoneOffset.UTC);
    }

    // ========== Date (java.util.Date) Conversions ==========

    /**
     * Converts java.util.Date to Instant
     */
    public static Instant toInstant(Date date) {
        if (date == null) {
            return null;
        }
        return date.toInstant();
    }

    /**
     * Converts Instant to java.util.Date
     */
    public static Date toDate(Instant instant) {
        if (instant == null) {
            return null;
        }
        return Date.from(instant);
    }

    /**
     * Converts LocalDateTime to java.util.Date
     */
    public static Date toDate(LocalDateTime localDateTime) {
        if (localDateTime == null) {
            return null;
        }
        return Date.from(toInstant(localDateTime));
    }

    /**
     * Converts java.util.Date to LocalDateTime
     */
    public static LocalDateTime toLocalDateTime(Date date) {
        if (date == null) {
            return null;
        }
        return toLocalDateTime(date.toInstant());
    }

    // ========== Epoch Milliseconds Conversions ==========

    /**
     * Converts epoch milliseconds to Instant
     */
    public static Instant toInstant(long epochMillis) {
        return Instant.ofEpochMilli(epochMillis);
    }

    /**
     * Converts Instant to epoch milliseconds
     */
    public static long toEpochMillis(Instant instant) {
        if (instant == null) {
            return 0L;
        }
        return instant.toEpochMilli();
    }

    /**
     * Converts LocalDateTime to epoch milliseconds (assumes UTC)
     */
    public static long toEpochMillis(LocalDateTime localDateTime) {
        if (localDateTime == null) {
            return 0L;
        }
        return toInstant(localDateTime).toEpochMilli();
    }

    // ========== ZonedDateTime Conversions ==========

    /**
     * Converts Instant to ZonedDateTime using UTC
     */
    public static ZonedDateTime toZonedDateTime(Instant instant) {
        if (instant == null) {
            return null;
        }
        return ZonedDateTime.ofInstant(instant, DEFAULT_ZONE);
    }

    /**
     * Converts Instant to ZonedDateTime using specified zone
     */
    public static ZonedDateTime toZonedDateTime(Instant instant, ZoneId zoneId) {
        if (instant == null) {
            return null;
        }
        return ZonedDateTime.ofInstant(instant, zoneId != null ? zoneId : DEFAULT_ZONE);
    }

    /**
     * Converts ZonedDateTime to Instant
     */
    public static Instant toInstant(ZonedDateTime zonedDateTime) {
        if (zonedDateTime == null) {
            return null;
        }
        return zonedDateTime.toInstant();
    }

    // ========== Utility Methods ==========

    /**
     * Gets current Instant
     */
    public static Instant nowInstant() {
        return Instant.now();
    }

    /**
     * Gets current LocalDateTime in UTC
     */
    public static LocalDateTime nowLocalDateTime() {
        return LocalDateTime.now(DEFAULT_ZONE);
    }

    /**
     * Gets current LocalDate in UTC
     */
    public static LocalDate nowLocalDate() {
        return LocalDate.now(DEFAULT_ZONE);
    }

    /**
     * Checks if two Instants are on the same day in UTC
     */
    public static boolean isSameDay(Instant instant1, Instant instant2) {
        if (instant1 == null || instant2 == null) {
            return false;
        }
        LocalDate date1 = toLocalDate(instant1);
        LocalDate date2 = toLocalDate(instant2);
        return date1.equals(date2);
    }

    /**
     * Checks if Instant is between start and end (inclusive)
     */
    public static boolean isBetween(Instant instant, Instant start, Instant end) {
        if (instant == null || start == null || end == null) {
            return false;
        }
        return !instant.isBefore(start) && !instant.isAfter(end);
    }

    /**
     * Adds days to Instant
     */
    public static Instant plusDays(Instant instant, long days) {
        if (instant == null) {
            return null;
        }
        return instant.plus(Duration.ofDays(days));
    }

    /**
     * Adds hours to Instant
     */
    public static Instant plusHours(Instant instant, long hours) {
        if (instant == null) {
            return null;
        }
        return instant.plus(Duration.ofHours(hours));
    }

    /**
     * Adds minutes to Instant
     */
    public static Instant plusMinutes(Instant instant, long minutes) {
        if (instant == null) {
            return null;
        }
        return instant.plus(Duration.ofMinutes(minutes));
    }

    /**
     * Gets start of day for given Instant in UTC
     */
    public static Instant startOfDay(Instant instant) {
        if (instant == null) {
            return null;
        }
        return toLocalDate(instant).atStartOfDay(DEFAULT_ZONE).toInstant();
    }

    /**
     * Gets end of day for given Instant in UTC
     */
    public static Instant endOfDay(Instant instant) {
        if (instant == null) {
            return null;
        }
        return toLocalDate(instant).atTime(LocalTime.MAX).toInstant(ZoneOffset.UTC);
    }
}
