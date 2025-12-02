package com.waqiti.common.transaction;

import java.util.Map;
import java.util.concurrent.CompletableFuture;

/**
 * Interface for handling compensation logic in saga patterns
 */
public interface CompensationHandler {
    
    /**
     * Execute compensation logic (original signature)
     * @param context The transaction context
     * @param participantData Data specific to this participant
     * @return Result of compensation operation
     */
    CompensationResult compensate(DistributedTransactionContext context, Map<String, Object> participantData);
    
    /**
     * Execute compensation logic (async version)
     * @return CompletableFuture with boolean indicating success
     */
    default CompletableFuture<Boolean> compensate() {
        return CompletableFuture.supplyAsync(() -> {
            try {
                CompensationResult result = compensate(null, null);
                return result.isSuccess();
            } catch (Exception e) {
                return false;
            }
        });
    }
    
    /**
     * Default no-op compensation handler
     */
    CompensationHandler NO_OP = (context, data) -> CompensationResult.success();
    
    /**
     * Create a simple compensation handler from a Runnable
     */
    static CompensationHandler fromRunnable(Runnable compensation) {
        return (context, data) -> {
            try {
                compensation.run();
                return CompensationResult.success();
            } catch (Exception e) {
                return CompensationResult.failure("Compensation failed: " + e.getMessage());
            }
        };
    }
    
    /**
     * Create an async compensation handler
     */
    static CompensationHandler fromAsync(java.util.function.Supplier<CompletableFuture<Boolean>> supplier) {
        return new CompensationHandler() {
            @Override
            public CompensationResult compensate(DistributedTransactionContext context, Map<String, Object> participantData) {
                try {
                    boolean success = supplier.get().get();
                    return success ? CompensationResult.success() : CompensationResult.failure("Async compensation failed");
                } catch (Exception e) {
                    return CompensationResult.failure("Async compensation error: " + e.getMessage());
                }
            }
            
            @Override
            public CompletableFuture<Boolean> compensate() {
                return supplier.get();
            }
        };
    }
    
    /**
     * Result of a compensation operation
     */
    class CompensationResult {
        private final boolean success;
        private final String message;
        private final Map<String, Object> resultData;
        
        private CompensationResult(boolean success, String message, Map<String, Object> resultData) {
            this.success = success;
            this.message = message;
            this.resultData = resultData;
        }
        
        public static CompensationResult success() {
            return new CompensationResult(true, "Compensation successful", null);
        }
        
        public static CompensationResult success(String message) {
            return new CompensationResult(true, message, null);
        }
        
        public static CompensationResult success(String message, Map<String, Object> data) {
            return new CompensationResult(true, message, data);
        }
        
        public static CompensationResult failure(String error) {
            return new CompensationResult(false, error, null);
        }
        
        public static CompensationResult failure(String error, Map<String, Object> data) {
            return new CompensationResult(false, error, data);
        }
        
        public boolean isSuccess() {
            return success;
        }
        
        public String getMessage() {
            return message;
        }
        
        public Map<String, Object> getResultData() {
            return resultData;
        }
    }
}