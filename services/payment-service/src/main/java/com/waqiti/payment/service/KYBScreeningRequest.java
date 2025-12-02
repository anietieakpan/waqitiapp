package com.waqiti.payment.service;

import lombok.Builder;
import lombok.Data;

import java.util.List;
import java.util.UUID;

@Data
@Builder
public class KYBScreeningRequest {
    private UUID businessId;
    private String businessName;
    private String legalName;
    private String taxId;
    private String registrationNumber;
    private String country;
    private String industry;
    private List<BeneficialOwner> beneficialOwners;
    private List<String> screeningTypes;
}

