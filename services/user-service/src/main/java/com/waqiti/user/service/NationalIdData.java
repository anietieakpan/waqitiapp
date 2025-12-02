package com.waqiti.user.service;

@lombok.Data
@lombok.Builder
@lombok.NoArgsConstructor
@lombok.AllArgsConstructor
public class NationalIdData {
    private String idNumber;
    private String firstName;
    private String lastName;
    private java.time.LocalDate dateOfBirth;
    private String gender;
    private String nationality;
    private java.time.LocalDate issueDate;
    private java.time.LocalDate expiryDate;
}
