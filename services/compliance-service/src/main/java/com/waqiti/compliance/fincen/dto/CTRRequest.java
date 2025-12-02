package com.waqiti.compliance.fincen.dto;

import lombok.Builder;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDate;

/**
 * Currency Transaction Report (CTR) Request
 *
 * Data required to file a CTR with FinCEN for transactions >= $10,000
 */
@Data
@Builder
public class CTRRequest {
    private String transactionId;
    private BigDecimal amount;
    private String transactionType; // "D" = Deposit, "W" = Withdrawal
    private LocalDate transactionDate;

    // Customer information
    private String customerFirstName;
    private String customerLastName;
    private String customerSSN;
    private String customerAddress;
    private String customerCity;
    private String customerState;
    private String customerZip;
    private String customerCountry;
    private String customerOccupation;
}
