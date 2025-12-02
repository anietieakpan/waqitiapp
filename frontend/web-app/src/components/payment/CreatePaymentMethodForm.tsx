import React, { useState } from 'react';
import {
  Box,
  Button,
  Typography,
  Alert,
  CircularProgress,
  Paper,
  FormControlLabel,
  Switch,
  TextField,
} from '@mui/material';
import {
  PaymentElement,
  useStripe,
  useElements,
} from '@stripe/react-stripe-js';
import { useNotification } from '../../hooks/useNotification';
import { PaymentMethod } from '../../types/payment';

interface CreatePaymentMethodFormProps {
  onSuccess: (paymentMethodId: string) => void;
  onCancel: () => void;
}

/**
 * PCI-DSS COMPLIANT Payment Method Form
 *
 * SECURITY IMPLEMENTATION:
 * ✓ Uses Stripe Payment Element (hosted input fields)
 * ✓ Card data NEVER enters our application code
 * ✓ CVV is NEVER stored (Stripe handles and discards after validation)
 * ✓ Only payment method token (pm_xxx) is returned to our backend
 * ✓ Reduces PCI scope to SAQ-A (simplest compliance level)
 *
 * PCI-DSS Requirements Satisfied:
 * - Requirement 3.2: Do not store sensitive authentication data (CVV) ✓
 * - Requirement 4.2: Encrypt transmission of cardholder data ✓
 * - Requirement 6.5: Secure development practices ✓
 *
 * @see https://docs.stripe.com/security/guide
 * @see https://stripe.com/docs/payments/save-and-reuse
 */
export const CreatePaymentMethodForm: React.FC<CreatePaymentMethodFormProps> = ({
  onSuccess,
  onCancel,
}) => {
  const stripe = useStripe();
  const elements = useElements();
  const { showNotification } = useNotification();

  const [loading, setLoading] = useState(false);
  const [error, setError] = useState<string | null>(null);
  const [displayName, setDisplayName] = useState('');
  const [setAsDefault, setSetAsDefault] = useState(false);

  const handleSubmit = async (event: React.FormEvent) => {
    event.preventDefault();

    // Stripe.js has not loaded yet
    if (!stripe || !elements) {
      setError('Stripe has not loaded. Please refresh the page and try again.');
      return;
    }

    setLoading(true);
    setError(null);

    try {
      // Submit payment element to Stripe (creates payment method)
      // SECURITY: Card data is sent directly to Stripe, never touching our servers
      const { error: submitError } = await elements.submit();

      if (submitError) {
        setError(submitError.message || 'Failed to validate payment details');
        setLoading(false);
        return;
      }

      // Create payment method using Stripe Elements
      // This returns a token (pm_xxx) that we can safely store
      const { error: stripeError, paymentMethod } = await stripe.createPaymentMethod({
        elements,
        params: {
          billing_details: {
            // Optionally collect billing details
            // Stripe Elements can collect these automatically
          },
        },
      });

      if (stripeError) {
        setError(stripeError.message || 'Failed to create payment method');
        setLoading(false);
        return;
      }

      if (!paymentMethod) {
        setError('Failed to create payment method. Please try again.');
        setLoading(false);
        return;
      }

      // PCI-COMPLIANT: Only send payment method ID to backend
      // Our backend receives only: pm_1234567890abcdef
      // NO card numbers, NO CVV, NO sensitive data
      console.log('✓ Payment method created:', paymentMethod.id);
      console.log('✓ Card brand:', paymentMethod.card?.brand);
      console.log('✓ Last 4 digits:', paymentMethod.card?.last4);

      // Call parent component with payment method ID
      onSuccess(paymentMethod.id);

      showNotification('Payment method added successfully', 'success');
    } catch (err) {
      const errorMessage = err instanceof Error
        ? err.message
        : 'An unexpected error occurred';
      setError(errorMessage);
      showNotification(errorMessage, 'error');
      console.error('Payment method creation error:', err);
    } finally {
      setLoading(false);
    }
  };

  return (
    <Box component="form" onSubmit={handleSubmit} sx={{ maxWidth: 500, mx: 'auto' }}>
      <Typography variant="h6" gutterBottom>
        Add Payment Method
      </Typography>

      <Typography variant="body2" color="text.secondary" sx={{ mb: 3 }}>
        Your payment information is securely processed by Stripe.
        We never see or store your card details.
      </Typography>

      {error && (
        <Alert severity="error" sx={{ mb: 2 }}>
          {error}
        </Alert>
      )}

      {/* Stripe Payment Element - PCI-DSS Compliant Card Collection */}
      <Paper elevation={0} sx={{ p: 2, mb: 2, border: '1px solid #e0e0e0' }}>
        <PaymentElement
          options={{
            layout: 'tabs',
            paymentMethodOrder: ['card', 'us_bank_account'],
            fields: {
              billingDetails: {
                address: {
                  country: 'auto',
                },
              },
            },
          }}
        />
      </Paper>

      {/* Optional: Custom display name */}
      <TextField
        fullWidth
        label="Display Name (Optional)"
        value={displayName}
        onChange={(e) => setDisplayName(e.target.value)}
        margin="normal"
        helperText="Custom name for easy identification (e.g., 'Personal Card')"
        disabled={loading}
      />

      {/* Optional: Set as default */}
      <FormControlLabel
        control={
          <Switch
            checked={setAsDefault}
            onChange={(e) => setSetAsDefault(e.target.checked)}
            disabled={loading}
          />
        }
        label="Set as default payment method"
        sx={{ mb: 2 }}
      />

      {/* Action Buttons */}
      <Box sx={{ display: 'flex', gap: 2, mt: 3 }}>
        <Button
          type="submit"
          variant="contained"
          disabled={!stripe || loading}
          startIcon={loading && <CircularProgress size={20} />}
          fullWidth
        >
          {loading ? 'Processing...' : 'Add Payment Method'}
        </Button>
        <Button
          variant="outlined"
          onClick={onCancel}
          disabled={loading}
          fullWidth
        >
          Cancel
        </Button>
      </Box>

      {/* Security Notice */}
      <Alert severity="info" icon={false} sx={{ mt: 2 }}>
        <Typography variant="caption" display="block">
          🔒 Secured by Stripe - PCI DSS Level 1 Certified
        </Typography>
        <Typography variant="caption" color="text.secondary">
          Your card details are encrypted and transmitted directly to Stripe.
          Waqiti never has access to your full card number or CVV.
        </Typography>
      </Alert>
    </Box>
  );
};

export default CreatePaymentMethodForm;
