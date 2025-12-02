package com.waqiti.payment.api;

/**
 * Health response DTO
 */
@lombok.Data
@lombok.Builder
@lombok.NoArgsConstructor
@lombok.AllArgsConstructor
public class HealthResponse {
    private String status;
    private String service;
    private java.time.LocalDateTime timestamp;
}
