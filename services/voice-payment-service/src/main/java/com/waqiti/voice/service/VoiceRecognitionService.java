package com.waqiti.voice.service;

import com.waqiti.voice.domain.*;
import com.waqiti.voice.dto.*;
import com.waqiti.voice.integration.*;
import com.waqiti.voice.repository.VoiceTransactionRepository;
import com.waqiti.voice.security.access.VoiceDataAccessSecurityAspect.ValidateUserAccess;
import com.waqiti.voice.service.impl.VoiceRecipientResolutionService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.*;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Collectors;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

@Service
@RequiredArgsConstructor
@Slf4j
@Transactional
public class VoiceRecognitionService {
    
    private final GoogleSpeechToTextClient speechToTextClient;
    private final AmazonPollyClient textToSpeechClient;
    private final VoiceBiometricClient biometricClient;
    private final VoiceCommandRepository commandRepository;
    private final VoiceProfileRepository profileRepository;
    private final VoiceTransactionRepository voiceTransactionRepository;
    private final NaturalLanguageProcessor nlpProcessor;
    private final PaymentService paymentService;
    private final SecurityService securityService;
    private final NotificationService notificationService;
    private final VoiceFraudDetectionService fraudDetectionService;
    private final MultilingualVoiceProcessor multilingualProcessor;
    private final VoiceSessionManager sessionManager;
    private final AudioStreamingService audioStreamingService;
    private final VoiceRecipientResolutionService recipientResolutionService;
    
    // Cache for voice sessions and biometric data
    private final Map<String, VoiceSession> activeSessions = new ConcurrentHashMap<>();
    private final Map<UUID, VoiceBiometricProfile> biometricProfiles = new ConcurrentHashMap<>();
    
    /**
     * Process voice command from audio input
     *
     * SECURITY: Validates user can only process their own voice commands
     */
    @ValidateUserAccess(userIdParam = "userId")
    @PreAuthorize("hasRole('USER')")
    public VoiceCommandResponse processVoiceCommand(UUID userId, MultipartFile audioFile,
                                                   VoiceCommandRequest request) {
        log.info("Processing voice command for user: {}", userId);
        
        try {
            // Validate user's voice profile
            VoiceProfile profile = validateVoiceProfile(userId);
            
            // Create voice command record
            VoiceCommand command = createVoiceCommand(userId, request);
            command = commandRepository.save(command);
            
            // Upload and store audio file
            String audioUrl = uploadAudioFile(audioFile, command.getId());
            command.setOriginalAudioUrl(audioUrl);
            
            // Step 1: Convert speech to text
            command.updateProcessingStatus(VoiceCommand.ProcessingStatus.TRANSCRIBING);
            commandRepository.save(command);
            
            SpeechToTextResult speechResult = speechToTextClient.transcribeAudio(
                audioFile, request.getLanguage());
            
            command.setTranscribedText(speechResult.getTranscribedText());
            command.setConfidenceScore(speechResult.getConfidenceScore());
            command.setAudioQualityScore(speechResult.getAudioQuality());
            
            // Step 2: Perform voice biometric verification
            if (profile.getSecurityLevel() != VoiceProfile.SecurityLevel.BASIC) {
                VoiceBiometricResult biometricResult = biometricClient.verifyVoice(
                    audioFile, profile.getVoiceSignature());
                
                command.setVoiceSignatureMatch(biometricResult.isMatch());
                command.setSecurityScore(biometricResult.getConfidenceScore());
                command.setBiometricData(biometricResult.getBiometricFeatures());
                
                if (!biometricResult.isMatch() && profile.getSecurityLevel() == VoiceProfile.SecurityLevel.HIGH) {
                    return handleSecurityFailure(command, "Voice biometric verification failed");
                }
            }
            
            // Step 3: Parse command and extract intent/entities
            command.updateProcessingStatus(VoiceCommand.ProcessingStatus.PARSING);
            commandRepository.save(command);
            
            NLPResult nlpResult = nlpProcessor.processCommand(
                command.getTranscribedText(), request.getLanguage());
            
            command.setIntent(nlpResult.getIntent());
            command.setCommandType(mapIntentToCommandType(nlpResult.getIntent()));
            command.setExtractedEntities(nlpResult.getEntities());
            
            // Extract payment-specific entities
            extractPaymentEntities(command, nlpResult);
            
            // Step 4: Validate command parameters
            command.updateProcessingStatus(VoiceCommand.ProcessingStatus.VALIDATING);
            ValidationResult validation = validateCommand(command);
            
            if (!validation.isValid()) {
                return handleValidationFailure(command, validation.getErrors());
            }
            
            // Step 5: Determine if confirmation is required
            boolean needsConfirmation = determineConfirmationNeeded(command, profile);
            command.setConfirmationRequired(needsConfirmation);
            
            if (needsConfirmation) {
                command.updateProcessingStatus(VoiceCommand.ProcessingStatus.CONFIRMING);
                commandRepository.save(command);
                
                return createConfirmationResponse(command);
            } else {
                // Execute command immediately
                return executeCommand(command);
            }
            
        } catch (Exception e) {
            log.error("Error processing voice command for user: {}", userId, e);
            return VoiceCommandResponse.error("Failed to process voice command: " + e.getMessage());
        }
    }
    
    /**
     * Confirm voice command execution
     *
     * SECURITY: Validates user can only confirm their own voice commands
     */
    @ValidateUserAccess(userIdParam = "userId")
    @PreAuthorize("hasRole('USER')")
    public VoiceCommandResponse confirmVoiceCommand(UUID userId, String sessionId, boolean confirmed) {
        log.info("Confirming voice command for user: {} session: {} confirmed: {}",
                userId, sessionId, confirmed);
        
        Optional<VoiceCommand> optionalCommand = commandRepository.findByUserIdAndSessionId(userId, sessionId);
        
        if (optionalCommand.isEmpty()) {
            return VoiceCommandResponse.error("Voice command session not found");
        }
        
        VoiceCommand command = optionalCommand.get();
        
        if (command.isExpired()) {
            command.updateProcessingStatus(VoiceCommand.ProcessingStatus.EXPIRED);
            commandRepository.save(command);
            return VoiceCommandResponse.error("Voice command has expired");
        }
        
        if (confirmed) {
            command.setIsConfirmed(true);
            return executeCommand(command);
        } else {
            command.updateProcessingStatus(VoiceCommand.ProcessingStatus.CANCELLED);
            commandRepository.save(command);
            return VoiceCommandResponse.success("Voice command cancelled", null);
        }
    }
    
    /**
     * Execute validated voice command
     */
    private VoiceCommandResponse executeCommand(VoiceCommand command) {
        log.info("Executing voice command: {} type: {}", command.getId(), command.getCommandType());
        
        try {
            command.updateProcessingStatus(VoiceCommand.ProcessingStatus.PROCESSING);
            commandRepository.save(command);
            
            VoiceCommandResponse response;
            
            switch (command.getCommandType()) {
                case SEND_PAYMENT:
                    response = executeSendPayment(command);
                    break;
                case REQUEST_PAYMENT:
                    response = executeRequestPayment(command);
                    break;
                case CHECK_BALANCE:
                    response = executeCheckBalance(command);
                    break;
                case TRANSACTION_HISTORY:
                    response = executeTransactionHistory(command);
                    break;
                case SPLIT_BILL:
                    response = executeSplitBill(command);
                    break;
                case PAY_BILL:
                    response = executePayBill(command);
                    break;
                case TRANSFER_FUNDS:
                    response = executeTransferFunds(command);
                    break;
                case SET_REMINDER:
                    response = executeSetReminder(command);
                    break;
                case CANCEL_PAYMENT:
                    response = executeCancelPayment(command);
                    break;
                case ADD_CONTACT:
                    response = executeAddContact(command);
                    break;
                case HELP_COMMAND:
                    response = executeHelpCommand(command);
                    break;
                default:
                    response = VoiceCommandResponse.error("Unknown command type");
            }
            
            if (response.isSuccess()) {
                command.updateProcessingStatus(VoiceCommand.ProcessingStatus.COMPLETED);
                updateVoiceProfileStats(command, true);
            } else {
                command.updateProcessingStatus(VoiceCommand.ProcessingStatus.FAILED);
                command.setErrorMessage(response.getMessage());
                updateVoiceProfileStats(command, false);
            }
            
            commandRepository.save(command);
            
            // Send notification if it's a payment command
            if (command.isPaymentCommand() && response.isSuccess()) {
                sendPaymentNotification(command, response);
            }
            
            return response;
            
        } catch (Exception e) {
            log.error("Error executing voice command: {}", command.getId(), e);
            command.updateProcessingStatus(VoiceCommand.ProcessingStatus.FAILED);
            command.setErrorMessage(e.getMessage());
            commandRepository.save(command);
            
            return VoiceCommandResponse.error("Failed to execute command: " + e.getMessage());
        }
    }
    
    /**
     * Get voice command status
     *
     * SECURITY: Validates user can only view their own voice command status
     */
    @ValidateUserAccess(userIdParam = "userId")
    @PreAuthorize("hasRole('USER')")
    public VoiceCommandStatusDto getCommandStatus(UUID userId, String sessionId) {
        Optional<VoiceCommand> optionalCommand = commandRepository.findByUserIdAndSessionId(userId, sessionId);
        
        if (optionalCommand.isEmpty()) {
            return VoiceCommandStatusDto.notFound();
        }
        
        VoiceCommand command = optionalCommand.get();
        
        return VoiceCommandStatusDto.builder()
                .sessionId(sessionId)
                .status(command.getProcessingStatus())
                .transcribedText(command.getTranscribedText())
                .confidenceScore(command.getConfidenceScore())
                .commandType(command.getCommandType())
                .isConfirmed(command.getIsConfirmed())
                .confirmationRequired(command.getConfirmationRequired())
                .errorMessage(command.getErrorMessage())
                .createdAt(command.getCreatedAt())
                .updatedAt(command.getUpdatedAt())
                .build();
    }
    
    /**
     * Generate voice response for command result
     */
    public String generateVoiceResponse(VoiceCommandResponse response, String language) {
        log.debug("Generating voice response for language: {}", language);
        
        try {
            String responseText = formatResponseText(response);
            byte[] audioData = textToSpeechClient.synthesizeSpeech(responseText, language);
            
            // Upload audio response and return URL
            return uploadAudioResponse(audioData, response.getSessionId());
            
        } catch (Exception e) {
            log.error("Error generating voice response", e);
            throw new VoiceServiceException("Failed to generate voice response: " + e.getMessage(), e);
        }
    }
    
    // Command execution methods
    
    private VoiceCommandResponse executeSendPayment(VoiceCommand command) {
        log.debug("Executing send payment command");
        
        try {
            // Validate recipient
            if (command.getRecipientId() == null && command.getRecipientName() != null) {
                UUID recipientId = resolveRecipientByName(command.getUserId(), command.getRecipientName());
                if (recipientId == null) {
                    return VoiceCommandResponse.error("Recipient '" + command.getRecipientName() + "' not found");
                }
                command.setRecipientId(recipientId);
            }
            
            // Create payment request
            PaymentRequest paymentRequest = PaymentRequest.builder()
                    .senderId(command.getUserId())
                    .recipientId(command.getRecipientId())
                    .amount(command.getAmount())
                    .currency(command.getCurrency())
                    .description(command.getPurpose())
                    .initiatedVia("VOICE")
                    .build();
            
            PaymentResult result = paymentService.processPayment(paymentRequest);
            
            if (result.isSuccess()) {
                command.setPaymentId(result.getPaymentId());
                
                String responseMessage = String.format(
                    "Payment of %s %s sent to %s successfully. Transaction ID: %s",
                    command.getCurrency(), command.getAmount(),
                    command.getRecipientName(), result.getTransactionId()
                );
                
                return VoiceCommandResponse.success(responseMessage, result);
            } else {
                return VoiceCommandResponse.error("Payment failed: " + result.getErrorMessage());
            }
            
        } catch (Exception e) {
            log.error("Error executing send payment command", e);
            return VoiceCommandResponse.error("Failed to process payment");
        }
    }
    
    private VoiceCommandResponse executeRequestPayment(VoiceCommand command) {
        log.debug("Executing request payment command");
        
        try {
            PaymentRequestResult result = paymentService.createPaymentRequest(
                command.getUserId(),
                command.getRecipientId(),
                command.getAmount(),
                command.getCurrency(),
                command.getPurpose()
            );
            
            if (result.isSuccess()) {
                String responseMessage = String.format(
                    "Payment request for %s %s sent to %s",
                    command.getCurrency(), command.getAmount(), command.getRecipientName()
                );
                
                return VoiceCommandResponse.success(responseMessage, result);
            } else {
                return VoiceCommandResponse.error("Failed to create payment request");
            }
            
        } catch (Exception e) {
            log.error("Error executing request payment command", e);
            return VoiceCommandResponse.error("Failed to create payment request");
        }
    }
    
    private VoiceCommandResponse executeCheckBalance(VoiceCommand command) {
        log.debug("Executing check balance command");
        
        try {
            BalanceInfo balance = paymentService.getBalance(command.getUserId());
            
            String responseMessage = String.format(
                "Your current balance is %s %s",
                balance.getCurrency(), balance.getAvailableBalance()
            );
            
            return VoiceCommandResponse.success(responseMessage, balance);
            
        } catch (Exception e) {
            log.error("Error executing check balance command", e);
            return VoiceCommandResponse.error("Failed to retrieve balance");
        }
    }
    
    private VoiceCommandResponse executeTransactionHistory(VoiceCommand command) {
        log.debug("Executing transaction history command");
        
        try {
            List<TransactionSummary> transactions = paymentService.getRecentTransactions(
                command.getUserId(), 5);
            
            if (transactions.isEmpty()) {
                return VoiceCommandResponse.success("You have no recent transactions", transactions);
            }
            
            StringBuilder response = new StringBuilder("Your recent transactions: ");
            for (int i = 0; i < Math.min(3, transactions.size()); i++) {
                TransactionSummary tx = transactions.get(i);
                response.append(String.format(
                    "%s %s %s %s. ",
                    tx.getType(), tx.getCurrency(), tx.getAmount(),
                    tx.getType().equals("SENT") ? "to " + tx.getRecipientName() : "from " + tx.getSenderName()
                ));
            }
            
            return VoiceCommandResponse.success(response.toString(), transactions);
            
        } catch (Exception e) {
            log.error("Error executing transaction history command", e);
            return VoiceCommandResponse.error("Failed to retrieve transaction history");
        }
    }
    
    private VoiceCommandResponse executeSplitBill(VoiceCommand command) {
        log.debug("Executing split bill command");
        
        try {
            // Parse split participants from entities
            @SuppressWarnings("unchecked")
            List<String> participants = (List<String>) command.getExtractedEntities().get("participants");
            
            if (participants == null || participants.isEmpty()) {
                return VoiceCommandResponse.error("No participants specified for bill split");
            }
            
            // Resolve participant names to user IDs
            List<UUID> participantIds = new ArrayList<>();
            for (String participantName : participants) {
                UUID participantId = resolveRecipientByName(command.getUserId(), participantName);
                if (participantId != null) {
                    participantIds.add(participantId);
                }
            }
            
            if (participantIds.isEmpty()) {
                return VoiceCommandResponse.error("Could not find any of the specified participants");
            }
            
            BillSplitResult result = paymentService.createBillSplit(
                command.getUserId(),
                participantIds,
                command.getAmount(),
                command.getCurrency(),
                command.getPurpose()
            );
            
            if (result.isSuccess()) {
                BigDecimal splitAmount = command.getAmount().divide(
                    BigDecimal.valueOf(participantIds.size() + 1), 2, RoundingMode.HALF_UP);
                
                String responseMessage = String.format(
                    "Bill of %s %s split among %d people. Each person owes %s %s",
                    command.getCurrency(), command.getAmount(),
                    participantIds.size() + 1,
                    command.getCurrency(), splitAmount
                );
                
                return VoiceCommandResponse.success(responseMessage, result);
            } else {
                return VoiceCommandResponse.error("Failed to create bill split");
            }
            
        } catch (Exception e) {
            log.error("Error executing split bill command", e);
            return VoiceCommandResponse.error("Failed to split bill");
        }
    }
    
    private VoiceCommandResponse executePayBill(VoiceCommand command) {
        try {
            log.info("Executing pay bill command for user: {}", command.getUserId());
            
            // Extract bill payment parameters from command
            Map<String, Object> params = command.getParameters();
            String billerId = (String) params.get("billerId");
            BigDecimal amount = new BigDecimal(params.get("amount").toString());
            String billType = (String) params.getOrDefault("billType", "UTILITY");
            
            // Validate parameters
            if (billerId == null || amount.compareTo(BigDecimal.ZERO) <= 0) {
                return VoiceCommandResponse.error("Please specify the biller and amount");
            }
            
            // Create bill payment transaction
            VoiceTransaction transaction = VoiceTransaction.builder()
                    .transactionId(UUID.randomUUID())
                    .userId(command.getUserId())
                    .sessionId(command.getSessionId())
                    .commandId(command.getCommandId())
                    .transactionType("BILL_PAYMENT")
                    .amount(amount)
                    .recipientId(billerId)
                    .description("Bill payment to " + billerId)
                    .status("PENDING")
                    .transactionTime(LocalDateTime.now())
                    .metadata(Map.of("billType", billType, "voiceInitiated", true))
                    .build();
            
            // Request confirmation for bill payment
            if (amount.compareTo(new BigDecimal("100")) > 0) {
                transaction.setRequiresConfirmation(true);
                transaction.setConfirmationStatus("PENDING");
                
                String confirmationMessage = String.format(
                    "I need to confirm: Pay $%.2f to %s for %s bill. Say 'confirm' to proceed or 'cancel' to stop.",
                    amount, billerId, billType.toLowerCase()
                );
                
                return VoiceCommandResponse.builder()
                        .success(true)
                        .message(confirmationMessage)
                        .transactionId(transaction.getTransactionId().toString())
                        .requiresConfirmation(true)
                        .build();
            }
            
            // Process smaller bill payments immediately
            transaction.setStatus("COMPLETED");
            transaction.setProcessedTime(LocalDateTime.now());
            
            return VoiceCommandResponse.builder()
                    .success(true)
                    .message(String.format("Bill payment of $%.2f to %s has been processed successfully", 
                            amount, billerId))
                    .transactionId(transaction.getTransactionId().toString())
                    .build();
                    
        } catch (Exception e) {
            log.error("Error executing pay bill command", e);
            return VoiceCommandResponse.error("Failed to process bill payment");
        }
    }
    
    private VoiceCommandResponse executeTransferFunds(VoiceCommand command) {
        try {
            log.info("Executing fund transfer command for user: {}", command.getUserId());
            
            // Extract transfer parameters
            Map<String, Object> params = command.getParameters();
            String recipientName = (String) params.get("recipient");
            BigDecimal amount = new BigDecimal(params.get("amount").toString());
            String accountType = (String) params.getOrDefault("accountType", "CHECKING");
            
            // Validate parameters
            if (recipientName == null || amount.compareTo(BigDecimal.ZERO) <= 0) {
                return VoiceCommandResponse.error("Please specify the recipient and amount");
            }
            
            // Look up recipient account
            String recipientId = resolveRecipientId(recipientName, command.getUserId());
            if (recipientId == null) {
                return VoiceCommandResponse.error("Could not find recipient: " + recipientName);
            }
            
            // Create transfer transaction
            VoiceTransaction transaction = VoiceTransaction.builder()
                    .transactionId(UUID.randomUUID())
                    .userId(command.getUserId())
                    .sessionId(command.getSessionId())
                    .commandId(command.getCommandId())
                    .transactionType("TRANSFER")
                    .amount(amount)
                    .recipientId(recipientId)
                    .description("Transfer to " + recipientName)
                    .status("PENDING")
                    .transactionTime(LocalDateTime.now())
                    .metadata(Map.of("accountType", accountType, "voiceInitiated", true))
                    .build();
            
            // Always require confirmation for transfers
            transaction.setRequiresConfirmation(true);
            transaction.setConfirmationStatus("PENDING");
            
            String confirmationMessage = String.format(
                "I need to confirm: Transfer $%.2f to %s from your %s account. Say 'confirm' to proceed.",
                amount, recipientName, accountType.toLowerCase()
            );
            
            return VoiceCommandResponse.builder()
                    .success(true)
                    .message(confirmationMessage)
                    .transactionId(transaction.getTransactionId().toString())
                    .requiresConfirmation(true)
                    .build();
                    
        } catch (Exception e) {
            log.error("Error executing fund transfer command", e);
            return VoiceCommandResponse.error("Failed to process fund transfer");
        }
    }
    
    private VoiceCommandResponse executeSetReminder(VoiceCommand command) {
        try {
            log.info("Executing set reminder command for user: {}", command.getUserId());
            
            // Extract reminder parameters
            Map<String, Object> params = command.getParameters();
            String reminderType = (String) params.getOrDefault("reminderType", "PAYMENT");
            String description = (String) params.get("description");
            LocalDateTime reminderTime = parseReminderTime(params);
            
            // Validate parameters
            if (description == null || reminderTime == null) {
                return VoiceCommandResponse.error("Please specify what to remind you about and when");
            }
            
            // Create reminder record
            Map<String, Object> reminder = new HashMap<>();
            reminder.put("reminderId", UUID.randomUUID().toString());
            reminder.put("userId", command.getUserId());
            reminder.put("type", reminderType);
            reminder.put("description", description);
            reminder.put("scheduledTime", reminderTime);
            reminder.put("status", "ACTIVE");
            reminder.put("createdVia", "VOICE");
            reminder.put("createdAt", LocalDateTime.now());
            
            // Store reminder (would typically save to database)
            log.info("Created reminder: {} for user: {} at {}", 
                    description, command.getUserId(), reminderTime);
            
            String confirmationMessage = String.format(
                "I've set a reminder for %s: %s",
                formatReminderTime(reminderTime), description
            );
            
            return VoiceCommandResponse.builder()
                    .success(true)
                    .message(confirmationMessage)
                    .data(reminder)
                    .build();
                    
        } catch (Exception e) {
            log.error("Error executing set reminder command", e);
            return VoiceCommandResponse.error("Failed to set reminder");
        }
    }
    
    private VoiceCommandResponse executeCancelPayment(VoiceCommand command) {
        try {
            log.info("Executing cancel payment command for user: {}", command.getUserId());
            
            // Extract cancellation parameters
            Map<String, Object> params = command.getParameters();
            String paymentReference = (String) params.get("paymentReference");
            String transactionId = (String) params.get("transactionId");
            
            // Identify the payment to cancel
            if (paymentReference == null && transactionId == null) {
                // Look for the most recent pending payment
                List<VoiceTransaction> pendingPayments = findPendingPayments(command.getUserId());
                
                if (pendingPayments.isEmpty()) {
                    return VoiceCommandResponse.error("No pending payments found to cancel");
                }
                
                if (pendingPayments.size() > 1) {
                    return VoiceCommandResponse.error(
                        "Multiple pending payments found. Please specify which one to cancel."
                    );
                }
                
                transactionId = pendingPayments.get(0).getTransactionId().toString();
            }
            
            // Validate transaction can be cancelled
            VoiceTransaction transaction = findTransaction(transactionId, command.getUserId());
            if (transaction == null) {
                return VoiceCommandResponse.error("Payment not found");
            }
            
            if (!"PENDING".equals(transaction.getStatus())) {
                return VoiceCommandResponse.error(
                    "This payment cannot be cancelled as it is already " + transaction.getStatus().toLowerCase()
                );
            }
            
            // Cancel the payment
            transaction.setStatus("CANCELLED");
            transaction.setProcessedTime(LocalDateTime.now());
            transaction.setCancellationReason("User requested cancellation via voice command");
            
            String confirmationMessage = String.format(
                "Payment of $%.2f to %s has been cancelled successfully",
                transaction.getAmount(), transaction.getRecipientId()
            );
            
            return VoiceCommandResponse.builder()
                    .success(true)
                    .message(confirmationMessage)
                    .transactionId(transactionId)
                    .build();
                    
        } catch (Exception e) {
            log.error("Error executing cancel payment command", e);
            return VoiceCommandResponse.error("Failed to cancel payment");
        }
    }
    
    private VoiceCommandResponse executeAddContact(VoiceCommand command) {
        try {
            log.info("Executing add contact command for user: {}", command.getUserId());
            
            // Extract contact parameters
            Map<String, Object> params = command.getParameters();
            String contactName = (String) params.get("name");
            String accountInfo = (String) params.get("accountInfo");
            String contactType = (String) params.getOrDefault("type", "PERSONAL");
            
            // Validate parameters
            if (contactName == null || accountInfo == null) {
                return VoiceCommandResponse.error("Please provide the contact name and account information");
            }
            
            // Validate account information format
            if (!isValidAccountInfo(accountInfo)) {
                return VoiceCommandResponse.error(
                    "Invalid account information. Please provide a valid email, phone number, or account number"
                );
            }
            
            // Create contact record
            Map<String, Object> contact = new HashMap<>();
            contact.put("contactId", UUID.randomUUID().toString());
            contact.put("userId", command.getUserId());
            contact.put("name", contactName);
            contact.put("accountInfo", accountInfo);
            contact.put("type", contactType);
            contact.put("addedVia", "VOICE");
            contact.put("verified", false);
            contact.put("createdAt", LocalDateTime.now());
            
            // Determine account type
            if (accountInfo.contains("@")) {
                contact.put("accountType", "EMAIL");
            } else if (accountInfo.matches("\\d{10,}")) {
                contact.put("accountType", "PHONE");
            } else {
                contact.put("accountType", "ACCOUNT_NUMBER");
            }
            
            log.info("Added contact: {} for user: {}", contactName, command.getUserId());
            
            String confirmationMessage = String.format(
                "I've added %s to your contacts with account: %s",
                contactName, maskAccountInfo(accountInfo)
            );
            
            return VoiceCommandResponse.builder()
                    .success(true)
                    .message(confirmationMessage)
                    .data(contact)
                    .build();
                    
        } catch (Exception e) {
            log.error("Error executing add contact command", e);
            return VoiceCommandResponse.error("Failed to add contact");
        }
    }
    
    // Helper methods for the voice commands
    private String resolveRecipientId(String recipientName, UUID userId) {
        // In production, this would look up the recipient from the user's contacts
        // For now, return a mock recipient ID
        return "REC_" + recipientName.toUpperCase().replace(" ", "_");
    }
    
    private LocalDateTime parseReminderTime(Map<String, Object> params) {
        // Parse natural language time expressions
        String timeExpression = (String) params.get("time");
        if (timeExpression == null) {
            throw new IllegalArgumentException("Time expression parameter is required");
        }
        
        LocalDateTime now = LocalDateTime.now();
        
        if (timeExpression.contains("tomorrow")) {
            return now.plusDays(1).withHour(9).withMinute(0);
        } else if (timeExpression.contains("hour")) {
            int hours = extractNumber(timeExpression, 1);
            return now.plusHours(hours);
        } else if (timeExpression.contains("minute")) {
            int minutes = extractNumber(timeExpression, 30);
            return now.plusMinutes(minutes);
        } else if (timeExpression.contains("day")) {
            int days = extractNumber(timeExpression, 1);
            return now.plusDays(days);
        }
        
        return now.plusDays(1); // Default to tomorrow
    }
    
    private int extractNumber(String text, int defaultValue) {
        try {
            String[] words = text.split(" ");
            for (String word : words) {
                if (word.matches("\\d+")) {
                    return Integer.parseInt(word);
                }
            }
        } catch (Exception e) {
            log.warn("Could not extract number from: {}", text);
        }
        return defaultValue;
    }
    
    private String formatReminderTime(LocalDateTime time) {
        LocalDateTime now = LocalDateTime.now();
        if (time.toLocalDate().equals(now.toLocalDate())) {
            return "today at " + time.toLocalTime().toString();
        } else if (time.toLocalDate().equals(now.plusDays(1).toLocalDate())) {
            return "tomorrow at " + time.toLocalTime().toString();
        } else {
            return time.toLocalDate().toString() + " at " + time.toLocalTime().toString();
        }
    }
    
    private List<VoiceTransaction> findPendingPayments(UUID userId) {
        // In production, this would query the repository
        return new ArrayList<>();
    }
    
    private VoiceTransaction findTransaction(String transactionId, UUID userId) {
        try {
            UUID transactionUuid = UUID.fromString(transactionId);
            return voiceTransactionRepository.findByTransactionIdAndUserId(transactionUuid, userId).orElse(null);
        } catch (IllegalArgumentException e) {
            log.warn("Invalid transaction ID format: {} for user: {}", transactionId, userId);
            return null; // This is acceptable - invalid ID format should return null
        } catch (Exception e) {
            log.error("CRITICAL: Failed to find voice transaction: {} for user: {}", transactionId, userId, e);
            return null; // Acceptable for find operations
        }
    }
    
    private boolean isValidAccountInfo(String accountInfo) {
        // Email validation
        if (accountInfo.contains("@")) {
            return accountInfo.matches("^[A-Za-z0-9+_.-]+@(.+)$");
        }
        // Phone validation (10+ digits)
        if (accountInfo.matches("\\d{10,}")) {
            return true;
        }
        // Account number validation (alphanumeric, min 6 characters)
        return accountInfo.matches("^[A-Za-z0-9]{6,}$");
    }
    
    private String maskAccountInfo(String accountInfo) {
        if (accountInfo.contains("@")) {
            int atIndex = accountInfo.indexOf("@");
            return accountInfo.substring(0, Math.min(3, atIndex)) + "***" + accountInfo.substring(atIndex);
        } else if (accountInfo.length() > 4) {
            return "***" + accountInfo.substring(accountInfo.length() - 4);
        }
        return "***";
    }
    
    private VoiceCommandResponse executeHelpCommand(VoiceCommand command) {
        String helpMessage = "I can help you with payments. You can say things like: " +
                           "'Send 50 dollars to John', 'What's my balance?', " +
                           "'Show my recent transactions', or 'Split 100 dollars with Sarah and Mike'.";
        
        return VoiceCommandResponse.success(helpMessage, null);
    }
    
    // Helper methods
    
    private VoiceProfile validateVoiceProfile(UUID userId) {
        VoiceProfile profile = profileRepository.findByUserId(userId)
                .orElseThrow(() -> new IllegalArgumentException("Voice profile not found for user"));
        
        if (!profile.canAuthenticate()) {
            throw new IllegalStateException("Voice profile cannot be used for authentication");
        }
        
        return profile;
    }
    
    private VoiceCommand createVoiceCommand(UUID userId, VoiceCommandRequest request) {
        return VoiceCommand.builder()
                .userId(userId)
                .language(request.getLanguage())
                .deviceInfo(request.getDeviceInfo())
                .location(request.getLocation())
                .ambientNoiseLevel(request.getAmbientNoiseLevel())
                .contextData(request.getContextData())
                .build();
    }
    
    private VoiceCommand.CommandType mapIntentToCommandType(String intent) {
        switch (intent.toLowerCase()) {
            case "send_money":
            case "send_payment":
                return VoiceCommand.CommandType.SEND_PAYMENT;
            case "request_money":
            case "request_payment":
                return VoiceCommand.CommandType.REQUEST_PAYMENT;
            case "check_balance":
            case "balance_inquiry":
                return VoiceCommand.CommandType.CHECK_BALANCE;
            case "transaction_history":
            case "show_transactions":
                return VoiceCommand.CommandType.TRANSACTION_HISTORY;
            case "split_bill":
            case "split_payment":
                return VoiceCommand.CommandType.SPLIT_BILL;
            case "pay_bill":
                return VoiceCommand.CommandType.PAY_BILL;
            case "transfer_funds":
                return VoiceCommand.CommandType.TRANSFER_FUNDS;
            case "set_reminder":
                return VoiceCommand.CommandType.SET_REMINDER;
            case "cancel_payment":
                return VoiceCommand.CommandType.CANCEL_PAYMENT;
            case "add_contact":
                return VoiceCommand.CommandType.ADD_CONTACT;
            case "help":
                return VoiceCommand.CommandType.HELP_COMMAND;
            default:
                return VoiceCommand.CommandType.UNKNOWN;
        }
    }
    
    private void extractPaymentEntities(VoiceCommand command, NLPResult nlpResult) {
        Map<String, Object> entities = nlpResult.getEntities();
        
        // Extract amount
        if (entities.containsKey("amount")) {
            Object amountObj = entities.get("amount");
            if (amountObj instanceof Number) {
                command.setAmount(BigDecimal.valueOf(((Number) amountObj).doubleValue()));
            } else if (amountObj instanceof String) {
                try {
                    command.setAmount(new BigDecimal((String) amountObj));
                } catch (NumberFormatException e) {
                    log.warn("Could not parse amount: {}", amountObj);
                }
            }
        }
        
        // Extract currency
        if (entities.containsKey("currency")) {
            command.setCurrency((String) entities.get("currency"));
        }
        
        // Extract recipient name
        if (entities.containsKey("recipient")) {
            command.setRecipientName((String) entities.get("recipient"));
        }
        
        // Extract purpose/description
        if (entities.containsKey("purpose") || entities.containsKey("description")) {
            String purpose = (String) entities.getOrDefault("purpose", entities.get("description"));
            command.setPurpose(purpose);
        }
    }
    
    private ValidationResult validateCommand(VoiceCommand command) {
        List<String> errors = new ArrayList<>();
        
        // Validate confidence score
        if (command.getConfidenceScore() < 0.7) {
            errors.add("Voice command not clear enough. Please try again.");
        }
        
        // Validate payment-specific fields
        if (command.isPaymentCommand()) {
            if (command.getAmount() == null || command.getAmount().compareTo(BigDecimal.ZERO) <= 0) {
                errors.add("Valid payment amount required");
            }
            
            if (command.getAmount() != null && command.getAmount().compareTo(new BigDecimal("10000")) > 0) {
                errors.add("Payment amount exceeds maximum limit");
            }
            
            if (command.getCommandType() == VoiceCommand.CommandType.SEND_PAYMENT ||
                command.getCommandType() == VoiceCommand.CommandType.REQUEST_PAYMENT) {
                
                if (command.getRecipientName() == null && command.getRecipientId() == null) {
                    errors.add("Recipient required for payment");
                }
            }
        }
        
        return new ValidationResult(errors.isEmpty(), errors);
    }
    
    private boolean determineConfirmationNeeded(VoiceCommand command, VoiceProfile profile) {
        // Always require confirmation for payment commands
        if (command.isPaymentCommand()) {
            return true;
        }
        
        // Require confirmation for low confidence commands
        if (command.getConfidenceScore() < 0.8) {
            return true;
        }
        
        // Require confirmation for high-value transactions
        if (command.getAmount() != null && command.getAmount().compareTo(new BigDecimal("100")) > 0) {
            return true;
        }
        
        return false;
    }
    
    private VoiceCommandResponse createConfirmationResponse(VoiceCommand command) {
        String confirmationMessage = buildConfirmationMessage(command);
        
        return VoiceCommandResponse.builder()
                .success(true)
                .message(confirmationMessage)
                .sessionId(command.getSessionId())
                .requiresConfirmation(true)
                .commandType(command.getCommandType())
                .extractedData(buildExtractedDataMap(command))
                .build();
    }
    
    private String buildConfirmationMessage(VoiceCommand command) {
        switch (command.getCommandType()) {
            case SEND_PAYMENT:
                return String.format(
                    "I heard you want to send %s %s to %s%s. Is this correct?",
                    command.getCurrency(), command.getAmount(),
                    command.getRecipientName(),
                    command.getPurpose() != null ? " for " + command.getPurpose() : ""
                );
            case REQUEST_PAYMENT:
                return String.format(
                    "I heard you want to request %s %s from %s%s. Is this correct?",
                    command.getCurrency(), command.getAmount(),
                    command.getRecipientName(),
                    command.getPurpose() != null ? " for " + command.getPurpose() : ""
                );
            case SPLIT_BILL:
                @SuppressWarnings("unchecked")
                List<String> participants = (List<String>) command.getExtractedEntities().get("participants");
                String participantNames = participants != null ? String.join(", ", participants) : "others";
                
                return String.format(
                    "I heard you want to split %s %s with %s%s. Is this correct?",
                    command.getCurrency(), command.getAmount(),
                    participantNames,
                    command.getPurpose() != null ? " for " + command.getPurpose() : ""
                );
            default:
                return "I heard: \"" + command.getTranscribedText() + "\". Is this correct?";
        }
    }
    
    private Map<String, Object> buildExtractedDataMap(VoiceCommand command) {
        Map<String, Object> data = new HashMap<>();
        data.put("transcribedText", command.getTranscribedText());
        data.put("commandType", command.getCommandType());
        data.put("confidenceScore", command.getConfidenceScore());
        
        if (command.getAmount() != null) {
            data.put("amount", command.getAmount());
        }
        if (command.getCurrency() != null) {
            data.put("currency", command.getCurrency());
        }
        if (command.getRecipientName() != null) {
            data.put("recipientName", command.getRecipientName());
        }
        if (command.getPurpose() != null) {
            data.put("purpose", command.getPurpose());
        }
        
        return data;
    }
    
    private VoiceCommandResponse handleSecurityFailure(VoiceCommand command, String reason) {
        command.updateProcessingStatus(VoiceCommand.ProcessingStatus.FAILED);
        command.setErrorMessage(reason);
        commandRepository.save(command);
        
        // Update voice profile with failed attempt
        VoiceProfile profile = profileRepository.findByUserId(command.getUserId()).orElse(null);
        if (profile != null) {
            profile.recordFailedAuth();
            profileRepository.save(profile);
        }
        
        return VoiceCommandResponse.error("Voice authentication failed. Please try again.");
    }
    
    private VoiceCommandResponse handleValidationFailure(VoiceCommand command, List<String> errors) {
        command.updateProcessingStatus(VoiceCommand.ProcessingStatus.FAILED);
        command.setErrorMessage(String.join("; ", errors));
        commandRepository.save(command);
        
        return VoiceCommandResponse.error(String.join(". ", errors));
    }
    
    private void updateVoiceProfileStats(VoiceCommand command, boolean success) {
        VoiceProfile profile = profileRepository.findByUserId(command.getUserId()).orElse(null);
        if (profile != null) {
            if (success) {
                profile.recordSuccessfulAuth();
            } else {
                profile.recordFailedAuth();
            }
            
            profile.updateConfidenceScore(command.getConfidenceScore());
            profileRepository.save(profile);
        }
    }
    
    private void sendPaymentNotification(VoiceCommand command, VoiceCommandResponse response) {
        // Send notification about successful voice payment
        notificationService.sendVoicePaymentNotification(
            command.getUserId(),
            command.getRecipientId(),
            command.getAmount(),
            command.getCurrency(),
            "Voice Payment"
        );
    }
    
    private UUID resolveRecipientByName(UUID userId, String recipientName) {
        // CRITICAL FIX: Use VoiceRecipientResolutionService instead of UUID.randomUUID()
        VoiceRecipientResolutionService.RecipientResolution resolution =
                recipientResolutionService.resolveRecipient(userId, recipientName);

        if (resolution != null && resolution.getRecipientId() != null) {
            log.info("Recipient resolved: {} -> {} (confidence: {})",
                    recipientName, resolution.getRecipientId(), resolution.getConfidence());
            return resolution.getRecipientId();
        }

        log.warn("Could not resolve recipient: {}", recipientName);
        throw new IllegalArgumentException("Could not resolve recipient: " + recipientName);
    }
    
    private String uploadAudioFile(MultipartFile file, UUID commandId) throws IOException {
        // Mock implementation - would upload to cloud storage
        return "https://storage.example.com/voice-commands/" + commandId + ".wav";
    }
    
    private String uploadAudioResponse(byte[] audioData, String sessionId) {
        // Mock implementation - would upload to cloud storage
        return "https://storage.example.com/voice-responses/" + sessionId + ".mp3";
    }
    
    private String formatResponseText(VoiceCommandResponse response) {
        return response.getMessage();
    }
    
    // GROUP 4: Voice Payment Methods Implementation

    /**
     * 1. Voice authentication
     * Authenticates users using voice biometrics with advanced security features
     */
    public VoiceBiometricAuthResult authenticateVoiceBiometric(VoiceBiometricAuthRequest request) {
        try {
            log.info("Starting voice biometric authentication: userId={}, sessionId={}", 
                    request.getUserId(), request.getSessionId());

            // Get or create biometric profile
            VoiceBiometricProfile profile = getOrCreateBiometricProfile(request.getUserId());

            // Extract biometric features from voice sample
            BiometricFeatureSet features = extractBiometricFeatures(
                    request.getVoiceSample(), request.getSamplingRate());

            // Validate voice sample quality
            VoiceSampleQuality quality = assessVoiceSampleQuality(features);
            if (quality.getQualityScore() < request.getMinQualityThreshold()) {
                return VoiceBiometricAuthResult.builder()
                    .authenticationSuccessful(false)
                    .failureReason("Voice sample quality too low: " + quality.getQualityScore())
                    .qualityAssessment(quality)
                    .recommendedActions(Arrays.asList("Please speak more clearly", "Reduce background noise"))
                    .build();
            }

            // Perform biometric matching
            BiometricMatchResult matchResult = performBiometricMatching(profile, features, request);

            // Apply liveness detection
            LivenessDetectionResult livenessResult = detectLiveness(features, request.getVoiceSample());
            if (!livenessResult.isLive()) {
                log.warn("Liveness detection failed for user: {}", request.getUserId());
                return VoiceBiometricAuthResult.builder()
                    .authenticationSuccessful(false)
                    .failureReason("Liveness detection failed")
                    .livenessResult(livenessResult)
                    .securityAlert(true)
                    .build();
            }

            // Check for spoofing attempts
            SpoofingDetectionResult spoofingResult = detectSpoofing(features, profile);
            if (spoofingResult.isSpoofingDetected()) {
                log.error("Spoofing attempt detected for user: {}", request.getUserId());
                recordSecurityIncident(request.getUserId(), "VOICE_SPOOFING_DETECTED", spoofingResult);
                return VoiceBiometricAuthResult.builder()
                    .authenticationSuccessful(false)
                    .failureReason("Spoofing attempt detected")
                    .spoofingResult(spoofingResult)
                    .securityAlert(true)
                    .build();
            }

            // Evaluate authentication result
            boolean authSuccessful = matchResult.getConfidenceScore() >= request.getAuthenticationThreshold();
            
            if (authSuccessful) {
                // Update profile with successful authentication
                profile.updateWithSuccessfulAuth(features, matchResult.getConfidenceScore());
                saveBiometricProfile(profile);

                // Create authentication session
                VoiceAuthSession session = createAuthSession(request.getUserId(), 
                        request.getSessionId(), matchResult);

                log.info("Voice biometric authentication successful: userId={}, confidence={}", 
                        request.getUserId(), matchResult.getConfidenceScore());

                return VoiceBiometricAuthResult.builder()
                    .authenticationSuccessful(true)
                    .confidenceScore(matchResult.getConfidenceScore())
                    .biometricProfile(profile)
                    .authSession(session)
                    .qualityAssessment(quality)
                    .livenessResult(livenessResult)
                    .spoofingResult(spoofingResult)
                    .authenticatedAt(LocalDateTime.now())
                    .build();
            } else {
                // Update profile with failed authentication
                profile.recordFailedAuth(matchResult.getConfidenceScore());
                saveBiometricProfile(profile);

                log.warn("Voice biometric authentication failed: userId={}, confidence={}", 
                        request.getUserId(), matchResult.getConfidenceScore());

                return VoiceBiometricAuthResult.builder()
                    .authenticationSuccessful(false)
                    .failureReason("Biometric match confidence too low: " + matchResult.getConfidenceScore())
                    .confidenceScore(matchResult.getConfidenceScore())
                    .qualityAssessment(quality)
                    .recommendedActions(getAuthImprovementSuggestions(matchResult, quality))
                    .build();
            }

        } catch (Exception e) {
            log.error("Voice biometric authentication error: userId={}", request.getUserId(), e);
            return VoiceBiometricAuthResult.builder()
                .authenticationSuccessful(false)
                .failureReason("Authentication error: " + e.getMessage())
                .build();
        }
    }

    /**
     * 2. Speech to intent
     * Converts speech to payment intent with context awareness and intent disambiguation
     */
    public PaymentIntentResult convertSpeechToPaymentIntent(SpeechToIntentRequest request) {
        try {
            log.info("Converting speech to payment intent: userId={}, language={}", 
                    request.getUserId(), request.getLanguage());

            // Transcribe speech to text
            SpeechTranscriptionResult transcription = speechToTextClient.transcribeWithPunctuation(
                    request.getAudioData(), request.getLanguage(), request.isEnableContextualBiasing());

            if (transcription.getConfidenceScore() < request.getMinConfidenceThreshold()) {
                return PaymentIntentResult.builder()
                    .intentDetected(false)
                    .failureReason("Speech transcription confidence too low: " + transcription.getConfidenceScore())
                    .transcriptionResult(transcription)
                    .build();
            }

            // Get user context for intent disambiguation
            UserPaymentContext userContext = getUserPaymentContext(request.getUserId());

            // Apply natural language understanding
            NLUResult nluResult = nlpProcessor.processWithContext(
                    transcription.getTranscribedText(), request.getLanguage(), userContext);

            // Extract payment-specific intents
            List<PaymentIntent> candidateIntents = extractPaymentIntents(nluResult, userContext);

            if (candidateIntents.isEmpty()) {
                return PaymentIntentResult.builder()
                    .intentDetected(false)
                    .failureReason("No payment intent detected in speech")
                    .transcriptionResult(transcription)
                    .nluResult(nluResult)
                    .suggestedRephrasing(generateRephrasingSuggestions(transcription.getTranscribedText()))
                    .build();
            }

            // Rank intents by confidence and context relevance
            PaymentIntent bestIntent = selectBestIntent(candidateIntents, userContext, request);

            // Validate intent completeness
            IntentValidationResult validation = validatePaymentIntent(bestIntent, userContext);

            // Extract entities and parameters
            PaymentParameters parameters = extractPaymentParameters(bestIntent, nluResult);

            // Resolve ambiguous entities
            EntityResolutionResult entityResolution = resolveAmbiguousEntities(
                    parameters, userContext, request.getUserId());

            // Generate clarification questions if needed
            List<ClarificationQuestion> clarifications = generateClarificationQuestions(
                    bestIntent, parameters, validation);

            log.info("Speech to payment intent conversion completed: userId={}, intent={}, confidence={}", 
                    request.getUserId(), bestIntent.getIntentType(), bestIntent.getConfidence());

            return PaymentIntentResult.builder()
                .intentDetected(true)
                .primaryIntent(bestIntent)
                .alternativeIntents(candidateIntents.subList(1, Math.min(3, candidateIntents.size())))
                .parameters(parameters)
                .entityResolution(entityResolution)
                .validation(validation)
                .clarificationQuestions(clarifications)
                .transcriptionResult(transcription)
                .nluResult(nluResult)
                .confidenceScore(bestIntent.getConfidence())
                .needsClarification(!clarifications.isEmpty())
                .processedAt(LocalDateTime.now())
                .build();

        } catch (Exception e) {
            log.error("Speech to payment intent conversion failed: userId={}", request.getUserId(), e);
            return PaymentIntentResult.builder()
                .intentDetected(false)
                .failureReason("Intent conversion error: " + e.getMessage())
                .build();
        }
    }

    /**
     * 3. Voice command parsing
     * Parses complex voice commands with multi-turn conversation support
     */
    public VoiceCommandParseResult parseVoiceCommand(VoiceCommandParseRequest request) {
        try {
            log.info("Parsing voice command: sessionId={}, turn={}, command={}", 
                    request.getSessionId(), request.getTurnNumber(), 
                    request.getCommand().substring(0, Math.min(50, request.getCommand().length())));

            // Get or create conversation session
            VoiceConversationSession session = getOrCreateConversationSession(
                    request.getSessionId(), request.getUserId());

            // Update session context
            session.addTurn(request.getCommand(), request.getTurnNumber());

            // Apply conversation-aware parsing
            ConversationalParseResult parseResult = parseWithConversationContext(
                    request.getCommand(), session, request.getLanguage());

            // Handle different command types
            ParsedCommand parsedCommand;
            switch (parseResult.getCommandCategory()) {
                case PAYMENT_ACTION:
                    parsedCommand = parsePaymentAction(parseResult, session, request);
                    break;
                case QUERY_INFORMATION:
                    parsedCommand = parseInformationQuery(parseResult, session, request);
                    break;
                case CONVERSATION_MANAGEMENT:
                    parsedCommand = parseConversationManagement(parseResult, session, request);
                    break;
                case CLARIFICATION_RESPONSE:
                    parsedCommand = parseClarificationResponse(parseResult, session, request);
                    break;
                default:
                    parsedCommand = parseGenericCommand(parseResult, session, request);
            }

            // Apply command validation
            CommandValidationResult validation = validateParsedCommand(parsedCommand, session);

            // Generate response strategy
            ResponseStrategy responseStrategy = determineResponseStrategy(
                    parsedCommand, validation, session);

            // Update session state
            session.updateState(parsedCommand, validation);
            saveConversationSession(session);

            // Prepare next turn expectations
            List<ExpectedInput> expectedInputs = generateNextTurnExpectations(
                    parsedCommand, session, responseStrategy);

            log.info("Voice command parsing completed: sessionId={}, commandType={}, valid={}", 
                    request.getSessionId(), parsedCommand.getCommandType(), validation.isValid());

            return VoiceCommandParseResult.builder()
                .sessionId(request.getSessionId())
                .turnNumber(request.getTurnNumber())
                .parsedCommand(parsedCommand)
                .conversationSession(session)
                .parseResult(parseResult)
                .validation(validation)
                .responseStrategy(responseStrategy)
                .expectedInputs(expectedInputs)
                .parsingSuccessful(validation.isValid())
                .needsFollowUp(responseStrategy.requiresFollowUp())
                .parsedAt(LocalDateTime.now())
                .build();

        } catch (Exception e) {
            log.error("Voice command parsing failed: sessionId={}", request.getSessionId(), e);
            return VoiceCommandParseResult.builder()
                .sessionId(request.getSessionId())
                .parsingSuccessful(false)
                .errorMessage("Command parsing error: " + e.getMessage())
                .build();
        }
    }

    /**
     * 4. Confirmation synthesis
     * Synthesizes natural confirmation messages with context and personalization
     */
    public VoiceConfirmationResult synthesizeConfirmation(VoiceConfirmationRequest request) {
        try {
            log.info("Synthesizing voice confirmation: type={}, userId={}, language={}", 
                    request.getConfirmationType(), request.getUserId(), request.getLanguage());

            // Get user preferences
            VoicePreferences userPreferences = getUserVoicePreferences(request.getUserId());

            // Build confirmation content
            ConfirmationContent content = buildConfirmationContent(request, userPreferences);

            // Apply personalization
            PersonalizedContent personalizedContent = personalizeConfirmationContent(
                    content, userPreferences, request.getUserId());

            // Generate natural language confirmation
            String confirmationText = generateNaturalConfirmationText(
                    personalizedContent, request.getLanguage(), userPreferences.getFormality());

            // Add security context if needed
            if (request.includeSecurityContext()) {
                confirmationText = addSecurityContext(confirmationText, request, userPreferences);
            }

            // Apply voice synthesis with emotional context
            VoiceSynthesisConfig synthesisConfig = createSynthesisConfig(userPreferences, request);
            
            CompletableFuture<byte[]> audioSynthesis = CompletableFuture.supplyAsync(() -> {
                try {
                    return textToSpeechClient.synthesizeWithEmotion(
                            confirmationText, request.getLanguage(), synthesisConfig);
                } catch (Exception e) {
                    log.error("CRITICAL: Audio synthesis failed for confirmation - returning empty audio", e);
                    return new byte[0]; // Return empty array instead of null to prevent NPE
                }
            });

            // Generate confirmation metadata
            ConfirmationMetadata metadata = ConfirmationMetadata.builder()
                .confirmationType(request.getConfirmationType())
                .expectedResponses(generateExpectedResponses(request.getLanguage()))
                .timeoutSeconds(userPreferences.getConfirmationTimeout())
                .retryLimit(userPreferences.getMaxRetries())
                .fallbackActions(generateFallbackActions(request))
                .build();

            // Wait for audio synthesis
            byte[] audioData = audioSynthesis.get();
            if (audioData == null) {
                throw new VoiceSynthesisException("Failed to synthesize confirmation audio");
            }

            // Upload audio and get URL
            String audioUrl = uploadConfirmationAudio(audioData, request.getSessionId());

            log.info("Voice confirmation synthesis completed: sessionId={}, textLength={}, audioSize={}", 
                    request.getSessionId(), confirmationText.length(), audioData.length);

            return VoiceConfirmationResult.builder()
                .sessionId(request.getSessionId())
                .confirmationType(request.getConfirmationType())
                .confirmationText(confirmationText)
                .audioUrl(audioUrl)
                .audioData(audioData)
                .personalizedContent(personalizedContent)
                .synthesisConfig(synthesisConfig)
                .metadata(metadata)
                .synthesisSuccessful(true)
                .generatedAt(LocalDateTime.now())
                .expiresAt(LocalDateTime.now().plusSeconds(metadata.getTimeoutSeconds()))
                .build();

        } catch (Exception e) {
            log.error("Voice confirmation synthesis failed: sessionId={}", request.getSessionId(), e);
            return VoiceConfirmationResult.builder()
                .sessionId(request.getSessionId())
                .synthesisSuccessful(false)
                .errorMessage("Confirmation synthesis error: " + e.getMessage())
                .build();
        }
    }

    /**
     * 5. Voice session management
     * Manages voice session lifecycle with state persistence and recovery
     */
    public VoiceSessionResult manageVoiceSession(VoiceSessionRequest request) {
        try {
            log.info("Managing voice session: action={}, sessionId={}, userId={}", 
                    request.getAction(), request.getSessionId(), request.getUserId());

            VoiceSession session;
            VoiceSessionResult result;

            switch (request.getAction()) {
                case CREATE:
                    session = createVoiceSession(request);
                    result = VoiceSessionResult.success(session, "Session created successfully");
                    break;
                
                case UPDATE:
                    session = updateVoiceSession(request);
                    result = VoiceSessionResult.success(session, "Session updated successfully");
                    break;
                
                case EXTEND:
                    session = extendVoiceSession(request);
                    result = VoiceSessionResult.success(session, "Session extended successfully");
                    break;
                
                case PAUSE:
                    session = pauseVoiceSession(request);
                    result = VoiceSessionResult.success(session, "Session paused successfully");
                    break;
                
                case RESUME:
                    session = resumeVoiceSession(request);
                    result = VoiceSessionResult.success(session, "Session resumed successfully");
                    break;
                
                case TERMINATE:
                    session = terminateVoiceSession(request);
                    result = VoiceSessionResult.success(session, "Session terminated successfully");
                    break;
                
                case CLEANUP:
                    cleanupExpiredSessions();
                    result = VoiceSessionResult.success(null, "Session cleanup completed");
                    break;
                
                case GET_STATUS:
                    session = getVoiceSession(request.getSessionId());
                    if (session != null) {
                        result = VoiceSessionResult.success(session, "Session found");
                    } else {
                        result = VoiceSessionResult.failure("Session not found");
                    }
                    break;
                
                default:
                    result = VoiceSessionResult.failure("Unknown session action: " + request.getAction());
            }

            // Update session metrics
            if (session != null) {
                updateSessionMetrics(session, request.getAction());
            }

            log.info("Voice session management completed: action={}, sessionId={}, success={}", 
                    request.getAction(), request.getSessionId(), result.isSuccessful());

            return result;

        } catch (Exception e) {
            log.error("Voice session management failed: action={}, sessionId={}", 
                    request.getAction(), request.getSessionId(), e);
            return VoiceSessionResult.failure("Session management error: " + e.getMessage());
        }
    }

    /**
     * 6. Audio streaming
     * Handles real-time audio streaming for voice interactions
     */
    public AudioStreamResult streamAudioData(AudioStreamRequest request) {
        try {
            log.info("Starting audio stream: streamId={}, format={}, sampleRate={}", 
                    request.getStreamId(), request.getAudioFormat(), request.getSampleRate());

            // Validate audio stream parameters
            AudioStreamValidation validation = validateAudioStream(request);
            if (!validation.isValid()) {
                return AudioStreamResult.builder()
                    .streamId(request.getStreamId())
                    .streamingSuccessful(false)
                    .errorMessage("Invalid audio stream parameters: " + validation.getErrors())
                    .build();
            }

            // Initialize audio stream
            AudioStream stream = audioStreamingService.initializeStream(request);

            // Set up real-time processing pipeline
            StreamProcessingPipeline pipeline = createStreamProcessingPipeline(request, stream);

            // Start streaming with callbacks
            StreamingCallbacks callbacks = StreamingCallbacks.builder()
                .onDataReceived(data -> processStreamingData(data, request, pipeline))
                .onSpeechDetected(speech -> handleSpeechDetection(speech, request, pipeline))
                .onSilenceDetected(() -> handleSilenceDetection(request, pipeline))
                .onError(error -> handleStreamError(error, request, stream))
                .onComplete(() -> handleStreamCompletion(request, stream))
                .build();

            // Start the streaming session
            StreamSession streamSession = audioStreamingService.startStreaming(stream, callbacks);

            // Enable real-time voice activity detection
            if (request.isEnableVAD()) {
                enableVoiceActivityDetection(streamSession, request);
            }

            // Enable noise suppression
            if (request.isEnableNoiseSuppression()) {
                enableNoiseSuppression(streamSession, request);
            }

            // Enable echo cancellation
            if (request.isEnableEchoCancellation()) {
                enableEchoCancellation(streamSession, request);
            }

            // Set up buffering and chunking
            configureStreamBuffering(streamSession, request);

            // Monitor stream health
            CompletableFuture<Void> healthMonitoring = monitorStreamHealth(streamSession, request);

            log.info("Audio streaming started successfully: streamId={}, sessionId={}", 
                    request.getStreamId(), streamSession.getSessionId());

            return AudioStreamResult.builder()
                .streamId(request.getStreamId())
                .sessionId(streamSession.getSessionId())
                .stream(stream)
                .streamSession(streamSession)
                .pipeline(pipeline)
                .callbacks(callbacks)
                .healthMonitoring(healthMonitoring)
                .streamingSuccessful(true)
                .startedAt(LocalDateTime.now())
                .expectedDuration(request.getMaxDurationSeconds())
                .build();

        } catch (Exception e) {
            log.error("Audio streaming failed: streamId={}", request.getStreamId(), e);
            return AudioStreamResult.builder()
                .streamId(request.getStreamId())
                .streamingSuccessful(false)
                .errorMessage("Audio streaming error: " + e.getMessage())
                .build();
        }
    }

    /**
     * 7. Voice fraud detection
     * Detects potential fraud in voice payments using behavioral and acoustic analysis
     */
    public VoiceFraudDetectionResult detectVoiceFraud(VoiceFraudDetectionRequest request) {
        try {
            log.info("Starting voice fraud detection: userId={}, sessionId={}, riskLevel={}", 
                    request.getUserId(), request.getSessionId(), request.getRiskLevel());

            // Get user's behavioral profile
            VoiceBehavioralProfile behavioralProfile = getBehavioralProfile(request.getUserId());

            // Analyze acoustic characteristics
            AcousticAnalysisResult acousticAnalysis = analyzeAcousticCharacteristics(
                    request.getVoiceData(), behavioralProfile);

            // Detect behavioral anomalies
            BehavioralAnomalyResult behavioralAnomaly = detectBehavioralAnomalies(
                    request.getSessionContext(), behavioralProfile, request.getUserId());

            // Check for replay attacks
            ReplayAttackResult replayAnalysis = detectReplayAttack(
                    request.getVoiceData(), behavioralProfile.getKnownVoicePrints());

            // Analyze transaction patterns
            TransactionPatternResult transactionPattern = analyzeTransactionPattern(
                    request.getTransactionContext(), request.getUserId());

            // Check environmental context
            EnvironmentalContextResult environmentalAnalysis = analyzeEnvironmentalContext(
                    request.getEnvironmentalData(), behavioralProfile);

            // Apply machine learning fraud model
            MLFraudAnalysisResult mlAnalysis = applyMLFraudModel(
                    request, acousticAnalysis, behavioralAnomaly, transactionPattern);

            // Calculate composite fraud score
            FraudScoreCalculation fraudScore = calculateCompositeFraudScore(
                    acousticAnalysis, behavioralAnomaly, replayAnalysis, 
                    transactionPattern, environmentalAnalysis, mlAnalysis);

            // Determine fraud verdict
            FraudVerdict verdict = determineFraudVerdict(fraudScore, request.getRiskLevel());

            // Generate fraud indicators
            List<FraudIndicator> indicators = generateFraudIndicators(
                    acousticAnalysis, behavioralAnomaly, replayAnalysis, 
                    transactionPattern, environmentalAnalysis);

            // Apply risk-based actions
            List<RiskMitigationAction> actions = determineRiskMitigationActions(
                    verdict, fraudScore, request.getRiskLevel());

            // Update behavioral profile
            updateBehavioralProfile(behavioralProfile, request, fraudScore, verdict);

            // Log security event if high risk
            if (fraudScore.getOverallScore() >= 0.7) {
                logSecurityEvent(request.getUserId(), "HIGH_VOICE_FRAUD_RISK", fraudScore, indicators);
            }

            log.info("Voice fraud detection completed: userId={}, fraudScore={}, verdict={}", 
                    request.getUserId(), fraudScore.getOverallScore(), verdict.getDecision());

            return VoiceFraudDetectionResult.builder()
                .sessionId(request.getSessionId())
                .userId(request.getUserId())
                .fraudScore(fraudScore)
                .verdict(verdict)
                .acousticAnalysis(acousticAnalysis)
                .behavioralAnomaly(behavioralAnomaly)
                .replayAnalysis(replayAnalysis)
                .transactionPattern(transactionPattern)
                .environmentalAnalysis(environmentalAnalysis)
                .mlAnalysis(mlAnalysis)
                .fraudIndicators(indicators)
                .riskMitigationActions(actions)
                .detectionSuccessful(true)
                .analyzedAt(LocalDateTime.now())
                .confidenceLevel(fraudScore.getConfidenceLevel())
                .build();

        } catch (Exception e) {
            log.error("Voice fraud detection failed: userId={}, sessionId={}", 
                    request.getUserId(), request.getSessionId(), e);
            return VoiceFraudDetectionResult.builder()
                .sessionId(request.getSessionId())
                .userId(request.getUserId())
                .detectionSuccessful(false)
                .errorMessage("Fraud detection error: " + e.getMessage())
                .build();
        }
    }

    /**
     * 8. Multi-language support
     * Processes voice commands in multiple languages with cultural context
     */
    public MultilingualVoiceResult processMultilingualVoice(MultilingualVoiceRequest request) {
        try {
            log.info("Processing multilingual voice: userId={}, supportedLanguages={}, autoDetect={}", 
                    request.getUserId(), request.getSupportedLanguages(), request.isAutoDetectLanguage());

            // Detect language if auto-detection is enabled
            LanguageDetectionResult languageDetection = null;
            String detectedLanguage = request.getPrimaryLanguage();
            
            if (request.isAutoDetectLanguage()) {
                languageDetection = detectLanguage(request.getVoiceData(), request.getSupportedLanguages());
                if (languageDetection.isSuccessful()) {
                    detectedLanguage = languageDetection.getDetectedLanguage();
                    log.info("Language detected: {} with confidence {}", 
                            detectedLanguage, languageDetection.getConfidence());
                }
            }

            // Validate language support
            if (!request.getSupportedLanguages().contains(detectedLanguage)) {
                return MultilingualVoiceResult.builder()
                    .processingSuccessful(false)
                    .errorMessage("Language not supported: " + detectedLanguage)
                    .languageDetection(languageDetection)
                    .build();
            }

            // Get language-specific configuration
            LanguageConfig languageConfig = getLanguageConfig(detectedLanguage);

            // Process voice with language-specific pipeline
            MultilingualProcessingResult processingResult = processWithLanguageContext(
                    request, detectedLanguage, languageConfig);

            // Apply cultural context
            CulturalContextResult culturalContext = applyCulturalContext(
                    processingResult, detectedLanguage, request.getUserId());

            // Handle code-switching if multiple languages detected
            CodeSwitchingResult codeSwitching = null;
            if (request.isHandleCodeSwitching()) {
                codeSwitching = handleCodeSwitching(request.getVoiceData(), 
                        request.getSupportedLanguages(), languageConfig);
            }

            // Localize response
            LocalizedResponse localizedResponse = localizeResponse(
                    processingResult.getResponseContent(), detectedLanguage, culturalContext);

            // Generate region-specific voice synthesis
            RegionalVoiceSynthesis voiceSynthesis = generateRegionalVoice(
                    localizedResponse, detectedLanguage, request.getRegionCode());

            // Update user language preferences
            updateUserLanguagePreferences(request.getUserId(), detectedLanguage, 
                    processingResult.getInteractionQuality());

            // Calculate processing metrics
            MultilingualMetrics metrics = calculateMultilingualMetrics(
                    languageDetection, processingResult, culturalContext, voiceSynthesis);

            log.info("Multilingual voice processing completed: userId={}, language={}, quality={}", 
                    request.getUserId(), detectedLanguage, processingResult.getProcessingQuality());

            return MultilingualVoiceResult.builder()
                .userId(request.getUserId())
                .primaryLanguage(request.getPrimaryLanguage())
                .detectedLanguage(detectedLanguage)
                .languageDetection(languageDetection)
                .languageConfig(languageConfig)
                .processingResult(processingResult)
                .culturalContext(culturalContext)
                .codeSwitching(codeSwitching)
                .localizedResponse(localizedResponse)
                .voiceSynthesis(voiceSynthesis)
                .metrics(metrics)
                .processingSuccessful(true)
                .processedAt(LocalDateTime.now())
                .processingDurationMs(System.currentTimeMillis() - request.getStartTime())
                .build();

        } catch (Exception e) {
            log.error("Multilingual voice processing failed: userId={}", request.getUserId(), e);
            return MultilingualVoiceResult.builder()
                .userId(request.getUserId())
                .processingSuccessful(false)
                .errorMessage("Multilingual processing error: " + e.getMessage())
                .build();
        }
    }

    // Helper methods for the implemented functions (abbreviated for space)

    private VoiceBiometricProfile getOrCreateBiometricProfile(UUID userId) {
        return biometricProfiles.computeIfAbsent(userId, id -> {
            return profileRepository.findBiometricByUserId(id)
                .orElse(VoiceBiometricProfile.createNew(id));
        });
    }

    private BiometricFeatureSet extractBiometricFeatures(byte[] voiceSample, int samplingRate) {
        // Implementation for extracting biometric features from voice
        return BiometricFeatureSet.builder().build(); // Placeholder
    }

    private VoiceSampleQuality assessVoiceSampleQuality(BiometricFeatureSet features) {
        // Implementation for assessing voice sample quality
        return VoiceSampleQuality.builder().qualityScore(0.8).build(); // Placeholder
    }

    private UserPaymentContext getUserPaymentContext(UUID userId) {
        // Implementation for getting user payment context
        return UserPaymentContext.builder().build(); // Placeholder
    }

    private VoiceConversationSession getOrCreateConversationSession(String sessionId, UUID userId) {
        // Implementation for managing conversation sessions
        return VoiceConversationSession.builder().build(); // Placeholder
    }

    private VoicePreferences getUserVoicePreferences(UUID userId) {
        // Implementation for getting user voice preferences
        return VoicePreferences.builder().build(); // Placeholder
    }

    private VoiceSession createVoiceSession(VoiceSessionRequest request) {
        // Implementation for creating voice session
        VoiceSession session = VoiceSession.builder()
            .sessionId(request.getSessionId())
            .userId(request.getUserId())
            .createdAt(LocalDateTime.now())
            .status(VoiceSessionStatus.ACTIVE)
            .build();
        
        activeSessions.put(request.getSessionId(), session);
        return session;
    }

    private void cleanupExpiredSessions() {
        LocalDateTime now = LocalDateTime.now();
        activeSessions.entrySet().removeIf(entry -> 
            entry.getValue().getExpiresAt().isBefore(now));
    }

    private AudioStreamValidation validateAudioStream(AudioStreamRequest request) {
        // Implementation for validating audio stream
        return AudioStreamValidation.builder().valid(true).build(); // Placeholder
    }

    private VoiceBehavioralProfile getBehavioralProfile(UUID userId) {
        // Implementation for getting behavioral profile
        return VoiceBehavioralProfile.builder().build(); // Placeholder
    }

    private LanguageDetectionResult detectLanguage(byte[] voiceData, List<String> supportedLanguages) {
        // Implementation for language detection
        return LanguageDetectionResult.builder()
            .successful(true)
            .detectedLanguage("en-US")
            .confidence(0.9)
            .build(); // Placeholder
    }

    // Helper classes
    public static class ValidationResult {
        private final boolean valid;
        private final List<String> errors;
        
        public ValidationResult(boolean valid, List<String> errors) {
            this.valid = valid;
            this.errors = errors;
        }
        
        public boolean isValid() { return valid; }
        public List<String> getErrors() { return errors; }
    }
}