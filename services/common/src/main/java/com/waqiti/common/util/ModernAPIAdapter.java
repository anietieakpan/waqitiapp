package com.waqiti.common.util;

import lombok.extern.slf4j.Slf4j;
import org.springframework.util.StringUtils;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.text.DecimalFormat;
import java.text.NumberFormat;
import java.time.*;
import java.time.format.DateTimeFormatter;
import java.time.temporal.ChronoUnit;
import java.util.*;
import java.util.concurrent.TimeUnit;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * Modern API adapter to replace deprecated Java APIs
 * Provides migration path from legacy code to modern Java APIs
 */
@Slf4j
public class ModernAPIAdapter {

    /**
     * Replace deprecated Date with LocalDateTime
     */
    public static LocalDateTime toLocalDateTime(Date date) {
        if (date == null) return null;
        return LocalDateTime.ofInstant(date.toInstant(), ZoneId.systemDefault());
    }

    /**
     * Convert LocalDateTime to Date for legacy APIs
     */
    public static Date toDate(LocalDateTime localDateTime) {
        if (localDateTime == null) return null;
        return Date.from(localDateTime.atZone(ZoneId.systemDefault()).toInstant());
    }

    /**
     * Replace deprecated Calendar with LocalDateTime operations
     */
    public static LocalDateTime addToDateTime(LocalDateTime dateTime, int field, int amount) {
        if (dateTime == null) return null;
        
        return switch (field) {
            case Calendar.YEAR -> dateTime.plusYears(amount);
            case Calendar.MONTH -> dateTime.plusMonths(amount);
            case Calendar.DAY_OF_MONTH, Calendar.DAY_OF_YEAR -> dateTime.plusDays(amount);
            case Calendar.HOUR, Calendar.HOUR_OF_DAY -> dateTime.plusHours(amount);
            case Calendar.MINUTE -> dateTime.plusMinutes(amount);
            case Calendar.SECOND -> dateTime.plusSeconds(amount);
            case Calendar.MILLISECOND -> dateTime.plus(amount, ChronoUnit.MILLIS);
            default -> dateTime;
        };
    }

    /**
     * Modern string manipulation to replace deprecated StringTokenizer
     */
    public static List<String> tokenize(String str, String delimiter) {
        if (!StringUtils.hasText(str)) {
            return Collections.emptyList();
        }
        return Arrays.stream(str.split(delimiter))
                .filter(StringUtils::hasText)
                .collect(Collectors.toList());
    }

    /**
     * Replace deprecated Vector with ArrayList
     */
    public static <T> List<T> createThreadSafeList() {
        return Collections.synchronizedList(new ArrayList<>());
    }

    /**
     * Replace deprecated Hashtable with ConcurrentHashMap
     */
    public static <K, V> Map<K, V> createThreadSafeMap() {
        return new java.util.concurrent.ConcurrentHashMap<>();
    }

    /**
     * Replace deprecated Stack with Deque
     */
    public static <T> Deque<T> createStack() {
        return new ArrayDeque<>();
    }

    /**
     * Modern number formatting to replace deprecated NumberFormat methods
     */
    public static String formatCurrency(BigDecimal amount, Locale locale) {
        if (amount == null) return "0.00";
        
        NumberFormat formatter = NumberFormat.getCurrencyInstance(
            locale != null ? locale : Locale.getDefault()
        );
        return formatter.format(amount);
    }

    /**
     * Format decimal with specified precision
     */
    public static String formatDecimal(BigDecimal value, int scale, RoundingMode roundingMode) {
        if (value == null) return "0";
        
        BigDecimal scaled = value.setScale(scale, roundingMode != null ? roundingMode : RoundingMode.HALF_UP);
        DecimalFormat df = new DecimalFormat();
        df.setMaximumFractionDigits(scale);
        df.setMinimumFractionDigits(scale);
        df.setGroupingUsed(false);
        
        return df.format(scaled);
    }

    /**
     * Replace deprecated Thread methods
     */
    public static void sleep(long millis) {
        try {
            TimeUnit.MILLISECONDS.sleep(millis);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            log.warn("Sleep interrupted", e);
        }
    }

    /**
     * Modern file operations replacing deprecated File methods
     */
    public static class FileOperations {
        
        public static boolean createDirectory(java.nio.file.Path path) {
            try {
                java.nio.file.Files.createDirectories(path);
                return true;
            } catch (Exception e) {
                log.error("Failed to create directory: {}", path, e);
                return false;
            }
        }
        
        public static boolean deleteFile(java.nio.file.Path path) {
            try {
                return java.nio.file.Files.deleteIfExists(path);
            } catch (Exception e) {
                log.error("Failed to delete file: {}", path, e);
                return false;
            }
        }
        
        public static List<String> readLines(java.nio.file.Path path) {
            try {
                return java.nio.file.Files.readAllLines(path);
            } catch (Exception e) {
                log.error("Failed to read file: {}", path, e);
                return Collections.emptyList();
            }
        }
        
        public static boolean writeLines(java.nio.file.Path path, List<String> lines) {
            try {
                java.nio.file.Files.write(path, lines);
                return true;
            } catch (Exception e) {
                log.error("Failed to write file: {}", path, e);
                return false;
            }
        }
    }

    /**
     * Replace deprecated URL encoding methods
     */
    public static String urlEncode(String value) {
        if (value == null) return "";
        try {
            return java.net.URLEncoder.encode(value, java.nio.charset.StandardCharsets.UTF_8);
        } catch (Exception e) {
            log.error("Failed to URL encode: {}", value, e);
            return value;
        }
    }

    public static String urlDecode(String value) {
        if (value == null) return "";
        try {
            return java.net.URLDecoder.decode(value, java.nio.charset.StandardCharsets.UTF_8);
        } catch (Exception e) {
            log.error("Failed to URL decode: {}", value, e);
            return value;
        }
    }

    /**
     * Replace deprecated Base64 encoding
     */
    public static String base64Encode(byte[] data) {
        if (data == null) return "";
        return Base64.getEncoder().encodeToString(data);
    }

    public static byte[] base64Decode(String encoded) {
        if (encoded == null) return new byte[0];
        try {
            return Base64.getDecoder().decode(encoded);
        } catch (IllegalArgumentException e) {
            log.error("Failed to decode base64: {}", encoded, e);
            return new byte[0];
        }
    }

    /**
     * Replace deprecated Optional methods
     */
    public static <T> T getOrElse(Optional<T> optional, T defaultValue) {
        return optional.orElse(defaultValue);
    }

    public static <T> T getOrElseGet(Optional<T> optional, java.util.function.Supplier<T> supplier) {
        return optional.orElseGet(supplier);
    }

    public static <T> Optional<T> filter(Optional<T> optional, java.util.function.Predicate<T> predicate) {
        return optional.filter(predicate);
    }

    /**
     * Replace deprecated System.currentTimeMillis() for elapsed time
     */
    public static class StopWatch {
        private final long startNanos;
        
        public StopWatch() {
            this.startNanos = System.nanoTime();
        }
        
        public long getElapsedMillis() {
            return TimeUnit.NANOSECONDS.toMillis(System.nanoTime() - startNanos);
        }
        
        public long getElapsedSeconds() {
            return TimeUnit.NANOSECONDS.toSeconds(System.nanoTime() - startNanos);
        }
    }

    /**
     * Replace deprecated Runtime.exec with ProcessBuilder
     */
    public static Process executeCommand(String... command) throws java.io.IOException {
        ProcessBuilder pb = new ProcessBuilder(command);
        pb.redirectErrorStream(true);
        return pb.start();
    }

    /**
     * Replace deprecated finalize() with AutoCloseable
     */
    public static abstract class ManagedResource implements AutoCloseable {
        protected abstract void cleanup();
        
        @Override
        public void close() {
            try {
                cleanup();
            } catch (Exception e) {
                log.error("Failed to cleanup resource", e);
            }
        }
    }

    /**
     * Modern collections operations
     */
    public static class Collections {
        
        public static <T> List<T> unmodifiableList(List<T> list) {
            return list == null ? java.util.Collections.emptyList() : 
                   java.util.Collections.unmodifiableList(new ArrayList<>(list));
        }
        
        public static <K, V> Map<K, V> unmodifiableMap(Map<K, V> map) {
            return map == null ? java.util.Collections.emptyMap() : 
                   java.util.Collections.unmodifiableMap(new HashMap<>(map));
        }
        
        public static <T> Set<T> unmodifiableSet(Set<T> set) {
            return set == null ? java.util.Collections.emptySet() : 
                   java.util.Collections.unmodifiableSet(new HashSet<>(set));
        }
        
        public static <T> List<T> synchronizedList(List<T> list) {
            return java.util.Collections.synchronizedList(
                list != null ? list : new ArrayList<>()
            );
        }
        
        public static <K, V> Map<K, V> synchronizedMap(Map<K, V> map) {
            return java.util.Collections.synchronizedMap(
                map != null ? map : new HashMap<>()
            );
        }
    }

    /**
     * Replace deprecated assert with Objects.requireNonNull
     */
    public static <T> T requireNonNull(T obj, String message) {
        return Objects.requireNonNull(obj, message);
    }

    public static void requireTrue(boolean condition, String message) {
        if (!condition) {
            throw new IllegalArgumentException(message);
        }
    }

    public static void requireFalse(boolean condition, String message) {
        if (condition) {
            throw new IllegalArgumentException(message);
        }
    }

    /**
     * Modern stream operations
     */
    public static <T, R> List<R> map(Collection<T> collection, Function<T, R> mapper) {
        if (collection == null) return Collections.emptyList();
        return collection.stream()
                .map(mapper)
                .collect(Collectors.toList());
    }

    public static <T> List<T> filter(Collection<T> collection, java.util.function.Predicate<T> predicate) {
        if (collection == null) return Collections.emptyList();
        return collection.stream()
                .filter(predicate)
                .collect(Collectors.toList());
    }

    public static <T> Optional<T> findFirst(Collection<T> collection, java.util.function.Predicate<T> predicate) {
        if (collection == null) return Optional.empty();
        return collection.stream()
                .filter(predicate)
                .findFirst();
    }
}