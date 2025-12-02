package com.waqiti.user.service;

import java.math.BigDecimal;

@lombok.Data
@lombok.Builder
@lombok.NoArgsConstructor
@lombok.AllArgsConstructor
public class EmploymentLetterData {
    private String employeeName;
    private String employerName;
    private String employerAddress;
    private String jobTitle;
    private java.time.LocalDate employmentStartDate;
    private BigDecimal salary;
    private java.time.LocalDate letterDate;
    private String signatoryName;
    private String signatoryTitle;
}
