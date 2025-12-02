package com.waqiti.payment.model;

public enum ProcessingMode {
    PARALLEL,
    SEQUENTIAL;

    public static ProcessingMode fromString(String value) {
        if (value == null) return PARALLEL;
        try {
            return ProcessingMode.valueOf(value.toUpperCase());
        } catch (IllegalArgumentException e) {
            return PARALLEL;
        }
    }
}
