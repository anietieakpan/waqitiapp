package com.waqiti.wallet.dto;

import lombok.*;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.util.UUID;

/**
 * Request DTO for unlinking a bank account.
 * 
 * @author Principal Software Engineer
 * @since 1.0.0
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class UnlinkBankAccountRequest {
    
    @NotNull(message = "User ID is required")
    private UUID userId;
    
    @Size(max = 500, message = "Reason must not exceed 500 characters")
    private String reason;
    
    private boolean keepTransactionHistory;
    
    private boolean notifyUser;
}