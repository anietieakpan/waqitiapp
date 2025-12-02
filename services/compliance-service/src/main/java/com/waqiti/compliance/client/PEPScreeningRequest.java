package com.waqiti.compliance.client;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDate;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class PEPScreeningRequest {

    private String fullName;
    private LocalDate dateOfBirth;
    private String nationality;
    private String position;
    private String country;
}
