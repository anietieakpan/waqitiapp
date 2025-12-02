# Waqiti Payment Commons Library

The Payment Commons library provides shared domain models, DTOs, utilities, and business logic for all payment-related services in the Waqiti platform. This library eliminates code duplication and ensures consistency across payment, transaction, wallet, and other financial services.

## Features

### 🏦 Core Domain Models
- **Money**: Immutable value object with currency support and arithmetic operations
- **PaymentStatus**: Standardized payment status enum with state transition logic
- **PaymentMethod**: Comprehensive payment method enum with capabilities and properties

### 📋 Data Transfer Objects (DTOs)
- **PaymentRequest**: Standardized payment request with validation and business logic
- **PaymentResponse**: Comprehensive payment response with status tracking and actions

### 🔧 Utilities
- **PaymentValidator**: Comprehensive validation for payments, currencies, and business rules
- **CurrencyConverter**: Currency conversion utilities with exchange rate management

### ⚠️ Exception Handling
- **PaymentException**: Hierarchical exception system with specific error types and recovery suggestions

## Installation

Add the dependency to your service's `pom.xml`:

```xml
<dependency>
    <groupId>com.waqiti</groupId>
    <artifactId>payment-commons</artifactId>
    <version>1.0.0-SNAPSHOT</version>
</dependency>
```

## Usage Examples

### Money Operations

```java
// Create money amounts
Money amount1 = Money.of(100.50, "USD");
Money amount2 = Money.of(new BigDecimal("50.25"), "USD");

// Arithmetic operations
Money sum = amount1.add(amount2);           // $150.75
Money difference = amount1.subtract(amount2); // $50.25
Money doubled = amount1.multiply(2.0);         // $201.00
Money half = amount1.divide(2.0);              // $50.25

// Validation
amount1.validatePositive();
amount1.validateMinimumAmount(Money.of(10.0, "USD"));

// Comparison
boolean isGreater = amount1.isGreaterThan(amount2); // true
```

### Payment Request Creation

```java
PaymentRequest request = PaymentRequest.builder()
    .withDefaults()  // Sets sensible defaults
    .senderId(senderId)
    .recipientId(recipientId)
    .recipientType("USER")
    .amount(100.0, "USD")  // Convenience method
    .paymentMethod("credit_card")  // String conversion
    .description("Coffee purchase")
    .notificationChannels(new String[]{"EMAIL", "PUSH"})
    .build();

// Validation
request.validate();  // Throws exception if invalid

// Business logic checks
boolean isHighValue = request.isHighValue();     // false
boolean isInternational = request.isInternational(); // depends on countries
```

### Payment Response Handling

```java
PaymentResponse response = PaymentResponse.builder()
    .generatePaymentId()  // Auto-generates UUID
    .withTimestamps()     // Sets created/updated timestamps
    .requestId(request.getRequestId())
    .amount(100.0, "USD")
    .processedAmount(100.0, "USD")
    .fees(2.50, "USD")
    .netAmount(97.50, "USD")
    .status("completed")
    .paymentMethod("credit_card")
    .addAction("refund", "Refund Payment", true)
    .build();

// Status checks
if (response.isSuccessful()) {
    // Handle success
}

// Action checks
if (response.canPerformAction("refund")) {
    PaymentAction refundAction = response.getAction("refund");
    // Show refund option to user
}
```

### Payment Validation

```java
PaymentValidator.ValidationResult result = 
    PaymentValidator.validatePaymentRequest(request);

if (result.isValid()) {
    // Process payment
    if (result.hasWarnings()) {
        log.warn("Payment validation warnings: {}", result.getWarningMessage());
    }
} else {
    // Handle validation errors
    throw new IllegalArgumentException("Validation failed: " + result.getErrorMessage());
}

// Individual validations
boolean validCurrency = PaymentValidator.isValidCurrencyCode("USD");
boolean validEmail = PaymentValidator.isValidEmail("user@example.com");
boolean validIBAN = PaymentValidator.isValidIBAN("GB29 NWBK 6016 1331 9268 19");
```

### Currency Conversion

```java
Money usdAmount = Money.of(100.0, "USD");

// Convert to EUR
Money eurAmount = CurrencyConverter.convert(usdAmount, "EUR");

// Get conversion preview
CurrencyConverter.ConversionPreview preview = 
    CurrencyConverter.getConversionPreview(usdAmount, "EUR");

System.out.println("Exchange Rate: " + preview.getExchangeRate());
System.out.println("Converted Amount: " + preview.getConvertedAmount());
System.out.println("Estimated Fees: " + preview.getEstimatedFees());

// Convert with fees
CurrencyConverter.ConversionResult result = 
    CurrencyConverter.convertWithFees(usdAmount, "EUR", new BigDecimal("1.5"));

System.out.println("Net Amount: " + result.getNetAmount());
```

### Exception Handling

```java
try {
    // Payment processing logic
    processPayment(request);
} catch (PaymentException.InsufficientFundsException e) {
    // Handle insufficient funds
    return PaymentResponse.builder()
        .status(e.getSuggestedStatus())
        .errorCode(e.getErrorCode())
        .errorMessage(e.getMessage())
        .build();
} catch (PaymentException.FraudDetectedException e) {
    // Handle fraud detection
    auditService.logFraudAttempt(e.getErrorContext());
    return PaymentResponse.builder()
        .status(PaymentStatus.FLAGGED)
        .build();
} catch (PaymentException e) {
    // Handle other payment exceptions
    if (e.isRetryable()) {
        scheduleRetry(request);
    }
    throw e;
}

// Factory methods for common exceptions
throw PaymentException.insufficientFunds(accountId);
throw PaymentException.limitExceeded("Daily", "$1000");
throw PaymentException.fraudDetected("Risk score: 95%");
```

## Payment Status Flow

The library includes comprehensive payment status management:

```
PENDING → PROCESSING → COMPLETED
    ↓         ↓           ↓
CANCELLED  FAILED    REFUNDED
```

### Status Categories

- **Pending**: `PENDING`, `INITIATED`, `SCHEDULED`
- **Processing**: `PROCESSING`, `VALIDATING`, `AUTHORIZING`, `CAPTURED`, `SETTLING`
- **Successful**: `COMPLETED`, `SETTLED`
- **Failed**: `FAILED`, `REJECTED`, `DECLINED`, `EXPIRED`, `ERROR`, `TIMEOUT`
- **Cancelled**: `CANCELLED`, `REFUNDED`, `PARTIALLY_REFUNDED`
- **Under Review**: `UNDER_REVIEW`, `FLAGGED`

### Status Transitions

```java
PaymentStatus current = PaymentStatus.PENDING;
PaymentStatus next = PaymentStatus.PROCESSING;

if (current.canTransitionTo(next)) {
    // Valid transition
    updatePaymentStatus(paymentId, next);
} else {
    throw PaymentException.invalidStatus(current.getCode(), next.getCode());
}
```

## Payment Method Support

The library supports a comprehensive range of payment methods:

### Categories
- **Cards**: Credit, Debit, Prepaid, Virtual
- **Bank Transfers**: ACH, Wire, SEPA, SWIFT
- **Digital Wallets**: PayPal, Apple Pay, Google Pay, Samsung Pay
- **Cryptocurrency**: Bitcoin, Ethereum, Litecoin
- **BNPL**: Klarna, Afterpay
- **Other**: Cash, Check, QR Code, NFC

### Method Properties

```java
PaymentMethod method = PaymentMethod.CREDIT_CARD;

// Capabilities
boolean instant = method.isInstantSettlement();     // true
boolean refundable = method.supportsRefunds();      // true
boolean chargeback = method.supportsChargebacks();  // true
boolean kyc = method.requiresKYC();                 // false

// Processing time
int processingMinutes = method.getTypicalProcessingTimeMinutes(); // 0
```

## Validation Rules

The library includes comprehensive validation:

### Business Rules
- Positive amounts only
- Supported currency codes (ISO 4217)
- Valid payment method for currency
- Geographic restrictions
- Compliance requirements (KYC, AML)
- Self-payment prevention
- Expiration checks

### Format Validations
- Email addresses
- Phone numbers
- IBAN codes
- SWIFT/BIC codes
- Currency codes

### Amount Limits
- Minimum: $0.01
- Maximum: $1,000,000
- High-value threshold: $10,000+ (triggers enhanced compliance)

## Integration with Services

### Payment Service
```java
@Service
public class PaymentService {
    
    public PaymentResponse processPayment(PaymentRequest request) {
        // Validate request
        PaymentValidator.ValidationResult validation = 
            PaymentValidator.validatePaymentRequest(request);
        
        if (!validation.isValid()) {
            throw new PaymentException.InvalidPaymentRequestException(
                validation.getErrorMessage()
            );
        }
        
        // Process payment using commons models
        // ...
    }
}
```

### Transaction Service
```java
@Entity
public class Transaction {
    
    @Embedded
    private Money amount;
    
    @Enumerated(EnumType.STRING)
    private PaymentStatus status;
    
    @Enumerated(EnumType.STRING)
    private PaymentMethod paymentMethod;
    
    // ... other fields
}
```

### Wallet Service
```java
@Service
public class WalletService {
    
    public void debitWallet(UUID walletId, Money amount) {
        // Use Money for type-safe amount handling
        amount.validatePositive();
        
        Wallet wallet = getWallet(walletId);
        if (wallet.getBalance().isLessThan(amount)) {
            throw PaymentException.insufficientFunds(walletId.toString());
        }
        
        // Process debit
        // ...
    }
}
```

## Testing

The library includes comprehensive test coverage:

```bash
mvn test
```

Key test areas:
- Money arithmetic and validation
- Payment status transitions
- Payment method properties
- Request/response DTO validation
- Currency conversion
- Exception handling
- Business rule validation

## Best Practices

### 1. Use Money for All Monetary Values
```java
// ✅ Good
private Money amount;

// ❌ Avoid
private BigDecimal amount;
private String currency;
```

### 2. Validate Early and Often
```java
// Validate immediately upon receipt
PaymentValidator.ValidationResult result = 
    PaymentValidator.validatePaymentRequest(request);

if (!result.isValid()) {
    throw new PaymentException.InvalidPaymentRequestException(
        result.getErrorMessage()
    );
}
```

### 3. Use Specific Exception Types
```java
// ✅ Specific exception with context
throw PaymentException.insufficientFunds(accountId);

// ❌ Generic exception
throw new PaymentException("Not enough money");
```

### 4. Check Status Transitions
```java
// ✅ Validate transitions
if (!currentStatus.canTransitionTo(newStatus)) {
    throw PaymentException.invalidStatus(currentStatus.getCode(), newStatus.getCode());
}
```

### 5. Handle Currency Conversion Safely
```java
// ✅ Check conversion support
if (CurrencyConverter.isConversionSupported(fromCurrency, toCurrency)) {
    Money converted = CurrencyConverter.convert(amount, toCurrency);
} else {
    throw PaymentException.currencyNotSupported(toCurrency);
}
```

## Migration Guide

### From Payment Service DTOs

**Before:**
```java
public class CreatePaymentRequest {
    private UUID senderId;
    private UUID recipientId;
    private BigDecimal amount;
    private String currency;
    // ...
}
```

**After:**
```java
// Use payment-commons DTO
import com.waqiti.payment.commons.dto.PaymentRequest;

PaymentRequest request = PaymentRequest.builder()
    .withDefaults()
    .senderId(senderId)
    .recipientId(recipientId)
    .amount(amount, currency)  // Uses Money internally
    .build();
```

### From Custom Status Enums

**Before:**
```java
public enum TransactionStatus {
    PENDING, COMPLETED, FAILED
}
```

**After:**
```java
// Use standardized status
import com.waqiti.payment.commons.domain.PaymentStatus;

PaymentStatus status = PaymentStatus.PENDING;
if (status.canTransitionTo(PaymentStatus.COMPLETED)) {
    // Valid transition
}
```

## Contributing

When contributing to payment-commons:

1. **Maintain Backward Compatibility**: Existing APIs should not break
2. **Add Comprehensive Tests**: All new features need test coverage
3. **Update Documentation**: Keep README and JavaDoc current
4. **Follow Naming Conventions**: Use clear, descriptive names
5. **Validate Business Rules**: Ensure new validations align with business requirements

## Version History

- **1.0.0-SNAPSHOT**: Initial release with core domain models, DTOs, and utilities

## Dependencies

- Spring Boot Starter Validation
- Spring Boot Starter Data JPA
- Jackson (JSON processing)
- Java Money API (Moneta)
- Lombok
- JUnit 5 (testing)

## License

This library is part of the Waqiti platform and follows the same licensing terms.