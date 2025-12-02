package com.waqiti.business.event;

import com.fasterxml.jackson.annotation.JsonSubTypes;
import com.fasterxml.jackson.annotation.JsonTypeInfo;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * Base class for all business events
 */
@JsonTypeInfo(
    use = JsonTypeInfo.Id.NAME,
    include = JsonTypeInfo.As.PROPERTY,
    property = "eventType"
)
@JsonSubTypes({
    @JsonSubTypes.Type(value = BusinessCardEvent.class, name = "BUSINESS_CARD"),
    @JsonSubTypes.Type(value = BusinessExpenseEvent.class, name = "BUSINESS_EXPENSE"),
    @JsonSubTypes.Type(value = ExpenseNotificationEvent.class, name = "EXPENSE_NOTIFICATION"),
    @JsonSubTypes.Type(value = ExpenseReimbursementEvent.class, name = "EXPENSE_REIMBURSEMENT"),
    @JsonSubTypes.Type(value = BudgetAlertEvent.class, name = "BUDGET_ALERT"),
    @JsonSubTypes.Type(value = ApprovalNotificationEvent.class, name = "APPROVAL_NOTIFICATION")
})
@Data
public abstract class BusinessEvent {
    private String eventId;
    private String eventType;
    private String businessId;
    private String userId;
    private LocalDateTime timestamp;
    private String source;
    private String version;
    
    protected BusinessEvent(String eventType) {
        this.eventType = eventType;
        this.timestamp = LocalDateTime.now();
        this.version = "1.0";
    }
}