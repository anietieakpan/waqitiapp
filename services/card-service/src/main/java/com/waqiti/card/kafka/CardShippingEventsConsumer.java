package com.waqiti.card.kafka;

import com.waqiti.common.events.CardShippingEvent;
import com.waqiti.card.domain.CardShipment;
import com.waqiti.card.domain.Card;
import com.waqiti.card.repository.CardShipmentRepository;
import com.waqiti.card.repository.CardRepository;
import com.waqiti.card.service.ShippingService;
import com.waqiti.card.service.CarrierIntegrationService;
import com.waqiti.card.service.AddressVerificationService;
import com.waqiti.card.metrics.CardMetricsService;
import com.waqiti.common.audit.AuditService;
import com.waqiti.common.notification.NotificationService;
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
import org.springframework.transaction.annotation.Isolation;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.time.LocalDate;
import java.time.Instant;
import java.util.*;

@Component
@Slf4j
@RequiredArgsConstructor
public class CardShippingEventsConsumer {
    
    private final CardShipmentRepository shipmentRepository;
    private final CardRepository cardRepository;
    private final ShippingService shippingService;
    private final CarrierIntegrationService carrierService;
    private final AddressVerificationService addressVerificationService;
    private final CardMetricsService metricsService;
    private final AuditService auditService;
    private final NotificationService notificationService;
    private final KafkaTemplate<String, Object> kafkaTemplate;
    
    private static final BigDecimal STANDARD_SHIPPING_FEE = BigDecimal.ZERO;
    private static final BigDecimal EXPRESS_SHIPPING_FEE = new BigDecimal("25.00");
    private static final BigDecimal OVERNIGHT_SHIPPING_FEE = new BigDecimal("50.00");
    
    @KafkaListener(
        topics = {"card-shipping-events", "card-delivery-events", "card-logistics-events"},
        groupId = "card-shipping-service-group",
        containerFactory = "criticalFinancialKafkaListenerContainerFactory",
        concurrency = "4"
    )
    @RetryableTopic(
        attempts = "4",
        backoff = @Backoff(delay = 2000, multiplier = 2.0, maxDelay = 20000),
        dltStrategy = org.springframework.kafka.retrytopic.DltStrategy.FAIL_ON_ERROR,
        topicSuffixingStrategy = TopicSuffixingStrategy.SUFFIX_WITH_INDEX_VALUE
    )
    @Transactional(isolation = Isolation.SERIALIZABLE, timeout = 120)
    public void handleCardShippingEvent(
            @Payload CardShippingEvent event,
            @Header(KafkaHeaders.RECEIVED_PARTITION) int partition,
            @Header(KafkaHeaders.OFFSET) long offset,
            Acknowledgment acknowledgment) {
        
        String correlationId = String.format("card-shipping-%s-p%d-o%d", 
            event.getCardId(), partition, offset);
        
        log.info("Processing card shipping event: cardId={}, type={}, shippingMethod={}", 
            event.getCardId(), event.getEventType(), event.getShippingMethod());
        
        try {
            switch (event.getEventType()) {
                case SHIPPING_REQUESTED:
                    processShippingRequested(event, correlationId);
                    break;
                case ADDRESS_VERIFIED:
                    processAddressVerified(event, correlationId);
                    break;
                case SHIPPING_LABEL_GENERATED:
                    processShippingLabelGenerated(event, correlationId);
                    break;
                case CARD_PACKAGED:
                    processCardPackaged(event, correlationId);
                    break;
                case SHIPPED:
                    processShipped(event, correlationId);
                    break;
                case IN_TRANSIT:
                    processInTransit(event, correlationId);
                    break;
                case OUT_FOR_DELIVERY:
                    processOutForDelivery(event, correlationId);
                    break;
                case DELIVERED:
                    processDelivered(event, correlationId);
                    break;
                case DELIVERY_FAILED:
                    processDeliveryFailed(event, correlationId);
                    break;
                case RETURN_TO_SENDER:
                    processReturnToSender(event, correlationId);
                    break;
                case DELIVERY_RESCHEDULED:
                    processDeliveryRescheduled(event, correlationId);
                    break;
                default:
                    log.warn("Unknown card shipping event type: {}", event.getEventType());
                    break;
            }
            
            auditService.logCardEvent(
                "CARD_SHIPPING_EVENT_PROCESSED",
                event.getCardId(),
                Map.of(
                    "eventType", event.getEventType(),
                    "userId", event.getUserId(),
                    "shipmentId", event.getShipmentId() != null ? event.getShipmentId() : "N/A",
                    "trackingNumber", event.getTrackingNumber() != null ? event.getTrackingNumber() : "N/A",
                    "correlationId", correlationId,
                    "timestamp", Instant.now()
                )
            );
            
            acknowledgment.acknowledge();
            
        } catch (Exception e) {
            log.error("Failed to process card shipping event: {}", e.getMessage(), e);
            kafkaTemplate.send("card-shipping-events-dlq", Map.of(
                "originalEvent", event,
                "error", e.getMessage(),
                "correlationId", correlationId,
                "timestamp", Instant.now()
            ));
            acknowledgment.acknowledge();
        }
    }
    
    private void processShippingRequested(CardShippingEvent event, String correlationId) {
        log.info("Card shipping requested: cardId={}, userId={}, method={}, address={}", 
            event.getCardId(), event.getUserId(), event.getShippingMethod(), event.getShippingAddress());
        
        Card card = cardRepository.findById(event.getCardId())
            .orElseThrow();
        
        BigDecimal shippingFee = calculateShippingFee(event.getShippingMethod());
        LocalDate estimatedDeliveryDate = calculateEstimatedDeliveryDate(
            event.getShippingMethod(), event.getShippingAddress().getCountry());
        
        CardShipment shipment = CardShipment.builder()
            .id(UUID.randomUUID().toString())
            .cardId(event.getCardId())
            .userId(event.getUserId())
            .shippingMethod(event.getShippingMethod())
            .shippingAddress(event.getShippingAddress())
            .shippingFee(shippingFee)
            .estimatedDeliveryDate(estimatedDeliveryDate)
            .requestedAt(LocalDateTime.now())
            .status("REQUESTED")
            .correlationId(correlationId)
            .build();
        
        shipmentRepository.save(shipment);
        
        addressVerificationService.verifyAddress(shipment.getId());
        
        metricsService.recordShippingRequested(event.getShippingMethod());
    }
    
    private void processAddressVerified(CardShippingEvent event, String correlationId) {
        log.info("Address verified: shipmentId={}, verified={}, corrections={}", 
            event.getShipmentId(), event.isAddressValid(), event.getAddressCorrections());
        
        CardShipment shipment = shipmentRepository.findById(event.getShipmentId())
            .orElseThrow();
        
        if (!event.isAddressValid()) {
            log.error("Address verification failed: shipmentId={}, issues={}", 
                event.getShipmentId(), event.getAddressValidationIssues());
            
            shipment.setStatus("ADDRESS_INVALID");
            shipment.setAddressValidationIssues(event.getAddressValidationIssues());
            shipmentRepository.save(shipment);
            
            notificationService.sendNotification(
                event.getUserId(),
                "Card Shipping - Address Issue",
                String.format("There's an issue with your shipping address: %s. " +
                    "Please update it to receive your card.",
                    String.join(", ", event.getAddressValidationIssues())),
                correlationId
            );
            return;
        }
        
        if (event.getAddressCorrections() != null && !event.getAddressCorrections().isEmpty()) {
            shipment.setShippingAddress(event.getCorrectedAddress());
            log.info("Address corrected: shipmentId={}, corrections={}", 
                event.getShipmentId(), event.getAddressCorrections());
        }
        
        shipment.setAddressVerifiedAt(LocalDateTime.now());
        shipment.setAddressValid(true);
        shipment.setStatus("ADDRESS_VERIFIED");
        shipmentRepository.save(shipment);
        
        shippingService.generateShippingLabel(shipment.getId());
        
        metricsService.recordAddressVerified(event.isAddressValid());
    }
    
    private void processShippingLabelGenerated(CardShippingEvent event, String correlationId) {
        log.info("Shipping label generated: shipmentId={}, trackingNumber={}, carrier={}", 
            event.getShipmentId(), event.getTrackingNumber(), event.getCarrier());
        
        CardShipment shipment = shipmentRepository.findById(event.getShipmentId())
            .orElseThrow();
        
        shipment.setTrackingNumber(event.getTrackingNumber());
        shipment.setCarrier(event.getCarrier());
        shipment.setShippingLabelUrl(event.getShippingLabelUrl());
        shipment.setLabelGeneratedAt(LocalDateTime.now());
        shipment.setStatus("LABEL_GENERATED");
        shipmentRepository.save(shipment);
        
        shippingService.schedulePackaging(shipment.getId());
        
        metricsService.recordShippingLabelGenerated(event.getCarrier());
    }
    
    private void processCardPackaged(CardShippingEvent event, String correlationId) {
        log.info("Card packaged: shipmentId={}, packageId={}, weight={}", 
            event.getShipmentId(), event.getPackageId(), event.getPackageWeight());
        
        CardShipment shipment = shipmentRepository.findById(event.getShipmentId())
            .orElseThrow();
        
        shipment.setPackageId(event.getPackageId());
        shipment.setPackageWeight(event.getPackageWeight());
        shipment.setPackagedAt(LocalDateTime.now());
        shipment.setPackagedBy(event.getPackagedBy());
        shipment.setStatus("PACKAGED");
        shipmentRepository.save(shipment);
        
        carrierService.handoverToCarrier(shipment.getId());
        
        metricsService.recordCardPackaged();
    }
    
    private void processShipped(CardShippingEvent event, String correlationId) {
        log.info("Card shipped: shipmentId={}, trackingNumber={}, shippedAt={}", 
            event.getShipmentId(), event.getTrackingNumber(), event.getShippedAt());
        
        CardShipment shipment = shipmentRepository.findById(event.getShipmentId())
            .orElseThrow();
        
        shipment.setShippedAt(event.getShippedAt());
        shipment.setStatus("SHIPPED");
        shipmentRepository.save(shipment);
        
        Card card = cardRepository.findById(shipment.getCardId())
            .orElseThrow();
        
        card.setShippingStatus("SHIPPED");
        card.setShippedAt(event.getShippedAt());
        cardRepository.save(card);
        
        notificationService.sendNotification(
            event.getUserId(),
            "Card Shipped",
            String.format("Your card has been shipped via %s %s. " +
                "Tracking number: %s. Estimated delivery: %s. Track at: %s",
                shipment.getCarrier(), shipment.getShippingMethod(),
                event.getTrackingNumber(), shipment.getEstimatedDeliveryDate(),
                carrierService.getTrackingUrl(shipment.getCarrier(), event.getTrackingNumber())),
            correlationId
        );
        
        metricsService.recordCardShipped(shipment.getShippingMethod(), shipment.getCarrier());
    }
    
    private void processInTransit(CardShippingEvent event, String correlationId) {
        log.info("Card in transit: shipmentId={}, currentLocation={}, nextStop={}", 
            event.getShipmentId(), event.getCurrentLocation(), event.getNextStop());
        
        CardShipment shipment = shipmentRepository.findById(event.getShipmentId())
            .orElseThrow();
        
        shipment.setCurrentLocation(event.getCurrentLocation());
        shipment.setNextStop(event.getNextStop());
        shipment.setLastTransitUpdate(LocalDateTime.now());
        shipment.setStatus("IN_TRANSIT");
        shipmentRepository.save(shipment);
        
        if (event.isTransitDelayed()) {
            LocalDate newEstimatedDelivery = shipment.getEstimatedDeliveryDate().plusDays(
                event.getDelayDays());
            shipment.setEstimatedDeliveryDate(newEstimatedDelivery);
            shipmentRepository.save(shipment);
            
            notificationService.sendNotification(
                event.getUserId(),
                "Card Delivery Update",
                String.format("Your card delivery has been delayed by %d days due to: %s. " +
                    "New estimated delivery: %s",
                    event.getDelayDays(), event.getDelayReason(), newEstimatedDelivery),
                correlationId
            );
        }
        
        metricsService.recordInTransit(event.getCurrentLocation());
    }
    
    private void processOutForDelivery(CardShippingEvent event, String correlationId) {
        log.info("Card out for delivery: shipmentId={}, deliveryDate={}, deliveryWindow={}", 
            event.getShipmentId(), event.getDeliveryDate(), event.getDeliveryWindow());
        
        CardShipment shipment = shipmentRepository.findById(event.getShipmentId())
            .orElseThrow();
        
        shipment.setDeliveryDate(event.getDeliveryDate());
        shipment.setDeliveryWindow(event.getDeliveryWindow());
        shipment.setOutForDeliveryAt(LocalDateTime.now());
        shipment.setStatus("OUT_FOR_DELIVERY");
        shipmentRepository.save(shipment);
        
        notificationService.sendNotification(
            event.getUserId(),
            "Card Out for Delivery",
            String.format("Your card is out for delivery today! Delivery window: %s. " +
                "Please be available to receive and sign for the package.",
                event.getDeliveryWindow()),
            correlationId
        );
        
        metricsService.recordOutForDelivery();
    }
    
    private void processDelivered(CardShippingEvent event, String correlationId) {
        log.info("Card delivered: shipmentId={}, deliveredAt={}, signedBy={}", 
            event.getShipmentId(), event.getDeliveredAt(), event.getSignedBy());
        
        CardShipment shipment = shipmentRepository.findById(event.getShipmentId())
            .orElseThrow();
        
        shipment.setDeliveredAt(event.getDeliveredAt());
        shipment.setSignedBy(event.getSignedBy());
        shipment.setDeliveryProofUrl(event.getDeliveryProofUrl());
        shipment.setStatus("DELIVERED");
        shipmentRepository.save(shipment);
        
        Card card = cardRepository.findById(shipment.getCardId())
            .orElseThrow();
        
        card.setShippingStatus("DELIVERED");
        card.setDeliveredAt(event.getDeliveredAt());
        cardRepository.save(card);
        
        long transitDays = java.time.Duration.between(
            shipment.getShippedAt(), event.getDeliveredAt()).toDays();
        
        notificationService.sendNotification(
            event.getUserId(),
            "Card Delivered",
            String.format("Your card has been delivered and signed for by %s. " +
                "Please activate it in the app to start using it. " +
                "Delivery proof: %s",
                event.getSignedBy(), event.getDeliveryProofUrl()),
            correlationId
        );
        
        metricsService.recordCardDelivered(
            shipment.getShippingMethod(), 
            shipment.getCarrier(), 
            transitDays
        );
    }
    
    private void processDeliveryFailed(CardShippingEvent event, String correlationId) {
        log.error("Card delivery failed: shipmentId={}, failureReason={}, attemptNumber={}", 
            event.getShipmentId(), event.getDeliveryFailureReason(), event.getDeliveryAttempt());
        
        CardShipment shipment = shipmentRepository.findById(event.getShipmentId())
            .orElseThrow();
        
        shipment.setDeliveryAttempts(event.getDeliveryAttempt());
        shipment.setLastDeliveryAttempt(LocalDateTime.now());
        shipment.setDeliveryFailureReason(event.getDeliveryFailureReason());
        shipment.setStatus("DELIVERY_FAILED");
        shipmentRepository.save(shipment);
        
        if (event.getDeliveryAttempt() >= 3) {
            log.warn("Maximum delivery attempts reached: shipmentId={}", event.getShipmentId());
            shipment.setStatus("RETURN_TO_SENDER");
            shipmentRepository.save(shipment);
            
            notificationService.sendNotification(
                event.getUserId(),
                "Card Delivery - Action Required",
                String.format("We were unable to deliver your card after %d attempts. Reason: %s. " +
                    "The package is being returned. Please contact support to arrange redelivery.",
                    event.getDeliveryAttempt(), event.getDeliveryFailureReason()),
                correlationId
            );
        } else {
            notificationService.sendNotification(
                event.getUserId(),
                "Card Delivery Attempt Failed",
                String.format("Delivery attempt %d failed. Reason: %s. " +
                    "We'll try again on %s. Please ensure someone is available to receive the package.",
                    event.getDeliveryAttempt(), event.getDeliveryFailureReason(), 
                    event.getNextDeliveryAttempt()),
                correlationId
            );
        }
        
        metricsService.recordDeliveryFailed(event.getDeliveryFailureReason(), event.getDeliveryAttempt());
    }
    
    private void processReturnToSender(CardShippingEvent event, String correlationId) {
        log.warn("Card returning to sender: shipmentId={}, reason={}", 
            event.getShipmentId(), event.getReturnReason());
        
        CardShipment shipment = shipmentRepository.findById(event.getShipmentId())
            .orElseThrow();
        
        shipment.setReturnedAt(LocalDateTime.now());
        shipment.setReturnReason(event.getReturnReason());
        shipment.setStatus("RETURNED");
        shipmentRepository.save(shipment);
        
        Card card = cardRepository.findById(shipment.getCardId())
            .orElseThrow();
        
        card.setShippingStatus("RETURNED");
        cardRepository.save(card);
        
        notificationService.sendNotification(
            event.getUserId(),
            "Card Shipment Returned",
            String.format("Your card shipment has been returned to us. Reason: %s. " +
                "Please update your address and contact support to arrange redelivery.",
                event.getReturnReason()),
            correlationId
        );
        
        metricsService.recordReturnToSender(event.getReturnReason());
    }
    
    private void processDeliveryRescheduled(CardShippingEvent event, String correlationId) {
        log.info("Delivery rescheduled: shipmentId={}, oldDate={}, newDate={}, reason={}", 
            event.getShipmentId(), event.getOldDeliveryDate(), 
            event.getNewDeliveryDate(), event.getRescheduleReason());
        
        CardShipment shipment = shipmentRepository.findById(event.getShipmentId())
            .orElseThrow();
        
        shipment.setEstimatedDeliveryDate(event.getNewDeliveryDate());
        shipment.setRescheduledAt(LocalDateTime.now());
        shipment.setRescheduleReason(event.getRescheduleReason());
        shipmentRepository.save(shipment);
        
        notificationService.sendNotification(
            event.getUserId(),
            "Card Delivery Rescheduled",
            String.format("Your card delivery has been rescheduled from %s to %s. Reason: %s",
                event.getOldDeliveryDate(), event.getNewDeliveryDate(), event.getRescheduleReason()),
            correlationId
        );
        
        metricsService.recordDeliveryRescheduled(event.getRescheduleReason());
    }
    
    private BigDecimal calculateShippingFee(String shippingMethod) {
        return switch (shippingMethod) {
            case "STANDARD" -> STANDARD_SHIPPING_FEE;
            case "EXPRESS" -> EXPRESS_SHIPPING_FEE;
            case "OVERNIGHT" -> OVERNIGHT_SHIPPING_FEE;
            default -> STANDARD_SHIPPING_FEE;
        };
    }
    
    private LocalDate calculateEstimatedDeliveryDate(String shippingMethod, String country) {
        LocalDate today = LocalDate.now();
        
        if ("DOMESTIC".equals(country) || "USA".equals(country)) {
            return switch (shippingMethod) {
                case "STANDARD" -> today.plusDays(7);
                case "EXPRESS" -> today.plusDays(3);
                case "OVERNIGHT" -> today.plusDays(1);
                default -> today.plusDays(7);
            };
        } else {
            return switch (shippingMethod) {
                case "STANDARD" -> today.plusDays(14);
                case "EXPRESS" -> today.plusDays(7);
                case "OVERNIGHT" -> today.plusDays(3);
                default -> today.plusDays(14);
            };
        }
    }
}