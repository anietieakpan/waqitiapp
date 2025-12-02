package com.waqiti.payment.client;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.UUID;

/**
 * Create Bank Account Request DTO
 *
 * @author Waqiti Platform Team
 * @version 2.0.0
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class CreateBankAccountRequest {
    private UUID userId;
    private String accountHolderName;
    private String routingNumber;
    private String accountNumber;
    private String accountType; // CHECKING, SAVINGS, etc.
    private String currency;
}
