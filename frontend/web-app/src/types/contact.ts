export interface Contact {
  id: string;
  name: string;
  email: string;
  phone?: string;
  avatar?: string;
  isFavorite?: boolean;
  lastTransactionDate?: string;
  totalTransactions?: number;
  createdAt?: string;
}

export interface ContactRequest {
  name: string;
  email: string;
  phone?: string;
}

export interface ContactSearchResult {
  contacts: Contact[];
  totalCount: number;
  hasMore: boolean;
}