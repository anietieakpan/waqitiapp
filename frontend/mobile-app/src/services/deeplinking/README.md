# Enhanced Deep Linking System

Advanced deep linking implementation for the Waqiti mobile app with pattern matching, context-aware routing, and comprehensive error handling.

## Features

### 🔗 Advanced Routing
- **Pattern Matching**: Support for parameterized URLs with optional parameters
- **Context-Aware Handling**: Intelligent routing based on user state and permissions
- **Fallback Support**: Graceful degradation and error handling
- **Legacy Compatibility**: Seamless integration with existing DeepLinkingService

### 🛡️ Security & Authentication
- **Authentication Requirements**: Route-level authentication control
- **Permission Checking**: Fine-grained permission validation
- **Privacy Controls**: Respect user privacy settings
- **Secure Parameter Handling**: URL encoding and validation

### 📊 Analytics & Monitoring
- **Comprehensive Tracking**: Route success/failure analytics
- **Performance Monitoring**: Latency and error rate tracking
- **User Journey Analysis**: Deep link source and campaign tracking
- **Debug Information**: Detailed logging and route testing

## Architecture

### Core Components

```
┌─────────────────────────────────────────────────────────────┐
│                 Enhanced Deep Linking System                │
├─────────────────────────────────────────────────────────────┤
│  ┌─────────────────┐  ┌─────────────────┐  ┌──────────────┐ │
│  │ DeepLinkManager │  │ DeepLinkRouter  │  │ Legacy       │ │
│  │ (Orchestrator)  │  │ (New System)    │  │ Service      │ │
│  └─────────────────┘  └─────────────────┘  └──────────────┘ │
├─────────────────────────────────────────────────────────────┤
│  ┌─────────────────┐  ┌─────────────────┐  ┌──────────────┐ │
│  │ Pattern         │  │ Authentication  │  │ Analytics    │ │
│  │ Matching        │  │ & Permissions   │  │ Tracking     │ │
│  └─────────────────┘  └─────────────────┘  └──────────────┘ │
└─────────────────────────────────────────────────────────────┘
```

### Route Patterns

The system supports flexible URL patterns with parameters:

```typescript
// Basic routes
'/pay/:merchantId'                    // waqiti://pay/merchant123
'/user/:userId'                       // waqiti://user/user456

// Optional parameters  
'/settings/:section?'                 // waqiti://settings or waqiti://settings/security
'/rewards/:category?'                 // waqiti://rewards or waqiti://rewards/cashback

// Complex patterns
'/qr/:action/:data'                   // waqiti://qr/pay/encoded-data
'/split/:splitId'                     // waqiti://split/split789
```

## Quick Start

### Basic Setup

```typescript
import DeepLinkManager from '@/services/deeplinking';

// Initialize with navigation reference
const navigationRef = useNavigationContainerRef();

useEffect(() => {
  const initializeDeepLinking = async () => {
    await DeepLinkManager.initialize(navigationRef);
  };
  
  initializeDeepLinking();
}, [navigationRef]);
```

### Handling Deep Links

```typescript
// Handle a deep link URL
const handleDeepLink = async (url: string) => {
  const result = await DeepLinkManager.handleDeepLink(url, {
    source: 'qr_code',
    campaign: 'summer_promo'
  });
  
  if (result.success) {
    console.log(`Navigated to: ${result.route}`);
  } else {
    console.error(`Failed: ${result.errorMessage}`);
  }
};
```

### Creating Deep Links

```typescript
// Generate a deep link URL
const paymentUrl = DeepLinkManager.generateDeepLink(
  '/pay/:merchantId',
  { merchantId: 'coffee_shop_123' },
  { 
    source: 'share_button',
    campaign: 'referral_bonus',
    utmParams: { utm_medium: 'social', utm_content: 'payment_share' }
  }
);

// Create dynamic links via legacy service
const paymentLink = await DeepLinkManager.createPaymentLink({
  amount: 25.50,
  currency: 'USD',
  merchantId: 'coffee_shop_123',
  description: 'Coffee and pastry'
});
```

## Configuration

### Manager Configuration

```typescript
const manager = DeepLinkManager.getInstance({
  enableLegacyMode: true,      // Enable legacy DeepLinkingService
  preferNewRouter: true,       // Prefer new router over legacy
  trackingEnabled: true        // Enable analytics tracking
});
```

### Custom Route Registration

```typescript
// Register a custom route
DeepLinkManager.registerRoute({
  pattern: '/custom/:action/:id',
  requiresAuth: true,
  permission: 'admin',
  handler: async (params, context) => {
    const { action, id } = params;
    
    switch (action) {
      case 'approve':
        context.navigation.navigate('AdminApproval', { itemId: id });
        break;
      case 'reject':
        context.navigation.navigate('AdminRejection', { itemId: id });
        break;
    }
    
    return { success: true, route: 'Admin', params };
  },
  metadata: {
    category: 'admin',
    description: 'Admin actions',
    public: false
  }
});
```

## Supported Routes

### Payment Routes

| Pattern | Description | Auth Required |
|---------|-------------|---------------|
| `/pay/:merchantId` | Pay a merchant | Yes |
| `/send/:userId` | Send money to user | Yes |
| `/request/:requestId` | Handle payment request | Yes |

### Social Routes

| Pattern | Description | Auth Required |
|---------|-------------|---------------|
| `/user/:userId` | View user profile | No* |
| `/split/:splitId` | Join split bill | Yes |
| `/split/create` | Create split bill | Yes |

### Utility Routes

| Pattern | Description | Auth Required |
|---------|-------------|---------------|
| `/qr/:action/:data` | QR code actions | Varies |
| `/help/:topic?` | Help and support | No |
| `/settings/:section?` | App settings | Yes |

*Subject to privacy settings

### Marketing Routes

| Pattern | Description | Auth Required |
|---------|-------------|---------------|
| `/referral/:code` | Referral signup | No |
| `/promo/:promoId` | Promotion details | No |
| `/merchant/:merchantId` | Merchant details | No |

## Error Handling

### Error Types

```typescript
interface DeepLinkResult {
  success: boolean;
  route?: string;
  params?: any;
  errorCode?: string;
  errorMessage?: string;
  requiresUserAction?: boolean;
  actionType?: 'confirm' | 'authenticate' | 'permissions' | 'update';
  actionData?: any;
}
```

### Common Error Codes

- `INVALID_URL`: Malformed URL
- `ROUTE_NOT_FOUND`: No matching route pattern
- `MERCHANT_NOT_FOUND`: Merchant doesn't exist
- `USER_NOT_FOUND`: User doesn't exist
- `PERMISSION_DENIED`: Insufficient permissions
- `AUTHENTICATION_REQUIRED`: User must log in
- `PROFILE_PRIVATE`: Private profile access denied

### Error Recovery

```typescript
const result = await DeepLinkManager.handleDeepLink(url);

if (!result.success) {
  switch (result.errorCode) {
    case 'AUTHENTICATION_REQUIRED':
      // User will be automatically redirected to login
      break;
      
    case 'PERMISSION_DENIED':
      Alert.alert('Access Denied', result.errorMessage);
      break;
      
    case 'ROUTE_NOT_FOUND':
      // Fallback to home screen
      navigation.navigate('Home');
      break;
      
    default:
      console.error('Deep link error:', result.errorMessage);
  }
}
```

## Advanced Features

### Context-Aware Routing

The system provides rich context information to route handlers:

```typescript
interface DeepLinkContext {
  navigation: NavigationContainerRef<any>;
  user?: any;
  isAuthenticated: boolean;
  deviceInfo: any;
  source: 'app' | 'web' | 'sms' | 'email' | 'qr' | 'nfc' | 'social';
  campaign?: string;
  referrer?: string;
}
```

### Permission Checking

Routes can specify required permissions:

```typescript
{
  pattern: '/admin/:action',
  requiresAuth: true,
  permission: 'admin_access',
  handler: adminHandler
}
```

### Privacy Validation

User profile routes respect privacy settings:

```typescript
// Private profiles are only accessible to friends
if (user.privacy?.profileVisibility === 'private' && 
    !user.friends?.includes(context.user?.id)) {
  return { success: false, errorCode: 'PROFILE_PRIVATE' };
}
```

### Navigation Queuing

Routes are queued when navigation isn't ready:

```typescript
// Routes are automatically processed when navigation becomes available
const result = await router.route(url, context);

if (result.errorCode === 'NAVIGATION_NOT_READY') {
  // Route is queued and will be processed later
}
```

## Testing

### Route Testing

```typescript
// Test if a URL would match any route
const testResult = DeepLinkManager.testURL('waqiti://pay/merchant123');

console.log('Matches:', testResult.matches);
console.log('Route:', testResult.route);
console.log('Params:', testResult.params);
```

### Unit Tests

The system includes comprehensive unit tests:

```bash
# Run deep linking tests
npm test -- --testPathPattern=deeplinking

# Run specific test files
npm test DeepLinkRouter.test.ts
npm test DeepLinkManager.test.ts
```

### Integration Testing

```typescript
describe('Deep Link Integration', () => {
  test('should handle complete payment flow', async () => {
    const url = 'waqiti://pay/merchant123?amount=25.50';
    
    // Mock API responses
    mockApiService.getMerchant.mockResolvedValue(mockMerchant);
    
    const result = await DeepLinkManager.handleDeepLink(url);
    
    expect(result.success).toBe(true);
    expect(mockNavigation.navigate).toHaveBeenCalledWith('Payment', 
      expect.objectContaining({
        merchantId: 'merchant123',
        amount: 25.50
      })
    );
  });
});
```

## Analytics & Monitoring

### Tracked Events

The system automatically tracks deep link usage:

```typescript
// Deep link attempt
{
  event: 'deep_link_attempt',
  url: 'waqiti://pay/merchant123',
  source: 'qr_code',
  campaign: 'summer_promo',
  user_id: 'user123'
}

// Successful routing
{
  event: 'deep_link_routed',
  url: 'waqiti://pay/merchant123',
  pattern: '/pay/:merchantId',
  success: true,
  route: 'Payment',
  latency_ms: 150
}

// Routing errors
{
  event: 'deep_link_error',
  url: 'waqiti://invalid/route',
  error_code: 'ROUTE_NOT_FOUND',
  error_message: 'No matching route found'
}
```

### Performance Monitoring

```typescript
// Route execution time tracking
const startTime = Date.now();
const result = await router.route(url, context);
const latency = Date.now() - startTime;

await AnalyticsService.track('deep_link_routed', {
  ...routeData,
  latency_ms: latency
});
```

## Debugging

### Route Inspection

```typescript
// Get all registered routes
const routes = DeepLinkManager.getRoutes();
console.log('Registered routes:', routes);

// Test URL matching
const testResult = DeepLinkManager.testURL(url);
console.log('URL test result:', testResult);
```

### Debug Logging

Enable detailed logging for troubleshooting:

```typescript
// Enable debug mode in development
if (__DEV__) {
  console.log('Deep link context:', context);
  console.log('Route matching result:', matchResult);
  console.log('Handler execution result:', handlerResult);
}
```

## Migration Guide

### From Legacy Service

To migrate from the legacy DeepLinkingService:

1. **Immediate**: Use DeepLinkManager which provides compatibility
2. **Gradual**: Enable new router with legacy fallback
3. **Complete**: Disable legacy mode once all routes are migrated

```typescript
// Step 1: Drop-in replacement
import DeepLinkManager from '@/services/deeplinking';
// Old: import DeepLinkingService from '@/services/DeepLinkingService';

// Step 2: Configure with legacy fallback
const manager = DeepLinkManager.getInstance({
  enableLegacyMode: true,
  preferNewRouter: true
});

// Step 3: Disable legacy when ready
manager.updateConfig({ enableLegacyMode: false });
```

### Custom Route Migration

```typescript
// Old: Switch statement in legacy service
switch (data.type) {
  case 'custom':
    NavigationService.navigate('CustomScreen', data.params);
    break;
}

// New: Registered route handler
DeepLinkManager.registerRoute({
  pattern: '/custom/:action',
  handler: async (params, context) => {
    context.navigation.navigate('CustomScreen', params);
    return { success: true, route: 'CustomScreen', params };
  }
});
```

## Best Practices

### Route Design

1. **Consistent Patterns**: Use predictable URL structures
2. **Parameter Validation**: Validate all URL parameters
3. **Error Handling**: Provide meaningful error messages
4. **Security**: Check permissions and authentication

### Performance

1. **Lazy Loading**: Load heavy screens only when needed
2. **Caching**: Cache API responses for repeated requests
3. **Batch Operations**: Combine multiple API calls when possible
4. **Monitoring**: Track route performance metrics

### User Experience

1. **Fast Navigation**: Minimize route processing time
2. **Clear Feedback**: Show loading states and error messages
3. **Graceful Degradation**: Handle failures gracefully
4. **Context Preservation**: Maintain user state during navigation

## Troubleshooting

### Common Issues

**Issue**: Routes not matching
```typescript
// Check pattern syntax
const testResult = DeepLinkManager.testURL(url);
console.log('Pattern match:', testResult);
```

**Issue**: Navigation not working
```typescript
// Ensure navigation ref is set
if (!navigationRef.current) {
  console.error('Navigation ref not ready');
}
```

**Issue**: Authentication loops
```typescript
// Check authentication state
const isAuth = await AuthService.isAuthenticated();
console.log('Auth state:', isAuth);
```

### Debug Tools

1. **Route Testing**: Use `testURL()` to verify patterns
2. **Context Inspection**: Log context object for debugging
3. **Analytics Review**: Check tracked events for insights
4. **Performance Monitoring**: Monitor route execution times

## Contributing

### Adding New Routes

1. Define the route pattern and handler
2. Add comprehensive tests
3. Update documentation
4. Test with various URL formats

### Testing Guidelines

1. Test successful routing scenarios
2. Test error conditions and edge cases
3. Verify authentication and permission checks
4. Test URL generation and parsing
5. Test analytics tracking

## License

This enhanced deep linking system is part of the Waqiti mobile application and follows the same licensing terms.