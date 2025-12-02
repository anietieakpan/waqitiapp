package com.waqiti.user.service;

@lombok.Data
@lombok.Builder
@lombok.NoArgsConstructor
@lombok.AllArgsConstructor
public class DriversLicenseData {
    private String licenseNumber;
    private String firstName;
    private String lastName;
    private java.time.LocalDate dateOfBirth;
    private String address;
    private java.time.LocalDate issueDate;
    private java.time.LocalDate expiryDate;
    private String licenseClass;
    private String barcode;
    private String magneticStripe;
    
    public void setBarcode(String barcode) {
        this.barcode = barcode;
    }
    
    public void setMagneticStripe(String magneticStripe) {
        this.magneticStripe = magneticStripe;
    }
}
