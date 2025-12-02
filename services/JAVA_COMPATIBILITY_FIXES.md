# Java 17 Compatibility Fixes

## Files That Need Modification for Java 17

### 1. PaymentValidationServiceImpl.java
**Line ~244-250**: Replace switch expression
```java
// Replace this:
int refundWindowDays = switch (paymentMethod.toLowerCase()) {
    case "credit_card" -> 120;
    case "debit_card" -> 60;
    case "ach", "bank_transfer" -> 30;
    case "crypto" -> 7;
    default -> 180;
};

// With this:
int refundWindowDays;
switch (paymentMethod.toLowerCase()) {
    case "credit_card":
        refundWindowDays = 120;
        break;
    case "debit_card":
        refundWindowDays = 60;
        break;
    case "ach":
    case "bank_transfer":
        refundWindowDays = 30;
        break;
    case "crypto":
        refundWindowDays = 7;
        break;
    default:
        refundWindowDays = 180;
        break;
}
```

### 2. PaymentService.java
**Line ~1817-1830**: Replace switch expression in `determinePaymentProvider()`
```java
// Replace switch expression with traditional switch statement
```

**Line ~1918-1937**: Replace switch expression in `calculateRefundArrival()`
```java
// Replace switch expression with traditional switch statement
```

### 3. PaymentAuditServiceImpl.java
**Line ~1042**: Replace switch expression in `determineThreatLevel()`
```java
// Replace:
return switch (violationType) {
    case "FRAUD_ATTEMPT", "SYSTEM_INTRUSION" -> SecurityAuditRecord.ThreatLevel.CRITICAL;
    case "SUSPICIOUS_PAYMENT_PATTERN", "SELF_PAYMENT_ATTEMPT" -> SecurityAuditRecord.ThreatLevel.MEDIUM;
    case "INSUFFICIENT_KYC_VERIFICATION" -> SecurityAuditRecord.ThreatLevel.LOW;
    default -> SecurityAuditRecord.ThreatLevel.LOW;
};

// With:
switch (violationType) {
    case "FRAUD_ATTEMPT":
    case "SYSTEM_INTRUSION":
        return SecurityAuditRecord.ThreatLevel.CRITICAL;
    case "SUSPICIOUS_PAYMENT_PATTERN":
    case "SELF_PAYMENT_ATTEMPT":
        return SecurityAuditRecord.ThreatLevel.MEDIUM;
    case "INSUFFICIENT_KYC_VERIFICATION":
    default:
        return SecurityAuditRecord.ThreatLevel.LOW;
}
```

**Line ~1053**: Replace switch expression in `determineResponseAction()`
```java
// Similar replacement needed
```

## Maven POM Configuration

Ensure your `pom.xml` has the correct Java version:

```xml
<properties>
    <java.version>17</java.version>
    <maven.compiler.source>17</maven.compiler.source>
    <maven.compiler.target>17</maven.compiler.target>
</properties>
```

## IntelliJ Settings Checklist

1. **File → Project Structure → Project**
   - SDK: 17
   - Language Level: 17 - Sealed types, always-strict floating-point semantics

2. **File → Project Structure → Modules**
   - Each module → Language Level: 17

3. **File → Settings → Build, Execution, Deployment → Compiler → Java Compiler**
   - Project bytecode version: 17
   - Per-module bytecode version: 17 for all modules

4. **Maven Settings** (if using Maven)
   - File → Settings → Build, Execution, Deployment → Build Tools → Maven
   - Maven home directory: (your maven installation)
   - User settings file: Check it points to correct settings.xml

## Common Compilation Errors and Fixes

### Error: "switch expressions are not supported in -source 17"
**Fix**: Replace all switch expressions with traditional switch statements

### Error: "records are not supported in -source 17"
**Fix**: Java 17 supports records, but ensure language level is set correctly

### Error: "pattern matching for switch is not supported"
**Fix**: This is a Java 21 feature, need to rewrite without pattern matching

## Recommended Action

**STRONGLY RECOMMEND**: Install Java 21 on your Mac for development to match the Linux deployment environment. This will prevent these compatibility issues.

### Install Java 21 on Mac:
```bash
# Option 1: Using SDKMAN
curl -s "https://get.sdkman.io" | bash
sdk install java 21.0.1-open

# Option 2: Using Homebrew
brew install openjdk@21

# Option 3: Download from Adoptium
# https://adoptium.net/temurin/releases/?version=21
```

After installing, configure IntelliJ to use Java 21 as described above.