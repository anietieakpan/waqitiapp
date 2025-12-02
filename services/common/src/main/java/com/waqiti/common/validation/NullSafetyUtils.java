package com.waqiti.common.validation;

import org.springframework.lang.NonNull;
import org.springframework.lang.Nullable;
import org.springframework.util.StringUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.math.BigDecimal;
import java.util.*;
import java.util.function.Consumer;
import java.util.function.Function;
import java.util.function.Predicate;
import java.util.function.Supplier;
import java.util.stream.Stream;

/**
 * Comprehensive null safety utilities for the Waqiti platform.
 * This class provides defensive programming patterns to prevent NullPointerExceptions.
 * 
 * CRITICAL: All service boundaries and payment processing flows MUST use these utilities.
 */
public final class NullSafetyUtils {
    
    private static final Logger logger = LoggerFactory.getLogger(NullSafetyUtils.class);
    
    private NullSafetyUtils() {
        // Utility class, prevent instantiation
    }
    
    /**
     * Safely gets a value or returns a default if null.
     * 
     * @param value The value to check
     * @param defaultValue The default value to return if null
     * @return The value or default
     */
    @NonNull
    public static <T> T getOrDefault(@Nullable T value, @NonNull T defaultValue) {
        Objects.requireNonNull(defaultValue, "Default value cannot be null");
        return value != null ? value : defaultValue;
    }
    
    /**
     * Safely gets a value or throws a custom exception if null.
     * 
     * @param value The value to check
     * @param exceptionSupplier The exception to throw if null
     * @return The non-null value
     * @throws X The custom exception if value is null
     */
    @NonNull
    public static <T, X extends Throwable> T getOrThrow(@Nullable T value, 
                                                         @NonNull Supplier<? extends X> exceptionSupplier) throws X {
        if (value == null) {
            throw exceptionSupplier.get();
        }
        return value;
    }
    
    /**
     * Safely executes a consumer if the value is not null.
     * 
     * @param value The value to check
     * @param consumer The consumer to execute
     */
    public static <T> void ifPresent(@Nullable T value, @NonNull Consumer<T> consumer) {
        if (value != null) {
            consumer.accept(value);
        }
    }
    
    /**
     * Safely executes a consumer if present, otherwise executes an alternative action.
     * 
     * @param value The value to check
     * @param consumer The consumer to execute if present
     * @param emptyAction The action to execute if null
     */
    public static <T> void ifPresentOrElse(@Nullable T value, 
                                           @NonNull Consumer<T> consumer,
                                           @NonNull Runnable emptyAction) {
        if (value != null) {
            consumer.accept(value);
        } else {
            emptyAction.run();
        }
    }
    
    /**
     * Safely applies a function to a value if not null.
     * 
     * @param value The value to transform
     * @param mapper The mapping function
     * @return Optional containing the mapped value or empty
     */
    @NonNull
    public static <T, R> Optional<R> map(@Nullable T value, @NonNull Function<T, R> mapper) {
        return value != null ? Optional.ofNullable(mapper.apply(value)) : Optional.empty();
    }
    
    /**
     * Safely applies a function with a default return value.
     * 
     * @param value The value to transform
     * @param mapper The mapping function
     * @param defaultResult The default result if value is null
     * @return The mapped value or default
     */
    @NonNull
    public static <T, R> R mapOrDefault(@Nullable T value, 
                                        @NonNull Function<T, R> mapper,
                                        @NonNull R defaultResult) {
        return value != null ? mapper.apply(value) : defaultResult;
    }
    
    /**
     * Checks if all provided values are non-null.
     * 
     * @param values The values to check
     * @return true if all values are non-null
     */
    public static boolean allNonNull(Object... values) {
        if (values == null) {
            return false;
        }
        return Arrays.stream(values).allMatch(Objects::nonNull);
    }
    
    /**
     * Checks if any of the provided values is null.
     * 
     * @param values The values to check
     * @return true if any value is null
     */
    public static boolean anyNull(Object... values) {
        if (values == null) {
            return true;
        }
        return Arrays.stream(values).anyMatch(Objects::isNull);
    }
    
    /**
     * Safely compares two objects for equality, handling nulls.
     * 
     * @param a First object
     * @param b Second object
     * @return true if equal (including both null)
     */
    public static boolean safeEquals(@Nullable Object a, @Nullable Object b) {
        return Objects.equals(a, b);
    }
    
    /**
     * Safely compares two strings, ignoring case.
     * 
     * @param a First string
     * @param b Second string
     * @return true if equal ignoring case
     */
    public static boolean safeEqualsIgnoreCase(@Nullable String a, @Nullable String b) {
        if (a == null && b == null) {
            return true;
        }
        if (a == null || b == null) {
            return false;
        }
        return a.equalsIgnoreCase(b);
    }
    
    /**
     * Safely gets the length of a string.
     * 
     * @param str The string
     * @return The length or 0 if null
     */
    public static int safeLength(@Nullable String str) {
        return str != null ? str.length() : 0;
    }
    
    /**
     * Safely gets the size of a collection.
     * 
     * @param collection The collection
     * @return The size or 0 if null
     */
    public static int safeSize(@Nullable Collection<?> collection) {
        return collection != null ? collection.size() : 0;
    }
    
    /**
     * Safely checks if a string is empty or null.
     * 
     * @param str The string to check
     * @return true if null or empty
     */
    public static boolean isNullOrEmpty(@Nullable String str) {
        return !StringUtils.hasText(str);
    }
    
    /**
     * Safely checks if a collection is empty or null.
     * 
     * @param collection The collection to check
     * @return true if null or empty
     */
    public static boolean isNullOrEmpty(@Nullable Collection<?> collection) {
        return collection == null || collection.isEmpty();
    }
    
    /**
     * Safely checks if a map is empty or null.
     * 
     * @param map The map to check
     * @return true if null or empty
     */
    public static boolean isNullOrEmpty(@Nullable Map<?, ?> map) {
        return map == null || map.isEmpty();
    }
    
    /**
     * Returns an empty list if the input is null.
     * 
     * @param list The list to check
     * @return The list or empty list
     */
    @NonNull
    public static <T> List<T> emptyIfNull(@Nullable List<T> list) {
        return list != null ? list : Collections.emptyList();
    }
    
    /**
     * Returns an empty set if the input is null.
     * 
     * @param set The set to check
     * @return The set or empty set
     */
    @NonNull
    public static <T> Set<T> emptyIfNull(@Nullable Set<T> set) {
        return set != null ? set : Collections.emptySet();
    }
    
    /**
     * Returns an empty map if the input is null.
     * 
     * @param map The map to check
     * @return The map or empty map
     */
    @NonNull
    public static <K, V> Map<K, V> emptyIfNull(@Nullable Map<K, V> map) {
        return map != null ? map : Collections.emptyMap();
    }
    
    /**
     * Safely converts an object to string.
     * 
     * @param obj The object to convert
     * @return String representation or empty string if null
     */
    @NonNull
    public static String safeToString(@Nullable Object obj) {
        return obj != null ? obj.toString() : "";
    }
    
    /**
     * Safely converts an object to string with a default.
     * 
     * @param obj The object to convert
     * @param defaultValue The default value if null
     * @return String representation or default
     */
    @NonNull
    public static String safeToString(@Nullable Object obj, @NonNull String defaultValue) {
        return obj != null ? obj.toString() : defaultValue;
    }
    
    /**
     * Safely parses a Long value.
     * 
     * @param value The string value to parse
     * @return Optional containing the parsed value or empty
     */
    @NonNull
    public static Optional<Long> safeParseLong(@Nullable String value) {
        if (value == null || value.trim().isEmpty()) {
            return Optional.empty();
        }
        try {
            return Optional.of(Long.parseLong(value.trim()));
        } catch (NumberFormatException e) {
            logger.debug("Failed to parse Long value: {}", value, e);
            return Optional.empty();
        }
    }
    
    /**
     * Safely parses an Integer value.
     * 
     * @param value The string value to parse
     * @return Optional containing the parsed value or empty
     */
    @NonNull
    public static Optional<Integer> safeParseInt(@Nullable String value) {
        if (value == null || value.trim().isEmpty()) {
            return Optional.empty();
        }
        try {
            return Optional.of(Integer.parseInt(value.trim()));
        } catch (NumberFormatException e) {
            logger.debug("Failed to parse Integer value: {}", value, e);
            return Optional.empty();
        }
    }
    
    /**
     * Safely parses a BigDecimal value.
     *
     * @param value The string value to parse
     * @return Optional containing the parsed value or empty
     */
    @NonNull
    public static Optional<BigDecimal> safeParseBigDecimal(@Nullable String value) {
        if (value == null || value.trim().isEmpty()) {
            return Optional.empty();
        }
        try {
            return Optional.of(new BigDecimal(value.trim()));
        } catch (NumberFormatException e) {
            logger.debug("Failed to parse BigDecimal value: {}", value, e);
            return Optional.empty();
        }
    }

    /**
     * Safely parses a BigDecimal with a default value.
     *
     * @param value The string value to parse
     * @param defaultValue The default value if parsing fails
     * @return The parsed value or default
     */
    @NonNull
    public static BigDecimal safeParseBigDecimal(@Nullable String value, @NonNull BigDecimal defaultValue) {
        return safeParseBigDecimal(value).orElse(defaultValue);
    }
    
    /**
     * Safely parses a Boolean value.
     * 
     * @param value The string value to parse
     * @return Optional containing the parsed value or empty
     */
    @NonNull
    public static Optional<Boolean> safeParseBoolean(@Nullable String value) {
        if (value == null || value.trim().isEmpty()) {
            return Optional.empty();
        }
        String trimmed = value.trim().toLowerCase();
        if ("true".equals(trimmed) || "1".equals(trimmed) || "yes".equals(trimmed)) {
            return Optional.of(Boolean.TRUE);
        } else if ("false".equals(trimmed) || "0".equals(trimmed) || "no".equals(trimmed)) {
            return Optional.of(Boolean.FALSE);
        }
        return Optional.empty();
    }
    
    /**
     * Safely gets a value from a map.
     * 
     * @param map The map to get from
     * @param key The key to lookup
     * @return Optional containing the value or empty
     */
    @NonNull
    public static <K, V> Optional<V> safeGet(@Nullable Map<K, V> map, @Nullable K key) {
        if (map == null || key == null) {
            return Optional.empty();
        }
        return Optional.ofNullable(map.get(key));
    }
    
    /**
     * Safely gets a value from a map with a default.
     * 
     * @param map The map to get from
     * @param key The key to lookup
     * @param defaultValue The default value if not found
     * @return The value or default
     */
    @NonNull
    public static <K, V> V safeGetOrDefault(@Nullable Map<K, V> map, 
                                            @Nullable K key, 
                                            @NonNull V defaultValue) {
        if (map == null || key == null) {
            return defaultValue;
        }
        return map.getOrDefault(key, defaultValue);
    }
    
    /**
     * Creates a stream from a nullable collection.
     * 
     * @param collection The collection to stream
     * @return Stream of the collection or empty stream
     */
    @NonNull
    public static <T> Stream<T> safeStream(@Nullable Collection<T> collection) {
        return collection != null ? collection.stream() : Stream.empty();
    }
    
    /**
     * Safely filters a collection.
     * 
     * @param collection The collection to filter
     * @param predicate The filter predicate
     * @return Filtered list or empty list
     */
    @NonNull
    public static <T> List<T> safeFilter(@Nullable Collection<T> collection, 
                                         @NonNull Predicate<T> predicate) {
        if (collection == null) {
            return Collections.emptyList();
        }
        return collection.stream()
            .filter(predicate)
            .toList();
    }
    
    /**
     * Validates that an object is not null with a custom message.
     * 
     * @param object The object to validate
     * @param message The error message if null
     * @return The non-null object
     * @throws IllegalArgumentException if object is null
     */
    @NonNull
    public static <T> T requireNonNull(@Nullable T object, @NonNull String message) {
        if (object == null) {
            throw new IllegalArgumentException(message);
        }
        return object;
    }
    
    /**
     * Validates that a string is not null or empty.
     * 
     * @param str The string to validate
     * @param message The error message if invalid
     * @return The non-empty string
     * @throws IllegalArgumentException if string is null or empty
     */
    @NonNull
    public static String requireNonEmpty(@Nullable String str, @NonNull String message) {
        if (isNullOrEmpty(str)) {
            throw new IllegalArgumentException(message);
        }
        return str;
    }
    
    /**
     * Validates that a collection is not null or empty.
     * 
     * @param collection The collection to validate
     * @param message The error message if invalid
     * @return The non-empty collection
     * @throws IllegalArgumentException if collection is null or empty
     */
    @NonNull
    public static <T extends Collection<?>> T requireNonEmpty(@Nullable T collection, 
                                                              @NonNull String message) {
        if (isNullOrEmpty(collection)) {
            throw new IllegalArgumentException(message);
        }
        return collection;
    }
    
    /**
     * Safely chains multiple optional operations.
     * 
     * @param value The initial value
     * @param operations The operations to apply
     * @return Optional containing the final result or empty
     */
    @SafeVarargs
    @NonNull
    public static <T> Optional<T> chain(@Nullable T value, 
                                        @NonNull Function<T, T>... operations) {
        if (value == null || operations == null || operations.length == 0) {
            return Optional.ofNullable(value);
        }
        
        T result = value;
        for (Function<T, T> operation : operations) {
            if (result == null) {
                break;
            }
            result = operation.apply(result);
        }
        
        return Optional.ofNullable(result);
    }
    
    /**
     * Logs and returns a default value if null.
     * 
     * @param value The value to check
     * @param defaultValue The default value
     * @param logMessage The message to log if null
     * @return The value or default
     */
    @NonNull
    public static <T> T logIfNull(@Nullable T value, 
                                  @NonNull T defaultValue, 
                                  @NonNull String logMessage) {
        if (value == null) {
            logger.warn("Null value encountered: {}", logMessage);
            return defaultValue;
        }
        return value;
    }
}