# IDOR Protection System - Production Implementation

## Overview

This system provides **comprehensive IDOR (Insecure Direct Object Reference) protection** for the Waqiti fintech platform, preventing unauthorized access to user resources and financial data.

**OWASP Compliance**: OWASP Top 10 A01:2021 - Broken Access Control
**Security Level**: CRITICAL
**Implementation**: Production-ready, enterprise-grade

---

## What is IDOR?

**IDOR (Insecure Direct Object Reference)** is a security vulnerability where an application exposes a reference to an internal implementation object (like a database key) without proper authorization checks.

### IDOR Attack Example

```http
# Attacker scenario:
# User A (ID: user-123) tries to access User B's wallet (ID: wallet-456)

GET /api/v1/wallets/wallet-456/balance
Authorization: Bearer <user-123-jwt-token>

# WITHOUT IDOR PROTECTION:
# ❌ Request succeeds, User A sees User B's balance

# WITH IDOR PROTECTION:
# ✅ Request fails with 403 Forbidden
# ✅ Security violation logged
# ✅ User A's account flagged for suspicious activity
```

---

## Architecture

### Defense-in-Depth Approach

Our IDOR protection uses **multiple layers of security**:

```
┌─────────────────────────────────────────────────────────────┐
│ Layer 1: JWT Authentication                                 │
│ - Verifies user identity                                    │
└─────────────────────────────────────────────────────────────┘
                          ↓
┌─────────────────────────────────────────────────────────────┐
│ Layer 2: Spring Security @PreAuthorize                      │
│ - Role-based access control                                 │
│ - Custom validators (@walletOwnershipValidator)             │
└─────────────────────────────────────────────────────────────┘
                          ↓
┌─────────────────────────────────────────────────────────────┐
│ Layer 3: @ValidateOwnership (NEW - THIS SYSTEM)            │
│ - AOP-based ownership validation                            │
│ - Database ownership lookup                                  │
│ - Fail-fast before method execution                          │
└─────────────────────────────────────────────────────────────┘
                          ↓
┌─────────────────────────────────────────────────────────────┐
│ Layer 4: Business Logic Validation                          │
│ - Service-level authorization checks                         │
└─────────────────────────────────────────────────────────────┘
```

### Component Diagram

```
┌──────────────────────────────────────────────────────────────┐
│                    @ValidateOwnership                        │
│                    (Annotation)                              │
└───────────────────────┬──────────────────────────────────────┘
                        │ Applied to controller methods
                        ↓
┌──────────────────────────────────────────────────────────────┐
│             OwnershipValidationAspect                        │
│             (AOP Interceptor)                                │
│  - Intercepts @ValidateOwnership methods                     │
│  - Extracts JWT user ID from SecurityContext                 │
│  - Extracts resource ID from method parameters               │
│  - Calls OwnershipValidator                                  │
└───────────────────────┬──────────────────────────────────────┘
                        │
                        ↓
┌──────────────────────────────────────────────────────────────┐
│                OwnershipValidator                            │
│                (Business Logic)                              │
│  - Validates ownership via database lookup                   │
│  - Checks admin bypass                                       │
│  - Logs security violations                                  │
└───────────────────────┬──────────────────────────────────────┘
                        │
          ┌─────────────┴─────────────┐
          ↓                           ↓
┌─────────────────────┐    ┌──────────────────────────┐
│ResourceOwnership    │    │  SecurityAuditLogger     │
│Repository           │    │                          │
│ - SQL queries       │    │  - IDOR attempt logging  │
│ - 12 resource types │    │  - Metrics tracking      │
│ - Fast lookups      │    │  - Alert triggers        │
└─────────────────────┘    └──────────────────────────┘
```

---

## Usage Guide

### 1. Basic Ownership Validation

Protect a GET endpoint to ensure users can only access their own resources:

```java
@GetMapping("/wallets/{walletId}")
@ValidateOwnership(resourceType = "WALLET", resourceIdParam = "walletId")
public WalletResponse getWallet(@PathVariable UUID walletId) {
    // This method ONLY executes if authenticated user owns the wallet
    return walletService.getWallet(walletId);
}
```

### 2. Ownership with Permission Check

For write operations, require both ownership AND specific permission:

```java
@DeleteMapping("/wallets/{walletId}")
@ValidateOwnership(
    resourceType = "WALLET",
    resourceIdParam = "walletId",
    requiredPermission = "DELETE"
)
public void deleteWallet(@PathVariable UUID walletId) {
    // Requires BOTH ownership AND delete permission
    walletService.deleteWallet(walletId);
}
```

### 3. Admin Bypass

Allow admin users to bypass ownership checks:

```java
@GetMapping("/users/{userId}/transactions")
@ValidateOwnership(
    resourceType = "USER",
    resourceIdParam = "userId",
    allowAdmin = true  // Admins can access any user's transactions
)
public List<Transaction> getUserTransactions(@PathVariable UUID userId) {
    return transactionService.getTransactionsByUser(userId);
}
```

### 4. Bulk Operations

Validate ownership for multiple resources:

```java
@PostMapping("/wallets/bulk-transfer")
public void bulkTransfer(@RequestBody BulkTransferRequest request) {
    UUID authenticatedUserId = securityContext.getCurrentUserId();

    // Validate ownership of all wallets in bulk
    ownershipValidator.validateBulkOwnership(
        authenticatedUserId,
        "WALLET",
        request.getWalletIds()
    );

    walletService.executeBulkTransfer(request);
}
```

---

## Supported Resource Types

The system supports **12 resource types** with optimized SQL queries:

| Resource Type         | Table                 | Ownership Column  | Notes                          |
|-----------------------|-----------------------|-------------------|--------------------------------|
| `WALLET`              | `wallets`             | `user_id`         | Direct ownership               |
| `ACCOUNT`             | `accounts`            | `user_id`         | Direct ownership               |
| `TRANSACTION`         | `transactions`        | `user_id`         | Direct ownership               |
| `PAYMENT`             | `payments`            | `user_id`         | Direct ownership               |
| `INVESTMENT_ACCOUNT`  | `investment_accounts` | `customer_id`     | Direct ownership               |
| `CARD`                | `tokenized_cards`     | `user_id`         | Direct ownership               |
| `BANK_ACCOUNT`        | `bank_accounts`       | `user_id`         | Direct ownership               |
| `BENEFICIARY`         | `beneficiaries`       | `user_id`         | Direct ownership               |
| `TRANSFER`            | `transfers`           | `user_id`         | Direct ownership               |
| `SCHEDULED_PAYMENT`   | `scheduled_payments`  | `user_id`         | Direct ownership               |
| `INVESTMENT_ORDER`    | `investment_orders`   | `ia.customer_id`  | Join through investment_account|
| `PORTFOLIO`           | `portfolios`          | `ia.customer_id`  | Join through investment_account|

### Adding New Resource Types

To add a new resource type:

1. **Update `ResourceOwnershipRepository.buildOwnershipQuery()`**:

```java
case "NEW_RESOURCE":
    return "SELECT COUNT(*) FROM new_resources WHERE user_id = ?::uuid AND id = ?::uuid";
```

2. **Add database index** for fast lookups:

```sql
CREATE INDEX idx_new_resources_user_id ON new_resources(user_id);
CREATE INDEX idx_new_resources_id ON new_resources(id);
```

3. **Update documentation** in `ValidateOwnership.java` annotation

---

## Security Features

### 1. Fail-Fast Execution

Ownership validation happens **BEFORE** method execution using AOP `@Around` advice:

```
HTTP Request → JWT Auth → @ValidateOwnership → Method Execution
                              ↓ (if fails)
                         403 Forbidden
                         Security Log
                         Metrics Alert
```

### 2. Audit Logging

All IDOR attempts are logged with full context:

```java
log.error("IDOR_ATTACK_ATTEMPT: User {} attempted to access {} {} without ownership",
    userId, resourceType, resourceId);
```

**Logged Information**:
- User ID (from JWT)
- Resource Type (WALLET, PAYMENT, etc.)
- Resource ID (attempted access)
- Timestamp
- Request context (IP, device ID, etc.)
- Severity: CRITICAL

### 3. Metrics and Monitoring

Security violations increment metrics:

```java
securityViolationCounter.increment();
```

**Prometheus Metrics**:
- `security.violations.total` - Total IDOR attempts
- `security.events.total` - All security events
- `authentication.failures.total` - Auth failures

### 4. Admin Bypass Logging

Admin bypasses are logged separately for compliance:

```java
log.info("ADMIN_OWNERSHIP_BYPASS: Admin {} bypassed ownership check for {} {}",
    adminUserId, resourceType, resourceId);
```

---

## Database Schema Requirements

### Required Columns

All protected tables **MUST** have:

1. **Ownership column**: `user_id` or `customer_id` or `owner_id`
2. **Primary key**: `id` (UUID type)

### Required Indexes

For optimal performance, create these indexes:

```sql
-- Composite index for ownership queries
CREATE INDEX idx_wallets_user_id_id ON wallets(user_id, id);
CREATE INDEX idx_payments_user_id_id ON payments(user_id, id);
CREATE INDEX idx_transactions_user_id_id ON transactions(user_id, id);
-- ... repeat for all resource tables
```

### Foreign Key Constraints

Ensure referential integrity:

```sql
ALTER TABLE wallets
ADD CONSTRAINT fk_wallets_user_id
FOREIGN KEY (user_id) REFERENCES users(id) ON DELETE CASCADE;
```

---

## Performance Considerations

### 1. Query Optimization

All ownership queries use:
- **Indexed columns** for fast lookups
- **COUNT(*) queries** (returns 0 or 1, very fast)
- **PostgreSQL UUID casting** for type safety

**Example Query**:
```sql
SELECT COUNT(*)
FROM wallets
WHERE user_id = ?::uuid AND id = ?::uuid
-- Uses composite index: idx_wallets_user_id_id
-- Average execution time: < 1ms
```

### 2. Caching Strategy

For frequently accessed resources, consider caching ownership:

```java
@Cacheable(value = "wallet-ownership", key = "#userId + ':' + #walletId")
public boolean isOwner(UUID userId, String resourceType, UUID resourceId) {
    // Cached for 5 minutes
}
```

### 3. Bulk Validation Optimization

For bulk operations, use batch queries:

```sql
SELECT id
FROM wallets
WHERE user_id = ? AND id IN (?, ?, ?, ...)
-- More efficient than N individual queries
```

---

## Error Handling

### OwnershipValidationException

Thrown when ownership validation fails:

```java
@ResponseStatus(value = HttpStatus.FORBIDDEN, reason = "Access to this resource is forbidden")
public class OwnershipValidationException extends RuntimeException {
    // Automatically maps to 403 Forbidden
}
```

**HTTP Response**:
```http
HTTP/1.1 403 Forbidden
Content-Type: application/json

{
  "timestamp": "2025-10-02T10:30:00.123Z",
  "status": 403,
  "error": "Forbidden",
  "message": "User 123e4567-e89b-12d3-a456-426614174000 does not have access to WALLET abc-456",
  "path": "/api/v1/wallets/abc-456/balance"
}
```

### Global Exception Handler

```java
@ExceptionHandler(OwnershipValidationException.class)
public ResponseEntity<ErrorResponse> handleOwnershipViolation(
    OwnershipValidationException e) {

    // Log security violation
    securityAuditLogger.logIDORAttempt(...);

    // Return generic 403 (don't expose details)
    return ResponseEntity.status(HttpStatus.FORBIDDEN)
        .body(new ErrorResponse("Access denied"));
}
```

---

## Testing

### Unit Tests

```java
@Test
void testOwnershipValidation_ValidOwner_Success() {
    UUID userId = UUID.randomUUID();
    UUID walletId = UUID.randomUUID();

    when(ownershipRepository.isOwner(userId, "WALLET", walletId))
        .thenReturn(true);

    // Should not throw exception
    ownershipValidator.validateOwnership(userId, "WALLET", walletId);

    verify(ownershipRepository).isOwner(userId, "WALLET", walletId);
}

@Test
void testOwnershipValidation_InvalidOwner_ThrowsException() {
    UUID userId = UUID.randomUUID();
    UUID walletId = UUID.randomUUID();

    when(ownershipRepository.isOwner(userId, "WALLET", walletId))
        .thenReturn(false);

    assertThrows(OwnershipValidationException.class, () -> {
        ownershipValidator.validateOwnership(userId, "WALLET", walletId);
    });

    // Verify security audit log was called
    verify(securityAuditLogger).logIDORAttempt(userId, "WALLET", walletId, any());
}
```

### Integration Tests

```java
@Test
@WithMockUser(username = "user-123")
void testGetWallet_NotOwner_Returns403() throws Exception {
    UUID otherUserWalletId = UUID.fromString("wallet-456");

    mockMvc.perform(get("/api/v1/wallets/{walletId}/balance", otherUserWalletId)
            .header("X-Request-ID", "test-request-123"))
        .andExpect(status().isForbidden())
        .andExpect(jsonPath("$.error").value("Forbidden"));
}

@Test
@WithMockUser(username = "user-123")
void testGetWallet_IsOwner_Returns200() throws Exception {
    UUID userWalletId = UUID.fromString("wallet-123");

    mockMvc.perform(get("/api/v1/wallets/{walletId}/balance", userWalletId)
            .header("X-Request-ID", "test-request-123"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.data.walletId").value("wallet-123"));
}
```

---

## Security Best Practices

### 1. Never Trust Client Input

❌ **BAD**: Using user ID from request body
```java
@PostMapping("/wallets/{walletId}/transfer")
public void transfer(@RequestBody TransferRequest request) {
    // request.userId can be manipulated!
    walletService.transfer(request.getUserId(), request);
}
```

✅ **GOOD**: Always use authenticated user ID
```java
@PostMapping("/wallets/{walletId}/transfer")
@ValidateOwnership(resourceType = "WALLET", resourceIdParam = "walletId")
public void transfer(@PathVariable UUID walletId, @RequestBody TransferRequest request) {
    UUID authenticatedUserId = securityContext.getCurrentUserId();
    walletService.transfer(authenticatedUserId, walletId, request);
}
```

### 2. Prevent Resource Enumeration

❌ **BAD**: Different errors for "not found" vs "forbidden"
```java
if (!walletExists(walletId)) {
    return 404; // Attacker learns wallet doesn't exist
}
if (!isOwner(userId, walletId)) {
    return 403; // Attacker learns wallet exists but not theirs
}
```

✅ **GOOD**: Consistent 403 for all unauthorized access
```java
if (!walletExists(walletId) || !isOwner(userId, walletId)) {
    return 403; // Same response regardless
}
```

### 3. Log All Violations

✅ **ALWAYS** log IDOR attempts for security monitoring:

```java
if (!isOwner) {
    securityAuditLogger.logIDORAttempt(userId, resourceType, resourceId, "UNAUTHORIZED_ACCESS");
    throw new OwnershipValidationException("Access denied");
}
```

---

## Compliance Mapping

| Framework       | Requirement                                  | How We Comply                          |
|-----------------|----------------------------------------------|----------------------------------------|
| **OWASP Top 10**| A01:2021 - Broken Access Control            | @ValidateOwnership on all endpoints    |
| **PCI DSS**     | 7.1 - Limit access to system components     | Database-backed ownership validation   |
| **SOX**         | ITGC - Access Controls                       | Audit logging of all access attempts   |
| **GDPR**        | Art 32 - Security of Processing              | Prevents unauthorized data access      |
| **ISO 27001**   | A.9.2.3 - Access Control Systems            | AOP-enforced access control            |

---

## Troubleshooting

### Issue: @ValidateOwnership not working

**Symptoms**: Ownership validation is not being enforced

**Solutions**:
1. Verify AspectJ is enabled:
   ```java
   @EnableAspectJAutoProxy(proxyTargetClass = true)
   ```

2. Check that `OwnershipValidationAspect` is a Spring bean:
   ```java
   @Component  // Should be present
   public class OwnershipValidationAspect { ... }
   ```

3. Ensure the controller is a Spring bean (not manually instantiated):
   ```java
   @RestController  // Should be present
   public class WalletController { ... }
   ```

### Issue: JWT user ID not extracted

**Symptoms**: `extractAuthenticatedUserId()` returns null

**Solutions**:
1. Verify JWT authentication filter is running before AOP:
   ```java
   .addFilterBefore(jwtAuthenticationFilter, UsernamePasswordAuthenticationFilter.class)
   ```

2. Check JWT token is valid and contains user ID claim

3. Verify SecurityContext is populated:
   ```java
   Authentication auth = SecurityContextHolder.getContext().getAuthentication();
   log.debug("Authentication: {}", auth);
   ```

### Issue: Performance degradation

**Symptoms**: Slow endpoint response times

**Solutions**:
1. Add database indexes:
   ```sql
   CREATE INDEX idx_wallets_user_id_id ON wallets(user_id, id);
   ```

2. Enable query logging to identify slow queries:
   ```yaml
   spring:
     jpa:
       show-sql: true
       properties:
         hibernate:
           format_sql: true
   ```

3. Consider caching for frequently accessed resources

---

## Migration Guide

### Migrating Existing Endpoints

1. **Identify endpoints that need protection**:
   - All GET /{id} endpoints
   - All PUT/PATCH/DELETE /{id} endpoints
   - Any endpoint that takes a resource ID parameter

2. **Add @ValidateOwnership annotation**:
   ```java
   @GetMapping("/wallets/{walletId}")
   + @ValidateOwnership(resourceType = "WALLET", resourceIdParam = "walletId")
   public WalletResponse getWallet(@PathVariable UUID walletId) { ... }
   ```

3. **Test thoroughly**:
   - Test with valid owner (should succeed)
   - Test with different user (should return 403)
   - Test with admin user if `allowAdmin = true`

4. **Monitor logs** for any issues:
   ```bash
   grep "IDOR_ATTACK_ATTEMPT" application.log
   ```

---

## Support and Contact

**Security Team**: security@example.com
**Documentation**: https://docs.example.com/security/idor-protection
**Version**: 3.0.0
**Last Updated**: 2025-10-02

---

## License

Copyright © 2025 Waqiti Financial Services
Internal use only - Confidential
