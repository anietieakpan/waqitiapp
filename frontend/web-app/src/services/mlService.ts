import axios from 'axios';
import { getAuthToken } from '../utils/auth';

const API_BASE_URL = process.env.REACT_APP_API_BASE_URL || 'http://localhost:8080';
const ML_SERVICE_URL = `${API_BASE_URL}/api/v1/ml`;

export interface FraudDetectionResult {
  transactionId: string;
  riskScore: number;
  riskLevel: 'LOW' | 'MEDIUM' | 'HIGH' | 'CRITICAL';
  fraudProbability: number;
  factors: RiskFactor[];
  recommendation: 'APPROVE' | 'REVIEW' | 'DECLINE' | 'ADDITIONAL_VERIFICATION';
  explanations: string[];
  similarFraudPatterns?: FraudPattern[];
  confidence: number;
}

export interface RiskFactor {
  factor: string;
  weight: number;
  description: string;
  impact: 'POSITIVE' | 'NEGATIVE' | 'NEUTRAL';
}

export interface FraudPattern {
  patternId: string;
  patternType: string;
  similarity: number;
  description: string;
  occurrences: number;
}

export interface SpendingInsights {
  customerId: string;
  period: string;
  totalSpent: number;
  averageTransactionAmount: number;
  topCategories: CategorySpending[];
  spendingTrends: SpendingTrend[];
  anomalies: SpendingAnomaly[];
  predictedNextMonthSpending: number;
  savingsOpportunities: SavingsOpportunity[];
  peerComparison?: PeerComparison;
}

export interface CategorySpending {
  category: string;
  amount: number;
  percentage: number;
  transactionCount: number;
  trend: 'INCREASING' | 'STABLE' | 'DECREASING';
  monthOverMonthChange: number;
}

export interface SpendingTrend {
  date: string;
  amount: number;
  predictedAmount?: number;
  categories: { [key: string]: number };
}

export interface SpendingAnomaly {
  date: string;
  category: string;
  amount: number;
  expectedAmount: number;
  deviation: number;
  severity: 'LOW' | 'MEDIUM' | 'HIGH';
  explanation: string;
}

export interface SavingsOpportunity {
  category: string;
  potentialSavings: number;
  recommendation: string;
  confidence: number;
  actions: string[];
}

export interface PeerComparison {
  peerGroup: string;
  percentile: number;
  averagePeerSpending: number;
  insights: string[];
}

export interface PersonalizedRecommendations {
  customerId: string;
  recommendations: Recommendation[];
  generatedAt: string;
}

export interface Recommendation {
  id: string;
  type: 'PRODUCT' | 'FEATURE' | 'SAVINGS' | 'INVESTMENT' | 'SECURITY' | 'USAGE';
  title: string;
  description: string;
  benefit: string;
  priority: 'LOW' | 'MEDIUM' | 'HIGH';
  relevanceScore: number;
  actionUrl?: string;
  expiresAt?: string;
  metadata?: Record<string, any>;
}

export interface CreditScorePrediction {
  customerId: string;
  currentScore?: number;
  predictedScore: number;
  confidence: number;
  timeframe: string;
  factors: CreditFactor[];
  recommendations: CreditRecommendation[];
  scoreTrajectory: ScorePoint[];
}

export interface CreditFactor {
  factor: string;
  currentValue: number;
  optimalValue: number;
  impact: number;
  improvementPotential: number;
}

export interface CreditRecommendation {
  action: string;
  impact: number;
  timeToEffect: string;
  difficulty: 'EASY' | 'MEDIUM' | 'HARD';
  priority: number;
}

export interface ScorePoint {
  date: string;
  score: number;
  confidence: number;
}

export interface CustomerSegment {
  customerId: string;
  segment: string;
  subSegment?: string;
  characteristics: string[];
  behaviorProfile: BehaviorProfile;
  lifetimeValue: number;
  churnRisk: number;
  nextBestActions: string[];
}

export interface BehaviorProfile {
  activityLevel: 'LOW' | 'MEDIUM' | 'HIGH' | 'VERY_HIGH';
  preferredChannels: string[];
  productUsage: { [product: string]: number };
  engagementScore: number;
  loyaltyScore: number;
}

export interface TransactionCategorization {
  transactionId: string;
  originalDescription: string;
  category: string;
  subCategory?: string;
  confidence: number;
  merchant?: MerchantInfo;
  tags: string[];
  isRecurring: boolean;
  recurringPattern?: RecurringPattern;
}

export interface MerchantInfo {
  name: string;
  cleanName: string;
  category: string;
  logo?: string;
  website?: string;
  phoneNumber?: string;
}

export interface RecurringPattern {
  frequency: 'DAILY' | 'WEEKLY' | 'BIWEEKLY' | 'MONTHLY' | 'QUARTERLY' | 'YEARLY';
  averageAmount: number;
  nextExpectedDate: string;
  confidence: number;
}

export interface ModelPerformance {
  modelName: string;
  version: string;
  accuracy: number;
  precision: number;
  recall: number;
  f1Score: number;
  lastTrainedDate: string;
  dataPoints: number;
  performanceTrends: PerformanceTrend[];
  confusionMatrix?: number[][];
}

export interface PerformanceTrend {
  date: string;
  metric: string;
  value: number;
}

export interface BatchPredictionRequest {
  modelType: 'FRAUD' | 'CREDIT_SCORE' | 'CHURN' | 'CATEGORIZATION';
  items: any[];
  options?: {
    includeExplanations?: boolean;
    confidenceThreshold?: number;
    maxResults?: number;
  };
}

export interface BatchPredictionResult {
  jobId: string;
  status: 'PENDING' | 'PROCESSING' | 'COMPLETED' | 'FAILED';
  progress: number;
  totalItems: number;
  processedItems: number;
  results?: any[];
  error?: string;
  startedAt: string;
  completedAt?: string;
}

class MLService {
  private getHeaders() {
    const token = getAuthToken();
    return {
      'Content-Type': 'application/json',
      'Authorization': `Bearer ${token}`
    };
  }

  // Fraud Detection
  async detectFraud(transactionData: {
    amount: number;
    merchantId?: string;
    location?: string;
    deviceId?: string;
    metadata?: Record<string, any>;
  }): Promise<FraudDetectionResult> {
    const response = await axios.post(
      `${ML_SERVICE_URL}/fraud/detect`,
      transactionData,
      { headers: this.getHeaders() }
    );
    return response.data;
  }

  async reportFraud(transactionId: string, isFraud: boolean, details?: string): Promise<void> {
    await axios.post(
      `${ML_SERVICE_URL}/fraud/report`,
      { transactionId, isFraud, details },
      { headers: this.getHeaders() }
    );
  }

  async getFraudHistory(customerId?: string, limit: number = 50): Promise<FraudDetectionResult[]> {
    const params = { customerId, limit };
    const response = await axios.get(
      `${ML_SERVICE_URL}/fraud/history`,
      { headers: this.getHeaders(), params }
    );
    return response.data;
  }

  // Spending Insights
  async getSpendingInsights(period: string = '30d'): Promise<SpendingInsights> {
    const response = await axios.get(
      `${ML_SERVICE_URL}/insights/spending`,
      { headers: this.getHeaders(), params: { period } }
    );
    return response.data;
  }

  async getCategoryInsights(category: string, period: string = '30d'): Promise<{
    insights: SpendingInsights;
    predictions: SpendingTrend[];
    recommendations: string[];
  }> {
    const response = await axios.get(
      `${ML_SERVICE_URL}/insights/category/${category}`,
      { headers: this.getHeaders(), params: { period } }
    );
    return response.data;
  }

  async predictSpending(months: number = 3): Promise<{
    predictions: SpendingTrend[];
    confidence: number;
    factors: string[];
  }> {
    const response = await axios.get(
      `${ML_SERVICE_URL}/insights/predict`,
      { headers: this.getHeaders(), params: { months } }
    );
    return response.data;
  }

  // Personalized Recommendations
  async getRecommendations(type?: string): Promise<PersonalizedRecommendations> {
    const params = type ? { type } : {};
    const response = await axios.get(
      `${ML_SERVICE_URL}/recommendations`,
      { headers: this.getHeaders(), params }
    );
    return response.data;
  }

  async dismissRecommendation(recommendationId: string, reason?: string): Promise<void> {
    await axios.post(
      `${ML_SERVICE_URL}/recommendations/${recommendationId}/dismiss`,
      { reason },
      { headers: this.getHeaders() }
    );
  }

  async acceptRecommendation(recommendationId: string): Promise<void> {
    await axios.post(
      `${ML_SERVICE_URL}/recommendations/${recommendationId}/accept`,
      {},
      { headers: this.getHeaders() }
    );
  }

  // Credit Score Prediction
  async predictCreditScore(timeframe: string = '6m'): Promise<CreditScorePrediction> {
    const response = await axios.get(
      `${ML_SERVICE_URL}/credit/predict`,
      { headers: this.getHeaders(), params: { timeframe } }
    );
    return response.data;
  }

  async getCreditFactors(): Promise<CreditFactor[]> {
    const response = await axios.get(
      `${ML_SERVICE_URL}/credit/factors`,
      { headers: this.getHeaders() }
    );
    return response.data;
  }

  async simulateCreditAction(action: {
    type: string;
    value: number;
  }): Promise<{
    currentScore: number;
    simulatedScore: number;
    impact: number;
    timeToEffect: string;
  }> {
    const response = await axios.post(
      `${ML_SERVICE_URL}/credit/simulate`,
      action,
      { headers: this.getHeaders() }
    );
    return response.data;
  }

  // Customer Segmentation
  async getCustomerSegment(): Promise<CustomerSegment> {
    const response = await axios.get(
      `${ML_SERVICE_URL}/segmentation/me`,
      { headers: this.getHeaders() }
    );
    return response.data;
  }

  async getSegmentInsights(segment: string): Promise<{
    description: string;
    size: number;
    characteristics: string[];
    trends: any[];
  }> {
    const response = await axios.get(
      `${ML_SERVICE_URL}/segmentation/insights/${segment}`,
      { headers: this.getHeaders() }
    );
    return response.data;
  }

  // Transaction Categorization
  async categorizeTransaction(transaction: {
    description: string;
    amount: number;
    date: string;
    merchantId?: string;
  }): Promise<TransactionCategorization> {
    const response = await axios.post(
      `${ML_SERVICE_URL}/categorize`,
      transaction,
      { headers: this.getHeaders() }
    );
    return response.data;
  }

  async recategorizeTransaction(
    transactionId: string,
    newCategory: string,
    feedback?: string
  ): Promise<void> {
    await axios.post(
      `${ML_SERVICE_URL}/categorize/${transactionId}/update`,
      { newCategory, feedback },
      { headers: this.getHeaders() }
    );
  }

  async detectRecurringTransactions(
    minConfidence: number = 0.8
  ): Promise<TransactionCategorization[]> {
    const response = await axios.get(
      `${ML_SERVICE_URL}/categorize/recurring`,
      { headers: this.getHeaders(), params: { minConfidence } }
    );
    return response.data;
  }

  // Batch Predictions
  async createBatchPrediction(request: BatchPredictionRequest): Promise<BatchPredictionResult> {
    const response = await axios.post(
      `${ML_SERVICE_URL}/batch/predict`,
      request,
      { headers: this.getHeaders() }
    );
    return response.data;
  }

  async getBatchPredictionStatus(jobId: string): Promise<BatchPredictionResult> {
    const response = await axios.get(
      `${ML_SERVICE_URL}/batch/${jobId}`,
      { headers: this.getHeaders() }
    );
    return response.data;
  }

  async getBatchPredictionResults(jobId: string, offset: number = 0, limit: number = 100): Promise<{
    results: any[];
    total: number;
    hasMore: boolean;
  }> {
    const response = await axios.get(
      `${ML_SERVICE_URL}/batch/${jobId}/results`,
      { headers: this.getHeaders(), params: { offset, limit } }
    );
    return response.data;
  }

  // Model Performance
  async getModelPerformance(modelName: string): Promise<ModelPerformance> {
    const response = await axios.get(
      `${ML_SERVICE_URL}/models/${modelName}/performance`,
      { headers: this.getHeaders() }
    );
    return response.data;
  }

  async getAllModelsPerformance(): Promise<ModelPerformance[]> {
    const response = await axios.get(
      `${ML_SERVICE_URL}/models/performance`,
      { headers: this.getHeaders() }
    );
    return response.data;
  }

  // Feature Store
  async getFeatureImportance(modelName: string): Promise<{
    features: Array<{
      name: string;
      importance: number;
      description: string;
    }>;
  }> {
    const response = await axios.get(
      `${ML_SERVICE_URL}/models/${modelName}/features`,
      { headers: this.getHeaders() }
    );
    return response.data;
  }
}

export default new MLService();