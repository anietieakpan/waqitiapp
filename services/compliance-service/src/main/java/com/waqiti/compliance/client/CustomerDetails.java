package com.waqiti.compliance.client;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDate;
import java.util.UUID;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class CustomerDetails {

    private UUID customerId;
    private String firstName;
    private String lastName;
    private String fullName;
    private LocalDate dateOfBirth;
    private String address;
    private String nationality;
    private String email;
    private String phoneNumber;
    private String identificationType;
    private String identificationNumber;
}
