import { TransactionType, TransactionStatus } from './wallet';

export interface TransactionFilter {
  page?: number;
  limit?: number;
  type?: TransactionType;
  status?: TransactionStatus;
  startDate?: string;
  endDate?: string;
  minAmount?: number;
  maxAmount?: number;
  walletId?: string;
  searchTerm?: string;
  sortBy?: string;
  sortDirection?: 'ASC' | 'DESC';
}

export interface TransactionSummary {
  totalIncome: number;
  totalExpense: number;
  totalTransactions: number;
  pendingTransactions: number;
  transactionsByType: {
    type: TransactionType;
    count: number;
    totalAmount: number;
  }[];
  transactionsByDay: {
    date: string;
    income: number;
    expense: number;
    count: number;
  }[];
}

export interface TransactionDetails {
  id: string;
  type: TransactionType;
  status: TransactionStatus;
  amount: number;
  currency: string;
  description: string;
  reference: string;
  createdAt: string;
  updatedAt: string;
  completedAt?: string;
  
  // Wallet information
  fromWallet?: {
    id: string;
    name: string;
    type: string;
  };
  toWallet?: {
    id: string;
    name: string;
    type: string;
  };
  
  // User information
  fromUser?: {
    id: string;
    name: string;
    email: string;
    phoneNumber: string;
    profilePicture?: string;
  };
  toUser?: {
    id: string;
    name: string;
    email: string;
    phoneNumber: string;
    profilePicture?: string;
  };
  
  // Transaction details
  fees?: {
    amount: number;
    currency: string;
    description: string;
  };
  exchangeRate?: {
    from: string;
    to: string;
    rate: number;
  };
  
  // Additional information
  metadata?: Record<string, any>;
  timeline?: TransactionEvent[];
  receipt?: {
    available: boolean;
    url?: string;
  };
  dispute?: {
    id: string;
    status: string;
    reason: string;
    createdAt: string;
  };
}

export interface TransactionEvent {
  event: string;
  status: string;
  description: string;
  timestamp: string;
}

export interface TransactionReceipt {
  transactionId: string;
  receiptNumber: string;
  date: string;
  amount: number;
  currency: string;
  type: TransactionType;
  status: TransactionStatus;
  fromAccount: string;
  toAccount: string;
  description: string;
  fees: number;
  totalAmount: number;
}

export interface DisputeRequest {
  reason: string;
  description: string;
  attachments?: File[];
}

export interface TransactionExportRequest {
  format: 'csv' | 'pdf';
  filter: TransactionFilter;
}