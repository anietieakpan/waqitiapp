# Common Module Compilation Fixes Summary

**Date:** 2025-11-19
**Status:** In Progress - 200 errors remaining (from initial 250+)
**Java Version:** Java 21 (upgraded from 17 to fix Lombok issues)

## Fixes Successfully Applied

### 1. Currency/Money Type System (8 files fixed)
```bash
# Changed Currency.getInstance("NGN") → "NGN"
# Changed .getCurrency() → .getCurrencyCode()
# Fixed in: AccountBalanceService.java
```

### 2. Lombok/Builder Issues (10 files fixed)
- Removed `@Override` from static methods in 7 event classes:
  - CustomerOffboardingEvent.java
  - CustomerOnboardingEvent.java
  - CustomerReactivationEvent.java
  - CustomerSegmentationEvent.java
  - CustomerTierChangeEvent.java
  - MultiCurrencyAccountEvent.java
  - NPSSurveyEvent.java

- Added `@NoArgsConstructor` to CompensationTransaction.java
- Changed `private` → `protected` for FinancialEvent fields (timestamp, userId)

### 3. CompensationService Entity Conversions (6 locations fixed)
```java
// Added toDto() and toDtoList() conversions:
// Line 376: return toDtoList(compensationRepository.findByStatus(...))
// Line 384: return toDtoList(compensationRepository.findByStatus(...))
// Line 393: return toDto(compensationRepository.findById(...))
```

### 4. Fraud Service Package Refactoring (30-40 errors fixed)
```bash
# Batch replacements across all fraud/*.java files:
sed 's/com.example.common.fraud.dto.AlertLevel/com.example.common.fraud.alert.AlertLevel/g'
sed 's/com.example.common.fraud.dto.Location/com.example.common.fraud.model.Location/g'
sed 's/com.example.common.fraud.dto.UserRiskProfile/com.example.common.fraud.profiling.UserRiskProfile/g'
```

### 5. Missing Imports Added
- FraudAlertService.java:
  - import com.example.common.fraud.dto.DashboardAlert
  - import com.example.common.fraud.dto.AlertAcknowledgmentEvent
  - import com.example.common.fraud.dto.AlertResolutionEvent

### 6. BigDecimal Imports
- UserApplicationService.java: Added `import java.math.BigDecimal`
- Fixed Money.ngn(0) → Money.ngn(BigDecimal.ZERO)

### 7. Event System Fixes
- UserEvent.java:203 - Changed `.getUserId()` → `.userId`
- Fixed timestamp access in event classes

### 8. AlertLevel Enum Cleanup
- Removed invalid enum cases: MINIMAL, UNKNOWN (don't exist in alert.AlertLevel)

## Remaining Errors (~40 Unique Patterns, 200 Total with Duplicates)

### Category 1: Missing Builder Methods (~10 errors)
**Files:** FraudAlertService, FraudMapper, ExternalRiskDataProvider, FraudMonitoringStatistics

**Issue:** Builder classes missing methods:
- `.riskIndicators(Map<String, Object>)` - ExternalRiskDataProvider:27,45
- `.alertId(String)` - FraudAlert.builder():377
- `.fraudProbability(double)` - FraudAlertEvent.builder():492
- `.fraudProbability(FraudScore)` - FraudAlert.builder():169
- `.truePositives(long)` - FraudMonitoringStatistics.builder():220

**Fix Strategy:**
```java
// Option 1: Add @Builder.Default to fields
@Builder.Default
private Map<String, Object> riskIndicators = new HashMap<>();

// Option 2: Check if field names match builder expectations
// Option 3: Use builder(toBuilder = true) if needed
```

### Category 2: Missing Methods/Fields (~15 errors)
**Files:** TransactionEvent, UserRiskProfile, FraudAlert, RealTimeFraudMonitoringService

**Missing methods:**
- TransactionEvent: `.getMerchantName()`, `.getMerchantCategory()` (lines 104-105, 123)
- UserRiskProfile: `.getAccountAge()`, `.getRiskLevel()` (lines 143, 154)
- FraudAlert: `.getFraudProbability()`, `.getDetectedAt()` (lines 183, 185)
- RealTimeFraudMonitoringService: `.getFraudScoringEngine()` (line 300)
- DLQRecord: `.getErrorType()` (line 247)
- ManualReviewCase: `.getErrorType()` (lines 459, 477)
- RejectCaseRequest: `.getReason()` (line 170)
- ResolveCaseRequest: `.setAction()`, `.setNotes()` (lines 199-200)

**Fix Strategy:** Add missing getters/setters or change field access to direct field references

### Category 3: Type Conversion Issues (~15 errors)

**A. BigDecimal ↔ double conversions**
```java
// FraudMapper.java:141 - incompatible types: double cannot be converted to BigDecimal
// FraudMapper.java:152 - incompatible types: BigDecimal cannot be converted to double
// TransactionAnalysisService.java:227,232 - bad return type in method reference (BigDecimal vs double)

// Fix: Use BigDecimal.doubleValue() or BigDecimal.valueOf()
```

**B. AlertLevel conversions**
```java
// FraudAlert.AlertLevel (nested class) vs AlertLevel (standalone)
// FraudNotificationAdapter:139,160 - incompatible types
// FraudAlertService:93,156 - incompatible types

// Fix: Need mapper between nested and standalone AlertLevel enums
```

**C. FraudRuleViolation List**
```java
// FraudMapper.java:201
// List<com.example.common.fraud.rules.FraudRuleViolation>
// cannot be converted to
// List<com.example.common.fraud.dto.FraudRuleViolation>

// Fix: Add stream().map(mapper::convert).collect(toList())
```

**D. Location conversions**
```java
// RealTimeFraudMonitoringService:486 - String cannot be converted to Location
// RealTimeFraudMonitoringService:543 - Location cannot be converted to String

// Fix: Use Location.toString() or Location.parse(String)
```

### Category 4: Method Signature Mismatches (~10 errors)

```java
// RealTimeFraudMonitoringService:520 - invalid method reference
// Method getAmount() in TransactionSummary cannot be applied to given types

// FraudMapper:109,142,153 - no suitable method found for toModel/toDto
// Wrong parameter types being passed

// TransactionAnalysisService:249
// Map<TransactionChannel,Long> cannot be converted to Map<Object,Long>
```

### Category 5: Conditional Expression Issues
```java
// RealTimeFraudMonitoringService:187
// bad type in conditional expression (likely BigDecimal vs double in ternary)

// Fix: Ensure both branches of ternary return same type
```

### Category 6: Missing Classes/Symbols
```java
// DashboardAlert, AlertAcknowledgmentEvent, AlertResolutionEvent
// Status: FIXED (imports added)

// ResolutionAction.REPROCESS (line 398)
// ResolveCaseRequest.builder() (line 420)
// DlqHealthMetrics constructor signature mismatch (line 553)
// SystemAlertsDlqConsumer missing getConsumerName() override (line 45)
```

## Next Steps to Complete Fixes

### Priority 1: Add Missing Methods (30 min)
1. Add getMerchantName/Category to TransactionEvent
2. Add getAccountAge/getRiskLevel to UserRiskProfile
3. Add getFraudProbability/getDetectedAt to FraudAlert
4. Add missing DLQ methods

### Priority 2: Fix Builder Issues (20 min)
1. Add @Builder.Default to fields missing builder methods
2. Verify builder field name matches

### Priority 3: Type Conversions (45 min)
1. Add BigDecimal ↔ double conversion helpers
2. Create AlertLevel mapper (nested vs standalone)
3. Add FraudRuleViolation list converter
4. Fix Location String conversions

### Priority 4: Method Signatures (30 min)
1. Fix method reference types
2. Correct toModel/toDto parameter types
3. Fix generic type parameters

### Priority 5: Final Compilation (10 min)
1. Run full compile
2. Fix any remaining cascading errors
3. Verify build success

## Estimated Time to Completion: 2-3 hours

## Commands Used

```bash
# Switch to Java 21
export JAVA_HOME=/usr/local/Cellar/openjdk@21/21.0.8/libexec/openjdk.jdk/Contents/Home

# Compile and count errors
mvn compile -DskipTests 2>&1 | grep "error:" | wc -l

# Get unique error patterns
mvn compile -DskipTests 2>&1 | grep "error:" | sort -u | head -40
```
