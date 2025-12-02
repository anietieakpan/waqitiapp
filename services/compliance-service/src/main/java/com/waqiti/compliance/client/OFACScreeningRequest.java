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
public class OFACScreeningRequest {

    private String firstName;
    private String lastName;
    private LocalDate dateOfBirth;
    private String address;
    private String nationality;
    private String identification;
}
