package com.waqiti.payment.exception;

import lombok.Getter;

import java.util.List;
import java.util.ArrayList;

/**
 * Specialized exception for check image processing errors
 * Provides detailed error information for troubleshooting and user feedback
 */
@Getter
public class CheckImageProcessingException extends RuntimeException {

    private final String errorCode;
    private final List<String> details;
    private final ProcessingStage failedStage;
    private final boolean retryable;

    public CheckImageProcessingException(String message) {
        this(message, ErrorCode.GENERAL_PROCESSING_ERROR, ProcessingStage.UNKNOWN, false);
    }

    public CheckImageProcessingException(String message, Throwable cause) {
        this(message, cause, ErrorCode.GENERAL_PROCESSING_ERROR, ProcessingStage.UNKNOWN, false);
    }

    public CheckImageProcessingException(String message, String errorCode, ProcessingStage stage, boolean retryable) {
        super(message);
        this.errorCode = errorCode;
        this.failedStage = stage;
        this.retryable = retryable;
        this.details = new ArrayList<>();
    }

    public CheckImageProcessingException(String message, Throwable cause, String errorCode, ProcessingStage stage, boolean retryable) {
        super(message, cause);
        this.errorCode = errorCode;
        this.failedStage = stage;
        this.retryable = retryable;
        this.details = new ArrayList<>();
    }

    /**
     * Creates an exception for image quality issues
     */
    public static CheckImageProcessingException imageQualityError(String message) {
        return new CheckImageProcessingException(
                message,
                ErrorCode.IMAGE_QUALITY_INSUFFICIENT,
                ProcessingStage.IMAGE_QUALITY_CHECK,
                false
        );
    }

    /**
     * Creates an exception for OCR processing failures
     */
    public static CheckImageProcessingException ocrError(String message, Throwable cause) {
        return new CheckImageProcessingException(
                message,
                cause,
                ErrorCode.OCR_PROCESSING_FAILED,
                ProcessingStage.TEXT_EXTRACTION,
                true
        );
    }

    /**
     * Creates an exception for data validation failures
     */
    public static CheckImageProcessingException validationError(String message) {
        return new CheckImageProcessingException(
                message,
                ErrorCode.DATA_VALIDATION_FAILED,
                ProcessingStage.DATA_VALIDATION,
                false
        );
    }

    /**
     * Creates an exception for fraud detection
     */
    public static CheckImageProcessingException fraudDetected(String message) {
        return new CheckImageProcessingException(
                message,
                ErrorCode.FRAUD_DETECTED,
                ProcessingStage.FRAUD_DETECTION,
                false
        );
    }

    /**
     * Creates an exception for storage failures
     */
    public static CheckImageProcessingException storageError(String message, Throwable cause) {
        return new CheckImageProcessingException(
                message,
                cause,
                ErrorCode.IMAGE_STORAGE_FAILED,
                ProcessingStage.IMAGE_STORAGE,
                true
        );
    }

    /**
     * Creates an exception for amount extraction failures
     */
    public static CheckImageProcessingException amountExtractionError(String message) {
        return new CheckImageProcessingException(
                message,
                ErrorCode.AMOUNT_EXTRACTION_FAILED,
                ProcessingStage.AMOUNT_EXTRACTION,
                true
        );
    }

    /**
     * Creates an exception for MICR line processing failures
     */
    public static CheckImageProcessingException micrProcessingError(String message) {
        return new CheckImageProcessingException(
                message,
                ErrorCode.MICR_PROCESSING_FAILED,
                ProcessingStage.MICR_PROCESSING,
                true
        );
    }

    /**
     * Adds additional detail to the exception
     */
    public CheckImageProcessingException addDetail(String detail) {
        this.details.add(detail);
        return this;
    }

    /**
     * Gets a user-friendly error message
     */
    public String getUserFriendlyMessage() {
        return switch (errorCode) {
            case ErrorCode.IMAGE_QUALITY_INSUFFICIENT ->
                    "The image quality is too poor to process. Please retake the photo with better lighting and focus.";
            case ErrorCode.OCR_PROCESSING_FAILED ->
                    "Unable to read the text on the check. Please ensure the check is clearly visible and try again.";
            case ErrorCode.DATA_VALIDATION_FAILED ->
                    "The information on the check appears to be invalid. Please verify the check details.";
            case ErrorCode.FRAUD_DETECTED ->
                    "This check has been flagged for potential fraud. Please contact customer support.";
            case ErrorCode.AMOUNT_EXTRACTION_FAILED ->
                    "Unable to determine the check amount. Please enter the amount manually.";
            case ErrorCode.MICR_PROCESSING_FAILED ->
                    "Unable to read the bank routing information. Please verify the check is not damaged.";
            case ErrorCode.IMAGE_STORAGE_FAILED ->
                    "Failed to save the check images. Please try again.";
            default ->
                    "An error occurred while processing the check. Please try again or contact support.";
        };
    }

    /**
     * Gets technical details for logging and debugging
     */
    public String getTechnicalDetails() {
        StringBuilder sb = new StringBuilder();
        sb.append("Error Code: ").append(errorCode).append("\n");
        sb.append("Failed Stage: ").append(failedStage).append("\n");
        sb.append("Retryable: ").append(retryable).append("\n");
        sb.append("Message: ").append(getMessage()).append("\n");

        if (!details.isEmpty()) {
            sb.append("Additional Details:\n");
            details.forEach(detail -> sb.append("  - ").append(detail).append("\n"));
        }

        if (getCause() != null) {
            sb.append("Cause: ").append(getCause().getMessage()).append("\n");
        }

        return sb.toString();
    }

    /**
     * Error codes for different types of processing failures
     */
    public static final class ErrorCode {
        public static final String GENERAL_PROCESSING_ERROR = "PROC_001";
        public static final String IMAGE_QUALITY_INSUFFICIENT = "IMG_001";
        public static final String OCR_PROCESSING_FAILED = "OCR_001";
        public static final String DATA_VALIDATION_FAILED = "VAL_001";
        public static final String FRAUD_DETECTED = "FRD_001";
        public static final String AMOUNT_EXTRACTION_FAILED = "AMT_001";
        public static final String MICR_PROCESSING_FAILED = "MCR_001";
        public static final String IMAGE_STORAGE_FAILED = "STR_001";

        private ErrorCode() {
            // Utility class
        }
    }

    /**
     * Processing stages where failures can occur
     */
    public enum ProcessingStage {
        UNKNOWN("Unknown stage"),
        IMAGE_QUALITY_CHECK("Image quality validation"),
        TEXT_EXTRACTION("Text extraction via OCR"),
        AMOUNT_EXTRACTION("Amount extraction and validation"),
        MICR_PROCESSING("MICR line processing"),
        DATA_VALIDATION("Extracted data validation"),
        FRAUD_DETECTION("Fraud detection analysis"),
        IMAGE_STORAGE("Image storage and archival"),
        FINAL_VALIDATION("Final validation and consistency checks");

        private final String description;

        ProcessingStage(String description) {
            this.description = description;
        }

        public String getDescription() {
            return description;
        }
    }

    /**
     * Creates a comprehensive error report
     */
    public ErrorReport toErrorReport() {
        return ErrorReport.builder()
                .errorCode(errorCode)
                .stage(failedStage)
                .message(getMessage())
                .userFriendlyMessage(getUserFriendlyMessage())
                .retryable(retryable)
                .details(new ArrayList<>(details))
                .timestamp(java.time.LocalDateTime.now())
                .build();
    }

    /**
     * Error report for structured error information
     */
    @Getter
    public static class ErrorReport {
        private final String errorCode;
        private final ProcessingStage stage;
        private final String message;
        private final String userFriendlyMessage;
        private final boolean retryable;
        private final List<String> details;
        private final java.time.LocalDateTime timestamp;

        private ErrorReport(Builder builder) {
            this.errorCode = builder.errorCode;
            this.stage = builder.stage;
            this.message = builder.message;
            this.userFriendlyMessage = builder.userFriendlyMessage;
            this.retryable = builder.retryable;
            this.details = builder.details != null ? new ArrayList<>(builder.details) : new ArrayList<>();
            this.timestamp = builder.timestamp;
        }

        public static Builder builder() {
            return new Builder();
        }

        public static class Builder {
            private String errorCode;
            private ProcessingStage stage;
            private String message;
            private String userFriendlyMessage;
            private boolean retryable;
            private List<String> details;
            private java.time.LocalDateTime timestamp;

            public Builder errorCode(String errorCode) {
                this.errorCode = errorCode;
                return this;
            }

            public Builder stage(ProcessingStage stage) {
                this.stage = stage;
                return this;
            }

            public Builder message(String message) {
                this.message = message;
                return this;
            }

            public Builder userFriendlyMessage(String userFriendlyMessage) {
                this.userFriendlyMessage = userFriendlyMessage;
                return this;
            }

            public Builder retryable(boolean retryable) {
                this.retryable = retryable;
                return this;
            }

            public Builder details(List<String> details) {
                this.details = details;
                return this;
            }

            public Builder timestamp(java.time.LocalDateTime timestamp) {
                this.timestamp = timestamp;
                return this;
            }

            public ErrorReport build() {
                return new ErrorReport(this);
            }
        }
    }
}