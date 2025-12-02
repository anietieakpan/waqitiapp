package com.waqiti.payment.model;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import jakarta.persistence.Embeddable;

/**
 * ATM Location (Embeddable)
 */
@Embeddable
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class AtmLocation {
    private String atmId;
    private String address;
    private String city;
    private String state;
    private String country;
    private String postalCode;
    private Double latitude;
    private Double longitude;
    private String locationType;
}
