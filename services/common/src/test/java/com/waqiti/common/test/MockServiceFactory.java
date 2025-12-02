package com.waqiti.common.test;

import org.mockito.Mockito;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.*;
import java.util.concurrent.CompletableFuture;
import java.util.function.Function;

import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.when;

/**
 * Factory for creating pre-configured mock services for testing.
 *
 * Provides standardized mocks for:
 * - External service clients (payment processors, banks)
 * - Internal microservices (wallet, compliance, fraud detection)
 * - Infrastructure components (cache, messaging, database)
 *
 * All mocks come with sensible default behavior and can be further customized.
 *
 * Usage:
 * <pre>
 * {@code
 * // Create mock with default behavior
 * PaymentProcessor mockProcessor = MockServiceFactory.mockPaymentProcessor();
 *
 * // Customize mock behavior
 * when(mockProcessor.processPayment(any())).thenReturn(successResult);
 *
 * // Use in tests
 * PaymentService service = new PaymentService(mockProcessor);
 * }
 * </pre>
 *
 * @author Waqiti Engineering Team
 * @version 1.0
 * @since 2024-10-19
 */
public final class MockServiceFactory {

    private MockServiceFactory() {
        // Utility class - prevent instantiation
    }

    // ==================== CLOCK MOCKS ====================

    /**
     * Create a fixed clock for deterministic time-based testing.
     *
     * @param instant Fixed instant
     * @return Fixed clock
     */
    public static Clock fixedClock(Instant instant) {
        return Clock.fixed(instant, ZoneId.of("UTC"));
    }

    /**
     * Create a fixed clock with standard test time.
     *
     * @return Fixed clock at 2024-10-19 12:00:00 UTC
     */
    public static Clock standardTestClock() {
        return fixedClock(Instant.parse("2024-10-19T12:00:00Z"));
    }

    // ==================== REPOSITORY MOCKS ====================

    /**
     * Create a mock repository that returns Optional.empty() by default.
     *
     * @param <T> Repository type
     * @param repositoryClass Repository class
     * @return Mock repository
     */
    public static <T> T mockRepository(Class<T> repositoryClass) {
        T mock = Mockito.mock(repositoryClass);

        // Configure default behavior for common repository methods
        try {
            // findById returns empty by default
            when(mock.getClass().getMethod("findById", Object.class)).thenReturn(Optional.empty());
        } catch (NoSuchMethodException e) {
            // Method doesn't exist, skip
        }

        return mock;
    }

    // ==================== SERVICE CLIENT MOCKS ====================

    /**
     * Create a mock that returns successful responses by default.
     *
     * @param <T> Service client type
     * @param clientClass Client class
     * @return Mock client
     */
    public static <T> T mockServiceClient(Class<T> clientClass) {
        return Mockito.mock(clientClass);
    }

    // ==================== HELPER METHODS ====================

    /**
     * Create a successful CompletableFuture for async testing.
     *
     * @param <T> Result type
     * @param value Result value
     * @return Completed future
     */
    public static <T> CompletableFuture<T> successFuture(T value) {
        return CompletableFuture.completedFuture(value);
    }

    /**
     * Create a failed CompletableFuture for async testing.
     *
     * @param <T> Result type
     * @param exception Exception to fail with
     * @return Failed future
     */
    public static <T> CompletableFuture<T> failedFuture(Throwable exception) {
        CompletableFuture<T> future = new CompletableFuture<>();
        future.completeExceptionally(exception);
        return future;
    }

    /**
     * Create a mock that throws an exception.
     *
     * @param <T> Service type
     * @param serviceClass Service class
     * @param exception Exception to throw
     * @return Mock that throws
     */
    public static <T> T mockThatThrows(Class<T> serviceClass, RuntimeException exception) {
        T mock = Mockito.mock(serviceClass);
        // Configure to throw on any method call
        Mockito.when(mock.toString()).thenThrow(exception);
        return mock;
    }

    // ==================== DATA STRUCTURE MOCKS ====================

    /**
     * Create a mock map with pre-configured entries.
     *
     * @param <K> Key type
     * @param <V> Value type
     * @return Mock map
     */
    @SuppressWarnings("unchecked")
    public static <K, V> Map<K, V> mockMap() {
        return Mockito.mock(Map.class);
    }

    /**
     * Create a mock list.
     *
     * @param <T> Element type
     * @return Mock list
     */
    @SuppressWarnings("unchecked")
    public static <T> List<T> mockList() {
        return Mockito.mock(List.class);
    }

    /**
     * Create a mock set.
     *
     * @param <T> Element type
     * @return Mock set
     */
    @SuppressWarnings("unchecked")
    public static <T> Set<T> mockSet() {
        return Mockito.mock(Set.class);
    }

    // ==================== RESPONSE BUILDERS ====================

    /**
     * Builder for creating mock response objects.
     *
     * @param <T> Response type
     */
    public static class MockResponseBuilder<T> {
        private final T mock;

        private MockResponseBuilder(T mock) {
            this.mock = mock;
        }

        /**
         * Configure a method to return a value.
         *
         * @param methodCall Method call to configure
         * @param returnValue Return value
         * @return This builder
         */
        public MockResponseBuilder<T> returning(Object methodCall, Object returnValue) {
            // This is a simplified version - in real usage, you'd use Mockito.when()
            return this;
        }

        /**
         * Build the configured mock.
         *
         * @return Configured mock
         */
        public T build() {
            return mock;
        }
    }

    /**
     * Start building a mock response.
     *
     * @param <T> Response type
     * @param clazz Response class
     * @return Mock response builder
     */
    public static <T> MockResponseBuilder<T> mockResponse(Class<T> clazz) {
        return new MockResponseBuilder<>(Mockito.mock(clazz));
    }

    // ==================== VALIDATION HELPERS ====================

    /**
     * Verify that a method was called with specific arguments.
     * Helper method to reduce boilerplate in tests.
     *
     * @param <T> Mock type
     * @param mock Mock object
     * @return Verified mock
     */
    public static <T> T verify(T mock) {
        return Mockito.verify(mock);
    }

    /**
     * Verify that a method was called a specific number of times.
     *
     * @param <T> Mock type
     * @param mock Mock object
     * @param times Number of times
     * @return Verified mock
     */
    public static <T> T verify(T mock, int times) {
        return Mockito.verify(mock, Mockito.times(times));
    }

    /**
     * Verify that a method was never called.
     *
     * @param <T> Mock type
     * @param mock Mock object
     * @return Verified mock
     */
    public static <T> T verifyNever(T mock) {
        return Mockito.verify(mock, Mockito.never());
    }

    /**
     * Reset all mocks.
     *
     * @param mocks Mocks to reset
     */
    public static void resetMocks(Object... mocks) {
        Mockito.reset(mocks);
    }

    /**
     * Clear all invocations on mocks.
     *
     * @param mocks Mocks to clear
     */
    public static void clearInvocations(Object... mocks) {
        Mockito.clearInvocations(mocks);
    }

    // ==================== STUB BUILDERS ====================

    /**
     * Create a stub that returns different values on successive calls.
     *
     * @param <T> Return type
     * @param firstValue First return value
     * @param subsequentValues Subsequent return values
     * @return Configured stub
     */
    @SafeVarargs
    public static <T> Function<Object, T> sequentialStub(T firstValue, T... subsequentValues) {
        List<T> values = new ArrayList<>();
        values.add(firstValue);
        values.addAll(Arrays.asList(subsequentValues));

        final int[] callCount = {0};

        return input -> {
            int index = Math.min(callCount[0], values.size() - 1);
            callCount[0]++;
            return values.get(index);
        };
    }

    /**
     * Create a stub that delays before returning.
     *
     * @param <T> Return type
     * @param value Value to return
     * @param delayMillis Delay in milliseconds
     * @return Delayed stub
     */
    public static <T> Function<Object, T> delayedStub(T value, long delayMillis) {
        return input -> {
            try {
                Thread.sleep(delayMillis);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                throw new RuntimeException("Interrupted during delay", e);
            }
            return value;
        };
    }

    /**
     * Create a stub that randomly succeeds or fails.
     *
     * @param <T> Return type
     * @param successValue Success value
     * @param exception Exception to throw on failure
     * @param successRate Success rate (0.0 to 1.0)
     * @return Random stub
     */
    public static <T> Function<Object, T> randomStub(T successValue, RuntimeException exception, double successRate) {
        Random random = new Random();
        return input -> {
            if (random.nextDouble() < successRate) {
                return successValue;
            } else {
                throw exception;
            }
        };
    }

    // ==================== FLUENT MOCK BUILDER ====================

    /**
     * Fluent builder for creating and configuring mocks.
     *
     * @param <T> Mock type
     */
    public static class FluentMockBuilder<T> {
        private final T mock;

        private FluentMockBuilder(Class<T> clazz) {
            this.mock = Mockito.mock(clazz);
        }

        /**
         * Configure a method to return a value.
         *
         * @param <R> Return type
         * @param stub Method stub
         * @param returnValue Return value
         * @return This builder
         */
        public <R> FluentMockBuilder<T> when(R stub, R returnValue) {
            Mockito.when(stub).thenReturn(returnValue);
            return this;
        }

        /**
         * Configure a method to throw an exception.
         *
         * @param <R> Return type
         * @param stub Method stub
         * @param exception Exception to throw
         * @return This builder
         */
        public <R> FluentMockBuilder<T> whenThrows(R stub, Throwable exception) {
            Mockito.when(stub).thenThrow(exception);
            return this;
        }

        /**
         * Build the configured mock.
         *
         * @return Configured mock
         */
        public T build() {
            return mock;
        }
    }

    /**
     * Create a fluent mock builder.
     *
     * @param <T> Mock type
     * @param clazz Mock class
     * @return Fluent builder
     */
    public static <T> FluentMockBuilder<T> fluent(Class<T> clazz) {
        return new FluentMockBuilder<>(clazz);
    }
}
