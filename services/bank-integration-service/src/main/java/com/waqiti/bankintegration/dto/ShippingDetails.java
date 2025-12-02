package com.waqiti.bankintegration.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDate;

/**
 * Shipping details for payment processing
 * 
 * Contains shipping information for physical goods
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ShippingDetails {
    private String name;
    private String phone;
    private Address address;
    private String carrier;
    private String trackingNumber;
    private String shippingMethod;
    private LocalDate estimatedDelivery;
    private String instructions;
}