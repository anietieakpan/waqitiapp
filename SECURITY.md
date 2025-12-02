# Security Policy

This document outlines the security policies, practices, and guidelines for the Waqiti fintech platform.

---

## Table of Contents

1. [Supported Versions](#supported-versions)
2. [Reporting a Vulnerability](#reporting-a-vulnerability)
3. [Security Architecture](#security-architecture)
4. [Security Best Practices](#security-best-practices-for-contributors)
5. [Compliance](#compliance)
6. [Security Testing](#security-testing-recommendations)

---

## Supported Versions

| Version | Supported          | Security Updates |
| ------- | ------------------ | ---------------- |
| 1.x.x   | :white_check_mark: | Active |
| < 1.0   | :x:                | End of life |

## Reporting a Vulnerability

We take the security of Waqiti seriously. If you believe you have found a security vulnerability, please report it to us as described below.

### Please DO NOT:

- Open a public GitHub issue for security vulnerabilities
- Disclose the vulnerability publicly before it has been addressed
- Access or modify other users' data without permission

### Please DO:

1. **Report via GitHub**: [Create a private security advisory](../../security/advisories/new) (preferred method)

2. **Include the following information:**
   - Type of vulnerability (e.g., SQL injection, XSS, authentication bypass)
   - Full paths of source file(s) related to the vulnerability
   - Step-by-step instructions to reproduce the issue
   - Proof-of-concept or exploit code (if possible)
   - Impact of the vulnerability

3. **Allow reasonable time** for us to respond and address the issue before any public disclosure

### What to Expect:

- **Acknowledgment**: We will acknowledge receipt of your vulnerability report within 48 hours
- **Communication**: We will keep you informed of our progress toward resolving the issue
- **Timeline**: We aim to resolve critical vulnerabilities within 7 days, and other issues within 30 days
- **Credit**: We will credit you in our security advisories (unless you prefer to remain anonymous)

---

## Security Architecture

### Defense in Depth

Waqiti implements security at multiple layers:

```
┌──────────────────────────────────────────────────────────────────────┐
│ LAYER 1: EDGE SECURITY                                               │
│ • DDoS Protection (Cloudflare)                                       │
│ • WAF Rules (OWASP Top 10)                                          │
│ • TLS 1.3 Termination                                                │
│ • Geo-blocking                                                       │
├──────────────────────────────────────────────────────────────────────┤
│ LAYER 2: API GATEWAY                                                 │
│ • Rate Limiting (Redis)                                              │
│ • JWT Validation (Keycloak)                                          │
│ • Request Schema Validation                                          │
│ • Security Headers (CSP, X-Frame-Options)                            │
├──────────────────────────────────────────────────────────────────────┤
│ LAYER 3: SERVICE SECURITY                                            │
│ • mTLS between services                                              │
│ • RBAC Authorization                                                 │
│ • Input Validation                                                   │
│ • Audit Logging                                                      │
├──────────────────────────────────────────────────────────────────────┤
│ LAYER 4: DATA SECURITY                                               │
│ • Encryption at Rest (AES-256)                                       │
│ • Field-level Encryption (PCI)                                       │
│ • Secrets Management (Vault)                                         │
│ • Key Rotation                                                       │
└──────────────────────────────────────────────────────────────────────┘
```

### Authentication Flow

```
User → API Gateway → Keycloak (OAuth2/OIDC) → JWT Token → Service Access
                          ↓
                   MFA (if enabled)
                          ↓
                   Session Management
```

### Rate Limiting Tiers

| Tier | Limit | Scope | Use Case |
|------|-------|-------|----------|
| IP-based | 10 req/sec | Public endpoints | Auth, registration |
| User-based | 100 req/sec | Authenticated endpoints | Normal operations |
| Sensitive | 5 req/sec | Financial operations | Payments, transfers |

---

## Security Best Practices for Contributors

When contributing to Waqiti, please ensure:

### Code Security

1. **Never commit secrets**: API keys, passwords, tokens, or any credentials
2. **Use environment variables**: All configuration should be externalized
3. **Validate all input**: Never trust user input; validate and sanitize
4. **Use parameterized queries**: Prevent SQL injection
5. **Implement proper authentication**: Use established libraries (Keycloak, Spring Security)
6. **Follow least privilege**: Services should only have necessary permissions

### Financial Application Security

As a fintech application, additional care must be taken:

1. **PCI DSS compliance**: Never log or expose card numbers
2. **Data encryption**: Encrypt sensitive data at rest and in transit
3. **Audit logging**: Log all financial transactions and access
4. **Rate limiting**: Implement rate limiting on all financial endpoints
5. **Transaction integrity**: Use database transactions appropriately

### Secure Coding Examples

**Input Validation:**
```java
// GOOD: Use validation annotations
public class PaymentRequest {
    @NotNull
    private UUID recipientId;

    @NotNull
    @Positive
    @Digits(integer = 15, fraction = 4)
    private BigDecimal amount;

    @NotBlank
    @Size(min = 3, max = 3)
    @Pattern(regexp = "^[A-Z]{3}$")
    private String currency;
}
```

**SQL Injection Prevention:**
```java
// GOOD: Parameterized queries
@Query("SELECT p FROM Payment p WHERE p.userId = :userId AND p.status = :status")
List<Payment> findByUserAndStatus(@Param("userId") UUID userId,
                                   @Param("status") PaymentStatus status);

// BAD: Never do this
@Query("SELECT p FROM Payment p WHERE p.userId = '" + userId + "'")  // VULNERABLE
```

**Sensitive Data Logging:**
```java
// GOOD: Mask sensitive data
log.info("Processing payment for user {}", userId);
log.info("Card ending in {}", maskCardNumber(cardNumber));

// BAD: Never log sensitive data
log.info("Card number: {}", cardNumber);  // NEVER DO THIS
log.info("Password: {}", password);        // NEVER DO THIS
```

### Pre-commit Checks

We recommend installing pre-commit hooks to prevent accidental secret commits:

```bash
# Install gitleaks
brew install gitleaks

# Run manually
gitleaks detect --source . -v

# Scan git history
gitleaks detect --source . --log-opts="--all"

# Or use pre-commit framework
pip install pre-commit
pre-commit install
```

### Prohibited Patterns

The following will cause PR rejection:

| Pattern | Reason |
|---------|--------|
| Hardcoded passwords | Security risk |
| API keys in code | Credential exposure |
| `eval()` or dynamic code execution | Code injection |
| Disabled SSL verification | MITM attacks |
| `*` CORS origins in production | Security bypass |
| SQL string concatenation | SQL injection |
| Logging sensitive data | Data leakage |

---

## Security Features

Waqiti implements the following security measures:

- **Authentication**: OAuth 2.0 / OIDC via Keycloak
- **Authorization**: Role-based access control (RBAC)
- **Encryption**: TLS 1.3 for all communications
- **Secrets Management**: HashiCorp Vault integration
- **Audit Logging**: Comprehensive audit trail
- **Rate Limiting**: API rate limiting and throttling
- **Input Validation**: Server-side validation on all inputs
- **CSRF Protection**: Cross-site request forgery protection
- **XSS Prevention**: Content Security Policy headers

## Dependency Security

We regularly scan dependencies for vulnerabilities:

- **Java**: OWASP Dependency-Check, Snyk
- **Node.js**: npm audit, Snyk
- **Container Images**: Trivy, Clair

## Compliance

Waqiti is designed with the following compliance frameworks in mind:

- PCI DSS (Payment Card Industry Data Security Standard)
- GDPR (General Data Protection Regulation)
- SOC 2 Type II
- Bank Secrecy Act (BSA) / Anti-Money Laundering (AML)

**Note**: Compliance is the responsibility of the deployer. This open-source release provides the framework, but proper configuration and operational procedures are required for compliance.

## Known Security Considerations

The following are known security considerations for deployers:

### Configuration Security

1. **External Configuration Required**: Many services have default threshold values for demonstration purposes. Production deployments MUST override these via:
   - Environment variables
   - Spring Cloud Config
   - HashiCorp Vault

2. **Secrets Management**: Kubernetes ConfigMaps use base64 encoding, not encryption. For production:
   - Enable KMS encryption for etcd
   - Use HashiCorp Vault with the Kubernetes auth method
   - Consider sealed-secrets or external-secrets operator

3. **IP Address Handling**: The `JwtAuthenticationFilter` trusts `X-Forwarded-For` headers for logging. For security-critical decisions:
   - Configure your load balancer to overwrite (not append) proxy headers
   - Validate that requests originate from trusted proxy IPs
   - Use `remoteAddr` for rate limiting and fraud detection

### Areas Requiring Attention Before Production

| Area | Status | Recommendation |
|------|--------|----------------|
| ML Model Loading | Enhanced | Models should be signed and verified |
| Compliance Alerts | Implemented | Configure downstream notification consumers |
| Secrets in ConfigMaps | Documented | Migrate to Vault before production |
| Event Versioning | Partial | Implement Avro schemas for all events |

### Security Testing Recommendations

Before production deployment:

1. **Penetration Testing**: Engage a qualified security firm
2. **Dependency Audit**: Run `mvn dependency-check:check`
3. **Container Scanning**: Scan all images with Trivy
4. **Secret Detection**: Run `gitleaks detect --source .`
5. **Compliance Review**: Review against PCI-DSS, GDPR requirements

## Disclaimer

This software is provided "as is" without warranty of any kind. The maintainers are not responsible for any financial losses, data breaches, or regulatory violations resulting from the use of this software. Users are responsible for:

- Proper security configuration
- Regulatory compliance in their jurisdiction
- Security audits before production deployment
- Ongoing security monitoring and maintenance
