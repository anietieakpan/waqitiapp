package com.waqiti.common.util;

import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.NoSuchElementException;
import java.util.Objects;
import java.util.Optional;
import java.util.function.Consumer;
import java.util.function.Function;
import java.util.function.Predicate;
import java.util.function.Supplier;

/**
 * Enterprise-grade Result wrapper for eliminating null returns
 * Similar to Optional but with additional error handling capabilities
 * 
 * This class provides a type-safe way to handle potentially absent values
 * and computation results that might fail, replacing null returns with
 * explicit success/failure states.
 * 
 * @param <T> The type of the value wrapped by this Result
 */
@Data
@NoArgsConstructor(access = AccessLevel.PRIVATE)
@AllArgsConstructor(access = AccessLevel.PRIVATE)
public class ResultWrapper<T> {
    
    private T value;
    private String errorMessage;
    private Exception exception;
    private boolean success;
    
    /**
     * Creates a successful result with a non-null value
     */
    public static <T> ResultWrapper<T> success(T value) {
        Objects.requireNonNull(value, "Success value cannot be null");
        return new ResultWrapper<>(value, null, null, true);
    }
    
    /**
     * Creates a successful result with an optional value
     */
    public static <T> ResultWrapper<T> successOptional(T value) {
        if (value == null) {
            return empty();
        }
        return new ResultWrapper<>(value, null, null, true);
    }
    
    /**
     * Creates an empty result (no value, but not an error)
     */
    public static <T> ResultWrapper<T> empty() {
        return new ResultWrapper<>(null, "No value present", null, false);
    }
    
    /**
     * Creates a failed result with an error message
     */
    public static <T> ResultWrapper<T> failure(String errorMessage) {
        return new ResultWrapper<>(null, errorMessage, null, false);
    }
    
    /**
     * Creates a failed result with an exception
     */
    public static <T> ResultWrapper<T> failure(Exception exception) {
        return new ResultWrapper<>(null, exception.getMessage(), exception, false);
    }
    
    /**
     * Creates a failed result with both message and exception
     */
    public static <T> ResultWrapper<T> failure(String errorMessage, Exception exception) {
        return new ResultWrapper<>(null, errorMessage, exception, false);
    }
    
    /**
     * Wraps a supplier that might throw an exception
     */
    public static <T> ResultWrapper<T> of(Supplier<T> supplier) {
        try {
            T value = supplier.get();
            return value != null ? success(value) : empty();
        } catch (Exception e) {
            return failure(e);
        }
    }
    
    /**
     * Wraps an Optional value
     */
    public static <T> ResultWrapper<T> fromOptional(Optional<T> optional) {
        return optional.map(ResultWrapper::success).orElse(empty());
    }
    
    /**
     * Wraps an Optional with a custom error message when empty
     */
    public static <T> ResultWrapper<T> fromOptional(Optional<T> optional, String errorIfEmpty) {
        return optional.map(ResultWrapper::success)
                      .orElse(failure(errorIfEmpty));
    }
    
    /**
     * Check if the result contains a value
     */
    public boolean isPresent() {
        return success && value != null;
    }
    
    /**
     * Check if the result is empty (no value, but not an error)
     */
    public boolean isEmpty() {
        return !success && exception == null;
    }
    
    /**
     * Check if the result is a failure
     */
    public boolean isFailure() {
        return !success && (exception != null || errorMessage != null);
    }
    
    /**
     * Get the value if present, otherwise throw exception
     */
    public T get() {
        if (isPresent()) {
            return value;
        }
        if (exception != null) {
            throw new NoSuchElementException("Result failed: " + errorMessage, exception);
        }
        throw new NoSuchElementException("Result is empty: " + errorMessage);
    }
    
    /**
     * Get the value or a default if not present
     */
    public T orElse(T defaultValue) {
        return isPresent() ? value : defaultValue;
    }
    
    /**
     * Get the value or compute a default if not present
     */
    public T orElseGet(Supplier<? extends T> supplier) {
        return isPresent() ? value : supplier.get();
    }
    
    /**
     * Get the value or throw a custom exception if not present
     */
    public <X extends Throwable> T orElseThrow(Supplier<? extends X> exceptionSupplier) throws X {
        if (isPresent()) {
            return value;
        }
        throw exceptionSupplier.get();
    }
    
    /**
     * Transform the value if present
     */
    public <U> ResultWrapper<U> map(Function<? super T, ? extends U> mapper) {
        if (isPresent()) {
            try {
                U mapped = mapper.apply(value);
                return mapped != null ? success(mapped) : empty();
            } catch (Exception e) {
                return failure("Error during mapping: " + e.getMessage(), e);
            }
        }
        return isFailure() ? failure(errorMessage, exception) : empty();
    }
    
    /**
     * Transform the value with a function that returns a ResultWrapper
     */
    public <U> ResultWrapper<U> flatMap(Function<? super T, ResultWrapper<U>> mapper) {
        if (isPresent()) {
            try {
                return mapper.apply(value);
            } catch (Exception e) {
                return failure("Error during flat mapping: " + e.getMessage(), e);
            }
        }
        return isFailure() ? failure(errorMessage, exception) : empty();
    }
    
    /**
     * Filter the value based on a predicate
     */
    public ResultWrapper<T> filter(Predicate<? super T> predicate) {
        if (isPresent()) {
            try {
                return predicate.test(value) ? this : empty();
            } catch (Exception e) {
                return failure("Error during filtering: " + e.getMessage(), e);
            }
        }
        return this;
    }
    
    /**
     * Execute an action if value is present
     */
    public ResultWrapper<T> ifPresent(Consumer<? super T> action) {
        if (isPresent()) {
            try {
                action.accept(value);
            } catch (Exception e) {
                return failure("Error during action execution: " + e.getMessage(), e);
            }
        }
        return this;
    }
    
    /**
     * Execute an action if the result is a failure
     */
    public ResultWrapper<T> ifFailure(Consumer<String> errorHandler) {
        if (isFailure()) {
            errorHandler.accept(errorMessage);
        }
        return this;
    }
    
    /**
     * Execute different actions based on result state
     */
    public void handle(Consumer<? super T> onSuccess, Consumer<String> onFailure) {
        if (isPresent()) {
            onSuccess.accept(value);
        } else if (isFailure()) {
            onFailure.accept(errorMessage);
        }
    }
    
    /**
     * Convert to Optional
     */
    public Optional<T> toOptional() {
        return Optional.ofNullable(value);
    }
    
    /**
     * Recover from failure with a default value
     */
    public ResultWrapper<T> recover(T defaultValue) {
        return isFailure() ? success(defaultValue) : this;
    }
    
    /**
     * Recover from failure with a supplier
     */
    public ResultWrapper<T> recover(Supplier<T> supplier) {
        if (isFailure()) {
            try {
                T recovered = supplier.get();
                return recovered != null ? success(recovered) : empty();
            } catch (Exception e) {
                return failure("Recovery failed: " + e.getMessage(), e);
            }
        }
        return this;
    }
    
    /**
     * Chain multiple operations with error handling
     */
    public static <T> ResultWrapper<T> tryAll(Supplier<T>... suppliers) {
        for (Supplier<T> supplier : suppliers) {
            ResultWrapper<T> result = of(supplier);
            if (result.isPresent()) {
                return result;
            }
        }
        return empty();
    }
    
    @Override
    public String toString() {
        if (isPresent()) {
            return "ResultWrapper[" + value + "]";
        }
        if (isFailure()) {
            return "ResultWrapper.failure[" + errorMessage + "]";
        }
        return "ResultWrapper.empty";
    }
}