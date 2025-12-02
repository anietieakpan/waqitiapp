package com.waqiti.corebanking.service;

import com.waqiti.common.util.DataMaskingUtil;
import com.waqiti.corebanking.domain.Account;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.security.SecureRandom;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;

@Service
@RequiredArgsConstructor
@Slf4j
public class AccountNumberGeneratorService {

    private final SecureRandom secureRandom = new SecureRandom();

    /**
     * Generates a unique account number based on account type
     */
    public String generateAccountNumber(Account.AccountType accountType) {
        String prefix = getAccountPrefix(accountType);
        String timestamp = LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyyMMdd"));
        String randomSuffix = generateRandomSuffix(6);
        
        String accountNumber = prefix + timestamp + randomSuffix;
        
        // Add check digit for validation
        String checkDigit = calculateCheckDigit(accountNumber);
        
        String finalAccountNumber = accountNumber + checkDigit;
        
        log.debug("Generated account number: {} for type: {}", finalAccountNumber, accountType);
        
        return finalAccountNumber;
    }

    /**
     * Validates an account number format and check digit
     */
    public boolean validateAccountNumber(String accountNumber) {
        if (accountNumber == null || accountNumber.length() < 10) {
            return false;
        }
        
        try {
            // Extract check digit (last digit)
            String checkDigit = accountNumber.substring(accountNumber.length() - 1);
            String baseNumber = accountNumber.substring(0, accountNumber.length() - 1);
            
            // Calculate expected check digit
            String expectedCheckDigit = calculateCheckDigit(baseNumber);
            
            return checkDigit.equals(expectedCheckDigit);

        } catch (Exception e) {
            // PCI DSS COMPLIANCE: Mask account number in logs per PCI DSS v4.0 Requirement 3.3
            log.warn("Error validating account number: {}", DataMaskingUtil.maskAccountNumber(accountNumber), e);
            return false;
        }
    }

    /**
     * Extracts account type from account number prefix
     */
    public Account.AccountType getAccountTypeFromNumber(String accountNumber) {
        if (accountNumber == null || accountNumber.length() < 2) {
            throw new IllegalArgumentException("Invalid account number format");
        }
        
        String prefix = accountNumber.substring(0, 2);
        
        switch (prefix) {
            case "10":
                return Account.AccountType.USER_WALLET;
            case "20":
                return Account.AccountType.USER_SAVINGS;
            case "30":
                return Account.AccountType.USER_CREDIT;
            case "40":
                return Account.AccountType.BUSINESS_OPERATING;
            case "50":
                return Account.AccountType.BUSINESS_ESCROW;
            case "60":
                return Account.AccountType.SYSTEM_ASSET;
            case "70":
                return Account.AccountType.SYSTEM_LIABILITY;
            default:
                throw new IllegalArgumentException("Unknown account type prefix: " + prefix);
        }
    }

    private String getAccountPrefix(Account.AccountType accountType) {
        switch (accountType) {
            case USER_WALLET:
                return "10";
            case USER_SAVINGS:
                return "20";
            case USER_CREDIT:
                return "30";
            case BUSINESS_OPERATING:
                return "40";
            case BUSINESS_ESCROW:
                return "50";
            case SYSTEM_ASSET:
                return "60";
            case SYSTEM_LIABILITY:
                return "70";
            case FEE_COLLECTION:
                return "80";
            case SUSPENSE:
                return "90";
            case NOSTRO:
                return "91";
            case MERCHANT:
                return "92";
            case TRANSIT:
                return "93";
            case RESERVE:
                return "94";
            default:
                return "99";
        }
    }

    private String generateRandomSuffix(int length) {
        StringBuilder suffix = new StringBuilder();
        for (int i = 0; i < length; i++) {
            suffix.append(secureRandom.nextInt(10));
        }
        return suffix.toString();
    }

    /**
     * Calculates check digit using Luhn algorithm
     */
    private String calculateCheckDigit(String accountNumber) {
        int sum = 0;
        boolean alternate = false;
        
        // Process digits from right to left
        for (int i = accountNumber.length() - 1; i >= 0; i--) {
            int digit = Character.getNumericValue(accountNumber.charAt(i));
            
            if (alternate) {
                digit *= 2;
                if (digit > 9) {
                    digit = (digit % 10) + 1;
                }
            }
            
            sum += digit;
            alternate = !alternate;
        }
        
        int checkDigit = (10 - (sum % 10)) % 10;
        return String.valueOf(checkDigit);
    }
}