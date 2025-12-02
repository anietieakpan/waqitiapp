package com.waqiti.user.event;

import lombok.Data;
import lombok.EqualsAndHashCode;

import java.time.LocalDateTime;
import java.util.Map;

/**
 * Event for account status changes
 */
@Data
@EqualsAndHashCode(callSuper = true)
public class AccountStatusChangeEvent extends UserEvent {
    
    private String accountId;
    private String previousStatus;
    private String newStatus; // ACTIVE, INACTIVE, FROZEN, RESTRICTED, CLOSED
    private String changeReason;
    private String changedBy;
    private String changeCategory; // COMPLIANCE, SECURITY, USER_REQUEST, ADMINISTRATIVE, AUTOMATED
    private LocalDateTime effectiveTime;
    private LocalDateTime reviewDate;
    private Map<String, String> restrictions;
    private boolean reversible;
    private String reversalProcess;
    private String ticketNumber;
    private String approvedBy;
    private Map<String, Object> statusMetadata;
    
    public AccountStatusChangeEvent() {
        super("ACCOUNT_STATUS_CHANGE");
    }
    
    public static AccountStatusChangeEvent accountFrozen(String userId, String accountId, String previousStatus, 
                                                       String reason, String ticketNumber) {
        AccountStatusChangeEvent event = new AccountStatusChangeEvent();
        event.setUserId(userId);
        event.setAccountId(accountId);
        event.setPreviousStatus(previousStatus);
        event.setNewStatus("FROZEN");
        event.setChangeReason(reason);
        event.setChangeCategory("SECURITY");
        event.setChangedBy("SYSTEM");
        event.setTicketNumber(ticketNumber);
        event.setEffectiveTime(LocalDateTime.now());
        event.setReversible(true);
        event.setReversalProcess("Contact support with ticket number");
        return event;
    }
    
    public static AccountStatusChangeEvent accountRestricted(String userId, String accountId, String previousStatus, 
                                                           Map<String, String> restrictions, String reason) {
        AccountStatusChangeEvent event = new AccountStatusChangeEvent();
        event.setUserId(userId);
        event.setAccountId(accountId);
        event.setPreviousStatus(previousStatus);
        event.setNewStatus("RESTRICTED");
        event.setChangeReason(reason);
        event.setChangeCategory("COMPLIANCE");
        event.setChangedBy("COMPLIANCE_SYSTEM");
        event.setRestrictions(restrictions);
        event.setEffectiveTime(LocalDateTime.now());
        event.setReviewDate(LocalDateTime.now().plusDays(30));
        event.setReversible(true);
        return event;
    }
    
    public static AccountStatusChangeEvent accountClosed(String userId, String accountId, String reason, 
                                                       String approvedBy) {
        AccountStatusChangeEvent event = new AccountStatusChangeEvent();
        event.setUserId(userId);
        event.setAccountId(accountId);
        event.setNewStatus("CLOSED");
        event.setChangeReason(reason);
        event.setChangeCategory("USER_REQUEST");
        event.setChangedBy("USER");
        event.setApprovedBy(approvedBy);
        event.setEffectiveTime(LocalDateTime.now());
        event.setReversible(false);
        return event;
    }
}