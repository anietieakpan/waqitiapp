package com.waqiti.wallet.dto;

import lombok.*;
import jakarta.validation.constraints.NotNull;
import java.util.UUID;

/**
 * Request DTO for setting a bank account as primary.
 * 
 * @author Principal Software Engineer
 * @since 1.0.0
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class SetPrimaryAccountRequest {
    
    @NotNull(message = "User ID is required")
    private UUID userId;
    
    private String reason;
    
    private boolean forceUpdate;
}