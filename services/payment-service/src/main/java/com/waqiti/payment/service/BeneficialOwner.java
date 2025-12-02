package com.waqiti.payment.service;

import lombok.Builder;
import lombok.Data;

@Data
@Builder
public class BeneficialOwner {
    private String name;
    private String dateOfBirth;
    private String nationality;
    private double ownershipPercentage;
    private String role;
}
