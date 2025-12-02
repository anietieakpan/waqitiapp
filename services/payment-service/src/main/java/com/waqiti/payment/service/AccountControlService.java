package com.waqiti.payment.service;

import com.waqiti.payment.domain.Account;
import com.waqiti.payment.domain.AccountControl;
import com.waqiti.payment.domain.AccountControlAction;
import com.waqiti.payment.domain.AccountStatus;
import com.waqiti.payment.repository.AccountControlRepository;
import com.waqiti.payment.repository.AccountRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

/**
 * Service for managing account controls and restrictions
 */
@Service
@Slf4j
@RequiredArgsConstructor
public class AccountControlService {
    
    private final AccountRepository accountRepository;
    private final AccountControlRepository accountControlRepository;
    
    /**
     * Apply account control based on the control action
     */
    @Transactional
    public void applyAccountControl(AccountControl control, String correlationId) {
        log.info("Applying account control: {}, correlationId: {}", control, correlationId);
        
        // Save the control record
        accountControlRepository.save(control);
        
        // Apply the control to the account
        Optional<Account> accountOpt = accountRepository.findByUserId(control.getUserId());
        
        if (accountOpt.isPresent()) {
            Account account = accountOpt.get();
            
            switch (control.getAction()) {
                case FREEZE_ACCOUNTS -> {
                    account.setFrozen(true);
                    account.setStatus(AccountStatus.FROZEN);
                    log.warn("Account frozen for user: {}, reason: {}", control.getUserId(), control.getReason());
                }
                case SUSPEND_PAYMENTS -> {
                    account.setRestricted(true);
                    account.setStatus(AccountStatus.SUSPENDED);
                    log.warn("Payments suspended for user: {}, reason: {}", control.getUserId(), control.getReason());
                }
                case RESTRICT_TRANSACTIONS -> {
                    account.setRestricted(true);
                    account.setStatus(AccountStatus.RESTRICTED);
                    log.warn("Transactions restricted for user: {}, reason: {}", control.getUserId(), control.getReason());
                }
                case BLOCK_CARD_OPERATIONS -> {
                    // Card operations would be handled by card service
                    log.warn("Card operations blocked for user: {}, reason: {}", control.getUserId(), control.getReason());
                }
                default -> {
                    log.warn("Unhandled control action: {}", control.getAction());
                }
            }
            
            account.setUpdatedAt(LocalDateTime.now());
            accountRepository.save(account);
        } else {
            log.error("Account not found for userId: {}", control.getUserId());
        }
    }
    
    /**
     * Remove account control
     */
    @Transactional
    public void removeAccountControl(String controlId, String reason) {
        Optional<AccountControl> controlOpt = accountControlRepository.findById(controlId);
        
        if (controlOpt.isPresent()) {
            AccountControl control = controlOpt.get();
            control.setActive(false);
            control.setUpdatedAt(LocalDateTime.now());
            accountControlRepository.save(control);
            
            // Update account status
            Optional<Account> accountOpt = accountRepository.findByUserId(control.getUserId());
            if (accountOpt.isPresent()) {
                Account account = accountOpt.get();
                
                // Check if there are any other active controls
                long activeControls = accountControlRepository.countByUserIdAndIsActive(control.getUserId(), true);
                
                if (activeControls == 0) {
                    account.setFrozen(false);
                    account.setRestricted(false);
                    account.setStatus(AccountStatus.ACTIVE);
                    account.setUpdatedAt(LocalDateTime.now());
                    accountRepository.save(account);
                    
                    log.info("All controls removed for user: {}, reason: {}", control.getUserId(), reason);
                } else {
                    log.info("Control {} removed but {} active controls remain for user: {}", 
                        controlId, activeControls, control.getUserId());
                }
            }
        } else {
            log.error("Control not found with id: {}", controlId);
        }
    }
    
    /**
     * Check if user has active controls
     */
    public boolean hasActiveControls(String userId) {
        return accountControlRepository.countByUserIdAndIsActive(userId, true) > 0;
    }
    
    /**
     * Get active controls for a user
     */
    public List<AccountControl> getActiveControls(String userId) {
        return accountControlRepository.findByUserIdAndIsActive(userId, true);
    }
    
    /**
     * Process expired controls
     */
    @Transactional
    public void processExpiredControls() {
        List<AccountControl> expiredControls = accountControlRepository.findExpiredActiveControls(LocalDateTime.now());
        
        for (AccountControl control : expiredControls) {
            control.setActive(false);
            control.setUpdatedAt(LocalDateTime.now());
            accountControlRepository.save(control);
            
            log.info("Expired control {} for user: {}", control.getId(), control.getUserId());
            
            // Check if account can be reactivated
            removeAccountControl(control.getId(), "CONTROL_EXPIRED");
        }
    }
}