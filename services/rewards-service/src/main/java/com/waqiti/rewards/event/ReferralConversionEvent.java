package com.waqiti.rewards.event;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

/**
 * Event published when a referral converts (signup/transaction)
 *
 * @author Waqiti Platform Team
 * @version 2.0
 * @since 2025-11-09
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class ReferralConversionEvent {

    private String eventId;
    private String eventType;
    private Instant timestamp;
    private String correlationId;

    // Event payload
    private String linkId;
    private String programId;
    private UUID referrerId;
    private UUID refereeId;
    private String referralCode;
    private String conversionType; // SIGNUP, TRANSACTION
    private BigDecimal conversionValue;
    private String currency;
    private Instant convertedAt;

    public static ReferralConversionEvent create(
            String linkId, String programId, UUID referrerId, UUID refereeId,
            String referralCode, String conversionType, BigDecimal conversionValue,
            String currency, String correlationId) {
        return ReferralConversionEvent.builder()
                .eventId(UUID.randomUUID().toString())
                .eventType("REFERRAL_CONVERSION")
                .timestamp(Instant.now())
                .correlationId(correlationId)
                .linkId(linkId)
                .programId(programId)
                .referrerId(referrerId)
                .refereeId(refereeId)
                .referralCode(referralCode)
                .conversionType(conversionType)
                .conversionValue(conversionValue)
                .currency(currency)
                .convertedAt(Instant.now())
                .build();
    }
}
