# Feign Clients Reference Guide - Customer Service

## Overview
Production-ready Feign client interfaces for inter-service communication in the customer-service microservice.

**Location:** `/services/customer-service/src/main/java/com/waqiti/customer/client/`

**Total Files:** 30 (7 clients, 7 fallbacks, 15 DTOs, 1 config)

---

## Quick Start

### 1. Enable Feign Clients

Add to `CustomerServiceApplication.java`:

```java
@SpringBootApplication
@EnableFeignClients(basePackages = "com.waqiti.customer.client")
public class CustomerServiceApplication {
    public static void main(String[] args) {
        SpringApplication.run(CustomerServiceApplication.class, args);
    }
}
```

### 2. Inject and Use

```java
@Service
@RequiredArgsConstructor
public class CustomerService {

    private final AccountServiceClient accountServiceClient;
    private final NotificationServiceClient notificationServiceClient;

    public void processCustomer(String customerId) {
        // Fetch accounts
        List<AccountResponse> accounts = accountServiceClient.getAccountsByCustomerId(customerId);

        // Send notification
        notificationServiceClient.sendEmail(
            EmailNotificationRequest.builder()
                .to("customer@example.com")
                .subject("Account Update")
                .body("Your accounts have been updated")
                .build()
        );
    }
}
```

---

## Client Interfaces

### 1. AccountServiceClient (6 methods)
**Service:** `account-service`

| Method | Endpoint | Description |
|--------|----------|-------------|
| `getAccount(accountId)` | GET `/api/v1/accounts/{accountId}` | Get account details |
| `getBalance(accountId)` | GET `/api/v1/accounts/{accountId}/balance` | Get balance info |
| `getAccountStatus(accountId)` | GET `/api/v1/accounts/{accountId}/status` | Get account status |
| `getAccountsByCustomerId(customerId)` | GET `/api/v1/accounts/customer/{customerId}` | List customer accounts |
| `freezeAccount(accountId)` | POST `/api/v1/accounts/{accountId}/freeze` | Freeze account |
| `getAccountCreationDate(accountId)` | GET `/api/v1/accounts/{accountId}/creation-date` | Get creation date |

### 2. WalletServiceClient (4 methods)
**Service:** `wallet-service`

| Method | Endpoint | Description |
|--------|----------|-------------|
| `getWallet(walletId)` | GET `/api/v1/wallets/{walletId}` | Get wallet details |
| `getWalletsByCustomerId(customerId)` | GET `/api/v1/wallets/customer/{customerId}` | List customer wallets |
| `getBalance(walletId)` | GET `/api/v1/wallets/{walletId}/balance` | Get wallet balance |
| `freezeWallet(walletId)` | POST `/api/v1/wallets/{walletId}/freeze` | Freeze wallet |

### 3. UserServiceClient (4 methods)
**Service:** `user-service`

| Method | Endpoint | Description |
|--------|----------|-------------|
| `getUser(userId)` | GET `/api/v1/users/{userId}` | Get user details |
| `getUserByCustomerId(customerId)` | GET `/api/v1/users/customer/{customerId}` | Get user by customer ID |
| `deactivateUser(userId)` | POST `/api/v1/users/{userId}/deactivate` | Deactivate user |
| `getUserProfile(userId)` | GET `/api/v1/users/{userId}/profile` | Get user profile |

### 4. LedgerServiceClient (4 methods)
**Service:** `ledger-service`

| Method | Endpoint | Description |
|--------|----------|-------------|
| `getPendingTransactions(accountId)` | GET `/api/v1/ledger/account/{accountId}/pending-transactions` | List pending transactions |
| `getPendingDebits(accountId)` | GET `/api/v1/ledger/account/{accountId}/pending-debits` | Get pending debits sum |
| `getPendingCredits(accountId)` | GET `/api/v1/ledger/account/{accountId}/pending-credits` | Get pending credits sum |
| `freezeLedger(accountId)` | POST `/api/v1/ledger/account/{accountId}/freeze` | Freeze ledger |

### 5. LegalServiceClient (3 methods)
**Service:** `legal-service`

| Method | Endpoint | Description |
|--------|----------|-------------|
| `getLegalHolds(customerId)` | GET `/api/v1/legal/customer/{customerId}/holds` | List legal holds |
| `hasLegalHolds(customerId)` | GET `/api/v1/legal/customer/{customerId}/has-holds` | Check for legal holds |
| `getSubpoenas(customerId)` | GET `/api/v1/legal/customer/{customerId}/subpoenas` | List subpoenas |

### 6. DisputeServiceClient (3 methods)
**Service:** `dispute-service`

| Method | Endpoint | Description |
|--------|----------|-------------|
| `getActiveDisputes(customerId)` | GET `/api/v1/disputes/customer/{customerId}/active` | List active disputes |
| `hasActiveDisputes(customerId)` | GET `/api/v1/disputes/customer/{customerId}/has-active` | Check for active disputes |
| `getDisputesByAccount(accountId)` | GET `/api/v1/disputes/account/{accountId}` | List account disputes |

### 7. NotificationServiceClient (4 methods)
**Service:** `notification-service`

| Method | Endpoint | Description |
|--------|----------|-------------|
| `sendNotification(request)` | POST `/api/v1/notifications/send` | Send general notification |
| `sendEmail(request)` | POST `/api/v1/notifications/email` | Send email |
| `sendSms(request)` | POST `/api/v1/notifications/sms` | Send SMS |
| `sendPushNotification(request)` | POST `/api/v1/notifications/push` | Send push notification |

---

## Data Transfer Objects (DTOs)

### Account Service DTOs
- **AccountResponse** - Complete account information (18 fields)
- **BalanceResponse** - Balance breakdown (8 fields)
- **AccountStatusResponse** - Status flags and tracking (10 fields)

### Wallet Service DTOs
- **WalletResponse** - Wallet information (11 fields)
- **WalletBalanceResponse** - Balance details (6 fields)

### User Service DTOs
- **UserResponse** - User authentication info (17 fields)
- **UserProfileResponse** - Extended profile (16 fields)

### Ledger Service DTOs
- **PendingTransactionResponse** - Transaction details (12 fields)

### Legal Service DTOs
- **LegalHoldResponse** - Legal hold info (13 fields)
- **SubpoenaResponse** - Subpoena tracking (13 fields)

### Dispute Service DTOs
- **DisputeResponse** - Dispute information (17 fields)

### Notification Service DTOs
- **NotificationRequest** - General notification (10 fields)
- **EmailNotificationRequest** - Email notification (11 fields)
- **SmsNotificationRequest** - SMS notification (9 fields)
- **PushNotificationRequest** - Push notification (13 fields)

---

## Fallback Behavior

All clients have fallback implementations that:

1. **Log errors** at ERROR level with context
2. **Return safe defaults:**
   - `null` for single objects
   - Empty lists for collections
   - `BigDecimal.ZERO` for amounts
   - `false` for boolean checks
3. **Never throw exceptions**

### Example Fallback Log

```
ERROR c.w.c.c.f.AccountServiceClientFallback - AccountServiceClient.getAccount fallback triggered for accountId: ACC-123
```

---

## Configuration

### FeignClientConfig.java

**Retry Policy:**
- Max attempts: 3
- Initial interval: 100ms
- Max interval: 1000ms
- Exponential backoff

**Timeouts:**
- Connect timeout: 5 seconds
- Read timeout: 10 seconds

**Logging:**
- Level: FULL (headers, body, metadata)

**Error Handling:**
- Custom error decoder with detailed logging

---

## Testing

### Unit Test Example

```java
@ExtendWith(MockitoExtension.class)
class CustomerServiceTest {

    @Mock
    private AccountServiceClient accountServiceClient;

    @InjectMocks
    private CustomerService customerService;

    @Test
    void testGetCustomerAccounts() {
        // Given
        String customerId = "CUST-123";
        List<AccountResponse> mockAccounts = Arrays.asList(
            AccountResponse.builder()
                .accountId("ACC-1")
                .customerId(customerId)
                .balance(new BigDecimal("1000.00"))
                .build()
        );

        when(accountServiceClient.getAccountsByCustomerId(customerId))
            .thenReturn(mockAccounts);

        // When
        List<AccountResponse> result = customerService.getAccounts(customerId);

        // Then
        assertThat(result).hasSize(1);
        assertThat(result.get(0).getAccountId()).isEqualTo("ACC-1");
    }
}
```

---

## Application Properties

```yaml
# Feign Configuration
feign:
  client:
    config:
      default:
        connectTimeout: 5000
        readTimeout: 10000
        loggerLevel: full

  # Circuit Breaker (if using Resilience4j)
  circuitbreaker:
    enabled: true

# Eureka Service Discovery
eureka:
  client:
    enabled: true
    serviceUrl:
      defaultZone: http://localhost:8761/eureka/

# Logging
logging:
  level:
    com.waqiti.customer.client: DEBUG
    feign: DEBUG
```

---

## Error Handling Best Practices

### 1. Handle Fallback Scenarios

```java
List<AccountResponse> accounts = accountServiceClient.getAccountsByCustomerId(customerId);
if (accounts == null || accounts.isEmpty()) {
    log.warn("No accounts found for customer: {}", customerId);
    // Handle fallback scenario
}
```

### 2. Validate Responses

```java
BalanceResponse balance = accountServiceClient.getBalance(accountId);
if (balance != null && balance.getCurrentBalance() != null) {
    // Safe to use balance
    processBalance(balance.getCurrentBalance());
}
```

### 3. Use Try-Catch for Critical Operations

```java
try {
    accountServiceClient.freezeAccount(accountId);
    log.info("Account frozen successfully: {}", accountId);
} catch (FeignException e) {
    log.error("Failed to freeze account: {}", accountId, e);
    // Handle error - send alert, retry later, etc.
}
```

---

## Monitoring

### Key Metrics to Monitor

1. **Request Rate:** Number of calls per second to each service
2. **Error Rate:** Percentage of failed requests
3. **Response Time:** P50, P95, P99 latencies
4. **Fallback Rate:** How often fallbacks are triggered
5. **Circuit Breaker State:** Open/Closed/Half-Open

### Example Micrometer Metrics

```java
@Timed(value = "feign.client.account", percentiles = {0.5, 0.95, 0.99})
```

---

## Troubleshooting

### Common Issues

1. **Connection Refused**
   - Check service discovery registration
   - Verify target service is running
   - Check network connectivity

2. **Timeout Errors**
   - Increase timeout values if needed
   - Check target service performance
   - Review query complexity

3. **Fallback Always Triggered**
   - Check circuit breaker state
   - Verify service health
   - Review logs for root cause

4. **Serialization Errors**
   - Ensure DTO fields match API response
   - Check Jackson configuration
   - Verify content-type headers

---

## Migration Guide

If you have existing REST templates, migrate to Feign clients:

### Before (RestTemplate)

```java
String url = "http://account-service/api/v1/accounts/" + accountId;
AccountResponse response = restTemplate.getForObject(url, AccountResponse.class);
```

### After (Feign Client)

```java
AccountResponse response = accountServiceClient.getAccount(accountId);
```

**Benefits:**
- Type-safe interface
- Automatic retry and circuit breaking
- Cleaner code
- Better error handling
- Service discovery integration

---

## Additional Resources

- [Spring Cloud OpenFeign Documentation](https://spring.io/projects/spring-cloud-openfeign)
- [Resilience4j Circuit Breaker](https://resilience4j.readme.io/docs/circuitbreaker)
- [Eureka Service Discovery](https://spring.io/projects/spring-cloud-netflix)

---

**Generated:** 2025-11-20
**Version:** 1.0
**Author:** Waqiti Engineering Team
