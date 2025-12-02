package com.waqiti.ledger.domain;

import lombok.Getter;
import lombok.RequiredArgsConstructor;

/**
 * Enum representing the type of ledger entry in double-entry bookkeeping.
 * Enforces strict type safety to prevent validation bypass.
 */
@Getter
@RequiredArgsConstructor
public enum EntryType {
    DEBIT("DEBIT", 1),
    CREDIT("CREDIT", -1);
    
    private final String code;
    private final int sign;
    
    /**
     * Parse entry type from string with strict validation.
     * Case-insensitive but only accepts valid enum values.
     * 
     * @param value The string value to parse
     * @return The corresponding EntryType
     * @throws IllegalArgumentException if value is invalid
     */
    public static EntryType fromString(String value) {
        if (value == null || value.trim().isEmpty()) {
            throw new IllegalArgumentException("Entry type cannot be null or empty");
        }
        
        String normalizedValue = value.trim().toUpperCase();
        
        for (EntryType type : EntryType.values()) {
            if (type.code.equals(normalizedValue)) {
                return type;
            }
        }
        
        throw new IllegalArgumentException(
            String.format("Invalid entry type: '%s'. Must be one of: %s", 
                value, java.util.Arrays.toString(EntryType.values()))
        );
    }
    
    /**
     * Check if this entry type is a debit.
     */
    public boolean isDebit() {
        return this == DEBIT;
    }
    
    /**
     * Check if this entry type is a credit.
     */
    public boolean isCredit() {
        return this == CREDIT;
    }
    
    /**
     * Get the opposite entry type (debit -> credit, credit -> debit).
     */
    public EntryType opposite() {
        return this == DEBIT ? CREDIT : DEBIT;
    }
}