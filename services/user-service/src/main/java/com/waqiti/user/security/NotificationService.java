package com.waqiti.user.security;

import com.waqiti.user.domain.User;
import com.waqiti.user.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.mail.javamail.MimeMessageHelper;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;

import jakarta.mail.internet.MimeMessage;
import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * Production-grade security notification service with multiple channels.
 * Implements comprehensive alerting for security events with fallback mechanisms.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class NotificationService {
    
    private final UserRepository userRepository;
    private final JavaMailSender mailSender;
    private final KafkaTemplate<String, Object> kafkaTemplate;
    private final RestTemplate restTemplate;
    private final ExecutorService notificationExecutor = Executors.newFixedThreadPool(5);
    
    @Value("${notification.email.from:security@example.com}")
    private String fromEmail;
    
    @Value("${notification.email.enabled:true}")
    private boolean emailEnabled;
    
    @Value("${notification.sms.enabled:true}")
    private boolean smsEnabled;
    
    @Value("${notification.push.enabled:true}")
    private boolean pushEnabled;
    
    @Value("${notification.webhook.enabled:true}")
    private boolean webhookEnabled;
    
    @Value("${notification.sms.service.url:}")
    private String smsServiceUrl;
    
    @Value("${notification.sms.api.key:}")
    private String smsApiKey;
    
    @Value("${notification.push.service.url:}")
    private String pushServiceUrl;
    
    @Value("${notification.webhook.security.url:}")
    private String securityWebhookUrl;
    
    @Value("${notification.admin.emails:admin@example.com}")
    private String adminEmails;
    
    @Value("${app.name:Waqiti}")
    private String appName;
    
    @Value("${app.support.url:https://support.example.com}")
    private String supportUrl;
    
    @Value("${app.security.url:https://example.com/security}")
    private String securityUrl;
    
    public void notifySuspiciousActivity(String userId, String sourceIp, String reason) {
        log.warn("SECURITY_NOTIFICATION: Suspicious activity detected for user {} from IP {}: {}",
                userId, sourceIp, reason);
        
        CompletableFuture.runAsync(() -> {
            try {
                Optional<User> userOpt = userRepository.findById(userId);
                if (userOpt.isEmpty()) {
                    log.error("User not found for suspicious activity notification: {}", userId);
                    return;
                }
                
                User user = userOpt.get();
                String timestamp = formatTimestamp(Instant.now());
                
                // Send multi-channel notifications
                sendSuspiciousActivityEmail(user, sourceIp, reason, timestamp);
                sendSuspiciousActivitySms(user, sourceIp, reason, timestamp);
                sendSuspiciousActivityPush(user, sourceIp, reason, timestamp);
                
                // Log to security event stream
                publishSecurityEvent(SecurityEventType.SUSPICIOUS_ACTIVITY, userId, Map.of(
                    "sourceIp", sourceIp,
                    "reason", reason,
                    "timestamp", timestamp,
                    "severity", "HIGH"
                ));
                
            } catch (Exception e) {
                log.error("Failed to send suspicious activity notification for user: {}", userId, e);
            }
        }, notificationExecutor);
    }
    
    public void notifyTemporaryLockout(String userId, int durationMinutes) {
        log.warn("SECURITY_NOTIFICATION: Temporary lockout for user {} - duration: {} minutes",
                userId, durationMinutes);
        
        CompletableFuture.runAsync(() -> {
            try {
                Optional<User> userOpt = userRepository.findById(userId);
                if (userOpt.isEmpty()) {
                    log.error("User not found for lockout notification: {}", userId);
                    return;
                }
                
                User user = userOpt.get();
                String unlockTime = formatTimestamp(Instant.now().plusSeconds(durationMinutes * 60L));
                
                sendTemporaryLockoutEmail(user, durationMinutes, unlockTime);
                sendTemporaryLockoutSms(user, durationMinutes);
                sendTemporaryLockoutPush(user, durationMinutes);
                
                publishSecurityEvent(SecurityEventType.TEMPORARY_LOCKOUT, userId, Map.of(
                    "durationMinutes", durationMinutes,
                    "unlockTime", unlockTime,
                    "severity", "MEDIUM"
                ));
                
            } catch (Exception e) {
                log.error("Failed to send temporary lockout notification for user: {}", userId, e);
            }
        }, notificationExecutor);
    }
    
    public void notifyExtendedLockout(String userId, int durationHours) {
        log.error("SECURITY_NOTIFICATION: Extended lockout for user {} - duration: {} hours",
                userId, durationHours);
        
        CompletableFuture.runAsync(() -> {
            try {
                Optional<User> userOpt = userRepository.findById(userId);
                if (userOpt.isEmpty()) {
                    log.error("User not found for extended lockout notification: {}", userId);
                    return;
                }
                
                User user = userOpt.get();
                String unlockTime = formatTimestamp(Instant.now().plusSeconds(durationHours * 3600L));
                
                sendExtendedLockoutEmail(user, durationHours, unlockTime);
                sendExtendedLockoutSms(user, durationHours);
                sendExtendedLockoutPush(user, durationHours);
                
                // Notify admins of extended lockout
                notifyAdminsOfExtendedLockout(user, durationHours);
                
                publishSecurityEvent(SecurityEventType.EXTENDED_LOCKOUT, userId, Map.of(
                    "durationHours", durationHours,
                    "unlockTime", unlockTime,
                    "severity", "HIGH"
                ));
                
            } catch (Exception e) {
                log.error("Failed to send extended lockout notification for user: {}", userId, e);
            }
        }, notificationExecutor);
    }
    
    public void notifyPermanentLockout(String userId) {
        log.error("SECURITY_NOTIFICATION: Permanent lockout for user {} - requires admin intervention",
                userId);
        
        CompletableFuture.runAsync(() -> {
            try {
                Optional<User> userOpt = userRepository.findById(userId);
                if (userOpt.isEmpty()) {
                    log.error("User not found for permanent lockout notification: {}", userId);
                    return;
                }
                
                User user = userOpt.get();
                
                sendPermanentLockoutEmail(user);
                sendPermanentLockoutSms(user);
                sendPermanentLockoutPush(user);
                
                // Immediately notify all admins
                notifyAdminsOfPermanentLockout(user);
                
                // Send webhook to external security systems
                sendSecurityWebhook(SecurityEventType.PERMANENT_LOCKOUT, userId, Map.of(
                    "userEmail", user.getEmail(),
                    "userName", user.getUsername(),
                    "timestamp", formatTimestamp(Instant.now()),
                    "severity", "CRITICAL",
                    "requiresIntervention", true
                ));
                
                publishSecurityEvent(SecurityEventType.PERMANENT_LOCKOUT, userId, Map.of(
                    "severity", "CRITICAL",
                    "requiresIntervention", true,
                    "userEmail", user.getEmail()
                ));
                
            } catch (Exception e) {
                log.error("Failed to send permanent lockout notification for user: {}", userId, e);
            }
        }, notificationExecutor);
    }
    
    // Email notification implementations
    
    private void sendSuspiciousActivityEmail(User user, String sourceIp, String reason, String timestamp) {
        if (!emailEnabled || user.getEmail() == null) {
            return;
        }
        
        try {
            MimeMessage message = mailSender.createMimeMessage();
            MimeMessageHelper helper = new MimeMessageHelper(message, true, "UTF-8");
            
            helper.setFrom(fromEmail);
            helper.setTo(user.getEmail());
            helper.setSubject(String.format("[%s Security Alert] Suspicious Activity Detected", appName));
            
            String htmlContent = buildSuspiciousActivityEmailHtml(user, sourceIp, reason, timestamp);
            helper.setText(htmlContent, true);
            
            mailSender.send(message);
            log.info("Suspicious activity email sent to user: {}", user.getId());
            
        } catch (Exception e) {
            log.error("Failed to send suspicious activity email to user: {}", user.getId(), e);
            // Fallback to simple email
            sendSimpleEmail(user.getEmail(), 
                String.format("[%s Security Alert] Suspicious Activity", appName),
                String.format("Suspicious activity detected from IP %s at %s. Reason: %s. Please review your account security.", 
                    sourceIp, timestamp, reason));
        }
    }
    
    private void sendTemporaryLockoutEmail(User user, int durationMinutes, String unlockTime) {
        if (!emailEnabled || user.getEmail() == null) {
            return;
        }
        
        try {
            MimeMessage message = mailSender.createMimeMessage();
            MimeMessageHelper helper = new MimeMessageHelper(message, true, "UTF-8");
            
            helper.setFrom(fromEmail);
            helper.setTo(user.getEmail());
            helper.setSubject(String.format("[%s Security] Account Temporarily Locked", appName));
            
            String htmlContent = buildTemporaryLockoutEmailHtml(user, durationMinutes, unlockTime);
            helper.setText(htmlContent, true);
            
            mailSender.send(message);
            log.info("Temporary lockout email sent to user: {}", user.getId());
            
        } catch (Exception e) {
            log.error("Failed to send temporary lockout email to user: {}", user.getId(), e);
        }
    }
    
    private void sendExtendedLockoutEmail(User user, int durationHours, String unlockTime) {
        if (!emailEnabled || user.getEmail() == null) {
            return;
        }
        
        try {
            MimeMessage message = mailSender.createMimeMessage();
            MimeMessageHelper helper = new MimeMessageHelper(message, true, "UTF-8");
            
            helper.setFrom(fromEmail);
            helper.setTo(user.getEmail());
            helper.setSubject(String.format("[%s Security] Account Locked - Extended Duration", appName));
            
            String htmlContent = buildExtendedLockoutEmailHtml(user, durationHours, unlockTime);
            helper.setText(htmlContent, true);
            
            mailSender.send(message);
            log.info("Extended lockout email sent to user: {}", user.getId());
            
        } catch (Exception e) {
            log.error("Failed to send extended lockout email to user: {}", user.getId(), e);
        }
    }
    
    private void sendPermanentLockoutEmail(User user) {
        if (!emailEnabled || user.getEmail() == null) {
            return;
        }
        
        try {
            MimeMessage message = mailSender.createMimeMessage();
            MimeMessageHelper helper = new MimeMessageHelper(message, true, "UTF-8");
            
            helper.setFrom(fromEmail);
            helper.setTo(user.getEmail());
            helper.setSubject(String.format("[%s Security] Account Permanently Locked", appName));
            
            String htmlContent = buildPermanentLockoutEmailHtml(user);
            helper.setText(htmlContent, true);
            
            mailSender.send(message);
            log.info("Permanent lockout email sent to user: {}", user.getId());
            
        } catch (Exception e) {
            log.error("Failed to send permanent lockout email to user: {}", user.getId(), e);
        }
    }
    
    // SMS notification implementations
    
    private void sendSuspiciousActivitySms(User user, String sourceIp, String reason, String timestamp) {
        if (!smsEnabled || user.getPhoneNumber() == null || smsServiceUrl.isEmpty()) {
            return;
        }
        
        try {
            String message = String.format("%s Security Alert: Suspicious activity from IP %s. Check your email for details.", 
                appName, sourceIp);
            
            sendSmsMessage(user.getPhoneNumber(), message);
            log.info("Suspicious activity SMS sent to user: {}", user.getId());
            
        } catch (Exception e) {
            log.error("Failed to send suspicious activity SMS to user: {}", user.getId(), e);
        }
    }
    
    private void sendTemporaryLockoutSms(User user, int durationMinutes) {
        if (!smsEnabled || user.getPhoneNumber() == null || smsServiceUrl.isEmpty()) {
            return;
        }
        
        try {
            String message = String.format("%s: Account locked for %d minutes due to failed authentication attempts. Check email for details.", 
                appName, durationMinutes);
            
            sendSmsMessage(user.getPhoneNumber(), message);
            log.info("Temporary lockout SMS sent to user: {}", user.getId());
            
        } catch (Exception e) {
            log.error("Failed to send temporary lockout SMS to user: {}", user.getId(), e);
        }
    }
    
    private void sendExtendedLockoutSms(User user, int durationHours) {
        if (!smsEnabled || user.getPhoneNumber() == null || smsServiceUrl.isEmpty()) {
            return;
        }
        
        try {
            String message = String.format("%s: Account locked for %d hours due to security concerns. Contact support if needed.", 
                appName, durationHours);
            
            sendSmsMessage(user.getPhoneNumber(), message);
            log.info("Extended lockout SMS sent to user: {}", user.getId());
            
        } catch (Exception e) {
            log.error("Failed to send extended lockout SMS to user: {}", user.getId(), e);
        }
    }
    
    private void sendPermanentLockoutSms(User user) {
        if (!smsEnabled || user.getPhoneNumber() == null || smsServiceUrl.isEmpty()) {
            return;
        }
        
        try {
            String message = String.format("%s: Account permanently locked for security. Contact support immediately for assistance.", 
                appName);
            
            sendSmsMessage(user.getPhoneNumber(), message);
            log.info("Permanent lockout SMS sent to user: {}", user.getId());
            
        } catch (Exception e) {
            log.error("Failed to send permanent lockout SMS to user: {}", user.getId(), e);
        }
    }
    
    // Push notification implementations
    
    private void sendSuspiciousActivityPush(User user, String sourceIp, String reason, String timestamp) {
        if (!pushEnabled || pushServiceUrl.isEmpty()) {
            return;
        }
        
        try {
            Map<String, Object> pushPayload = Map.of(
                "userId", user.getId(),
                "title", "Security Alert",
                "body", String.format("Suspicious activity detected from IP %s", sourceIp),
                "type", "security_alert",
                "priority", "high",
                "data", Map.of(
                    "sourceIp", sourceIp,
                    "reason", reason,
                    "timestamp", timestamp
                )
            );
            
            sendPushNotification(pushPayload);
            log.info("Suspicious activity push notification sent to user: {}", user.getId());
            
        } catch (Exception e) {
            log.error("Failed to send suspicious activity push notification to user: {}", user.getId(), e);
        }
    }
    
    private void sendTemporaryLockoutPush(User user, int durationMinutes) {
        if (!pushEnabled || pushServiceUrl.isEmpty()) {
            return;
        }
        
        try {
            Map<String, Object> pushPayload = Map.of(
                "userId", user.getId(),
                "title", "Account Locked",
                "body", String.format("Your account has been locked for %d minutes", durationMinutes),
                "type", "account_lockout",
                "priority", "high",
                "data", Map.of(
                    "lockoutDuration", durationMinutes,
                    "lockoutType", "temporary"
                )
            );
            
            sendPushNotification(pushPayload);
            log.info("Temporary lockout push notification sent to user: {}", user.getId());
            
        } catch (Exception e) {
            log.error("Failed to send temporary lockout push notification to user: {}", user.getId(), e);
        }
    }
    
    private void sendExtendedLockoutPush(User user, int durationHours) {
        if (!pushEnabled || pushServiceUrl.isEmpty()) {
            return;
        }
        
        try {
            Map<String, Object> pushPayload = Map.of(
                "userId", user.getId(),
                "title", "Account Locked - Extended",
                "body", String.format("Your account has been locked for %d hours due to security concerns", durationHours),
                "type", "account_lockout",
                "priority", "high",
                "data", Map.of(
                    "lockoutDuration", durationHours,
                    "lockoutType", "extended"
                )
            );
            
            sendPushNotification(pushPayload);
            log.info("Extended lockout push notification sent to user: {}", user.getId());
            
        } catch (Exception e) {
            log.error("Failed to send extended lockout push notification to user: {}", user.getId(), e);
        }
    }
    
    private void sendPermanentLockoutPush(User user) {
        if (!pushEnabled || pushServiceUrl.isEmpty()) {
            return;
        }
        
        try {
            Map<String, Object> pushPayload = Map.of(
                "userId", user.getId(),
                "title", "Account Permanently Locked",
                "body", "Your account has been permanently locked for security reasons",
                "type", "account_lockout",
                "priority", "critical",
                "data", Map.of(
                    "lockoutType", "permanent",
                    "supportUrl", supportUrl
                )
            );
            
            sendPushNotification(pushPayload);
            log.info("Permanent lockout push notification sent to user: {}", user.getId());
            
        } catch (Exception e) {
            log.error("Failed to send permanent lockout push notification to user: {}", user.getId(), e);
        }
    }
    
    // Admin notification methods
    
    private void notifyAdminsOfExtendedLockout(User user, int durationHours) {
        String[] admins = adminEmails.split(",");
        for (String adminEmail : admins) {
            sendSimpleEmail(adminEmail.trim(),
                String.format("[%s Admin Alert] Extended User Lockout", appName),
                String.format("User %s (%s) has been locked for %d hours due to repeated MFA failures. User ID: %s", 
                    user.getUsername(), user.getEmail(), durationHours, user.getId()));
        }
    }
    
    private void notifyAdminsOfPermanentLockout(User user) {
        String[] admins = adminEmails.split(",");
        for (String adminEmail : admins) {
            sendSimpleEmail(adminEmail.trim(),
                String.format("[%s Admin Alert] CRITICAL - Permanent User Lockout", appName),
                String.format("CRITICAL: User %s (%s) has been permanently locked due to excessive MFA failures. " +
                    "Manual intervention required. User ID: %s. Review security logs immediately.", 
                    user.getUsername(), user.getEmail(), user.getId()));
        }
    }
    
    // Helper methods for external service integration
    
    private void sendSmsMessage(String phoneNumber, String message) {
        try {
            Map<String, Object> smsRequest = Map.of(
                "to", phoneNumber,
                "message", message,
                "apiKey", smsApiKey
            );
            
            restTemplate.postForEntity(smsServiceUrl, smsRequest, String.class);
            
        } catch (Exception e) {
            log.error("Failed to send SMS to {}: {}", phoneNumber, e.getMessage());
        }
    }
    
    private void sendPushNotification(Map<String, Object> payload) {
        try {
            restTemplate.postForEntity(pushServiceUrl, payload, String.class);
            
        } catch (Exception e) {
            log.error("Failed to send push notification: {}", e.getMessage());
        }
    }
    
    private void sendSecurityWebhook(SecurityEventType eventType, String userId, Map<String, Object> data) {
        if (!webhookEnabled || securityWebhookUrl.isEmpty()) {
            return;
        }
        
        try {
            Map<String, Object> webhookPayload = new HashMap<>();
            webhookPayload.put("eventType", eventType.toString());
            webhookPayload.put("userId", userId);
            webhookPayload.put("timestamp", Instant.now().toString());
            webhookPayload.putAll(data);
            
            restTemplate.postForEntity(securityWebhookUrl, webhookPayload, String.class);
            
        } catch (Exception e) {
            log.error("Failed to send security webhook for event {}: {}", eventType, e.getMessage());
        }
    }
    
    private void publishSecurityEvent(SecurityEventType eventType, String userId, Map<String, Object> data) {
        try {
            Map<String, Object> event = new HashMap<>();
            event.put("eventType", eventType.toString());
            event.put("userId", userId);
            event.put("timestamp", Instant.now().toString());
            event.putAll(data);
            
            kafkaTemplate.send("security-events", userId, event);
            
        } catch (Exception e) {
            log.error("Failed to publish security event {}: {}", eventType, e.getMessage());
        }
    }
    
    private void sendSimpleEmail(String toEmail, String subject, String body) {
        try {
            SimpleMailMessage message = new SimpleMailMessage();
            message.setFrom(fromEmail);
            message.setTo(toEmail);
            message.setSubject(subject);
            message.setText(body);
            
            mailSender.send(message);
            
        } catch (Exception e) {
            log.error("Failed to send simple email to {}: {}", toEmail, e.getMessage());
        }
    }
    
    private String formatTimestamp(Instant timestamp) {
        return DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss z")
                .withZone(ZoneId.systemDefault())
                .format(timestamp);
    }
    
    // HTML email template builders
    
    private String buildSuspiciousActivityEmailHtml(User user, String sourceIp, String reason, String timestamp) {
        return String.format("""
            <html>
            <body style="font-family: Arial, sans-serif; line-height: 1.6; color: #333;">
                <div style="max-width: 600px; margin: 0 auto; padding: 20px;">
                    <div style="background: #f44336; color: white; padding: 15px; text-align: center;">
                        <h2>🔒 Security Alert</h2>
                    </div>
                    <div style="padding: 20px; background: #f9f9f9;">
                        <p>Hello %s,</p>
                        <p><strong>We detected suspicious activity on your %s account.</strong></p>
                        <div style="background: white; padding: 15px; border-left: 4px solid #f44336; margin: 15px 0;">
                            <p><strong>Details:</strong></p>
                            <ul>
                                <li><strong>IP Address:</strong> %s</li>
                                <li><strong>Time:</strong> %s</li>
                                <li><strong>Reason:</strong> %s</li>
                            </ul>
                        </div>
                        <p><strong>What to do:</strong></p>
                        <ul>
                            <li>If this was you, no action is needed</li>
                            <li>If this wasn't you, change your password immediately</li>
                            <li>Review your recent account activity</li>
                            <li>Enable additional security measures</li>
                        </ul>
                        <div style="text-align: center; margin: 20px 0;">
                            <a href="%s" style="background: #2196F3; color: white; padding: 12px 24px; text-decoration: none; border-radius: 4px;">Review Security Settings</a>
                        </div>
                        <p>If you need assistance, please contact our support team at <a href="%s">%s</a>.</p>
                        <p>Best regards,<br>%s Security Team</p>
                    </div>
                    <div style="text-align: center; font-size: 12px; color: #666; padding: 10px;">
                        This is an automated security alert. Please do not reply to this email.
                    </div>
                </div>
            </body>
            </html>
            """, user.getUsername(), appName, sourceIp, timestamp, reason, 
                securityUrl, supportUrl, supportUrl, appName);
    }
    
    private String buildTemporaryLockoutEmailHtml(User user, int durationMinutes, String unlockTime) {
        return String.format("""
            <html>
            <body style="font-family: Arial, sans-serif; line-height: 1.6; color: #333;">
                <div style="max-width: 600px; margin: 0 auto; padding: 20px;">
                    <div style="background: #ff9800; color: white; padding: 15px; text-align: center;">
                        <h2>🔒 Account Temporarily Locked</h2>
                    </div>
                    <div style="padding: 20px; background: #f9f9f9;">
                        <p>Hello %s,</p>
                        <p><strong>Your %s account has been temporarily locked due to multiple failed authentication attempts.</strong></p>
                        <div style="background: white; padding: 15px; border-left: 4px solid #ff9800; margin: 15px 0;">
                            <p><strong>Lockout Details:</strong></p>
                            <ul>
                                <li><strong>Duration:</strong> %d minutes</li>
                                <li><strong>Unlock Time:</strong> %s</li>
                                <li><strong>Reason:</strong> Multiple failed MFA attempts</li>
                            </ul>
                        </div>
                        <p><strong>What happens next:</strong></p>
                        <ul>
                            <li>Your account will be automatically unlocked at the time shown above</li>
                            <li>You can then try logging in again</li>
                            <li>Make sure you're using the correct authentication credentials</li>
                        </ul>
                        <p><strong>Security Tips:</strong></p>
                        <ul>
                            <li>Ensure your authentication app is synced correctly</li>
                            <li>Check for any clock synchronization issues</li>
                            <li>Contact support if you're having persistent issues</li>
                        </ul>
                        <p>If you believe this lockout was triggered in error, please contact our support team.</p>
                        <div style="text-align: center; margin: 20px 0;">
                            <a href="%s" style="background: #2196F3; color: white; padding: 12px 24px; text-decoration: none; border-radius: 4px;">Contact Support</a>
                        </div>
                        <p>Best regards,<br>%s Security Team</p>
                    </div>
                </div>
            </body>
            </html>
            """, user.getUsername(), appName, durationMinutes, unlockTime, supportUrl, appName);
    }
    
    private String buildExtendedLockoutEmailHtml(User user, int durationHours, String unlockTime) {
        return String.format("""
            <html>
            <body style="font-family: Arial, sans-serif; line-height: 1.6; color: #333;">
                <div style="max-width: 600px; margin: 0 auto; padding: 20px;">
                    <div style="background: #f44336; color: white; padding: 15px; text-align: center;">
                        <h2>🚨 Account Locked - Extended Duration</h2>
                    </div>
                    <div style="padding: 20px; background: #f9f9f9;">
                        <p>Hello %s,</p>
                        <p><strong>Your %s account has been locked for an extended period due to repeated security violations.</strong></p>
                        <div style="background: white; padding: 15px; border-left: 4px solid #f44336; margin: 15px 0;">
                            <p><strong>Lockout Details:</strong></p>
                            <ul>
                                <li><strong>Duration:</strong> %d hours</li>
                                <li><strong>Unlock Time:</strong> %s</li>
                                <li><strong>Reason:</strong> Repeated failed authentication attempts</li>
                            </ul>
                        </div>
                        <p><strong>Important:</strong> This extended lockout indicates potential security concerns with your account.</p>
                        <p><strong>What you should do:</strong></p>
                        <ul>
                            <li>Review your account security settings</li>
                            <li>Check for any unauthorized access attempts</li>
                            <li>Consider changing your password when the lockout expires</li>
                            <li>Verify your MFA device is working correctly</li>
                        </ul>
                        <div style="background: #ffebee; padding: 15px; border-radius: 4px; margin: 15px 0;">
                            <p><strong>⚠️ Security Notice:</strong> Our administrators have been notified of this lockout. 
                            If you continue to experience issues, your account may be subject to permanent restrictions.</p>
                        </div>
                        <div style="text-align: center; margin: 20px 0;">
                            <a href="%s" style="background: #f44336; color: white; padding: 12px 24px; text-decoration: none; border-radius: 4px;">Get Security Help</a>
                        </div>
                        <p>Best regards,<br>%s Security Team</p>
                    </div>
                </div>
            </body>
            </html>
            """, user.getUsername(), appName, durationHours, unlockTime, supportUrl, appName);
    }
    
    private String buildPermanentLockoutEmailHtml(User user) {
        return String.format("""
            <html>
            <body style="font-family: Arial, sans-serif; line-height: 1.6; color: #333;">
                <div style="max-width: 600px; margin: 0 auto; padding: 20px;">
                    <div style="background: #d32f2f; color: white; padding: 15px; text-align: center;">
                        <h2>🔒 Account Permanently Locked</h2>
                    </div>
                    <div style="padding: 20px; background: #f9f9f9;">
                        <p>Hello %s,</p>
                        <p><strong>Your %s account has been permanently locked due to excessive security violations.</strong></p>
                        <div style="background: white; padding: 15px; border-left: 4px solid #d32f2f; margin: 15px 0;">
                            <p><strong>Lockout Details:</strong></p>
                            <ul>
                                <li><strong>Status:</strong> Permanently Locked</li>
                                <li><strong>Reason:</strong> Excessive failed authentication attempts</li>
                                <li><strong>Action Required:</strong> Contact support for account recovery</li>
                            </ul>
                        </div>
                        <div style="background: #ffcdd2; padding: 15px; border-radius: 4px; margin: 15px 0;">
                            <p><strong>⛔ Critical Security Alert:</strong> Your account has been permanently disabled to protect against potential security threats. 
                            This action cannot be reversed automatically and requires manual intervention from our security team.</p>
                        </div>
                        <p><strong>To recover your account:</strong></p>
                        <ul>
                            <li>Contact our support team immediately</li>
                            <li>Provide verification of your identity</li>
                            <li>Explain the circumstances that led to the lockout</li>
                            <li>Be prepared to answer security questions</li>
                        </ul>
                        <p><strong>What our team will do:</strong></p>
                        <ul>
                            <li>Review your account security logs</li>
                            <li>Verify your identity</li>
                            <li>Assess the security risk</li>
                            <li>Determine if account recovery is appropriate</li>
                        </ul>
                        <div style="text-align: center; margin: 20px 0;">
                            <a href="%s" style="background: #d32f2f; color: white; padding: 12px 24px; text-decoration: none; border-radius: 4px;">Contact Support Immediately</a>
                        </div>
                        <p><strong>Important:</strong> Do not attempt to create a new account. Contact support first to resolve this issue.</p>
                        <p>Best regards,<br>%s Security Team</p>
                    </div>
                    <div style="text-align: center; font-size: 12px; color: #666; padding: 10px;">
                        This is a critical security notification. Your account security is our top priority.
                    </div>
                </div>
            </body>
            </html>
            """, user.getUsername(), appName, supportUrl, appName);
    }
    
    private enum SecurityEventType {
        SUSPICIOUS_ACTIVITY,
        TEMPORARY_LOCKOUT,
        EXTENDED_LOCKOUT,
        PERMANENT_LOCKOUT
    }
}