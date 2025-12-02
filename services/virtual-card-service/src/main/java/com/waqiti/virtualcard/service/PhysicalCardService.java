package com.waqiti.virtualcard.service;

import com.waqiti.virtualcard.domain.*;
import com.waqiti.virtualcard.dto.*;
import com.waqiti.virtualcard.repository.*;
import com.waqiti.virtualcard.provider.CardProvider;
import com.waqiti.virtualcard.provider.ShippingProvider;
import com.waqiti.common.events.EventPublisher;
import com.waqiti.common.events.PhysicalCardEvent;
import com.waqiti.common.security.SecurityContext;
import com.waqiti.common.exception.*;
import com.waqiti.common.kyc.service.KYCClientService;
import com.waqiti.common.kyc.annotation.RequireKYCVerification;
import com.waqiti.common.kyc.annotation.RequireKYCVerification.VerificationLevel;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.util.*;
import java.util.concurrent.CompletableFuture;
import java.util.stream.Collectors;

/**
 * Physical Card Service - Manages physical card issuance, shipping, and activation
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class PhysicalCardService {

    private final PhysicalCardRepository physicalCardRepository;
    private final CardOrderRepository orderRepository;
    private final ShippingRepository shippingRepository;
    private final CardDesignRepository designRepository;
    private final CardProvider cardProvider;
    private final ShippingProvider shippingProvider;
    private final AddressValidationService addressValidationService;
    private final FraudDetectionService fraudDetectionService;
    private final NotificationService notificationService;
    private final EventPublisher eventPublisher;
    private final SecurityContext securityContext;
    private final KYCClientService kycClientService;
    
    @Value("${physical-card.production-time-days:5}")
    private int productionTimeDays;
    
    @Value("${physical-card.shipping.standard-days:7}")
    private int standardShippingDays;
    
    @Value("${physical-card.shipping.express-days:3}")
    private int expressShippingDays;
    
    @Value("${physical-card.max-active-cards:3}")
    private int maxActivePhysicalCards;

    /**
     * Order a new physical card
     */
    @Transactional
    @RequireKYCVerification(level = VerificationLevel.ADVANCED, action = "PHYSICAL_CARD")
    public CardOrderDto orderPhysicalCard(OrderCardRequest request) {
        String userId = securityContext.getCurrentUserId();
        
        // Validate request
        validateOrderRequest(request, userId);
        
        // Check active card limit
        long activeCards = physicalCardRepository.countByUserIdAndStatusIn(
            userId, List.of(CardStatus.ACTIVE, CardStatus.ORDERED, CardStatus.SHIPPED)
        );
        if (activeCards >= maxActivePhysicalCards) {
            throw new CardLimitExceededException("Maximum physical card limit reached");
        }
        
        // Validate and standardize address
        ShippingAddress validatedAddress = addressValidationService.validateAddress(
            request.getShippingAddress()
        );
        if (!validatedAddress.isValid()) {
            throw new InvalidAddressException("Invalid shipping address provided");
        }
        
        // Fraud check
        FraudCheckResult fraudCheck = fraudDetectionService.checkCardOrder(userId, request);
        if (fraudCheck.isBlocked()) {
            throw new SecurityException("Order blocked by fraud detection: " + fraudCheck.getReason());
        }
        
        try {
            // Calculate fees
            BigDecimal orderFee = calculateOrderFee(request);
            BigDecimal shippingFee = calculateShippingFee(request.getShippingMethod(), validatedAddress);
            BigDecimal totalFee = orderFee.add(shippingFee);
            
            // Charge user for card and shipping
            if (totalFee.compareTo(BigDecimal.ZERO) > 0) {
                walletService.debit(userId, totalFee, "USD", 
                    "Physical card order fee", 
                    Map.of("orderType", "physical_card", "fees", Map.of(
                        "orderFee", orderFee.toString(),
                        "shippingFee", shippingFee.toString()
                    )));
            }
            
            // Create card order
            CardOrder order = CardOrder.builder()
                .userId(userId)
                .type(CardType.PHYSICAL)
                .brand(request.getBrand() != null ? request.getBrand() : CardBrand.VISA)
                .design(request.getDesign())
                .personalization(request.getPersonalization())
                .shippingAddress(validatedAddress)
                .shippingMethod(request.getShippingMethod())
                .currency(request.getCurrency())
                .status(OrderStatus.PENDING)
                .orderFee(orderFee)
                .shippingFee(shippingFee)
                .totalFee(totalFee)
                .orderedAt(Instant.now())
                .estimatedDelivery(calculateEstimatedDelivery(
                    request.getShippingMethod(), validatedAddress))
                .build();
            
            order = orderRepository.save(order);
            
            // Submit to card provider for production
            CardProviderOrderResponse providerResponse = cardProvider.submitCardOrder(
                CardProviderOrderRequest.builder()
                    .orderId(order.getId())
                    .userId(userId)
                    .type(CardType.PHYSICAL)
                    .brand(order.getBrand())
                    .design(order.getDesign())
                    .personalization(order.getPersonalization())
                    .shippingAddress(validatedAddress)
                    .build()
            );
            
            // Update order with provider details
            order.setProviderOrderId(providerResponse.getProviderOrderId());
            order.setStatus(OrderStatus.SUBMITTED);
            order.setSubmittedAt(Instant.now());
            order = orderRepository.save(order);
            
            // Create physical card record
            PhysicalCard card = PhysicalCard.builder()
                .orderId(order.getId())
                .userId(userId)
                .providerId(providerResponse.getProviderCardId())
                .type(CardType.PHYSICAL)
                .brand(order.getBrand())
                .status(CardStatus.ORDERED)
                .design(order.getDesign())
                .personalization(order.getPersonalization())
                .currency(order.getCurrency())
                .balance(BigDecimal.ZERO)
                .lastFourDigits(providerResponse.getLastFourDigits())
                .expiryMonth(providerResponse.getExpiryMonth())
                .expiryYear(providerResponse.getExpiryYear())
                .orderedAt(Instant.now())
                .estimatedDelivery(order.getEstimatedDelivery())
                .build();
            
            card = physicalCardRepository.save(card);
            
            // Create shipping record
            Shipping shipping = Shipping.builder()
                .orderId(order.getId())
                .cardId(card.getId())
                .method(order.getShippingMethod())
                .address(validatedAddress)
                .status(ShippingStatus.PREPARING)
                .estimatedDelivery(order.getEstimatedDelivery())
                .createdAt(Instant.now())
                .build();
            
            shippingRepository.save(shipping);
            
            // Send order confirmation
            notificationService.sendCardOrderConfirmation(userId, order, card);
            
            // Publish event
            eventPublisher.publish(PhysicalCardEvent.cardOrdered(order, card));
            
            log.info("Physical card ordered for user {}, order ID: {}, card ID: {}", 
                userId, order.getId(), card.getId());
            
            return toOrderDto(order, card);
            
        } catch (Exception e) {
            log.error("Failed to order physical card for user {}", userId, e);
            throw new CardOrderException("Failed to process card order", e);
        }
    }

    /**
     * Activate physical card
     */
    @Transactional
    @RequireKYCVerification(level = VerificationLevel.BASIC, action = "CARD_ACTIVATION")
    public PhysicalCardDto activateCard(String cardId, ActivateCardRequest request) {
        String userId = securityContext.getCurrentUserId();
        PhysicalCard card = getCardByIdAndUser(cardId, userId);
        
        if (card.getStatus() != CardStatus.DELIVERED) {
            throw new IllegalStateException("Card must be delivered before activation");
        }
        
        // Verify activation code/PIN
        boolean activationValid = cardProvider.verifyActivation(
            card.getProviderId(), request.getActivationCode(), request.getPin()
        );
        
        if (!activationValid) {
            // Record failed activation attempt
            recordActivationAttempt(card, false, request.getActivationCode());
            throw new InvalidActivationException("Invalid activation code or PIN");
        }
        
        // Activate card
        card.setStatus(CardStatus.ACTIVE);
        card.setActivatedAt(Instant.now());
        card.setPinSet(true);
        card = physicalCardRepository.save(card);
        
        // Set default limits
        setDefaultPhysicalCardLimits(card.getId());
        
        // Set default controls
        setDefaultPhysicalCardControls(card.getId());
        
        // Activate with provider
        cardProvider.activatePhysicalCard(card.getProviderId());
        
        // Record successful activation
        recordActivationAttempt(card, true, request.getActivationCode());
        
        // Send activation notification
        notificationService.sendCardActivatedNotification(userId, card);
        
        // Publish event
        eventPublisher.publish(PhysicalCardEvent.cardActivated(card));
        
        log.info("Physical card {} activated for user {}", cardId, userId);
        
        return toPhysicalCardDto(card);
    }

    /**
     * Get physical card details
     */
    @Transactional(readOnly = true)
    public PhysicalCardDto getPhysicalCard(String cardId) {
        String userId = securityContext.getCurrentUserId();
        PhysicalCard card = getCardByIdAndUser(cardId, userId);
        
        return toPhysicalCardDto(card);
    }

    /**
     * Get user's physical cards
     */
    @Transactional(readOnly = true)
    public List<PhysicalCardDto> getUserPhysicalCards(String userId) {
        List<PhysicalCard> cards = physicalCardRepository.findByUserIdOrderByCreatedAtDesc(userId);
        return cards.stream()
            .map(this::toPhysicalCardDto)
            .collect(Collectors.toList());
    }

    /**
     * Track card shipment
     */
    @Transactional(readOnly = true)
    public ShippingTrackingDto trackShipment(String cardId) {
        String userId = securityContext.getCurrentUserId();
        PhysicalCard card = getCardByIdAndUser(cardId, userId);
        
        Shipping shipping = shippingRepository.findByCardId(cardId)
            .orElseThrow(() -> new ShippingNotFoundException("Shipping record not found"));
        
        // Get tracking updates from shipping provider
        ShippingTrackingResponse tracking = shippingProvider.getTrackingInfo(
            shipping.getTrackingNumber()
        );
        
        return ShippingTrackingDto.builder()
            .cardId(cardId)
            .orderId(card.getOrderId())
            .trackingNumber(shipping.getTrackingNumber())
            .status(shipping.getStatus())
            .estimatedDelivery(shipping.getEstimatedDelivery())
            .actualDelivery(shipping.getActualDelivery())
            .carrier(shipping.getCarrier())
            .trackingEvents(tracking.getEvents())
            .currentLocation(tracking.getCurrentLocation())
            .build();
    }

    /**
     * Replace damaged or lost card
     */
    @Transactional
    @RequireKYCVerification(level = VerificationLevel.BASIC, action = "CARD_REPLACEMENT")
    public CardOrderDto replaceCard(String cardId, ReplaceCardRequest request) {
        String userId = securityContext.getCurrentUserId();
        PhysicalCard card = getCardByIdAndUser(cardId, userId);
        
        if (card.getStatus() == CardStatus.REPLACED) {
            throw new IllegalStateException("Card has already been replaced");
        }
        
        // Validate replacement reason
        if (!isValidReplacementReason(request.getReason())) {
            throw new IllegalArgumentException("Invalid replacement reason");
        }
        
        // Calculate replacement fee
        BigDecimal replacementFee = calculateReplacementFee(request.getReason());
        
        // Charge replacement fee if applicable
        if (replacementFee.compareTo(BigDecimal.ZERO) > 0) {
            walletService.debit(userId, replacementFee, "USD", 
                "Card replacement fee", 
                Map.of("originalCardId", cardId, "reason", request.getReason().toString()));
        }
        
        // Deactivate original card
        card.setStatus(CardStatus.REPLACED);
        card.setReplacedAt(Instant.now());
        card.setReplacementReason(request.getReason());
        physicalCardRepository.save(card);
        
        // Block original card with provider
        cardProvider.blockCard(card.getProviderId(), "REPLACEMENT");
        
        // Create replacement order
        CardOrder replacementOrder = CardOrder.builder()
            .userId(userId)
            .originalCardId(cardId)
            .type(CardType.PHYSICAL)
            .brand(card.getBrand())
            .design(card.getDesign())
            .personalization(card.getPersonalization())
            .shippingAddress(request.getShippingAddress() != null ? 
                request.getShippingAddress() : getLastShippingAddress(userId))
            .shippingMethod(request.getShippingMethod() != null ? 
                request.getShippingMethod() : ShippingMethod.STANDARD)
            .currency(card.getCurrency())
            .status(OrderStatus.PENDING)
            .orderFee(BigDecimal.ZERO) // No order fee for replacements
            .shippingFee(calculateShippingFee(
                request.getShippingMethod() != null ? 
                    request.getShippingMethod() : ShippingMethod.STANDARD,
                request.getShippingAddress() != null ? 
                    request.getShippingAddress() : getLastShippingAddress(userId)))
            .totalFee(replacementFee)
            .orderedAt(Instant.now())
            .isReplacement(true)
            .replacementReason(request.getReason())
            .build();
        
        replacementOrder = orderRepository.save(replacementOrder);
        
        // Submit replacement to provider
        CardProviderOrderResponse providerResponse = cardProvider.submitReplacementOrder(
            CardProviderReplacementRequest.builder()
                .originalCardProviderId(card.getProviderId())
                .orderId(replacementOrder.getId())
                .reason(request.getReason())
                .rushDelivery(request.isRushDelivery())
                .shippingAddress(replacementOrder.getShippingAddress())
                .build()
        );
        
        // Create new physical card
        PhysicalCard newCard = PhysicalCard.builder()
            .orderId(replacementOrder.getId())
            .originalCardId(cardId)
            .userId(userId)
            .providerId(providerResponse.getProviderCardId())
            .type(CardType.PHYSICAL)
            .brand(card.getBrand())
            .status(CardStatus.ORDERED)
            .design(card.getDesign())
            .personalization(card.getPersonalization())
            .currency(card.getCurrency())
            .balance(card.getBalance()) // Transfer balance
            .lastFourDigits(providerResponse.getLastFourDigits())
            .expiryMonth(providerResponse.getExpiryMonth())
            .expiryYear(providerResponse.getExpiryYear())
            .orderedAt(Instant.now())
            .isReplacement(true)
            .replacementReason(request.getReason())
            .build();
        
        newCard = physicalCardRepository.save(newCard);
        
        // Transfer balance from old card to new card
        transferCardBalance(card, newCard);
        
        // Send replacement notification
        notificationService.sendCardReplacementNotification(userId, card, newCard, request.getReason());
        
        // Publish event
        eventPublisher.publish(PhysicalCardEvent.cardReplaced(card, newCard, request.getReason()));
        
        log.info("Physical card {} replaced with {} for user {}, reason: {}", 
            cardId, newCard.getId(), userId, request.getReason());
        
        return toOrderDto(replacementOrder, newCard);
    }

    /**
     * Report card as lost or stolen
     */
    @Transactional
    public void reportCardLostStolen(String cardId, ReportLostStolenRequest request) {
        String userId = securityContext.getCurrentUserId();
        PhysicalCard card = getCardByIdAndUser(cardId, userId);
        
        if (card.getStatus() == CardStatus.BLOCKED || card.getStatus() == CardStatus.CLOSED) {
            throw new IllegalStateException("Card is already blocked or closed");
        }
        
        // Immediately block card
        card.setStatus(CardStatus.BLOCKED);
        card.setBlockedAt(Instant.now());
        card.setBlockReason(request.getReason().toString());
        physicalCardRepository.save(card);
        
        // Block with provider immediately
        cardProvider.blockCard(card.getProviderId(), request.getReason().toString());
        
        // Create incident report
        CardIncident incident = CardIncident.builder()
            .cardId(cardId)
            .userId(userId)
            .type(request.getReason())
            .description(request.getDescription())
            .location(request.getLocation())
            .reportedAt(Instant.now())
            .policeReportNumber(request.getPoliceReportNumber())
            .status(IncidentStatus.REPORTED)
            .build();
        
        incidentRepository.save(incident);
        
        // Send emergency notification
        notificationService.sendEmergencyCardBlockNotification(userId, card, request.getReason());
        
        // Alert fraud team
        notificationService.sendFraudTeamAlert(card, incident);
        
        // Publish security event
        eventPublisher.publish(PhysicalCardEvent.cardReportedLostStolen(card, incident));
        
        log.warn("Physical card {} reported {} by user {}", cardId, request.getReason(), userId);
    }

    /**
     * Get available card designs
     */
    @Transactional(readOnly = true)
    public List<CardDesignDto> getAvailableDesigns() {
        List<CardDesign> designs = designRepository.findByActiveTrue();
        return designs.stream()
            .map(this::toDesignDto)
            .collect(Collectors.toList());
    }

    /**
     * Webhook to update shipping status
     */
    @Transactional
    public void updateShippingStatus(ShippingUpdateWebhook webhook) {
        Shipping shipping = shippingRepository.findByTrackingNumber(webhook.getTrackingNumber())
            .orElseThrow(() -> new ShippingNotFoundException("Shipping record not found"));
        
        PhysicalCard card = physicalCardRepository.findById(shipping.getCardId())
            .orElseThrow(() -> new CardNotFoundException("Card not found"));
        
        // Update shipping status
        shipping.setStatus(webhook.getStatus());
        shipping.setCurrentLocation(webhook.getCurrentLocation());
        shipping.setLastUpdate(Instant.now());
        
        if (webhook.getStatus() == ShippingStatus.DELIVERED) {
            shipping.setActualDelivery(webhook.getDeliveredAt());
            card.setStatus(CardStatus.DELIVERED);
            card.setDeliveredAt(webhook.getDeliveredAt());
        }
        
        shippingRepository.save(shipping);
        physicalCardRepository.save(card);
        
        // Send notification to user
        notificationService.sendShippingUpdate(card.getUserId(), card, shipping);
        
        // Publish event
        eventPublisher.publish(PhysicalCardEvent.shippingStatusUpdated(card, shipping));
        
        log.info("Updated shipping status for card {} to {}", card.getId(), webhook.getStatus());
    }

    /**
     * Scheduled job to check card production status
     */
    @Scheduled(fixedRate = 3600000) // Every hour
    @Transactional
    public void checkProductionStatus() {
        List<PhysicalCard> cardsInProduction = physicalCardRepository.findByStatusIn(
            List.of(CardStatus.ORDERED, CardStatus.IN_PRODUCTION)
        );
        
        for (PhysicalCard card : cardsInProduction) {
            try {
                CardProductionStatus status = cardProvider.getProductionStatus(card.getProviderId());
                
                if (status.getStatus() != card.getStatus()) {
                    card.setStatus(status.getStatus());
                    card.setProductionUpdatedAt(Instant.now());
                    
                    if (status.getStatus() == CardStatus.SHIPPED && status.getTrackingNumber() != null) {
                        // Update shipping with tracking number
                        Shipping shipping = shippingRepository.findByCardId(card.getId())
                            .orElseThrow(() -> new ShippingNotFoundException("Shipping record not found"));
                        
                        shipping.setTrackingNumber(status.getTrackingNumber());
                        shipping.setCarrier(status.getCarrier());
                        shipping.setStatus(ShippingStatus.SHIPPED);
                        shipping.setShippedAt(Instant.now());
                        shippingRepository.save(shipping);
                        
                        // Notify user
                        notificationService.sendCardShippedNotification(card.getUserId(), card, shipping);
                    }
                    
                    physicalCardRepository.save(card);
                    
                    log.info("Updated production status for card {} to {}", card.getId(), status.getStatus());
                }
            } catch (Exception e) {
                log.error("Failed to check production status for card {}", card.getId(), e);
            }
        }
    }

    private void validateOrderRequest(OrderCardRequest request, String userId) {
        // KYC verification is now handled by @RequireKYCVerification annotation
        
        // Validate shipping address
        if (request.getShippingAddress() == null) {
            throw new IllegalArgumentException("Shipping address is required");
        }
        
        // Validate currency
        if (!isSupportedCurrency(request.getCurrency())) {
            throw new IllegalArgumentException("Unsupported currency: " + request.getCurrency());
        }
        
        // Check if user has existing orders in progress
        long pendingOrders = orderRepository.countByUserIdAndStatusIn(
            userId, List.of(OrderStatus.PENDING, OrderStatus.SUBMITTED, OrderStatus.IN_PRODUCTION)
        );
        if (pendingOrders > 0) {
            throw new IllegalStateException("You have pending card orders. Please wait for completion.");
        }
    }

    private BigDecimal calculateOrderFee(OrderCardRequest request) {
        // Standard card is free, premium designs may have fees
        if (request.getDesign() != null && request.getDesign().isPremium()) {
            return request.getDesign().getFee();
        }
        return BigDecimal.ZERO;
    }

    private BigDecimal calculateShippingFee(ShippingMethod method, ShippingAddress address) {
        BigDecimal baseFee = BigDecimal.ZERO;
        
        switch (method) {
            case STANDARD:
                baseFee = BigDecimal.ZERO; // Free standard shipping
                break;
            case EXPRESS:
                baseFee = BigDecimal.valueOf(15.00);
                break;
            case OVERNIGHT:
                baseFee = BigDecimal.valueOf(35.00);
                break;
        }
        
        // International shipping surcharge
        if (!address.getCountry().equals("US")) {
            baseFee = baseFee.add(BigDecimal.valueOf(25.00));
        }
        
        return baseFee;
    }

    private BigDecimal calculateReplacementFee(ReplacementReason reason) {
        switch (reason) {
            case LOST:
            case STOLEN:
                return BigDecimal.valueOf(25.00);
            case DAMAGED:
                return BigDecimal.valueOf(10.00);
            case DEFECTIVE:
                return BigDecimal.ZERO; // No charge for defective cards
            case NAME_CHANGE:
                return BigDecimal.valueOf(15.00);
            default:
                return BigDecimal.valueOf(25.00);
        }
    }

    private Instant calculateEstimatedDelivery(ShippingMethod method, ShippingAddress address) {
        int businessDays = productionTimeDays;
        
        switch (method) {
            case STANDARD:
                businessDays += standardShippingDays;
                break;
            case EXPRESS:
                businessDays += expressShippingDays;
                break;
            case OVERNIGHT:
                businessDays += 1;
                break;
        }
        
        // International shipping adds time
        if (!address.getCountry().equals("US")) {
            businessDays += 7;
        }
        
        return Instant.now().plus(Duration.ofDays(businessDays));
    }

    private boolean isValidReplacementReason(ReplacementReason reason) {
        return reason != null && Set.of(
            ReplacementReason.LOST,
            ReplacementReason.STOLEN,
            ReplacementReason.DAMAGED,
            ReplacementReason.DEFECTIVE,
            ReplacementReason.NAME_CHANGE
        ).contains(reason);
    }

    private boolean isUserFullyVerified(String userId) {
        // Use KYC client service to check verification status
        return kycClientService.isUserAdvancedVerified(userId);
    }

    private boolean isSupportedCurrency(String currency) {
        return Set.of("USD", "EUR", "GBP", "CAD", "AUD").contains(currency);
    }

    private void setDefaultPhysicalCardLimits(String cardId) {
        // Physical cards have higher limits than virtual cards
        CardLimits limits = CardLimits.builder()
            .cardId(cardId)
            .dailyLimit(new BigDecimal("5000"))
            .weeklyLimit(new BigDecimal("15000"))
            .monthlyLimit(new BigDecimal("50000"))
            .transactionLimit(new BigDecimal("2500"))
            .atmLimit(new BigDecimal("1000"))
            .onlineLimit(new BigDecimal("5000"))
            .internationalLimit(new BigDecimal("2000"))
            .build();
        
        limitRepository.save(limits);
    }

    private void setDefaultPhysicalCardControls(String cardId) {
        CardControls controls = CardControls.builder()
            .cardId(cardId)
            .onlineTransactions(true)
            .internationalTransactions(true)
            .atmWithdrawals(true)
            .contactlessPayments(true)
            .chipAndPin(true)
            .magneticStripe(true)
            .allowedMerchantCategories(new HashSet<>())
            .blockedMerchantCategories(Set.of("7995")) // Gambling
            .allowedCountries(new HashSet<>())
            .blockedCountries(new HashSet<>())
            .build();
        
        controlRepository.save(controls);
    }

    private void recordActivationAttempt(PhysicalCard card, boolean successful, String activationCode) {
        CardActivationAttempt attempt = CardActivationAttempt.builder()
            .cardId(card.getId())
            .userId(card.getUserId())
            .activationCode(activationCode)
            .successful(successful)
            .attemptedAt(Instant.now())
            .ipAddress(securityContext.getClientIpAddress())
            .userAgent(securityContext.getUserAgent())
            .build();
        
        activationAttemptRepository.save(attempt);
    }

    private void transferCardBalance(PhysicalCard fromCard, PhysicalCard toCard) {
        if (fromCard.getBalance().compareTo(BigDecimal.ZERO) > 0) {
            toCard.setBalance(fromCard.getBalance());
            fromCard.setBalance(BigDecimal.ZERO);
            
            physicalCardRepository.save(fromCard);
            physicalCardRepository.save(toCard);
            
            // Update balances with provider
            cardProvider.updateCardBalance(fromCard.getProviderId(), BigDecimal.ZERO);
            cardProvider.updateCardBalance(toCard.getProviderId(), toCard.getBalance());
        }
    }

    private ShippingAddress getLastShippingAddress(String userId) {
        return orderRepository.findFirstByUserIdOrderByOrderedAtDesc(userId)
            .map(CardOrder::getShippingAddress)
            .orElseThrow(() -> new IllegalStateException("No previous shipping address found"));
    }

    private PhysicalCard getCardByIdAndUser(String cardId, String userId) {
        return physicalCardRepository.findByIdAndUserId(cardId, userId)
            .orElseThrow(() -> new CardNotFoundException("Physical card not found"));
    }

    // DTO conversion methods
    private CardOrderDto toOrderDto(CardOrder order, PhysicalCard card) {
        return CardOrderDto.builder()
            .id(order.getId())
            .cardId(card.getId())
            .type(order.getType())
            .brand(order.getBrand())
            .design(order.getDesign())
            .status(order.getStatus())
            .shippingMethod(order.getShippingMethod())
            .orderFee(order.getOrderFee())
            .shippingFee(order.getShippingFee())
            .totalFee(order.getTotalFee())
            .orderedAt(order.getOrderedAt())
            .estimatedDelivery(order.getEstimatedDelivery())
            .isReplacement(order.isReplacement())
            .build();
    }

    private PhysicalCardDto toPhysicalCardDto(PhysicalCard card) {
        return PhysicalCardDto.builder()
            .id(card.getId())
            .orderId(card.getOrderId())
            .type(card.getType())
            .brand(card.getBrand())
            .status(card.getStatus())
            .design(card.getDesign())
            .personalization(card.getPersonalization())
            .currency(card.getCurrency())
            .balance(card.getBalance())
            .lastFourDigits(card.getLastFourDigits())
            .expiryMonth(card.getExpiryMonth())
            .expiryYear(card.getExpiryYear())
            .orderedAt(card.getOrderedAt())
            .deliveredAt(card.getDeliveredAt())
            .activatedAt(card.getActivatedAt())
            .estimatedDelivery(card.getEstimatedDelivery())
            .isReplacement(card.isReplacement())
            .pinSet(card.isPinSet())
            .build();
    }

    private CardDesignDto toDesignDto(CardDesign design) {
        return CardDesignDto.builder()
            .id(design.getId())
            .name(design.getName())
            .description(design.getDescription())
            .imageUrl(design.getImageUrl())
            .isPremium(design.isPremium())
            .fee(design.getFee())
            .active(design.isActive())
            .build();
    }
}