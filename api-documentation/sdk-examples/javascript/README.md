# Waqiti JavaScript/TypeScript SDK

Official JavaScript SDK for the Waqiti API with full TypeScript support.

## Installation

```bash
npm install @waqiti/sdk
# or
yarn add @waqiti/sdk
# or
pnpm add @waqiti/sdk
```

## Quick Start

```javascript
import { WaqitiClient } from '@waqiti/sdk';

// Initialize the client
const client = new WaqitiClient({
  apiKey: 'YOUR_API_KEY',
  environment: 'production' // or 'sandbox'
});

// Create a payment
const payment = await client.payments.create({
  type: 'p2p',
  amount: 5000, // $50.00 in cents
  currency: 'USD',
  recipient: {
    type: 'email',
    value: 'friend@example.com'
  },
  description: 'Lunch money'
});
```

## Configuration

### Client Options

```typescript
interface WaqitiClientOptions {
  apiKey: string;
  environment?: 'production' | 'staging' | 'sandbox';
  timeout?: number; // Request timeout in ms (default: 30000)
  maxRetries?: number; // Max retry attempts (default: 3)
  webhookSecret?: string; // For webhook signature verification
  onError?: (error: WaqitiError) => void; // Global error handler
}
```

### Environment Variables

```bash
WAQITI_API_KEY=your_api_key
WAQITI_ENVIRONMENT=production
WAQITI_WEBHOOK_SECRET=your_webhook_secret
```

## Core Features

### Payments

#### Create P2P Payment

```typescript
const payment = await client.payments.create({
  type: 'p2p',
  amount: 10000, // $100.00
  currency: 'USD',
  recipient: {
    type: 'username',
    value: '@johndoe'
  },
  description: 'Birthday gift',
  metadata: {
    occasion: 'birthday',
    note: 'Happy Birthday!'
  }
});
```

#### Create Merchant Payment

```typescript
const payment = await client.payments.create({
  type: 'merchant',
  amount: 2500, // $25.00
  currency: 'USD',
  recipient: {
    type: 'merchant_id',
    value: 'mer_abc123'
  },
  paymentMethodId: 'pm_card_xyz',
  savePaymentMethod: true
});
```

#### List Payments

```typescript
const payments = await client.payments.list({
  status: 'completed',
  fromDate: '2024-01-01',
  toDate: '2024-01-31',
  limit: 20,
  page: 1
});

// Iterate through all pages
for await (const payment of client.payments.listAll({ status: 'completed' })) {
  console.log(payment.id, payment.amount);
}
```

#### Get Payment Details

```typescript
const payment = await client.payments.get('pay_abc123');
console.log(payment.status, payment.amount);
```

### Money Requests

#### Create Money Request

```typescript
const request = await client.moneyRequests.create({
  amount: 30000, // $300.00 total
  currency: 'USD',
  recipients: [
    { type: 'email', value: 'alice@example.com' },
    { type: 'email', value: 'bob@example.com' },
    { type: 'phone', value: '+1234567890' }
  ],
  description: 'Split dinner bill',
  expiresAt: new Date(Date.now() + 7 * 24 * 60 * 60 * 1000) // 7 days
});
```

#### Respond to Money Request

```typescript
// Accept request
await client.moneyRequests.accept('req_abc123', {
  paymentMethodId: 'pm_card_xyz'
});

// Decline request
await client.moneyRequests.decline('req_abc123', {
  reason: 'Already paid in cash'
});
```

### Payment Methods

#### Add Card

```typescript
const card = await client.paymentMethods.createCard({
  token: 'tok_visa_4242', // Token from Stripe or payment processor
  billingAddress: {
    line1: '123 Main St',
    city: 'San Francisco',
    state: 'CA',
    postalCode: '94105',
    country: 'US'
  },
  setAsDefault: true
});
```

#### Add Bank Account

```typescript
const bankAccount = await client.paymentMethods.createBankAccount({
  accountNumber: '000123456789',
  routingNumber: '110000000',
  accountType: 'checking',
  accountHolderName: 'John Doe',
  setAsDefault: false
});
```

#### List Payment Methods

```typescript
const paymentMethods = await client.paymentMethods.list({
  type: 'card',
  status: 'active'
});
```

### Wallet Operations

#### Get Balance

```typescript
const balance = await client.wallet.getBalance();
console.log(balance.available); // { USD: 15000, EUR: 5000 }
console.log(balance.pending); // { USD: 2000 }
```

#### Add Funds

```typescript
const transaction = await client.wallet.addFunds({
  amount: 10000, // $100.00
  currency: 'USD',
  paymentMethodId: 'pm_card_xyz'
});
```

#### Withdraw Funds

```typescript
const withdrawal = await client.wallet.withdraw({
  amount: 5000, // $50.00
  currency: 'USD',
  destinationId: 'pm_bank_abc',
  description: 'Monthly withdrawal'
});
```

### Webhooks

#### Verify Webhook Signature

```typescript
import { verifyWebhookSignature } from '@waqiti/sdk';

app.post('/webhooks/waqiti', (req, res) => {
  const signature = req.headers['x-waqiti-signature'];
  const payload = req.rawBody;
  
  try {
    const event = verifyWebhookSignature(
      payload,
      signature,
      process.env.WAQITI_WEBHOOK_SECRET
    );
    
    // Process the event
    switch (event.type) {
      case 'payment.completed':
        console.log('Payment completed:', event.data.id);
        break;
      case 'payment.failed':
        console.log('Payment failed:', event.data.id);
        break;
    }
    
    res.json({ received: true });
  } catch (error) {
    console.error('Webhook verification failed:', error);
    res.status(400).json({ error: 'Invalid signature' });
  }
});
```

## Advanced Usage

### Error Handling

```typescript
import { WaqitiError, ValidationError, RateLimitError } from '@waqiti/sdk';

try {
  const payment = await client.payments.create({...});
} catch (error) {
  if (error instanceof ValidationError) {
    console.error('Validation failed:', error.errors);
  } else if (error instanceof RateLimitError) {
    console.error('Rate limit exceeded. Retry after:', error.retryAfter);
  } else if (error instanceof WaqitiError) {
    console.error('API error:', error.code, error.message);
  } else {
    console.error('Unexpected error:', error);
  }
}
```

### Retry Configuration

```typescript
const client = new WaqitiClient({
  apiKey: 'YOUR_API_KEY',
  maxRetries: 5,
  retryConfig: {
    retryDelay: (retryCount) => Math.min(1000 * 2 ** retryCount, 10000),
    retryCondition: (error) => {
      return error.code === 'NETWORK_ERROR' || error.status >= 500;
    }
  }
});
```

### Request Interceptors

```typescript
// Add request interceptor
client.interceptors.request.use((config) => {
  console.log('Request:', config.method, config.url);
  config.headers['X-Custom-Header'] = 'value';
  return config;
});

// Add response interceptor
client.interceptors.response.use(
  (response) => {
    console.log('Response:', response.status);
    return response;
  },
  (error) => {
    console.error('Error:', error);
    throw error;
  }
);
```

### Idempotency

```typescript
const payment = await client.payments.create({
  type: 'p2p',
  amount: 5000,
  currency: 'USD',
  recipient: { type: 'email', value: 'friend@example.com' }
}, {
  idempotencyKey: 'unique-key-123'
});
```

### Pagination Helpers

```typescript
// Manual pagination
let hasMore = true;
let page = 1;

while (hasMore) {
  const result = await client.payments.list({ page, perPage: 50 });
  
  for (const payment of result.data) {
    console.log(payment);
  }
  
  hasMore = result.hasNext;
  page++;
}

// Auto-pagination with async iterator
for await (const payment of client.payments.listAll()) {
  console.log(payment);
}
```

### TypeScript Support

```typescript
import { 
  Payment, 
  PaymentStatus, 
  CreatePaymentRequest,
  PaymentType 
} from '@waqiti/sdk';

// Type-safe payment creation
const request: CreatePaymentRequest = {
  type: PaymentType.P2P,
  amount: 5000,
  currency: 'USD',
  recipient: {
    type: 'email',
    value: 'friend@example.com'
  }
};

const payment: Payment = await client.payments.create(request);

// Type guards
if (payment.status === PaymentStatus.Completed) {
  console.log('Payment completed at:', payment.completedAt);
}
```

## Testing

### Mock Client

```typescript
import { createMockClient } from '@waqiti/sdk/testing';

const mockClient = createMockClient();

// Mock specific responses
mockClient.payments.create.mockResolvedValue({
  id: 'pay_test_123',
  status: 'completed',
  amount: 5000,
  currency: 'USD'
});

// Use in tests
const payment = await mockClient.payments.create({...});
expect(payment.id).toBe('pay_test_123');
```

### Test Helpers

```typescript
import { generateTestPayment, generateTestUser } from '@waqiti/sdk/testing';

const testPayment = generateTestPayment({
  amount: 10000,
  status: 'completed'
});

const testUser = generateTestUser({
  email: 'test@example.com'
});
```

## Best Practices

1. **Always handle errors**: Wrap API calls in try-catch blocks
2. **Use idempotency keys**: For critical operations like payments
3. **Implement webhooks**: For reliable payment status updates
4. **Cache payment methods**: To reduce API calls
5. **Use TypeScript**: For better type safety and IDE support
6. **Set appropriate timeouts**: Based on your use case
7. **Log API interactions**: For debugging and monitoring

## Examples

See the [examples](./examples) directory for complete working examples:

- [Basic Payment Flow](./examples/basic-payment.js)
- [Recurring Payments](./examples/recurring-payments.js)
- [International Transfers](./examples/international-transfer.js)
- [Webhook Handler](./examples/webhook-handler.js)
- [React Integration](./examples/react-integration.tsx)
- [Next.js Integration](./examples/nextjs-integration.tsx)

## Support

- 📚 [API Documentation](https://developers.example.com)
- 💬 [Discord Community](https://discord.gg/waqiti)
- 📧 [Email Support](mailto:sdk-support@example.com)
- 🐛 [Report Issues](https://github.com/waqiti/waqiti-js/issues)

## License

MIT License - see [LICENSE](./LICENSE) for details.