import { useState, useCallback } from 'react';
import { useDispatch, useSelector } from 'react-redux';
import { Alert } from 'react-native';
import { RootState } from '../store';
import { createPayment } from '../store/slices/paymentSlice';
import { AnalyticsService } from '../services/AnalyticsService';

/**
 * usePayment Hook
 *
 * Custom hook for payment processing with comprehensive error handling,
 * validation, analytics, and state management
 *
 * Features:
 * - Payment initiation and processing
 * - Comprehensive validation
 * - Error handling and retry logic
 * - Analytics tracking
 * - Loading states
 * - Balance checks
 * - Transaction limits
 *
 * @author Waqiti Engineering Team
 * @version 1.0.0
 */

export interface PaymentRequest {
  recipientId: string;
  amount: number;
  currency?: string;
  description?: string;
  paymentMethodId?: string;
  metadata?: Record<string, any>;
}

export interface PaymentValidationResult {
  isValid: boolean;
  errors: string[];
}

export interface PaymentResult {
  success: boolean;
  transactionId?: string;
  error?: string;
}

interface UsePaymentReturn {
  initiatePayment: (request: PaymentRequest) => Promise<PaymentResult>;
  validatePayment: (request: PaymentRequest) => PaymentValidationResult;
  isProcessing: boolean;
  error: string | null;
  clearError: () => void;
  canRetry: boolean;
  retryPayment: () => Promise<PaymentResult>;
}

export const usePayment = (): UsePaymentReturn => {
  const dispatch = useDispatch();

  const { user } = useSelector((state: RootState) => state.auth);
  const { balance } = useSelector((state: RootState) => state.wallet);
  const { limits } = useSelector((state: RootState) => state.settings);

  const [isProcessing, setIsProcessing] = useState(false);
  const [error, setError] = useState<string | null>(null);
  const [lastPaymentRequest, setLastPaymentRequest] = useState<PaymentRequest | null>(null);
  const [retryCount, setRetryCount] = useState(0);

  const MAX_RETRY_ATTEMPTS = 3;

  /**
   * Validates payment request before processing
   */
  const validatePayment = useCallback(
    (request: PaymentRequest): PaymentValidationResult => {
      const errors: string[] = [];

      // Validate recipient
      if (!request.recipientId) {
        errors.push('Recipient is required');
      }

      if (request.recipientId === user?.id) {
        errors.push('Cannot send payment to yourself');
      }

      // Validate amount
      if (!request.amount || request.amount <= 0) {
        errors.push('Amount must be greater than 0');
      }

      const minAmount = limits?.minTransactionAmount || 0.01;
      const maxAmount = limits?.maxTransactionAmount || 10000;

      if (request.amount < minAmount) {
        errors.push(`Minimum amount is $${minAmount.toFixed(2)}`);
      }

      if (request.amount > maxAmount) {
        errors.push(`Maximum amount is $${maxAmount.toFixed(2)}`);
      }

      // Validate balance
      if (balance && request.amount > balance) {
        errors.push('Insufficient balance');
      }

      // Validate daily limit
      const dailyLimit = limits?.dailyTransactionLimit || 5000;
      const todaySpent = 0; // TODO: Calculate from transactions

      if (todaySpent + request.amount > dailyLimit) {
        errors.push(`Daily limit of $${dailyLimit.toFixed(2)} would be exceeded`);
      }

      // Validate KYC status for large amounts
      const kycRequiredAmount = limits?.kycRequiredAmount || 1000;
      if (request.amount >= kycRequiredAmount && user?.kycStatus !== 'verified') {
        errors.push('KYC verification required for this amount');
      }

      // Validate currency
      const supportedCurrencies = ['USD', 'EUR', 'GBP'];
      if (request.currency && !supportedCurrencies.includes(request.currency)) {
        errors.push('Unsupported currency');
      }

      return {
        isValid: errors.length === 0,
        errors,
      };
    },
    [user, balance, limits]
  );

  /**
   * Initiates payment processing
   */
  const initiatePayment = useCallback(
    async (request: PaymentRequest): Promise<PaymentResult> => {
      setIsProcessing(true);
      setError(null);
      setLastPaymentRequest(request);

      AnalyticsService.trackEvent('payment_initiated', {
        userId: user?.id,
        recipientId: request.recipientId,
        amount: request.amount,
        currency: request.currency || 'USD',
      });

      try {
        // Validate payment
        const validation = validatePayment(request);
        if (!validation.isValid) {
          const errorMessage = validation.errors.join(', ');
          setError(errorMessage);

          AnalyticsService.trackEvent('payment_validation_failed', {
            userId: user?.id,
            errors: validation.errors,
          });

          Alert.alert('Payment Failed', errorMessage);

          return {
            success: false,
            error: errorMessage,
          };
        }

        // Process payment via Redux action
        const result = await dispatch(
          createPayment({
            senderId: user!.id,
            recipientId: request.recipientId,
            amount: request.amount,
            currency: request.currency || 'USD',
            description: request.description,
            paymentMethodId: request.paymentMethodId,
            metadata: request.metadata,
          })
        ).unwrap();

        AnalyticsService.trackEvent('payment_success', {
          userId: user?.id,
          transactionId: result.transactionId,
          amount: request.amount,
          currency: request.currency || 'USD',
        });

        Alert.alert(
          'Payment Successful',
          `$${request.amount.toFixed(2)} sent successfully`
        );

        setRetryCount(0);

        return {
          success: true,
          transactionId: result.transactionId,
        };

      } catch (err: any) {
        const errorMessage = err.message || 'Payment failed. Please try again.';
        setError(errorMessage);

        AnalyticsService.trackEvent('payment_failed', {
          userId: user?.id,
          error: errorMessage,
          retryCount,
        });

        // Determine if error is retryable
        const retryableErrors = [
          'network error',
          'timeout',
          'service unavailable',
          'temporary failure',
        ];

        const isRetryable = retryableErrors.some((e) =>
          errorMessage.toLowerCase().includes(e)
        );

        if (isRetryable && retryCount < MAX_RETRY_ATTEMPTS) {
          Alert.alert(
            'Payment Failed',
            errorMessage,
            [
              {
                text: 'Cancel',
                style: 'cancel',
              },
              {
                text: 'Retry',
                onPress: () => retryPayment(),
              },
            ]
          );
        } else {
          Alert.alert('Payment Failed', errorMessage);
        }

        return {
          success: false,
          error: errorMessage,
        };

      } finally {
        setIsProcessing(false);
      }
    },
    [user, dispatch, validatePayment, retryCount]
  );

  /**
   * Retries last failed payment
   */
  const retryPayment = useCallback(async (): Promise<PaymentResult> => {
    if (!lastPaymentRequest) {
      return {
        success: false,
        error: 'No payment to retry',
      };
    }

    setRetryCount((prev) => prev + 1);

    AnalyticsService.trackEvent('payment_retry', {
      userId: user?.id,
      retryCount: retryCount + 1,
    });

    return initiatePayment(lastPaymentRequest);
  }, [lastPaymentRequest, retryCount, initiatePayment]);

  /**
   * Clears error state
   */
  const clearError = useCallback(() => {
    setError(null);
  }, []);

  const canRetry = lastPaymentRequest !== null && retryCount < MAX_RETRY_ATTEMPTS;

  return {
    initiatePayment,
    validatePayment,
    isProcessing,
    error,
    clearError,
    canRetry,
    retryPayment,
  };
};

export default usePayment;
