/**
 * Core Banking Service for SMS/USSD Operations
 * Integrates with core banking microservices to provide banking operations
 */
package com.waqiti.smsbanking.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.*;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ThreadLocalRandom;
import org.springframework.retry.annotation.Backoff;
import org.springframework.retry.annotation.Retryable;

@Service
@RequiredArgsConstructor
@Slf4j
public class CoreBankingService {
    
    private final RestTemplate restTemplate;
    
    @Value("${core.banking.user.service.url:http://user-service:8080}")
    private String userServiceUrl;
    
    @Value("${core.banking.account.service.url:http://account-service:8080}")
    private String accountServiceUrl;
    
    @Value("${core.banking.transaction.service.url:http://transaction-service:8080}")
    private String transactionServiceUrl;
    
    @Value("${core.banking.loan.service.url:http://bnpl-service:8080}")
    private String loanServiceUrl;
    
    @Value("${core.banking.notification.service.url:http://notification-service:8080}")
    private String notificationServiceUrl;
    
    public UUID getUserIdByPhoneNumber(String phoneNumber) {
        try {
            log.debug("Getting user ID for phone number: {}", phoneNumber);
            
            String url = userServiceUrl + "/api/v1/users/by-phone?phoneNumber=" + phoneNumber;
            
            ResponseEntity<Map> response = restTemplate.exchange(
                url,
                HttpMethod.GET,
                new HttpEntity<>(createHeaders()),
                Map.class
            );
            
            if (response.getStatusCode() == HttpStatus.OK && response.getBody() != null) {
                Map<String, Object> userInfo = response.getBody();
                String userIdStr = (String) userInfo.get("id");
                return UUID.fromString(userIdStr);
            }
            
            log.warn("User not found for phone number: {}", phoneNumber);
            // Return a guest user ID for unregistered users
            return generateGuestUserId(phoneNumber);
            
        } catch (Exception e) {
            log.error("Error getting user ID for phone number {}: {}", phoneNumber, e.getMessage(), e);
            // Return a guest user ID for error cases
            return generateGuestUserId(phoneNumber);
        }
    }
    
    public String getUserPinHash(UUID userId) {
        try {
            log.debug("Getting PIN hash for user: {}", userId);
            
            String url = userServiceUrl + "/api/v1/users/" + userId + "/pin-hash";
            
            ResponseEntity<Map> response = restTemplate.exchange(
                url,
                HttpMethod.GET,
                new HttpEntity<>(createHeaders()),
                Map.class
            );
            
            if (response.getStatusCode() == HttpStatus.OK && response.getBody() != null) {
                Map<String, Object> pinInfo = response.getBody();
                return (String) pinInfo.get("pinHash");
            }
            
            log.warn("PIN hash not found for user: {}", userId);
            // Return a default hash for users without PIN
            return generateDefaultPinHash(userId);
            
        } catch (Exception e) {
            log.error("Error getting PIN hash for user {}: {}", userId, e.getMessage(), e);
            // Return a default hash for error cases
            return generateDefaultPinHash(userId);
        }
    }
    
    public String getAccountBalance(UUID userId) {
        try {
            log.debug("Getting account balance for user: {}", userId);
            
            String url = accountServiceUrl + "/api/v1/accounts/user/" + userId + "/balance";
            
            ResponseEntity<Map> response = restTemplate.exchange(
                url,
                HttpMethod.GET,
                new HttpEntity<>(createHeaders()),
                Map.class
            );
            
            if (response.getStatusCode() == HttpStatus.OK && response.getBody() != null) {
                Map<String, Object> balanceInfo = response.getBody();
                BigDecimal balance = new BigDecimal(balanceInfo.get("availableBalance").toString());
                String currency = (String) balanceInfo.get("currency");
                
                return String.format("%s %.2f", currency, balance);
            }
            
            return "Balance unavailable";
            
        } catch (Exception e) {
            log.error("Error getting account balance for user {}: {}", userId, e.getMessage(), e);
            return "Balance unavailable";
        }
    }
    
    public double getAccountBalanceAsDouble(UUID userId) {
        try {
            String url = accountServiceUrl + "/api/v1/accounts/user/" + userId + "/balance";
            
            ResponseEntity<Map> response = restTemplate.exchange(
                url,
                HttpMethod.GET,
                new HttpEntity<>(createHeaders()),
                Map.class
            );
            
            if (response.getStatusCode() == HttpStatus.OK && response.getBody() != null) {
                Map<String, Object> balanceInfo = response.getBody();
                return Double.parseDouble(balanceInfo.get("availableBalance").toString());
            }
            
            return 0.0;
            
        } catch (Exception e) {
            log.error("Error getting account balance as double for user {}: {}", userId, e.getMessage(), e);
            return 0.0;
        }
    }
    
    public String processTransfer(UUID senderId, UUID recipientId, double amount, String description) {
        try {
            log.info("Processing transfer: {} -> {} amount: {}", senderId, recipientId, amount);
            
            Map<String, Object> transferRequest = new HashMap<>();
            transferRequest.put("senderId", senderId.toString());
            transferRequest.put("recipientId", recipientId.toString());
            transferRequest.put("amount", amount);
            transferRequest.put("currency", "USD");
            transferRequest.put("description", description);
            transferRequest.put("channel", "SMS_BANKING");
            transferRequest.put("reference", generateTransactionReference());
            
            String url = transactionServiceUrl + "/api/v1/transactions/transfer";
            
            HttpEntity<Map<String, Object>> entity = new HttpEntity<>(transferRequest, createHeaders());
            
            ResponseEntity<Map> response = restTemplate.exchange(
                url,
                HttpMethod.POST,
                entity,
                Map.class
            );
            
            if (response.getStatusCode() == HttpStatus.OK && response.getBody() != null) {
                Map<String, Object> result = response.getBody();
                return (String) result.get("transactionReference");
            }
            
            throw new RuntimeException("Transfer failed");
            
        } catch (Exception e) {
            log.error("Error processing transfer: {}", e.getMessage(), e);
            throw new RuntimeException("Transfer failed: " + e.getMessage());
        }
    }
    
    public String purchaseAirtime(UUID userId, String recipientPhone, double amount) {
        try {
            log.info("Processing airtime purchase for user: {} amount: {}", userId, amount);
            
            Map<String, Object> airtimeRequest = new HashMap<>();
            airtimeRequest.put("userId", userId.toString());
            airtimeRequest.put("recipientPhone", recipientPhone);
            airtimeRequest.put("amount", amount);
            airtimeRequest.put("provider", "AUTO_DETECT");
            airtimeRequest.put("channel", "SMS_BANKING");
            airtimeRequest.put("reference", generateTransactionReference());
            
            String url = transactionServiceUrl + "/api/v1/transactions/airtime";
            
            HttpEntity<Map<String, Object>> entity = new HttpEntity<>(airtimeRequest, createHeaders());
            
            ResponseEntity<Map> response = restTemplate.exchange(
                url,
                HttpMethod.POST,
                entity,
                Map.class
            );
            
            if (response.getStatusCode() == HttpStatus.OK && response.getBody() != null) {
                Map<String, Object> result = response.getBody();
                return (String) result.get("transactionReference");
            }
            
            throw new RuntimeException("Airtime purchase failed");
            
        } catch (Exception e) {
            log.error("Error processing airtime purchase: {}", e.getMessage(), e);
            throw new RuntimeException("Airtime purchase failed: " + e.getMessage());
        }
    }
    
    public String getUserLoanStatus(UUID userId) {
        try {
            log.debug("Getting loan status for user: {}", userId);
            
            String url = loanServiceUrl + "/api/v1/loans/users/" + userId + "/applications";
            
            ResponseEntity<Map[]> response = restTemplate.exchange(
                url,
                HttpMethod.GET,
                new HttpEntity<>(createHeaders()),
                Map[].class
            );
            
            if (response.getStatusCode() == HttpStatus.OK && response.getBody() != null) {
                Map<String, Object>[] loans = response.getBody();
                
                if (loans.length == 0) {
                    return null;
                }
                
                StringBuilder status = new StringBuilder("Loan Status:\n");
                for (Map<String, Object> loan : loans) {
                    String loanNumber = (String) loan.get("loanNumber");
                    String loanStatus = (String) loan.get("status");
                    BigDecimal outstandingBalance = new BigDecimal(loan.get("outstandingBalance").toString());
                    
                    status.append(String.format("Loan: %s\nStatus: %s\nBalance: $%.2f\n\n", 
                        loanNumber, loanStatus, outstandingBalance));
                }
                
                return status.toString().trim();
            }
            
            return "No active loans found.";
            
        } catch (Exception e) {
            log.error("Error getting loan status for user {}: {}", userId, e.getMessage(), e);
            return "Unable to retrieve loan status. Please try again later.";
        }
    }
    
    public boolean hasActiveLoans(UUID userId) {
        try {
            String url = loanServiceUrl + "/api/v1/loans/users/" + userId + "/applications";
            
            ResponseEntity<Map[]> response = restTemplate.exchange(
                url,
                HttpMethod.GET,
                new HttpEntity<>(createHeaders()),
                Map[].class
            );
            
            if (response.getStatusCode() == HttpStatus.OK && response.getBody() != null) {
                Map<String, Object>[] loans = response.getBody();
                
                for (Map<String, Object> loan : loans) {
                    String status = (String) loan.get("status");
                    if ("ACTIVE".equals(status) || "OVERDUE".equals(status)) {
                        return true;
                    }
                }
            }
            
            return false;
            
        } catch (Exception e) {
            log.error("Error checking active loans for user {}: {}", userId, e.getMessage(), e);
            return false;
        }
    }
    
    public String processLoanPayment(UUID userId, double amount, String description) {
        try {
            log.info("Processing loan payment for user: {} amount: {}", userId, amount);
            
            Map<String, Object> paymentRequest = new HashMap<>();
            paymentRequest.put("userId", userId.toString());
            paymentRequest.put("amount", amount);
            paymentRequest.put("paymentMethod", "ACCOUNT_DEBIT");
            paymentRequest.put("channel", "SMS_BANKING");
            paymentRequest.put("reference", generateTransactionReference());
            paymentRequest.put("description", description);
            
            String url = loanServiceUrl + "/api/v1/loans/payments";
            
            HttpEntity<Map<String, Object>> entity = new HttpEntity<>(paymentRequest, createHeaders());
            
            ResponseEntity<Map> response = restTemplate.exchange(
                url,
                HttpMethod.POST,
                entity,
                Map.class
            );
            
            if (response.getStatusCode() == HttpStatus.OK && response.getBody() != null) {
                Map<String, Object> result = response.getBody();
                return (String) result.get("transactionReference");
            }
            
            throw new RuntimeException("Loan payment failed");
            
        } catch (Exception e) {
            log.error("Error processing loan payment: {}", e.getMessage(), e);
            throw new RuntimeException("Loan payment failed: " + e.getMessage());
        }
    }
    
    public String getMiniStatement(UUID userId, int days) {
        try {
            log.debug("Getting mini statement for user: {} for {} days", userId, days);
            
            LocalDate endDate = LocalDate.now();
            LocalDate startDate = endDate.minusDays(days);
            
            String url = transactionServiceUrl + "/api/v1/transactions/user/" + userId + 
                "/statement?startDate=" + startDate + "&endDate=" + endDate + "&limit=10";
            
            ResponseEntity<Map[]> response = restTemplate.exchange(
                url,
                HttpMethod.GET,
                new HttpEntity<>(createHeaders()),
                Map[].class
            );
            
            if (response.getStatusCode() == HttpStatus.OK && response.getBody() != null) {
                Map<String, Object>[] transactions = response.getBody();
                
                if (transactions.length == 0) {
                    return "No transactions found for the specified period.";
                }
                
                StringBuilder statement = new StringBuilder("Mini Statement (" + days + " days):\n\n");
                
                for (Map<String, Object> transaction : transactions) {
                    String date = ((String) transaction.get("transactionDate")).substring(0, 10);
                    String type = (String) transaction.get("transactionType");
                    BigDecimal amount = new BigDecimal(transaction.get("amount").toString());
                    String description = (String) transaction.get("description");
                    
                    statement.append(String.format("%s %s $%.2f\n%s\n\n", 
                        date, type, amount, description));
                }
                
                return statement.toString().trim();
            }
            
            return "Statement unavailable";
            
        } catch (Exception e) {
            log.error("Error getting mini statement for user {}: {}", userId, e.getMessage(), e);
            return "Statement unavailable";
        }
    }
    
    public void sendNotification(UUID userId, String message, String channel) {
        try {
            log.debug("Sending notification to user: {} via {}", userId, channel);
            
            Map<String, Object> notificationRequest = new HashMap<>();
            notificationRequest.put("userId", userId.toString());
            notificationRequest.put("message", message);
            notificationRequest.put("channel", channel);
            notificationRequest.put("priority", "NORMAL");
            notificationRequest.put("timestamp", LocalDateTime.now().toString());
            
            String url = notificationServiceUrl + "/api/v1/notifications/send";
            
            HttpEntity<Map<String, Object>> entity = new HttpEntity<>(notificationRequest, createHeaders());
            
            restTemplate.exchange(
                url,
                HttpMethod.POST,
                entity,
                Void.class
            );
            
        } catch (Exception e) {
            log.error("Error sending notification: {}", e.getMessage(), e);
        }
    }
    
    private HttpHeaders createHeaders() {
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        headers.set("X-Service-Name", "SMS-Banking-Service");
        headers.set("X-Channel", "SMS_USSD");
        return headers;
    }
    
    private String generateTransactionReference() {
        return "SMS-" + UUID.randomUUID().toString().substring(0, 8).toUpperCase();
    }
    
    /**
     * Generate a guest user ID for unregistered phone numbers
     */
    private UUID generateGuestUserId(String phoneNumber) {
        // Create a deterministic UUID based on phone number
        return UUID.nameUUIDFromBytes(("GUEST_" + phoneNumber).getBytes());
    }
    
    /**
     * Generate a default PIN hash for users without set PINs
     */
    private String generateDefaultPinHash(UUID userId) {
        // This should be replaced with actual encryption in production
        return "DEFAULT_HASH_" + userId.toString().substring(0, 8);
    }
    
    /**
     * Custom exception classes for better error handling
     */
    public static class CoreBankingException extends RuntimeException {
        public CoreBankingException(String message) {
            super(message);
        }
        
        public CoreBankingException(String message, Throwable cause) {
            super(message, cause);
        }
    }
    
    public static class UserNotFoundException extends RuntimeException {
        public UserNotFoundException(String message) {
            super(message);
        }
    }
}