package com.waqiti.payment.checkdeposit.exception;

import lombok.Getter;
import org.springframework.http.HttpStatus;

/**
 * Exception thrown when image storage operations fail
 */
@Getter
public class ImageStorageException extends RuntimeException {

    private final String errorCode;
    private final HttpStatus httpStatus;

    public ImageStorageException(String message) {
        super(message);
        this.errorCode = "IMAGE_STORAGE_ERROR";
        this.httpStatus = HttpStatus.INTERNAL_SERVER_ERROR;
    }

    public ImageStorageException(String message, Throwable cause) {
        super(message, cause);
        this.errorCode = "IMAGE_STORAGE_ERROR";
        this.httpStatus = HttpStatus.INTERNAL_SERVER_ERROR;
    }

    public ImageStorageException(String message, String errorCode) {
        super(message);
        this.errorCode = errorCode;
        this.httpStatus = HttpStatus.INTERNAL_SERVER_ERROR;
    }

    public ImageStorageException(String message, String errorCode, HttpStatus httpStatus) {
        super(message);
        this.errorCode = errorCode;
        this.httpStatus = httpStatus;
    }

    public ImageStorageException(String message, String errorCode, Throwable cause) {
        super(message, cause);
        this.errorCode = errorCode;
        this.httpStatus = HttpStatus.INTERNAL_SERVER_ERROR;
    }

    public ImageStorageException(String message, String errorCode, HttpStatus httpStatus, Throwable cause) {
        super(message, cause);
        this.errorCode = errorCode;
        this.httpStatus = httpStatus;
    }
}
