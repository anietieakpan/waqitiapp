package com.waqiti.common.security.masking;

import org.springframework.stereotype.Service;

import java.util.regex.Pattern;

@Service
public class DataMaskingService {

    private static final Pattern EMAIL_PATTERN = Pattern.compile("^(.{1,3}).*(@.*)$");
    private static final Pattern PHONE_PATTERN = Pattern.compile("^(\\d{3})\\d*(\\d{2})$");
    private static final Pattern SSN_PATTERN = Pattern.compile("^(\\d{3})-?\\d{2}-?(\\d{4})$");
    private static final Pattern CARD_NUMBER_PATTERN = Pattern.compile("^(\\d{4})\\d*(\\d{4})$");
    private static final Pattern ACCOUNT_NUMBER_PATTERN = Pattern.compile("^(.{2}).*(.{4})$");
    
    /**
     * Mask email address - shows first 3 characters and domain
     * Example: joh***@example.com
     */
    public String maskEmail(String email) {
        if (email == null || email.isEmpty()) {
            return email;
        }
        return EMAIL_PATTERN.matcher(email).replaceAll("$1***$2");
    }
    
    /**
     * Mask phone number - shows first 3 and last 2 digits
     * Example: 123-***-**45
     */
    public String maskPhoneNumber(String phoneNumber) {
        if (phoneNumber == null || phoneNumber.isEmpty()) {
            return phoneNumber;
        }
        String digits = phoneNumber.replaceAll("[^0-9]", "");
        if (digits.length() < 5) {
            return "***";
        }
        return PHONE_PATTERN.matcher(digits).replaceAll("$1-***-**$2");
    }
    
    /**
     * Mask SSN - shows last 4 digits only
     * Example: ***-**-1234
     */
    public String maskSSN(String ssn) {
        if (ssn == null || ssn.isEmpty()) {
            return ssn;
        }
        return SSN_PATTERN.matcher(ssn).replaceAll("***-**-$2");
    }
    
    /**
     * Mask credit card number - shows first 4 and last 4 digits
     * Example: 1234 **** **** 5678
     */
    public String maskCardNumber(String cardNumber) {
        if (cardNumber == null || cardNumber.isEmpty()) {
            return cardNumber;
        }
        String digits = cardNumber.replaceAll("[^0-9]", "");
        if (digits.length() < 8) {
            return "****";
        }
        return CARD_NUMBER_PATTERN.matcher(digits).replaceAll("$1 **** **** $2");
    }
    
    /**
     * Mask bank account number - shows first 2 and last 4 characters
     * Example: 12******7890
     */
    public String maskAccountNumber(String accountNumber) {
        if (accountNumber == null || accountNumber.isEmpty()) {
            return accountNumber;
        }
        if (accountNumber.length() < 6) {
            return "****";
        }
        return ACCOUNT_NUMBER_PATTERN.matcher(accountNumber).replaceAll("$1****$2");
    }
    
    /**
     * Mask name - shows first character and masks the rest
     * Example: J*** D**
     */
    public String maskName(String name) {
        if (name == null || name.isEmpty()) {
            return name;
        }
        
        String[] parts = name.split("\\s+");
        StringBuilder masked = new StringBuilder();
        
        for (int i = 0; i < parts.length; i++) {
            if (i > 0) masked.append(" ");
            
            String part = parts[i];
            if (part.length() > 0) {
                masked.append(part.charAt(0));
                masked.append("*".repeat(Math.min(part.length() - 1, 3)));
            }
        }
        
        return masked.toString();
    }
    
    /**
     * Mask address - shows partial street number and city
     * Example: 1** Main St, S** Francisco, CA
     */
    public String maskAddress(String address) {
        if (address == null || address.isEmpty()) {
            return address;
        }
        
        String[] parts = address.split(",");
        if (parts.length == 0) {
            return maskGeneric(address);
        }
        
        StringBuilder masked = new StringBuilder();
        
        // Mask street address
        String street = parts[0].trim();
        if (street.matches("^\\d+.*")) {
            masked.append(street.charAt(0)).append("** ");
            String[] streetParts = street.split("\\s+", 2);
            if (streetParts.length > 1) {
                masked.append(streetParts[1]);
            }
        } else {
            masked.append(maskGeneric(street));
        }
        
        // Keep city partially visible
        if (parts.length > 1) {
            masked.append(", ");
            String city = parts[1].trim();
            if (city.length() > 3) {
                masked.append(city.substring(0, 3)).append("*".repeat(Math.min(city.length() - 3, 5)));
            } else {
                masked.append(city);
            }
        }
        
        // Keep state/zip visible
        for (int i = 2; i < parts.length; i++) {
            masked.append(", ").append(parts[i].trim());
        }
        
        return masked.toString();
    }
    
    /**
     * Generic masking - shows first and last character
     * Example: p*****d
     */
    public String maskGeneric(String data) {
        if (data == null || data.isEmpty()) {
            return data;
        }
        
        if (data.length() <= 2) {
            return "*".repeat(data.length());
        }
        
        return data.charAt(0) + "*".repeat(data.length() - 2) + data.charAt(data.length() - 1);
    }
    
    /**
     * Mask IP address - shows first octet
     * Example: 192.*.*.*
     */
    public String maskIpAddress(String ipAddress) {
        if (ipAddress == null || ipAddress.isEmpty()) {
            return ipAddress;
        }
        
        String[] octets = ipAddress.split("\\.");
        if (octets.length != 4) {
            return maskGeneric(ipAddress);
        }
        
        return octets[0] + ".*.*.*";
    }
}