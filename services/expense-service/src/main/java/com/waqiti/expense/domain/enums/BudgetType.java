package com.waqiti.expense.domain.enums;

/**
 * Budget Type Enumeration
 */
public enum BudgetType {
    OVERALL,        // Total spending budget
    CATEGORY,       // Budget for specific categories
    PROJECT,        // Project-based budget
    GOAL_BASED,     // Budget tied to specific financial goals
    ENVELOPE,       // Envelope budgeting method
    ZERO_BASED,     // Zero-based budgeting
    PERCENTAGE,     // Percentage-based budgeting (50/30/20 rule)
    FLEXIBLE,       // Flexible spending budget
    EMERGENCY,      // Emergency fund budget
    VACATION,       // Travel/vacation budget
    BUSINESS,       // Business expense budget
    CUSTOM          // User-defined custom budget
}