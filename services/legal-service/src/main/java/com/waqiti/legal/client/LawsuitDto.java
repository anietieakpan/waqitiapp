package com.waqiti.legal.client;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.io.Serializable;
import java.time.LocalDate;

/**
 * Lawsuit DTO
 *
 * @author Waqiti Legal Team
 * @version 1.0.0
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class LawsuitDto implements Serializable {

    private static final long serialVersionUID = 1L;

    private String lawsuitId;
    private String customerId;
    private String accountId;
    private String caseNumber;
    private String court;
    private String status; // FILED, ACTIVE, SUSPENDED, DISMISSED, JUDGMENT
    private LocalDate filingDate;
    private LocalDate suspensionDate;
    private String suspensionReason;
    private String bankruptcyId; // If suspended due to bankruptcy
}
