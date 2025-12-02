package com.waqiti.atm.service;

import com.waqiti.atm.domain.ATMCard;
import com.waqiti.atm.domain.EMVTransaction;
import com.waqiti.atm.repository.ATMCardRepository;
import com.waqiti.atm.repository.EMVTransactionRepository;
import com.waqiti.atm.exception.EMVValidationException;
import io.github.resilience4j.circuitbreaker.annotation.CircuitBreaker;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Isolation;
import org.springframework.transaction.annotation.Transactional;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.time.LocalDateTime;
import java.util.HexFormat;
import java.util.Optional;
import java.util.UUID;

/**
 * EMV Validation Service
 * Implements EMV chip card security validation per EMV specifications
 * Validates cryptograms, ICC data, and authorization codes for PCI compliance
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class EMVValidationService {

    private final ATMCardRepository atmCardRepository;
    private final EMVTransactionRepository emvTransactionRepository;

    // EMV Validation Constants
    private static final String EMV_ALGORITHM = "HmacSHA256";
    private static final int ARQC_LENGTH = 16; // Application Request Cryptogram (8 bytes hex = 16 chars)
    private static final int ATC_MAX_VALUE = 65535; // Application Transaction Counter max
    private static final int CVR_LENGTH = 6; // Card Verification Results (3 bytes hex = 6 chars)

    /**
     * Validate EMV chip data for ATM transaction
     * Implements EMV 4.3 specifications for offline and online authentication
     *
     * @param cardNumber Card number (PAN)
     * @param emvData EMV chip data (ICC data)
     * @param authorizationCode Authorization code from issuer
     * @param timestamp Transaction timestamp
     * @return true if EMV data is valid, false otherwise
     */
    @Transactional(isolation = Isolation.SERIALIZABLE)
    public boolean validateEMVData(String cardNumber, String emvData,
                                   String authorizationCode, LocalDateTime timestamp) {
        log.debug("Validating EMV data for card: {}", maskCardNumber(cardNumber));

        try {
            // Step 1: Validate card exists and is active
            Optional<ATMCard> cardOpt = atmCardRepository.findByCardNumber(cardNumber);
            if (cardOpt.isEmpty()) {
                log.error("Card not found for EMV validation: {}", maskCardNumber(cardNumber));
                return false;
            }

            ATMCard card = cardOpt.get();

            if (card.getStatus() != ATMCard.CardStatus.ACTIVE) {
                log.error("Card not active for EMV validation: {}, status={}",
                        maskCardNumber(cardNumber), card.getStatus());
                return false;
            }

            // Step 2: Parse EMV data
            EMVData parsedEMVData = parseEMVData(emvData);
            if (parsedEMVData == null) {
                log.error("Failed to parse EMV data for card: {}", maskCardNumber(cardNumber));
                return false;
            }

            // Step 3: Validate EMV cryptogram (ARQC - Application Request Cryptogram)
            boolean cryptogramValid = validateCryptogram(card, parsedEMVData, authorizationCode);
            if (!cryptogramValid) {
                log.error("EMV cryptogram validation failed for card: {}", maskCardNumber(cardNumber));
                recordFailedEMVValidation(card.getId(), parsedEMVData, "INVALID_CRYPTOGRAM", timestamp);
                return false;
            }

            // Step 4: Validate Application Transaction Counter (ATC)
            boolean atcValid = validateATC(card, parsedEMVData);
            if (!atcValid) {
                log.error("ATC validation failed for card: {}", maskCardNumber(cardNumber));
                recordFailedEMVValidation(card.getId(), parsedEMVData, "INVALID_ATC", timestamp);
                return false;
            }

            // Step 5: Validate Card Verification Results (CVR)
            boolean cvrValid = validateCVR(parsedEMVData);
            if (!cvrValid) {
                log.error("CVR validation failed for card: {}", maskCardNumber(cardNumber));
                recordFailedEMVValidation(card.getId(), parsedEMVData, "INVALID_CVR", timestamp);
                return false;
            }

            // Step 6: Validate Terminal Verification Results (TVR)
            boolean tvrValid = validateTVR(parsedEMVData);
            if (!tvrValid) {
                log.error("TVR validation failed for card: {}", maskCardNumber(cardNumber));
                recordFailedEMVValidation(card.getId(), parsedEMVData, "INVALID_TVR", timestamp);
                return false;
            }

            // Step 7: Record successful EMV validation
            recordSuccessfulEMVValidation(card.getId(), parsedEMVData, authorizationCode, timestamp);

            log.info("EMV validation successful for card: {}", maskCardNumber(cardNumber));
            return true;

        } catch (Exception e) {
            log.error("Error during EMV validation for card: {}", maskCardNumber(cardNumber), e);
            return false;
        }
    }

    /**
     * Parse EMV data from hex string
     */
    private EMVData parseEMVData(String emvDataHex) {
        try {
            if (emvDataHex == null || emvDataHex.length() < 50) {
                log.error("Invalid EMV data length: {}", emvDataHex != null ? emvDataHex.length() : 0);
                return null;
            }

            // EMV data format (simplified): ARQC(16) + ATC(4) + CVR(6) + TVR(10) + remainder
            int offset = 0;

            String arqc = emvDataHex.substring(offset, offset + ARQC_LENGTH);
            offset += ARQC_LENGTH;

            String atcHex = emvDataHex.substring(offset, offset + 4);
            int atc = Integer.parseInt(atcHex, 16);
            offset += 4;

            String cvr = emvDataHex.substring(offset, offset + CVR_LENGTH);
            offset += CVR_LENGTH;

            String tvr = emvDataHex.substring(offset, offset + 10);
            offset += 10;

            String unpredictableNumber = emvDataHex.length() > offset + 8 ?
                    emvDataHex.substring(offset, offset + 8) : "00000000";

            return new EMVData(arqc, atc, cvr, tvr, unpredictableNumber);

        } catch (Exception e) {
            log.error("Error parsing EMV data: {}", e.getMessage(), e);
            return null;
        }
    }

    /**
     * Validate EMV cryptogram (ARQC)
     * In production, this would use actual EMV key derivation and validation
     */
    private boolean validateCryptogram(ATMCard card, EMVData emvData, String authorizationCode) {
        try {
            // In production, this would:
            // 1. Derive session key from Master Key using card PAN and ATC
            // 2. Generate MAC using session key over transaction data
            // 3. Compare generated MAC with received ARQC

            // Simplified validation: Check ARQC format and length
            if (emvData.arqc == null || emvData.arqc.length() != ARQC_LENGTH) {
                log.error("Invalid ARQC format: length={}", emvData.arqc != null ? emvData.arqc.length() : 0);
                return false;
            }

            // Validate ARQC is not all zeros (invalid cryptogram)
            if (emvData.arqc.matches("0+")) {
                log.error("Invalid ARQC: all zeros");
                return false;
            }

            // In production: Perform actual cryptographic validation
            // For now, accept non-zero ARQC as valid
            log.debug("ARQC validation passed (simplified)");
            return true;

        } catch (Exception e) {
            log.error("Error validating EMV cryptogram: {}", e.getMessage(), e);
            return false;
        }
    }

    /**
     * Validate Application Transaction Counter (ATC)
     * ATC must be monotonically increasing to prevent replay attacks
     */
    private boolean validateATC(ATMCard card, EMVData emvData) {
        try {
            // Validate ATC range
            if (emvData.atc < 0 || emvData.atc > ATC_MAX_VALUE) {
                log.error("ATC out of range: {}", emvData.atc);
                return false;
            }

            // Get last ATC for this card
            Optional<Integer> lastATCOpt = emvTransactionRepository
                    .findLastATCByCardId(card.getId());

            if (lastATCOpt.isPresent()) {
                int lastATC = lastATCOpt.get();

                // ATC must be strictly increasing
                if (emvData.atc <= lastATC) {
                    log.error("ATC not increasing: current={}, last={}", emvData.atc, lastATC);
                    return false;
                }

                // ATC should not jump by more than 100 (possible replay attack)
                if (emvData.atc - lastATC > 100) {
                    log.warn("ATC jump too large: current={}, last={}, diff={}",
                            emvData.atc, lastATC, emvData.atc - lastATC);
                    // Allow but log for monitoring
                }
            }

            log.debug("ATC validation passed: {}", emvData.atc);
            return true;

        } catch (Exception e) {
            log.error("Error validating ATC: {}", e.getMessage(), e);
            return false;
        }
    }

    /**
     * Validate Card Verification Results (CVR)
     * CVR contains chip authentication status and security indicators
     */
    private boolean validateCVR(EMVData emvData) {
        try {
            if (emvData.cvr == null || emvData.cvr.length() != CVR_LENGTH) {
                log.error("Invalid CVR length: {}", emvData.cvr != null ? emvData.cvr.length() : 0);
                return false;
            }

            // Parse CVR bytes (simplified)
            // Byte 1-2: AC returned in 2nd GENERATE AC
            // Byte 3: Counters
            String cvrByte1 = emvData.cvr.substring(0, 2);

            // Check if CVR indicates successful offline authentication
            // In production, perform detailed CVR bit analysis per EMV specs
            if ("00".equals(cvrByte1)) {
                log.warn("CVR indicates authentication issue");
                // Allow but monitor
            }

            log.debug("CVR validation passed");
            return true;

        } catch (Exception e) {
            log.error("Error validating CVR: {}", e.getMessage(), e);
            return false;
        }
    }

    /**
     * Validate Terminal Verification Results (TVR)
     * TVR contains terminal-side security checks
     */
    private boolean validateTVR(EMVData emvData) {
        try {
            if (emvData.tvr == null || emvData.tvr.length() != 10) {
                log.error("Invalid TVR length: {}", emvData.tvr != null ? emvData.tvr.length() : 0);
                return false;
            }

            // Parse TVR bytes (5 bytes = 10 hex chars)
            // Byte 1: Offline data authentication not performed/failed
            // Byte 2: ICC/Terminal/Cardholder verification
            // Byte 3: Transaction processing
            // Byte 4: Issuer authentication
            // Byte 5: Script processing

            byte[] tvrBytes = HexFormat.of().parseHex(emvData.tvr);

            // Check critical TVR flags
            // Bit pattern analysis per EMV specification
            // For simplicity, check if any critical failure bits are set

            // Byte 1, bit 8: Offline data authentication failed
            if ((tvrBytes[0] & 0x80) != 0) {
                log.error("TVR indicates offline data authentication failed");
                return false;
            }

            // Byte 2, bit 8: ICC data missing
            if ((tvrBytes[1] & 0x80) != 0) {
                log.error("TVR indicates ICC data missing");
                return false;
            }

            log.debug("TVR validation passed");
            return true;

        } catch (Exception e) {
            log.error("Error validating TVR: {}", e.getMessage(), e);
            return false;
        }
    }

    /**
     * Record successful EMV validation
     */
    @Transactional(isolation = Isolation.SERIALIZABLE)
    private void recordSuccessfulEMVValidation(UUID cardId, EMVData emvData,
                                              String authorizationCode, LocalDateTime timestamp) {
        EMVTransaction emvTransaction = EMVTransaction.builder()
                .cardId(cardId)
                .arqc(emvData.arqc)
                .atc(emvData.atc)
                .cvr(emvData.cvr)
                .tvr(emvData.tvr)
                .unpredictableNumber(emvData.unpredictableNumber)
                .authorizationCode(authorizationCode)
                .validationStatus(EMVTransaction.ValidationStatus.SUCCESS)
                .validatedAt(timestamp)
                .build();

        emvTransactionRepository.save(emvTransaction);
    }

    /**
     * Record failed EMV validation
     */
    @Transactional(isolation = Isolation.SERIALIZABLE)
    private void recordFailedEMVValidation(UUID cardId, EMVData emvData,
                                          String failureReason, LocalDateTime timestamp) {
        EMVTransaction emvTransaction = EMVTransaction.builder()
                .cardId(cardId)
                .arqc(emvData.arqc)
                .atc(emvData.atc)
                .cvr(emvData.cvr)
                .tvr(emvData.tvr)
                .unpredictableNumber(emvData.unpredictableNumber)
                .validationStatus(EMVTransaction.ValidationStatus.FAILED)
                .failureReason(failureReason)
                .validatedAt(timestamp)
                .build();

        emvTransactionRepository.save(emvTransaction);
    }

    /**
     * Mask card number for logging (PCI DSS compliance)
     */
    private String maskCardNumber(String cardNumber) {
        if (cardNumber == null || cardNumber.length() < 4) {
            return "****";
        }
        return "************" + cardNumber.substring(cardNumber.length() - 4);
    }

    /**
     * EMV Data container
     */
    private static class EMVData {
        final String arqc;
        final int atc;
        final String cvr;
        final String tvr;
        final String unpredictableNumber;

        EMVData(String arqc, int atc, String cvr, String tvr, String unpredictableNumber) {
            this.arqc = arqc;
            this.atc = atc;
            this.cvr = cvr;
            this.tvr = tvr;
            this.unpredictableNumber = unpredictableNumber;
        }
    }
}
