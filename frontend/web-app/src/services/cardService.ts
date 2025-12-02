import axios from 'axios';
import {
  Card,
  CreateVirtualCardRequest,
  CreatePhysicalCardRequest,
  UpdateCardLimitsRequest,
  CardTransaction,
} from '@/types/card';

const API_BASE_URL = import.meta.env.VITE_API_BASE_URL || 'http://localhost:8443';

export const cardService = {
  // Get all cards for the user
  getCards: async (): Promise<Card[]> => {
    const response = await axios.get(`${API_BASE_URL}/api/v1/cards`);
    return response.data.data || response.data;
  },

  // Get single card details
  getCard: async (cardId: string): Promise<Card> => {
    const response = await axios.get(`${API_BASE_URL}/api/v1/cards/${cardId}`);
    return response.data.data || response.data;
  },

  // Create virtual card
  createVirtualCard: async (request: CreateVirtualCardRequest): Promise<Card> => {
    const response = await axios.post(`${API_BASE_URL}/api/v1/cards/virtual`, request);
    return response.data.data || response.data;
  },

  // Create physical card
  createPhysicalCard: async (request: CreatePhysicalCardRequest): Promise<Card> => {
    const response = await axios.post(`${API_BASE_URL}/api/v1/cards/physical`, request);
    return response.data.data || response.data;
  },

  // Freeze card
  freezeCard: async (cardId: string): Promise<void> => {
    await axios.put(`${API_BASE_URL}/api/v1/cards/${cardId}/freeze`);
  },

  // Unfreeze card
  unfreezeCard: async (cardId: string): Promise<void> => {
    await axios.put(`${API_BASE_URL}/api/v1/cards/${cardId}/unfreeze`);
  },

  // Block card (permanent)
  blockCard: async (cardId: string, reason: string): Promise<void> => {
    await axios.put(`${API_BASE_URL}/api/v1/cards/${cardId}/block`, { reason });
  },

  // Update card limits
  updateLimits: async (cardId: string, request: UpdateCardLimitsRequest): Promise<Card> => {
    const response = await axios.put(`${API_BASE_URL}/api/v1/cards/${cardId}/limits`, request);
    return response.data.data || response.data;
  },

  // Change PIN
  changePin: async (cardId: string, currentPin: string, newPin: string): Promise<void> => {
    await axios.put(`${API_BASE_URL}/api/v1/cards/${cardId}/pin`, {
      currentPin,
      newPin,
    });
  },

  // Get card transactions
  getCardTransactions: async (cardId: string, limit = 50): Promise<CardTransaction[]> => {
    const response = await axios.get(`${API_BASE_URL}/api/v1/cards/${cardId}/transactions`, {
      params: { limit },
    });
    return response.data.data || response.data;
  },

  // Cancel card
  cancelCard: async (cardId: string, reason: string): Promise<void> => {
    await axios.delete(`${API_BASE_URL}/api/v1/cards/${cardId}`, {
      data: { reason },
    });
  },
};
