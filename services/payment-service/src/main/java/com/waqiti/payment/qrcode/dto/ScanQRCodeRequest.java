package com.waqiti.payment.qrcode.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import java.math.BigDecimal;
import java.util.Map;

/**
 * Request DTO for scanning a QR code
 * Contains scanner information and device context
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Schema(description = "Request for scanning and validating a QR code")
public class ScanQRCodeRequest {
    
    @NotNull(message = "Scanner user ID is required")
    @Schema(description = "ID of the user scanning the QR code", required = true, example = "usr_654321")
    private String scannerUserId;
    
    @NotBlank(message = "QR code data is required")
    @Size(max = 5000, message = "QR code data too large")
    @Schema(description = "Raw QR code data or deep link", required = true)
    private String qrCodeData;
    
    @Schema(description = "Type of scan source", example = "CAMERA")
    private ScanSource scanSource;
    
    @Schema(description = "Device information of the scanner")
    private DeviceInfo deviceInfo;
    
    @Schema(description = "GPS location where QR was scanned")
    private LocationInfo location;
    
    @Schema(description = "Amount to pay (for dynamic QR codes)", example = "25.50")
    private BigDecimal proposedAmount;
    
    @Schema(description = "Tip amount to add", example = "5.00")
    private BigDecimal tipAmount;
    
    @Schema(description = "Payment method ID to use", example = "pm_123456")
    private String paymentMethodId;
    
    @Schema(description = "Note or message for the payment", example = "Thanks for the coffee!")
    @Size(max = 255)
    private String paymentNote;
    
    @Schema(description = "Session ID for tracking", example = "sess_abc123")
    private String sessionId;
    
    @Schema(description = "Additional scan metadata")
    private Map<String, String> metadata;
    
    @Schema(description = "Request payment confirmation UI", example = "true")
    private Boolean requestConfirmation;
    
    @Schema(description = "Enable split bill for this payment", example = "false")
    private Boolean enableSplitBill;
    
    @Schema(description = "Number of people to split with", example = "4")
    private Integer splitCount;
    
    /**
     * Source of QR code scan
     */
    public enum ScanSource {
        CAMERA,           // Scanned via camera
        GALLERY,          // Loaded from image gallery
        DEEP_LINK,        // Opened via deep link
        NFC,             // Read via NFC tag
        MANUAL_ENTRY,    // Manually entered code
        BLUETOOTH        // Received via Bluetooth
    }
    
    /**
     * Device information for security and analytics
     */
    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    @Schema(description = "Device information of the scanner")
    public static class DeviceInfo {
        
        @Schema(description = "Device unique identifier", example = "device_123456")
        private String deviceId;
        
        @Schema(description = "Device type", example = "iPhone 14 Pro")
        private String deviceType;
        
        @Schema(description = "Operating system", example = "iOS")
        private String platform;
        
        @Schema(description = "OS version", example = "16.5")
        private String osVersion;
        
        @Schema(description = "App version", example = "2.5.0")
        private String appVersion;
        
        @Schema(description = "Device manufacturer", example = "Apple")
        private String manufacturer;
        
        @Schema(description = "Device model", example = "iPhone14,3")
        private String model;
        
        @Pattern(regexp = "^[0-9]{1,3}\\.[0-9]{1,3}\\.[0-9]{1,3}\\.[0-9]{1,3}$")
        @Schema(description = "IP address", example = "192.168.1.100")
        private String ipAddress;
        
        @Schema(description = "Network type", example = "WIFI")
        private String networkType;
        
        @Schema(description = "Biometric capability available", example = "true")
        private Boolean biometricCapable;
        
        @Schema(description = "NFC capability available", example = "true")
        private Boolean nfcCapable;
        
        @Schema(description = "Device fingerprint for fraud detection")
        private String deviceFingerprint;
    }
    
    /**
     * Location information for geo-based validation
     */
    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    @Schema(description = "GPS location information")
    public static class LocationInfo {
        
        @Schema(description = "Latitude", example = "37.7749")
        private Double latitude;
        
        @Schema(description = "Longitude", example = "-122.4194")
        private Double longitude;
        
        @Schema(description = "Location accuracy in meters", example = "10.5")
        private Double accuracy;
        
        @Schema(description = "Altitude in meters", example = "50.0")
        private Double altitude;
        
        @Schema(description = "Address if available", example = "123 Main St, San Francisco, CA")
        private String address;
        
        @Schema(description = "City", example = "San Francisco")
        private String city;
        
        @Schema(description = "State or province", example = "CA")
        private String state;
        
        @Schema(description = "Country code", example = "US")
        private String countryCode;
        
        @Schema(description = "Postal code", example = "94102")
        private String postalCode;
        
        @Schema(description = "Location timestamp")
        private Long timestamp;
        
        @Schema(description = "Location provider", example = "GPS")
        private String provider;
    }
}