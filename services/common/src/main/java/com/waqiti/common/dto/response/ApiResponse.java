package com.waqiti.common.dto.response;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Getter;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;

import java.io.Serial;
import java.io.Serializable;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.*;

/**
 * Enterprise-grade API response wrapper implementing RFC 7807 Problem Details
 * 
 * This class provides a standardized response format with:
 * - Success/error status indication
 * - Detailed error information
 * - Request tracing
 * - Performance metrics
 * - HATEOAS links support
 * 
 * @param <T> The type of the response payload
 * 
 * @author Principal Software Engineer
 * @since 1.0.0
 */
@Getter
@Schema(description = "Standard API response wrapper")
@JsonInclude(JsonInclude.Include.NON_NULL)
public class ApiResponse<T> implements Serializable {
    
    @Serial
    private static final long serialVersionUID = 1L;
    
    @Schema(description = "Indicates if the request was successful", required = true)
    @JsonProperty("success")
    private final boolean success;
    
    @Schema(description = "HTTP status code", example = "200", required = true)
    @JsonProperty("status")
    private final int status;
    
    @Schema(description = "Human-readable message", example = "Request processed successfully")
    @JsonProperty("message")
    private final String message;
    
    @Schema(description = "Response payload")
    @JsonProperty("data")
    private final T data;
    
    @Schema(description = "Error details for failed requests")
    @JsonProperty("error")
    private final ErrorDetails error;
    
    @Schema(description = "Response metadata")
    @JsonProperty("meta")
    private final ResponseMetadata meta;
    
    @Schema(description = "HATEOAS links")
    @JsonProperty("links")
    private final Map<String, String> links;
    
    /**
     * Private constructor - use builder methods
     */
    private ApiResponse(Builder<T> builder) {
        this.success = builder.success;
        this.status = builder.status;
        this.message = builder.message;
        this.data = builder.data;
        this.error = builder.error;
        this.meta = builder.meta != null ? builder.meta : ResponseMetadata.create();
        this.links = builder.links != null ? Collections.unmodifiableMap(builder.links) : null;
    }
    
    /**
     * Error details for failed requests
     */
    @Getter
    @Schema(description = "Detailed error information")
    @JsonInclude(JsonInclude.Include.NON_NULL)
    public static class ErrorDetails implements Serializable {
        
        @Serial
        private static final long serialVersionUID = 1L;
        
        @Schema(description = "Error code for client handling", example = "VALIDATION_ERROR")
        private final String code;
        
        @Schema(description = "Detailed error description")
        private final String detail;
        
        @Schema(description = "Error type URI per RFC 7807")
        private final String type;
        
        @Schema(description = "Field-level validation errors")
        private final Map<String, List<String>> validationErrors;
        
        @Schema(description = "Stack trace (only in development)")
        private final List<String> stackTrace;
        
        private ErrorDetails(String code, String detail, String type, 
                            Map<String, List<String>> validationErrors, 
                            List<String> stackTrace) {
            this.code = code;
            this.detail = detail;
            this.type = type;
            this.validationErrors = validationErrors != null ? 
                Collections.unmodifiableMap(validationErrors) : null;
            this.stackTrace = stackTrace;
        }
        
        public static ErrorDetails of(String code, String detail) {
            return new ErrorDetails(code, detail, null, null, null);
        }
        
        public static ErrorDetails validation(Map<String, List<String>> errors) {
            return new ErrorDetails(
                "VALIDATION_ERROR",
                "Request validation failed",
                "/errors/validation",
                errors,
                null
            );
        }
        
        public static ErrorDetails exception(Exception e, boolean includeStackTrace) {
            List<String> trace = null;
            if (includeStackTrace) {
                trace = Arrays.stream(e.getStackTrace())
                    .limit(10)
                    .map(StackTraceElement::toString)
                    .toList();
            }
            
            return new ErrorDetails(
                e.getClass().getSimpleName(),
                e.getMessage(),
                "/errors/internal",
                null,
                trace
            );
        }
    }
    
    /**
     * Response metadata for tracking and monitoring
     */
    @Getter
    @Schema(description = "Response metadata")
    @JsonInclude(JsonInclude.Include.NON_NULL)
    public static class ResponseMetadata implements Serializable {
        
        @Serial
        private static final long serialVersionUID = 1L;
        
        @Schema(description = "Response timestamp (Unix epoch)", example = "1609459200000")
        private final long timestamp;
        
        @Schema(description = "Request processing time in milliseconds", example = "150")
        private final Long processingTime;
        
        @Schema(description = "Request tracking ID", example = "550e8400-e29b-41d4-a716-446655440000")
        private final String requestId;
        
        @Schema(description = "API version", example = "1.0.0")
        private final String version;
        
        @Schema(description = "Server that processed the request", example = "api-server-01")
        private final String server;
        
        @Schema(description = "Additional custom metadata")
        private final Map<String, Object> custom;
        
        private ResponseMetadata(long timestamp, Long processingTime, String requestId,
                                String version, String server, Map<String, Object> custom) {
            this.timestamp = timestamp;
            this.processingTime = processingTime;
            this.requestId = requestId;
            this.version = version;
            this.server = server;
            this.custom = custom != null ? Collections.unmodifiableMap(custom) : null;
        }
        
        public static ResponseMetadata create() {
            return new ResponseMetadata(
                LocalDateTime.now().toInstant(ZoneOffset.UTC).toEpochMilli(),
                null,
                UUID.randomUUID().toString(),
                "1.0.0",
                getServerName(),
                null
            );
        }
        
        public static ResponseMetadata withProcessingTime(long startTime) {
            return new ResponseMetadata(
                LocalDateTime.now().toInstant(ZoneOffset.UTC).toEpochMilli(),
                System.currentTimeMillis() - startTime,
                UUID.randomUUID().toString(),
                "1.0.0",
                getServerName(),
                null
            );
        }
        
        private static String getServerName() {
            try {
                return java.net.InetAddress.getLocalHost().getHostName();
            } catch (Exception e) {
                return "unknown";
            }
        }
    }
    
    /**
     * Success response factory methods
     */
    public static <T> ApiResponse<T> success(T data) {
        return ApiResponse.<T>builder()
            .success(true)
            .status(HttpStatus.OK.value())
            .message("Success")
            .data(data)
            .build();
    }
    
    public static <T> ApiResponse<T> success(T data, String message) {
        return ApiResponse.<T>builder()
            .success(true)
            .status(HttpStatus.OK.value())
            .message(message)
            .data(data)
            .build();
    }
    
    public static <T> ApiResponse<T> created(T data) {
        return ApiResponse.<T>builder()
            .success(true)
            .status(HttpStatus.CREATED.value())
            .message("Resource created successfully")
            .data(data)
            .build();
    }
    
    public static ApiResponse<Void> noContent() {
        return ApiResponse.<Void>builder()
            .success(true)
            .status(HttpStatus.NO_CONTENT.value())
            .message("Request processed successfully")
            .build();
    }
    
    /**
     * Error response factory methods
     */
    public static <T> ApiResponse<T> error(HttpStatus status, String message) {
        return ApiResponse.<T>builder()
            .success(false)
            .status(status.value())
            .message(message)
            .error(ErrorDetails.of(status.name(), message))
            .build();
    }
    
    public static <T> ApiResponse<T> error(HttpStatus status, String message, ErrorDetails error) {
        return ApiResponse.<T>builder()
            .success(false)
            .status(status.value())
            .message(message)
            .error(error)
            .build();
    }
    
    public static <T> ApiResponse<T> badRequest(String message) {
        return error(HttpStatus.BAD_REQUEST, message);
    }
    
    public static <T> ApiResponse<T> unauthorized(String message) {
        return error(HttpStatus.UNAUTHORIZED, message);
    }
    
    public static <T> ApiResponse<T> forbidden(String message) {
        return error(HttpStatus.FORBIDDEN, message);
    }
    
    public static <T> ApiResponse<T> notFound(String message) {
        return error(HttpStatus.NOT_FOUND, message);
    }
    
    public static <T> ApiResponse<T> conflict(String message) {
        return error(HttpStatus.CONFLICT, message);
    }
    
    public static <T> ApiResponse<T> internalError(String message) {
        return error(HttpStatus.INTERNAL_SERVER_ERROR, message);
    }
    
    public static <T> ApiResponse<T> validationError(Map<String, List<String>> errors) {
        return ApiResponse.<T>builder()
            .success(false)
            .status(HttpStatus.BAD_REQUEST.value())
            .message("Validation failed")
            .error(ErrorDetails.validation(errors))
            .build();
    }
    
    /**
     * Convert to ResponseEntity
     */
    public ResponseEntity<ApiResponse<T>> toResponseEntity() {
        return ResponseEntity.status(status).body(this);
    }
    
    /**
     * Builder for ApiResponse
     */
    public static <T> Builder<T> builder() {
        return new Builder<>();
    }
    
    public static class Builder<T> {
        private boolean success;
        private int status;
        private String message;
        private T data;
        private ErrorDetails error;
        private ResponseMetadata meta;
        private Map<String, String> links;
        
        public Builder<T> success(boolean success) {
            this.success = success;
            return this;
        }
        
        public Builder<T> status(int status) {
            this.status = status;
            return this;
        }
        
        public Builder<T> status(HttpStatus status) {
            this.status = status.value();
            return this;
        }
        
        public Builder<T> message(String message) {
            this.message = message;
            return this;
        }
        
        public Builder<T> data(T data) {
            this.data = data;
            return this;
        }
        
        public Builder<T> error(ErrorDetails error) {
            this.error = error;
            return this;
        }
        
        public Builder<T> meta(ResponseMetadata meta) {
            this.meta = meta;
            return this;
        }
        
        public Builder<T> withProcessingTime(long startTime) {
            this.meta = ResponseMetadata.withProcessingTime(startTime);
            return this;
        }
        
        public Builder<T> links(Map<String, String> links) {
            this.links = links;
            return this;
        }
        
        public Builder<T> addLink(String rel, String href) {
            if (this.links == null) {
                this.links = new HashMap<>();
            }
            this.links.put(rel, href);
            return this;
        }
        
        public ApiResponse<T> build() {
            if (status == 0) {
                status = success ? 200 : 500;
            }
            if (message == null) {
                message = success ? "Success" : "Error";
            }
            return new ApiResponse<>(this);
        }
    }
}