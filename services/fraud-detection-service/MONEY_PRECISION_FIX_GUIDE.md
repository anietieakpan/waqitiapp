# MONEY PRECISION FIX GUIDE

**Status:** 1/20 files fixed
**Priority:** HIGH - Prevents financial calculation errors
**Created:** October 16, 2025

---

## PROBLEM

Using `double` or `float` for monetary values causes precision errors:

```java
// WRONG - Precision loss
double amount = 0.1 + 0.2;  // = 0.30000000000000004 (not 0.3!)

BigDecimal bd1 = new BigDecimal("10.00");
BigDecimal bd2 = new BigDecimal("3.00");
double result = bd1.doubleValue() / bd2.doubleValue();  // Precision lost!
```

**Impact:**
- Incorrect fraud risk calculations
- ML model receives imprecise features
- Financial reporting errors
- Regulatory compliance issues

---

## SOLUTION

Use `MoneyUtils` for all money-to-float conversions:

```java
// CORRECT - Precision preserved
BigDecimal amount1 = new BigDecimal("0.10");
BigDecimal amount2 = new BigDecimal("0.20");
BigDecimal total = MoneyUtils.add(amount1, amount2);  // Exact: 0.30

// For ML features
float mlFeature = MoneyUtils.toMLFeature(total);  // Safe conversion
```

---

## IMPLEMENTATION PATTERN

### Step 1: Add MoneyUtils Import

```java
import com.waqiti.frauddetection.util.MoneyUtils;
```

### Step 2: Replace .doubleValue() Calls

**BEFORE:**
```java
BigDecimal amount = transaction.getAmount();
features.put("amount", amount.doubleValue());
features.put("amount_log", Math.log10(amount.doubleValue() + 1));
features.put("amount_sqrt", Math.sqrt(amount.doubleValue()));
```

**AFTER:**
```java
BigDecimal amount = transaction.getAmount();
features.put("amount", (double) MoneyUtils.toMLFeature(amount));
features.put("amount_log", (double) MoneyUtils.toMLFeatureLog(amount));
features.put("amount_sqrt", Math.sqrt(MoneyUtils.toMLFeature(amount)));
```

### Step 3: Replace Direct Arithmetic

**BEFORE:**
```java
double total = amount1.doubleValue() + amount2.doubleValue();
double avg = total / count;
```

**AFTER:**
```java
BigDecimal total = MoneyUtils.add(amount1, amount2);
BigDecimal avg = MoneyUtils.calculateAverage(total, count);

// For ML features
float totalFeature = MoneyUtils.toMLFeature(total);
```

---

## MONEYUTILS API REFERENCE

### ML Feature Conversion

```java
// Basic conversion
float mlFeature = MoneyUtils.toMLFeature(amount);

// Log transformation (for ML)
float logFeature = MoneyUtils.toMLFeatureLog(amount);

// Normalized (0-1 range)
float normalized = MoneyUtils.toMLFeatureNormalized(amount, maxAmount);
```

### Safe Arithmetic

```java
// Addition
BigDecimal sum = MoneyUtils.add(amount1, amount2);

// Subtraction
BigDecimal diff = MoneyUtils.subtract(amount1, amount2);

// Multiplication
BigDecimal product = MoneyUtils.multiply(amount1, amount2);

// Division
BigDecimal quotient = MoneyUtils.divide(amount1, amount2);
```

### Calculations

```java
// Percentage
BigDecimal percent = MoneyUtils.calculatePercentage(part, total);

// Ratio (0.0 - 1.0)
BigDecimal ratio = MoneyUtils.calculateRatio(part, total);

// Average
BigDecimal avg = MoneyUtils.calculateAverage(totalAmount, count);
```

### Validation

```java
// Check if zero
boolean isZero = MoneyUtils.isZero(amount);

// Check if positive
boolean isPositive = MoneyUtils.isPositive(amount);

// Validate range
boolean isValid = MoneyUtils.isValidRange(amount, min, max);
```

---

## FILES REQUIRING FIX (20 TOTAL)

### ✅ FIXED (1)

1. ✅ **FeatureEngineeringService.java** - DONE
   - Lines: 161-163, 409-410, 438-455, 562

### ⏳ TO FIX (19)

#### High Priority - Services (8 files)

2. ⏳ **FraudDetectionService.java**
   - Search: `\.doubleValue\(\)` near "amount"
   - Pattern: Amount calculations, risk scoring

3. ⏳ **RiskScoringService.java**
   - Search: `\.doubleValue\(\)` near "score\|amount"
   - Pattern: Risk calculations, thresholds

4. ⏳ **GraphBasedFraudDetectionService.java**
   - Search: `\.doubleValue\(\)` near "amount\|value"
   - Pattern: Network value calculations

5. ⏳ **SecureVelocityCheckService.java**
   - Search: `\.doubleValue\(\)` near "amount\|limit"
   - Pattern: Velocity amount calculations

6. ⏳ **FraudAnalysisService.java**
   - Search: `\.doubleValue\(\)` near "amount"
   - Pattern: Analysis calculations

7. ⏳ **DeviceRiskScoringService.java**
   - Search: `\.doubleValue\(\)` near "amount\|score"
   - Pattern: Device-based risk calculations

8. ⏳ **FraudRulesEngine.java**
   - Search: `\.doubleValue\(\)` near "amount\|threshold"
   - Pattern: Rule threshold comparisons

9. ⏳ **FraudMLModel.java** (existing)
   - Search: `\.doubleValue\(\)` near "amount"
   - Pattern: ML feature extraction

#### Medium Priority - Consumers (10 files)

10. ⏳ **FraudAlertsConsumer.java**
11. ⏳ **FraudAlertConsumer.java**
12. ⏳ **RiskAssessmentEventsConsumer.java**
13. ⏳ **VelocityMonitoringConsumer.java**
14. ⏳ **FraudActivityLogsDlqConsumer.java**
15. ⏳ **MerchantRiskScoringConsumer.java**
16. ⏳ **PatternAnalysisConsumer.java**
17. ⏳ **CardFraudDetectionConsumer.java**
18. ⏳ **RiskScoresEventsConsumer.java**
19. ⏳ **VelocityProfileService.java** (our own - may have missed some)

#### Lower Priority - Utilities (1 file)

20. ⏳ **MoneyUtils.java** itself
    - Check internal usage is consistent

---

## VERIFICATION SCRIPT

Run this command to find remaining issues:

```bash
# Find all .doubleValue() calls related to money
grep -rn "\.doubleValue()" \
  --include="*.java" \
  --exclude="MoneyUtils.java" \
  services/fraud-detection-service/src/main/java/ \
  | grep -i "amount\|price\|total\|balance\|limit\|value"

# Find all .floatValue() calls related to money
grep -rn "\.floatValue()" \
  --include="*.java" \
  --exclude="MoneyUtils.java" \
  services/fraud-detection-service/src/main/java/ \
  | grep -i "amount\|price\|total\|balance"

# Find double/float variable declarations for money
grep -rn "double.*amount\|float.*amount\|double.*price\|float.*price" \
  --include="*.java" \
  services/fraud-detection-service/src/main/java/
```

---

## TESTING

### Unit Test Template

```java
@Test
void testMoneyPrecisionInFeatureExtraction() {
    // Given - Amount that causes precision issues with double
    BigDecimal amount = new BigDecimal("0.1")
        .add(new BigDecimal("0.2"));  // Should be exactly 0.3

    // When - Extract features
    Map<String, Object> features = Map.of("amount", amount);
    float mlFeature = MoneyUtils.toMLFeature(amount);

    // Then - Precision is preserved
    assertEquals(0.3f, mlFeature, 0.0001f);

    // Verify no precision loss
    BigDecimal reconstructed = MoneyUtils.fromDouble(mlFeature);
    assertTrue(amount.subtract(reconstructed).abs()
        .compareTo(new BigDecimal("0.0001")) < 0);
}
```

### Integration Test

```java
@Test
void testEndToEndFraudDetectionWithPreciseMoney() {
    // Given
    FraudDetectionRequest request = FraudDetectionRequest.builder()
        .amount(new BigDecimal("1234.56"))  // Precise amount
        .userId(testUserId)
        .build();

    // When
    FraudDetectionResult result = fraudDetectionService.analyze(request);

    // Then - All calculations use BigDecimal precision
    assertNotNull(result);
    assertTrue(result.getRiskScore() >= 0.0 && result.getRiskScore() <= 1.0);

    // Verify features used precise amounts
    Map<String, Object> features = result.getFeatures();
    float amountFeature = (float) features.get("amount");
    assertEquals(1234.56f, amountFeature, 0.01f);
}
```

---

## COMMON PATTERNS TO FIX

### Pattern 1: Feature Extraction

**BEFORE:**
```java
features.put("amount", amount.doubleValue());
features.put("amount_log", Math.log10(amount.doubleValue() + 1));
```

**AFTER:**
```java
features.put("amount", (double) MoneyUtils.toMLFeature(amount));
features.put("amount_log", (double) MoneyUtils.toMLFeatureLog(amount));
```

### Pattern 2: Threshold Comparison

**BEFORE:**
```java
if (amount.doubleValue() > HIGH_VALUE_THRESHOLD) {
    // High value transaction
}
```

**AFTER:**
```java
BigDecimal threshold = new BigDecimal(HIGH_VALUE_THRESHOLD);
if (amount.compareTo(threshold) > 0) {
    // High value transaction
}
```

### Pattern 3: Average Calculation

**BEFORE:**
```java
double avgAmount = amounts.stream()
    .mapToDouble(BigDecimal::doubleValue)
    .average()
    .orElse(0.0);
```

**AFTER:**
```java
double avgAmount = amounts.stream()
    .mapToDouble(amt -> MoneyUtils.toMLFeature(amt))
    .average()
    .orElse(0.0);
```

### Pattern 4: Arithmetic Operations

**BEFORE:**
```java
double total = amount1.doubleValue() + amount2.doubleValue();
double diff = amount1.doubleValue() - amount2.doubleValue();
```

**AFTER:**
```java
BigDecimal total = MoneyUtils.add(amount1, amount2);
BigDecimal diff = MoneyUtils.subtract(amount1, amount2);

// For ML features only
float totalFeature = MoneyUtils.toMLFeature(total);
```

---

## ROLLOUT PLAN

### Phase 1: Services (Day 1)
Fix 8 service files (high priority)
- FraudDetectionService
- RiskScoringService
- GraphBasedFraudDetectionService
- SecureVelocityCheckService
- FraudAnalysisService
- DeviceRiskScoringService
- FraudRulesEngine
- FraudMLModel

### Phase 2: Consumers (Day 2)
Fix 10 consumer files (medium priority)

### Phase 3: Verification (Day 2)
- Run verification script
- Execute unit tests
- Integration testing
- Performance testing (ensure no regression)

---

## CHECKLIST

For each file:

- [ ] Add `import com.waqiti.frauddetection.util.MoneyUtils;`
- [ ] Replace `.doubleValue()` with `MoneyUtils.toMLFeature()`
- [ ] Replace `.floatValue()` with `MoneyUtils.toMLFeature()`
- [ ] Replace arithmetic with `MoneyUtils.add/subtract/multiply/divide()`
- [ ] Update log transformations to use `MoneyUtils.toMLFeatureLog()`
- [ ] Add unit test for precision
- [ ] Verify no compilation errors
- [ ] Run existing tests (should pass)
- [ ] Code review

---

## SUCCESS CRITERIA

✅ All 20 files updated
✅ No `.doubleValue()` calls for money (except in MoneyUtils itself)
✅ All unit tests pass
✅ Integration tests pass
✅ No precision-related bugs in production
✅ Financial calculations are exact
✅ ML features maintain acceptable precision

---

**Reference Implementation:** `FeatureEngineeringService.java` (lines 8, 161-163, 409-410, 438-455, 562)

**Estimated Time:** 2-3 hours for all 19 remaining files

**Priority:** HIGH - Financial precision is critical

Last Updated: October 16, 2025
