# Webhook Implementation Guide

This guide covers how to implement and handle Waqiti webhooks in your application.

## Overview

Webhooks allow your application to receive real-time notifications about events that occur in Waqiti. Instead of polling the API for changes, webhooks push data to your endpoint as events happen.

## Available Events

### Payment Events
- `payment.created` - A new payment has been initiated
- `payment.processing` - Payment is being processed
- `payment.completed` - Payment was successful
- `payment.failed` - Payment failed
- `payment.cancelled` - Payment was cancelled
- `payment.refunded` - Payment was refunded

### Money Request Events
- `money_request.created` - New money request created
- `money_request.paid` - Money request was paid
- `money_request.declined` - Money request was declined
- `money_request.expired` - Money request expired
- `money_request.cancelled` - Money request was cancelled

### Payment Method Events
- `payment_method.created` - New payment method added
- `payment_method.updated` - Payment method updated
- `payment_method.removed` - Payment method removed
- `payment_method.verification_completed` - Bank account verified

### Wallet Events
- `wallet.funded` - Funds added to wallet
- `wallet.withdrawn` - Funds withdrawn from wallet
- `wallet.low_balance` - Wallet balance below threshold

## Setting Up Webhooks

### 1. Create Webhook Endpoint

First, create an endpoint in your application to receive webhooks:

```javascript
// Node.js + Express example
app.post('/webhooks/waqiti', express.raw({ type: 'application/json' }), (req, res) => {
  const signature = req.headers['x-waqiti-signature'];
  const timestamp = req.headers['x-waqiti-timestamp'];
  const payload = req.body;
  
  // Verify and process webhook
  // ...
  
  res.json({ received: true });
});
```

### 2. Register Webhook Endpoint

Register your endpoint with Waqiti:

```bash
curl -X POST https://api.example.com/v1/webhooks \
  -H "Authorization: Bearer YOUR_API_KEY" \
  -H "Content-Type: application/json" \
  -d '{
    "url": "https://yourapp.com/webhooks/waqiti",
    "events": [
      "payment.completed",
      "payment.failed",
      "money_request.created"
    ],
    "description": "Production webhook"
  }'
```

### 3. Handle Webhook Events

Process incoming webhook events:

```javascript
async function handleWebhook(event) {
  switch (event.type) {
    case 'payment.completed':
      await handlePaymentCompleted(event.data);
      break;
    case 'payment.failed':
      await handlePaymentFailed(event.data);
      break;
    case 'money_request.created':
      await handleMoneyRequestCreated(event.data);
      break;
    default:
      console.log('Unhandled event type:', event.type);
  }
}
```

## Security

### Signature Verification

All webhooks are signed using HMAC-SHA256. **Always verify the signature** to ensure the webhook is from Waqiti:

```javascript
const crypto = require('crypto');

function verifyWebhookSignature(payload, signature, secret, timestamp) {
  // Check timestamp to prevent replay attacks (5 minute window)
  const currentTime = Math.floor(Date.now() / 1000);
  if (Math.abs(currentTime - parseInt(timestamp)) > 300) {
    throw new Error('Webhook timestamp too old');
  }
  
  // Compute expected signature
  const signedPayload = `${timestamp}.${payload}`;
  const expectedSignature = crypto
    .createHmac('sha256', secret)
    .update(signedPayload)
    .digest('hex');
  
  // Compare signatures
  const providedSignature = signature.replace('sha256=', '');
  if (!crypto.timingSafeEqual(
    Buffer.from(expectedSignature),
    Buffer.from(providedSignature)
  )) {
    throw new Error('Invalid webhook signature');
  }
  
  return true;
}
```

### Best Practices

1. **Use HTTPS**: Always use HTTPS for webhook endpoints
2. **Verify signatures**: Never process webhooks without verification
3. **Idempotency**: Handle duplicate webhooks gracefully
4. **Quick response**: Respond with 2xx status quickly, process asynchronously
5. **Retry handling**: Implement proper retry logic
6. **Logging**: Log all webhook events for debugging

## Event Payload Structure

### Common Fields

All webhook events include these fields:

```json
{
  "id": "evt_1234567890",
  "type": "payment.completed",
  "created": "2024-01-20T10:30:00Z",
  "data": {
    // Event-specific data
  },
  "request_id": "req_abc123", // ID of triggering API request
  "api_version": "2024-01-01"
}
```

### Payment Event Data

```json
{
  "id": "evt_1234567890",
  "type": "payment.completed",
  "created": "2024-01-20T10:30:00Z",
  "data": {
    "id": "pay_abc123",
    "amount": 5000,
    "currency": "USD",
    "status": "completed",
    "sender": {
      "id": "usr_123",
      "email": "sender@example.com",
      "name": "John Doe"
    },
    "recipient": {
      "id": "usr_456",
      "email": "recipient@example.com",
      "name": "Jane Smith"
    },
    "completed_at": "2024-01-20T10:29:55Z",
    "description": "Lunch money",
    "metadata": {
      "order_id": "12345"
    }
  }
}
```

## Implementation Examples

### Node.js + Express

```javascript
const express = require('express');
const crypto = require('crypto');

const app = express();
const WEBHOOK_SECRET = process.env.WAQITI_WEBHOOK_SECRET;

app.post('/webhooks/waqiti', 
  express.raw({ type: 'application/json' }), 
  async (req, res) => {
    try {
      // Verify signature
      const signature = req.headers['x-waqiti-signature'];
      const timestamp = req.headers['x-waqiti-timestamp'];
      
      verifyWebhookSignature(
        req.body.toString(),
        signature,
        WEBHOOK_SECRET,
        timestamp
      );
      
      // Parse event
      const event = JSON.parse(req.body);
      
      // Process event asynchronously
      setImmediate(() => processWebhookEvent(event));
      
      // Respond immediately
      res.json({ received: true });
      
    } catch (error) {
      console.error('Webhook error:', error);
      res.status(400).json({ error: error.message });
    }
  }
);

async function processWebhookEvent(event) {
  console.log(`Processing ${event.type} event:`, event.id);
  
  try {
    switch (event.type) {
      case 'payment.completed':
        await updateOrderStatus(event.data.metadata.order_id, 'paid');
        await sendPaymentConfirmation(event.data);
        break;
        
      case 'payment.failed':
        await updateOrderStatus(event.data.metadata.order_id, 'failed');
        await notifyPaymentFailure(event.data);
        break;
        
      case 'money_request.created':
        await sendMoneyRequestNotification(event.data);
        break;
    }
    
    // Log successful processing
    await logWebhookEvent(event.id, 'processed');
    
  } catch (error) {
    console.error('Error processing webhook:', error);
    await logWebhookEvent(event.id, 'failed', error.message);
  }
}
```

### Python + Flask

```python
from flask import Flask, request, jsonify
import hmac
import hashlib
import json
import time

app = Flask(__name__)
WEBHOOK_SECRET = os.environ.get('WAQITI_WEBHOOK_SECRET')

def verify_webhook_signature(payload, signature, secret, timestamp):
    # Check timestamp (5 minute window)
    current_time = int(time.time())
    if abs(current_time - int(timestamp)) > 300:
        raise ValueError('Webhook timestamp too old')
    
    # Compute expected signature
    signed_payload = f"{timestamp}.{payload}"
    expected_signature = hmac.new(
        secret.encode(),
        signed_payload.encode(),
        hashlib.sha256
    ).hexdigest()
    
    # Compare signatures
    provided_signature = signature.replace('sha256=', '')
    if not hmac.compare_digest(expected_signature, provided_signature):
        raise ValueError('Invalid webhook signature')
    
    return True

@app.route('/webhooks/waqiti', methods=['POST'])
def handle_webhook():
    try:
        # Get headers
        signature = request.headers.get('X-Waqiti-Signature')
        timestamp = request.headers.get('X-Waqiti-Timestamp')
        
        # Get raw payload
        payload = request.get_data(as_text=True)
        
        # Verify signature
        verify_webhook_signature(payload, signature, WEBHOOK_SECRET, timestamp)
        
        # Parse event
        event = json.loads(payload)
        
        # Process event asynchronously
        process_webhook_async.delay(event)
        
        return jsonify({'received': True}), 200
        
    except Exception as e:
        app.logger.error(f'Webhook error: {str(e)}')
        return jsonify({'error': str(e)}), 400

# Using Celery for async processing
@celery.task
def process_webhook_async(event):
    if event['type'] == 'payment.completed':
        handle_payment_completed(event['data'])
    elif event['type'] == 'payment.failed':
        handle_payment_failed(event['data'])
    # ... handle other events
```

### Ruby on Rails

```ruby
class WebhooksController < ApplicationController
  skip_before_action :verify_authenticity_token
  
  def waqiti
    payload = request.body.read
    signature = request.headers['X-Waqiti-Signature']
    timestamp = request.headers['X-Waqiti-Timestamp']
    
    # Verify signature
    verify_webhook_signature(payload, signature, timestamp)
    
    # Parse event
    event = JSON.parse(payload)
    
    # Process asynchronously
    WebhookProcessorJob.perform_later(event)
    
    render json: { received: true }, status: :ok
  rescue StandardError => e
    Rails.logger.error "Webhook error: #{e.message}"
    render json: { error: e.message }, status: :bad_request
  end
  
  private
  
  def verify_webhook_signature(payload, signature, timestamp)
    # Check timestamp
    current_time = Time.now.to_i
    if (current_time - timestamp.to_i).abs > 300
      raise 'Webhook timestamp too old'
    end
    
    # Compute expected signature
    signed_payload = "#{timestamp}.#{payload}"
    expected_signature = OpenSSL::HMAC.hexdigest(
      'SHA256',
      ENV['WAQITI_WEBHOOK_SECRET'],
      signed_payload
    )
    
    # Compare signatures
    provided_signature = signature.gsub('sha256=', '')
    unless ActiveSupport::SecurityUtils.secure_compare(
      expected_signature,
      provided_signature
    )
      raise 'Invalid webhook signature'
    end
  end
end

class WebhookProcessorJob < ApplicationJob
  def perform(event)
    case event['type']
    when 'payment.completed'
      handle_payment_completed(event['data'])
    when 'payment.failed'
      handle_payment_failed(event['data'])
    when 'money_request.created'
      handle_money_request_created(event['data'])
    end
  end
end
```

## Testing Webhooks

### Using the Test Endpoint

Test your webhook implementation:

```bash
curl -X POST https://api.example.com/v1/webhooks/wh_123/test \
  -H "Authorization: Bearer YOUR_API_KEY" \
  -H "Content-Type: application/json" \
  -d '{
    "event": "payment.completed"
  }'
```

### Local Development with ngrok

For local testing, use ngrok to expose your local endpoint:

```bash
# Start your local server
npm run dev

# In another terminal, expose it with ngrok
ngrok http 3000

# Use the ngrok URL for webhook registration
# https://abc123.ngrok.io/webhooks/waqiti
```

### Webhook Testing Checklist

- [ ] Endpoint responds with 2xx status
- [ ] Signature verification works correctly
- [ ] Duplicate events handled idempotently
- [ ] Events processed asynchronously
- [ ] Errors logged appropriately
- [ ] Retry logic implemented
- [ ] Monitoring/alerting configured

## Retry Logic

Waqiti will retry failed webhooks with exponential backoff:

- 1st retry: 5 seconds
- 2nd retry: 30 seconds
- 3rd retry: 2 minutes
- 4th retry: 10 minutes
- 5th retry: 30 minutes
- 6th retry: 2 hours
- 7th retry: 6 hours
- 8th retry: 24 hours

After 8 failed attempts, the webhook is marked as failed.

## Monitoring

### Webhook Metrics

Track these metrics for webhook health:

- Success rate
- Response time
- Error rate by type
- Processing time
- Queue depth (if using async processing)

### Example Monitoring

```javascript
const prometheus = require('prom-client');

// Define metrics
const webhookCounter = new prometheus.Counter({
  name: 'waqiti_webhooks_total',
  help: 'Total number of webhooks received',
  labelNames: ['event_type', 'status']
});

const webhookDuration = new prometheus.Histogram({
  name: 'waqiti_webhook_duration_seconds',
  help: 'Webhook processing duration',
  labelNames: ['event_type']
});

// Track metrics
async function processWebhook(event) {
  const timer = webhookDuration.startTimer({ event_type: event.type });
  
  try {
    await handleWebhookEvent(event);
    webhookCounter.inc({ event_type: event.type, status: 'success' });
  } catch (error) {
    webhookCounter.inc({ event_type: event.type, status: 'error' });
    throw error;
  } finally {
    timer();
  }
}
```

## Troubleshooting

### Common Issues

1. **Signature Verification Fails**
   - Check webhook secret is correct
   - Ensure using raw request body
   - Verify timestamp format

2. **Timeouts**
   - Respond quickly, process async
   - Increase server timeout if needed
   - Check for blocking operations

3. **Duplicate Events**
   - Implement idempotency using event ID
   - Store processed event IDs
   - Check before processing

4. **Missing Events**
   - Check webhook registration
   - Verify endpoint is accessible
   - Check logs for errors

### Debug Mode

Enable detailed logging for troubleshooting:

```javascript
if (process.env.WEBHOOK_DEBUG === 'true') {
  console.log('Webhook received:', {
    headers: req.headers,
    body: req.body,
    signature: req.headers['x-waqiti-signature'],
    timestamp: req.headers['x-waqiti-timestamp']
  });
}
```

## Support

Need help with webhooks?

- 📚 [API Reference](https://developers.example.com/api-reference/webhooks)
- 💬 [Discord Community](https://discord.gg/waqiti)
- 📧 [Developer Support](mailto:developer-support@example.com)