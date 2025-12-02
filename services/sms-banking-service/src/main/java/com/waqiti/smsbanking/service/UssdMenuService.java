/**
 * USSD Menu Service
 * Handles USSD menu navigation and interactive sessions
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
import java.util.UUID;

@Service
@RequiredArgsConstructor
@Slf4j
@Transactional
public class UssdMenuService {
    
    private final SmsSessionRepository sessionRepository;
    private final CoreBankingService coreBankingService;
    private final SecurityService securityService;
    private final SmsBankingMfaService mfaService;
    private final ObjectMapper objectMapper;
    
    public UssdResponse processUssdRequest(String sessionId, String phoneNumber, String input) {
        try {
            SmsSession session = getOrCreateSession(sessionId, phoneNumber);
            
            if (session.isExpired()) {
                sessionRepository.delete(session);
                return createEndResponse("Session expired. Please try again.", sessionId);
            }
            
            // Handle input based on current menu state
            return handleMenuNavigation(session, input);
            
        } catch (Exception e) {
            log.error("Error processing USSD request for session: {}", sessionId, e);
            return createEndResponse("Service temporarily unavailable. Please try again later.", sessionId);
        }
    }
    
    /**
     * Enhanced USSD processing with step-up MFA authentication for high-value operations
     */
    public UssdResponse processUssdRequestWithMfa(String sessionId, String phoneNumber, 
                                                String input, String gatewayRef, String mfaCode) {
        try {
            log.info("Processing USSD request with MFA for session: {}", sessionId);
            
            SmsSession session = getOrCreateSession(sessionId, phoneNumber);
            
            if (session.isExpired()) {
                sessionRepository.delete(session);
                return createEndResponse("🔒 Session expired. Please dial *123# again.", sessionId);
            }
            
            // Enhanced MFA validation for USSD
            SmsBankingMfaService.UssdMfaResult mfaResult = mfaService.validateUssdSession(
                sessionId, phoneNumber, input, mfaCode);
            
            if (mfaResult.isRequiresMfa()) {
                // Step-up authentication required
                return UssdResponse.builder()
                    .continueSession(true)
                    .message("🛡️ SECURITY VERIFICATION\n" + mfaResult.getMessage())
                    .menuOptions(mfaResult.getMenuOptions())
                    .sessionId(sessionId)
                    .requiresMfa(true)
                    .mfaCodeId(mfaResult.getCodeId())
                    .build();
            }
            
            if (!mfaResult.isContinueSession()) {
                return createEndResponse("🔐 Authentication failed. Session terminated.", sessionId);
            }
            
            // Process authenticated USSD request
            return handleEnhancedMenuNavigation(session, input, mfaResult.getSessionState());
            
        } catch (Exception e) {
            log.error("Error processing USSD request with MFA for session: {}", sessionId, e);
            return createEndResponse("🚫 Service temporarily unavailable. Please try again later.", sessionId);
        }
    }
    
    private SmsSession getOrCreateSession(String sessionId, String phoneNumber) {
        return sessionRepository.findBySessionId(sessionId)
            .orElseGet(() -> createNewSession(sessionId, phoneNumber));
    }
    
    private SmsSession createNewSession(String sessionId, String phoneNumber) {
        SmsSession session = SmsSession.builder()
            .sessionId(sessionId)
            .phoneNumber(phoneNumber)
            .channel(SmsSession.Channel.USSD)
            .status(SmsSession.SessionStatus.ACTIVE)
            .currentMenu("MAIN_MENU")
            .currentStep("START")
            .sessionData(objectMapper.createObjectNode())
            .transactionContext(objectMapper.createObjectNode())
            .languagePreference("en")
            .pinAttempts(0)
            .isAuthenticated(false)
            .lastActivity(LocalDateTime.now())
            .timeoutMinutes(5)
            .build();
        
        return sessionRepository.save(session);
    }
    
    private UssdResponse handleMenuNavigation(SmsSession session, String input) {
        String currentMenu = session.getCurrentMenu();
        
        switch (currentMenu) {
            case "MAIN_MENU":
                return handleMainMenu(session, input);
            case "BALANCE_MENU":
                return handleBalanceMenu(session, input);
            case "TRANSFER_MENU":
                return handleTransferMenu(session, input);
            case "PAYMENT_MENU":
                return handlePaymentMenu(session, input);
            case "LOAN_MENU":
                return handleLoanMenu(session, input);
            case "PIN_VERIFICATION":
                return handlePinVerification(session, input);
            case "AMOUNT_INPUT":
                return handleAmountInput(session, input);
            case "RECIPIENT_INPUT":
                return handleRecipientInput(session, input);
            case "CONFIRMATION":
                return handleConfirmation(session, input);
            default:
                return handleMainMenu(session, "");
        }
    }
    
    private UssdResponse handleMainMenu(SmsSession session, String input) {
        if (input.isEmpty()) {
            session.setCurrentMenu("MAIN_MENU");
            session.setCurrentStep("MENU_DISPLAY");
            sessionRepository.save(session);
            
            String menu = buildMainMenu(session.getLanguagePreference());
            return createContinueResponse(menu, session.getSessionId());
        }
        
        switch (input.trim()) {
            case "1":
                return initiateBalanceInquiry(session);
            case "2":
                return initiateTransfer(session);
            case "3":
                return initiatePayment(session);
            case "4":
                return initiateLoanOperations(session);
            case "5":
                return initiateSettings(session);
            case "0":
                return createEndResponse("Thank you for using Waqiti Banking. Goodbye!", session.getSessionId());
            default:
                return createContinueResponse("Invalid option. " + buildMainMenu(session.getLanguagePreference()), 
                    session.getSessionId());
        }
    }
    
    private UssdResponse handleBalanceInquiry(SmsSession session, String input) {
        if (!session.getIsAuthenticated()) {
            return requestPinAuthentication(session);
        }
        
        try {
            // Get user ID from phone number
            UUID userId = coreBankingService.getUserIdByPhoneNumber(session.getPhoneNumber());
            if (userId == null) {
                return createEndResponse("Account not found. Please contact customer service.", session.getSessionId());
            }
            
            // Get account balance
            String balance = coreBankingService.getAccountBalance(userId);
            String response = String.format("Your account balance is: %s\n\n0. Back to main menu\n00. Exit", balance);
            
            session.setCurrentMenu("BALANCE_MENU");
            session.setCurrentStep("DISPLAY_BALANCE");
            sessionRepository.save(session);
            
            return createContinueResponse(response, session.getSessionId());
            
        } catch (Exception e) {
            log.error("Error retrieving balance for session: {}", session.getSessionId(), e);
            return createEndResponse("Unable to retrieve balance. Please try again later.", session.getSessionId());
        }
    }
    
    private UssdResponse handleBalanceMenu(SmsSession session, String input) {
        switch (input.trim()) {
            case "0":
                return handleMainMenu(session, "");
            case "00":
                return createEndResponse("Thank you for using Waqiti Banking. Goodbye!", session.getSessionId());
            default:
                return createContinueResponse("Invalid option.\n0. Back to main menu\n00. Exit", session.getSessionId());
        }
    }
    
    private UssdResponse initiateTransfer(SmsSession session) {
        if (!session.getIsAuthenticated()) {
            return requestPinAuthentication(session);
        }
        
        session.setCurrentMenu("TRANSFER_MENU");
        session.setCurrentStep("RECIPIENT_INPUT");
        sessionRepository.save(session);
        
        return createContinueResponse("Enter recipient phone number:", session.getSessionId());
    }
    
    private UssdResponse handleTransferMenu(SmsSession session, String input) {
        String currentStep = session.getCurrentStep();
        
        switch (currentStep) {
            case "RECIPIENT_INPUT":
                return handleRecipientInput(session, input);
            case "AMOUNT_INPUT":
                return handleAmountInput(session, input);
            case "CONFIRMATION":
                return handleTransferConfirmation(session, input);
            default:
                return handleMainMenu(session, "");
        }
    }
    
    private UssdResponse handleRecipientInput(SmsSession session, String input) {
        if (!isValidPhoneNumber(input)) {
            return createContinueResponse("Invalid phone number. Please enter a valid phone number:", session.getSessionId());
        }
        
        // Store recipient in session data
        ObjectNode sessionData = (ObjectNode) session.getSessionData();
        sessionData.put("recipient_phone", input);
        session.setSessionData(sessionData);
        
        session.setCurrentStep("AMOUNT_INPUT");
        sessionRepository.save(session);
        
        return createContinueResponse("Enter amount to transfer:", session.getSessionId());
    }
    
    private UssdResponse handleAmountInput(SmsSession session, String input) {
        try {
            double amount = Double.parseDouble(input);
            if (amount <= 0) {
                return createContinueResponse("Invalid amount. Please enter a positive amount:", session.getSessionId());
            }
            
            // Store amount in session data
            ObjectNode sessionData = (ObjectNode) session.getSessionData();
            sessionData.put("transfer_amount", amount);
            session.setSessionData(sessionData);
            
            session.setCurrentStep("CONFIRMATION");
            sessionRepository.save(session);
            
            String recipient = sessionData.get("recipient_phone").asText();
            String confirmationMsg = String.format("Transfer $%.2f to %s?\n1. Confirm\n2. Cancel", amount, recipient);
            
            return createContinueResponse(confirmationMsg, session.getSessionId());
            
        } catch (NumberFormatException e) {
            return createContinueResponse("Invalid amount format. Please enter a valid amount:", session.getSessionId());
        }
    }
    
    private UssdResponse handleTransferConfirmation(SmsSession session, String input) {
        switch (input.trim()) {
            case "1":
                return processTransfer(session);
            case "2":
                return createEndResponse("Transfer cancelled.", session.getSessionId());
            default:
                return createContinueResponse("Invalid option.\n1. Confirm\n2. Cancel", session.getSessionId());
        }
    }
    
    private UssdResponse processTransfer(SmsSession session) {
        try {
            JsonNode sessionData = session.getSessionData();
            String recipientPhone = sessionData.get("recipient_phone").asText();
            double amount = sessionData.get("transfer_amount").asDouble();
            
            UUID userId = coreBankingService.getUserIdByPhoneNumber(session.getPhoneNumber());
            UUID recipientId = coreBankingService.getUserIdByPhoneNumber(recipientPhone);
            
            if (recipientId == null) {
                return createEndResponse("Recipient not found. Transfer cancelled.", session.getSessionId());
            }
            
            String transactionRef = coreBankingService.processTransfer(userId, recipientId, amount, "USSD Transfer");
            
            String successMsg = String.format("Transfer successful!\nReference: %s\nAmount: $%.2f\nTo: %s", 
                transactionRef, amount, recipientPhone);
            
            return createEndResponse(successMsg, session.getSessionId());
            
        } catch (Exception e) {
            log.error("Error processing transfer for session: {}", session.getSessionId(), e);
            return createEndResponse("Transfer failed. Please try again later.", session.getSessionId());
        }
    }
    
    private UssdResponse initiateBalanceInquiry(SmsSession session) {
        return handleBalanceInquiry(session, "");
    }
    
    private UssdResponse initiatePayment(SmsSession session) {
        session.setCurrentMenu("PAYMENT_MENU");
        session.setCurrentStep("START");
        sessionRepository.save(session);
        
        String menu = "Payment Services:\n1. Bill Payment\n2. Merchant Payment\n3. Airtime Purchase\n0. Back\n00. Exit";
        return createContinueResponse(menu, session.getSessionId());
    }
    
    private UssdResponse handlePaymentMenu(SmsSession session, String input) {
        switch (input.trim()) {
            case "1":
                return createContinueResponse("Bill payment feature coming soon.\n0. Back\n00. Exit", session.getSessionId());
            case "2":
                return createContinueResponse("Merchant payment feature coming soon.\n0. Back\n00. Exit", session.getSessionId());
            case "3":
                return createContinueResponse("Airtime purchase feature coming soon.\n0. Back\n00. Exit", session.getSessionId());
            case "0":
                return handleMainMenu(session, "");
            case "00":
                return createEndResponse("Thank you for using Waqiti Banking. Goodbye!", session.getSessionId());
            default:
                return createContinueResponse("Invalid option. Please try again.", session.getSessionId());
        }
    }
    
    private UssdResponse initiateLoanOperations(SmsSession session) {
        session.setCurrentMenu("LOAN_MENU");
        session.setCurrentStep("START");
        sessionRepository.save(session);
        
        String menu = "Loan Services:\n1. Check Loan Status\n2. Make Loan Payment\n3. Apply for Loan\n0. Back\n00. Exit";
        return createContinueResponse(menu, session.getSessionId());
    }
    
    private UssdResponse handleLoanMenu(SmsSession session, String input) {
        switch (input.trim()) {
            case "1":
                return checkLoanStatus(session);
            case "2":
                return initiateLoanPayment(session);
            case "3":
                return createContinueResponse("Loan application via USSD coming soon.\n0. Back\n00. Exit", session.getSessionId());
            case "0":
                return handleMainMenu(session, "");
            case "00":
                return createEndResponse("Thank you for using Waqiti Banking. Goodbye!", session.getSessionId());
            default:
                return createContinueResponse("Invalid option. Please try again.", session.getSessionId());
        }
    }
    
    private UssdResponse checkLoanStatus(SmsSession session) {
        try {
            UUID userId = coreBankingService.getUserIdByPhoneNumber(session.getPhoneNumber());
            String loanStatus = coreBankingService.getUserLoanStatus(userId);
            
            if (loanStatus == null || loanStatus.isEmpty()) {
                return createContinueResponse("No active loans found.\n0. Back\n00. Exit", session.getSessionId());
            }
            
            return createContinueResponse(loanStatus + "\n0. Back\n00. Exit", session.getSessionId());
            
        } catch (Exception e) {
            log.error("Error checking loan status for session: {}", session.getSessionId(), e);
            return createContinueResponse("Unable to retrieve loan status.\n0. Back\n00. Exit", session.getSessionId());
        }
    }
    
    private UssdResponse initiateLoanPayment(SmsSession session) {
        session.setCurrentStep("LOAN_PAYMENT_AMOUNT");
        sessionRepository.save(session);
        
        return createContinueResponse("Enter loan payment amount:", session.getSessionId());
    }
    
    private UssdResponse initiateSettings(SmsSession session) {
        String menu = "Settings:\n1. Change PIN\n2. Language Settings\n3. Account Info\n0. Back\n00. Exit";
        return createContinueResponse(menu, session.getSessionId());
    }
    
    private UssdResponse requestPinAuthentication(SmsSession session) {
        session.setCurrentMenu("PIN_VERIFICATION");
        session.setCurrentStep("PIN_INPUT");
        sessionRepository.save(session);
        
        return createContinueResponse("Enter your 4-digit PIN:", session.getSessionId());
    }
    
    private UssdResponse handlePinVerification(SmsSession session, String input) {
        if (!session.canAttemptPin()) {
            sessionRepository.delete(session);
            return createEndResponse("Too many failed PIN attempts. Session terminated.", session.getSessionId());
        }
        
        if (input.length() != 4 || !input.matches("\\d{4}")) {
            session.incrementPinAttempts();
            sessionRepository.save(session);
            return createContinueResponse("Invalid PIN format. Enter your 4-digit PIN:", session.getSessionId());
        }
        
        boolean pinValid = securityService.verifyPin(session.getPhoneNumber(), input);
        
        if (pinValid) {
            session.setIsAuthenticated(true);
            session.resetPinAttempts();
            session.setCurrentMenu("MAIN_MENU");
            sessionRepository.save(session);
            
            return handleMainMenu(session, "");
        } else {
            session.incrementPinAttempts();
            sessionRepository.save(session);
            
            int attemptsLeft = 3 - session.getPinAttempts();
            String msg = String.format("Incorrect PIN. %d attempts remaining. Enter your PIN:", attemptsLeft);
            
            return createContinueResponse(msg, session.getSessionId());
        }
    }
    
    private UssdResponse handleConfirmation(SmsSession session, String input) {
        return handleTransferConfirmation(session, input);
    }
    
    private String buildMainMenu(String language) {
        // Support for multiple languages can be added here
        return "Waqiti Banking\n1. Balance Inquiry\n2. Transfer Money\n3. Pay Bills\n4. Loan Services\n5. Settings\n0. Exit";
    }
    
    private boolean isValidPhoneNumber(String phoneNumber) {
        return phoneNumber != null && phoneNumber.matches("^\\+?[1-9]\\d{1,14}$");
    }
    
    private UssdResponse createContinueResponse(String message, String sessionId) {
        return UssdResponse.builder()
            .sessionId(sessionId)
            .message(message)
            .action(UssdResponse.Action.CONTINUE)
            .build();
    }
    
    private UssdResponse createEndResponse(String message, String sessionId) {
        return UssdResponse.builder()
            .sessionId(sessionId)
            .message(message)
            .action(UssdResponse.Action.END)
            .build();
    }
    
    public static class UssdResponse {
        private String sessionId;
        private String message;
        private Action action;
        
        public enum Action {
            CONTINUE, END
        }
        
        public static UssdResponseBuilder builder() {
            return new UssdResponseBuilder();
        }
        
        public static class UssdResponseBuilder {
            private String sessionId;
            private String message;
            private Action action;
            
            public UssdResponseBuilder sessionId(String sessionId) {
                this.sessionId = sessionId;
                return this;
            }
            
            public UssdResponseBuilder message(String message) {
                this.message = message;
                return this;
            }
            
            public UssdResponseBuilder action(Action action) {
                this.action = action;
                return this;
            }
            
            public UssdResponse build() {
                UssdResponse response = new UssdResponse();
                response.sessionId = this.sessionId;
                response.message = this.message;
                response.action = this.action;
                return response;
            }
        }
        
        // Getters
        public String getSessionId() { return sessionId; }
        public String getMessage() { return message; }
        public Action getAction() { return action; }
    }
}