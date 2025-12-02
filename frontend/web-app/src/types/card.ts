export enum CardType {
  VIRTUAL = 'VIRTUAL',
  PHYSICAL = 'PHYSICAL',
}

export enum CardBrand {
  VISA = 'VISA',
  MASTERCARD = 'MASTERCARD',
  AMEX = 'AMEX',
}

export enum CardStatus {
  ACTIVE = 'ACTIVE',
  FROZEN = 'FROZEN',
  BLOCKED = 'BLOCKED',
  EXPIRED = 'EXPIRED',
  CANCELLED = 'CANCELLED',
}

export interface Card {
  id: string;
  userId: string;
  type: CardType;
  brand: CardBrand;
  last4: string;
  expiryMonth: number;
  expiryYear: number;
  cardholderName: string;
  status: CardStatus;
  billingAddress?: Address;
  dailySpendLimit?: number;
  monthlySpendLimit?: number;
  remainingDailyLimit?: number;
  remainingMonthlyLimit?: number;
  cvv?: string; // Only for virtual cards, temporary
  cardNumber?: string; // Only for virtual cards, temporary
  createdAt: string;
  updatedAt: string;
}

export interface Address {
  street: string;
  city: string;
  state: string;
  postalCode: string;
  country: string;
}

export interface CreateVirtualCardRequest {
  cardholderName: string;
  billingAddress: Address;
  dailySpendLimit?: number;
  monthlySpendLimit?: number;
}

export interface CreatePhysicalCardRequest {
  cardholderName: string;
  billingAddress: Address;
  shippingAddress?: Address;
}

export interface UpdateCardLimitsRequest {
  dailySpendLimit?: number;
  monthlySpendLimit?: number;
}

export interface CardTransaction {
  id: string;
  cardId: string;
  amount: number;
  currency: string;
  merchantName: string;
  merchantCategory: string;
  status: 'PENDING' | 'COMPLETED' | 'DECLINED' | 'REVERSED';
  transactionDate: string;
  description?: string;
}

export interface CardState {
  cards: Card[];
  selectedCard: Card | null;
  transactions: CardTransaction[];
  loading: boolean;
  error: string | null;
}
