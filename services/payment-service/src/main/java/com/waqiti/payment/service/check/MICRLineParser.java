package com.waqiti.payment.service.check;

import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * MICR (Magnetic Ink Character Recognition) Line Parser
 *
 * Parses MICR lines from checks to extract:
 * - Routing Number (9 digits) - ABA routing transit number
 * - Account Number (variable length, typically 4-17 digits)
 * - Check Number (variable length, typically 1-10 digits)
 *
 * MICR Line Format:
 * ⑆routing⑆account⑆check⑆
 * OR
 * [transit]⑆account⑆check⑆amount⑆
 *
 * Special Characters (MICR E-13B font):
 * ⑆ (U+2446) - Transit/Amount symbol
 * ⑈ (U+2448) - On-Us symbol (account delimiter)
 * ⑆ (U+2446) - Dash symbol
 *
 * Standard US Check Format:
 * ⑆123456789⑆ ⑈123456789012⑈ 0001⑆
 *  ^routing^    ^account^      ^check^
 *
 * @author Waqiti Platform - Payment Processing Team
 * @version 1.0
 * @since 2025-10-11
 */
@Component
@Slf4j
public class MICRLineParser {

    // MICR special characters (multiple representations)
    private static final String TRANSIT_SYMBOL = "[⑆\u2446\\|]";  // Transit symbol or pipe
    private static final String ON_US_SYMBOL = "[⑈\u2448]";       // On-Us symbol
    private static final String DASH_SYMBOL = "[⑆\u2446-]";       // Dash or transit

    // Regex patterns for different MICR formats
    private static final Pattern STANDARD_MICR_PATTERN = Pattern.compile(
        TRANSIT_SYMBOL + "(\\d{9})" + TRANSIT_SYMBOL + "\\s*" +
        ON_US_SYMBOL + "?(\\d{4,17})" + ON_US_SYMBOL + "?\\s*" +
        "(\\d{1,10})" + TRANSIT_SYMBOL + "?"
    );

    // Alternative format (space-separated)
    private static final Pattern SPACE_SEPARATED_PATTERN = Pattern.compile(
        "(\\d{9})\\s+(\\d{4,17})\\s+(\\d{1,10})"
    );

    // Simplified format (common in scanned checks)
    private static final Pattern SIMPLE_PATTERN = Pattern.compile(
        "([\\dOlIoS]{9}).*?([\\dOlIoS]{4,17}).*?([\\dOlIoS]{1,10})"
    );

    // ABA routing number validation (Mod 10 algorithm)
    private static final int[] ABA_MULTIPLIERS = {3, 7, 1, 3, 7, 1, 3, 7, 1};

    /**
     * Parse MICR line and extract routing, account, and check numbers
     *
     * @param micrLine The MICR line string from check image
     * @return MICRData object containing extracted information
     * @throws MICRParseException if MICR line cannot be parsed
     */
    public MICRData parseMICRLine(String micrLine) throws MICRParseException {
        if (micrLine == null || micrLine.trim().isEmpty()) {
            throw new MICRParseException("MICR line is null or empty");
        }

        // Clean and normalize MICR line
        String normalized = normalizeMICRLine(micrLine);
        log.debug("Normalized MICR line: {}", maskMICR(normalized));

        // Try different parsing strategies
        MICRData data = tryStandardFormat(normalized);
        if (data == null) {
            data = trySpaceSeparatedFormat(normalized);
        }
        if (data == null) {
            data = trySimpleFormat(normalized);
        }

        if (data == null) {
            throw new MICRParseException("Unable to parse MICR line: " + maskMICR(micrLine));
        }

        // Validate extracted data
        validateMICRData(data);

        log.info("Successfully parsed MICR line: routing={}, accountLength={}, checkNumber={}",
            maskRouting(data.getRoutingNumber()),
            data.getAccountNumber().length(),
            data.getCheckNumber());

        return data;
    }

    /**
     * Extract routing number from MICR line
     */
    public String extractRoutingNumber(String micrLine) {
        try {
            return parseMICRLine(micrLine).getRoutingNumber();
        } catch (MICRParseException e) {
            log.error("Failed to extract routing number: {}", e.getMessage());
            return null;
        }
    }

    /**
     * Extract account number from MICR line
     */
    public String extractAccountNumber(String micrLine) {
        try {
            return parseMICRLine(micrLine).getAccountNumber();
        } catch (MICRParseException e) {
            log.error("Failed to extract account number: {}", e.getMessage());
            return null;
        }
    }

    /**
     * Extract check number from MICR line
     */
    public String extractCheckNumber(String micrLine) {
        try {
            return parseMICRLine(micrLine).getCheckNumber();
        } catch (MICRParseException e) {
            log.error("Failed to extract check number: {}", e.getMessage());
            return null;
        }
    }

    // Private helper methods

    private String normalizeMICRLine(String micrLine) {
        // Remove common OCR artifacts and normalize
        return micrLine
            .toUpperCase()
            .replaceAll("\\s+", " ")  // Normalize whitespace
            .replaceAll("[^0-9⑆⑈\\|\\-\\s]", "")  // Remove non-MICR characters
            .trim();
    }

    private MICRData tryStandardFormat(String micrLine) {
        Matcher matcher = STANDARD_MICR_PATTERN.matcher(micrLine);
        if (matcher.find()) {
            return MICRData.builder()
                .routingNumber(matcher.group(1))
                .accountNumber(matcher.group(2))
                .checkNumber(matcher.group(3))
                .build();
        }
        return null;
    }

    private MICRData trySpaceSeparatedFormat(String micrLine) {
        Matcher matcher = SPACE_SEPARATED_PATTERN.matcher(micrLine);
        if (matcher.find()) {
            return MICRData.builder()
                .routingNumber(matcher.group(1))
                .accountNumber(matcher.group(2))
                .checkNumber(matcher.group(3))
                .build();
        }
        return null;
    }

    private MICRData trySimpleFormat(String micrLine) {
        // OCR may misread characters, attempt correction
        String corrected = correctOCRErrors(micrLine);
        Matcher matcher = SIMPLE_PATTERN.matcher(corrected);
        if (matcher.find()) {
            return MICRData.builder()
                .routingNumber(correctOCRErrors(matcher.group(1)))
                .accountNumber(correctOCRErrors(matcher.group(2)))
                .checkNumber(correctOCRErrors(matcher.group(3)))
                .build();
        }
        return null;
    }

    private String correctOCRErrors(String text) {
        // Common OCR misreads
        return text
            .replace('O', '0')
            .replace('o', '0')
            .replace('l', '1')
            .replace('I', '1')
            .replace('S', '5');
    }

    private void validateMICRData(MICRData data) throws MICRParseException {
        // Validate routing number format
        if (data.getRoutingNumber() == null || !data.getRoutingNumber().matches("\\d{9}")) {
            throw new MICRParseException("Invalid routing number format: must be 9 digits");
        }

        // Validate routing number checksum (ABA Mod 10 algorithm)
        if (!validateABAChecksum(data.getRoutingNumber())) {
            log.warn("Invalid ABA routing number checksum: {}", maskRouting(data.getRoutingNumber()));
            // Don't throw - some valid routing numbers may fail due to OCR errors
        }

        // Validate account number
        if (data.getAccountNumber() == null || !data.getAccountNumber().matches("\\d{4,17}")) {
            throw new MICRParseException("Invalid account number format: must be 4-17 digits");
        }

        // Validate check number
        if (data.getCheckNumber() == null || !data.getCheckNumber().matches("\\d{1,10}")) {
            throw new MICRParseException("Invalid check number format: must be 1-10 digits");
        }
    }

    /**
     * Validate ABA routing number using Mod 10 algorithm
     *
     * Formula: (3×d1 + 7×d2 + 1×d3 + 3×d4 + 7×d5 + 1×d6 + 3×d7 + 7×d8 + 1×d9) mod 10 = 0
     */
    private boolean validateABAChecksum(String routingNumber) {
        if (routingNumber.length() != 9) {
            return false;
        }

        int sum = 0;
        for (int i = 0; i < 9; i++) {
            int digit = Character.getNumericValue(routingNumber.charAt(i));
            sum += digit * ABA_MULTIPLIERS[i];
        }

        return (sum % 10) == 0;
    }

    private String maskMICR(String micr) {
        if (micr == null || micr.length() < 10) {
            return "***";
        }
        return micr.substring(0, 4) + "***" + micr.substring(micr.length() - 4);
    }

    private String maskRouting(String routing) {
        if (routing == null || routing.length() != 9) {
            return "***";
        }
        return routing.substring(0, 2) + "***" + routing.substring(7);
    }

    // Data classes

    @lombok.Data
    @lombok.Builder
    public static class MICRData {
        private String routingNumber;
        private String accountNumber;
        private String checkNumber;
    }

    public static class MICRParseException extends Exception {
        public MICRParseException(String message) {
            super(message);
        }

        public MICRParseException(String message, Throwable cause) {
            super(message, cause);
        }
    }
}
