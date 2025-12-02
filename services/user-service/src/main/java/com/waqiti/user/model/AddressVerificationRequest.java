package com.waqiti.user.model;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.Instant;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class AddressVerificationRequest {
    private String userId;
    private String street;
    private String city;
    private String state;
    private String postalCode;
    private String country;
    private String verificationMethod;
    private String documentUrl;
    private Instant requestTimestamp;
}
