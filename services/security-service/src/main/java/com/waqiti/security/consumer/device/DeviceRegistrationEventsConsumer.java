package com.waqiti.security.consumer.device;

import com.waqiti.common.events.DeviceRegistrationEvent;
import com.waqiti.security.domain.RegisteredDevice;
import com.waqiti.security.repository.DeviceRepository;
import com.waqiti.security.service.DeviceManagementService;
import com.waqiti.security.service.DeviceFingerprintService;
import com.waqiti.security.service.DeviceSecurityService;
import com.waqiti.security.metrics.DeviceMetricsService;
import com.waqiti.common.audit.AuditService;
import com.waqiti.common.notification.NotificationService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.kafka.annotation.RetryableTopic;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.kafka.support.Acknowledgment;
import org.springframework.kafka.support.KafkaHeaders;
import org.springframework.messaging.handler.annotation.Header;
import org.springframework.messaging.handler.annotation.Payload;
import org.springframework.retry.annotation.Backoff;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Isolation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.kafka.retrytopic.TopicSuffixingStrategy;

import java.time.LocalDateTime;
import java.time.Instant;
import java.util.*;

@Component
@Slf4j
@RequiredArgsConstructor
public class DeviceRegistrationEventsConsumer {
    
    private final DeviceRepository deviceRepository;
    private final DeviceManagementService managementService;
    private final DeviceFingerprintService fingerprintService;
    private final DeviceSecurityService securityService;
    private final DeviceMetricsService metricsService;
    private final AuditService auditService;
    private final NotificationService notificationService;
    private final KafkaTemplate<String, Object> kafkaTemplate;
    
    private static final int MAX_DEVICES_PER_USER = 10;
    private static final long DEVICE_INACTIVITY_DAYS = 90;
    
    @KafkaListener(
        topics = {"device-registration-events", "device-lifecycle-events", "device-management-events"},
        groupId = "device-registration-service-group",
        containerFactory = "criticalFinancialKafkaListenerContainerFactory",
        concurrency = "5"
    )
    @RetryableTopic(
        attempts = "3",
        backoff = @Backoff(delay = 2000, multiplier = 2.0, maxDelay = 20000),
        dltStrategy = org.springframework.kafka.retrytopic.DltStrategy.FAIL_ON_ERROR,
        topicSuffixingStrategy = TopicSuffixingStrategy.SUFFIX_WITH_INDEX_VALUE
    )
    @Transactional(isolation = Isolation.READ_COMMITTED)
    public void handleDeviceRegistrationEvent(
            @Payload DeviceRegistrationEvent event,
            @Header(KafkaHeaders.RECEIVED_PARTITION) int partition,
            @Header(KafkaHeaders.OFFSET) long offset,
            Acknowledgment acknowledgment) {
        
        String correlationId = String.format("device-%s-%s-p%d-o%d", 
            event.getUserId(), event.getDeviceId(), partition, offset);
        
        log.info("Processing device event: userId={}, deviceId={}, type={}, status={}",
            event.getUserId(), event.getDeviceId(), event.getDeviceType(), event.getStatus());
        
        try {
            switch (event.getStatus()) {
                case "REGISTRATION_INITIATED":
                    initiateDeviceRegistration(event, correlationId);
                    break;
                    
                case "REGISTERED":
                    registerDevice(event, correlationId);
                    break;
                    
                case "VERIFIED":
                    verifyDevice(event, correlationId);
                    break;
                    
                case "TRUSTED":
                    trustDevice(event, correlationId);
                    break;
                    
                case "SUSPICIOUS":
                    flagSuspiciousDevice(event, correlationId);
                    break;
                    
                case "BLOCKED":
                    blockDevice(event, correlationId);
                    break;
                    
                case "UNREGISTERED":
                    unregisterDevice(event, correlationId);
                    break;
                    
                case "INACTIVE":
                    markDeviceInactive(event, correlationId);
                    break;
                    
                case "COMPROMISED":
                    handleCompromisedDevice(event, correlationId);
                    break;
                    
                default:
                    log.warn("Unknown device status: {}", event.getStatus());
                    break;
            }
            
            auditService.logDeviceEvent("DEVICE_EVENT_PROCESSED", event.getUserId(),
                Map.of("deviceId", event.getDeviceId(), "deviceType", event.getDeviceType(),
                    "status", event.getStatus(), "correlationId", correlationId, 
                    "timestamp", Instant.now()));
            
            acknowledgment.acknowledge();
            
        } catch (Exception e) {
            log.error("Failed to process device registration event: {}", e.getMessage(), e);
            kafkaTemplate.send("device-registration-events-dlq", Map.of(
                "originalEvent", event, "error", e.getMessage(), 
                "correlationId", correlationId, "timestamp", Instant.now()));
            acknowledgment.acknowledge();
        }
    }
    
    private void initiateDeviceRegistration(DeviceRegistrationEvent event, String correlationId) {
        int userDeviceCount = deviceRepository.countActiveDevicesByUserId(event.getUserId());
        
        if (userDeviceCount >= MAX_DEVICES_PER_USER) {
            log.warn("User {} has reached maximum device limit: {}", event.getUserId(), MAX_DEVICES_PER_USER);
            
            notificationService.sendNotification(event.getUserId(), "Device Limit Reached",
                String.format("You have reached the maximum limit of %d registered devices. Please remove an old device to register a new one.", 
                    MAX_DEVICES_PER_USER),
                correlationId);
            
            return;
        }
        
        String fingerprint = fingerprintService.generateDeviceFingerprint(
            event.getDeviceId(), 
            event.getDeviceType(), 
            event.getDeviceModel(),
            event.getOsVersion(),
            event.getUserAgent()
        );
        
        RegisteredDevice device = RegisteredDevice.builder()
            .deviceId(event.getDeviceId())
            .userId(event.getUserId())
            .deviceType(event.getDeviceType())
            .deviceModel(event.getDeviceModel())
            .osVersion(event.getOsVersion())
            .appVersion(event.getAppVersion())
            .deviceFingerprint(fingerprint)
            .status("REGISTRATION_INITIATED")
            .registrationInitiatedAt(LocalDateTime.now())
            .ipAddress(event.getIpAddress())
            .correlationId(correlationId)
            .build();
        deviceRepository.save(device);
        
        kafkaTemplate.send("device-lifecycle-events", Map.of(
            "userId", event.getUserId(),
            "deviceId", event.getDeviceId(),
            "status", "REGISTERED",
            "correlationId", correlationId,
            "timestamp", Instant.now()
        ));
        
        metricsService.recordDeviceRegistrationInitiated(event.getDeviceType());
        
        log.info("Device registration initiated: userId={}, deviceId={}, type={}", 
            event.getUserId(), event.getDeviceId(), event.getDeviceType());
    }
    
    private void registerDevice(DeviceRegistrationEvent event, String correlationId) {
        RegisteredDevice device = deviceRepository.findByDeviceId(event.getDeviceId())
            .orElseThrow(() -> new RuntimeException("Device not found"));
        
        device.setStatus("REGISTERED");
        device.setRegisteredAt(LocalDateTime.now());
        deviceRepository.save(device);
        
        securityService.performDeviceSecurityCheck(event.getDeviceId());
        
        notificationService.sendNotification(event.getUserId(), "New Device Registered",
            String.format("A new %s device was registered to your account. If this wasn't you, please secure your account immediately.", 
                event.getDeviceType()),
            correlationId);
        
        metricsService.recordDeviceRegistered(event.getDeviceType());
        
        log.info("Device registered: userId={}, deviceId={}, type={}", 
            event.getUserId(), event.getDeviceId(), event.getDeviceType());
    }
    
    private void verifyDevice(DeviceRegistrationEvent event, String correlationId) {
        RegisteredDevice device = deviceRepository.findByDeviceId(event.getDeviceId())
            .orElseThrow(() -> new RuntimeException("Device not found"));
        
        device.setStatus("VERIFIED");
        device.setVerifiedAt(LocalDateTime.now());
        device.setVerificationMethod(event.getVerificationMethod());
        deviceRepository.save(device);
        
        metricsService.recordDeviceVerified(event.getDeviceType());
        
        log.info("Device verified: userId={}, deviceId={}, method={}", 
            event.getUserId(), event.getDeviceId(), event.getVerificationMethod());
    }
    
    private void trustDevice(DeviceRegistrationEvent event, String correlationId) {
        RegisteredDevice device = deviceRepository.findByDeviceId(event.getDeviceId())
            .orElseThrow(() -> new RuntimeException("Device not found"));
        
        device.setStatus("TRUSTED");
        device.setTrustedAt(LocalDateTime.now());
        device.setTrustScore(event.getTrustScore());
        deviceRepository.save(device);
        
        managementService.enableTrustedDeviceFeatures(event.getDeviceId());
        
        notificationService.sendNotification(event.getUserId(), "Device Trusted",
            "Your device has been marked as trusted. You'll enjoy faster authentication and enhanced features.",
            correlationId);
        
        metricsService.recordDeviceTrusted(event.getDeviceType());
        
        log.info("Device trusted: userId={}, deviceId={}, trustScore={}", 
            event.getUserId(), event.getDeviceId(), event.getTrustScore());
    }
    
    private void flagSuspiciousDevice(DeviceRegistrationEvent event, String correlationId) {
        RegisteredDevice device = deviceRepository.findByDeviceId(event.getDeviceId())
            .orElseThrow(() -> new RuntimeException("Device not found"));
        
        device.setStatus("SUSPICIOUS");
        device.setFlaggedAt(LocalDateTime.now());
        device.setSuspiciousReason(event.getReason());
        device.setRiskScore(event.getRiskScore());
        deviceRepository.save(device);
        
        kafkaTemplate.send("security-incidents", Map.of(
            "userId", event.getUserId(),
            "deviceId", event.getDeviceId(),
            "incidentType", "SUSPICIOUS_DEVICE",
            "reason", event.getReason(),
            "riskScore", event.getRiskScore(),
            "correlationId", correlationId,
            "timestamp", Instant.now()
        ));
        
        notificationService.sendNotification(event.getUserId(), "Suspicious Device Activity",
            String.format("We detected suspicious activity from your %s device. If you don't recognize this device, please secure your account.", 
                event.getDeviceType()),
            correlationId);
        
        metricsService.recordSuspiciousDevice(event.getDeviceType(), event.getReason());
        
        log.warn("Suspicious device flagged: userId={}, deviceId={}, reason={}, riskScore={}", 
            event.getUserId(), event.getDeviceId(), event.getReason(), event.getRiskScore());
    }
    
    private void blockDevice(DeviceRegistrationEvent event, String correlationId) {
        RegisteredDevice device = deviceRepository.findByDeviceId(event.getDeviceId())
            .orElseThrow(() -> new RuntimeException("Device not found"));
        
        device.setStatus("BLOCKED");
        device.setBlockedAt(LocalDateTime.now());
        device.setBlockReason(event.getReason());
        deviceRepository.save(device);
        
        managementService.revokeDeviceAccess(event.getDeviceId());
        
        notificationService.sendNotification(event.getUserId(), "Device Blocked",
            String.format("Your %s device has been blocked for security reasons. Contact support if you need assistance.", 
                event.getDeviceType()),
            correlationId);
        
        metricsService.recordDeviceBlocked(event.getDeviceType(), event.getReason());
        
        log.error("Device blocked: userId={}, deviceId={}, reason={}", 
            event.getUserId(), event.getDeviceId(), event.getReason());
    }
    
    private void unregisterDevice(DeviceRegistrationEvent event, String correlationId) {
        RegisteredDevice device = deviceRepository.findByDeviceId(event.getDeviceId())
            .orElseThrow(() -> new RuntimeException("Device not found"));
        
        device.setStatus("UNREGISTERED");
        device.setUnregisteredAt(LocalDateTime.now());
        deviceRepository.save(device);
        
        managementService.revokeDeviceAccess(event.getDeviceId());
        managementService.cleanupDeviceData(event.getDeviceId());
        
        metricsService.recordDeviceUnregistered(event.getDeviceType());
        
        log.info("Device unregistered: userId={}, deviceId={}", 
            event.getUserId(), event.getDeviceId());
    }
    
    private void markDeviceInactive(DeviceRegistrationEvent event, String correlationId) {
        RegisteredDevice device = deviceRepository.findByDeviceId(event.getDeviceId())
            .orElseThrow(() -> new RuntimeException("Device not found"));
        
        device.setStatus("INACTIVE");
        device.setMarkedInactiveAt(LocalDateTime.now());
        deviceRepository.save(device);
        
        notificationService.sendNotification(event.getUserId(), "Inactive Device",
            String.format("Your %s device has been inactive for %d days and may be automatically unregistered soon.", 
                event.getDeviceType(), DEVICE_INACTIVITY_DAYS),
            correlationId);
        
        metricsService.recordDeviceInactive(event.getDeviceType());
        
        log.info("Device marked inactive: userId={}, deviceId={}, lastActivityDaysAgo={}", 
            event.getUserId(), event.getDeviceId(), DEVICE_INACTIVITY_DAYS);
    }
    
    private void handleCompromisedDevice(DeviceRegistrationEvent event, String correlationId) {
        RegisteredDevice device = deviceRepository.findByDeviceId(event.getDeviceId())
            .orElseThrow(() -> new RuntimeException("Device not found"));
        
        device.setStatus("COMPROMISED");
        device.setCompromisedAt(LocalDateTime.now());
        device.setCompromiseReason(event.getReason());
        deviceRepository.save(device);
        
        managementService.immediatelyRevokeAllAccess(event.getDeviceId());
        managementService.terminateAllActiveSessions(event.getUserId(), event.getDeviceId());
        
        kafkaTemplate.send("security-incidents", Map.of(
            "userId", event.getUserId(),
            "deviceId", event.getDeviceId(),
            "incidentType", "DEVICE_COMPROMISED",
            "reason", event.getReason(),
            "severity", "CRITICAL",
            "correlationId", correlationId,
            "timestamp", Instant.now()
        ));
        
        notificationService.sendNotification(event.getUserId(), "URGENT: Device Compromised",
            String.format("Your %s device has been compromised. All access has been revoked. Please contact support immediately.", 
                event.getDeviceType()),
            correlationId);
        
        metricsService.recordDeviceCompromised(event.getDeviceType());
        
        log.error("CRITICAL: Device compromised: userId={}, deviceId={}, reason={}", 
            event.getUserId(), event.getDeviceId(), event.getReason());
    }
}