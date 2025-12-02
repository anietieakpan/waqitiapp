import axios from 'axios';
import { BNPLPlan, BNPLInstallment, CreateBNPLRequest } from '@/types/bnpl';

const API_BASE_URL = import.meta.env.VITE_API_BASE_URL || 'http://localhost:8443';

export const bnplService = {
  getPlans: async (): Promise<BNPLPlan[]> => {
    const response = await axios.get(`${API_BASE_URL}/api/v1/bnpl/plans`);
    return response.data.data || response.data;
  },

  getPlan: async (planId: string): Promise<BNPLPlan> => {
    const response = await axios.get(`${API_BASE_URL}/api/v1/bnpl/plans/${planId}`);
    return response.data.data || response.data;
  },

  createPlan: async (request: CreateBNPLRequest): Promise<BNPLPlan> => {
    const response = await axios.post(`${API_BASE_URL}/api/v1/bnpl/apply`, request);
    return response.data.data || response.data;
  },

  getInstallments: async (planId: string): Promise<BNPLInstallment[]> => {
    const response = await axios.get(`${API_BASE_URL}/api/v1/bnpl/plans/${planId}/installments`);
    return response.data.data || response.data;
  },

  payInstallment: async (planId: string, installmentId: string): Promise<void> => {
    await axios.post(`${API_BASE_URL}/api/v1/bnpl/plans/${planId}/installments/${installmentId}/pay`);
  },

  cancelPlan: async (planId: string): Promise<void> => {
    await axios.delete(`${API_BASE_URL}/api/v1/bnpl/plans/${planId}`);
  },
};
