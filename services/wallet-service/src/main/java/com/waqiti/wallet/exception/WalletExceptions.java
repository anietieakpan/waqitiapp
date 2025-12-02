package com.waqiti.wallet.exception;

/**
 * Custom exceptions for wallet operations
 */
public class WalletExceptions {
    
    /**
     * Thrown when wallet has insufficient balance
     */
    public static class InsufficientBalanceException extends RuntimeException {
        private final String walletId;
        private final String amount;
        private final String available;
        
        public InsufficientBalanceException(String message) {
            super(message);
            this.walletId = null;
            this.amount = null;
            this.available = null;
        }
        
        public InsufficientBalanceException(String walletId, String amount, String available) {
            super(String.format("Insufficient balance in wallet %s: required %s, available %s", 
                walletId, amount, available));
            this.walletId = walletId;
            this.amount = amount;
            this.available = available;
        }
        
        public String getWalletId() { return walletId; }
        public String getAmount() { return amount; }
        public String getAvailable() { return available; }
    }
    
    /**
     * Thrown when wallet is not in active state
     */
    public static class WalletNotActiveException extends RuntimeException {
        private final String walletId;
        private final String status;
        
        public WalletNotActiveException(String message) {
            super(message);
            this.walletId = null;
            this.status = null;
        }
        
        public WalletNotActiveException(String walletId, String status) {
            super(String.format("Wallet %s is not active. Current status: %s", walletId, status));
            this.walletId = walletId;
            this.status = status;
        }
        
        public String getWalletId() { return walletId; }
        public String getStatus() { return status; }
    }
    
    /**
     * Thrown when transaction limit is exceeded
     */
    public static class TransactionLimitExceededException extends RuntimeException {
        private final String limitType;
        private final String limit;
        private final String current;
        private final String requested;
        
        public TransactionLimitExceededException(String message) {
            super(message);
            this.limitType = null;
            this.limit = null;
            this.current = null;
            this.requested = null;
        }
        
        public TransactionLimitExceededException(String limitType, String limit, 
                                                String current, String requested) {
            super(String.format("%s limit exceeded. Limit: %s, Current: %s, Requested: %s",
                limitType, limit, current, requested));
            this.limitType = limitType;
            this.limit = limit;
            this.current = current;
            this.requested = requested;
        }
        
        public String getLimitType() { return limitType; }
        public String getLimit() { return limit; }
        public String getCurrent() { return current; }
        public String getRequested() { return requested; }
    }
    
    /**
     * Thrown when reservation is not found
     */
    public static class ReservationNotFoundException extends RuntimeException {
        private final String reservationId;
        
        public ReservationNotFoundException(String reservationId) {
            super("Reservation not found: " + reservationId);
            this.reservationId = reservationId;
        }
        
        public String getReservationId() { return reservationId; }
    }
    
    /**
     * Thrown when wallet is not found
     */
    public static class WalletNotFoundException extends RuntimeException {
        private final String identifier;
        
        public WalletNotFoundException(String identifier) {
            super("Wallet not found: " + identifier);
            this.identifier = identifier;
        }
        
        public String getIdentifier() { return identifier; }
    }
    
    /**
     * Thrown when there's a concurrency conflict
     */
    public static class OptimisticLockException extends RuntimeException {
        private final String walletId;
        
        public OptimisticLockException(String walletId) {
            super("Concurrent modification detected for wallet: " + walletId);
            this.walletId = walletId;
        }
        
        public String getWalletId() { return walletId; }
    }
}