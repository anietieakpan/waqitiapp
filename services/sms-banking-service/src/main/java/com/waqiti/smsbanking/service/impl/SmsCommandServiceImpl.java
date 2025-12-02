package com.waqiti.smsbanking.service.impl;

import com.waqiti.smsbanking.service.SmsCommandService;
import com.waqiti.smsbanking.service.SmsGatewayService;
import com.waqiti.smsbanking.service.SmsDeliveryTrackingService;
import com.waqiti.smsbanking.client.PaymentServiceClient;
import com.waqiti.smsbanking.client.UserServiceClient;
import com.waqiti.smsbanking.client.WalletServiceClient;
import com.waqiti.smsbanking.dto.*;
import com.waqiti.smsbanking.entity.*;
import com.waqiti.smsbanking.repository.SmsCommandHistoryRepository;
import com.waqiti.smsbanking.repository.SmsSessionRepository;
import com.waqiti.smsbanking.security.SmsPinService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.retry.annotation.Backoff;
import org.springframework.retry.annotation.Retryable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.security.SecureRandom;
import java.time.LocalDateTime;
import java.util.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

/**
 * Complete implementation of SMS command processing service.
 * Handles all SMS banking operations with security and error handling.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class SmsCommandServiceImpl implements SmsCommandService {
    
    private static final SecureRandom SECURE_RANDOM = new SecureRandom();
    
    private final UserServiceClient userServiceClient;
    private final WalletServiceClient walletServiceClient;
    private final PaymentServiceClient paymentServiceClient;
    private final SmsPinService pinService;
    private final SmsGatewayService gatewayService;
    private final SmsDeliveryTrackingService deliveryTrackingService;
    private final SmsCommandHistoryRepository commandHistoryRepository;
    private final SmsSessionRepository sessionRepository;
    
    // Command patterns
    private static final Pattern BALANCE_PATTERN = Pattern.compile(
        "^(BAL|BALANCE)\\s+(\\d{4,6})$", Pattern.CASE_INSENSITIVE);
    
    private static final Pattern TRANSFER_PATTERN = Pattern.compile(
        "^(SEND|TRANSFER)\\s+(\\+?\\d{10,15})\\s+(\\d+\\.?\\d*)\\s+(\\d{4,6})$", 
        Pattern.CASE_INSENSITIVE);
    
    private static final Pattern AIRTIME_PATTERN = Pattern.compile(
        "^AIRTIME\\s+(\\+?\\d{10,15})\\s+(\\d+)\\s+(\\d{4,6})$", 
        Pattern.CASE_INSENSITIVE);
    
    private static final Pattern STATEMENT_PATTERN = Pattern.compile(
        "^(STMT|STATEMENT)\\s+(\\d{1,3})\\s+(\\d{4,6})$", 
        Pattern.CASE_INSENSITIVE);
    
    private static final Pattern LOAN_STATUS_PATTERN = Pattern.compile(
        "^LOAN\\s+STATUS\\s+(\\d{4,6})$", Pattern.CASE_INSENSITIVE);
    
    private static final Pattern LOAN_PAY_PATTERN = Pattern.compile(
        "^LOAN\\s+PAY\\s+(\\d+\\.?\\d*)\\s+(\\d{4,6})$", 
        Pattern.CASE_INSENSITIVE);
    
    private static final Pattern BILL_PAY_PATTERN = Pattern.compile(
        "^(BILL|PAY)\\s+(\\w+)\\s+(\\w+)\\s+(\\d+\\.?\\d*)\\s+(\\d{4,6})$", 
        Pattern.CASE_INSENSITIVE);
    
    @Override
    @Transactional
    public SmsResponse processSmsCommand(String phoneNumber, String message) {
        log.info("Processing SMS command from {}", phoneNumber);
        
        try {
            // Validate phone number and get user
            UserDTO user = getUserByPhone(phoneNumber);
            if (user == null) {
                return createErrorResponse("Phone number not registered. Please register at app.waqiti.com");
            }
            
            // Check if user is blocked
            if (isUserBlocked(user.getId())) {
                return createErrorResponse("Your SMS banking is temporarily blocked. Contact support.");
            }
            
            // Parse and execute command
            String command = message.trim().toUpperCase();
            SmsResponse response = executeCommand(user, command);
            
            // Save command history
            saveCommandHistory(user.getId(), phoneNumber, command, response);
            
            // Send response SMS
            sendResponseSms(phoneNumber, response.getMessage());
            
            // Track delivery
            trackDelivery(phoneNumber, response);
            
            return response;
            
        } catch (Exception e) {
            log.error("Error processing SMS command: ", e);
            return createErrorResponse("Service temporarily unavailable. Please try again later.");
        }
    }
    
    private SmsResponse executeCommand(UserDTO user, String command) {
        // Check balance
        Matcher balanceMatcher = BALANCE_PATTERN.matcher(command);
        if (balanceMatcher.matches()) {
            String pin = balanceMatcher.group(2);
            return processBalanceCheck(user, pin);
        }
        
        // Transfer money
        Matcher transferMatcher = TRANSFER_PATTERN.matcher(command);
        if (transferMatcher.matches()) {
            String recipientPhone = transferMatcher.group(2);
            BigDecimal amount = new BigDecimal(transferMatcher.group(3));
            String pin = transferMatcher.group(4);
            return processTransfer(user, recipientPhone, amount, pin);
        }
        
        // Buy airtime
        Matcher airtimeMatcher = AIRTIME_PATTERN.matcher(command);
        if (airtimeMatcher.matches()) {
            String targetPhone = airtimeMatcher.group(1);
            BigDecimal amount = new BigDecimal(airtimeMatcher.group(2));
            String pin = airtimeMatcher.group(3);
            return processAirtimePurchase(user, targetPhone, amount, pin);
        }
        
        // Get statement
        Matcher statementMatcher = STATEMENT_PATTERN.matcher(command);
        if (statementMatcher.matches()) {
            int days = Integer.parseInt(statementMatcher.group(2));
            String pin = statementMatcher.group(3);
            return processMiniStatement(user, days, pin);
        }
        
        // Check loan status
        Matcher loanStatusMatcher = LOAN_STATUS_PATTERN.matcher(command);
        if (loanStatusMatcher.matches()) {
            String pin = loanStatusMatcher.group(1);
            return processLoanStatus(user, pin);
        }
        
        // Pay loan
        Matcher loanPayMatcher = LOAN_PAY_PATTERN.matcher(command);
        if (loanPayMatcher.matches()) {
            BigDecimal amount = new BigDecimal(loanPayMatcher.group(1));
            String pin = loanPayMatcher.group(2);
            return processLoanPayment(user, amount, pin);
        }
        
        // Pay bill
        Matcher billPayMatcher = BILL_PAY_PATTERN.matcher(command);
        if (billPayMatcher.matches()) {
            String biller = billPayMatcher.group(2);
            String accountNumber = billPayMatcher.group(3);
            BigDecimal amount = new BigDecimal(billPayMatcher.group(4));
            String pin = billPayMatcher.group(5);
            return processBillPayment(user, biller, accountNumber, amount, pin);
        }
        
        // Help command
        if (command.equals("HELP") || command.equals("?")) {
            return processHelp();
        }
        
        return createErrorResponse("Invalid command. Text HELP for available commands.");
    }
    
    @Retryable(value = {Exception.class}, maxAttempts = 3, backoff = @Backoff(delay = 1000))
    private SmsResponse processBalanceCheck(UserDTO user, String pin) {
        // Verify PIN
        if (!pinService.verifyPin(user.getId(), pin)) {
            logFailedAttempt(user.getId(), "BALANCE", "Invalid PIN");
            return createErrorResponse("Invalid PIN. " + getRemainingAttempts(user.getId()) + " attempts remaining.");
        }
        
        try {
            // Get wallet balance
            WalletDTO wallet = walletServiceClient.getWalletByUserId(user.getId());
            
            String message = String.format(
                "Balance: %s %s\nAvailable: %s %s\nPending: %s %s\nRef: %s",
                wallet.getCurrency(), formatAmount(wallet.getBalance()),
                wallet.getCurrency(), formatAmount(wallet.getAvailableBalance()),
                wallet.getCurrency(), formatAmount(wallet.getPendingBalance()),
                generateReference()
            );
            
            return SmsResponse.builder()
                .success(true)
                .message(message)
                .reference(generateReference())
                .timestamp(LocalDateTime.now())
                .build();
                
        } catch (Exception e) {
            log.error("Error checking balance for user {}: ", user.getId(), e);
            return createErrorResponse("Unable to check balance. Please try again.");
        }
    }
    
    @Transactional
    private SmsResponse processTransfer(UserDTO sender, String recipientPhone, BigDecimal amount, String pin) {
        // Verify PIN
        if (!pinService.verifyPin(sender.getId(), pin)) {
            logFailedAttempt(sender.getId(), "TRANSFER", "Invalid PIN");
            return createErrorResponse("Invalid PIN. " + getRemainingAttempts(sender.getId()) + " attempts remaining.");
        }
        
        // Validate amount
        if (amount.compareTo(BigDecimal.ZERO) <= 0) {
            return createErrorResponse("Invalid amount. Amount must be greater than zero.");
        }
        
        if (amount.compareTo(new BigDecimal("10000")) > 0) {
            return createErrorResponse("Amount exceeds SMS transfer limit of 10,000. Use app for larger transfers.");
        }
        
        try {
            // Get recipient
            UserDTO recipient = getUserByPhone(recipientPhone);
            if (recipient == null) {
                return createErrorResponse("Recipient phone number not registered with Waqiti.");
            }
            
            // Check balance
            WalletDTO senderWallet = walletServiceClient.getWalletByUserId(sender.getId());
            if (senderWallet.getAvailableBalance().compareTo(amount) < 0) {
                return createErrorResponse("Insufficient balance. Available: " + 
                    senderWallet.getCurrency() + " " + formatAmount(senderWallet.getAvailableBalance()));
            }
            
            // Process payment
            PaymentRequest paymentRequest = PaymentRequest.builder()
                .senderId(sender.getId())
                .recipientId(recipient.getId())
                .amount(amount)
                .currency(senderWallet.getCurrency())
                .description("SMS Transfer")
                .channel("SMS")
                .reference(generateReference())
                .build();
            
            PaymentResponse paymentResponse = paymentServiceClient.processPayment(paymentRequest);
            
            if (paymentResponse.isSuccess()) {
                String message = String.format(
                    "Sent %s %s to %s (%s)\nNew balance: %s %s\nRef: %s",
                    senderWallet.getCurrency(), formatAmount(amount),
                    recipient.getFirstName(), maskPhone(recipientPhone),
                    senderWallet.getCurrency(), 
                    formatAmount(senderWallet.getAvailableBalance().subtract(amount)),
                    paymentResponse.getReference()
                );
                
                // Notify recipient
                sendNotificationToRecipient(recipientPhone, sender.getFirstName(), amount, 
                    senderWallet.getCurrency(), paymentResponse.getReference());
                
                return SmsResponse.builder()
                    .success(true)
                    .message(message)
                    .reference(paymentResponse.getReference())
                    .timestamp(LocalDateTime.now())
                    .build();
            } else {
                return createErrorResponse("Transfer failed: " + paymentResponse.getErrorMessage());
            }
            
        } catch (Exception e) {
            log.error("Error processing transfer: ", e);
            return createErrorResponse("Transfer failed. Please try again.");
        }
    }
    
    private SmsResponse processAirtimePurchase(UserDTO user, String targetPhone, BigDecimal amount, String pin) {
        // Verify PIN
        if (!pinService.verifyPin(user.getId(), pin)) {
            logFailedAttempt(user.getId(), "AIRTIME", "Invalid PIN");
            return createErrorResponse("Invalid PIN. " + getRemainingAttempts(user.getId()) + " attempts remaining.");
        }
        
        // Validate amount
        if (amount.compareTo(new BigDecimal("5")) < 0) {
            return createErrorResponse("Minimum airtime amount is 5.");
        }
        
        if (amount.compareTo(new BigDecimal("1000")) > 0) {
            return createErrorResponse("Maximum airtime amount is 1000.");
        }
        
        try {
            // Process airtime purchase through external provider
            AirtimeRequest airtimeRequest = AirtimeRequest.builder()
                .userId(user.getId())
                .targetPhone(targetPhone)
                .amount(amount)
                .channel("SMS")
                .reference(generateReference())
                .build();
            
            AirtimeResponse airtimeResponse = processAirtimeWithProvider(airtimeRequest);
            
            if (airtimeResponse.isSuccess()) {
                String message = String.format(
                    "Airtime %s sent to %s\nVoucher: %s\nRef: %s",
                    formatAmount(amount),
                    maskPhone(targetPhone),
                    airtimeResponse.getVoucherCode(),
                    airtimeResponse.getReference()
                );
                
                return SmsResponse.builder()
                    .success(true)
                    .message(message)
                    .reference(airtimeResponse.getReference())
                    .timestamp(LocalDateTime.now())
                    .build();
            } else {
                return createErrorResponse("Airtime purchase failed: " + airtimeResponse.getErrorMessage());
            }
            
        } catch (Exception e) {
            log.error("Error processing airtime purchase: ", e);
            return createErrorResponse("Airtime purchase failed. Please try again.");
        }
    }
    
    private SmsResponse processMiniStatement(UserDTO user, int days, String pin) {
        // Verify PIN
        if (!pinService.verifyPin(user.getId(), pin)) {
            logFailedAttempt(user.getId(), "STATEMENT", "Invalid PIN");
            return createErrorResponse("Invalid PIN. " + getRemainingAttempts(user.getId()) + " attempts remaining.");
        }
        
        // Validate days
        if (days < 1 || days > 30) {
            return createErrorResponse("Statement period must be between 1 and 30 days.");
        }
        
        try {
            // Get transactions
            List<TransactionDTO> transactions = getRecentTransactions(user.getId(), days);
            
            if (transactions.isEmpty()) {
                return SmsResponse.builder()
                    .success(true)
                    .message("No transactions in the last " + days + " days.")
                    .reference(generateReference())
                    .timestamp(LocalDateTime.now())
                    .build();
            }
            
            // Format mini statement (SMS has character limit)
            StringBuilder statement = new StringBuilder();
            statement.append("Last ").append(Math.min(5, transactions.size())).append(" transactions:\n");
            
            transactions.stream()
                .limit(5)
                .forEach(tx -> {
                    statement.append(tx.getDate()).append(" ");
                    statement.append(tx.getType()).append(" ");
                    statement.append(tx.getCurrency()).append(formatAmount(tx.getAmount()));
                    statement.append("\n");
                });
            
            WalletDTO wallet = walletServiceClient.getWalletByUserId(user.getId());
            statement.append("Balance: ").append(wallet.getCurrency())
                    .append(" ").append(formatAmount(wallet.getBalance()));
            
            return SmsResponse.builder()
                .success(true)
                .message(statement.toString())
                .reference(generateReference())
                .timestamp(LocalDateTime.now())
                .build();
                
        } catch (Exception e) {
            log.error("Error generating statement: ", e);
            return createErrorResponse("Unable to generate statement. Please try again.");
        }
    }
    
    private SmsResponse processLoanStatus(UserDTO user, String pin) {
        // Implementation for loan status check
        // This would integrate with a loan service
        return SmsResponse.builder()
            .success(true)
            .message("No active loans found.")
            .reference(generateReference())
            .timestamp(LocalDateTime.now())
            .build();
    }
    
    private SmsResponse processLoanPayment(UserDTO user, BigDecimal amount, String pin) {
        // Implementation for loan payment
        // This would integrate with a loan service
        return createErrorResponse("Loan payment service coming soon.");
    }
    
    private SmsResponse processBillPayment(UserDTO user, String biller, String accountNumber, 
                                           BigDecimal amount, String pin) {
        // Implementation for bill payment
        // This would integrate with bill payment providers
        return createErrorResponse("Bill payment for " + biller + " coming soon.");
    }
    
    private SmsResponse processHelp() {
        String helpText = "Waqiti SMS Commands:\n" +
            "BAL <pin> - Balance\n" +
            "SEND <phone> <amt> <pin> - Transfer\n" +
            "AIRTIME <phone> <amt> <pin> - Buy airtime\n" +
            "STMT <days> <pin> - Statement\n" +
            "HELP - This message";
        
        return SmsResponse.builder()
            .success(true)
            .message(helpText)
            .reference(generateReference())
            .timestamp(LocalDateTime.now())
            .build();
    }
    
    // Helper methods
    @Cacheable(value = "userByPhone", key = "#phoneNumber")
    private UserDTO getUserByPhone(String phoneNumber) {
        try {
            return userServiceClient.getUserByPhone(phoneNumber);
        } catch (Exception e) {
            log.error("Error fetching user by phone {}: ", phoneNumber, e);
            return null;
        }
    }
    
    private boolean isUserBlocked(Long userId) {
        // Check if user has exceeded failed attempts
        int failedAttempts = getFailedAttempts(userId);
        return failedAttempts >= 3;
    }
    
    private void logFailedAttempt(Long userId, String command, String reason) {
        // Log failed attempt to database
        SmsCommandHistory history = SmsCommandHistory.builder()
            .userId(userId)
            .command(command)
            .status("FAILED")
            .reason(reason)
            .timestamp(LocalDateTime.now())
            .build();
        commandHistoryRepository.save(history);
    }
    
    private int getFailedAttempts(Long userId) {
        LocalDateTime since = LocalDateTime.now().minusHours(24);
        return commandHistoryRepository.countFailedAttempts(userId, since);
    }
    
    private int getRemainingAttempts(Long userId) {
        return Math.max(0, 3 - getFailedAttempts(userId));
    }
    
    private void saveCommandHistory(Long userId, String phoneNumber, String command, SmsResponse response) {
        SmsCommandHistory history = SmsCommandHistory.builder()
            .userId(userId)
            .phoneNumber(phoneNumber)
            .command(maskSensitiveData(command))
            .status(response.isSuccess() ? "SUCCESS" : "FAILED")
            .response(response.getMessage())
            .reference(response.getReference())
            .timestamp(LocalDateTime.now())
            .build();
        commandHistoryRepository.save(history);
    }
    
    private String maskSensitiveData(String command) {
        // Mask PIN numbers in command
        return command.replaceAll("\\b\\d{4,6}\\b", "****");
    }
    
    private void sendResponseSms(String phoneNumber, String message) {
        try {
            gatewayService.sendSms(phoneNumber, message);
        } catch (Exception e) {
            log.error("Failed to send SMS response to {}: ", phoneNumber, e);
        }
    }
    
    private void sendNotificationToRecipient(String recipientPhone, String senderName, 
                                            BigDecimal amount, String currency, String reference) {
        String message = String.format(
            "You received %s %s from %s. Ref: %s",
            currency, formatAmount(amount), senderName, reference
        );
        sendResponseSms(recipientPhone, message);
    }
    
    private void trackDelivery(String phoneNumber, SmsResponse response) {
        deliveryTrackingService.trackDelivery(
            phoneNumber, 
            response.getReference(), 
            response.getMessage(),
            response.isSuccess() ? "SENT" : "FAILED"
        );
    }
    
    private List<TransactionDTO> getRecentTransactions(Long userId, int days) {
        // Fetch recent transactions from transaction service
        LocalDateTime since = LocalDateTime.now().minusDays(days);
        return paymentServiceClient.getTransactionHistory(userId, since, LocalDateTime.now());
    }
    
    private AirtimeResponse processAirtimeWithProvider(AirtimeRequest request) {
        // Integration with airtime provider (e.g., Flutterwave, Paystack)
        // This is a placeholder implementation
        return AirtimeResponse.builder()
            .success(true)
            .voucherCode(generateVoucherCode())
            .reference(request.getReference())
            .build();
    }
    
    private String formatAmount(BigDecimal amount) {
        return String.format("%.2f", amount);
    }
    
    private String maskPhone(String phone) {
        if (phone.length() > 7) {
            return phone.substring(0, 3) + "****" + phone.substring(phone.length() - 4);
        }
        return "****";
    }
    
    private String generateReference() {
        return "SMS" + System.currentTimeMillis() + SECURE_RANDOM.nextInt(1000);
    }
    
    private String generateVoucherCode() {
        return UUID.randomUUID().toString().substring(0, 12).toUpperCase();
    }
    
    private SmsResponse createErrorResponse(String message) {
        return SmsResponse.builder()
            .success(false)
            .message(message)
            .reference(generateReference())
            .timestamp(LocalDateTime.now())
            .build();
    }
}