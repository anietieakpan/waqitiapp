package com.waqiti.compliance.dto;

import jakarta.validation.constraints.NotNull;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class SanctionsScreeningRequest {
    
    @NotNull
    private String entityId;
    
    @NotNull
    private String entityType; // INDIVIDUAL, ORGANIZATION, VESSEL, AIRCRAFT
    
    @NotNull
    private String name;
    
    private List<String> aliases;
    private String dateOfBirth;
    private String nationality;
    private String address;
    private String country;
    private List<String> identificationNumbers;
    private String screeningContext; // ONBOARDING, TRANSACTION, PERIODIC
}