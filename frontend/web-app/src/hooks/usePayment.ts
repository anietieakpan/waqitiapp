import { useState, useCallback } from 'react';
import { useAppDispatch, useAppSelector } from './redux';
import { paymentService } from '../services/paymentService';
import { Payment, PaymentRequest, PaymentRequestFilters } from '../types/payment';
import { toast } from 'react-hot-toast';

export const usePayment = () => {
  const dispatch = useAppDispatch();
  const { user } = useAppSelector(state => state.auth);
  
  const [loading, setLoading] = useState(false);
  const [paymentRequests, setPaymentRequests] = useState<PaymentRequest[]>([]);
  const [recentPayments, setRecentPayments] = useState<Payment[]>([]);

  const sendMoney = useCallback(async (paymentData: {
    recipientId: string;
    amount: number;
    currency: string;
    description?: string;
    walletId: string;
  }) => {
    try {
      setLoading(true);
      const payment = await paymentService.sendMoney(paymentData);
      toast.success('Payment sent successfully');
      return payment;
    } catch (error: any) {
      toast.error(error.message || 'Failed to send payment');
      throw error;
    } finally {
      setLoading(false);
    }
  }, []);

  const requestMoney = useCallback(async (requestData: {
    fromUserId: string;
    amount: number;
    currency: string;
    description?: string;
    dueDate?: Date;
  }) => {
    try {
      setLoading(true);
      const request = await paymentService.requestMoney(requestData);
      toast.success('Payment request sent successfully');
      return request;
    } catch (error: any) {
      toast.error(error.message || 'Failed to send payment request');
      throw error;
    } finally {
      setLoading(false);
    }
  }, []);

  const acceptPaymentRequest = useCallback(async (requestId: string, walletId: string) => {
    try {
      setLoading(true);
      const payment = await paymentService.acceptPaymentRequest(requestId, walletId);
      
      // Update payment requests list
      setPaymentRequests(prev => 
        prev.map(req => 
          req.id === requestId 
            ? { ...req, status: 'ACCEPTED' }
            : req
        )
      );
      
      toast.success('Payment request accepted');
      return payment;
    } catch (error: any) {
      toast.error(error.message || 'Failed to accept payment request');
      throw error;
    } finally {
      setLoading(false);
    }
  }, []);

  const rejectPaymentRequest = useCallback(async (requestId: string, reason?: string) => {
    try {
      setLoading(true);
      await paymentService.rejectPaymentRequest(requestId, reason);
      
      // Update payment requests list
      setPaymentRequests(prev => 
        prev.map(req => 
          req.id === requestId 
            ? { ...req, status: 'REJECTED' }
            : req
        )
      );
      
      toast.success('Payment request rejected');
    } catch (error: any) {
      toast.error(error.message || 'Failed to reject payment request');
      throw error;
    } finally {
      setLoading(false);
    }
  }, []);

  const cancelPaymentRequest = useCallback(async (requestId: string) => {
    try {
      setLoading(true);
      await paymentService.cancelPaymentRequest(requestId);
      
      // Update payment requests list
      setPaymentRequests(prev => 
        prev.map(req => 
          req.id === requestId 
            ? { ...req, status: 'CANCELLED' }
            : req
        )
      );
      
      toast.success('Payment request cancelled');
    } catch (error: any) {
      toast.error(error.message || 'Failed to cancel payment request');
      throw error;
    } finally {
      setLoading(false);
    }
  }, []);

  const fetchPaymentRequests = useCallback(async (filters?: PaymentRequestFilters) => {
    try {
      setLoading(true);
      const requests = await paymentService.getPaymentRequests(filters);
      setPaymentRequests(requests);
    } catch (error: any) {
      toast.error(error.message || 'Failed to fetch payment requests');
    } finally {
      setLoading(false);
    }
  }, []);

  const fetchRecentPayments = useCallback(async (limit = 10) => {
    try {
      setLoading(true);
      const payments = await paymentService.getRecentPayments(limit);
      setRecentPayments(payments);
    } catch (error: any) {
      toast.error(error.message || 'Failed to fetch recent payments');
    } finally {
      setLoading(false);
    }
  }, []);

  const getPaymentById = useCallback(async (paymentId: string) => {
    try {
      setLoading(true);
      const payment = await paymentService.getPaymentById(paymentId);
      return payment;
    } catch (error: any) {
      toast.error(error.message || 'Failed to fetch payment details');
      throw error;
    } finally {
      setLoading(false);
    }
  }, []);

  const refundPayment = useCallback(async (paymentId: string, reason?: string) => {
    try {
      setLoading(true);
      const refund = await paymentService.refundPayment(paymentId, reason);
      toast.success('Payment refunded successfully');
      return refund;
    } catch (error: any) {
      toast.error(error.message || 'Failed to refund payment');
      throw error;
    } finally {
      setLoading(false);
    }
  }, []);

  const schedulePayment = useCallback(async (paymentData: {
    recipientId: string;
    amount: number;
    currency: string;
    description?: string;
    walletId: string;
    scheduledDate: Date;
    recurring?: {
      frequency: 'DAILY' | 'WEEKLY' | 'MONTHLY' | 'YEARLY';
      endDate?: Date;
      maxOccurrences?: number;
    };
  }) => {
    try {
      setLoading(true);
      const scheduledPayment = await paymentService.schedulePayment(paymentData);
      toast.success('Payment scheduled successfully');
      return scheduledPayment;
    } catch (error: any) {
      toast.error(error.message || 'Failed to schedule payment');
      throw error;
    } finally {
      setLoading(false);
    }
  }, []);

  const splitPayment = useCallback(async (splitData: {
    totalAmount: number;
    currency: string;
    description?: string;
    participants: Array<{
      userId: string;
      amount: number;
      percentage?: number;
    }>;
    walletId: string;
  }) => {
    try {
      setLoading(true);
      const splitPayment = await paymentService.createSplitPayment(splitData);
      toast.success('Split payment created successfully');
      return splitPayment;
    } catch (error: any) {
      toast.error(error.message || 'Failed to create split payment');
      throw error;
    } finally {
      setLoading(false);
    }
  }, []);

  const validatePaymentLimits = useCallback(async (amount: number, currency: string) => {
    try {
      const validation = await paymentService.validatePaymentLimits(amount, currency);
      return validation;
    } catch (error: any) {
      toast.error(error.message || 'Failed to validate payment limits');
      return { isValid: false, reason: 'Validation failed' };
    }
  }, []);

  const estimatePaymentFee = useCallback(async (amount: number, currency: string, type: string) => {
    try {
      const fee = await paymentService.estimateFee(amount, currency, type);
      return fee;
    } catch (error: any) {
      console.error('Failed to estimate fee:', error);
      return 0;
    }
  }, []);

  // Get pending payment requests count
  const getPendingRequestsCount = useCallback(() => {
    return paymentRequests.filter(req => req.status === 'PENDING').length;
  }, [paymentRequests]);

  // Get incoming vs outgoing requests
  const getIncomingRequests = useCallback(() => {
    return paymentRequests.filter(req => req.toUserId === user?.id);
  }, [paymentRequests, user]);

  const getOutgoingRequests = useCallback(() => {
    return paymentRequests.filter(req => req.fromUserId === user?.id);
  }, [paymentRequests, user]);

  return {
    // State
    loading,
    paymentRequests,
    recentPayments,
    
    // Payment actions
    sendMoney,
    requestMoney,
    acceptPaymentRequest,
    rejectPaymentRequest,
    cancelPaymentRequest,
    refundPayment,
    schedulePayment,
    splitPayment,
    
    // Fetch actions
    fetchPaymentRequests,
    fetchRecentPayments,
    getPaymentById,
    
    // Validation and utilities
    validatePaymentLimits,
    estimatePaymentFee,
    
    // Computed values
    getPendingRequestsCount,
    getIncomingRequests,
    getOutgoingRequests
  };
};