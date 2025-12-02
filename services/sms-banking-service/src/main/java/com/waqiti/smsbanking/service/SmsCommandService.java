/**
 * SMS Command Service
 * Processes SMS banking commands and operations
 */
package com.waqiti.smsbanking.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.waqiti.smsbanking.entity.SmsSession;
import com.waqiti.smsbanking.repository.SmsSessionRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.Arrays;
import java.util.List;
import java.util.UUID;
import java.util.regex.Pattern;

@Service
@RequiredArgsConstructor
@Slf4j
@Transactional
public class SmsCommandService {
    
    private final SmsSessionRepository sessionRepository;
    private final CoreBankingService coreBankingService;
    private final SecurityService securityService;
    private final SmsBankingMfaService mfaService;
    private final ObjectMapper objectMapper;
    
    // SMS Command patterns
    private static final Pattern BALANCE_PATTERN = Pattern.compile("^(BAL|BALANCE)\\s+(\\d{4})$", Pattern.CASE_INSENSITIVE);
    private static final Pattern TRANSFER_PATTERN = Pattern.compile("^(SEND|TRANSFER)\\s+(\\+?\\d{10,15})\\s+(\\d+(?:\\.\\d{2})?)\\s+(\\d{4})$", Pattern.CASE_INSENSITIVE);
    private static final Pattern AIRTIME_PATTERN = Pattern.compile("^(AIRTIME|AIR)\\s+(\\+?\\d{10,15})\\s+(\\d+)\\s+(\\d{4})$", Pattern.CASE_INSENSITIVE);
    private static final Pattern LOAN_STATUS_PATTERN = Pattern.compile("^(LOAN|LN)\\s+(STATUS|STAT)\\s+(\\d{4})$", Pattern.CASE_INSENSITIVE);
    private static final Pattern LOAN_PAYMENT_PATTERN = Pattern.compile("^(LOAN|LN)\\s+(PAY|PAYMENT)\\s+(\\d+(?:\\.\\d{2})?)\\s+(\\d{4})$", Pattern.CASE_INSENSITIVE);
    private static final Pattern HELP_PATTERN = Pattern.compile("^(HELP|H|\\?)$", Pattern.CASE_INSENSITIVE);
    private static final Pattern STATEMENT_PATTERN = Pattern.compile("^(STMT|STATEMENT)\\s+(\\d{1,2})\\s+(\\d{4})$", Pattern.CASE_INSENSITIVE);
    
    public SmsResponse processSmsCommand(String phoneNumber, String message) {
        try {
            log.info("Processing SMS command from {}: {}", phoneNumber, message);
            
            // Rate limiting check
            if (!securityService.isAllowedSmsRequest(phoneNumber)) {
                return createErrorResponse("Too many requests. Please wait before sending another command.");
            }
            
            // Clean and validate message
            String cleanMessage = message.trim().toUpperCase();
            
            if (cleanMessage.isEmpty()) {
                return createHelpResponse();
            }
            
            // Process different command patterns
            if (HELP_PATTERN.matcher(cleanMessage).matches()) {
                return createHelpResponse();
            }
            
            if (BALANCE_PATTERN.matcher(cleanMessage).matches()) {
                return processBalanceCommand(phoneNumber, cleanMessage);
            }
            
            if (TRANSFER_PATTERN.matcher(cleanMessage).matches()) {
                return processTransferCommand(phoneNumber, cleanMessage);
            }
            
            if (AIRTIME_PATTERN.matcher(cleanMessage).matches()) {
                return processAirtimeCommand(phoneNumber, cleanMessage);
            }
            
            if (LOAN_STATUS_PATTERN.matcher(cleanMessage).matches()) {
                return processLoanStatusCommand(phoneNumber, cleanMessage);
            }
            
            if (LOAN_PAYMENT_PATTERN.matcher(cleanMessage).matches()) {
                return processLoanPaymentCommand(phoneNumber, cleanMessage);
            }
            
            if (STATEMENT_PATTERN.matcher(cleanMessage).matches()) {
                return processStatementCommand(phoneNumber, cleanMessage);
            }
            
            // Unknown command
            return createErrorResponse("Unknown command. Reply HELP for available commands.");
            
        } catch (Exception e) {
            log.error("Error processing SMS command from {}: {}", phoneNumber, message, e);
            return createErrorResponse("Service temporarily unavailable. Please try again later.");
        }
    }
    
    /**
     * Enhanced SMS processing with mandatory 2FA verification
     */
    public SmsResponse processSmsCommandWithMfa(String phoneNumber, String message, 
                                              String gatewayRef, String verificationCode) {
        try {
            log.info("Processing SMS command with MFA from {}", phoneNumber);
            
            // Basic rate limiting and validation
            if (!securityService.isAllowedSmsRequest(phoneNumber)) {
                return createErrorResponse("Too many requests. Please wait before sending another command.");
            }
            
            String cleanMessage = message.trim().toUpperCase();
            if (cleanMessage.isEmpty()) {
                return createHelpResponse();
            }
            
            // Handle 2FA setup and status commands (no MFA required)
            if (cleanMessage.equals("2FA SETUP")) {
                return process2faSetupCommand(phoneNumber);
            }
            
            if (cleanMessage.equals("2FA STATUS")) {
                return process2faStatusCommand(phoneNumber);
            }
            
            if (cleanMessage.equals("2FA CODE")) {
                return process2faCodeRequest(phoneNumber);
            }
            
            if (HELP_PATTERN.matcher(cleanMessage).matches()) {
                return createHelpResponse();
            }
            
            // Validate MFA for all other commands
            SmsBankingMfaService.MfaValidationResult mfaResult = 
                mfaService.validateSmsCommand(phoneNumber, cleanMessage, verificationCode);
            
            if (!mfaResult.isValid()) {
                if (mfaResult.isRequiresMfa()) {
                    return SmsResponse.builder()
                        .success(false)
                        .message(mfaResult.getMessage())
                        .requiresMfa(true)
                        .mfaCodeId(mfaResult.getCodeId())
                        .errorCode(mfaResult.getErrorCode())
                        .build();
                } else {
                    return createErrorResponse(mfaResult.getErrorMessage());
                }
            }
            
            // MFA validated - process the command
            log.info("MFA validation successful, processing command for {}", phoneNumber);
            
            // Update command patterns to expect 2FA code
            if (cleanMessage.matches("^(BAL|BALANCE)\\s+(\\d{4})\\s+(\\d{6})$")) {
                return processBalanceCommandWithMfa(phoneNumber, cleanMessage, mfaResult.getSessionToken());
            }
            
            if (cleanMessage.matches("^(SEND|TRANSFER)\\s+(\\+?\\d{10,15})\\s+(\\d+(?:\\.\\d{2})?)\\s+(\\d{4})\\s+(\\d{6})$")) {
                return processTransferCommandWithMfa(phoneNumber, cleanMessage, mfaResult.getSessionToken());
            }
            
            if (cleanMessage.matches("^(AIRTIME|AIR)\\s+(\\+?\\d{10,15})\\s+(\\d+)\\s+(\\d{4})\\s+(\\d{6})$")) {
                return processAirtimeCommandWithMfa(phoneNumber, cleanMessage, mfaResult.getSessionToken());
            }
            
            if (cleanMessage.matches("^(LOAN|LN)\\s+(STATUS|STAT)\\s+(\\d{4})\\s+(\\d{6})$")) {
                return processLoanStatusCommandWithMfa(phoneNumber, cleanMessage, mfaResult.getSessionToken());
            }
            
            if (cleanMessage.matches("^(LOAN|LN)\\s+(PAY|PAYMENT)\\s+(\\d+(?:\\.\\d{2})?)\\s+(\\d{4})\\s+(\\d{6})$")) {
                return processLoanPaymentCommandWithMfa(phoneNumber, cleanMessage, mfaResult.getSessionToken());
            }
            
            if (cleanMessage.matches("^(STMT|STATEMENT)\\s+(\\d{1,2})\\s+(\\d{4})\\s+(\\d{6})$")) {
                return processStatementCommandWithMfa(phoneNumber, cleanMessage, mfaResult.getSessionToken());
            }
            
            // Command doesn't match enhanced 2FA patterns
            return createErrorResponse("Invalid command format. All transactions require PIN + 2FA code. " +
                "Example: BAL 1234 567890. Reply HELP for details.");
            
        } catch (Exception e) {
            log.error("Error processing SMS command with MFA from {}: {}", phoneNumber, message, e);
            return createErrorResponse("Service temporarily unavailable. Please try again later.");
        }
    }
    
    private SmsResponse processBalanceCommand(String phoneNumber, String message) {
        try {
            // Extract PIN from command
            String[] parts = message.split("\\s+");
            String pin = parts[1];
            
            // Verify PIN
            if (!securityService.verifyPin(phoneNumber, pin)) {
                securityService.recordFailedPinAttempt(phoneNumber);
                return createErrorResponse("Incorrect PIN. Please try again.");
            }
            
            // Get user account
            UUID userId = coreBankingService.getUserIdByPhoneNumber(phoneNumber);
            if (userId == null) {
                return createErrorResponse("Account not found. Please contact customer service.");
            }
            
            // Get balance
            String balance = coreBankingService.getAccountBalance(userId);
            
            // Log transaction
            securityService.logSmsTransaction(phoneNumber, "BALANCE_INQUIRY", "SUCCESS");
            
            return SmsResponse.builder()
                .success(true)
                .message(String.format("Your account balance is: %s", balance))
                .build();
                
        } catch (Exception e) {
            log.error("Error processing balance command for {}", phoneNumber, e);
            securityService.logSmsTransaction(phoneNumber, "BALANCE_INQUIRY", "FAILED");
            return createErrorResponse("Unable to retrieve balance. Please try again later.");
        }
    }
    
    private SmsResponse processTransferCommand(String phoneNumber, String message) {
        try {
            // Parse command: SEND <recipient> <amount> <pin>
            String[] parts = message.split("\\s+");
            String recipient = parts[1];
            double amount = Double.parseDouble(parts[2]);
            String pin = parts[3];
            
            // Validate amount
            if (amount <= 0) {
                return createErrorResponse("Invalid amount. Amount must be greater than 0.");
            }
            
            // Verify PIN
            if (!securityService.verifyPin(phoneNumber, pin)) {
                securityService.recordFailedPinAttempt(phoneNumber);
                return createErrorResponse("Incorrect PIN. Transfer cancelled.");
            }
            
            // Get user accounts
            UUID senderId = coreBankingService.getUserIdByPhoneNumber(phoneNumber);
            UUID recipientId = coreBankingService.getUserIdByPhoneNumber(recipient);
            
            if (senderId == null) {
                return createErrorResponse("Sender account not found.");
            }
            
            if (recipientId == null) {
                return createErrorResponse("Recipient account not found.");
            }
            
            // Check balance
            double senderBalance = coreBankingService.getAccountBalanceAsDouble(senderId);
            if (senderBalance < amount) {
                return createErrorResponse("Insufficient balance for this transfer.");
            }
            
            // Process transfer
            String transactionRef = coreBankingService.processTransfer(senderId, recipientId, amount, "SMS Transfer");
            
            // Log transaction
            securityService.logSmsTransaction(phoneNumber, "TRANSFER", "SUCCESS");
            
            return SmsResponse.builder()
                .success(true)
                .message(String.format("Transfer successful! $%.2f sent to %s. Reference: %s", 
                    amount, recipient, transactionRef))
                .build();
                
        } catch (NumberFormatException e) {
            return createErrorResponse("Invalid amount format. Please use: SEND <phone> <amount> <pin>");
        } catch (Exception e) {
            log.error("Error processing transfer command for {}", phoneNumber, e);
            securityService.logSmsTransaction(phoneNumber, "TRANSFER", "FAILED");
            return createErrorResponse("Transfer failed. Please try again later.");
        }
    }
    
    private SmsResponse processAirtimeCommand(String phoneNumber, String message) {
        try {
            // Parse command: AIRTIME <recipient> <amount> <pin>
            String[] parts = message.split("\\s+");
            String recipient = parts[1];
            double amount = Double.parseDouble(parts[2]);
            String pin = parts[3];
            
            // Validate amount
            if (amount <= 0 || amount > 1000) {
                return createErrorResponse("Invalid airtime amount. Amount must be between $1 and $1000.");
            }
            
            // Verify PIN
            if (!securityService.verifyPin(phoneNumber, pin)) {
                securityService.recordFailedPinAttempt(phoneNumber);
                return createErrorResponse("Incorrect PIN. Airtime purchase cancelled.");
            }
            
            // Get user account
            UUID userId = coreBankingService.getUserIdByPhoneNumber(phoneNumber);
            if (userId == null) {
                return createErrorResponse("Account not found.");
            }
            
            // Check balance
            double balance = coreBankingService.getAccountBalanceAsDouble(userId);
            if (balance < amount) {
                return createErrorResponse("Insufficient balance for airtime purchase.");
            }
            
            // Process airtime purchase
            String transactionRef = coreBankingService.purchaseAirtime(userId, recipient, amount);
            
            // Log transaction
            securityService.logSmsTransaction(phoneNumber, "AIRTIME_PURCHASE", "SUCCESS");
            
            return SmsResponse.builder()
                .success(true)
                .message(String.format("Airtime purchase successful! $%.2f airtime sent to %s. Reference: %s", 
                    amount, recipient, transactionRef))
                .build();
                
        } catch (NumberFormatException e) {
            return createErrorResponse("Invalid amount format. Please use: AIRTIME <phone> <amount> <pin>");
        } catch (Exception e) {
            log.error("Error processing airtime command for {}", phoneNumber, e);
            securityService.logSmsTransaction(phoneNumber, "AIRTIME_PURCHASE", "FAILED");
            return createErrorResponse("Airtime purchase failed. Please try again later.");
        }
    }
    
    private SmsResponse processLoanStatusCommand(String phoneNumber, String message) {
        try {
            // Parse command: LOAN STATUS <pin>
            String[] parts = message.split("\\s+");
            String pin = parts[2];
            
            // Verify PIN
            if (!securityService.verifyPin(phoneNumber, pin)) {
                securityService.recordFailedPinAttempt(phoneNumber);
                return createErrorResponse("Incorrect PIN. Please try again.");
            }
            
            // Get user account
            UUID userId = coreBankingService.getUserIdByPhoneNumber(phoneNumber);
            if (userId == null) {
                return createErrorResponse("Account not found.");
            }
            
            // Get loan status
            String loanStatus = coreBankingService.getUserLoanStatus(userId);
            
            if (loanStatus == null || loanStatus.isEmpty()) {
                return SmsResponse.builder()
                    .success(true)
                    .message("No active loans found.")
                    .build();
            }
            
            // Log transaction
            securityService.logSmsTransaction(phoneNumber, "LOAN_STATUS", "SUCCESS");
            
            return SmsResponse.builder()
                .success(true)
                .message(loanStatus)
                .build();
                
        } catch (Exception e) {
            log.error("Error processing loan status command for {}", phoneNumber, e);
            securityService.logSmsTransaction(phoneNumber, "LOAN_STATUS", "FAILED");
            return createErrorResponse("Unable to retrieve loan status. Please try again later.");
        }
    }
    
    private SmsResponse processLoanPaymentCommand(String phoneNumber, String message) {
        try {
            // Parse command: LOAN PAY <amount> <pin>
            String[] parts = message.split("\\s+");
            double amount = Double.parseDouble(parts[2]);
            String pin = parts[3];
            
            // Validate amount
            if (amount <= 0) {
                return createErrorResponse("Invalid payment amount. Amount must be greater than 0.");
            }
            
            // Verify PIN
            if (!securityService.verifyPin(phoneNumber, pin)) {
                securityService.recordFailedPinAttempt(phoneNumber);
                return createErrorResponse("Incorrect PIN. Loan payment cancelled.");
            }
            
            // Get user account
            UUID userId = coreBankingService.getUserIdByPhoneNumber(phoneNumber);
            if (userId == null) {
                return createErrorResponse("Account not found.");
            }
            
            // Check if user has active loans
            if (!coreBankingService.hasActiveLoans(userId)) {
                return createErrorResponse("No active loans found for payment.");
            }
            
            // Check balance
            double balance = coreBankingService.getAccountBalanceAsDouble(userId);
            if (balance < amount) {
                return createErrorResponse("Insufficient balance for loan payment.");
            }
            
            // Process loan payment
            String transactionRef = coreBankingService.processLoanPayment(userId, amount, "SMS Payment");
            
            // Log transaction
            securityService.logSmsTransaction(phoneNumber, "LOAN_PAYMENT", "SUCCESS");
            
            return SmsResponse.builder()
                .success(true)
                .message(String.format("Loan payment successful! $%.2f paid. Reference: %s", 
                    amount, transactionRef))
                .build();
                
        } catch (NumberFormatException e) {
            return createErrorResponse("Invalid amount format. Please use: LOAN PAY <amount> <pin>");
        } catch (Exception e) {
            log.error("Error processing loan payment command for {}", phoneNumber, e);
            securityService.logSmsTransaction(phoneNumber, "LOAN_PAYMENT", "FAILED");
            return createErrorResponse("Loan payment failed. Please try again later.");
        }
    }
    
    private SmsResponse processStatementCommand(String phoneNumber, String message) {
        try {
            // Parse command: STMT <days> <pin>
            String[] parts = message.split("\\s+");
            int days = Integer.parseInt(parts[1]);
            String pin = parts[2];
            
            // Validate days
            if (days <= 0 || days > 90) {
                return createErrorResponse("Invalid period. Please specify 1-90 days.");
            }
            
            // Verify PIN
            if (!securityService.verifyPin(phoneNumber, pin)) {
                securityService.recordFailedPinAttempt(phoneNumber);
                return createErrorResponse("Incorrect PIN. Please try again.");
            }
            
            // Get user account
            UUID userId = coreBankingService.getUserIdByPhoneNumber(phoneNumber);
            if (userId == null) {
                return createErrorResponse("Account not found.");
            }
            
            // Get mini statement
            String statement = coreBankingService.getMiniStatement(userId, days);
            
            // Log transaction
            securityService.logSmsTransaction(phoneNumber, "MINI_STATEMENT", "SUCCESS");
            
            return SmsResponse.builder()
                .success(true)
                .message(statement)
                .build();
                
        } catch (NumberFormatException e) {
            return createErrorResponse("Invalid format. Please use: STMT <days> <pin>");
        } catch (Exception e) {
            log.error("Error processing statement command for {}", phoneNumber, e);
            securityService.logSmsTransaction(phoneNumber, "MINI_STATEMENT", "FAILED");
            return createErrorResponse("Unable to retrieve statement. Please try again later.");
        }
    }
    
    private SmsResponse createHelpResponse() {
        String helpText = "Waqiti SMS Banking Commands:\n" +
            "BAL <pin> - Check balance\n" +
            "SEND <phone> <amount> <pin> - Transfer money\n" +
            "AIRTIME <phone> <amount> <pin> - Buy airtime\n" +
            "LOAN STATUS <pin> - Check loan status\n" +
            "LOAN PAY <amount> <pin> - Make loan payment\n" +
            "STMT <days> <pin> - Mini statement\n" +
            "HELP - Show this help";
        
        return SmsResponse.builder()
            .success(true)
            .message(helpText)
            .build();
    }
    
    private SmsResponse createErrorResponse(String message) {
        return SmsResponse.builder()
            .success(false)
            .message(message)
            .build();
    }
    
    // Enhanced command processing methods with MFA
    
    private SmsResponse process2faSetupCommand(String phoneNumber) {
        try {
            // Validate user exists
            UUID userId = coreBankingService.getUserIdByPhoneNumber(phoneNumber);
            if (userId == null) {
                return createErrorResponse("Account not found. Please contact customer service.");
            }
            
            // Setup 2FA for SMS banking
            boolean setupResult = mfaService.setupSmsBasedMfa(phoneNumber, userId);
            
            if (setupResult) {
                securityService.logSmsTransaction(phoneNumber, "2FA_SETUP", "SUCCESS");
                return SmsResponse.builder()
                    .success(true)
                    .message("✅ 2FA Setup Complete\n" +
                           "SMS 2FA is now enabled for your account.\n" +
                           "All financial transactions require PIN + 2FA code.\n" +
                           "Test with: BAL <pin> to receive your first verification code.")
                    .build();
            } else {
                return createErrorResponse("2FA setup failed. Please try again later or contact support.");
            }
            
        } catch (Exception e) {
            log.error("Error setting up 2FA for {}", phoneNumber, e);
            return createErrorResponse("2FA setup temporarily unavailable. Please try again later.");
        }
    }
    
    private SmsResponse process2faStatusCommand(String phoneNumber) {
        try {
            // Validate user exists
            UUID userId = coreBankingService.getUserIdByPhoneNumber(phoneNumber);
            if (userId == null) {
                return createErrorResponse("Account not found. Please contact customer service.");
            }
            
            // Check 2FA status for the user
            boolean is2faEnabled = mfaService.is2faEnabledForUser(userId);
            String lastUsed = mfaService.getLastMfaUsageTime(userId);
            
            securityService.logSmsTransaction(phoneNumber, "2FA_STATUS_CHECK", "SUCCESS");
            
            if (is2faEnabled) {
                return SmsResponse.builder()
                    .success(true)
                    .message(String.format("✅ 2FA Status: ENABLED\n" +
                           "Method: SMS\n" +
                           "Phone: %s\n" +
                           "Last Used: %s\n" +
                           "All transactions require PIN + 2FA code",
                           maskPhoneNumber(phoneNumber), 
                           lastUsed != null ? lastUsed : "Never"))
                    .build();
            } else {
                return SmsResponse.builder()
                    .success(true)
                    .message("⚠️ 2FA Status: DISABLED\n" +
                           "Your account is not protected with 2FA.\n" +
                           "Text '2FA SETUP' to enable enhanced security.")
                    .build();
            }
            
        } catch (Exception e) {
            log.error("Error checking 2FA status for {}", phoneNumber, e);
            return createErrorResponse("Unable to check 2FA status. Please try again later.");
        }
    }
    
    private SmsResponse process2faCodeRequest(String phoneNumber) {
        try {
            // Validate user exists
            UUID userId = coreBankingService.getUserIdByPhoneNumber(phoneNumber);
            if (userId == null) {
                return createErrorResponse("Account not found. Please contact customer service.");
            }
            
            // Check if 2FA is enabled
            if (!mfaService.is2faEnabledForUser(userId)) {
                return createErrorResponse("2FA is not enabled for your account. Text '2FA SETUP' to enable it.");
            }
            
            // Generate a test 2FA code
            String testCodeId = mfaService.generateTestVerificationCode(phoneNumber, userId);
            
            securityService.logSmsTransaction(phoneNumber, "2FA_TEST_CODE", "SUCCESS");
            
            return SmsResponse.builder()
                .success(true)
                .message("✅ Test 2FA Code Sent\n" +
                       "A verification code has been sent to your phone.\n" +
                       "Use this format for transactions:\n" +
                       "BAL <pin> <code> - Check balance\n" +
                       "SEND <phone> <amount> <pin> <code> - Transfer\n" +
                       "Code ID: " + testCodeId.substring(0, 8) + "***")
                .mfaCodeId(testCodeId)
                .build();
                
        } catch (Exception e) {
            log.error("Error generating test 2FA code for {}", phoneNumber, e);
            return createErrorResponse("Unable to generate test code. Please try again later.");
        }
    }
    
    private SmsResponse processBalanceCommandWithMfa(String phoneNumber, String message, String sessionToken) {
        try {
            String[] parts = message.split("\\s+");
            String pin = parts[1];
            String mfaCode = parts[2];
            
            if (!securityService.verifyPin(phoneNumber, pin)) {
                securityService.recordFailedPinAttempt(phoneNumber);
                return createErrorResponse("Incorrect PIN. Please try again with format: BAL <pin> <2fa>");
            }
            
            UUID userId = coreBankingService.getUserIdByPhoneNumber(phoneNumber);
            if (userId == null) {
                return createErrorResponse("Account not found. Please contact customer service.");
            }
            
            String balance = coreBankingService.getAccountBalance(userId);
            securityService.logSmsTransaction(phoneNumber, "BALANCE_INQUIRY", "SUCCESS");
            
            return SmsResponse.builder()
                .success(true)
                .message(String.format("✅ Balance Inquiry\nAccount Balance: %s\nSecure Session: %s", 
                    balance, sessionToken.substring(0, 8) + "***"))
                .transactionRef(generateTransactionRef("BAL"))
                .build();
                
        } catch (Exception e) {
            log.error("Error processing MFA balance command for {}", phoneNumber, e);
            return createErrorResponse("Unable to retrieve balance. Please try again.");
        }
    }
    
    private SmsResponse processTransferCommandWithMfa(String phoneNumber, String message, String sessionToken) {
        try {
            String[] parts = message.split("\\s+");
            String recipient = parts[1];
            double amount = Double.parseDouble(parts[2]);
            String pin = parts[3];
            String mfaCode = parts[4];
            
            if (amount <= 0 || amount > 10000) {
                return createErrorResponse("Invalid amount. Must be between $0.01 and $10,000.");
            }
            
            if (!securityService.verifyPin(phoneNumber, pin)) {
                securityService.recordFailedPinAttempt(phoneNumber);
                return createErrorResponse("Incorrect PIN. Transfer cancelled.");
            }
            
            UUID userId = coreBankingService.getUserIdByPhoneNumber(phoneNumber);
            String transferRef = coreBankingService.processTransfer(userId, recipient, amount);
            
            securityService.logSmsTransaction(phoneNumber, "TRANSFER", "SUCCESS");
            
            return SmsResponse.builder()
                .success(true)
                .message(String.format("✅ Transfer Successful\nTo: %s\nAmount: $%.2f\nRef: %s\nSecure Session: %s", 
                    maskPhoneNumber(recipient), amount, transferRef, sessionToken.substring(0, 8) + "***"))
                .transactionRef(transferRef)
                .build();
                
        } catch (Exception e) {
            log.error("Error processing MFA transfer command for {}", phoneNumber, e);
            return createErrorResponse("Transfer failed. Please try again later.");
        }
    }
    
    private SmsResponse processAirtimeCommandWithMfa(String phoneNumber, String message, String sessionToken) {
        try {
            String[] parts = message.split("\\s+");
            String recipientPhone = parts[1];
            int amount = Integer.parseInt(parts[2]);
            String pin = parts[3];
            String mfaCode = parts[4];
            
            if (amount < 1 || amount > 100) {
                return createErrorResponse("Invalid amount. Airtime must be between $1 and $100.");
            }
            
            if (!securityService.verifyPin(phoneNumber, pin)) {
                securityService.recordFailedPinAttempt(phoneNumber);
                return createErrorResponse("Incorrect PIN. Airtime purchase cancelled.");
            }
            
            UUID userId = coreBankingService.getUserIdByPhoneNumber(phoneNumber);
            String airtimeRef = coreBankingService.purchaseAirtime(userId, recipientPhone, amount);
            
            securityService.logSmsTransaction(phoneNumber, "AIRTIME_PURCHASE", "SUCCESS");
            
            return SmsResponse.builder()
                .success(true)
                .message(String.format("✅ Airtime Purchase\nFor: %s\nAmount: $%d\nRef: %s\nSecure Session: %s", 
                    maskPhoneNumber(recipientPhone), amount, airtimeRef, sessionToken.substring(0, 8) + "***"))
                .transactionRef(airtimeRef)
                .build();
                
        } catch (Exception e) {
            log.error("Error processing MFA airtime command for {}", phoneNumber, e);
            return createErrorResponse("Airtime purchase failed. Please try again later.");
        }
    }
    
    private SmsResponse processLoanStatusCommandWithMfa(String phoneNumber, String message, String sessionToken) {
        try {
            String[] parts = message.split("\\s+");
            String pin = parts[2]; // LOAN STATUS <pin> <2fa>
            String mfaCode = parts[3];
            
            if (!securityService.verifyPin(phoneNumber, pin)) {
                return createErrorResponse("Incorrect PIN. Please try again with format: LOAN STATUS <pin> <2fa>");
            }
            
            UUID userId = coreBankingService.getUserIdByPhoneNumber(phoneNumber);
            String loanStatus = coreBankingService.getLoanStatus(userId);
            
            securityService.logSmsTransaction(phoneNumber, "LOAN_STATUS", "SUCCESS");
            
            return SmsResponse.builder()
                .success(true)
                .message(String.format("✅ Loan Status\n%s\nSecure Session: %s", 
                    loanStatus, sessionToken.substring(0, 8) + "***"))
                .build();
                
        } catch (Exception e) {
            log.error("Error processing MFA loan status command for {}", phoneNumber, e);
            return createErrorResponse("Unable to retrieve loan status. Please try again.");
        }
    }
    
    private SmsResponse processLoanPaymentCommandWithMfa(String phoneNumber, String message, String sessionToken) {
        try {
            String[] parts = message.split("\\s+");
            double amount = Double.parseDouble(parts[2]); // LOAN PAY <amount> <pin> <2fa>
            String pin = parts[3];
            String mfaCode = parts[4];
            
            if (amount <= 0) {
                return createErrorResponse("Invalid payment amount. Must be greater than $0.");
            }
            
            if (!securityService.verifyPin(phoneNumber, pin)) {
                return createErrorResponse("Incorrect PIN. Loan payment cancelled.");
            }
            
            UUID userId = coreBankingService.getUserIdByPhoneNumber(phoneNumber);
            String paymentRef = coreBankingService.makeLoanPayment(userId, amount);
            
            securityService.logSmsTransaction(phoneNumber, "LOAN_PAYMENT", "SUCCESS");
            
            return SmsResponse.builder()
                .success(true)
                .message(String.format("✅ Loan Payment\nAmount: $%.2f\nRef: %s\nSecure Session: %s", 
                    amount, paymentRef, sessionToken.substring(0, 8) + "***"))
                .transactionRef(paymentRef)
                .build();
                
        } catch (Exception e) {
            log.error("Error processing MFA loan payment command for {}", phoneNumber, e);
            return createErrorResponse("Loan payment failed. Please try again later.");
        }
    }
    
    private SmsResponse processStatementCommandWithMfa(String phoneNumber, String message, String sessionToken) {
        try {
            String[] parts = message.split("\\s+");
            int days = Integer.parseInt(parts[1]);
            String pin = parts[2];
            String mfaCode = parts[3];
            
            if (days < 1 || days > 30) {
                return createErrorResponse("Invalid period. Statement can cover 1-30 days only.");
            }
            
            if (!securityService.verifyPin(phoneNumber, pin)) {
                return createErrorResponse("Incorrect PIN. Statement request cancelled.");
            }
            
            UUID userId = coreBankingService.getUserIdByPhoneNumber(phoneNumber);
            String statement = coreBankingService.getMiniStatement(userId, days);
            
            securityService.logSmsTransaction(phoneNumber, "MINI_STATEMENT", "SUCCESS");
            
            return SmsResponse.builder()
                .success(true)
                .message(String.format("✅ Mini Statement (%d days)\n%s\nSecure Session: %s", 
                    days, statement, sessionToken.substring(0, 8) + "***"))
                .build();
                
        } catch (Exception e) {
            log.error("Error processing MFA statement command for {}", phoneNumber, e);
            return createErrorResponse("Unable to generate statement. Please try again.");
        }
    }
    
    private String generateTransactionRef(String type) {
        return type + System.currentTimeMillis();
    }
    
    private String maskPhoneNumber(String phoneNumber) {
        if (phoneNumber.length() > 4) {
            return phoneNumber.substring(0, 3) + "***" + phoneNumber.substring(phoneNumber.length() - 2);
        }
        return "***";
    }

    // Enhanced SMS Response DTO with MFA support
    public static class SmsResponse {
        private boolean success;
        private String message;
        private String transactionRef;
        private boolean requiresMfa;
        private String mfaCodeId;
        private String errorCode;
        
        public static SmsResponseBuilder builder() {
            return new SmsResponseBuilder();
        }
        
        public static class SmsResponseBuilder {
            private boolean success;
            private String message;
            private String transactionRef;
            private boolean requiresMfa;
            private String mfaCodeId;
            private String errorCode;
            
            public SmsResponseBuilder success(boolean success) {
                this.success = success;
                return this;
            }
            
            public SmsResponseBuilder message(String message) {
                this.message = message;
                return this;
            }
            
            public SmsResponseBuilder transactionRef(String transactionRef) {
                this.transactionRef = transactionRef;
                return this;
            }
            
            public SmsResponseBuilder requiresMfa(boolean requiresMfa) {
                this.requiresMfa = requiresMfa;
                return this;
            }
            
            public SmsResponseBuilder mfaCodeId(String mfaCodeId) {
                this.mfaCodeId = mfaCodeId;
                return this;
            }
            
            public SmsResponseBuilder errorCode(String errorCode) {
                this.errorCode = errorCode;
                return this;
            }
            
            public SmsResponse build() {
                SmsResponse response = new SmsResponse();
                response.success = this.success;
                response.message = this.message;
                response.transactionRef = this.transactionRef;
                response.requiresMfa = this.requiresMfa;
                response.mfaCodeId = this.mfaCodeId;
                response.errorCode = this.errorCode;
                return response;
            }
        }
        
        // Getters
        public boolean isSuccess() { return success; }
        public String getMessage() { return message; }
        public String getTransactionRef() { return transactionRef; }
        public boolean isRequiresMfa() { return requiresMfa; }
        public String getMfaCodeId() { return mfaCodeId; }
        public String getErrorCode() { return errorCode; }
    }
}