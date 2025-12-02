# Keycloak Migration - Legacy JWT Decommissioning Plan

## Executive Summary
This document provides a comprehensive plan for safely decommissioning the legacy JWT authentication system and completing the migration to Keycloak across the Waqiti P2P payment platform.

## Current State Analysis

### Authentication Architecture Status
- **Keycloak Implementation**: 70% complete
- **Legacy JWT**: Active in production
- **Dual-Mode**: Enabled for zero-downtime migration
- **Risk Level**: HIGH - Critical missing implementations identified

### Critical Issues Identified
1. **Missing JwtTokenProvider Implementation** - Referenced but not found
2. **Dual authentication complexity** - Potential race conditions
3. **Inconsistent token formats** - RS256 (Keycloak) vs HMAC-SHA (Legacy)
4. **Service-to-service authentication gaps**
5. **Hardcoded security fallbacks**

## Phase 1: Stabilization (Week 1-2)

### 1.1 Implement Missing Components
```java
// Priority 1: Create JwtTokenProvider implementation
package com.example.common.security;

@Component
public class JwtTokenProvider {
    @Value("${jwt.secret}")
    private String jwtSecret;
    
    @Value("${jwt.expiration:3600000}")
    private long jwtExpiration;
    
    public String generateToken(Authentication authentication) {
        // Implementation for backward compatibility
    }
    
    public boolean validateToken(String token) {
        // Validation logic
    }
}
```

### 1.2 Security Audit Checklist
- [ ] Review all hardcoded secrets
- [ ] Validate Vault integration
- [ ] Test failover mechanisms
- [ ] Verify service-to-service auth
- [ ] Check CORS configurations
- [ ] Validate rate limiting

### 1.3 Monitoring Setup
```yaml
monitoring:
  authentication:
    - track_legacy_jwt_usage
    - track_keycloak_usage
    - monitor_auth_failures
    - alert_on_fallback_activation
```

## Phase 2: Complete Keycloak Integration (Week 3-4)

### 2.1 Service Migration Order
Priority based on risk and dependencies:

1. **Core Services** (Week 3)
   - [ ] payment-service
   - [ ] wallet-service
   - [ ] transaction-service
   - [ ] user-service

2. **Supporting Services** (Week 3)
   - [ ] notification-service
   - [ ] analytics-service
   - [ ] fraud-detection-service
   - [ ] kyc-service

3. **Extended Services** (Week 4)
   - [ ] card-service
   - [ ] savings-service
   - [ ] merchant-service
   - [ ] billing-service

4. **Infrastructure Services** (Week 4)
   - [ ] reporting-service
   - [ ] config-service
   - [ ] admin-service
   - [ ] compliance-service
   - [ ] audit-service

### 2.2 Migration Steps Per Service

#### Step 1: Update Security Configuration
```java
@Configuration
@EnableWebSecurity
@Profile("keycloak")
public class ServiceKeycloakConfig extends KeycloakSecurityConfig {
    @Override
    protected void configure(HttpSecurity http) throws Exception {
        super.configure(http);
        // Service-specific configuration
    }
}
```

#### Step 2: Update Application Properties
```yaml
spring:
  profiles:
    active: keycloak
    
keycloak:
  enabled: true
  realm: waqiti
  auth-server-url: ${KEYCLOAK_URL}
  ssl-required: external
  resource: ${service-name}
  credentials:
    secret: ${KEYCLOAK_CLIENT_SECRET}
```

#### Step 3: Remove Legacy JWT Code
```java
// Remove or deprecate:
- JwtAuthenticationFilter (legacy)
- JwtTokenUtil (legacy)
- CustomUserDetailsService (if using)
- TokenAuthenticationService
```

#### Step 4: Update Controllers
```java
@RestController
@RequestMapping("/api/v1")
public class ServiceController {
    
    @GetMapping("/protected")
    @PreAuthorize("hasRole('USER')")
    public ResponseEntity<?> protectedEndpoint() {
        // Keycloak will handle authentication
    }
}
```

## Phase 3: Testing & Validation (Week 5)

### 3.1 Test Scenarios

#### Authentication Tests
```java
@Test
public void testKeycloakAuthentication() {
    // Test Keycloak JWT validation
}

@Test
public void testLegacyJWTRejection() {
    // Ensure legacy tokens are rejected
}

@Test
public void testServiceToServiceAuth() {
    // Test inter-service communication
}
```

### 3.2 Performance Testing
- Load test authentication endpoints
- Measure token validation latency
- Test concurrent authentication requests
- Validate cache performance

### 3.3 Security Testing
- Penetration testing
- Token manipulation tests
- Session fixation tests
- CORS validation
- Rate limiting verification

## Phase 4: Gradual Rollout (Week 6-7)

### 4.1 Canary Deployment Strategy
```yaml
deployment:
  strategy: canary
  stages:
    - percentage: 5
      duration: 1d
      rollback_on_error: true
    - percentage: 25
      duration: 2d
      rollback_on_error: true
    - percentage: 50
      duration: 2d
      rollback_on_error: true
    - percentage: 100
      duration: continuous
```

### 4.2 Feature Flags
```java
@Component
public class AuthenticationFeatureFlags {
    
    @Value("${feature.keycloak.enabled:false}")
    private boolean keycloakEnabled;
    
    @Value("${feature.legacy-jwt.enabled:true}")
    private boolean legacyJwtEnabled;
    
    @Value("${feature.dual-mode.enabled:true}")
    private boolean dualModeEnabled;
    
    public AuthMode getAuthMode() {
        if (dualModeEnabled) return AuthMode.DUAL;
        if (keycloakEnabled) return AuthMode.KEYCLOAK;
        return AuthMode.LEGACY;
    }
}
```

### 4.3 Rollback Plan
```bash
#!/bin/bash
# Emergency rollback script

# 1. Switch feature flags
kubectl set env deployment/api-gateway FEATURE_KEYCLOAK_ENABLED=false
kubectl set env deployment/api-gateway FEATURE_LEGACY_JWT_ENABLED=true

# 2. Rollback deployments
kubectl rollout undo deployment/api-gateway
kubectl rollout undo deployment/user-service
kubectl rollout undo deployment/payment-service

# 3. Clear Keycloak tokens from cache
redis-cli FLUSHDB

# 4. Alert team
./notify-team.sh "Authentication rollback initiated"
```

## Phase 5: Legacy Decommissioning (Week 8-9)

### 5.1 Code Removal Checklist

#### Remove Legacy JWT Classes
- [ ] `JwtAuthenticationFilter.java`
- [ ] `JwtTokenProvider.java`
- [ ] `JwtUtil.java`
- [ ] `TokenAuthenticationService.java`
- [ ] `CustomUserDetailsService.java`
- [ ] `LegacySecurityConfig.java`

#### Remove Configuration
- [ ] JWT secret from properties
- [ ] JWT expiration settings
- [ ] Legacy authentication endpoints
- [ ] Custom security filters

#### Update Dependencies
```xml
<!-- Remove from pom.xml -->
<dependency>
    <groupId>io.jsonwebtoken</groupId>
    <artifactId>jjwt</artifactId>
</dependency>
```

### 5.2 Database Cleanup
```sql
-- Archive legacy authentication data
CREATE TABLE auth_migration_archive AS 
SELECT * FROM user_tokens WHERE created_date < '2024-01-01';

-- Remove legacy tables
DROP TABLE user_tokens CASCADE;
DROP TABLE refresh_tokens CASCADE;
DROP TABLE jwt_blacklist CASCADE;
```

### 5.3 API Cleanup
```java
// Mark as deprecated initially
@Deprecated(since = "2.0", forRemoval = true)
@PostMapping("/auth/login")
public ResponseEntity<?> legacyLogin() {
    throw new UnsupportedOperationException(
        "Legacy authentication deprecated. Use Keycloak OAuth2 flow."
    );
}
```

## Phase 6: Post-Migration (Week 10)

### 6.1 Performance Optimization
```yaml
keycloak:
  cache:
    public-key-cache-ttl: 86400
    policy-cache-ttl: 3600
    user-cache-ttl: 1800
  connection-pool:
    size: 50
    max-idle: 10
    min-idle: 5
```

### 6.2 Monitoring & Alerting
```yaml
alerts:
  - name: keycloak_availability
    condition: keycloak_up == 0
    severity: critical
    
  - name: token_validation_latency
    condition: p95_latency > 100ms
    severity: warning
    
  - name: authentication_failure_rate
    condition: failure_rate > 5%
    severity: warning
```

### 6.3 Documentation Updates
- [ ] Update API documentation
- [ ] Update developer guides
- [ ] Create runbooks
- [ ] Update security policies
- [ ] Train support team

## Risk Mitigation

### High-Risk Areas
1. **Token Format Migration**
   - Risk: Incompatible token formats
   - Mitigation: Dual-mode support, gradual migration

2. **Service Communication**
   - Risk: Inter-service auth failures
   - Mitigation: Service accounts, mTLS backup

3. **Performance Impact**
   - Risk: Increased latency
   - Mitigation: Token caching, connection pooling

4. **User Experience**
   - Risk: Authentication disruptions
   - Mitigation: Transparent token refresh, session migration

### Contingency Plans
- 24/7 on-call rotation during migration
- Automated rollback triggers
- Real-time monitoring dashboards
- Customer communication plan
- Incident response procedures

## Success Criteria

### Technical Metrics
- [ ] 100% services on Keycloak
- [ ] Zero legacy JWT usage
- [ ] < 100ms p95 auth latency
- [ ] < 0.1% auth failure rate
- [ ] 99.99% auth availability

### Business Metrics
- [ ] No increase in customer complaints
- [ ] No impact on transaction success rate
- [ ] Improved security audit scores
- [ ] Reduced authentication incidents
- [ ] Compliance certification maintained

## Timeline Summary

| Phase | Duration | Start Date | End Date | Status |
|-------|----------|------------|----------|--------|
| Phase 1: Stabilization | 2 weeks | Week 1 | Week 2 | Not Started |
| Phase 2: Integration | 2 weeks | Week 3 | Week 4 | Not Started |
| Phase 3: Testing | 1 week | Week 5 | Week 5 | Not Started |
| Phase 4: Rollout | 2 weeks | Week 6 | Week 7 | Not Started |
| Phase 5: Decommission | 2 weeks | Week 8 | Week 9 | Not Started |
| Phase 6: Post-Migration | 1 week | Week 10 | Week 10 | Not Started |

## Approval & Sign-off

| Role | Name | Date | Signature |
|------|------|------|-----------|
| CTO | | | |
| Security Lead | | | |
| DevOps Lead | | | |
| Product Owner | | | |
| Compliance Officer | | | |

---

**Document Version**: 1.0
**Last Updated**: 2024-01-15
**Next Review**: Weekly during migration