# Rate Limiting Implementation Guide for Waqiti Services

## Overview
This guide explains how to implement rate limiting in Waqiti services using the @RateLimited annotation.

## Quick Start

### 1. Basic Rate Limiting (By IP Address)
```java
@RateLimited
@GetMapping("/api/v1/resource")
public ResponseEntity<?> getResource() {
    // Default: 100 requests per minute per IP
}
```

### 2. User-Based Rate Limiting
```java
@RateLimited(keyType = RateLimited.KeyType.USER)
@PostMapping("/api/v1/payments")
public ResponseEntity<?> createPayment() {
    // 100 requests per minute per authenticated user
}
```

### 3. Custom Rate Limits
```java
@RateLimited(
    keyType = RateLimited.KeyType.USER,
    capacity = 10,              // Max 10 tokens
    refillTokens = 10,          // Refill 10 tokens
    refillPeriodMinutes = 5,    // Every 5 minutes
    tokens = 2                  // Consume 2 tokens per request
)
@PostMapping("/api/v1/high-value-transfer")
public ResponseEntity<?> highValueTransfer() {
    // 10 requests per 5 minutes, each request consumes 2 tokens
}
```

## Key Types

### IP-Based (Default)
```java
@RateLimited(keyType = RateLimited.KeyType.IP)
```
- Rate limits by client IP address
- Good for public endpoints
- Default if keyType not specified

### User-Based
```java
@RateLimited(keyType = RateLimited.KeyType.USER)
```
- Rate limits by authenticated user ID
- Requires authentication
- Best for user-specific operations

### API Key-Based
```java
@RateLimited(keyType = RateLimited.KeyType.API_KEY)
```
- Rate limits by API key
- Good for B2B integrations
- Requires API key in request

### Custom Key
```java
@RateLimited(
    keyType = RateLimited.KeyType.CUSTOM,
    customKeyExpression = "#accountId"  // Uses method parameter
)
public void processAccount(@PathVariable String accountId) {
    // Rate limited by accountId parameter
}
```

### Method-Based
```java
@RateLimited(keyType = RateLimited.KeyType.METHOD)
```
- Shared rate limit for all callers of this method
- Good for expensive operations

### Global
```java
@RateLimited(keyType = RateLimited.KeyType.GLOBAL)
```
- Single shared limit for everyone
- Use sparingly, only for system-wide protection

## Best Practices

### 1. Financial Operations
```java
@RateLimited(
    keyType = RateLimited.KeyType.USER,
    capacity = 10,
    refillTokens = 10,
    refillPeriodMinutes = 5,
    errorMessage = "Too many payment attempts. Please wait before trying again."
)
@PostMapping("/api/v1/payments/transfer")
public ResponseEntity<?> transfer(@RequestBody TransferRequest request) {
    // Strict limits for financial operations
}
```

### 2. Document Uploads
```java
@RateLimited(
    keyType = RateLimited.KeyType.USER,
    capacity = 5,
    refillTokens = 5,
    refillPeriodMinutes = 10,
    tokens = 3  // Higher cost for resource-intensive operations
)
@PostMapping("/api/v1/documents/upload")
public ResponseEntity<?> uploadDocument(@RequestParam MultipartFile file) {
    // Document processing is expensive
}
```

### 3. Public APIs
```java
@RateLimited(
    capacity = 30,  // Lower limit for public endpoints
    refillTokens = 30,
    refillPeriodMinutes = 1
)
@GetMapping("/api/public/rates")
public ResponseEntity<?> getExchangeRates() {
    // Public endpoints need stricter limits
}
```

### 4. Webhook Endpoints
```java
@RateLimited(
    keyType = RateLimited.KeyType.CUSTOM,
    customKeyExpression = "#webhookId",
    capacity = 100,
    refillTokens = 100,
    refillPeriodMinutes = 1
)
@PostMapping("/webhooks/{webhookId}/events")
public ResponseEntity<?> handleWebhook(@PathVariable String webhookId) {
    // Rate limit per webhook source
}
```

## Configuration Override

You can override rate limits in application.yml:

```yaml
rate-limiting:
  services:
    payment-service:
      endpoints:
        "/api/v1/payments/transfer":
          capacity: 5
          refill-tokens: 5
          refill-period-minutes: 10
```

## Error Handling

When rate limit is exceeded, the framework automatically returns:
```json
{
  "error": "RATE_LIMIT_EXCEEDED",
  "message": "Rate limit exceeded. Please try again later.",
  "retryAfter": 45,  // seconds
  "limit": 10,
  "remaining": 0,
  "resetTime": "2024-01-20T10:30:00Z"
}
```

## Monitoring

Rate limit metrics are automatically exposed via Micrometer:
- `rate_limit_requests_total` - Total requests
- `rate_limit_rejected_total` - Rejected requests
- `rate_limit_tokens_consumed` - Tokens consumed
- `rate_limit_bucket_capacity` - Current bucket capacity

## Testing Rate Limits

```java
@Test
@WithMockUser
public void testRateLimit() {
    // Make requests up to the limit
    for (int i = 0; i < 10; i++) {
        mockMvc.perform(post("/api/v1/resource"))
            .andExpect(status().isOk());
    }
    
    // Next request should be rate limited
    mockMvc.perform(post("/api/v1/resource"))
        .andExpect(status().isTooManyRequests())
        .andExpect(jsonPath("$.error").value("RATE_LIMIT_EXCEEDED"));
}
```

## Common Patterns

### 1. Tiered Limits by User Type
```java
@RateLimited(
    keyType = RateLimited.KeyType.CUSTOM,
    customKeyExpression = "@rateLimitKeyResolver.resolveUserTier(#authentication)"
)
public ResponseEntity<?> apiEndpoint() {
    // Different limits for free/premium/enterprise users
}
```

### 2. Bypass for Admin
```java
@RateLimited(
    keyType = RateLimited.KeyType.USER,
    prefix = "user",
    // Admins bypass rate limits via Spring Security
)
@PreAuthorize("hasRole('USER') or hasRole('ADMIN')")
public ResponseEntity<?> userAction() {
    // Rate limited for users, not for admins
}
```

### 3. Cost-Based Limiting
```java
@RateLimited(
    keyType = RateLimited.KeyType.USER,
    tokens = 5  // This operation costs 5 tokens
)
@PostMapping("/api/v1/expensive-operation")
public ResponseEntity<?> expensiveOperation() {
    // Consumes 5 tokens from user's bucket
}
```

## Troubleshooting

1. **Rate limits not working**: Ensure Redis is running and connected
2. **Too restrictive**: Check configuration in application.yml
3. **Key collision**: Use unique prefix for different limit types
4. **Testing locally**: Use different IPs or user accounts

## Default Limits Reference

| Service | Default Capacity | Refill Period |
|---------|-----------------|---------------|
| Payment | 50 req | 1 min |
| KYC | 30 req | 1 min |
| User Auth | 60 req | 1 min |
| Virtual Card | 40 req | 1 min |
| Merchant | 80 req | 1 min |
| Investment | 40 req | 1 min |
| Notification | 200 req | 1 min |

See `rate-limiting-defaults.yml` for complete configuration.