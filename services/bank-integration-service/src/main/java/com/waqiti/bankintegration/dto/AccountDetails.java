package com.waqiti.bankintegration.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Account Details for ACH transfers
 *
 * SECURITY FIX: Replaced @Data with @Getter/@Setter to prevent account number exposure
 * PCI DSS & GDPR: Bank account numbers are sensitive and must be masked
 */
@lombok.Getter
@lombok.Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class AccountDetails {
    private String accountNumber; // PCI DSS: Sensitive - must mask in logs
    private String routingNumber;
    private String accountType; // CHECKING, SAVINGS
    private String accountHolderName;
    private String bankName;

    /**
     * Custom toString() that masks account number per PCI DSS requirements
     */
    @Override
    public String toString() {
        return "AccountDetails{" +
            "accountNumber='[REDACTED-PCI-DSS]'" +
            ", routingNumber='" + routingNumber + '\'' +
            ", accountType='" + accountType + '\'' +
            ", accountHolderName='" + accountHolderName + '\'' +
            ", bankName='" + bankName + '\'' +
            '}';
    }
}