/**
 * Cryptocurrency Types
 * TypeScript definitions for all cryptocurrency-related data structures
 */

export enum CryptoCurrency {
  BITCOIN = 'BITCOIN',
  ETHEREUM = 'ETHEREUM',
  LITECOIN = 'LITECOIN',
  USDC = 'USDC',
  USDT = 'USDT',
}

export enum WalletType {
  HD = 'HD',
  MULTISIG_HD = 'MULTISIG_HD',
  HARDWARE = 'HARDWARE',
}

export enum WalletStatus {
  ACTIVE = 'ACTIVE',
  INACTIVE = 'INACTIVE',
  FROZEN = 'FROZEN',
  CLOSED = 'CLOSED',
}

export enum CryptoTransactionType {
  BUY = 'BUY',
  SELL = 'SELL',
  SEND = 'SEND',
  RECEIVE = 'RECEIVE',
  CONVERT = 'CONVERT',
  STAKE = 'STAKE',
  UNSTAKE = 'UNSTAKE',
}

export enum CryptoTransactionStatus {
  PENDING = 'PENDING',
  PENDING_DELAY = 'PENDING_DELAY',
  PENDING_APPROVAL = 'PENDING_APPROVAL',
  PENDING_REVIEW = 'PENDING_REVIEW',
  BROADCASTED = 'BROADCASTED',
  CONFIRMED = 'CONFIRMED',
  COMPLETED = 'COMPLETED',
  FAILED = 'FAILED',
  CANCELLED = 'CANCELLED',
}

export enum FeeSpeed {
  SLOW = 'SLOW',
  STANDARD = 'STANDARD',
  FAST = 'FAST',
}

export enum AddressType {
  RECEIVING = 'RECEIVING',
  CHANGE = 'CHANGE',
}

export enum AddressStatus {
  ACTIVE = 'ACTIVE',
  INACTIVE = 'INACTIVE',
  USED = 'USED',
}

export enum BalanceUpdateType {
  AVAILABLE_INCREASE = 'AVAILABLE_INCREASE',
  AVAILABLE_DECREASE = 'AVAILABLE_DECREASE',
  PENDING_INCREASE = 'PENDING_INCREASE',
  PENDING_DECREASE = 'PENDING_DECREASE',
  PENDING_TO_AVAILABLE = 'PENDING_TO_AVAILABLE',
  STAKED_INCREASE = 'STAKED_INCREASE',
  STAKED_DECREASE = 'STAKED_DECREASE',
}

export enum RiskLevel {
  MINIMAL = 'MINIMAL',
  LOW = 'LOW',
  MEDIUM = 'MEDIUM',
  HIGH = 'HIGH',
  CRITICAL = 'CRITICAL',
}

// Wallet Interfaces
export interface CryptoWallet {
  walletId: string;
  userId: string;
  currency: CryptoCurrency;
  walletType: WalletType;
  multiSigAddress: string;
  availableBalance: number;
  pendingBalance: number;
  stakedBalance: number;
  totalBalance: number;
  usdValue: number;
  currentPrice: number;
  status: WalletStatus;
  createdAt: string;
  lastUpdated: string;
}

export interface CryptoWalletDetailsResponse extends CryptoWallet {
  addresses: CryptoAddressResponse[];
  derivationPath?: string;
  publicKey?: string;
  requiredSignatures?: number;
  totalKeys?: number;
}

export interface CryptoBalance {
  walletId: string;
  currency: CryptoCurrency;
  availableBalance: number;
  pendingBalance: number;
  stakedBalance: number;
  totalBalance: number;
  lastUpdated: string;
}

export interface CryptoAddress {
  addressId: string;
  walletId: string;
  address: string;
  derivationPath: string;
  publicKey: string;
  addressIndex: number;
  addressType: AddressType;
  label?: string;
  status: AddressStatus;
  createdAt: string;
}

export interface CryptoAddressResponse {
  address: string;
  addressType: AddressType;
  label?: string;
  derivationPath: string;
  addressIndex: number;
  status: AddressStatus;
  createdAt: string;
}

// Transaction Interfaces
export interface CryptoTransaction {
  transactionId: string;
  userId: string;
  walletId?: string;
  currency: CryptoCurrency;
  transactionType: CryptoTransactionType;
  amount: number;
  usdValue: number;
  fee: number;
  price?: number;
  fromAddress?: string;
  toAddress?: string;
  memo?: string;
  txHash?: string;
  status: CryptoTransactionStatus;
  confirmations?: number;
  blockNumber?: number;
  blockHash?: string;
  riskScore?: number;
  riskLevel?: RiskLevel;
  createdAt: string;
  confirmedAt?: string;
  completedAt?: string;
  scheduledFor?: string;
}

export interface CryptoTransactionResponse {
  transactionId: string;
  txHash?: string;
  currency: CryptoCurrency;
  transactionType: CryptoTransactionType;
  amount: number;
  usdValue: number;
  fee: number;
  fromAddress?: string;
  toAddress?: string;
  memo?: string;
  status: CryptoTransactionStatus;
  confirmations?: number;
  createdAt: string;
  confirmedAt?: string;
  scheduledFor?: string;
}

export interface CryptoTransactionDetailsResponse extends CryptoTransactionResponse {
  blockNumber?: number;
  blockHash?: string;
  blockchainDetails?: TransactionDetails;
}

export interface TransactionDetails {
  blockHeight: number;
  blockTime: string;
  gasUsed?: number;
  gasPrice?: number;
  inputs?: any[];
  outputs?: any[];
}

// Request Interfaces
export interface CreateCryptoWalletRequest {
  currency: CryptoCurrency;
}

export interface GenerateReceiveAddressRequest {
  currency: CryptoCurrency;
  addressType?: AddressType;
  label?: string;
  amount?: number;
}

export interface CryptoReceiveResponse {
  address: string;
  qrCodeData: string;
  addressType: AddressType;
  label?: string;
  currency: CryptoCurrency;
  derivationPath: string;
  createdAt: string;
}

export interface CryptoBuyRequest {
  currency: CryptoCurrency;
  usdAmount: number;
  paymentMethod: any; // PaymentMethod from payment types
}

export interface CryptoSellRequest {
  currency: CryptoCurrency;
  amount: number;
}

export interface CryptoSendRequest {
  currency: CryptoCurrency;
  toAddress: string;
  amount: number;
  memo?: string;
  feeSpeed?: FeeSpeed;
}

export interface CryptoConvertRequest {
  fromCurrency: CryptoCurrency;
  toCurrency: CryptoCurrency;
  amount: number;
}

export interface CryptoConversionResponse {
  conversionId: string;
  fromCurrency: CryptoCurrency;
  toCurrency: CryptoCurrency;
  fromAmount: number;
  toAmount: number;
  exchangeRate: number;
  fee: number;
  status: CryptoTransactionStatus;
  createdAt: string;
}

// Price and Market Data
export interface CryptoPriceData {
  currency: CryptoCurrency;
  price: number;
  change24h: number;
  changePercent24h: number;
  volume24h: number;
  marketCap: number;
  lastUpdated: string;
}

export interface CryptoPriceHistory {
  timestamp: string;
  price: number;
  volume?: number;
}

export interface NetworkFees {
  slow: number;
  standard: number;
  fast: number;
  currency: CryptoCurrency;
  estimatedConfirmationTime: {
    slow: number;
    standard: number;
    fast: number;
  };
}

export interface NetworkStatus {
  currency: CryptoCurrency;
  isHealthy: boolean;
  currentBlockHeight: number;
  networkFees: NetworkFees;
  lastChecked: string;
}

// Security and Risk Management
export interface FraudScore {
  overallScore: number;
  addressRiskScore: number;
  behavioralScore: number;
  velocityScore: number;
  patternScore: number;
  amountScore: number;
  confidenceLevel: number;
  analysisDetails: string;
  riskFactors: string[];
  recommendations: string[];
}

export interface FraudAssessment {
  transactionId: string;
  userId: string;
  fraudScore: FraudScore;
  riskLevel: RiskLevel;
  recommendedAction: RecommendedAction;
  assessmentTimestamp: string;
}

export enum RecommendedAction {
  ALLOW = 'ALLOW',
  MONITOR = 'MONITOR',
  ADDITIONAL_VERIFICATION = 'ADDITIONAL_VERIFICATION',
  MANUAL_REVIEW = 'MANUAL_REVIEW',
  BLOCK = 'BLOCK',
}

// Multi-Signature Wallet
export interface MultiSigWallet {
  address: string;
  redeemScript?: string;
  scriptType?: string;
  contractType?: string;
  contractAddress?: string;
  requiredSignatures: number;
  totalKeys: number;
  currency: CryptoCurrency;
  userPublicKey: string;
  hotWalletPublicKey: string;
  coldStoragePublicKey: string;
  owners?: string[];
}

export interface HDWalletKeys {
  privateKey: string;
  publicKey: string;
  address: string;
  derivationPath: string;
  chainCode: string;
}

// Staking
export interface StakingInfo {
  currency: CryptoCurrency;
  isSupported: boolean;
  currentAPY: number;
  minimumStake: number;
  lockupPeriod: number;
  stakedAmount: number;
  pendingRewards: number;
  totalRewards: number;
  nextRewardDate?: string;
}

export interface StakingReward {
  rewardId: string;
  currency: CryptoCurrency;
  amount: number;
  usdValue: number;
  earnedDate: string;
  paidDate?: string;
  status: 'PENDING' | 'PAID' | 'CANCELLED';
}

// Portfolio
export interface PortfolioSummary {
  totalValue: number;
  change24h: number;
  changePercent24h: number;
  allocation: PortfolioAllocation[];
  lastUpdated: string;
}

export interface PortfolioAllocation {
  currency: CryptoCurrency;
  percentage: number;
  value: number;
  amount: number;
}

export interface PortfolioHistory {
  timestamp: string;
  value: number;
  change: number;
  changePercent: number;
}

// Analytics
export interface TransactionAnalytics {
  totalTransactions: number;
  totalVolume: number;
  averageTransactionSize: number;
  topCurrencies: Array<{
    currency: CryptoCurrency;
    transactionCount: number;
    volume: number;
  }>;
  monthlyActivity: Array<{
    month: string;
    transactionCount: number;
    volume: number;
  }>;
}

// Error Types
export interface CryptoError {
  code: string;
  message: string;
  details?: any;
}

export class CryptoWalletCreationException extends Error {
  constructor(message: string, public cause?: Error) {
    super(message);
    this.name = 'CryptoWalletCreationException';
  }
}

export class WalletNotFoundException extends Error {
  constructor(message: string) {
    super(message);
    this.name = 'WalletNotFoundException';
  }
}

export class WalletAlreadyExistsException extends Error {
  constructor(message: string) {
    super(message);
    this.name = 'WalletAlreadyExistsException';
  }
}

export class TransactionBlockedException extends Error {
  constructor(message: string) {
    super(message);
    this.name = 'TransactionBlockedException';
  }
}

export class CryptoTransactionException extends Error {
  constructor(message: string, public cause?: Error) {
    super(message);
    this.name = 'CryptoTransactionException';
  }
}

export class AddressGenerationException extends Error {
  constructor(message: string, public cause?: Error) {
    super(message);
    this.name = 'AddressGenerationException';
  }
}

export class BalanceUpdateException extends Error {
  constructor(message: string, public cause?: Error) {
    super(message);
    this.name = 'BalanceUpdateException';
  }
}