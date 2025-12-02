export enum BNPLStatus {
  ACTIVE = 'ACTIVE',
  COMPLETED = 'COMPLETED',
  DEFAULTED = 'DEFAULTED',
  CANCELLED = 'CANCELLED',
}

export interface BNPLPlan {
  id: string;
  merchantName: string;
  totalAmount: number;
  downPayment: number;
  remainingAmount: number;
  numberOfInstallments: number;
  installmentAmount: number;
  paidInstallments: number;
  nextPaymentDate: string;
  nextPaymentAmount: number;
  status: BNPLStatus;
  interestRate: number;
  createdAt: string;
  completedAt?: string;
}

export interface BNPLInstallment {
  id: string;
  planId: string;
  installmentNumber: number;
  amount: number;
  dueDate: string;
  paidDate?: string;
  status: 'PENDING' | 'PAID' | 'OVERDUE' | 'MISSED';
}

export interface CreateBNPLRequest {
  merchantId: string;
  totalAmount: number;
  numberOfInstallments: number;
  downPaymentPercent: number;
}

export interface BNPLState {
  plans: BNPLPlan[];
  selectedPlan: BNPLPlan | null;
  installments: BNPLInstallment[];
  loading: boolean;
  error: string | null;
}
