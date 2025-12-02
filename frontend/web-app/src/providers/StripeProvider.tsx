import React from 'react';
import { Elements } from '@stripe/react-stripe-js';
import { loadStripe, Stripe } from '@stripe/stripe-js';

// Load Stripe with public key from environment
// This is safe to expose in client-side code (public key only)
const stripePromise: Promise<Stripe | null> = loadStripe(
  import.meta.env.VITE_STRIPE_PUBLIC_KEY || ''
);

interface StripeProviderProps {
  children: React.ReactNode;
}

/**
 * Stripe Provider - Wraps app with Stripe Elements context
 *
 * SECURITY: This enables PCI-DSS compliant payment collection.
 * Card data is handled entirely by Stripe's secure iframes.
 * Our application never sees or stores raw card numbers or CVVs.
 *
 * @see https://stripe.com/docs/security/guide#validating-pci-compliance
 */
export const StripeProvider: React.FC<StripeProviderProps> = ({ children }) => {
  // Validate Stripe key is present
  if (!import.meta.env.VITE_STRIPE_PUBLIC_KEY) {
    console.warn(
      'VITE_STRIPE_PUBLIC_KEY is not set. Payment features will not work. ' +
      'Add your Stripe public key to .env.development or .env.production'
    );
  }

  return (
    <Elements
      stripe={stripePromise}
      options={{
        appearance: {
          theme: 'stripe',
          variables: {
            colorPrimary: '#1976d2', // Match MUI primary color
            colorBackground: '#ffffff',
            colorText: '#30313d',
            colorDanger: '#df1b41',
            fontFamily: 'Roboto, sans-serif',
            spacingUnit: '4px',
            borderRadius: '4px',
          },
        },
        // Localization
        locale: 'auto',
      }}
    >
      {children}
    </Elements>
  );
};

export default StripeProvider;
