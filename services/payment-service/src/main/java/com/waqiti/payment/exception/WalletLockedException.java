package com.waqiti.payment.exception;

import lombok.Getter;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.ResponseStatus;

import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.Map;

/**
 * Exception thrown when a wallet is locked and cannot be accessed
 */
@Getter
@ResponseStatus(HttpStatus.LOCKED)
public class WalletLockedException extends WalletServiceException {
    
    private final String walletId;
    private final LockReason lockReason;
    private final LocalDateTime lockedAt;
    private final LocalDateTime unlockAt;
    private final String lockedBy;
    private final Map<String, Object> lockDetails;
    
    public enum LockReason {
        SECURITY_HOLD,
        FRAUD_DETECTION,
        SUSPICIOUS_ACTIVITY,
        KYC_PENDING,
        COMPLIANCE_REVIEW,
        USER_REQUEST,
        ADMINISTRATIVE_ACTION,
        PAYMENT_DISPUTE,
        ACCOUNT_VERIFICATION,
        LEGAL_HOLD,
        MAINTENANCE,
        SYSTEM_ERROR
    }
    
    public WalletLockedException(String walletId, LockReason reason) {
        super(String.format("Wallet %s is locked: %s", walletId, reason.name().toLowerCase().replace('_', ' ')),
              "WALLET_LOCKED",
              HttpStatus.LOCKED,
              walletId,
              ErrorCategory.WALLET_LOCKED);
        this.walletId = walletId;
        this.lockReason = reason;
        this.lockedAt = LocalDateTime.now();
        this.unlockAt = null;
        this.lockedBy = "SYSTEM";
        this.lockDetails = new HashMap<>();
        
        withDetail("lockReason", reason.name());
    }
    
    public WalletLockedException(String walletId, LockReason reason, LocalDateTime unlockAt, String lockedBy) {
        super(buildMessage(walletId, reason, unlockAt),
              "WALLET_LOCKED",
              HttpStatus.LOCKED,
              walletId,
              ErrorCategory.WALLET_LOCKED);
        this.walletId = walletId;
        this.lockReason = reason;
        this.lockedAt = LocalDateTime.now();
        this.unlockAt = unlockAt;
        this.lockedBy = lockedBy;
        this.lockDetails = new HashMap<>();
        
        withDetail("lockReason", reason.name());
        withDetail("unlockAt", unlockAt);
        withDetail("lockedBy", lockedBy);
    }
    
    public WalletLockedException withLockDetail(String key, Object value) {
        this.lockDetails.put(key, value);
        return this;
    }
    
    public WalletLockedException withUnlockInstructions(String instructions) {
        this.lockDetails.put("unlockInstructions", instructions);
        return this;
    }
    
    public WalletLockedException withContactSupport(boolean required) {
        this.lockDetails.put("contactSupportRequired", required);
        return this;
    }
    
    private static String buildMessage(String walletId, LockReason reason, LocalDateTime unlockAt) {
        StringBuilder message = new StringBuilder();
        message.append(String.format("Wallet %s is locked due to %s", 
                                    walletId, 
                                    reason.name().toLowerCase().replace('_', ' ')));
        
        if (unlockAt != null) {
            message.append(String.format(". Will be unlocked at %s", unlockAt));
        }
        
        return message.toString();
    }
    
    public boolean isTemporary() {
        return unlockAt != null && unlockAt.isAfter(LocalDateTime.now());
    }
    
    public long getSecondsUntilUnlock() {
        if (unlockAt == null || unlockAt.isBefore(LocalDateTime.now())) {
            return -1;
        }
        return java.time.Duration.between(LocalDateTime.now(), unlockAt).getSeconds();
    }
    
    @Override
    public Map<String, Object> toErrorResponse() {
        Map<String, Object> response = super.toErrorResponse();
        
        Map<String, Object> lockInfo = new HashMap<>();
        lockInfo.put("walletId", walletId);
        lockInfo.put("lockReason", lockReason);
        lockInfo.put("lockedAt", lockedAt);
        lockInfo.put("unlockAt", unlockAt);
        lockInfo.put("lockedBy", lockedBy);
        lockInfo.put("isTemporary", isTemporary());
        
        if (isTemporary()) {
            lockInfo.put("secondsUntilUnlock", getSecondsUntilUnlock());
        }
        
        if (!lockDetails.isEmpty()) {
            lockInfo.put("details", lockDetails);
        }
        
        response.put("lockInfo", lockInfo);
        
        return response;
    }
}