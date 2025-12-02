package com.waqiti.common.validation.validators;

import com.waqiti.common.validation.PaymentValidation;
import com.waqiti.common.validation.PaymentValidation.ValidCryptoAddress;
import jakarta.validation.ConstraintValidator;
import jakarta.validation.ConstraintValidatorContext;

/**
 * Validator for cryptocurrency addresses
 */
public class CryptoAddressValidator implements ConstraintValidator<ValidCryptoAddress, String> {
    
    private PaymentValidation.CryptoType cryptoType;
    
    @Override
    public void initialize(ValidCryptoAddress constraintAnnotation) {
        this.cryptoType = constraintAnnotation.cryptoType();
    }
    
    @Override
    public boolean isValid(String value, ConstraintValidatorContext context) {
        if (value == null || value.trim().isEmpty()) {
            return true; // Let @NotNull handle null validation
        }
        
        return switch (cryptoType) {
            case BITCOIN -> isValidBitcoinAddress(value);
            case ETHEREUM -> isValidEthereumAddress(value);
            case LITECOIN -> isValidLitecoinAddress(value);
            case RIPPLE -> isValidRippleAddress(value);
            case STELLAR -> isValidStellarAddress(value);
        };
    }
    
    private boolean isValidBitcoinAddress(String address) {
        // Bitcoin address validation
        // P2PKH addresses start with 1, P2SH addresses start with 3
        // Bech32 addresses start with bc1
        return address.matches("^[13][a-km-zA-HJ-NP-Z1-9]{25,34}$") ||
               address.matches("^bc1[a-z0-9]{39,59}$");
    }
    
    private boolean isValidEthereumAddress(String address) {
        // Ethereum address validation
        // 40 hex characters preceded by 0x
        return address.matches("^0x[a-fA-F0-9]{40}$");
    }
    
    private boolean isValidLitecoinAddress(String address) {
        // Litecoin address validation
        // Legacy addresses start with L or M
        // SegWit addresses start with ltc1
        return address.matches("^[LM][a-km-zA-HJ-NP-Z1-9]{26,33}$") ||
               address.matches("^ltc1[a-z0-9]{39,59}$");
    }
    
    private boolean isValidRippleAddress(String address) {
        // Ripple (XRP) address validation
        // Ripple addresses start with 'r' and are 25-35 characters
        return address.matches("^r[0-9a-zA-Z]{24,34}$");
    }
    
    private boolean isValidStellarAddress(String address) {
        // Stellar address validation
        // Stellar addresses start with 'G' and are 56 characters
        return address.matches("^G[A-Z2-7]{55}$");
    }
}