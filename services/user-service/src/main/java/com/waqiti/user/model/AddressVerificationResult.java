package com.waqiti.user.model;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class AddressVerificationResult {
    private boolean verified;
    private BigDecimal verificationScore;
    private boolean addressExists;
    private boolean postalCodeValid;
    private String normalizedAddress;
    private String provider;
    private boolean deliverable;
    private String confidenceLevel;
    private Duration estimatedDeliveryTime;
}



