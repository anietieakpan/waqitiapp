package com.waqiti.payment.service;

import java.time.LocalDateTime; /**
 * Update request DTO for payment links
 */
@lombok.Data
@lombok.Builder
@lombok.NoArgsConstructor
@lombok.AllArgsConstructor
public class UpdatePaymentLinkRequest {
    private String title;
    private String description;
    private String customMessage;
    private LocalDateTime expiresAt;
    private Integer maxUses;
    private Boolean requiresNote;
}
