# Fund Reservation System Migration Guide

## 🎯 Purpose

This guide walks through migrating from the vulnerable JVM-local fund reservation system to the production-grade distributed implementation.

---

## ⚠️ CRITICAL SECURITY FIX

**Vulnerability Fixed:** Double-spending attack due to in-memory (@Transient) fund reservations

**Impact:** Reservations were lost on service restart, allowing same funds to be spent twice

**Solution:** Persistent database storage + distributed Redis locking + idempotency

---

## 📊 Migration Overview

### Before (Vulnerable):
```java
@Deprecated
wallet.reserveFunds(amount, transactionId, idempotencyKey);
// ❌ JVM-local synchronization
// ❌ In-memory storage (@Transient)
// ❌ Lost on service restart
```

### After (Production-Grade):
```java
walletBalanceService.reserveFunds(FundReservationRequest.builder()
    .walletId(walletId)
    .transactionId(transactionId)
    .amount(amount)
    .idempotencyKey(idempotencyKey)
    .build());
// ✅ Distributed Redis locking
// ✅ Persistent database storage
// ✅ Survives service restart
```

---

## 🔍 Step 1: Identify All Callers

Run this command to find all usages of deprecated methods:

```bash
# Find all usages
grep -r "\.reserveFunds\|\.confirmReservation\|\.releaseReservation" \
  services/*/src/main/java/ \
  --include="*.java" \
  --exclude-dir=wallet-service

# Expected locations:
# - PaymentProcessingService
# - TransactionService
# - MerchantPaymentService
# - RecurringPaymentService
# - NFCPaymentService
```

---

## 🛠️ Step 2: Update Each Caller

### Example 1: Payment Processing Service

**Before:**
```java
@Service
public class PaymentProcessingService {

    private final WalletRepository walletRepository;

    public PaymentResponse processPayment(PaymentRequest request) {
        Wallet wallet = walletRepository.findById(request.getWalletId())
            .orElseThrow();

        // ❌ VULNERABLE - Deprecated method
        FundReservation reservation = wallet.reserveFunds(
            request.getAmount(),
            request.getTransactionId(),
            request.getIdempotencyKey()
        );

        try {
            // Process payment...
            wallet.confirmReservation(reservation.getId());
        } catch (Exception e) {
            wallet.releaseReservation(reservation.getId(), e.getMessage());
            throw e;
        }
    }
}
```

**After:**
```java
@Service
public class PaymentProcessingService {

    private final ProductionWalletBalanceService walletBalanceService;  // ✅ Inject new service

    public PaymentResponse processPayment(PaymentRequest request) {

        // ✅ SECURE - Production-grade reservation
        FundReservationRequest reservationRequest = FundReservationRequest.builder()
            .walletId(request.getWalletId())
            .transactionId(request.getTransactionId())
            .amount(request.getAmount())
            .idempotencyKey(request.getIdempotencyKey())
            .ttlMinutes(5)  // Auto-release after 5 minutes
            .reason("Payment processing")
            .build();

        FundReservationResponse reservation =
            walletBalanceService.reserveFunds(reservationRequest);

        try {
            // Process payment...
            walletBalanceService.confirmReservation(reservation.getReservationId());
        } catch (Exception e) {
            walletBalanceService.releaseReservation(
                reservation.getReservationId(),
                "Payment failed: " + e.getMessage()
            );
            throw e;
        }
    }
}
```

### Example 2: Transaction Service

**Before:**
```java
@Transactional
public void executeTransaction(TransactionRequest request) {
    Wallet sourceWallet = walletRepository.findById(request.getSourceWalletId())
        .orElseThrow();

    // ❌ VULNERABLE
    FundReservation reservation = sourceWallet.reserveFunds(
        request.getAmount(),
        request.getTransactionId(),
        UUID.randomUUID().toString()
    );

    // ... transaction logic
}
```

**After:**
```java
@Transactional
public void executeTransaction(TransactionRequest request) {

    // ✅ SECURE
    FundReservationRequest reservationRequest = FundReservationRequest.builder()
        .walletId(request.getSourceWalletId())
        .transactionId(request.getTransactionId())
        .amount(request.getAmount())
        .idempotencyKey(generateIdempotencyKey(request))
        .build();

    FundReservationResponse reservation =
        walletBalanceService.reserveFunds(reservationRequest);

    // ... transaction logic
}
```

---

## 🧪 Step 3: Testing Strategy

### Unit Tests

```java
@Test
void testPaymentWithNewReservationSystem() {
    // Given
    when(walletBalanceService.reserveFunds(any()))
        .thenReturn(createMockReservationResponse());

    // When
    PaymentResponse response = paymentService.processPayment(paymentRequest);

    // Then
    verify(walletBalanceService).reserveFunds(argThat(req ->
        req.getWalletId().equals(WALLET_ID) &&
        req.getAmount().equals(AMOUNT) &&
        req.getIdempotencyKey() != null
    ));
}
```

### Integration Tests

```java
@SpringBootTest
@Testcontainers
class PaymentIntegrationTest {

    @Container
    static RedisContainer redis = new RedisContainer();

    @Container
    static PostgreSQLContainer postgres = new PostgreSQLContainer();

    @Test
    void testConcurrentPayments() {
        // Test 100 concurrent payments to same wallet
        // Verify no double-spending occurs
    }
}
```

---

## 🚀 Step 4: Deployment Plan

### Phase 1: Canary Deployment (Week 1)

```yaml
# Deploy with feature flag OFF
deployment:
  replicas: 3
  env:
    - name: USE_NEW_RESERVATION_SYSTEM
      value: "false"  # Old system still active
```

1. Deploy new code (with deprecated methods still working)
2. Monitor for any compilation/runtime errors
3. Verify new service is healthy

### Phase 2: Gradual Rollout (Week 2)

```java
// Feature flag implementation
@Service
public class PaymentService {

    @Value("${features.new-reservation-system:false}")
    private boolean useNewReservationSystem;

    public PaymentResponse processPayment(PaymentRequest request) {
        if (useNewReservationSystem) {
            return processWithNewSystem(request);
        } else {
            return processWithOldSystem(request);  // Fallback
        }
    }
}
```

**Rollout Schedule:**
- Day 1-2: 1% of traffic
- Day 3-4: 10% of traffic
- Day 5-6: 50% of traffic
- Day 7: 100% of traffic

**Monitoring During Rollout:**
```bash
# Watch double-spending counter (should be ZERO)
watch 'curl -s localhost:9090/metrics | grep double_spending'

# Watch success rate (should be > 99%)
watch 'curl -s localhost:9090/metrics | grep reservation_success'
```

### Phase 3: Remove Deprecated Code (Week 3)

Once 100% traffic on new system for 7 days with zero issues:

```java
// Remove deprecated methods from Wallet.java
// This will cause compilation errors if any caller still using old methods

// Delete these methods:
@Deprecated
public FundReservation reserveFunds(...) { /* DELETE */ }

@Deprecated
public void confirmReservation(...) { /* DELETE */ }

@Deprecated
public void releaseReservation(...) { /* DELETE */ }
```

---

## 📈 Step 5: Monitoring & Validation

### Key Metrics to Watch

```promql
# Success rate (should be > 99%)
rate(wallet_reservation_success_total[5m]) /
rate(wallet_reservation_total[5m])

# Double-spending attempts (should be ZERO)
rate(wallet_double_spending_prevented_total[5m])

# Latency P95 (should be < 100ms)
histogram_quantile(0.95, wallet_reservation_duration_seconds_bucket)

# Lock acquisition time (should be < 50ms)
histogram_quantile(0.95, wallet_lock_acquisition_seconds_bucket)
```

### Grafana Dashboard

Import this dashboard JSON:

```json
{
  "title": "Wallet Fund Reservation Monitoring",
  "panels": [
    {
      "title": "Reservation Success Rate",
      "targets": [
        {
          "expr": "rate(wallet_reservation_success_total[5m]) / rate(wallet_reservation_total[5m])"
        }
      ]
    },
    {
      "title": "Double-Spending Prevention",
      "targets": [
        {
          "expr": "rate(wallet_double_spending_prevented_total[5m])"
        }
      ]
    }
  ]
}
```

---

## 🔒 Security Validation Checklist

- [ ] No `@Transient` fields for fund reservations
- [ ] All reservations persisted to database
- [ ] Distributed Redis locking in place
- [ ] Idempotency keys enforced
- [ ] Lock timeout handling implemented
- [ ] Optimistic locking with `@Version` field
- [ ] Metrics recording double-spending attempts
- [ ] Integration tests passing (12/12)
- [ ] Load test passed (1000 concurrent requests)
- [ ] Service restart test passed
- [ ] No deprecated methods in use

---

## 🛑 Rollback Plan

If issues occur during deployment:

### Immediate Rollback (< 5 minutes)

```bash
# Flip feature flag OFF
kubectl set env deployment/wallet-service \
  USE_NEW_RESERVATION_SYSTEM=false

# Verify rollback
kubectl rollout status deployment/wallet-service
```

### Partial Rollback

```java
// Reduce percentage via feature flag
@Value("${features.new-reservation-rollout-percentage:0}")
private int rolloutPercentage;  // Set to 0 for full rollback
```

---

## 📞 Support & Escalation

### During Migration

**Primary Contact:** Platform Team (Slack: #platform-team)
**Escalation:** CTO (for critical issues only)

### Post-Migration

**Monitoring:** DevOps Team
**Incidents:** PagerDuty rotation

---

## 🎓 Training Materials

### For Developers

**Required Reading:**
1. This migration guide
2. `ProductionWalletBalanceService` JavaDoc
3. Integration test examples

**Required Training:**
- 1-hour workshop on new reservation system
- Code review of their migration changes

### For Ops Team

**Required Knowledge:**
1. How to interpret Prometheus alerts
2. How to check Redis cluster health
3. How to manually release stuck reservations (runbook)

---

## 🏁 Success Criteria

Migration is considered successful when:

✅ 100% of traffic using new system for 7+ days
✅ Zero double-spending attempts detected
✅ Success rate > 99.9%
✅ P95 latency < 100ms
✅ No critical incidents
✅ All deprecated code removed
✅ Team trained and confident

---

## 📚 Additional Resources

- **Technical Design:** [Fund Reservation Architecture.pdf]
- **Security Review:** [Security Audit Report.pdf]
- **Performance Test Results:** [Load Test Results.pdf]
- **Runbooks:** https://wiki.example.com/runbooks/wallet-service

---

## ✅ Migration Checklist Template

Use this for each service that needs migration:

```markdown
### Service: payment-service

- [ ] Identified all usages of deprecated methods
- [ ] Created feature flag configuration
- [ ] Implemented new reservation calls
- [ ] Added error handling
- [ ] Wrote unit tests
- [ ] Wrote integration tests
- [ ] Code review completed
- [ ] Deployed to staging
- [ ] Staging validation passed
- [ ] Deployed to canary (1%)
- [ ] Canary metrics healthy (24 hours)
- [ ] Rolled out to 10%
- [ ] Rolled out to 50%
- [ ] Rolled out to 100%
- [ ] Removed feature flag
- [ ] Removed deprecated method calls
- [ ] Documentation updated
```

---

**Last Updated:** 2025-11-01
**Version:** 2.0
**Author:** Waqiti Engineering Team
