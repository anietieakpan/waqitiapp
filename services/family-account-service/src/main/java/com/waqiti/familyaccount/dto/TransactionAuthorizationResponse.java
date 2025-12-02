package com.waqiti.familyaccount.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Transaction Authorization Response DTO
 *
 * @author Waqiti Family Account Team
 * @version 2.0.0
 * @since 2025-10-17
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class TransactionAuthorizationResponse {

    private Boolean authorized;
    private String declineReason;
    private Boolean requiresParentApproval;
    private String approvalMessage;
    private Long transactionAttemptId;
}
