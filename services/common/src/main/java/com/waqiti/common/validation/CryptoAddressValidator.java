package com.waqiti.common.validation;

import jakarta.validation.ConstraintValidator;
import jakarta.validation.ConstraintValidatorContext;
import java.util.regex.Pattern;

/**
 * Validator for cryptocurrency addresses
 */
public class CryptoAddressValidator implements ConstraintValidator<ValidationConstraints.ValidCryptoAddress, String> {
    
    private String currency;
    
    // Bitcoin address patterns
    private static final Pattern BTC_P2PKH = Pattern.compile("^[13][a-km-zA-HJ-NP-Z1-9]{25,34}$");
    private static final Pattern BTC_P2SH = Pattern.compile("^3[a-km-zA-HJ-NP-Z1-9]{25,34}$");
    private static final Pattern BTC_BECH32 = Pattern.compile("^bc1[a-z0-9]{39,59}$");
    
    // Ethereum address pattern
    private static final Pattern ETH_PATTERN = Pattern.compile("^0x[a-fA-F0-9]{40}$");
    
    // Litecoin address patterns
    private static final Pattern LTC_LEGACY = Pattern.compile("^[LM3][a-km-zA-HJ-NP-Z1-9]{26,33}$");
    private static final Pattern LTC_SEGWIT = Pattern.compile("^ltc1[a-z0-9]{39,59}$");
    
    @Override
    public void initialize(ValidationConstraints.ValidCryptoAddress constraintAnnotation) {
        this.currency = constraintAnnotation.currency();
    }
    
    @Override
    public boolean isValid(String value, ConstraintValidatorContext context) {
        if (value == null || value.isEmpty()) {
            return true; // Let @NotNull/@NotEmpty handle null/empty validation
        }
        
        String address = value.trim();
        
        switch (currency.toUpperCase()) {
            case "BTC":
            case "BITCOIN":
                return validateBitcoinAddress(address);
            case "ETH":
            case "ETHEREUM":
                return validateEthereumAddress(address);
            case "LTC":
            case "LITECOIN":
                return validateLitecoinAddress(address);
            case "ANY":
                // Try to validate against any known pattern
                return validateBitcoinAddress(address) || 
                       validateEthereumAddress(address) || 
                       validateLitecoinAddress(address);
            default:
                // Unknown currency, perform basic validation
                return address.length() >= 26 && address.length() <= 95;
        }
    }
    
    private boolean validateBitcoinAddress(String address) {
        // Check P2PKH (starts with 1)
        if (address.startsWith("1") && BTC_P2PKH.matcher(address).matches()) {
            return true;
        }
        
        // Check P2SH (starts with 3)
        if (address.startsWith("3") && BTC_P2SH.matcher(address).matches()) {
            return true;
        }
        
        // Check Bech32 (starts with bc1)
        if (address.startsWith("bc1") && BTC_BECH32.matcher(address).matches()) {
            return true;
        }
        
        return false;
    }
    
    private boolean validateEthereumAddress(String address) {
        if (!ETH_PATTERN.matcher(address).matches()) {
            return false;
        }
        
        // Additional validation: check if address has valid checksum (EIP-55)
        // For simplicity, we'll accept both checksummed and non-checksummed addresses
        return true;
    }
    
    private boolean validateLitecoinAddress(String address) {
        // Check legacy addresses (starts with L or M)
        if ((address.startsWith("L") || address.startsWith("M")) && 
            LTC_LEGACY.matcher(address).matches()) {
            return true;
        }
        
        // Check SegWit addresses (starts with ltc1)
        if (address.startsWith("ltc1") && LTC_SEGWIT.matcher(address).matches()) {
            return true;
        }
        
        // Check P2SH addresses (starts with 3, same as Bitcoin)
        if (address.startsWith("3") && BTC_P2SH.matcher(address).matches()) {
            return true;
        }
        
        return false;
    }
}