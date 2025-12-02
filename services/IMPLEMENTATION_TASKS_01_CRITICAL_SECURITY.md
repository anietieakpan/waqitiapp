# 🔴 PART 1: CRITICAL SECURITY FIXES
## Timeline: Week 1 (IMMEDIATE ACTION REQUIRED)
## Priority: CRITICAL - Production Blocker
## Team Size: 5 Developers

---

## 🚨 DAY 1-2: IMMEDIATE SECURITY PATCHES

### 1. WALLET SERVICE AUTHORIZATION (8 hours)
**File**: `services/wallet-service/src/main/java/com/waqiti/wallet/api/WalletController.java`

#### Task 1.1: Add Spring Security Annotations
- [ ] Add `@PreAuthorize("hasRole('USER')")` at class level
- [ ] Add `@PreAuthorize("@walletService.isWalletOwner(authentication.name, #walletId)")` to:
  - [ ] Line 32: `getWallet(UUID walletId)`
  - [ ] Line 46: `transfer(TransferRequest request)` - check fromWalletId
  - [ ] Line 53: `deposit(UUID walletId, DepositRequest request)`
  - [ ] Line 60: `withdraw(UUID walletId, WithdrawRequest request)`
  - [ ] Line 67: `getTransactionHistory(UUID walletId)`
  - [ ] Line 74: `freezeWallet(UUID walletId)`
  - [ ] Line 81: `unfreezeWallet(UUID walletId)`

#### Task 1.2: Implement Ownership Validation Service
**File**: Create `services/wallet-service/src/main/java/com/waqiti/wallet/security/WalletOwnershipValidator.java`
```java
@Component("walletOwnershipValidator")
public class WalletOwnershipValidator {
    
    @Autowired
    private WalletRepository walletRepository;
    
    public boolean isWalletOwner(String username, UUID walletId) {
        // Implementation needed
    }
    
    public boolean canAccessWallet(String username, UUID walletId, String permission) {
        // Implementation needed
    }
}
```

#### Task 1.3: Add Rate Limiting
- [ ] Add `@RateLimited(permits = 10, window = "PT1M")` to transfer endpoint
- [ ] Add `@RateLimited(permits = 5, window = "PT1M")` to withdraw endpoint
- [ ] Configure rate limiting in application.yml

#### Task 1.4: Input Validation
- [ ] Add `@Valid` annotation to all `@RequestBody` parameters
- [ ] Add `@NotNull` to all UUID path parameters
- [ ] Create custom validators for amount fields (positive, max limits)

---

### 2. PAYMENT SERVICE AUTHORIZATION (8 hours)
**File**: `services/payment-service/src/main/java/com/waqiti/payment/api/PaymentController.java`

#### Task 2.1: Secure Payment Endpoints
- [ ] Add `@PreAuthorize` annotations to all payment endpoints
- [ ] Implement payment ownership validation
- [ ] Add transaction amount limits based on user roles
- [ ] Implement dual approval for large transactions

#### Task 2.2: Add Audit Logging
- [ ] Log all payment attempts (success and failure)
- [ ] Include user ID, amount, timestamp, IP address
- [ ] Create alerts for suspicious patterns

---

### 3. REMOVE HARDCODED SECRETS (4 hours)
**File**: `services/common/src/main/java/com/waqiti/common/security/SecretRotationManager.java`

#### Task 3.1: Fix Line 741 - Hardcoded Encryption Key
- [ ] Remove `"DefaultEncryptionKey123456789012"`
- [ ] Implement secure failure mode (throw exception instead)
- [ ] Add environment variable configuration
- [ ] Create key generation utility

#### Task 3.2: Scan for Other Hardcoded Secrets
- [ ] Search all services for hardcoded passwords
- [ ] Check application.yml files for embedded secrets
- [ ] Replace with Vault references or environment variables

---

### 4. SECURE CONFIGURATION FILES (4 hours)

#### Task 4.1: Fix Keycloak Secrets
**Files**: All `application-keycloak.yml` files
- [ ] Replace predictable secrets like `service-name-secret`
- [ ] Generate random 32-character secrets for each service
- [ ] Store in HashiCorp Vault or AWS Secrets Manager
- [ ] Update deployment scripts

#### Task 4.2: Database Credentials
**File**: `services/expense-service/src/main/resources/application.yml:16`
- [ ] Remove default password `waqiti_password`
- [ ] Configure environment-specific credentials
- [ ] Use Spring Cloud Config for centralized management

---

## 🛡️ DAY 3-4: AUTHORIZATION FRAMEWORK

### 5. IMPLEMENT ROLE-BASED ACCESS CONTROL (16 hours)

#### Task 5.1: Define Security Roles
**File**: Create `services/common/src/main/java/com/waqiti/common/security/Roles.java`
```java
public enum Roles {
    USER,           // Basic user
    PREMIUM_USER,   // Premium features
    MERCHANT,       // Merchant account
    ADMIN,          // Admin access
    COMPLIANCE,     // Compliance officer
    SUPPORT,        // Customer support
    SYSTEM          // System operations
}
```

#### Task 5.2: Create Permission Matrix
**File**: Create `services/common/src/main/java/com/waqiti/common/security/PermissionMatrix.java`
- [ ] Define permissions for each role
- [ ] Create method-level permission checks
- [ ] Implement hierarchical permissions

#### Task 5.3: Implement Custom Security Expressions
**File**: Create `services/common/src/main/java/com/waqiti/common/security/CustomMethodSecurityExpressionRoot.java`
- [ ] Create custom SpEL expressions for complex authorization
- [ ] Implement transaction limit checks
- [ ] Add time-based restrictions

---

### 6. SECURE ALL FINANCIAL CONTROLLERS (16 hours)

#### Task 6.1: Core Banking Service
**File**: `services/core-banking-service/src/main/java/com/waqiti/corebanking/api/*.java`
- [ ] Add `@PreAuthorize` to all controllers
- [ ] Implement account ownership validation
- [ ] Add transaction signing for wire transfers

#### Task 6.2: Transaction Service
**File**: `services/transaction-service/src/main/java/com/waqiti/transaction/api/*.java`
- [ ] Secure all transaction endpoints
- [ ] Implement transaction approval workflow
- [ ] Add fraud check integration points

#### Task 6.3: Ledger Service
**File**: `services/ledger-service/src/main/java/com/waqiti/ledger/api/*.java`
- [ ] Add read-only access for most users
- [ ] Restrict write access to system accounts
- [ ] Implement audit trail for all modifications

---

## ✅ DAY 5: VALIDATION & TESTING

### 7. ADD INPUT VALIDATION (8 hours)

#### Task 7.1: Add @Valid Annotations
**Files**: All controller classes
- [ ] Find all `@PostMapping` and `@PutMapping` methods
- [ ] Add `@Valid` to `@RequestBody` parameters
- [ ] Add `@Validated` at class level where needed

#### Task 7.2: Create Custom Validators
**File**: Create `services/common/src/main/java/com/waqiti/common/validation/`
- [ ] `@ValidAmount` - Positive amounts, max limits
- [ ] `@ValidAccountNumber` - Account format validation
- [ ] `@ValidCurrency` - Supported currency codes
- [ ] `@ValidPhoneNumber` - International format
- [ ] `@ValidSSN` - SSN format and checksum

#### Task 7.3: Implement Request Sanitization
- [ ] XSS prevention filters
- [ ] SQL injection prevention
- [ ] Path traversal protection
- [ ] File upload validation

---

### 8. SECURITY TESTING (8 hours)

#### Task 8.1: Unit Tests for Security
**File**: Create test classes for each secured controller
- [ ] Test authorized access succeeds
- [ ] Test unauthorized access fails with 403
- [ ] Test invalid input returns 400
- [ ] Test rate limiting works

#### Task 8.2: Integration Tests
- [ ] End-to-end authentication flow
- [ ] Token refresh scenarios
- [ ] Session timeout handling
- [ ] Concurrent access tests

#### Task 8.3: Penetration Testing Preparation
- [ ] Document all endpoints
- [ ] Create test user accounts
- [ ] Prepare test data
- [ ] Set up monitoring for pen test

---

## 📋 VERIFICATION CHECKLIST

### Security Verification
- [ ] All financial endpoints require authentication
- [ ] Wallet operations check ownership
- [ ] Payment operations have amount limits
- [ ] No hardcoded secrets in codebase
- [ ] All passwords are encrypted
- [ ] Rate limiting is active
- [ ] Audit logging is comprehensive

### Testing Verification
- [ ] 100% of financial endpoints have security tests
- [ ] All validators have unit tests
- [ ] Integration tests pass
- [ ] No security warnings in build

### Deployment Verification
- [ ] Security patches deployed to staging
- [ ] Monitoring alerts configured
- [ ] Security incident response plan ready
- [ ] Rollback plan documented

---

## 🚀 DELIVERABLES

### Code Deliverables
1. **Secured Controllers** - All financial endpoints protected
2. **Ownership Validators** - Wallet and account ownership checks
3. **Custom Validators** - Input validation for all requests
4. **Security Tests** - Comprehensive test coverage

### Documentation Deliverables
1. **Security Matrix** - Roles and permissions documented
2. **API Security Guide** - How to use secured endpoints
3. **Incident Response Plan** - Security breach procedures
4. **Deployment Guide** - Secure deployment checklist

### Configuration Deliverables
1. **Vault Configuration** - All secrets in Vault
2. **Rate Limiting Config** - Limits for all endpoints
3. **Monitoring Config** - Security alerts set up
4. **Backup Config** - Secure backup procedures

---

## 🎯 SUCCESS CRITERIA

### Must Complete (100%)
- [ ] Wallet service fully secured
- [ ] Payment service fully secured
- [ ] No hardcoded secrets
- [ ] All endpoints validated

### Should Complete (90%)
- [ ] Security tests written
- [ ] Documentation complete
- [ ] Monitoring configured

### Nice to Have (70%)
- [ ] Advanced fraud rules
- [ ] Behavioral analytics
- [ ] ML-based anomaly detection

---

## 👥 TEAM ASSIGNMENTS

### Developer 1: Wallet Service Lead
- Primary: Tasks 1.1-1.4 (Wallet authorization)
- Secondary: Task 7.1 (Validation)

### Developer 2: Payment Service Lead
- Primary: Tasks 2.1-2.2 (Payment authorization)
- Secondary: Task 7.2 (Custom validators)

### Developer 3: Security Infrastructure
- Primary: Tasks 3.1-3.2, 4.1-4.2 (Secrets management)
- Secondary: Task 5.1-5.3 (RBAC framework)

### Developer 4: Framework Development
- Primary: Tasks 5.1-5.3 (Authorization framework)
- Secondary: Task 6.1-6.3 (Controller security)

### Developer 5: Testing & Validation
- Primary: Tasks 8.1-8.3 (Security testing)
- Secondary: Task 7.3 (Request sanitization)

---

## ⚠️ RISK MITIGATION

### Technical Risks
- **Risk**: Breaking existing functionality
- **Mitigation**: Feature flags for gradual rollout

### Business Risks
- **Risk**: Customer locked out of accounts
- **Mitigation**: Customer support override capability

### Operational Risks
- **Risk**: Performance degradation
- **Mitigation**: Load testing before deployment

---

## 📞 ESCALATION CONTACTS

- **Security Lead**: [Name] - Critical security decisions
- **Compliance Officer**: [Name] - Regulatory requirements
- **DevOps Lead**: [Name] - Deployment issues
- **Product Owner**: [Name] - Business impact decisions

---

**Last Updated**: September 10, 2025  
**Next Review**: End of Day 2  
**Status**: NOT STARTED