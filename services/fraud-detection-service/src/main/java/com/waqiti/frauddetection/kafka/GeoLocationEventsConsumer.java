package com.waqiti.frauddetection.kafka;

import com.waqiti.common.events.GeoLocationEvent;
import com.waqiti.common.events.FraudAlertEvent;
import com.waqiti.frauddetection.domain.GeoLocationRecord;
import com.waqiti.frauddetection.domain.LocationRiskLevel;
import com.waqiti.frauddetection.domain.TravelPattern;
import com.waqiti.frauddetection.repository.GeoLocationRecordRepository;
import com.waqiti.frauddetection.service.GeoLocationService;
import com.waqiti.frauddetection.service.LocationRiskService;
import com.waqiti.frauddetection.service.ImpossibleTravelDetectionService;
import com.waqiti.frauddetection.service.GeoFencingService;
import com.waqiti.frauddetection.exception.GeoLocationException;
import com.waqiti.frauddetection.metrics.GeoLocationMetricsService;
import com.waqiti.common.audit.AuditService;
import com.waqiti.common.notification.NotificationService;
import com.waqiti.common.security.SecurityContext;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.kafka.annotation.RetryableTopic;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.kafka.retrytopic.TopicSuffixingStrategy;
import org.springframework.kafka.support.Acknowledgment;
import org.springframework.kafka.support.KafkaHeaders;
import org.springframework.messaging.handler.annotation.Header;
import org.springframework.messaging.handler.annotation.Payload;
import org.springframework.retry.annotation.Backoff;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.annotation.Isolation;

import java.time.LocalDateTime;
import java.time.Instant;
import java.util.*;

@Component
@Slf4j
@RequiredArgsConstructor
public class GeoLocationEventsConsumer {
    
    private final GeoLocationRecordRepository geoLocationRepository;
    private final GeoLocationService geoLocationService;
    private final LocationRiskService locationRiskService;
    private final ImpossibleTravelDetectionService travelDetectionService;
    private final GeoFencingService geoFencingService;
    private final GeoLocationMetricsService metricsService;
    private final AuditService auditService;
    private final NotificationService notificationService;
    private final SecurityContext securityContext;
    private final KafkaTemplate<String, Object> kafkaTemplate;
    
    private static final double IMPOSSIBLE_TRAVEL_THRESHOLD_KM = 500.0;
    private static final int IMPOSSIBLE_TRAVEL_WINDOW_MINUTES = 30;
    private static final Set<String> HIGH_RISK_COUNTRIES = Set.of("KP", "IR", "SY", "CU");
    
    @KafkaListener(
        topics = {"geo-location-events", "location-tracking-events", "ip-geolocation-events"},
        groupId = "fraud-geolocation-service-group",
        containerFactory = "criticalFinancialKafkaListenerContainerFactory",
        concurrency = "8"
    )
    @RetryableTopic(
        attempts = "4",
        backoff = @Backoff(delay = 2000, multiplier = 2.0, maxDelay = 25000),
        dltStrategy = org.springframework.kafka.retrytopic.DltStrategy.FAIL_ON_ERROR,
        topicSuffixingStrategy = TopicSuffixingStrategy.SUFFIX_WITH_INDEX_VALUE,
        include = {Exception.class}
    )
    @Transactional(isolation = Isolation.SERIALIZABLE)
    public void handleGeoLocationEvent(
            @Payload GeoLocationEvent event,
            @Header(KafkaHeaders.RECEIVED_PARTITION) int partition,
            @Header(KafkaHeaders.OFFSET) long offset,
            @Header(value = KafkaHeaders.RECEIVED_TOPIC, required = false) String topic,
            Acknowledgment acknowledgment) {
        
        String correlationId = String.format("geo-%s-p%d-o%d", 
            event.getUserId(), partition, offset);
        
        log.info("Processing geo-location event: userId={}, eventType={}, location={}, correlation={}",
            event.getUserId(), event.getEventType(), event.getCountryCode(), correlationId);
        
        try {
            validateGeoLocationEvent(event);
            
            switch (event.getEventType()) {
                case LOCATION_DETECTED:
                    processLocationDetected(event, correlationId);
                    break;
                case IP_LOCATION_IDENTIFIED:
                    processIpLocationIdentified(event, correlationId);
                    break;
                case DEVICE_LOCATION_UPDATED:
                    processDeviceLocationUpdated(event, correlationId);
                    break;
                case IMPOSSIBLE_TRAVEL_DETECTED:
                    processImpossibleTravelDetected(event, correlationId);
                    break;
                case HIGH_RISK_LOCATION:
                    processHighRiskLocation(event, correlationId);
                    break;
                case GEO_FENCE_VIOLATION:
                    processGeoFenceViolation(event, correlationId);
                    break;
                case VPN_PROXY_DETECTED:
                    processVpnProxyDetected(event, correlationId);
                    break;
                case LOCATION_MISMATCH:
                    processLocationMismatch(event, correlationId);
                    break;
                default:
                    log.warn("Unknown geo-location event type: {}", event.getEventType());
                    break;
            }
            
            auditService.logSecurityEvent(
                "GEO_LOCATION_EVENT_PROCESSED",
                event.getUserId(),
                Map.of(
                    "eventType", event.getEventType(),
                    "latitude", event.getLatitude(),
                    "longitude", event.getLongitude(),
                    "countryCode", event.getCountryCode(),
                    "ipAddress", event.getIpAddress(),
                    "correlationId", correlationId,
                    "timestamp", Instant.now()
                )
            );
            
            acknowledgment.acknowledge();
            
        } catch (Exception e) {
            log.error("Failed to process geo-location event: userId={}, error={}",
                event.getUserId(), e.getMessage(), e);
            
            handleGeoLocationEventError(event, e, correlationId);
            acknowledgment.acknowledge();
        }
    }
    
    private void processLocationDetected(GeoLocationEvent event, String correlationId) {
        log.info("Processing location detected: userId={}, lat={}, lon={}, country={}", 
            event.getUserId(), event.getLatitude(), event.getLongitude(), event.getCountryCode());
        
        GeoLocationRecord location = GeoLocationRecord.builder()
            .id(UUID.randomUUID().toString())
            .userId(event.getUserId())
            .sessionId(event.getSessionId())
            .transactionId(event.getTransactionId())
            .latitude(event.getLatitude())
            .longitude(event.getLongitude())
            .countryCode(event.getCountryCode())
            .countryName(event.getCountryName())
            .city(event.getCity())
            .region(event.getRegion())
            .ipAddress(event.getIpAddress())
            .isp(event.getIsp())
            .detectionMethod(event.getDetectionMethod())
            .accuracyMeters(event.getAccuracyMeters())
            .detectedAt(LocalDateTime.now())
            .correlationId(correlationId)
            .build();
        
        LocationRiskLevel riskLevel = locationRiskService.assessLocationRisk(location);
        location.setRiskLevel(riskLevel);
        location.setRiskScore(locationRiskService.calculateRiskScore(location));
        
        geoLocationRepository.save(location);
        
        checkForImpossibleTravel(event.getUserId(), location, correlationId);
        
        checkForHighRiskLocation(location, correlationId);
        
        checkForGeoFenceViolations(event.getUserId(), location, correlationId);
        
        metricsService.recordLocationDetected(
            event.getCountryCode(), 
            riskLevel,
            event.getDetectionMethod()
        );
        
        log.info("Location detected and stored: userId={}, locationId={}, riskLevel={}", 
            event.getUserId(), location.getId(), riskLevel);
    }
    
    private void processIpLocationIdentified(GeoLocationEvent event, String correlationId) {
        log.info("Processing IP location: userId={}, ipAddress={}, country={}", 
            event.getUserId(), event.getIpAddress(), event.getCountryCode());
        
        Map<String, Object> ipInfo = geoLocationService.enrichIpInformation(
            event.getIpAddress()
        );
        
        GeoLocationRecord location = GeoLocationRecord.builder()
            .id(UUID.randomUUID().toString())
            .userId(event.getUserId())
            .sessionId(event.getSessionId())
            .ipAddress(event.getIpAddress())
            .countryCode(event.getCountryCode())
            .countryName(event.getCountryName())
            .city(event.getCity())
            .region(event.getRegion())
            .isp((String) ipInfo.get("isp"))
            .asn((String) ipInfo.get("asn"))
            .organization((String) ipInfo.get("organization"))
            .isVpn((Boolean) ipInfo.getOrDefault("isVpn", false))
            .isProxy((Boolean) ipInfo.getOrDefault("isProxy", false))
            .isTor((Boolean) ipInfo.getOrDefault("isTor", false))
            .detectionMethod("IP_GEOLOCATION")
            .detectedAt(LocalDateTime.now())
            .correlationId(correlationId)
            .build();
        
        LocationRiskLevel riskLevel = locationRiskService.assessLocationRisk(location);
        location.setRiskLevel(riskLevel);
        location.setRiskScore(locationRiskService.calculateRiskScore(location));
        
        geoLocationRepository.save(location);
        
        if (location.isVpn() || location.isProxy() || location.isTor()) {
            handleVpnProxyDetection(location, correlationId);
        }
        
        metricsService.recordIpLocationIdentified(
            event.getCountryCode(),
            riskLevel,
            location.isVpn()
        );
    }
    
    private void processDeviceLocationUpdated(GeoLocationEvent event, String correlationId) {
        log.info("Processing device location update: userId={}, deviceId={}", 
            event.getUserId(), event.getDeviceId());
        
        geoLocationService.updateDeviceLocation(
            event.getDeviceId(),
            event.getLatitude(),
            event.getLongitude(),
            event.getCountryCode(),
            event.getAccuracyMeters()
        );
        
        metricsService.recordDeviceLocationUpdated(event.getCountryCode());
    }
    
    private void processImpossibleTravelDetected(GeoLocationEvent event, String correlationId) {
        log.warn("Processing impossible travel: userId={}, fromLocation={} to toLocation={}, timeWindow={}min", 
            event.getUserId(), event.getPreviousCountryCode(), event.getCountryCode(), 
            event.getTravelTimeMinutes());
        
        TravelPattern travelPattern = TravelPattern.builder()
            .userId(event.getUserId())
            .fromLatitude(event.getPreviousLatitude())
            .fromLongitude(event.getPreviousLongitude())
            .fromCountry(event.getPreviousCountryCode())
            .toLatitude(event.getLatitude())
            .toLongitude(event.getLongitude())
            .toCountry(event.getCountryCode())
            .distanceKm(event.getDistanceKm())
            .travelTimeMinutes(event.getTravelTimeMinutes())
            .impossibleTravel(true)
            .detectedAt(LocalDateTime.now())
            .build();
        
        travelDetectionService.recordImpossibleTravel(travelPattern);
        
        FraudAlertEvent fraudAlert = FraudAlertEvent.builder()
            .alertId(UUID.randomUUID())
            .userId(event.getUserId())
            .alertType("IMPOSSIBLE_TRAVEL")
            .severity("HIGH")
            .riskScore(95.0)
            .riskFactors(List.of(
                String.format("Impossible travel: %s to %s in %d minutes", 
                    event.getPreviousCountryCode(), event.getCountryCode(), event.getTravelTimeMinutes()),
                String.format("Distance: %.2f km", event.getDistanceKm())
            ))
            .timestamp(Instant.now())
            .correlationId(correlationId)
            .build();
        
        kafkaTemplate.send("fraud-alert-events", fraudAlert);
        
        notificationService.sendSecurityAlert(
            "Impossible Travel Detected",
            String.format("User %s traveled from %s to %s (%.2f km) in %d minutes",
                event.getUserId(), event.getPreviousCountryCode(), event.getCountryCode(),
                event.getDistanceKm(), event.getTravelTimeMinutes()),
            NotificationService.Priority.HIGH
        );
        
        metricsService.recordImpossibleTravelDetected(
            event.getPreviousCountryCode(),
            event.getCountryCode(),
            event.getDistanceKm()
        );
    }
    
    private void processHighRiskLocation(GeoLocationEvent event, String correlationId) {
        log.warn("Processing high-risk location: userId={}, country={}, riskReason={}", 
            event.getUserId(), event.getCountryCode(), event.getRiskReason());
        
        FraudAlertEvent fraudAlert = FraudAlertEvent.builder()
            .alertId(UUID.randomUUID())
            .userId(event.getUserId())
            .alertType("HIGH_RISK_LOCATION")
            .severity("MEDIUM")
            .riskScore(75.0)
            .riskFactors(List.of(
                String.format("High-risk country: %s", event.getCountryCode()),
                String.format("Reason: %s", event.getRiskReason())
            ))
            .timestamp(Instant.now())
            .correlationId(correlationId)
            .build();
        
        kafkaTemplate.send("fraud-alert-events", fraudAlert);
        
        locationRiskService.flagHighRiskActivity(
            event.getUserId(),
            event.getCountryCode(),
            event.getRiskReason()
        );
        
        metricsService.recordHighRiskLocationAccess(event.getCountryCode(), event.getRiskReason());
    }
    
    private void processGeoFenceViolation(GeoLocationEvent event, String correlationId) {
        log.warn("Processing geo-fence violation: userId={}, fenceId={}, action={}", 
            event.getUserId(), event.getGeoFenceId(), event.getViolationAction());
        
        geoFencingService.recordViolation(
            event.getUserId(),
            event.getGeoFenceId(),
            event.getLatitude(),
            event.getLongitude(),
            event.getViolationAction()
        );
        
        FraudAlertEvent fraudAlert = FraudAlertEvent.builder()
            .alertId(UUID.randomUUID())
            .userId(event.getUserId())
            .alertType("GEO_FENCE_VIOLATION")
            .severity("MEDIUM")
            .riskScore(70.0)
            .riskFactors(List.of(
                String.format("Geo-fence violation: %s", event.getGeoFenceId()),
                String.format("Location: %s, %s", event.getCountryCode(), event.getCity())
            ))
            .timestamp(Instant.now())
            .correlationId(correlationId)
            .build();
        
        kafkaTemplate.send("fraud-alert-events", fraudAlert);
        
        metricsService.recordGeoFenceViolation(event.getGeoFenceId());
    }
    
    private void processVpnProxyDetected(GeoLocationEvent event, String correlationId) {
        log.warn("Processing VPN/Proxy detection: userId={}, ipAddress={}, type={}", 
            event.getUserId(), event.getIpAddress(), event.getProxyType());
        
        FraudAlertEvent fraudAlert = FraudAlertEvent.builder()
            .alertId(UUID.randomUUID())
            .userId(event.getUserId())
            .alertType("VPN_PROXY_DETECTED")
            .severity("MEDIUM")
            .riskScore(65.0)
            .riskFactors(List.of(
                String.format("VPN/Proxy detected: %s", event.getProxyType()),
                String.format("IP: %s", event.getIpAddress())
            ))
            .timestamp(Instant.now())
            .correlationId(correlationId)
            .build();
        
        kafkaTemplate.send("fraud-alert-events", fraudAlert);
        
        metricsService.recordVpnProxyDetected(event.getProxyType());
    }
    
    private void processLocationMismatch(GeoLocationEvent event, String correlationId) {
        log.warn("Processing location mismatch: userId={}, ipLocation={}, deviceLocation={}", 
            event.getUserId(), event.getIpCountryCode(), event.getDeviceCountryCode());
        
        double mismatchDistance = geoLocationService.calculateDistance(
            event.getIpLatitude(), event.getIpLongitude(),
            event.getDeviceLatitude(), event.getDeviceLongitude()
        );
        
        if (mismatchDistance > 100.0) {
            FraudAlertEvent fraudAlert = FraudAlertEvent.builder()
                .alertId(UUID.randomUUID())
                .userId(event.getUserId())
                .alertType("LOCATION_MISMATCH")
                .severity("MEDIUM")
                .riskScore(60.0)
                .riskFactors(List.of(
                    String.format("Location mismatch: IP=%s, Device=%s", 
                        event.getIpCountryCode(), event.getDeviceCountryCode()),
                    String.format("Distance: %.2f km", mismatchDistance)
                ))
                .timestamp(Instant.now())
                .correlationId(correlationId)
                .build();
            
            kafkaTemplate.send("fraud-alert-events", fraudAlert);
        }
        
        metricsService.recordLocationMismatch(
            event.getIpCountryCode(),
            event.getDeviceCountryCode(),
            mismatchDistance
        );
    }
    
    private void checkForImpossibleTravel(String userId, GeoLocationRecord currentLocation, 
            String correlationId) {
        
        Optional<GeoLocationRecord> previousLocation = geoLocationRepository
            .findMostRecentByUserId(userId, currentLocation.getDetectedAt().minusHours(2));
        
        if (previousLocation.isPresent()) {
            GeoLocationRecord prev = previousLocation.get();
            
            double distance = geoLocationService.calculateDistance(
                prev.getLatitude(), prev.getLongitude(),
                currentLocation.getLatitude(), currentLocation.getLongitude()
            );
            
            long minutesBetween = java.time.Duration.between(
                prev.getDetectedAt(), currentLocation.getDetectedAt()
            ).toMinutes();
            
            if (distance > IMPOSSIBLE_TRAVEL_THRESHOLD_KM && 
                minutesBetween < IMPOSSIBLE_TRAVEL_WINDOW_MINUTES) {
                
                log.warn("Impossible travel detected: userId={}, distance={}km, time={}min", 
                    userId, distance, minutesBetween);
                
                GeoLocationEvent impossibleTravelEvent = GeoLocationEvent.builder()
                    .userId(userId)
                    .eventType("IMPOSSIBLE_TRAVEL_DETECTED")
                    .previousLatitude(prev.getLatitude())
                    .previousLongitude(prev.getLongitude())
                    .previousCountryCode(prev.getCountryCode())
                    .latitude(currentLocation.getLatitude())
                    .longitude(currentLocation.getLongitude())
                    .countryCode(currentLocation.getCountryCode())
                    .distanceKm(distance)
                    .travelTimeMinutes((int) minutesBetween)
                    .timestamp(Instant.now())
                    .build();
                
                kafkaTemplate.send("geo-location-events", impossibleTravelEvent);
            }
        }
    }
    
    private void checkForHighRiskLocation(GeoLocationRecord location, String correlationId) {
        if (HIGH_RISK_COUNTRIES.contains(location.getCountryCode())) {
            log.warn("High-risk country detected: userId={}, country={}", 
                location.getUserId(), location.getCountryCode());
            
            GeoLocationEvent highRiskEvent = GeoLocationEvent.builder()
                .userId(location.getUserId())
                .eventType("HIGH_RISK_LOCATION")
                .countryCode(location.getCountryCode())
                .latitude(location.getLatitude())
                .longitude(location.getLongitude())
                .riskReason("Sanctioned or high-risk country")
                .timestamp(Instant.now())
                .build();
            
            kafkaTemplate.send("geo-location-events", highRiskEvent);
        }
    }
    
    private void checkForGeoFenceViolations(String userId, GeoLocationRecord location, 
            String correlationId) {
        
        List<String> violatedFences = geoFencingService.checkViolations(
            userId,
            location.getLatitude(),
            location.getLongitude()
        );
        
        for (String fenceId : violatedFences) {
            log.warn("Geo-fence violation: userId={}, fenceId={}", userId, fenceId);
            
            GeoLocationEvent violationEvent = GeoLocationEvent.builder()
                .userId(userId)
                .eventType("GEO_FENCE_VIOLATION")
                .geoFenceId(fenceId)
                .latitude(location.getLatitude())
                .longitude(location.getLongitude())
                .countryCode(location.getCountryCode())
                .city(location.getCity())
                .violationAction("ALERT")
                .timestamp(Instant.now())
                .build();
            
            kafkaTemplate.send("geo-location-events", violationEvent);
        }
    }
    
    private void handleVpnProxyDetection(GeoLocationRecord location, String correlationId) {
        String proxyType = location.isTor() ? "TOR" : 
                          location.isVpn() ? "VPN" : "PROXY";
        
        GeoLocationEvent vpnProxyEvent = GeoLocationEvent.builder()
            .userId(location.getUserId())
            .eventType("VPN_PROXY_DETECTED")
            .ipAddress(location.getIpAddress())
            .countryCode(location.getCountryCode())
            .proxyType(proxyType)
            .timestamp(Instant.now())
            .build();
        
        kafkaTemplate.send("geo-location-events", vpnProxyEvent);
    }
    
    private void validateGeoLocationEvent(GeoLocationEvent event) {
        if (event.getUserId() == null || event.getUserId().trim().isEmpty()) {
            throw new IllegalArgumentException("User ID is required");
        }
        
        if (event.getEventType() == null || event.getEventType().trim().isEmpty()) {
            throw new IllegalArgumentException("Event type is required");
        }
        
        if (event.getTimestamp() == null) {
            throw new IllegalArgumentException("Timestamp is required");
        }
    }
    
    private void handleGeoLocationEventError(GeoLocationEvent event, Exception error, 
            String correlationId) {
        
        Map<String, Object> dlqPayload = Map.of(
            "originalEvent", event,
            "error", error.getMessage(),
            "correlationId", correlationId,
            "timestamp", Instant.now()
        );
        
        kafkaTemplate.send("geo-location-events-dlq", dlqPayload);
        
        notificationService.sendOperationalAlert(
            "Geo-Location Event Processing Failed",
            String.format("Failed to process geo-location event for user %s: %s",
                event.getUserId(), error.getMessage()),
            NotificationService.Priority.MEDIUM
        );
        
        metricsService.incrementGeoLocationEventError(event.getEventType());
    }
}