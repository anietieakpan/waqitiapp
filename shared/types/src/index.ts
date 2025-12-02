// Comprehensive Type Definitions for Waqiti Application
// This file contains all shared type definitions to ensure type safety across the application

// ============================================
// Core Domain Types
// ============================================

export interface User {
  id: string;
  email: string;
  username: string;
  firstName: string;
  lastName: string;
  phoneNumber: string;
  avatar?: string;
  dateOfBirth?: Date;
  address?: Address;
  kycStatus: KYCStatus;
  mfaEnabled: boolean;
  biometricEnabled: boolean;
  createdAt: Date;
  updatedAt: Date;
  lastLoginAt?: Date;
  preferences: UserPreferences;
  securitySettings: SecuritySettings;
  notificationSettings: NotificationSettings;
  wallets: Wallet[];
  linkedAccounts: LinkedAccount[];
  devices: DeviceInfo[];
  riskScore: number;
  verificationLevel: VerificationLevel;
  metadata: Record<string, unknown>;
}

export interface Address {
  id: string;
  line1: string;
  line2?: string;
  city: string;
  state: string;
  postalCode: string;
  country: string;
  type: 'residential' | 'business' | 'mailing';
  isDefault: boolean;
}

export interface UserPreferences {
  language: string;
  currency: string;
  timezone: string;
  theme: 'light' | 'dark' | 'auto';
  defaultPaymentMethod?: string;
  defaultWallet?: string;
  privacySettings: PrivacySettings;
  communicationPreferences: CommunicationPreferences;
}

export interface SecuritySettings {
  twoFactorEnabled: boolean;
  biometricEnabled: boolean;
  pinEnabled: boolean;
  faceIdEnabled: boolean;
  touchIdEnabled: boolean;
  voiceAuthEnabled: boolean;
  trustedDevices: string[];
  sessionTimeout: number;
  requirePinForTransactions: boolean;
  transactionLimit: number;
  dailyLimit: number;
  monthlyLimit: number;
}

export interface NotificationSettings {
  pushEnabled: boolean;
  emailEnabled: boolean;
  smsEnabled: boolean;
  inAppEnabled: boolean;
  paymentAlerts: boolean;
  securityAlerts: boolean;
  marketingMessages: boolean;
  transactionReceipts: boolean;
  loginAlerts: boolean;
  channels: NotificationChannel[];
}

export interface NotificationChannel {
  id: string;
  name: string;
  type: 'push' | 'email' | 'sms' | 'in-app';
  enabled: boolean;
  preferences: Record<string, boolean>;
}

export interface PrivacySettings {
  profileVisibility: 'public' | 'friends' | 'private';
  transactionVisibility: 'public' | 'friends' | 'private';
  showInSearch: boolean;
  allowFriendRequests: boolean;
  dataSharing: boolean;
  analyticsTracking: boolean;
}

export interface CommunicationPreferences {
  promotionalEmails: boolean;
  transactionalEmails: boolean;
  weeklyDigest: boolean;
  monthlyStatement: boolean;
  productUpdates: boolean;
}

export interface DeviceInfo {
  id: string;
  deviceId: string;
  deviceName: string;
  deviceType: 'ios' | 'android' | 'web';
  osVersion: string;
  appVersion: string;
  fcmToken?: string;
  lastActive: Date;
  trusted: boolean;
  biometricEnabled: boolean;
}

// ============================================
// Transaction Types
// ============================================

export interface Transaction {
  id: string;
  type: TransactionType;
  status: TransactionStatus;
  amount: Money;
  fee?: Money;
  totalAmount: Money;
  sender: TransactionParty;
  recipient: TransactionParty;
  description?: string;
  category?: TransactionCategory;
  tags?: string[];
  reference: string;
  externalReference?: string;
  paymentMethod: PaymentMethod;
  metadata: TransactionMetadata;
  createdAt: Date;
  updatedAt: Date;
  completedAt?: Date;
  failedAt?: Date;
  reversedAt?: Date;
  scheduledFor?: Date;
  recurringId?: string;
  splitBillId?: string;
  attachments?: Attachment[];
  location?: GeoLocation;
  merchantInfo?: MerchantInfo;
  fraudScore: number;
  riskLevel: RiskLevel;
  auditTrail: AuditEntry[];
}

export interface Money {
  amount: number;
  currency: string;
  displayAmount: string;
}

export interface TransactionParty {
  id: string;
  type: 'user' | 'merchant' | 'bank' | 'external';
  name: string;
  avatar?: string;
  accountNumber?: string;
  routingNumber?: string;
  email?: string;
  phoneNumber?: string;
  walletId?: string;
}

export interface TransactionMetadata {
  deviceId: string;
  ipAddress: string;
  location?: GeoLocation;
  userAgent: string;
  sessionId: string;
  correlationId: string;
  processingTime: number;
  retryCount: number;
  errorDetails?: ErrorDetails;
  customFields?: Record<string, unknown>;
}

export interface GeoLocation {
  latitude: number;
  longitude: number;
  accuracy: number;
  address?: string;
  city?: string;
  state?: string;
  country?: string;
  postalCode?: string;
}

export interface MerchantInfo {
  id: string;
  name: string;
  category: string;
  mcc: string;
  logo?: string;
  website?: string;
  phone?: string;
  address?: Address;
  isVerified: boolean;
}

export interface Attachment {
  id: string;
  type: 'image' | 'pdf' | 'receipt';
  url: string;
  thumbnailUrl?: string;
  mimeType: string;
  size: number;
  uploadedAt: Date;
}

export interface AuditEntry {
  id: string;
  action: string;
  performedBy: string;
  timestamp: Date;
  details: Record<string, unknown>;
  ipAddress?: string;
  userAgent?: string;
}

export interface ErrorDetails {
  code: string;
  message: string;
  details?: Record<string, unknown>;
  stackTrace?: string;
}

// ============================================
// Wallet Types
// ============================================

export interface Wallet {
  id: string;
  userId: string;
  type: WalletType;
  balance: Money;
  availableBalance: Money;
  pendingBalance: Money;
  currency: string;
  status: WalletStatus;
  isDefault: boolean;
  limits: WalletLimits;
  linkedAccounts: LinkedAccount[];
  cards: Card[];
  createdAt: Date;
  updatedAt: Date;
  lastActivityAt?: Date;
  metadata: Record<string, unknown>;
}

export interface WalletLimits {
  dailyLimit: Money;
  monthlyLimit: Money;
  transactionLimit: Money;
  withdrawalLimit: Money;
  depositLimit: Money;
}

export interface LinkedAccount {
  id: string;
  type: 'bank' | 'card' | 'crypto' | 'paypal';
  provider: string;
  accountNumber: string;
  routingNumber?: string;
  accountName: string;
  isDefault: boolean;
  isVerified: boolean;
  verifiedAt?: Date;
  capabilities: AccountCapability[];
  metadata: Record<string, unknown>;
}

export interface Card {
  id: string;
  type: 'debit' | 'credit' | 'virtual' | 'physical';
  brand: 'visa' | 'mastercard' | 'amex' | 'discover';
  last4: string;
  expiryMonth: number;
  expiryYear: number;
  holderName: string;
  billingAddress?: Address;
  status: CardStatus;
  isDefault: boolean;
  limits?: CardLimits;
  metadata: Record<string, unknown>;
}

export interface CardLimits {
  dailyLimit: Money;
  atmLimit: Money;
  onlineLimit: Money;
  internationalLimit: Money;
}

// ============================================
// Payment Types
// ============================================

export interface Payment {
  id: string;
  type: PaymentType;
  status: PaymentStatus;
  amount: Money;
  sender: PaymentParty;
  recipient: PaymentParty;
  paymentMethod: PaymentMethod;
  schedule?: PaymentSchedule;
  splitDetails?: SplitPaymentDetails;
  metadata: PaymentMetadata;
  createdAt: Date;
  processedAt?: Date;
  completedAt?: Date;
  failedAt?: Date;
  cancelledAt?: Date;
}

export interface PaymentParty {
  id: string;
  type: 'user' | 'merchant' | 'bank';
  name: string;
  email?: string;
  phoneNumber?: string;
  accountId?: string;
  walletId?: string;
}

export interface PaymentMethod {
  id: string;
  type: PaymentMethodType;
  details: PaymentMethodDetails;
  isDefault: boolean;
  isVerified: boolean;
}

export interface PaymentMethodDetails {
  last4?: string;
  brand?: string;
  bankName?: string;
  accountType?: string;
  walletProvider?: string;
  cryptoAddress?: string;
  network?: string;
}

export interface PaymentSchedule {
  frequency: 'once' | 'daily' | 'weekly' | 'monthly' | 'yearly';
  startDate: Date;
  endDate?: Date;
  nextPaymentDate?: Date;
  occurrences?: number;
  completedOccurrences: number;
}

export interface SplitPaymentDetails {
  totalAmount: Money;
  participants: SplitParticipant[];
  createdBy: string;
  dueDate?: Date;
  notes?: string;
  settled: boolean;
  settledAt?: Date;
}

export interface SplitParticipant {
  userId: string;
  name: string;
  amount: Money;
  paid: boolean;
  paidAt?: Date;
  paymentId?: string;
}

export interface PaymentMetadata {
  referenceId: string;
  description?: string;
  category?: string;
  tags?: string[];
  location?: GeoLocation;
  deviceInfo?: DeviceInfo;
  fraudCheckResult?: FraudCheckResult;
  customFields?: Record<string, unknown>;
}

export interface FraudCheckResult {
  score: number;
  riskLevel: RiskLevel;
  checks: FraudCheck[];
  decision: 'approve' | 'review' | 'decline';
  reasons?: string[];
}

export interface FraudCheck {
  type: string;
  passed: boolean;
  score: number;
  details?: Record<string, unknown>;
}

// ============================================
// Crypto Types
// ============================================

export interface CryptoWallet {
  id: string;
  userId: string;
  type: 'hot' | 'cold' | 'custodial';
  currency: string;
  network: string;
  address: string;
  balance: CryptoBalance;
  transactions: CryptoTransaction[];
  status: 'active' | 'inactive' | 'frozen';
  createdAt: Date;
  updatedAt: Date;
}

export interface CryptoBalance {
  available: string;
  pending: string;
  locked: string;
  total: string;
  usdValue: number;
}

export interface CryptoTransaction {
  id: string;
  type: 'send' | 'receive' | 'buy' | 'sell' | 'swap';
  status: 'pending' | 'confirmed' | 'failed';
  amount: string;
  currency: string;
  fee: string;
  hash: string;
  confirmations: number;
  fromAddress: string;
  toAddress: string;
  network: string;
  metadata: Record<string, unknown>;
  createdAt: Date;
  confirmedAt?: Date;
}

export interface CryptoPrice {
  symbol: string;
  price: number;
  change24h: number;
  change7d: number;
  marketCap: number;
  volume24h: number;
  lastUpdated: Date;
}

export interface CryptoOrder {
  id: string;
  userId: string;
  type: 'market' | 'limit' | 'stop';
  side: 'buy' | 'sell';
  status: 'pending' | 'filled' | 'cancelled';
  symbol: string;
  amount: string;
  price?: string;
  filledAmount?: string;
  averagePrice?: string;
  fee: string;
  createdAt: Date;
  filledAt?: Date;
  cancelledAt?: Date;
}

// ============================================
// Banking Types
// ============================================

export interface BankAccount {
  id: string;
  userId: string;
  institutionId: string;
  institutionName: string;
  accountNumber: string;
  routingNumber: string;
  accountType: 'checking' | 'savings' | 'credit' | 'loan';
  balance: Money;
  availableBalance: Money;
  status: 'active' | 'inactive' | 'closed';
  isVerified: boolean;
  verificationMethod?: 'micro-deposit' | 'instant' | 'manual';
  verifiedAt?: Date;
  capabilities: AccountCapability[];
  metadata: Record<string, unknown>;
}

export interface CheckDeposit {
  id: string;
  userId: string;
  accountId: string;
  amount: Money;
  checkNumber: string;
  frontImageUrl: string;
  backImageUrl: string;
  status: CheckDepositStatus;
  micrData?: MICRData;
  ocrData?: OCRData;
  fraudCheckResult?: FraudCheckResult;
  processedAt?: Date;
  clearedAt?: Date;
  rejectedAt?: Date;
  rejectionReason?: string;
  metadata: Record<string, unknown>;
}

export interface MICRData {
  routingNumber: string;
  accountNumber: string;
  checkNumber: string;
  amount?: string;
  confidence: number;
}

export interface OCRData {
  payeeName?: string;
  amount?: string;
  date?: string;
  memo?: string;
  signature?: boolean;
  confidence: number;
}

// ============================================
// Notification Types
// ============================================

export interface Notification {
  id: string;
  userId: string;
  type: NotificationType;
  title: string;
  body: string;
  data?: Record<string, unknown>;
  channels: NotificationChannel[];
  priority: 'low' | 'normal' | 'high' | 'urgent';
  status: 'pending' | 'sent' | 'delivered' | 'read' | 'failed';
  sentAt?: Date;
  deliveredAt?: Date;
  readAt?: Date;
  failedAt?: Date;
  errorDetails?: ErrorDetails;
  metadata: Record<string, unknown>;
}

export interface PushNotification {
  title: string;
  body: string;
  data?: Record<string, unknown>;
  sound?: string;
  badge?: number;
  icon?: string;
  image?: string;
  action?: string;
  category?: string;
  threadId?: string;
  priority: 'low' | 'normal' | 'high';
}

// ============================================
// Support Types
// ============================================

export interface SupportTicket {
  id: string;
  userId: string;
  type: TicketType;
  status: TicketStatus;
  priority: TicketPriority;
  subject: string;
  description: string;
  category: string;
  assignedTo?: string;
  messages: TicketMessage[];
  attachments: Attachment[];
  createdAt: Date;
  updatedAt: Date;
  resolvedAt?: Date;
  closedAt?: Date;
  satisfaction?: number;
  metadata: Record<string, unknown>;
}

export interface TicketMessage {
  id: string;
  ticketId: string;
  senderId: string;
  senderType: 'user' | 'agent' | 'system';
  message: string;
  attachments?: Attachment[];
  isInternal: boolean;
  createdAt: Date;
}

export interface LiveChatSession {
  id: string;
  userId: string;
  agentId?: string;
  status: 'waiting' | 'active' | 'ended';
  messages: ChatMessage[];
  startedAt: Date;
  endedAt?: Date;
  rating?: number;
  feedback?: string;
  metadata: Record<string, unknown>;
}

export interface ChatMessage {
  id: string;
  sessionId: string;
  senderId: string;
  senderType: 'user' | 'agent' | 'bot';
  message: string;
  timestamp: Date;
  delivered: boolean;
  read: boolean;
}

// ============================================
// Enums
// ============================================

export enum TransactionType {
  PAYMENT = 'payment',
  TRANSFER = 'transfer',
  DEPOSIT = 'deposit',
  WITHDRAWAL = 'withdrawal',
  REFUND = 'refund',
  FEE = 'fee',
  ADJUSTMENT = 'adjustment',
  CRYPTO_BUY = 'crypto_buy',
  CRYPTO_SELL = 'crypto_sell',
  CRYPTO_SWAP = 'crypto_swap',
  BILL_PAYMENT = 'bill_payment',
  SPLIT_BILL = 'split_bill',
  REQUEST_MONEY = 'request_money'
}

export enum TransactionStatus {
  PENDING = 'pending',
  PROCESSING = 'processing',
  COMPLETED = 'completed',
  FAILED = 'failed',
  CANCELLED = 'cancelled',
  REVERSED = 'reversed',
  EXPIRED = 'expired',
  ON_HOLD = 'on_hold'
}

export enum TransactionCategory {
  FOOD = 'food',
  TRANSPORTATION = 'transportation',
  SHOPPING = 'shopping',
  ENTERTAINMENT = 'entertainment',
  BILLS = 'bills',
  HEALTHCARE = 'healthcare',
  EDUCATION = 'education',
  TRAVEL = 'travel',
  PERSONAL = 'personal',
  BUSINESS = 'business',
  OTHER = 'other'
}

export enum PaymentType {
  SEND_MONEY = 'send_money',
  REQUEST_MONEY = 'request_money',
  BILL_PAYMENT = 'bill_payment',
  SPLIT_BILL = 'split_bill',
  RECURRING = 'recurring',
  SCHEDULED = 'scheduled',
  INSTANT = 'instant',
  INTERNATIONAL = 'international'
}

export enum PaymentStatus {
  PENDING = 'pending',
  PROCESSING = 'processing',
  COMPLETED = 'completed',
  FAILED = 'failed',
  CANCELLED = 'cancelled',
  REFUNDED = 'refunded',
  EXPIRED = 'expired'
}

export enum PaymentMethodType {
  BANK_ACCOUNT = 'bank_account',
  DEBIT_CARD = 'debit_card',
  CREDIT_CARD = 'credit_card',
  WALLET = 'wallet',
  CRYPTO = 'crypto',
  APPLE_PAY = 'apple_pay',
  GOOGLE_PAY = 'google_pay',
  PAYPAL = 'paypal',
  CASH = 'cash'
}

export enum WalletType {
  PERSONAL = 'personal',
  BUSINESS = 'business',
  SAVINGS = 'savings',
  CRYPTO = 'crypto',
  REWARDS = 'rewards'
}

export enum WalletStatus {
  ACTIVE = 'active',
  INACTIVE = 'inactive',
  SUSPENDED = 'suspended',
  CLOSED = 'closed',
  FROZEN = 'frozen'
}

export enum CardStatus {
  ACTIVE = 'active',
  INACTIVE = 'inactive',
  BLOCKED = 'blocked',
  EXPIRED = 'expired',
  LOST = 'lost',
  STOLEN = 'stolen'
}

export enum KYCStatus {
  NOT_STARTED = 'not_started',
  IN_PROGRESS = 'in_progress',
  PENDING_REVIEW = 'pending_review',
  APPROVED = 'approved',
  REJECTED = 'rejected',
  EXPIRED = 'expired'
}

export enum VerificationLevel {
  UNVERIFIED = 'unverified',
  BASIC = 'basic',
  INTERMEDIATE = 'intermediate',
  ADVANCED = 'advanced',
  PREMIUM = 'premium'
}

export enum RiskLevel {
  LOW = 'low',
  MEDIUM = 'medium',
  HIGH = 'high',
  CRITICAL = 'critical'
}

export enum AccountCapability {
  ACH = 'ach',
  WIRE = 'wire',
  INSTANT = 'instant',
  INTERNATIONAL = 'international',
  DEPOSIT = 'deposit',
  WITHDRAWAL = 'withdrawal'
}

export enum CheckDepositStatus {
  PENDING = 'pending',
  PROCESSING = 'processing',
  PENDING_REVIEW = 'pending_review',
  APPROVED = 'approved',
  REJECTED = 'rejected',
  CLEARED = 'cleared',
  RETURNED = 'returned'
}

export enum NotificationType {
  PAYMENT_RECEIVED = 'payment_received',
  PAYMENT_SENT = 'payment_sent',
  PAYMENT_FAILED = 'payment_failed',
  REQUEST_RECEIVED = 'request_received',
  REQUEST_APPROVED = 'request_approved',
  REQUEST_DECLINED = 'request_declined',
  SECURITY_ALERT = 'security_alert',
  LOGIN_ALERT = 'login_alert',
  PROMOTIONAL = 'promotional',
  SYSTEM = 'system'
}

export enum TicketType {
  TECHNICAL = 'technical',
  BILLING = 'billing',
  ACCOUNT = 'account',
  TRANSACTION = 'transaction',
  SECURITY = 'security',
  FEATURE_REQUEST = 'feature_request',
  COMPLAINT = 'complaint',
  GENERAL = 'general'
}

export enum TicketStatus {
  OPEN = 'open',
  IN_PROGRESS = 'in_progress',
  WAITING_FOR_USER = 'waiting_for_user',
  ESCALATED = 'escalated',
  RESOLVED = 'resolved',
  CLOSED = 'closed'
}

export enum TicketPriority {
  LOW = 'low',
  MEDIUM = 'medium',
  HIGH = 'high',
  URGENT = 'urgent',
  CRITICAL = 'critical'
}

// ============================================
// API Response Types
// ============================================

export interface ApiResponse<T> {
  success: boolean;
  data?: T;
  error?: ApiError;
  metadata?: ResponseMetadata;
}

export interface ApiError {
  code: string;
  message: string;
  details?: Record<string, unknown>;
  timestamp: Date;
  path?: string;
  method?: string;
}

export interface ResponseMetadata {
  timestamp: Date;
  requestId: string;
  duration: number;
  version: string;
}

export interface PaginatedResponse<T> {
  items: T[];
  total: number;
  page: number;
  pageSize: number;
  hasNext: boolean;
  hasPrevious: boolean;
}

export interface FilterOptions {
  search?: string;
  sortBy?: string;
  sortOrder?: 'asc' | 'desc';
  page?: number;
  pageSize?: number;
  filters?: Record<string, unknown>;
}

// ============================================
// Form Types
// ============================================

export interface FormField {
  name: string;
  label: string;
  type: 'text' | 'email' | 'password' | 'number' | 'tel' | 'select' | 'checkbox' | 'radio' | 'textarea';
  value: unknown;
  required?: boolean;
  disabled?: boolean;
  placeholder?: string;
  validation?: ValidationRule[];
  options?: SelectOption[];
  error?: string;
}

export interface ValidationRule {
  type: 'required' | 'email' | 'minLength' | 'maxLength' | 'pattern' | 'custom';
  value?: unknown;
  message: string;
  validator?: (value: unknown) => boolean;
}

export interface SelectOption {
  value: string | number;
  label: string;
  disabled?: boolean;
}

// ============================================
// Chart/Analytics Types
// ============================================

export interface ChartData {
  labels: string[];
  datasets: Dataset[];
}

export interface Dataset {
  label: string;
  data: number[];
  backgroundColor?: string | string[];
  borderColor?: string | string[];
  borderWidth?: number;
}

export interface AnalyticsData {
  period: string;
  metrics: Metric[];
  charts: Chart[];
  insights: Insight[];
}

export interface Metric {
  name: string;
  value: number | string;
  change?: number;
  changeType?: 'increase' | 'decrease' | 'neutral';
  icon?: string;
}

export interface Chart {
  id: string;
  type: 'line' | 'bar' | 'pie' | 'doughnut' | 'area';
  title: string;
  data: ChartData;
  options?: Record<string, unknown>;
}

export interface Insight {
  id: string;
  type: 'info' | 'warning' | 'success' | 'error';
  title: string;
  description: string;
  actionable?: boolean;
  action?: InsightAction;
}

export interface InsightAction {
  label: string;
  handler: () => void;
}

// ============================================
// Voice/AR Types
// ============================================

export interface VoiceCommand {
  id: string;
  command: string;
  intent: string;
  parameters: Record<string, unknown>;
  confidence: number;
  timestamp: Date;
}

export interface ARSession {
  id: string;
  userId: string;
  type: 'payment' | 'visualization' | 'scanner';
  status: 'active' | 'paused' | 'ended';
  startedAt: Date;
  endedAt?: Date;
  interactions: ARInteraction[];
  metadata: Record<string, unknown>;
}

export interface ARInteraction {
  id: string;
  type: string;
  timestamp: Date;
  data: Record<string, unknown>;
}

// Export all types as default
export default {
  // Re-export all types for convenience
};