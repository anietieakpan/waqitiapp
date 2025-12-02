package com.waqiti.support.domain;

public enum ActivityType {
    // Ticket lifecycle
    CREATED,
    ASSIGNED,
    UNASSIGNED,
    STATUS_CHANGED,
    PRIORITY_CHANGED,
    CATEGORY_CHANGED,
    RESOLVED,
    CLOSED,
    REOPENED,
    CANCELLED,
    
    // Communication
    MESSAGE_ADDED,
    INTERNAL_NOTE_ADDED,
    ATTACHMENT_ADDED,
    ATTACHMENT_REMOVED,
    
    // Escalation
    ESCALATED,
    DE_ESCALATED,
    
    // Feedback
    FEEDBACK_RECEIVED,
    SATISFACTION_RATING_RECEIVED,
    
    // Tagging
    TAGGED,
    UNTAGGED,
    
    // Auto-categorization
    CATEGORIZED,
    RECATEGORIZED,
    CONFIDENCE_LOW,
    
    // Agent actions
    AGENT_ASSIGNED,
    AGENT_UNASSIGNED,
    VIEWED_BY_AGENT,
    
    // System actions
    AUTO_RESPONSE_SENT,
    SLA_BREACH_WARNING,
    SLA_BREACHED,
    REMINDER_SENT,
    
    // Administrative
    MERGED,
    SPLIT,
    LINKED,
    UNLINKED,
    ARCHIVED,
    RESTORED
}