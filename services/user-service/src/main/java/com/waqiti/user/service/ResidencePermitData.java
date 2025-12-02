package com.waqiti.user.service;

@lombok.Data
@lombok.Builder
@lombok.NoArgsConstructor
@lombok.AllArgsConstructor
public class ResidencePermitData {
    private String permitNumber;
    private String holderName;
    private String nationality;
    private String permitType;
    private java.time.LocalDate issueDate;
    private java.time.LocalDate expiryDate;
    private String issuingAuthority;
}
