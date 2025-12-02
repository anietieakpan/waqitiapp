# Certificate Pinning Implementation Guide

This guide describes the comprehensive certificate pinning implementation for the Waqiti mobile app, providing protection against man-in-the-middle (MITM) attacks.

## Overview

The certificate pinning implementation consists of three layers:

1. **JavaScript Implementation**: Pure JavaScript certificate validation using SHA-256 hashes
2. **Native Implementation**: Platform-specific implementations for iOS and Android
3. **Network Client Integration**: Axios-based HTTP client with automatic certificate validation

## Architecture

```
┌─────────────────────────────────────────────────────────┐
│                    Mobile App                           │
├─────────────────────────────────────────────────────────┤
│                SecureNetworkClient                      │
│                  (Axios + Pinning)                      │
├─────────────────────────────────────────────────────────┤
│          CertificatePinningService (JS)                │
├─────────────────────────────────────────────────────────┤
│              Native Certificate Pinning                  │
│    ┌─────────────────┐    ┌─────────────────┐         │
│    │   iOS (Swift)   │    │ Android (Java)  │         │
│    └─────────────────┘    └─────────────────┘         │
└─────────────────────────────────────────────────────────┘
```

## Implementation Details

### 1. JavaScript Certificate Pinning

**File**: `CertificatePinningService.ts`

Features:
- SHA-256 based certificate pinning
- Configurable enforcement modes (strict, report, disabled)
- Automatic pin updates from remote configuration
- Offline support with pin caching
- Security event reporting

### 2. Native iOS Implementation

**Files**: 
- `WQTCertificatePinning.h`
- `WQTCertificatePinning.m`

Features:
- NSURLSession delegate-based certificate validation
- Keychain storage for persistent pins
- TLS 1.2+ enforcement
- Certificate chain validation
- Security event reporting to JavaScript

### 3. Native Android Implementation

**Files**:
- `CertificatePinningModule.java`
- `CertificatePinningPackage.java`

Features:
- OkHttp certificate pinner integration
- SHA-256 based pin validation
- Custom trust manager
- TLS configuration
- Security event reporting to JavaScript

### 4. Secure Network Client

**File**: `SecureNetworkClient.ts`

Features:
- Axios interceptors for automatic certificate validation
- Request/response encryption for sensitive endpoints
- HMAC-based request signing
- Automatic token refresh
- Network security monitoring

## Configuration

### Default Pins

The implementation includes default pins for Waqiti services:

```typescript
{
  "api.example.com": [
    "sha256/AAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAA=",
    "sha256/BBBBBBBBBBBBBBBBBBBBBBBBBBBBBBBBBBBBBBBBBBB="
  ],
  "auth.example.com": [
    "sha256/EEEEEEEEEEEEEEEEEEEEEEEEEEEEEEEEEEEEEEEEEEE=",
    "sha256/FFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFF="
  ],
  "payments.example.com": [
    "sha256/IIIIIIIIIIIIIIIIIIIIIIIIIIIIIIIIIIIIIIIIIII=",
    "sha256/JJJJJJJJJJJJJJJJJJJJJJJJJJJJJJJJJJJJJJJJJJJ="
  ]
}
```

**Note**: Replace these placeholder pins with actual certificate pins before production deployment.

### Enforcement Modes

1. **strict**: Blocks connections if pinning fails (default)
2. **report**: Allows connections but reports failures
3. **disabled**: Certificate pinning is disabled

## Usage

### Basic Usage

```typescript
import { initializeSecurityServices } from '@services/security';

// Initialize on app startup
await initializeSecurityServices();

// Use secure network client for API calls
import { SecureNetworkClient } from '@services/security';

const client = SecureNetworkClient.getInstance();
const response = await client.get('/api/v1/user/profile');
```

### Manual Certificate Validation

```typescript
import { CertificatePinningService } from '@services/security';

const pinningService = CertificatePinningService.getInstance();
const result = await pinningService.validateCertificate(
  'api.example.com',
  certificateChain
);

if (!result.valid) {
  console.error('Certificate validation failed:', result.error);
}
```

### Testing Certificate Pinning

```typescript
import { testCertificatePinning } from '@services/security';

const results = await testCertificatePinning();
console.log('Pinning test results:', results);
```

## iOS Setup

1. Add to `ios/Podfile`:
```ruby
pod 'TrustKit', '~> 1.7.0'  # Optional: For additional features
```

2. Import in `AppDelegate.m`:
```objc
#import "WQTCertificatePinning.h"
```

3. Configure Info.plist:
```xml
<key>NSAppTransportSecurity</key>
<dict>
    <key>NSPinnedDomains</key>
    <dict>
        <key>api.example.com</key>
        <dict>
            <key>NSIncludesSubdomains</key>
            <true/>
            <key>NSPinnedLeafIdentities</key>
            <array>
                <dict>
                    <key>SPKI-SHA256-BASE64</key>
                    <string>AAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAA=</string>
                </dict>
            </array>
        </dict>
    </dict>
</dict>
```

## Android Setup

1. Add to `android/app/build.gradle`:
```gradle
dependencies {
    implementation 'com.squareup.okhttp3:okhttp:4.10.0'
    implementation 'com.squareup.okhttp3:okhttp-tls:4.10.0'
}
```

2. Register package in `MainApplication.java`:
```java
import com.waqiti.certificatepinning.CertificatePinningPackage;

@Override
protected List<ReactPackage> getPackages() {
    return Arrays.<ReactPackage>asList(
        new MainReactPackage(),
        new CertificatePinningPackage()
    );
}
```

3. Add network security config in `res/xml/network_security_config.xml`:
```xml
<?xml version="1.0" encoding="utf-8"?>
<network-security-config>
    <domain-config>
        <domain includeSubdomains="true">api.example.com</domain>
        <pin-set expiration="2025-01-01">
            <pin digest="SHA-256">AAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAA=</pin>
            <pin digest="SHA-256">BBBBBBBBBBBBBBBBBBBBBBBBBBBBBBBBBBBBBBBBBBB=</pin>
        </pin-set>
    </domain-config>
</network-security-config>
```

## Generating Certificate Pins

### Using OpenSSL

```bash
# Get certificate
echo | openssl s_client -servername api.example.com -connect api.example.com:443 2>/dev/null | \
  openssl x509 -pubkey -noout | \
  openssl pkey -pubin -outform der | \
  openssl dgst -sha256 -binary | \
  openssl enc -base64
```

### Using Chrome

1. Open Chrome DevTools
2. Go to Security tab
3. View certificate
4. Export public key
5. Calculate SHA-256 hash

## Security Considerations

### Pin Rotation

1. Always include at least 2 pins (primary + backup)
2. Include pins for intermediate certificates
3. Plan for certificate rotation
4. Set appropriate expiration dates

### Failure Handling

1. Implement graceful degradation
2. Cache valid pins locally
3. Report failures to security monitoring
4. Have a pin update mechanism

### Testing

1. Test with correct pins
2. Test with incorrect pins
3. Test certificate rotation
4. Test offline scenarios
5. Test pin update mechanism

## Monitoring

The implementation includes automatic security event reporting:

```typescript
// Subscribe to pinning failures
import { nativePinning } from '@services/security';

const unsubscribe = nativePinning.onPinningFailure((event) => {
  console.warn('Certificate pinning failure:', event);
  // Send to analytics/monitoring
});
```

## Common Issues

### Issue: Certificate validation always fails

**Solution**: Ensure pins are correctly generated and match the server's certificate.

### Issue: App crashes on network requests

**Solution**: Check enforcement mode and ensure graceful error handling.

### Issue: Pins not persisting across app restarts

**Solution**: Verify keychain (iOS) or SharedPreferences (Android) permissions.

## Best Practices

1. **Use Multiple Pins**: Include primary and backup pins
2. **Pin to Intermediate CA**: More stable than leaf certificates
3. **Monitor Failures**: Set up alerting for pinning failures
4. **Plan Updates**: Have a mechanism to update pins without app updates
5. **Test Thoroughly**: Test all scenarios including failures
6. **Document Pins**: Keep a secure record of all pins and their expiration

## References

- [OWASP Certificate Pinning Guide](https://owasp.org/www-community/controls/Certificate_and_Public_Key_Pinning)
- [Android Network Security Configuration](https://developer.android.com/training/articles/security-config)
- [iOS App Transport Security](https://developer.apple.com/documentation/security/preventing_insecure_network_connections)
- [RFC 7469 - HTTP Public Key Pinning](https://tools.ietf.org/html/rfc7469)