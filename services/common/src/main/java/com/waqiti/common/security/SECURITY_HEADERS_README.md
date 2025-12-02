# Security Headers Implementation Guide

This guide describes the comprehensive security headers implementation for all Waqiti services.

## Overview

The security headers implementation provides automatic configuration of OWASP-recommended security headers across all Waqiti microservices. It includes:

- **Automatic Configuration**: Security headers are automatically applied to all services
- **Environment-Specific**: Different configurations for development, staging, and production
- **Service Customization**: Services can override or extend the default configuration
- **Health Monitoring**: Built-in health indicators to monitor security header compliance
- **PCI Compliance**: Specific headers for payment services to meet PCI DSS requirements

## Quick Start

### 1. Default Configuration (Automatic)

Security headers are automatically configured for all services that include the `waqiti-common` dependency. No additional configuration is required for basic protection.

### 2. Import Default Configuration

To use the default configuration explicitly, add this to your `application.yml`:

```yaml
spring:
  config:
    import: classpath:security-headers-defaults.yml
```

### 3. Service-Specific Configuration

Override specific headers in your service's `application.yml`:

```yaml
waqiti:
  security:
    headers:
      service-name: "payment-service"
      content-security-policy: |
        default-src 'self';
        script-src 'self' https://js.stripe.com;
        # ... custom CSP for your service
```

## Security Headers Included

### Required Headers

1. **Strict-Transport-Security (HSTS)**
   - Forces HTTPS connections
   - Default: `max-age=31536000; includeSubDomains; preload`

2. **X-Frame-Options**
   - Prevents clickjacking attacks
   - Default: `DENY`

3. **X-Content-Type-Options**
   - Prevents MIME type sniffing
   - Default: `nosniff`

4. **X-XSS-Protection**
   - Legacy XSS protection for older browsers
   - Default: `1; mode=block`

5. **Content-Security-Policy (CSP)**
   - Controls resource loading
   - Includes nonce-based script execution
   - Comprehensive default policy

6. **Referrer-Policy**
   - Controls referrer information
   - Default: `strict-origin-when-cross-origin`

7. **Permissions-Policy**
   - Controls browser features
   - Restrictive by default

### Additional Headers

- **Cross-Origin Headers**: COEP, COOP, CORP
- **Certificate Transparency**: Expect-CT
- **Network Error Logging**: NEL
- **Report-To**: For security violation reporting
- **Cache-Control**: Prevents caching of sensitive data

## Implementation Examples

### 1. Payment Service Example

```java
@Configuration
@EnableWebSecurity
public class PaymentSecurityHeadersConfig {
    
    @Bean
    public SecurityFilterChain paymentSecurityFilterChain(HttpSecurity http) throws Exception {
        // Apply common security headers
        securityHeadersConfiguration.configureSecurityHeaders(http);
        
        // Add payment-specific headers
        http.headers(headers -> headers
            .addHeaderWriter((request, response) -> {
                response.setHeader("X-PCI-Compliance", "PCI-DSS-3.2.1");
                response.setHeader("X-Payment-Security-Version", "2.0");
            })
        );
        
        return http.build();
    }
}
```

### 2. API Gateway Example

```java
@Bean
public GlobalFilter securityHeadersGlobalFilter() {
    return (exchange, chain) -> {
        return chain.filter(exchange).then(Mono.fromRunnable(() -> {
            ServerHttpResponse response = exchange.getResponse();
            HttpHeaders headers = response.getHeaders();
            
            // Add comprehensive security headers
            headers.set("Strict-Transport-Security", "max-age=31536000; includeSubDomains; preload");
            headers.set("X-Frame-Options", "DENY");
            // ... other headers
        }));
    };
}
```

## Configuration Properties

### Basic Properties

```yaml
waqiti:
  security:
    headers:
      enabled: true                    # Enable/disable security headers
      service-name: "my-service"       # Service identifier
      api-version: "1.0"              # API version header
```

### HSTS Configuration

```yaml
waqiti:
  security:
    headers:
      hsts-max-age: 31536000          # HSTS max age in seconds
      hsts-include-sub-domains: true   # Include subdomains
      hsts-preload: true              # HSTS preload list
```

### CSP Configuration

```yaml
waqiti:
  security:
    headers:
      content-security-policy: |
        default-src 'self';
        script-src 'self' 'strict-dynamic' 'nonce-{nonce}';
        # ... full CSP policy
```

### Reporting Configuration

```yaml
waqiti:
  security:
    headers:
      enable-security-reporting: true
      csp-report-endpoint: "https://csp.example.com/report"
      ct-report-endpoint: "https://ct.example.com/report"
      nel-report-endpoint: "https://nel.example.com/report"
```

## Health Monitoring

The security headers implementation includes a health indicator:

```bash
curl http://localhost:8080/actuator/health/securityHeaders
```

Response:
```json
{
  "status": "UP",
  "details": {
    "enabled": true,
    "securityScore": "95%",
    "hsts": {
      "maxAge": 31536000,
      "includeSubDomains": true,
      "preload": true,
      "valid": true
    },
    "csp": {
      "configured": true,
      "hasDefaultSrc": true,
      "hasScriptSrc": true,
      "hasObjectSrc": true,
      "hasFrameAncestors": true,
      "hasUpgradeInsecureRequests": true,
      "hasReportUri": true
    }
  }
}
```

## Environment-Specific Configuration

### Development Environment

Relaxed policies for local development:

```yaml
spring:
  profiles:
    active: development

waqiti:
  security:
    headers:
      frame-deny-enabled: false
      content-security-policy: |
        default-src 'self' localhost:*;
        script-src 'self' 'unsafe-inline' 'unsafe-eval' localhost:*;
```

### Production Environment

Strict security policies:

```yaml
spring:
  profiles:
    active: production

waqiti:
  security:
    headers:
      frame-deny-enabled: true
      hsts-preload: true
      enable-security-reporting: true
```

## Testing Security Headers

### 1. Manual Testing

```bash
# Test security headers
curl -I https://api.example.com/health

# Check specific header
curl -I https://api.example.com/health | grep "Strict-Transport-Security"
```

### 2. Automated Testing

```java
@Test
public void testSecurityHeaders() {
    mockMvc.perform(get("/api/test"))
        .andExpect(header().string("X-Frame-Options", "DENY"))
        .andExpect(header().string("X-Content-Type-Options", "nosniff"))
        .andExpect(header().exists("Strict-Transport-Security"));
}
```

## Troubleshooting

### Headers Not Applied

1. Check if security headers are enabled:
   ```yaml
   waqiti.security.headers.enabled: true
   ```

2. Verify the filter is registered:
   ```bash
   curl http://localhost:8080/actuator/beans | grep SecurityHeadersFilter
   ```

### CSP Violations

1. Check browser console for CSP violations
2. Review CSP report endpoint logs
3. Adjust CSP policy as needed

### Performance Impact

Security headers add minimal overhead (<1ms per request). If experiencing issues:

1. Disable security reporting in non-production environments
2. Simplify CSP policy
3. Use CDN for static resources

## Security Best Practices

1. **Never disable in production**: Always keep security headers enabled
2. **Regular reviews**: Review and update CSP policy quarterly
3. **Monitor reports**: Set up alerting for CSP and CT violations
4. **Test thoroughly**: Test CSP changes in staging before production
5. **Document exceptions**: Document any relaxed policies with justification

## Compliance

This implementation helps meet requirements for:

- **OWASP Top 10**: Addresses multiple security risks
- **PCI DSS**: Required headers for payment processing
- **GDPR**: Privacy and security headers
- **SOC 2**: Security controls for service organizations

## References

- [OWASP Secure Headers Project](https://owasp.org/www-project-secure-headers/)
- [MDN Web Docs - HTTP Headers](https://developer.mozilla.org/en-US/docs/Web/HTTP/Headers)
- [Content Security Policy Reference](https://content-security-policy.com/)
- [Security Headers Scanner](https://securityheaders.com/)