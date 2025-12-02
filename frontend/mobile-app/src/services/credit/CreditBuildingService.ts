/**
 * Credit Building Service
 * Helps users build and monitor their credit through responsible financial behavior
 * Includes credit reporting, score monitoring, and improvement recommendations
 */

import { Platform } from 'react-native';
import AsyncStorage from '@react-native-async-storage/async-storage';
import { ApiService } from '../ApiService';
import { NotificationService } from '../NotificationService';
import { format, subMonths, differenceInDays } from 'date-fns';

// Credit score models
export enum CreditScoreModel {
  FICO = 'FICO',
  VANTAGE = 'VantageScore',
  EXPERIAN_PLUS = 'Experian PLUS',
}

// Credit bureaus
export enum CreditBureau {
  EXPERIAN = 'Experian',
  EQUIFAX = 'Equifax',
  TRANSUNION = 'TransUnion',
}

// Credit account types
export enum CreditAccountType {
  CREDIT_BUILDER_LOAN = 'credit_builder_loan',
  SECURED_CREDIT_CARD = 'secured_credit_card',
  AUTHORIZED_USER = 'authorized_user',
  RENT_REPORTING = 'rent_reporting',
  UTILITY_REPORTING = 'utility_reporting',
  BILL_REPORTING = 'bill_reporting',
}

// Credit score ranges
export interface CreditScoreRange {
  min: number;
  max: number;
  category: 'Poor' | 'Fair' | 'Good' | 'Very Good' | 'Excellent';
  description: string;
  color: string;
}

// Credit score data
export interface CreditScore {
  score: number;
  model: CreditScoreModel;
  bureau: CreditBureau;
  date: string;
  change: {
    value: number;
    period: '30d' | '60d' | '90d';
    direction: 'up' | 'down' | 'stable';
  };
  range: CreditScoreRange;
  factors: CreditFactor[];
}

// Factors affecting credit score
export interface CreditFactor {
  name: string;
  impact: 'positive' | 'negative' | 'neutral';
  weight: 'high' | 'medium' | 'low';
  score: number; // 0-100
  description: string;
  recommendation?: string;
}

// Credit building account
export interface CreditBuildingAccount {
  id: string;
  type: CreditAccountType;
  status: 'active' | 'pending' | 'closed' | 'suspended';
  openedDate: string;
  accountDetails: {
    accountNumber?: string;
    creditLimit?: number;
    currentBalance?: number;
    availableCredit?: number;
    paymentAmount?: number;
    nextPaymentDue?: string;
    totalPayments?: number;
    onTimePayments?: number;
  };
  reportingStatus: {
    [bureau in CreditBureau]: {
      isReporting: boolean;
      lastReported?: string;
      nextReporting?: string;
    };
  };
  impact: {
    estimatedScoreIncrease: number;
    timeToImpact: string; // e.g., "2-3 months"
    confidence: 'high' | 'medium' | 'low';
  };
}

// Credit report entry
export interface CreditReportEntry {
  date: string;
  bureaus: CreditBureau[];
  type: 'payment' | 'balance_update' | 'account_opening' | 'account_closure';
  description: string;
  impact: 'positive' | 'negative' | 'neutral';
  accountId?: string;
}

// Credit monitoring alert
export interface CreditAlert {
  id: string;
  type: 'score_change' | 'new_account' | 'hard_inquiry' | 'payment_due' | 'milestone';
  severity: 'info' | 'warning' | 'critical';
  title: string;
  message: string;
  date: string;
  isRead: boolean;
  actionRequired: boolean;
  actionUrl?: string;
}

// Credit improvement recommendation
export interface CreditRecommendation {
  id: string;
  priority: 'high' | 'medium' | 'low';
  category: 'payment_history' | 'credit_utilization' | 'credit_mix' | 'credit_age' | 'new_credit';
  title: string;
  description: string;
  estimatedImpact: {
    scoreIncrease: string; // e.g., "+10-20 points"
    timeframe: string; // e.g., "1-2 months"
  };
  actions: {
    label: string;
    actionType: 'internal' | 'external' | 'educational';
    actionData?: any;
  }[];
  difficulty: 'easy' | 'moderate' | 'hard';
  isCompleted: boolean;
}

// Credit building progress
export interface CreditBuildingProgress {
  startDate: string;
  startScore: number;
  currentScore: number;
  targetScore: number;
  milestones: {
    score: number;
    title: string;
    achievedAt?: string;
    reward?: string;
  }[];
  projectedTimeline: {
    date: string;
    projectedScore: number;
    confidence: number; // 0-100
  }[];
}

// Credit simulator scenario
export interface CreditScenario {
  id: string;
  name: string;
  description: string;
  actions: {
    type: string;
    value: any;
  }[];
  impact: {
    scoreChange: number;
    timeframe: string;
    factors: {
      factor: string;
      change: number;
    }[];
  };
}

/**
 * Credit Building Service Implementation
 */
class CreditBuildingService {
  private static instance: CreditBuildingService;
  private readonly STORAGE_KEY = '@credit_building';
  private currentScore: CreditScore | null = null;
  private accounts: CreditBuildingAccount[] = [];
  private isInitialized: boolean = false;

  // Credit score ranges
  private readonly SCORE_RANGES: CreditScoreRange[] = [
    { min: 300, max: 579, category: 'Poor', description: 'Well below average', color: '#FF4444' },
    { min: 580, max: 669, category: 'Fair', description: 'Below average', color: '#FF8800' },
    { min: 670, max: 739, category: 'Good', description: 'Near average', color: '#FFD700' },
    { min: 740, max: 799, category: 'Very Good', description: 'Above average', color: '#90EE90' },
    { min: 800, max: 850, category: 'Excellent', description: 'Well above average', color: '#00FF00' },
  ];

  private constructor() {}

  public static getInstance(): CreditBuildingService {
    if (!CreditBuildingService.instance) {
      CreditBuildingService.instance = new CreditBuildingService();
    }
    return CreditBuildingService.instance;
  }

  /**
   * Initialize credit building service
   */
  async initialize(): Promise<void> {
    if (this.isInitialized) return;

    try {
      console.log('Initializing Credit Building Service...');

      // Load cached data
      await this.loadCachedData();

      // Fetch latest credit score
      await this.refreshCreditScore();

      // Load credit building accounts
      await this.loadCreditAccounts();

      // Schedule monitoring
      this.scheduleMonitoring();

      this.isInitialized = true;
      console.log('Credit Building Service initialized successfully');

    } catch (error) {
      console.error('Failed to initialize Credit Building Service:', error);
      throw error;
    }
  }

  /**
   * Get current credit score
   */
  async getCreditScore(bureau?: CreditBureau): Promise<CreditScore | null> {
    try {
      if (!this.currentScore || this.isScoreOutdated()) {
        await this.refreshCreditScore();
      }

      if (bureau && this.currentScore?.bureau !== bureau) {
        // Fetch score from specific bureau
        const response = await ApiService.get(`/credit/score/${bureau.toLowerCase()}`);
        if (response.success && response.data) {
          return this.processCreditScore(response.data);
        }
      }

      return this.currentScore;
    } catch (error) {
      console.error('Failed to get credit score:', error);
      return this.currentScore;
    }
  }

  /**
   * Get credit scores from all bureaus
   */
  async getAllCreditScores(): Promise<CreditScore[]> {
    try {
      const response = await ApiService.get('/credit/scores/all');
      
      if (!response.success || !response.data) {
        throw new Error('Failed to fetch credit scores');
      }

      return response.data.map((score: any) => this.processCreditScore(score));
    } catch (error) {
      console.error('Failed to get all credit scores:', error);
      
      // Return mock data for development
      return this.getMockCreditScores();
    }
  }

  /**
   * Open a credit building account
   */
  async openCreditBuildingAccount(
    type: CreditAccountType,
    options: {
      amount?: number;
      term?: number;
      autoPayment?: boolean;
    } = {}
  ): Promise<CreditBuildingAccount> {
    try {
      console.log(`Opening ${type} account...`);

      const response = await ApiService.post('/credit/accounts/open', {
        type,
        ...options,
      });

      if (!response.success || !response.data) {
        throw new Error(response.message || 'Failed to open account');
      }

      const account = response.data as CreditBuildingAccount;
      this.accounts.push(account);
      await this.saveAccountsToStorage();

      // Send notification
      await NotificationService.scheduleNotification({
        title: 'Credit Building Account Opened! 🎉',
        body: `Your ${this.getAccountTypeName(type)} is now active and will start building your credit.`,
        data: { type: 'credit_account_opened', accountId: account.id },
      });

      // Track analytics
      await this.trackEvent('credit_account_opened', {
        accountType: type,
        ...options,
      });

      return account;
    } catch (error) {
      console.error('Failed to open credit building account:', error);
      throw error;
    }
  }

  /**
   * Get credit building accounts
   */
  async getCreditBuildingAccounts(): Promise<CreditBuildingAccount[]> {
    if (this.accounts.length === 0) {
      await this.loadCreditAccounts();
    }
    return this.accounts;
  }

  /**
   * Make payment on credit building account
   */
  async makePayment(
    accountId: string,
    amount: number,
    options: {
      paymentMethod?: string;
      scheduledDate?: string;
    } = {}
  ): Promise<boolean> {
    try {
      const account = this.accounts.find(a => a.id === accountId);
      if (!account) {
        throw new Error('Account not found');
      }

      const response = await ApiService.post(`/credit/accounts/${accountId}/payment`, {
        amount,
        ...options,
      });

      if (!response.success) {
        throw new Error(response.message || 'Payment failed');
      }

      // Update account details
      await this.loadCreditAccounts();

      // Track on-time payment
      await this.trackEvent('credit_payment_made', {
        accountId,
        amount,
        onTime: true,
      });

      // Send confirmation
      await NotificationService.scheduleNotification({
        title: 'Payment Successful ✅',
        body: `Your $${amount.toFixed(2)} payment has been processed. Keep up the great work building your credit!`,
        data: { type: 'credit_payment_success', accountId },
      });

      return true;
    } catch (error) {
      console.error('Failed to make payment:', error);
      throw error;
    }
  }

  /**
   * Get credit improvement recommendations
   */
  async getRecommendations(): Promise<CreditRecommendation[]> {
    try {
      const response = await ApiService.get('/credit/recommendations');
      
      if (!response.success || !response.data) {
        throw new Error('Failed to fetch recommendations');
      }

      return response.data;
    } catch (error) {
      console.error('Failed to get recommendations:', error);
      
      // Return mock recommendations for development
      return this.getMockRecommendations();
    }
  }

  /**
   * Get credit building progress
   */
  async getCreditBuildingProgress(): Promise<CreditBuildingProgress> {
    try {
      const response = await ApiService.get('/credit/progress');
      
      if (!response.success || !response.data) {
        throw new Error('Failed to fetch progress');
      }

      return response.data;
    } catch (error) {
      console.error('Failed to get credit building progress:', error);
      
      // Return mock progress for development
      return this.getMockProgress();
    }
  }

  /**
   * Simulate credit score changes
   */
  async simulateScenario(scenario: CreditScenario): Promise<CreditScenario> {
    try {
      const response = await ApiService.post('/credit/simulate', scenario);
      
      if (!response.success || !response.data) {
        throw new Error('Failed to simulate scenario');
      }

      return response.data;
    } catch (error) {
      console.error('Failed to simulate scenario:', error);
      
      // Return mock simulation
      return {
        ...scenario,
        impact: {
          scoreChange: Math.floor(Math.random() * 50) + 10,
          timeframe: '2-3 months',
          factors: [
            { factor: 'Payment History', change: 15 },
            { factor: 'Credit Utilization', change: 10 },
          ],
        },
      };
    }
  }

  /**
   * Get credit monitoring alerts
   */
  async getAlerts(unreadOnly: boolean = false): Promise<CreditAlert[]> {
    try {
      const response = await ApiService.get('/credit/alerts', {
        unreadOnly,
      });
      
      if (!response.success || !response.data) {
        throw new Error('Failed to fetch alerts');
      }

      return response.data;
    } catch (error) {
      console.error('Failed to get credit alerts:', error);
      return [];
    }
  }

  /**
   * Mark alert as read
   */
  async markAlertAsRead(alertId: string): Promise<void> {
    try {
      await ApiService.post(`/credit/alerts/${alertId}/read`);
    } catch (error) {
      console.error('Failed to mark alert as read:', error);
    }
  }

  /**
   * Enable credit monitoring
   */
  async enableMonitoring(options: {
    scoreAlerts: boolean;
    reportAlerts: boolean;
    identityAlerts: boolean;
    frequency: 'daily' | 'weekly' | 'monthly';
  }): Promise<void> {
    try {
      const response = await ApiService.post('/credit/monitoring/enable', options);
      
      if (!response.success) {
        throw new Error('Failed to enable monitoring');
      }

      await AsyncStorage.setItem(`${this.STORAGE_KEY}_monitoring`, JSON.stringify(options));

      await NotificationService.scheduleNotification({
        title: 'Credit Monitoring Activated 🛡️',
        body: 'We\'ll keep an eye on your credit and alert you to any important changes.',
        data: { type: 'credit_monitoring_enabled' },
      });

    } catch (error) {
      console.error('Failed to enable credit monitoring:', error);
      throw error;
    }
  }

  /**
   * Report rent payments to credit bureaus
   */
  async enableRentReporting(landlordInfo: {
    name: string;
    address: string;
    monthlyRent: number;
    leaseStartDate: string;
  }): Promise<void> {
    try {
      const response = await ApiService.post('/credit/rent-reporting/enable', landlordInfo);
      
      if (!response.success) {
        throw new Error('Failed to enable rent reporting');
      }

      // Open rent reporting account
      await this.openCreditBuildingAccount(CreditAccountType.RENT_REPORTING, {
        amount: landlordInfo.monthlyRent,
      });

      await this.trackEvent('rent_reporting_enabled', {
        monthlyRent: landlordInfo.monthlyRent,
      });

    } catch (error) {
      console.error('Failed to enable rent reporting:', error);
      throw error;
    }
  }

  /**
   * Process credit score data
   */
  private processCreditScore(data: any): CreditScore {
    const score = data.score;
    const range = this.getScoreRange(score);
    
    return {
      score,
      model: data.model || CreditScoreModel.FICO,
      bureau: data.bureau || CreditBureau.EXPERIAN,
      date: data.date || new Date().toISOString(),
      change: data.change || {
        value: 0,
        period: '30d',
        direction: 'stable',
      },
      range,
      factors: data.factors || this.getDefaultFactors(score),
    };
  }

  /**
   * Get score range for a given score
   */
  private getScoreRange(score: number): CreditScoreRange {
    return this.SCORE_RANGES.find(range => 
      score >= range.min && score <= range.max
    ) || this.SCORE_RANGES[0];
  }

  /**
   * Get default credit factors
   */
  private getDefaultFactors(score: number): CreditFactor[] {
    return [
      {
        name: 'Payment History',
        impact: score >= 700 ? 'positive' : 'negative',
        weight: 'high',
        score: score >= 700 ? 85 : 60,
        description: 'Your payment history over the past 24 months',
        recommendation: score < 700 ? 'Make all payments on time' : undefined,
      },
      {
        name: 'Credit Utilization',
        impact: score >= 650 ? 'positive' : 'negative',
        weight: 'high',
        score: score >= 650 ? 75 : 45,
        description: 'Percentage of available credit being used',
        recommendation: score < 650 ? 'Keep utilization below 30%' : undefined,
      },
      {
        name: 'Credit Age',
        impact: 'neutral',
        weight: 'medium',
        score: 50,
        description: 'Average age of your credit accounts',
      },
      {
        name: 'Credit Mix',
        impact: score >= 700 ? 'positive' : 'neutral',
        weight: 'low',
        score: score >= 700 ? 70 : 50,
        description: 'Variety of credit account types',
      },
      {
        name: 'New Credit',
        impact: 'neutral',
        weight: 'low',
        score: 65,
        description: 'Recent credit inquiries and new accounts',
      },
    ];
  }

  /**
   * Refresh credit score from API
   */
  private async refreshCreditScore(): Promise<void> {
    try {
      const response = await ApiService.get('/credit/score/current');
      
      if (response.success && response.data) {
        this.currentScore = this.processCreditScore(response.data);
        await this.saveCreditScoreToStorage();
      }
    } catch (error) {
      console.error('Failed to refresh credit score:', error);
    }
  }

  /**
   * Load credit building accounts
   */
  private async loadCreditAccounts(): Promise<void> {
    try {
      const response = await ApiService.get('/credit/accounts');
      
      if (response.success && response.data) {
        this.accounts = response.data;
        await this.saveAccountsToStorage();
      }
    } catch (error) {
      console.error('Failed to load credit accounts:', error);
    }
  }

  /**
   * Check if score is outdated
   */
  private isScoreOutdated(): boolean {
    if (!this.currentScore) return true;
    
    const scoreDate = new Date(this.currentScore.date);
    const daysSinceUpdate = differenceInDays(new Date(), scoreDate);
    
    return daysSinceUpdate > 30; // Refresh if older than 30 days
  }

  /**
   * Get account type display name
   */
  private getAccountTypeName(type: CreditAccountType): string {
    const names: Record<CreditAccountType, string> = {
      [CreditAccountType.CREDIT_BUILDER_LOAN]: 'Credit Builder Loan',
      [CreditAccountType.SECURED_CREDIT_CARD]: 'Secured Credit Card',
      [CreditAccountType.AUTHORIZED_USER]: 'Authorized User Account',
      [CreditAccountType.RENT_REPORTING]: 'Rent Reporting',
      [CreditAccountType.UTILITY_REPORTING]: 'Utility Reporting',
      [CreditAccountType.BILL_REPORTING]: 'Bill Reporting',
    };
    return names[type] || type;
  }

  /**
   * Schedule credit monitoring
   */
  private scheduleMonitoring(): void {
    // Check for updates daily
    setInterval(async () => {
      await this.checkForUpdates();
    }, 24 * 60 * 60 * 1000); // 24 hours
  }

  /**
   * Check for credit updates
   */
  private async checkForUpdates(): Promise<void> {
    try {
      const response = await ApiService.get('/credit/updates/check');
      
      if (response.success && response.data?.hasUpdates) {
        await this.refreshCreditScore();
        await this.loadCreditAccounts();
        
        // Notify user of changes
        if (response.data.scoreChange) {
          await NotificationService.scheduleNotification({
            title: 'Credit Score Update! 📊',
            body: `Your credit score ${response.data.scoreChange > 0 ? 'increased' : 'decreased'} by ${Math.abs(response.data.scoreChange)} points.`,
            data: { type: 'credit_score_update' },
          });
        }
      }
    } catch (error) {
      console.error('Failed to check for credit updates:', error);
    }
  }

  /**
   * Load cached data
   */
  private async loadCachedData(): Promise<void> {
    try {
      const scoreData = await AsyncStorage.getItem(`${this.STORAGE_KEY}_score`);
      if (scoreData) {
        this.currentScore = JSON.parse(scoreData);
      }

      const accountsData = await AsyncStorage.getItem(`${this.STORAGE_KEY}_accounts`);
      if (accountsData) {
        this.accounts = JSON.parse(accountsData);
      }
    } catch (error) {
      console.error('Failed to load cached data:', error);
    }
  }

  /**
   * Save credit score to storage
   */
  private async saveCreditScoreToStorage(): Promise<void> {
    try {
      if (this.currentScore) {
        await AsyncStorage.setItem(
          `${this.STORAGE_KEY}_score`,
          JSON.stringify(this.currentScore)
        );
      }
    } catch (error) {
      console.error('Failed to save credit score:', error);
    }
  }

  /**
   * Save accounts to storage
   */
  private async saveAccountsToStorage(): Promise<void> {
    try {
      await AsyncStorage.setItem(
        `${this.STORAGE_KEY}_accounts`,
        JSON.stringify(this.accounts)
      );
    } catch (error) {
      console.error('Failed to save accounts:', error);
    }
  }

  /**
   * Get mock credit scores for development
   */
  private getMockCreditScores(): CreditScore[] {
    return [
      this.processCreditScore({
        score: 720,
        model: CreditScoreModel.FICO,
        bureau: CreditBureau.EXPERIAN,
        change: { value: 15, period: '30d', direction: 'up' },
      }),
      this.processCreditScore({
        score: 715,
        model: CreditScoreModel.FICO,
        bureau: CreditBureau.EQUIFAX,
        change: { value: 10, period: '30d', direction: 'up' },
      }),
      this.processCreditScore({
        score: 725,
        model: CreditScoreModel.FICO,
        bureau: CreditBureau.TRANSUNION,
        change: { value: 20, period: '30d', direction: 'up' },
      }),
    ];
  }

  /**
   * Get mock recommendations
   */
  private getMockRecommendations(): CreditRecommendation[] {
    return [
      {
        id: '1',
        priority: 'high',
        category: 'credit_utilization',
        title: 'Lower Your Credit Card Balances',
        description: 'Your credit utilization is at 45%. Reducing it below 30% could significantly boost your score.',
        estimatedImpact: {
          scoreIncrease: '+20-40 points',
          timeframe: '1-2 months',
        },
        actions: [
          {
            label: 'Make Extra Payment',
            actionType: 'internal',
            actionData: { screen: 'PaymentScreen' },
          },
          {
            label: 'Learn More',
            actionType: 'educational',
            actionData: { topic: 'credit_utilization' },
          },
        ],
        difficulty: 'moderate',
        isCompleted: false,
      },
      {
        id: '2',
        priority: 'medium',
        category: 'payment_history',
        title: 'Set Up Automatic Payments',
        description: 'Never miss a payment again with automatic payments. Payment history is 35% of your score.',
        estimatedImpact: {
          scoreIncrease: '+10-20 points',
          timeframe: '3-6 months',
        },
        actions: [
          {
            label: 'Set Up AutoPay',
            actionType: 'internal',
            actionData: { screen: 'AutoPaySetup' },
          },
        ],
        difficulty: 'easy',
        isCompleted: false,
      },
      {
        id: '3',
        priority: 'low',
        category: 'credit_mix',
        title: 'Add a Credit Builder Loan',
        description: 'Diversify your credit mix with a credit builder loan. This shows lenders you can handle different types of credit.',
        estimatedImpact: {
          scoreIncrease: '+5-15 points',
          timeframe: '6-12 months',
        },
        actions: [
          {
            label: 'Apply Now',
            actionType: 'internal',
            actionData: { accountType: CreditAccountType.CREDIT_BUILDER_LOAN },
          },
        ],
        difficulty: 'easy',
        isCompleted: false,
      },
    ];
  }

  /**
   * Get mock progress data
   */
  private getMockProgress(): CreditBuildingProgress {
    const startDate = subMonths(new Date(), 6).toISOString();
    
    return {
      startDate,
      startScore: 650,
      currentScore: 720,
      targetScore: 750,
      milestones: [
        {
          score: 670,
          title: 'Good Credit',
          achievedAt: subMonths(new Date(), 4).toISOString(),
          reward: '🎉 Lower interest rates unlocked',
        },
        {
          score: 700,
          title: 'Very Good Credit',
          achievedAt: subMonths(new Date(), 2).toISOString(),
          reward: '🏆 Premium credit cards available',
        },
        {
          score: 740,
          title: 'Excellent Credit',
          reward: '👑 Best rates and terms',
        },
      ],
      projectedTimeline: [
        {
          date: new Date().toISOString(),
          projectedScore: 720,
          confidence: 100,
        },
        {
          date: format(new Date().setMonth(new Date().getMonth() + 1), 'yyyy-MM-dd'),
          projectedScore: 728,
          confidence: 85,
        },
        {
          date: format(new Date().setMonth(new Date().getMonth() + 2), 'yyyy-MM-dd'),
          projectedScore: 735,
          confidence: 75,
        },
        {
          date: format(new Date().setMonth(new Date().getMonth() + 3), 'yyyy-MM-dd'),
          projectedScore: 742,
          confidence: 65,
        },
        {
          date: format(new Date().setMonth(new Date().getMonth() + 4), 'yyyy-MM-dd'),
          projectedScore: 750,
          confidence: 55,
        },
      ],
    };
  }

  /**
   * Track analytics event
   */
  private async trackEvent(event: string, properties?: Record<string, any>): Promise<void> {
    try {
      await ApiService.trackEvent(`credit_${event}`, {
        ...properties,
        platform: Platform.OS,
        timestamp: new Date().toISOString(),
      });
    } catch (error) {
      console.warn('Failed to track credit event:', error);
    }
  }
}

export default CreditBuildingService.getInstance();