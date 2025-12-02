package com.waqiti.dispute.security;

import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.Getter;

/**
 * ClamAV Scan Result
 *
 * Represents the outcome of a virus scan operation.
 *
 * @author Waqiti Security Team
 * @version 1.0.0
 */
@Getter
@AllArgsConstructor(access = AccessLevel.PRIVATE)
public class ClamAVScanResult {

    private final ScanStatus status;
    private final String virusName;
    private final String errorMessage;

    public enum ScanStatus {
        CLEAN,      // No virus detected
        INFECTED,   // Virus detected
        ERROR       // Scan error occurred
    }

    /**
     * Creates a result indicating a clean file
     */
    public static ClamAVScanResult clean() {
        return new ClamAVScanResult(ScanStatus.CLEAN, null, null);
    }

    /**
     * Creates a result indicating an infected file
     *
     * @param virusName The name of the detected virus
     */
    public static ClamAVScanResult infected(String virusName) {
        return new ClamAVScanResult(ScanStatus.INFECTED, virusName, null);
    }

    /**
     * Creates a result indicating a scan error
     *
     * @param errorMessage The error message
     */
    public static ClamAVScanResult error(String errorMessage) {
        return new ClamAVScanResult(ScanStatus.ERROR, null, errorMessage);
    }

    /**
     * @return true if the file is infected
     */
    public boolean isInfected() {
        return status == ScanStatus.INFECTED;
    }

    /**
     * @return true if the file is clean
     */
    public boolean isClean() {
        return status == ScanStatus.CLEAN;
    }

    /**
     * @return true if an error occurred during scanning
     */
    public boolean isError() {
        return status == ScanStatus.ERROR;
    }

    @Override
    public String toString() {
        return switch (status) {
            case CLEAN -> "ClamAVScanResult[CLEAN]";
            case INFECTED -> String.format("ClamAVScanResult[INFECTED: %s]", virusName);
            case ERROR -> String.format("ClamAVScanResult[ERROR: %s]", errorMessage);
        };
    }
}
