package com.waqiti.recurringpayment.domain;

/**
 * Holiday handling options.
 */
public enum HolidayHandling {
    SKIP,           // Skip payment on holidays
    BEFORE,         // Process before holiday
    AFTER,          // Process after holiday
    PROCESS_ANYWAY  // Process on holiday
}
