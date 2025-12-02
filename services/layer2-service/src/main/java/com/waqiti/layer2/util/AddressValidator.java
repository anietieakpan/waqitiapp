package com.waqiti.layer2.util;

import com.waqiti.layer2.exception.InvalidAddressException;
import org.web3j.crypto.Keys;

/**
 * Utility for validating Ethereum addresses
 */
public class AddressValidator {

    /**
     * Validates Ethereum address format and checksum
     *
     * @param address Ethereum address to validate
     * @throws InvalidAddressException if address is invalid
     */
    public static void validateEthereumAddress(String address) {
        if (address == null || address.isEmpty()) {
            throw new InvalidAddressException("Address cannot be null or empty");
        }

        if (!address.startsWith("0x")) {
            throw new InvalidAddressException("Address must start with 0x: " + address);
        }

        if (address.length() != 42) {
            throw new InvalidAddressException("Address must be 42 characters (0x + 40 hex): " + address);
        }

        try {
            // Validate checksum using Web3j
            String checksumAddress = Keys.toChecksumAddress(address);
            if (!address.equals(checksumAddress) && !address.equals(address.toLowerCase())) {
                throw new InvalidAddressException("Invalid address checksum: " + address);
            }
        } catch (Exception e) {
            throw new InvalidAddressException("Invalid Ethereum address: " + address + " - " + e.getMessage());
        }
    }

    /**
     * Check if address is valid without throwing exception
     *
     * @param address Address to check
     * @return true if valid, false otherwise
     */
    public static boolean isValidAddress(String address) {
        try {
            validateEthereumAddress(address);
            return true;
        } catch (InvalidAddressException e) {
            return false;
        }
    }
}
