# CSRF Protection Implementation Guide

## Overview

This implementation provides enterprise-grade CSRF (Cross-Site Request Forgery) protection for the Waqiti fintech platform, specifically designed for distributed microservices architectures.

## Critical Security Features

### ✅ PCI DSS 6.5.9 Compliance
- Protects against Cross-Site Request Forgery (CSRF) attacks
- Mandatory for all financial transaction endpoints
- Token-based validation with cryptographic security

### ✅ Distributed System Support
- Redis-based token storage for horizontal scalability
- Works across multiple service instances
- Session-independent token management

### ✅ Token Rotation
- Automatic token rotation after sensitive financial operations
- Prevents token reuse attacks
- Configurable token expiration (30 minutes default)

## Architecture

```
┌─────────────┐         ┌──────────────┐         ┌─────────────┐
│   Frontend  │────────▶│   API Gateway │────────▶│  Service    │
│   (SPA)     │         │  + Security   │         │  Instance   │
└─────────────┘         └──────────────┘         └─────────────┘
      │                        │                         │
      │                        │                         │
      │                        ▼                         │
      │                 ┌──────────────┐                │
      │                 │ CSRF Filter  │                │
      │                 └──────────────┘                │
      │                        │                         │
      │                        ▼                         │
      │                 ┌──────────────┐                │
      │                 │ CSRF Token   │                │
      │                 │  Repository  │                │
      │                 └──────────────┘                │
      │                        │                         │
      │                        ▼                         │
      │                 ┌──────────────┐                │
      └────────────────▶│    Redis     │◀───────────────┘
                        │ (Token Store)│
                        └──────────────┘
```

## Components

### 1. CsrfTokenRepository
**Location**: `com.waqiti.security.csrf.CsrfTokenRepository`

**Responsibilities**:
- Generate cryptographically secure CSRF tokens (256-bit)
- Store tokens in Redis with session binding
- Validate tokens with constant-time comparison
- Rotate tokens for sensitive operations
- Auto-expire tokens after 30 minutes

**Key Methods**:
```java
CsrfToken generateToken(HttpServletRequest request)
void saveToken(CsrfToken token, HttpServletRequest request, HttpServletResponse response)
CsrfToken loadToken(HttpServletRequest request)
CsrfToken rotateToken(HttpServletRequest request, HttpServletResponse response)
boolean isTokenExpired(String tokenValue)
```

### 2. CsrfProtectionFilter
**Location**: `com.waqiti.security.csrf.CsrfProtectionFilter`

**Responsibilities**:
- Validate CSRF tokens on POST/PUT/PATCH/DELETE requests
- Exempt safe methods (GET, HEAD, OPTIONS)
- Exempt public endpoints (login, registration, webhooks)
- Enhanced logging for financial endpoints
- Automatic token rotation for critical operations

**Protected Endpoints**:
- `/api/v1/payments/**`
- `/api/v1/transfers/**`
- `/api/v1/withdrawals/**`
- `/api/v1/wallets/*/transfer`
- `/api/v1/wallets/*/withdraw`
- `/api/v1/investments/*/buy`
- `/api/v1/investments/*/sell`
- `/api/v1/beneficiaries/**`
- `/api/v1/cards/add`
- `/api/v1/bank-accounts/add`
- `/api/v1/scheduled-payments/**`
- `/api/v1/limits/update`

**Exempt Endpoints**:
- Public authentication endpoints
- Webhooks (verified by provider signatures)
- Service-to-service calls (verified by API keys)
- Actuator endpoints
- Documentation endpoints

### 3. CsrfController
**Location**: `com.waqiti.security.csrf.CsrfController`

**Endpoints**:

#### GET /api/v1/csrf/token
Get CSRF token for frontend applications

**Response**:
```json
{
  "token": "abc123...",
  "headerName": "X-CSRF-TOKEN",
  "parameterName": "_csrf",
  "expiresInMinutes": 30
}
```

#### GET /api/v1/csrf/validate
Validate current CSRF token

**Response**:
```json
{
  "valid": true,
  "message": "Token valid"
}
```

#### GET /api/v1/csrf/refresh
Refresh CSRF token (generates new token)

**Response**:
```json
{
  "token": "xyz789...",
  "headerName": "X-CSRF-TOKEN",
  "parameterName": "_csrf",
  "expiresInMinutes": 30,
  "message": "Token refreshed successfully"
}
```

## Frontend Integration

### React/Angular/Vue (SPA)

```javascript
// 1. Fetch CSRF token on app initialization
async function initializeCsrf() {
  const response = await fetch('/api/v1/csrf/token', {
    method: 'GET',
    credentials: 'include',
    headers: {
      'Authorization': `Bearer ${getAccessToken()}`
    }
  });

  const data = await response.json();

  // Store token in memory (NOT localStorage for security)
  window.csrfToken = data.token;
  window.csrfHeaderName = data.headerName;

  // Set up automatic token refresh (every 25 minutes)
  setInterval(refreshCsrfToken, 25 * 60 * 1000);
}

// 2. Include CSRF token in all POST/PUT/PATCH/DELETE requests
async function makeFinancialRequest(url, data) {
  const response = await fetch(url, {
    method: 'POST',
    credentials: 'include',
    headers: {
      'Authorization': `Bearer ${getAccessToken()}`,
      'Content-Type': 'application/json',
      [window.csrfHeaderName]: window.csrfToken
    },
    body: JSON.stringify(data)
  });

  if (response.status === 403) {
    // CSRF token invalid/expired - refresh and retry
    await refreshCsrfToken();
    return makeFinancialRequest(url, data);
  }

  return response.json();
}

// 3. Refresh token periodically or on error
async function refreshCsrfToken() {
  const response = await fetch('/api/v1/csrf/refresh', {
    method: 'GET',
    credentials: 'include',
    headers: {
      'Authorization': `Bearer ${getAccessToken()}`
    }
  });

  const data = await response.json();
  window.csrfToken = data.token;
}
```

### Axios Interceptor Example

```javascript
import axios from 'axios';

// Add request interceptor to include CSRF token
axios.interceptors.request.use(
  config => {
    if (['post', 'put', 'patch', 'delete'].includes(config.method)) {
      config.headers['X-CSRF-TOKEN'] = window.csrfToken;
    }
    return config;
  },
  error => Promise.reject(error)
);

// Add response interceptor to handle CSRF errors
axios.interceptors.response.use(
  response => response,
  async error => {
    if (error.response?.status === 403 &&
        error.response?.data?.code === 'CSRF_INVALID') {
      // Refresh token and retry request
      await refreshCsrfToken();
      error.config.headers['X-CSRF-TOKEN'] = window.csrfToken;
      return axios.request(error.config);
    }
    return Promise.reject(error);
  }
);
```

### Mobile Apps (iOS/Android)

```swift
// iOS Example
class CsrfManager {
    private var token: String?
    private var headerName: String = "X-CSRF-TOKEN"

    func initialize() async throws {
        let url = URL(string: "\(baseURL)/api/v1/csrf/token")!
        var request = URLRequest(url: url)
        request.setValue("Bearer \(accessToken)", forHTTPHeaderField: "Authorization")

        let (data, _) = try await URLSession.shared.data(for: request)
        let response = try JSONDecoder().decode(CsrfResponse.self, from: data)

        self.token = response.token
        self.headerName = response.headerName

        // Schedule refresh every 25 minutes
        scheduleTokenRefresh()
    }

    func addCsrfHeader(to request: inout URLRequest) {
        if let token = token {
            request.setValue(token, forHTTPHeaderField: headerName)
        }
    }
}
```

## Configuration

### application.yml

```yaml
security:
  csrf:
    enabled: true
    token:
      validity-minutes: 30
      length: 32
    redis:
      key-prefix: csrf:
      ttl-minutes: 30
```

### Redis Configuration

Ensure Redis is configured for token storage:

```yaml
spring:
  data:
    redis:
      host: ${REDIS_HOST:localhost}
      port: ${REDIS_PORT:6379}
      password: ${REDIS_PASSWORD:}
      database: 0
      timeout: 2000ms
      lettuce:
        pool:
          max-active: 8
          max-idle: 8
          min-idle: 0
```

## Testing

### Unit Tests

```java
@Test
void testCsrfTokenGeneration() {
    MockHttpServletRequest request = new MockHttpServletRequest();
    CsrfToken token = csrfTokenRepository.generateToken(request);

    assertNotNull(token);
    assertNotNull(token.getToken());
    assertEquals("X-CSRF-TOKEN", token.getHeaderName());
    assertEquals("_csrf", token.getParameterName());
}

@Test
void testCsrfProtectionOnFinancialEndpoint() throws Exception {
    mockMvc.perform(post("/api/v1/payments/create")
            .contentType(MediaType.APPLICATION_JSON)
            .content("{\"amount\": 100.00}")
            .header("Authorization", "Bearer " + validJwt))
        .andExpect(status().isForbidden());

    // With valid CSRF token
    mockMvc.perform(post("/api/v1/payments/create")
            .contentType(MediaType.APPLICATION_JSON)
            .content("{\"amount\": 100.00}")
            .header("Authorization", "Bearer " + validJwt)
            .header("X-CSRF-TOKEN", csrfToken))
        .andExpect(status().isOk());
}
```

### Integration Tests

```java
@SpringBootTest
@AutoConfigureMockMvc
class CsrfIntegrationTest {

    @Test
    void testEndToEndCsrfFlow() throws Exception {
        // 1. Get CSRF token
        MvcResult result = mockMvc.perform(get("/api/v1/csrf/token")
                .header("Authorization", "Bearer " + jwt))
            .andExpect(status().isOk())
            .andReturn();

        String token = JsonPath.read(result.getResponse().getContentAsString(), "$.token");

        // 2. Use token for financial transaction
        mockMvc.perform(post("/api/v1/transfers/initiate")
                .header("Authorization", "Bearer " + jwt)
                .header("X-CSRF-TOKEN", token)
                .contentType(MediaType.APPLICATION_JSON)
                .content(transferRequest))
            .andExpect(status().isOk());

        // 3. Verify token rotation
        mockMvc.perform(post("/api/v1/transfers/initiate")
                .header("Authorization", "Bearer " + jwt)
                .header("X-CSRF-TOKEN", token) // Old token
                .contentType(MediaType.APPLICATION_JSON)
                .content(transferRequest))
            .andExpect(status().isForbidden());
    }
}
```

## Monitoring and Alerting

### Metrics

Monitor these metrics in your observability platform:

```
csrf_token_generated_total
csrf_token_validated_total
csrf_token_validation_failed_total
csrf_token_rotated_total
csrf_token_expired_total
```

### Alerts

Set up alerts for:

1. **High CSRF Failure Rate**: > 5% failed validations
2. **CSRF Attack Pattern**: Multiple failures from same IP
3. **Token Expiration Issues**: High rate of expired tokens

### Logging

All CSRF operations are logged with appropriate severity:

- Token generation: DEBUG
- Token validation: DEBUG
- Validation failures: WARN
- Financial endpoint attack attempts: ERROR
- Token rotation: INFO

## Security Considerations

### ✅ DO

- Always include CSRF token in state-changing requests
- Store CSRF tokens in memory (not localStorage)
- Refresh tokens periodically (every 25 minutes)
- Handle 403 errors with automatic token refresh
- Use HTTPS for all API calls

### ❌ DON'T

- Store CSRF tokens in localStorage (XSS vulnerability)
- Disable CSRF protection in production
- Use same token across multiple sessions
- Hardcode CSRF tokens
- Skip CSRF protection for "internal" endpoints

## Troubleshooting

### Issue: 403 CSRF Validation Failed

**Causes**:
1. Token expired (> 30 minutes old)
2. Token not included in request
3. Session mismatch (different user/device)
4. Token corrupted or tampered with

**Solutions**:
1. Refresh token via `/api/v1/csrf/refresh`
2. Check header name: `X-CSRF-TOKEN`
3. Verify JWT token is valid and matches CSRF session
4. Generate new token

### Issue: Token Not Found

**Causes**:
1. Redis connection failure
2. Token not generated on app init
3. Session expired

**Solutions**:
1. Check Redis connectivity
2. Call `/api/v1/csrf/token` on app startup
3. Regenerate session and token

## Compliance Mapping

| Requirement | Implementation |
|-------------|----------------|
| PCI DSS 6.5.9 | CSRF protection on all financial endpoints |
| OWASP A01:2021 | Token-based access control |
| SOC 2 | Audit logging of all CSRF operations |
| ISO 27001 | Cryptographically secure tokens (256-bit) |

## References

- [OWASP CSRF Prevention Cheat Sheet](https://cheatsheetseries.owasp.org/cheatsheets/Cross-Site_Request_Forgery_Prevention_Cheat_Sheet.html)
- [PCI DSS Requirement 6.5.9](https://www.pcisecuritystandards.org/)
- [Spring Security CSRF Documentation](https://docs.spring.io/spring-security/reference/features/exploits/csrf.html)
