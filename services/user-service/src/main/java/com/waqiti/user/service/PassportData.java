package com.waqiti.user.service;

@lombok.Data
@lombok.Builder
@lombok.NoArgsConstructor
@lombok.AllArgsConstructor
public class PassportData {
    private String documentNumber;
    private String firstName;
    private String lastName;
    private java.time.LocalDate dateOfBirth;
    private String nationality;
    private java.time.LocalDate issueDate;
    private java.time.LocalDate expiryDate;
    private String mrzCode;
}
