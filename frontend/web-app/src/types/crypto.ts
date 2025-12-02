export enum CryptoCurrency {
  BTC = 'BTC',
  ETH = 'ETH',
  USDT = 'USDT',
  USDC = 'USDC',
  BNB = 'BNB',
  SOL = 'SOL',
  ADA = 'ADA',
  MATIC = 'MATIC',
}

export interface CryptoWallet {
  id: string;
  currency: CryptoCurrency;
  address: string;
  balance: number;
  balanceUSD: number;
  network: string;
}

export interface CryptoPrice {
  currency: CryptoCurrency;
  priceUSD: number;
  change24h: number;
  change7d: number;
  marketCap: number;
  volume24h: number;
}

export interface CryptoTransaction {
  id: string;
  type: 'SEND' | 'RECEIVE' | 'BUY' | 'SELL' | 'SWAP';
  currency: CryptoCurrency;
  amount: number;
  amountUSD: number;
  from?: string;
  to?: string;
  txHash?: string;
  status: 'PENDING' | 'CONFIRMED' | 'FAILED';
  timestamp: string;
  fee?: number;
  confirmations?: number;
}

export interface BuyCryptoRequest {
  currency: CryptoCurrency;
  amount: number;
  paymentMethodId: string;
}

export interface SendCryptoRequest {
  currency: CryptoCurrency;
  amount: number;
  toAddress: string;
  network?: string;
}

export interface SwapCryptoRequest {
  fromCurrency: CryptoCurrency;
  toCurrency: CryptoCurrency;
  amount: number;
}

export interface CryptoState {
  wallets: CryptoWallet[];
  prices: Record<CryptoCurrency, CryptoPrice>;
  transactions: CryptoTransaction[];
  selectedWallet: CryptoWallet | null;
  loading: boolean;
  error: string | null;
}
