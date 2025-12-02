/**
 * SMS/USSD Banking Controller
 * REST endpoints for SMS and USSD banking operations
 */
package com.waqiti.smsbanking.controller;

import com.waqiti.smsbanking.service.SmsCommandService;
import com.waqiti.smsbanking.service.UssdMenuService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1/sms-banking")
@RequiredArgsConstructor
@Slf4j
@Tag(name = "SMS/USSD Banking", description = "SMS and USSD banking operations")
public class SmsUssdController {
    
    private final SmsCommandService smsCommandService;
    private final UssdMenuService ussdMenuService;
    
    @PostMapping("/sms/receive")
    @Operation(summary = "Process incoming SMS", description = "Process SMS banking commands with mandatory 2FA verification")
    @ApiResponse(responseCode = "200", description = "SMS processed successfully")
    @ApiResponse(responseCode = "401", description = "2FA verification required")
    @ApiResponse(responseCode = "403", description = "2FA verification failed")
    @PreAuthorize("hasRole('SMS_GATEWAY') or hasRole('SYSTEM')")
    public ResponseEntity<SmsCommandService.SmsResponse> processSms(
            @Parameter(description = "Phone number") @RequestParam String phoneNumber,
            @Parameter(description = "SMS message") @RequestParam String message,
            @Parameter(description = "Gateway reference") @RequestParam(required = false) String gatewayRef,
            @Parameter(description = "2FA verification code") @RequestParam(required = false) String verificationCode) {
        
        log.info("Processing SMS from {} with 2FA verification", phoneNumber);
        
        // Enhanced SMS processing with mandatory 2FA verification
        SmsCommandService.SmsResponse response = smsCommandService.processSmsCommandWithMfa(
            phoneNumber, message, gatewayRef, verificationCode);
        
        return ResponseEntity.ok(response);
    }
    
    @PostMapping("/ussd/session")
    @Operation(summary = "Process USSD session", description = "Handle USSD menu interactions with step-up authentication")
    @ApiResponse(responseCode = "200", description = "USSD session processed successfully")
    @ApiResponse(responseCode = "401", description = "Step-up authentication required")
    @PreAuthorize("hasRole('USSD_GATEWAY') or hasRole('SYSTEM')")
    public ResponseEntity<UssdMenuService.UssdResponse> processUssd(
            @Parameter(description = "USSD session ID") @RequestParam String sessionId,
            @Parameter(description = "Phone number") @RequestParam String phoneNumber,
            @Parameter(description = "User input") @RequestParam String input,
            @Parameter(description = "Gateway reference") @RequestParam(required = false) String gatewayRef,
            @Parameter(description = "2FA code for high-value operations") @RequestParam(required = false) String mfaCode) {
        
        log.info("Processing USSD session {} from {} with step-up auth", sessionId, phoneNumber);
        
        UssdMenuService.UssdResponse response = ussdMenuService.processUssdRequestWithMfa(
            sessionId, phoneNumber, input, gatewayRef, mfaCode);
        
        return ResponseEntity.ok(response);
    }
    
    @GetMapping("/help")
    @Operation(summary = "Get SMS banking help", description = "Get list of available SMS commands")
    @ApiResponse(responseCode = "200", description = "Help information retrieved successfully")
    public ResponseEntity<String> getSmsHelp() {
        String helpText = "Waqiti SMS Banking Commands (Enhanced Security):\n\n" +
            "🔒 2FA REQUIRED FOR ALL TRANSACTIONS:\n" +
            "BAL <pin> <2fa> - Check account balance\n" +
            "SEND <phone> <amount> <pin> <2fa> - Transfer money\n" +
            "AIRTIME <phone> <amount> <pin> <2fa> - Purchase airtime\n" +
            "LOAN STATUS <pin> <2fa> - Check loan status\n" +
            "LOAN PAY <amount> <pin> <2fa> - Make loan payment\n" +
            "STMT <days> <pin> <2fa> - Get mini statement\n\n" +
            "🛡️ SECURITY COMMANDS:\n" +
            "2FA SETUP - Enable two-factor authentication\n" +
            "2FA STATUS - Check 2FA status\n" +
            "2FA CODE - Request new verification code\n" +
            "HELP - Show available commands\n\n" +
            "📱 Examples with 2FA:\n" +
            "BAL 1234 567890\n" +
            "SEND +1234567890 50.00 1234 567890\n" +
            "AIRTIME +1234567890 20 1234 567890\n\n" +
            "⚡ For USSD: Dial *123# (2FA required for transactions)\n\n" +
            "🔐 IMPORTANT: 2FA codes expire in 5 minutes. Request new code with '2FA CODE'";
        
        return ResponseEntity.ok(helpText);
    }
    
    @PostMapping("/webhook/delivery-report")
    @Operation(summary = "SMS delivery report", description = "Receive SMS delivery status")
    @ApiResponse(responseCode = "200", description = "Delivery report processed")
    public ResponseEntity<Void> deliveryReport(
            @Parameter(description = "Message ID") @RequestParam String messageId,
            @Parameter(description = "Phone number") @RequestParam String phoneNumber,
            @Parameter(description = "Delivery status") @RequestParam String status,
            @Parameter(description = "Timestamp") @RequestParam(required = false) String timestamp) {
        
        log.info("SMS delivery report - ID: {}, Phone: {}, Status: {}", messageId, phoneNumber, status);
        
        // Store delivery status for monitoring and analytics
        storeDeliveryStatus(messageId, phoneNumber, status, timestamp);
        
        return ResponseEntity.ok().build();
    }
    
    /**
     * Store SMS delivery status for monitoring and analytics
     */
    private void storeDeliveryStatus(String messageId, String phoneNumber, String status, String timestamp) {
        try {
            // Parse delivery status
            SmsDeliveryStatus deliveryStatus = mapDeliveryStatus(status);
            LocalDateTime deliveredAt = parseTimestamp(timestamp);
            
            // Create delivery report record
            SmsDeliveryReport report = SmsDeliveryReport.builder()
                .messageId(messageId)
                .phoneNumber(phoneNumber)
                .status(deliveryStatus)
                .deliveredAt(deliveredAt)
                .reportedAt(LocalDateTime.now())
                .build();
            
            // Save to database
            smsDeliveryReportRepository.save(report);
            
            // Update message status if it exists
            updateMessageStatus(messageId, deliveryStatus, deliveredAt);
            
            // Send analytics event
            publishDeliveryAnalytics(report);
            
            // Handle failed deliveries
            if (deliveryStatus == SmsDeliveryStatus.FAILED || 
                deliveryStatus == SmsDeliveryStatus.REJECTED) {
                handleFailedDelivery(messageId, phoneNumber, status);
            }
            
            log.debug("Stored delivery status for message {} to {}: {}", 
                messageId, phoneNumber, deliveryStatus);
                
        } catch (Exception e) {
            log.error("Failed to store delivery status for message {}: {}", messageId, e.getMessage(), e);
        }
    }
    
    private SmsDeliveryStatus mapDeliveryStatus(String status) {
        if (status == null) return SmsDeliveryStatus.UNKNOWN;
        
        switch (status.toLowerCase()) {
            case "delivered":
            case "dlvrd":
            case "success":
                return SmsDeliveryStatus.DELIVERED;
                
            case "failed":
            case "undeliv":
            case "fail":
                return SmsDeliveryStatus.FAILED;
                
            case "rejected":
            case "rejectd":
                return SmsDeliveryStatus.REJECTED;
                
            case "expired":
            case "expired_":
                return SmsDeliveryStatus.EXPIRED;
                
            case "pending":
            case "bufferd":
                return SmsDeliveryStatus.PENDING;
                
            case "accepted":
            case "acceptd":
                return SmsDeliveryStatus.ACCEPTED;
                
            default:
                log.warn("Unknown SMS delivery status: {}", status);
                return SmsDeliveryStatus.UNKNOWN;
        }
    }
    
    private LocalDateTime parseTimestamp(String timestamp) {
        if (timestamp == null || timestamp.isEmpty()) {
            return LocalDateTime.now();
        }
        
        try {
            // Try different timestamp formats
            if (timestamp.matches("\\d{10}")) {
                // Unix timestamp (seconds)
                return LocalDateTime.ofInstant(
                    Instant.ofEpochSecond(Long.parseLong(timestamp)), 
                    ZoneId.systemDefault()
                );
            } else if (timestamp.matches("\\d{13}")) {
                // Unix timestamp (milliseconds)
                return LocalDateTime.ofInstant(
                    Instant.ofEpochMilli(Long.parseLong(timestamp)), 
                    ZoneId.systemDefault()
                );
            } else {
                // Try ISO format
                return LocalDateTime.parse(timestamp.replace("Z", ""));
            }
        } catch (Exception e) {
            log.warn("Failed to parse timestamp '{}': {}", timestamp, e.getMessage());
            return LocalDateTime.now();
        }
    }
    
    private void updateMessageStatus(String messageId, SmsDeliveryStatus status, LocalDateTime deliveredAt) {
        try {
            Optional<SmsMessage> messageOpt = smsMessageRepository.findByMessageId(messageId);
            if (messageOpt.isPresent()) {
                SmsMessage message = messageOpt.get();
                message.setDeliveryStatus(status);
                message.setDeliveredAt(deliveredAt);
                message.setUpdatedAt(LocalDateTime.now());
                
                // Update delivery attempts
                if (status == SmsDeliveryStatus.FAILED) {
                    message.incrementDeliveryAttempts();
                    
                    // Schedule retry if under max attempts
                    if (message.getDeliveryAttempts() < MAX_DELIVERY_ATTEMPTS) {
                        scheduleMessageRetry(message);
                    }
                }
                
                smsMessageRepository.save(message);
                log.debug("Updated message {} status to {}", messageId, status);
            }
        } catch (Exception e) {
            log.error("Failed to update message status for {}", messageId, e);
        }
    }
    
    private void publishDeliveryAnalytics(SmsDeliveryReport report) {
        try {
            // Create analytics event
            SmsAnalyticsEvent analyticsEvent = SmsAnalyticsEvent.builder()
                .eventType("SMS_DELIVERY_REPORT")
                .messageId(report.getMessageId())
                .phoneNumber(hashPhoneNumber(report.getPhoneNumber()))
                .deliveryStatus(report.getStatus().toString())
                .timestamp(report.getReportedAt())
                .metadata(Map.of(
                    "delivery_latency_ms", calculateDeliveryLatency(report),
                    "carrier", detectCarrier(report.getPhoneNumber()),
                    "country_code", extractCountryCode(report.getPhoneNumber())
                ))
                .build();
            
            // Publish to analytics pipeline
            kafkaTemplate.send("sms-analytics", analyticsEvent);
            
            // Update delivery metrics
            updateDeliveryMetrics(report.getStatus());
            
        } catch (Exception e) {
            log.error("Failed to publish delivery analytics", e);
        }
    }
    
    private void handleFailedDelivery(String messageId, String phoneNumber, String status) {
        try {
            log.warn("SMS delivery failed - ID: {}, Phone: {}, Status: {}", messageId, phoneNumber, status);
            
            // Create failure notification
            SmsFailureNotification notification = SmsFailureNotification.builder()
                .messageId(messageId)
                .phoneNumber(phoneNumber)
                .failureReason(status)
                .timestamp(LocalDateTime.now())
                .build();
            
            // Send to dead letter queue for manual review if needed
            kafkaTemplate.send("sms-failures", notification);
            
            // Check if this phone number has high failure rate
            checkPhoneNumberFailureRate(phoneNumber);
            
        } catch (Exception e) {
            log.error("Failed to handle SMS delivery failure", e);
        }
    }
    
    private long calculateDeliveryLatency(SmsDeliveryReport report) {
        try {
            Optional<SmsMessage> messageOpt = smsMessageRepository.findByMessageId(report.getMessageId());
            if (messageOpt.isPresent()) {
                SmsMessage message = messageOpt.get();
                return Duration.between(message.getCreatedAt(), report.getDeliveredAt()).toMillis();
            }
        } catch (Exception e) {
            log.debug("Could not calculate delivery latency", e);
        }
        return 0;
    }
    
    private String hashPhoneNumber(String phoneNumber) {
        // Hash phone number for privacy in analytics
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] hash = digest.digest(phoneNumber.getBytes());
            return Base64.getEncoder().encodeToString(hash).substring(0, 16);
        } catch (Exception e) {
            return "unknown";
        }
    }
    
    private String detectCarrier(String phoneNumber) {
        // Simple carrier detection based on number patterns
        if (phoneNumber.startsWith("+1")) {
            // US numbers - simplified detection
            String prefix = phoneNumber.substring(2, 5);
            switch (prefix) {
                case "310": case "424": case "213": return "Verizon";
                case "323": case "818": case "626": return "AT&T";
                case "747": case "714": return "T-Mobile";
                default: return "Unknown";
            }
        }
        return "International";
    }
    
    private String extractCountryCode(String phoneNumber) {
        if (phoneNumber.startsWith("+1")) return "US";
        if (phoneNumber.startsWith("+44")) return "GB";
        if (phoneNumber.startsWith("+33")) return "FR";
        if (phoneNumber.startsWith("+49")) return "DE";
        if (phoneNumber.startsWith("+234")) return "NG";
        if (phoneNumber.startsWith("+254")) return "KE";
        return "Unknown";
    }
    
    private void updateDeliveryMetrics(SmsDeliveryStatus status) {
        try {
            String metricName = "sms.delivery." + status.toString().toLowerCase();
            meterRegistry.counter(metricName).increment();
            
            // Update success rate
            if (status == SmsDeliveryStatus.DELIVERED) {
                meterRegistry.counter("sms.delivery.success").increment();
            } else {
                meterRegistry.counter("sms.delivery.failure").increment();
            }
        } catch (Exception e) {
            log.debug("Failed to update delivery metrics", e);
        }
    }
    
    private void scheduleMessageRetry(SmsMessage message) {
        // Schedule retry with exponential backoff
        long delayMinutes = (long) Math.pow(2, message.getDeliveryAttempts()) * 5; // 5, 10, 20, 40 minutes
        LocalDateTime retryAt = LocalDateTime.now().plusMinutes(delayMinutes);
        
        SmsRetryTask retryTask = SmsRetryTask.builder()
            .messageId(message.getMessageId())
            .phoneNumber(message.getPhoneNumber())
            .content(message.getContent())
            .retryAt(retryAt)
            .attempt(message.getDeliveryAttempts() + 1)
            .build();
        
        kafkaTemplate.send("sms-retry-queue", retryTask);
        log.info("Scheduled retry for message {} at {}", message.getMessageId(), retryAt);
    }
    
    private void checkPhoneNumberFailureRate(String phoneNumber) {
        try {
            // Check failure rate in last 24 hours
            LocalDateTime since = LocalDateTime.now().minusDays(1);
            List<SmsDeliveryReport> reports = smsDeliveryReportRepository
                .findByPhoneNumberAndReportedAtAfter(phoneNumber, since);
            
            long totalMessages = reports.size();
            long failures = reports.stream()
                .mapToLong(r -> r.getStatus() == SmsDeliveryStatus.FAILED || 
                              r.getStatus() == SmsDeliveryStatus.REJECTED ? 1 : 0)
                .sum();
            
            if (totalMessages > 5 && (double) failures / totalMessages > 0.8) {
                // High failure rate - flag for investigation
                PhoneNumberAlert alert = PhoneNumberAlert.builder()
                    .phoneNumber(phoneNumber)
                    .alertType("HIGH_FAILURE_RATE")
                    .totalMessages((int) totalMessages)
                    .failedMessages((int) failures)
                    .failureRate((double) failures / totalMessages)
                    .alertedAt(LocalDateTime.now())
                    .build();
                
                kafkaTemplate.send("phone-number-alerts", alert);
                log.warn("High SMS failure rate detected for {}: {}/{} failed", 
                    phoneNumber, failures, totalMessages);
            }
        } catch (Exception e) {
            log.error("Failed to check phone number failure rate", e);
        }
    }
    
    // Constants
    private static final int MAX_DELIVERY_ATTEMPTS = 3;
    
    // Enum definitions
    public enum SmsDeliveryStatus {
        PENDING, ACCEPTED, DELIVERED, FAILED, REJECTED, EXPIRED, UNKNOWN
    }
}