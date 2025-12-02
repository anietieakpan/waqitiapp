package com.waqiti.payment.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.waqiti.payment.client.UserServiceClient;
import com.waqiti.payment.client.UnifiedWalletServiceClient;
import com.waqiti.payment.client.dto.TransferRequest;
import com.waqiti.payment.client.dto.TransferResponse;
import com.waqiti.payment.client.dto.UserResponse;
import com.waqiti.payment.client.dto.WalletResponse;
import com.waqiti.payment.domain.*;
import com.waqiti.payment.dto.*;
import com.waqiti.payment.domain.*;
import com.waqiti.payment.dto.*;
import com.waqiti.payment.repository.SplitPaymentParticipantRepository;
import com.waqiti.payment.repository.SplitPaymentRepository;
import io.github.resilience4j.circuitbreaker.annotation.CircuitBreaker;
import io.github.resilience4j.retry.annotation.Retry;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import com.waqiti.common.exception.InsufficientFundsException;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDateTime;
import java.util.*;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * Service for managing split payments
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class SplitPaymentService {
    private final SplitPaymentRepository splitPaymentRepository;
    private final SplitPaymentParticipantRepository participantRepository;
    private final UnifiedWalletServiceClient walletClient;
    private final UserServiceClient userClient;
    private final KafkaTemplate<String, String> kafkaTemplate;
    private final ObjectMapper objectMapper;

    private static final String SPLIT_PAYMENT_EVENTS_TOPIC = "split-payment-events";
    private static final int DEFAULT_EXPIRY_DAYS = 30;
    private static final BigDecimal MAX_PAYMENT_AMOUNT = new BigDecimal("10000");
    private static final int MAX_PARTICIPANTS = 20;

    /**
     * Creates a split payment with validation
     */
    @Transactional
    public SplitPaymentResponse createSplitPayment(UUID organizerId, CreateSplitPaymentRequest request) {
        log.info("Creating split payment by user: {}", organizerId);

        // Validate title length
        if (request.getTitle() == null || request.getTitle().trim().isEmpty()) {
            throw new IllegalArgumentException("Title is required");
        }
        if (request.getTitle().length() > 100) {
            throw new IllegalArgumentException("Title cannot exceed 100 characters");
        }

        // Validate description length
        if (request.getDescription() != null && request.getDescription().length() > 500) {
            throw new IllegalArgumentException("Description cannot exceed 500 characters");
        }

        // Validate amount
        if (request.getTotalAmount().compareTo(BigDecimal.ZERO) <= 0) {
            throw new IllegalArgumentException("Total amount must be greater than zero");
        }

        if (request.getTotalAmount().compareTo(MAX_PAYMENT_AMOUNT) > 0) {
            throw new PaymentLimitExceededException("Total amount exceeds maximum allowed: " + MAX_PAYMENT_AMOUNT);
        }

        // Validate participants
        if (request.getParticipants() == null || request.getParticipants().isEmpty()) {
            throw new IllegalArgumentException("At least one participant is required");
        }

        if (request.getParticipants().size() > MAX_PARTICIPANTS) {
            throw new IllegalArgumentException("Number of participants cannot exceed " + MAX_PARTICIPANTS);
        }

        // Validate participant details and total
        Set<UUID> participantIds = new HashSet<>();
        BigDecimal totalParticipantAmount = BigDecimal.ZERO;

        for (SplitPaymentParticipantRequest participant : request.getParticipants()) {
            // Validate participant ID
            if (participant.getUserId() == null) {
                throw new IllegalArgumentException("Participant user ID is required");
            }

            // Validate no duplicate participants
            if (!participantIds.add(participant.getUserId())) {
                throw new IllegalArgumentException("Duplicate participant: " + participant.getUserId());
            }

            // Check if organizer is a participant
            if (participant.getUserId().equals(organizerId)) {
                throw new IllegalArgumentException("Organizer cannot be a participant");
            }

            // Validate participant amount
            if (participant.getAmount().compareTo(BigDecimal.ZERO) <= 0) {
                throw new IllegalArgumentException(
                        "Participant amount must be greater than zero: " + participant.getUserId());
            }

            totalParticipantAmount = totalParticipantAmount.add(participant.getAmount());
        }

        // Validate total amount matches sum of participant amounts
        if (request.getTotalAmount().compareTo(totalParticipantAmount) != 0) {
            throw new IllegalArgumentException(
                    "Total amount (" + request.getTotalAmount() +
                            ") must equal sum of all participant amounts (" + totalParticipantAmount + ")");
        }

        // Validate all participants exist
        try {
            List<UserResponse> users = userClient.getUsers(new ArrayList<>(participantIds));
            Set<UUID> foundUserIds = users.stream()
                    .map(UserResponse::getId)
                    .collect(Collectors.toSet());

            participantIds.removeAll(foundUserIds);
            if (!participantIds.isEmpty()) {
                throw new IllegalArgumentException("Invalid participant user IDs: " + participantIds);
            }
        } catch (Exception e) {
            log.error("Error validating participant users", e);
            throw new IllegalArgumentException("Unable to validate participants: " + e.getMessage());
        }

        // Create the split payment
        SplitPayment splitPayment = SplitPayment.create(
                organizerId,
                request.getTitle(),
                request.getDescription(),
                request.getTotalAmount(),
                request.getCurrency(),
                request.getExpiryDays() != null ? request.getExpiryDays() : DEFAULT_EXPIRY_DAYS
        );

        splitPayment = splitPaymentRepository.save(splitPayment);

        // Add participants
        for (SplitPaymentParticipantRequest participantRequest : request.getParticipants()) {
            // Skip if participant is the organizer (already validated above)
            splitPayment.addParticipant(
                    participantRequest.getUserId(),
                    participantRequest.getAmount()
            );
        }

        splitPayment = splitPaymentRepository.save(splitPayment);

        // Publish event for notification
        publishSplitPaymentCreatedEvent(splitPayment);

        return enrichWithUserInfo(mapToSplitPaymentResponse(splitPayment));
    }

    /**
     * Gets a split payment by ID
     */
    @Transactional(readOnly = true)
    public SplitPaymentResponse getSplitPaymentById(UUID id) {
        log.info("Getting split payment with ID: {}", id);

        SplitPayment splitPayment = splitPaymentRepository.findById(id)
                .orElseThrow(() -> new SplitPaymentNotFoundException(id));

        return enrichWithUserInfo(mapToSplitPaymentResponse(splitPayment));
    }

    /**
     * Gets split payments organized by a user
     */
    @Transactional(readOnly = true)
    public Page<SplitPaymentResponse> getSplitPaymentsByOrganizer(UUID organizerId, Pageable pageable) {
        log.info("Getting split payments organized by user: {}", organizerId);

        Page<SplitPayment> payments = splitPaymentRepository.findByOrganizerId(organizerId, pageable);

        return payments.map(this::mapToSplitPaymentResponse)
                .map(this::enrichWithUserInfo);
    }

    /**
     * Gets split payments that a user is participating in
     */
    @Transactional(readOnly = true)
    public Page<SplitPaymentResponse> getSplitPaymentsByParticipant(UUID userId, Pageable pageable) {
        log.info("Getting split payments where user {} is a participant", userId);

        Page<SplitPayment> payments = splitPaymentRepository.findByParticipantId(userId, pageable);

        return payments.map(this::mapToSplitPaymentResponse)
                .map(this::enrichWithUserInfo);
    }

    /**
     * Adds a participant to a split payment
     */
    @Transactional
    public SplitPaymentResponse addParticipant(UUID organizerId, UUID paymentId, AddParticipantRequest request) {
        log.info("Adding participant {} to split payment {}", request.getUserId(), paymentId);

        SplitPayment splitPayment = splitPaymentRepository.findById(paymentId)
                .orElseThrow(() -> new SplitPaymentNotFoundException(paymentId));

        // Verify user is the organizer
        if (!splitPayment.getOrganizerId().equals(organizerId)) {
            throw new IllegalArgumentException("User is not the organizer of this split payment");
        }

        // Verify split payment is active
        if (splitPayment.getStatus() != SplitPaymentStatus.ACTIVE) {
            throw new InvalidPaymentStatusException(
                    "Split payment is not active. Current status: " + splitPayment.getStatus());
        }

        // Verify participant is not the organizer
        if (request.getUserId().equals(organizerId)) {
            throw new IllegalArgumentException("Organizer cannot be added as a participant");
        }

        // Verify participant is not already in the split payment
        if (participantRepository.existsBySplitPaymentIdAndUserId(paymentId, request.getUserId())) {
            throw new IllegalArgumentException("User is already a participant in this split payment");
        }

        // Verify participant exists
        try {
            UserResponse user = userClient.getUser(request.getUserId());
            if (user == null) {
                throw new IllegalArgumentException("User not found: " + request.getUserId());
            }
        } catch (Exception e) {
            log.error("Error validating participant user", e);
            throw new IllegalArgumentException("Unable to validate participant: " + e.getMessage());
        }

        // Validate participant amount
        if (request.getAmount().compareTo(BigDecimal.ZERO) <= 0) {
            throw new IllegalArgumentException("Amount must be greater than zero");
        }

        // Add the participant
        SplitPaymentParticipant participant = splitPayment.addParticipant(
                request.getUserId(), request.getAmount());
        splitPayment = splitPaymentRepository.save(splitPayment);

        // Publish event for notification
        publishSplitPaymentParticipantAddedEvent(splitPayment, participant);

        return enrichWithUserInfo(mapToSplitPaymentResponse(splitPayment));
    }

    /**
     * Removes a participant from a split payment
     */
    @Transactional
    public SplitPaymentResponse removeParticipant(UUID organizerId, UUID paymentId, UUID participantId) {
        log.info("Removing participant {} from split payment {}", participantId, paymentId);

        SplitPayment splitPayment = splitPaymentRepository.findById(paymentId)
                .orElseThrow(() -> new SplitPaymentNotFoundException(paymentId));

        // Verify user is the organizer
        if (!splitPayment.getOrganizerId().equals(organizerId)) {
            throw new IllegalArgumentException("User is not the organizer of this split payment");
        }

        // Verify split payment is active
        if (splitPayment.getStatus() != SplitPaymentStatus.ACTIVE) {
            throw new InvalidPaymentStatusException(
                    "Split payment is not active. Current status: " + splitPayment.getStatus());
        }

        // Verify participant exists and hasn't paid yet
        SplitPaymentParticipant participant = participantRepository
                .findBySplitPaymentIdAndUserId(paymentId, participantId)
                .orElseThrow(() -> new IllegalArgumentException(
                        "User is not a participant in this split payment: " + participantId));

        if (participant.isPaid()) {
            throw new IllegalArgumentException("Cannot remove a participant who has already paid");
        }

        // Remove the participant
        splitPayment.removeParticipant(participantId);
        splitPayment = splitPaymentRepository.save(splitPayment);

        // Publish event for notification
        publishSplitPaymentParticipantRemovedEvent(splitPayment, participantId);

        return enrichWithUserInfo(mapToSplitPaymentResponse(splitPayment));
    }

    /**
     * Updates a participant's amount
     */
    @Transactional
    public SplitPaymentResponse updateParticipantAmount(UUID organizerId, UUID paymentId,
                                                        UUID participantId, UpdateParticipantAmountRequest request) {
        log.info("Updating amount for participant {} in split payment {}", participantId, paymentId);

        SplitPayment splitPayment = splitPaymentRepository.findById(paymentId)
                .orElseThrow(() -> new SplitPaymentNotFoundException(paymentId));

        // Verify user is the organizer
        if (!splitPayment.getOrganizerId().equals(organizerId)) {
            throw new IllegalArgumentException("User is not the organizer of this split payment");
        }

        // Verify split payment is active
        if (splitPayment.getStatus() != SplitPaymentStatus.ACTIVE) {
            throw new InvalidPaymentStatusException(
                    "Split payment is not active. Current status: " + splitPayment.getStatus());
        }

        // Verify participant exists and hasn't paid yet
        SplitPaymentParticipant participant = participantRepository
                .findBySplitPaymentIdAndUserId(paymentId, participantId)
                .orElseThrow(() -> new IllegalArgumentException(
                        "User is not a participant in this split payment: " + participantId));

        if (participant.isPaid()) {
            throw new IllegalArgumentException("Cannot update amount for a participant who has already paid");
        }

        // Validate participant amount
        if (request.getAmount().compareTo(BigDecimal.ZERO) <= 0) {
            throw new IllegalArgumentException("Amount must be greater than zero");
        }

        BigDecimal currentAmount = participant.getAmount();

        // Update the participant's amount
        splitPayment.updateParticipantAmount(participantId, request.getAmount());
        splitPayment = splitPaymentRepository.save(splitPayment);

        // Publish event for notification
        publishSplitPaymentAmountUpdatedEvent(splitPayment, participantId, currentAmount, request.getAmount());

        return enrichWithUserInfo(mapToSplitPaymentResponse(splitPayment));
    }

    /**
     * Pays a share in a split payment
     */
    @Transactional
    @CircuitBreaker(name = "walletService", fallbackMethod = "payShareFallback")
    @Retry(name = "walletService")
    public SplitPaymentResponse payShare(UUID userId, UUID paymentId, PaySplitShareRequest request) {
        log.info("User {} paying their share in split payment {}", userId, paymentId);

        SplitPayment splitPayment = splitPaymentRepository.findById(paymentId)
                .orElseThrow(() -> new SplitPaymentNotFoundException(paymentId));

        // Verify split payment is active
        if (splitPayment.getStatus() != SplitPaymentStatus.ACTIVE) {
            throw new InvalidPaymentStatusException(
                    "Split payment is not active. Current status: " + splitPayment.getStatus());
        }

        // Find the participant
        SplitPaymentParticipant participant = participantRepository
                .findBySplitPaymentIdAndUserId(paymentId, userId)
                .orElseThrow(() -> new IllegalArgumentException(
                        "User is not a participant in this split payment: " + userId));

        // Check if already paid
        if (participant.isPaid()) {
            throw new IllegalStateException("This share has already been paid");
        }

        // Verify source wallet exists and belongs to the user
        WalletResponse wallet;
        try {
            wallet = walletClient.getWallet(request.getSourceWalletId());
            if (wallet == null || !wallet.getUserId().equals(userId)) {
                throw new IllegalArgumentException("Invalid source wallet ID: " + request.getSourceWalletId());
            }

            // Verify wallet currency matches split payment currency
            if (!wallet.getCurrency().equals(splitPayment.getCurrency())) {
                throw new IllegalArgumentException(
                        "Wallet currency does not match split payment currency. " +
                                "Wallet: " + wallet.getCurrency() + ", Split payment: " + splitPayment.getCurrency());
            }

            // Verify wallet has sufficient balance
            if (wallet.getBalance().compareTo(participant.getAmount()) < 0) {
                throw new InsufficientFundsException(
                        "Insufficient funds in wallet. Available: " + wallet.getBalance() +
                                " " + wallet.getCurrency() + ", Required: " + participant.getAmount() +
                                " " + splitPayment.getCurrency());
            }

            // Verify wallet is active
            if (!"ACTIVE".equals(wallet.getStatus())) {
                throw new IllegalArgumentException("Wallet is not active. Status: " + wallet.getStatus());
            }
        } catch (Exception e) {
            log.error("Error validating wallet", e);
            throw new IllegalArgumentException("Unable to validate wallet: " + e.getMessage());
        }

        // Find or get the organizer's wallet
        UUID targetWalletId = findOrGetOrganizerWallet(splitPayment.getOrganizerId(), splitPayment.getCurrency());

        // Execute the payment through the wallet service
        TransferRequest transferRequest = TransferRequest.builder()
                .sourceWalletId(request.getSourceWalletId())
                .targetWalletId(targetWalletId)
                .amount(participant.getAmount())
                .description("Split payment share for: " + splitPayment.getTitle())
                .build();

        try {
            TransferResponse transferResponse = walletClient.transfer(transferRequest);

            // Mark the participant as paid
            UUID transactionId = UUID.fromString(transferResponse.getId().toString());
            participant.markAsPaid(transactionId);
            splitPaymentRepository.save(splitPayment);

            // Check if all participants have paid and update status if necessary
            checkAndUpdateSplitPaymentStatus(splitPayment);

            // Publish event for notification
            publishSplitPaymentParticipantPaidEvent(splitPayment, participant);

            return enrichWithUserInfo(mapToSplitPaymentResponse(splitPayment));
        } catch (Exception e) {
            log.error("Failed to process split payment share", e);
            throw new PaymentFailedException("Failed to process payment: " + e.getMessage(), e);
        }
    }

    /**
     * Fallback method for payShare
     */
    private SplitPaymentResponse payShareFallback(UUID userId, UUID paymentId,
                                                  PaySplitShareRequest request, Exception e) {
        log.warn("Fallback for payShare executed: {}", e.getMessage());
        throw new PaymentFailedException("Payment service temporarily unavailable. Please try again later.");
    }

    /**
     * Find or get the organizer's wallet for receiving payments
     */
    private UUID findOrGetOrganizerWallet(UUID organizerId, String currency) {
        try {
            List<WalletResponse> wallets = walletClient.getUserWallets(organizerId);

            // First try to find a wallet with the matching currency
            Optional<WalletResponse> matchingWallet = wallets.stream()
                    .filter(w -> currency.equals(w.getCurrency()) && "ACTIVE".equals(w.getStatus()))
                    .findFirst();

            if (matchingWallet.isPresent()) {
                return matchingWallet.get().getId();
            }

            // If no matching currency wallet, get the first active wallet
            Optional<WalletResponse> anyActiveWallet = wallets.stream()
                    .filter(w -> "ACTIVE".equals(w.getStatus()))
                    .findFirst();

            if (anyActiveWallet.isPresent()) {
                log.warn("No wallet found with currency {}, using default wallet", currency);
                return anyActiveWallet.get().getId();
            }

            throw new IllegalStateException("No active wallet found for organizer " + organizerId);
        } catch (Exception e) {
            log.error("Error finding organizer wallet", e);
            throw new IllegalStateException("Unable to find a wallet for the organizer: " + e.getMessage());
        }
    }

    /**
     * Checks if all participants have paid and updates the split payment status if necessary
     */
    private void checkAndUpdateSplitPaymentStatus(SplitPayment splitPayment) {
        boolean allPaid = true;
        for (SplitPaymentParticipant participant : splitPayment.getParticipants()) {
            if (!participant.isPaid()) {
                allPaid = false;
                break;
            }
        }

        if (allPaid) {
            splitPayment.complete();
            splitPaymentRepository.save(splitPayment);

            // Publish event for notification
            publishSplitPaymentCompletedEvent(splitPayment);
        }
    }

    /**
     * Cancels a split payment (can only be done by organizer)
     */
    @Transactional
    public SplitPaymentResponse cancelSplitPayment(UUID userId, UUID paymentId) {
        log.info("Canceling split payment with ID: {}", paymentId);

        SplitPayment splitPayment = splitPaymentRepository.findById(paymentId)
                .orElseThrow(() -> new SplitPaymentNotFoundException(paymentId));

        // Verify user is the organizer
        if (!splitPayment.getOrganizerId().equals(userId)) {
            throw new IllegalArgumentException("Only the organizer can cancel this split payment");
        }

        // Verify split payment is in a cancellable state
        if (splitPayment.getStatus() != SplitPaymentStatus.ACTIVE) {
            throw new InvalidPaymentStatusException(
                    "Split payment cannot be canceled. Current status: " + splitPayment.getStatus());
        }

        // Check if any participants have already paid
        boolean anyPaid = splitPayment.getParticipants().stream()
                .anyMatch(SplitPaymentParticipant::isPaid);

        if (anyPaid) {
            throw new InvalidPaymentOperationException(
                    "Cannot cancel split payment when some participants have already paid");
        }

        // Cancel the split payment
        splitPayment.cancel();
        splitPayment = splitPaymentRepository.save(splitPayment);

        // Publish event for notification
        publishSplitPaymentCanceledEvent(splitPayment);

        return enrichWithUserInfo(mapToSplitPaymentResponse(splitPayment));
    }

    /**
     * Scheduled task to expire split payments
     */
    @Scheduled(cron = "0 0 * * * *") // Every hour
    @Transactional
    public void expireSplitPayments() {
        log.info("Checking for expired split payments");

        List<SplitPayment> expiredPayments = splitPaymentRepository.findByStatusAndExpiryDateBefore(
                SplitPaymentStatus.ACTIVE, LocalDateTime.now());

        for (SplitPayment splitPayment : expiredPayments) {
            // Check if any participants have paid
            boolean anyPaid = splitPayment.getParticipants().stream()
                    .anyMatch(SplitPaymentParticipant::isPaid);

            // Only expire if no one has paid
            if (!anyPaid) {
                splitPayment.expire();
                splitPaymentRepository.save(splitPayment);

                // Publish event for notification
                publishSplitPaymentExpiredEvent(splitPayment);

                log.info("Marked split payment as expired: {}", splitPayment.getId());
            }
        }
    }

    /**
     * Create a reminder for unpaid participants
     */
    @Transactional
    public void sendReminder(UUID organizerId, UUID paymentId) {
        log.info("Sending reminders for split payment with ID: {}", paymentId);

        SplitPayment splitPayment = splitPaymentRepository.findById(paymentId)
                .orElseThrow(() -> new SplitPaymentNotFoundException(paymentId));

        // Verify user is the organizer
        if (!splitPayment.getOrganizerId().equals(organizerId)) {
            throw new IllegalArgumentException("Only the organizer can send reminders");
        }

        // Verify split payment is active
        if (splitPayment.getStatus() != SplitPaymentStatus.ACTIVE) {
            throw new InvalidPaymentStatusException(
                    "Cannot send reminders for non-active split payment. Current status: "
                            + splitPayment.getStatus());
        }

        // Get unpaid participants
        List<SplitPaymentParticipant> unpaidParticipants = splitPayment.getParticipants().stream()
                .filter(p -> !p.isPaid())
                .collect(Collectors.toList());

        if (unpaidParticipants.isEmpty()) {
            log.info("No unpaid participants found for split payment: {}", paymentId);
            return;
        }

        // Send reminder notifications to unpaid participants
        for (SplitPaymentParticipant participant : unpaidParticipants) {
            publishSplitPaymentReminderEvent(splitPayment, participant);
        }
    }

    /**
     * Get payment statistics for a split payment
     */
    @Transactional(readOnly = true)
    public SplitPaymentStatisticsResponse getStatistics(UUID paymentId) {
        log.info("Getting statistics for split payment with ID: {}", paymentId);

        SplitPayment splitPayment = splitPaymentRepository.findById(paymentId)
                .orElseThrow(() -> new SplitPaymentNotFoundException(paymentId));

        // Calculate statistics
        int totalParticipants = splitPayment.getParticipants().size();
        int paidParticipants = (int) splitPayment.getParticipants().stream()
                .filter(SplitPaymentParticipant::isPaid)
                .count();
        int unpaidParticipants = totalParticipants - paidParticipants;

        BigDecimal totalAmount = splitPayment.getTotalAmount();
        BigDecimal paidAmount = splitPayment.getParticipants().stream()
                .filter(SplitPaymentParticipant::isPaid)
                .map(SplitPaymentParticipant::getAmount)
                .reduce(BigDecimal.ZERO, BigDecimal::add);
        BigDecimal remainingAmount = totalAmount.subtract(paidAmount);

        BigDecimal completionPercentage = BigDecimal.ZERO;
        if (totalAmount.compareTo(BigDecimal.ZERO) > 0) {
            completionPercentage = paidAmount
                    .multiply(new BigDecimal("100"))
                    .divide(totalAmount, 2, RoundingMode.HALF_UP);
        }

        // Build response
        return SplitPaymentStatisticsResponse.builder()
                .id(splitPayment.getId())
                .title(splitPayment.getTitle())
                .totalAmount(totalAmount)
                .paidAmount(paidAmount)
                .remainingAmount(remainingAmount)
                .currency(splitPayment.getCurrency())
                .totalParticipants(totalParticipants)
                .paidParticipants(paidParticipants)
                .unpaidParticipants(unpaidParticipants)
                .completionPercentage(completionPercentage)
                .status(splitPayment.getStatus().toString())
                .expiryDate(splitPayment.getExpiryDate())
                .build();
    }

    /**
     * Publish a participant paid event
     */
    private void publishSplitPaymentParticipantPaidEvent(SplitPayment splitPayment,
                                                         SplitPaymentParticipant participant) {
        try {
            Map<String, Object> event = new HashMap<>();
            event.put("eventType", "SPLIT_PAYMENT");
            event.put("userId", participant.getUserId().toString());
            event.put("paymentId", splitPayment.getId().toString());
            event.put("status", "PARTICIPANT_PAID");
            event.put("title", splitPayment.getTitle());
            event.put("organizerId", splitPayment.getOrganizerId().toString());
            event.put("totalAmount", splitPayment.getTotalAmount());
            event.put("userAmount", participant.getAmount());
            event.put("currency", splitPayment.getCurrency());
            event.put("timestamp", LocalDateTime.now().toString());

            // Get user info for better notifications
            try {
                UserResponse organizer = userClient.getUser(splitPayment.getOrganizerId());
                if (organizer != null) {
                    event.put("organizerName", organizer.getDisplayName());
                }

                UserResponse participantUser = userClient.getUser(participant.getUserId());
                if (participantUser != null) {
                    event.put("participantName", participantUser.getDisplayName());
                }
            } catch (Exception e) {
                log.warn("Could not get user info for notification", e);
            }

            String jsonEvent = objectMapper.writeValueAsString(event);
            kafkaTemplate.send(SPLIT_PAYMENT_EVENTS_TOPIC, participant.getUserId().toString(), jsonEvent);
            kafkaTemplate.send(SPLIT_PAYMENT_EVENTS_TOPIC, splitPayment.getOrganizerId().toString(), jsonEvent);
        } catch (Exception e) {
            log.error("Failed to publish split payment participant paid event", e);
        }
    }

    /**
     * Publish a split payment completed event
     */
    private void publishSplitPaymentCompletedEvent(SplitPayment splitPayment) {
        try {
            // Notify organizer
            Map<String, Object> organizerEvent = new HashMap<>();
            organizerEvent.put("eventType", "SPLIT_PAYMENT");
            organizerEvent.put("userId", splitPayment.getOrganizerId().toString());
            organizerEvent.put("paymentId", splitPayment.getId().toString());
            organizerEvent.put("status", "COMPLETED");
            organizerEvent.put("title", splitPayment.getTitle());
            organizerEvent.put("totalAmount", splitPayment.getTotalAmount());
            organizerEvent.put("currency", splitPayment.getCurrency());
            organizerEvent.put("timestamp", LocalDateTime.now().toString());

            String jsonOrganizerEvent = objectMapper.writeValueAsString(organizerEvent);
            kafkaTemplate.send(SPLIT_PAYMENT_EVENTS_TOPIC, splitPayment.getOrganizerId().toString(), jsonOrganizerEvent);

            // Notify all participants
            for (SplitPaymentParticipant participant : splitPayment.getParticipants()) {
                Map<String, Object> participantEvent = new HashMap<>(organizerEvent);
                participantEvent.put("userId", participant.getUserId().toString());
                participantEvent.put("userAmount", participant.getAmount());

                String jsonParticipantEvent = objectMapper.writeValueAsString(participantEvent);
                kafkaTemplate.send(SPLIT_PAYMENT_EVENTS_TOPIC, participant.getUserId().toString(), jsonParticipantEvent);
            }
        } catch (Exception e) {
            log.error("Failed to publish split payment completed event", e);
        }
    }

    /**
     * Publish a split payment reminder event for unpaid participants
     */
    private void publishSplitPaymentReminderEvent(SplitPayment splitPayment, SplitPaymentParticipant participant) {
        try {
            Map<String, Object> event = new HashMap<>();
            event.put("eventType", "SPLIT_PAYMENT");
            event.put("userId", participant.getUserId().toString());
            event.put("paymentId", splitPayment.getId().toString());
            event.put("status", "REMINDER");
            event.put("title", splitPayment.getTitle());
            event.put("organizerId", splitPayment.getOrganizerId().toString());
            event.put("totalAmount", splitPayment.getTotalAmount());
            event.put("userAmount", participant.getAmount());
            event.put("currency", splitPayment.getCurrency());
            event.put("expiryDate", splitPayment.getExpiryDate().toString());
            event.put("timestamp", LocalDateTime.now().toString());

            // Add organizer info
            try {
                UserResponse organizer = userClient.getUser(splitPayment.getOrganizerId());
                if (organizer != null) {
                    event.put("organizerName", organizer.getDisplayName());
                }
            } catch (Exception e) {
                log.warn("Could not get user info for notification", e);
            }

            String jsonEvent = objectMapper.writeValueAsString(event);
            kafkaTemplate.send(SPLIT_PAYMENT_EVENTS_TOPIC, participant.getUserId().toString(), jsonEvent);
        } catch (Exception e) {
            log.error("Failed to publish split payment reminder event", e);
        }
    }

    /**
     * Publish a split payment canceled event
     */
    private void publishSplitPaymentCanceledEvent(SplitPayment splitPayment) {
        try {
            // Notify organizer
            Map<String, Object> organizerEvent = new HashMap<>();
            organizerEvent.put("eventType", "SPLIT_PAYMENT");
            organizerEvent.put("userId", splitPayment.getOrganizerId().toString());
            organizerEvent.put("paymentId", splitPayment.getId().toString());
            organizerEvent.put("status", "CANCELED");
            organizerEvent.put("title", splitPayment.getTitle());
            organizerEvent.put("totalAmount", splitPayment.getTotalAmount());
            organizerEvent.put("currency", splitPayment.getCurrency());
            organizerEvent.put("timestamp", LocalDateTime.now().toString());

            String jsonOrganizerEvent = objectMapper.writeValueAsString(organizerEvent);
            kafkaTemplate.send(SPLIT_PAYMENT_EVENTS_TOPIC, splitPayment.getOrganizerId().toString(), jsonOrganizerEvent);

            // Notify all participants
            for (SplitPaymentParticipant participant : splitPayment.getParticipants()) {
                Map<String, Object> participantEvent = new HashMap<>(organizerEvent);
                participantEvent.put("userId", participant.getUserId().toString());
                participantEvent.put("userAmount", participant.getAmount());

                String jsonParticipantEvent = objectMapper.writeValueAsString(participantEvent);
                kafkaTemplate.send(SPLIT_PAYMENT_EVENTS_TOPIC, participant.getUserId().toString(), jsonParticipantEvent);
            }
        } catch (Exception e) {
            log.error("Failed to publish split payment canceled event", e);
        }
    }

    /**
     * Publish a split payment expired event
     */
    private void publishSplitPaymentExpiredEvent(SplitPayment splitPayment) {
        try {
            // Notify organizer
            Map<String, Object> organizerEvent = new HashMap<>();
            organizerEvent.put("eventType", "SPLIT_PAYMENT");
            organizerEvent.put("userId", splitPayment.getOrganizerId().toString());
            organizerEvent.put("paymentId", splitPayment.getId().toString());
            organizerEvent.put("status", "EXPIRED");
            organizerEvent.put("title", splitPayment.getTitle());
            organizerEvent.put("totalAmount", splitPayment.getTotalAmount());
            organizerEvent.put("currency", splitPayment.getCurrency());
            organizerEvent.put("timestamp", LocalDateTime.now().toString());

            String jsonOrganizerEvent = objectMapper.writeValueAsString(organizerEvent);
            kafkaTemplate.send(SPLIT_PAYMENT_EVENTS_TOPIC, splitPayment.getOrganizerId().toString(), jsonOrganizerEvent);

            // Notify all participants
            for (SplitPaymentParticipant participant : splitPayment.getParticipants()) {
                Map<String, Object> participantEvent = new HashMap<>(organizerEvent);
                participantEvent.put("userId", participant.getUserId().toString());
                participantEvent.put("userAmount", participant.getAmount());

                String jsonParticipantEvent = objectMapper.writeValueAsString(participantEvent);
                kafkaTemplate.send(SPLIT_PAYMENT_EVENTS_TOPIC, participant.getUserId().toString(), jsonParticipantEvent);
            }
        } catch (Exception e) {
            log.error("Failed to publish split payment expired event", e);
        }
    }

    /**
     * Publish a split payment amount updated event
     */
    private void publishSplitPaymentAmountUpdatedEvent(SplitPayment splitPayment, UUID participantId,
                                                       BigDecimal oldAmount, BigDecimal newAmount) {
        try {
            // Get participant
            SplitPaymentParticipant participant = splitPayment.getParticipants().stream()
                    .filter(p -> p.getUserId().equals(participantId))
                    .findFirst()
                    .orElse(null);

            if (participant == null) {
                return;
            }

            // Notify organizer
            Map<String, Object> organizerEvent = new HashMap<>();
            organizerEvent.put("eventType", "SPLIT_PAYMENT");
            organizerEvent.put("userId", splitPayment.getOrganizerId().toString());
            organizerEvent.put("paymentId", splitPayment.getId().toString());
            organizerEvent.put("status", "AMOUNT_UPDATED");
            organizerEvent.put("title", splitPayment.getTitle());
            organizerEvent.put("totalAmount", splitPayment.getTotalAmount());
            organizerEvent.put("currency", splitPayment.getCurrency());
            organizerEvent.put("participantId", participantId.toString());
            organizerEvent.put("oldAmount", oldAmount);
            organizerEvent.put("newAmount", newAmount);
            organizerEvent.put("timestamp", LocalDateTime.now().toString());

            // Get user info for better notifications
            try {
                UserResponse participantUser = userClient.getUser(participantId);
                if (participantUser != null) {
                    organizerEvent.put("participantName", participantUser.getDisplayName());
                }
            } catch (Exception e) {
                log.warn("Could not get user info for notification", e);
            }

            String jsonOrganizerEvent = objectMapper.writeValueAsString(organizerEvent);
            kafkaTemplate.send(SPLIT_PAYMENT_EVENTS_TOPIC, splitPayment.getOrganizerId().toString(), jsonOrganizerEvent);

            // Notify participant
            Map<String, Object> participantEvent = new HashMap<>();
            participantEvent.put("eventType", "SPLIT_PAYMENT");
            participantEvent.put("userId", participantId.toString());
            participantEvent.put("paymentId", splitPayment.getId().toString());
            participantEvent.put("status", "AMOUNT_UPDATED");
            participantEvent.put("title", splitPayment.getTitle());
            participantEvent.put("oldAmount", oldAmount);
            participantEvent.put("newAmount", newAmount);
            participantEvent.put("currency", splitPayment.getCurrency());
            participantEvent.put("timestamp", LocalDateTime.now().toString());

            // Add organizer info
            try {
                UserResponse organizer = userClient.getUser(splitPayment.getOrganizerId());
                if (organizer != null) {
                    participantEvent.put("organizerName", organizer.getDisplayName());
                }
            } catch (Exception e) {
                log.warn("Could not get user info for notification", e);
            }

            String jsonParticipantEvent = objectMapper.writeValueAsString(participantEvent);
            kafkaTemplate.send(SPLIT_PAYMENT_EVENTS_TOPIC, participantId.toString(), jsonParticipantEvent);
        } catch (Exception e) {
            log.error("Failed to publish split payment amount updated event", e);
        }
    }

    /**
     * Publish a split payment participant added event
     */
    private void publishSplitPaymentParticipantAddedEvent(SplitPayment splitPayment,
                                                          SplitPaymentParticipant participant) {
        try {
            // Notify organizer
            Map<String, Object> organizerEvent = new HashMap<>();
            organizerEvent.put("eventType", "SPLIT_PAYMENT");
            organizerEvent.put("userId", splitPayment.getOrganizerId().toString());
            organizerEvent.put("paymentId", splitPayment.getId().toString());
            organizerEvent.put("status", "PARTICIPANT_ADDED");
            organizerEvent.put("title", splitPayment.getTitle());
            organizerEvent.put("totalAmount", splitPayment.getTotalAmount());
            organizerEvent.put("currency", splitPayment.getCurrency());
            organizerEvent.put("participantId", participant.getUserId().toString());
            organizerEvent.put("participantAmount", participant.getAmount());
            organizerEvent.put("timestamp", LocalDateTime.now().toString());

            // Get user info for better notifications
            try {
                UserResponse participantUser = userClient.getUser(participant.getUserId());
                if (participantUser != null) {
                    organizerEvent.put("participantName", participantUser.getDisplayName());
                }
            } catch (Exception e) {
                log.warn("Could not get user info for notification", e);
            }

            String jsonOrganizerEvent = objectMapper.writeValueAsString(organizerEvent);
            kafkaTemplate.send(SPLIT_PAYMENT_EVENTS_TOPIC, splitPayment.getOrganizerId().toString(), jsonOrganizerEvent);

            // Notify new participant
            Map<String, Object> participantEvent = new HashMap<>();
            participantEvent.put("eventType", "SPLIT_PAYMENT");
            participantEvent.put("userId", participant.getUserId().toString());
            participantEvent.put("paymentId", splitPayment.getId().toString());
            participantEvent.put("status", "PARTICIPANT_ADDED");
            participantEvent.put("title", splitPayment.getTitle());
            participantEvent.put("totalAmount", splitPayment.getTotalAmount());
            participantEvent.put("userAmount", participant.getAmount());
            participantEvent.put("currency", splitPayment.getCurrency());
            participantEvent.put("timestamp", LocalDateTime.now().toString());

            // Add organizer info
            try {
                UserResponse organizer = userClient.getUser(splitPayment.getOrganizerId());
                if (organizer != null) {
                    participantEvent.put("organizerName", organizer.getDisplayName());
                }
            } catch (Exception e) {
                log.warn("Could not get user info for notification", e);
            }

            String jsonParticipantEvent = objectMapper.writeValueAsString(participantEvent);
            kafkaTemplate.send(SPLIT_PAYMENT_EVENTS_TOPIC, participant.getUserId().toString(), jsonParticipantEvent);
        } catch (Exception e) {
            log.error("Failed to publish split payment participant added event", e);
        }
    }

    /**
     * Publish a split payment participant removed event
     */
    private void publishSplitPaymentParticipantRemovedEvent(SplitPayment splitPayment, UUID participantId) {
        try {
            // Notify organizer
            Map<String, Object> organizerEvent = new HashMap<>();
            organizerEvent.put("eventType", "SPLIT_PAYMENT");
            organizerEvent.put("userId", splitPayment.getOrganizerId().toString());
            organizerEvent.put("paymentId", splitPayment.getId().toString());
            organizerEvent.put("status", "PARTICIPANT_REMOVED");
            organizerEvent.put("title", splitPayment.getTitle());
            organizerEvent.put("totalAmount", splitPayment.getTotalAmount());
            organizerEvent.put("currency", splitPayment.getCurrency());
            organizerEvent.put("participantId", participantId.toString());
            organizerEvent.put("timestamp", LocalDateTime.now().toString());

            // Get user info for better notifications
            try {
                UserResponse participantUser = userClient.getUser(participantId);
                if (participantUser != null) {
                    organizerEvent.put("participantName", participantUser.getDisplayName());
                }
            } catch (Exception e) {
                log.warn("Could not get user info for notification", e);
            }

            String jsonOrganizerEvent = objectMapper.writeValueAsString(organizerEvent);
            kafkaTemplate.send(SPLIT_PAYMENT_EVENTS_TOPIC, splitPayment.getOrganizerId().toString(), jsonOrganizerEvent);

            // Notify removed participant
            Map<String, Object> participantEvent = new HashMap<>();
            participantEvent.put("eventType", "SPLIT_PAYMENT");
            participantEvent.put("userId", participantId.toString());
            participantEvent.put("paymentId", splitPayment.getId().toString());
            participantEvent.put("status", "PARTICIPANT_REMOVED");
            participantEvent.put("title", splitPayment.getTitle());
            participantEvent.put("currency", splitPayment.getCurrency());
            participantEvent.put("timestamp", LocalDateTime.now().toString());

            // Add organizer info
            try {
                UserResponse organizer = userClient.getUser(splitPayment.getOrganizerId());
                if (organizer != null) {
                    participantEvent.put("organizerName", organizer.getDisplayName());
                }
            } catch (Exception e) {
                log.warn("Could not get user info for notification", e);
            }

            String jsonParticipantEvent = objectMapper.writeValueAsString(participantEvent);
            kafkaTemplate.send(SPLIT_PAYMENT_EVENTS_TOPIC, participantId.toString(), jsonParticipantEvent);
        } catch (Exception e) {
            log.error("Failed to publish split payment participant removed event", e);
        }
    }

    /**
     * Publish a split payment created event
     */
    private void publishSplitPaymentCreatedEvent(SplitPayment splitPayment) {
        try {
            // Notify organizer
            Map<String, Object> organizerEvent = new HashMap<>();
            organizerEvent.put("eventType", "SPLIT_PAYMENT");
            organizerEvent.put("userId", splitPayment.getOrganizerId().toString());
            organizerEvent.put("paymentId", splitPayment.getId().toString());
            organizerEvent.put("status", "CREATED");
            organizerEvent.put("title", splitPayment.getTitle());
            organizerEvent.put("totalAmount", splitPayment.getTotalAmount());
            organizerEvent.put("currency", splitPayment.getCurrency());
            organizerEvent.put("timestamp", LocalDateTime.now().toString());

            String jsonOrganizerEvent = objectMapper.writeValueAsString(organizerEvent);
            kafkaTemplate.send(SPLIT_PAYMENT_EVENTS_TOPIC, splitPayment.getOrganizerId().toString(), jsonOrganizerEvent);

            // Notify all participants
            for (SplitPaymentParticipant participant : splitPayment.getParticipants()) {
                Map<String, Object> participantEvent = new HashMap<>();
                participantEvent.put("eventType", "SPLIT_PAYMENT");
                participantEvent.put("userId", participant.getUserId().toString());
                participantEvent.put("paymentId", splitPayment.getId().toString());
                participantEvent.put("status", "CREATED");
                participantEvent.put("title", splitPayment.getTitle());
                participantEvent.put("totalAmount", splitPayment.getTotalAmount());
                participantEvent.put("userAmount", participant.getAmount());
                participantEvent.put("currency", splitPayment.getCurrency());
                participantEvent.put("timestamp", LocalDateTime.now().toString());

                // Add organizer info
                try {
                    UserResponse organizer = userClient.getUser(splitPayment.getOrganizerId());
                    if (organizer != null) {
                        participantEvent.put("organizerName", organizer.getDisplayName());
                    }
                } catch (Exception e) {
                    log.warn("Could not get user info for notification", e);
                }

                String jsonParticipantEvent = objectMapper.writeValueAsString(participantEvent);
                kafkaTemplate.send(SPLIT_PAYMENT_EVENTS_TOPIC, participant.getUserId().toString(), jsonParticipantEvent);
            }
        } catch (Exception e) {
            log.error("Failed to publish split payment created event", e);
        }
    }

    /**
     * Maps a SplitPayment entity to a SplitPaymentResponse DTO
     */
    private SplitPaymentResponse mapToSplitPaymentResponse(SplitPayment splitPayment) {
        List<SplitPaymentParticipantResponse> participants = splitPayment.getParticipants().stream()
                .map(participant -> SplitPaymentParticipantResponse.builder()
                        .id(participant.getId())
                        .userId(participant.getUserId())
                        .amount(participant.getAmount())
                        .transactionId(participant.getTransactionId())
                        .paid(participant.isPaid())
                        .paymentDate(participant.getPaymentDate())
                        .build())
                .collect(Collectors.toList());

        // Calculate payment statistics
        BigDecimal paidAmount = splitPayment.getParticipants().stream()
                .filter(SplitPaymentParticipant::isPaid)
                .map(SplitPaymentParticipant::getAmount)
                .reduce(BigDecimal.ZERO, BigDecimal::add);

        BigDecimal remainingAmount = splitPayment.getTotalAmount().subtract(paidAmount);

        BigDecimal completionPercentage = BigDecimal.ZERO;
        if (splitPayment.getTotalAmount().compareTo(BigDecimal.ZERO) > 0) {
            completionPercentage = paidAmount
                    .multiply(new BigDecimal("100"))
                    .divide(splitPayment.getTotalAmount(), 2, RoundingMode.HALF_UP);
        }

        return SplitPaymentResponse.builder()
                .id(splitPayment.getId())
                .organizerId(splitPayment.getOrganizerId())
                .title(splitPayment.getTitle())
                .description(splitPayment.getDescription())
                .totalAmount(splitPayment.getTotalAmount())
                .currency(splitPayment.getCurrency())
                .status(splitPayment.getStatus().toString())
                .expiryDate(splitPayment.getExpiryDate())
                .createdAt(splitPayment.getCreatedAt())
                .updatedAt(splitPayment.getUpdatedAt())
                .paidAmount(paidAmount)
                .remainingAmount(remainingAmount)
                .completionPercentage(completionPercentage)
                .participants(participants)
                .build();
    }

    /**
     * Enriches a split payment response with user information
     */
    private SplitPaymentResponse enrichWithUserInfo(SplitPaymentResponse response) {
        try {
            // Get organizer details
            UserResponse organizer = userClient.getUser(response.getOrganizerId());
            if (organizer != null) {
                response.setOrganizerName(organizer.getDisplayName());
            }

            // Get participant details
            List<UUID> participantIds = response.getParticipants().stream()
                    .map(SplitPaymentParticipantResponse::getUserId)
                    .collect(Collectors.toList());

            if (!participantIds.isEmpty()) {
                List<UserResponse> users = userClient.getUsers(participantIds);
                Map<UUID, UserResponse> userMap = users.stream()
                        .collect(Collectors.toMap(UserResponse::getId, Function.identity()));

                for (SplitPaymentParticipantResponse participant : response.getParticipants()) {
                    UserResponse user = userMap.get(participant.getUserId());
                    if (user != null) {
                        participant.setUserName(user.getDisplayName());
                    }
                }
            }
        } catch (Exception e) {
            log.warn("Failed to enrich split payment with user info", e);
            // Continue without user info
        }

        return response;
    }
}


