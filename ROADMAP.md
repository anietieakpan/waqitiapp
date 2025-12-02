# Waqiti Roadmap

> **This document outlines what needs to be done to make Waqiti production-ready.**
>
> Contributors are welcome! Pick an area that interests you and dive in.

## Project Status Overview

| Area | Status | Notes |
|------|--------|-------|
| **Architecture** | Designed | 70+ microservices defined |
| **Core Services** | Partial | Some services compile, others need work |
| **Tests** | Incomplete | Many services lack comprehensive tests |
| **Documentation** | Partial | Architecture docs exist, API docs incomplete |
| **Infrastructure** | Defined | K8s manifests and Docker Compose exist |
| **Security** | Designed | Patterns defined, implementation ongoing |

> **Note**: Some documents in `docs/` reference higher completion percentages. Those were internal working estimates. This ROADMAP.md reflects the honest state for open source contributors.

---

## Priority 1: Critical Path to MVP

These items are essential for a minimally functional system.

### 1.1 Get Core Services Compiling

**Goal**: Ensure the core payment flow compiles and runs.

- [ ] `user-service` - User registration and authentication
- [ ] `wallet-service` - Wallet creation and balance management
- [ ] `payment-service` - P2P payment processing
- [ ] `transaction-service` - Transaction state management
- [ ] `ledger-service` - Double-entry accounting
- [ ] `notification-service` - Basic notifications
- [ ] `api-gateway` - Request routing
- [ ] `discovery-service` - Service registration (Eureka)
- [ ] `config-service` - Centralized configuration

**How to help**: Try building each service with `mvn clean compile -pl services/<service-name>` and fix compilation errors.

### 1.2 Basic Integration Tests

**Goal**: Verify the core payment flow works end-to-end.

- [ ] User registration flow
- [ ] Wallet creation flow
- [ ] P2P transfer between two users
- [ ] Transaction history retrieval

**How to help**: Write integration tests using TestContainers.

### 1.3 Docker Compose Verification

**Goal**: `docker-compose up` should start a working system.

- [ ] Verify all service Dockerfiles build
- [ ] Verify services start in correct order
- [ ] Verify services can communicate
- [ ] Document any manual steps needed

---

## Priority 2: Test Coverage

### 2.1 Unit Tests

**Goal**: 80% code coverage for business logic.

| Service | Current | Target | Status |
|---------|---------|--------|--------|
| user-service | Unknown | 80% | Needs assessment |
| wallet-service | Unknown | 80% | Needs assessment |
| payment-service | Unknown | 80% | Needs assessment |
| transaction-service | Unknown | 80% | Needs assessment |
| ledger-service | Unknown | 80% | Needs assessment |
| fraud-detection-service | Unknown | 80% | Needs assessment |

**How to help**: Pick a service and add unit tests. Focus on:
- Service layer business logic
- Edge cases and error handling
- Input validation

### 2.2 Integration Tests

- [ ] Database integration tests with TestContainers
- [ ] Kafka integration tests
- [ ] Redis integration tests
- [ ] Cross-service integration tests

### 2.3 Contract Tests

- [ ] Define API contracts for core services
- [ ] Implement Pact tests for service-to-service communication

---

## Priority 3: Security Hardening

### 3.1 Authentication & Authorization

- [ ] Verify Keycloak integration works
- [ ] Test OAuth2 flows end-to-end
- [ ] Verify role-based access control
- [ ] Test MFA implementation

### 3.2 Data Security

- [ ] Verify encryption at rest is implemented
- [ ] Verify PII masking in logs
- [ ] Test Vault integration for secrets
- [ ] Security audit of payment flows

### 3.3 Input Validation

- [ ] Audit all API endpoints for input validation
- [ ] Add validation where missing
- [ ] Test for SQL injection, XSS, etc.

---

## Priority 4: Compliance Features

### 4.1 KYC/AML

- [ ] Document verification flow
- [ ] Sanctions screening integration
- [ ] Suspicious activity detection
- [ ] Regulatory reporting

### 4.2 Audit Logging

- [ ] Verify all financial operations are logged
- [ ] Ensure logs are tamper-evident
- [ ] Implement log retention policies

### 4.3 GDPR

- [ ] Data subject access requests
- [ ] Right to erasure implementation
- [ ] Consent management
- [ ] Data portability

---

## Priority 5: Documentation

### 5.1 API Documentation

- [ ] OpenAPI specs for all services
- [ ] Example requests/responses
- [ ] Error code documentation
- [ ] Authentication guide

### 5.2 Deployment Guides

- [ ] Local development setup (verified working)
- [ ] Kubernetes deployment guide
- [ ] Production checklist
- [ ] Troubleshooting guide

### 5.3 Architecture Documentation

- [ ] Service interaction diagrams
- [ ] Data flow documentation
- [ ] Event schema documentation
- [ ] Database schema documentation

---

## Priority 6: Additional Services

These services exist but need completion:

### 6.1 Financial Services
- [ ] `investment-service` - Portfolio management
- [ ] `lending-service` - Loan origination
- [ ] `savings-service` - Savings goals
- [ ] `bnpl-service` - Buy Now Pay Later

### 6.2 Card Services
- [ ] `card-service` - Card management
- [ ] `virtual-card-service` - Virtual card issuance

### 6.3 Crypto Services
- [ ] `crypto-service` - Cryptocurrency operations
- [ ] `nft-service` - NFT management

---

## How to Contribute

### Getting Started

1. **Fork the repository**
2. **Pick an item** from this roadmap
3. **Create an issue** to claim it (prevents duplicate work)
4. **Submit a PR** when ready

### Good First Issues

Look for issues labeled `good-first-issue`:
- Adding unit tests to existing services
- Fixing compilation errors
- Improving documentation
- Adding input validation

### Communication

- **Questions**: Open a GitHub Discussion
- **Bugs**: Open a GitHub Issue
- **Ideas**: Open a GitHub Discussion in "Ideas" category

---

## Version Milestones

### v0.1.0 - "It Compiles"
- [ ] All core services compile
- [ ] Docker Compose starts all services
- [ ] Basic health checks pass

### v0.2.0 - "Basic Flow Works"
- [ ] User registration works
- [ ] Wallet creation works
- [ ] P2P transfer completes successfully
- [ ] Basic tests pass

### v0.3.0 - "Tested"
- [ ] 60% unit test coverage
- [ ] Integration tests for core flows
- [ ] CI pipeline green

### v0.4.0 - "Secure"
- [ ] Security audit completed
- [ ] Authentication flows verified
- [ ] Input validation comprehensive

### v1.0.0 - "Production Ready"
- [ ] 80% test coverage
- [ ] Security audit passed
- [ ] Performance benchmarks met
- [ ] Documentation complete
- [ ] Compliance features verified

---

## Questions?

If you're unsure where to start or have questions about the architecture, open a GitHub Discussion. We're happy to help!

---

*Last updated: December 2025*
