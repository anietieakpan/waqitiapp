import { createSlice, createAsyncThunk, PayloadAction } from '@reduxjs/toolkit';
import mlService, {
  FraudDetectionResult,
  SpendingInsights,
  PersonalizedRecommendations,
  CreditScorePrediction,
  CustomerSegment,
  TransactionCategorization,
  ModelPerformance,
  BatchPredictionResult,
  Recommendation
} from '../../services/mlService';

interface MLState {
  // Fraud Detection
  fraudDetection: {
    recentResults: FraudDetectionResult[];
    lastResult: FraudDetectionResult | null;
    history: FraudDetectionResult[];
  };
  
  // Spending Insights
  spendingInsights: SpendingInsights | null;
  categoryInsights: { [category: string]: any };
  spendingPredictions: any | null;
  
  // Recommendations
  recommendations: PersonalizedRecommendations | null;
  dismissedRecommendations: string[];
  acceptedRecommendations: string[];
  
  // Credit Score
  creditPrediction: CreditScorePrediction | null;
  creditFactors: any[];
  creditSimulation: any | null;
  
  // Customer Segmentation
  customerSegment: CustomerSegment | null;
  segmentInsights: { [segment: string]: any };
  
  // Transaction Categorization
  categorizations: { [transactionId: string]: TransactionCategorization };
  recurringTransactions: TransactionCategorization[];
  
  // Batch Predictions
  batchJobs: { [jobId: string]: BatchPredictionResult };
  activeBatchJobs: string[];
  
  // Model Performance
  modelPerformance: { [modelName: string]: ModelPerformance };
  
  // Loading States
  loading: {
    fraud: boolean;
    insights: boolean;
    recommendations: boolean;
    credit: boolean;
    segmentation: boolean;
    categorization: boolean;
    batch: boolean;
    models: boolean;
  };
  
  // Error State
  error: string | null;
}

const initialState: MLState = {
  fraudDetection: {
    recentResults: [],
    lastResult: null,
    history: []
  },
  spendingInsights: null,
  categoryInsights: {},
  spendingPredictions: null,
  recommendations: null,
  dismissedRecommendations: [],
  acceptedRecommendations: [],
  creditPrediction: null,
  creditFactors: [],
  creditSimulation: null,
  customerSegment: null,
  segmentInsights: {},
  categorizations: {},
  recurringTransactions: [],
  batchJobs: {},
  activeBatchJobs: [],
  modelPerformance: {},
  loading: {
    fraud: false,
    insights: false,
    recommendations: false,
    credit: false,
    segmentation: false,
    categorization: false,
    batch: false,
    models: false
  },
  error: null
};

// Async thunks
// Fraud Detection
export const detectFraud = createAsyncThunk(
  'ml/detectFraud',
  async (transactionData: {
    amount: number;
    merchantId?: string;
    location?: string;
    deviceId?: string;
    metadata?: Record<string, any>;
  }) => {
    return await mlService.detectFraud(transactionData);
  }
);

export const reportFraud = createAsyncThunk(
  'ml/reportFraud',
  async ({ transactionId, isFraud, details }: {
    transactionId: string;
    isFraud: boolean;
    details?: string;
  }) => {
    await mlService.reportFraud(transactionId, isFraud, details);
    return { transactionId, isFraud };
  }
);

export const fetchFraudHistory = createAsyncThunk(
  'ml/fetchFraudHistory',
  async ({ customerId, limit }: { customerId?: string; limit?: number }) => {
    return await mlService.getFraudHistory(customerId, limit);
  }
);

// Spending Insights
export const fetchSpendingInsights = createAsyncThunk(
  'ml/fetchSpendingInsights',
  async (period: string = '30d') => {
    return await mlService.getSpendingInsights(period);
  }
);

export const fetchCategoryInsights = createAsyncThunk(
  'ml/fetchCategoryInsights',
  async ({ category, period }: { category: string; period?: string }) => {
    const insights = await mlService.getCategoryInsights(category, period);
    return { category, insights };
  }
);

export const predictSpending = createAsyncThunk(
  'ml/predictSpending',
  async (months: number = 3) => {
    return await mlService.predictSpending(months);
  }
);

// Recommendations
export const fetchRecommendations = createAsyncThunk(
  'ml/fetchRecommendations',
  async (type?: string) => {
    return await mlService.getRecommendations(type);
  }
);

export const dismissRecommendation = createAsyncThunk(
  'ml/dismissRecommendation',
  async ({ recommendationId, reason }: { recommendationId: string; reason?: string }) => {
    await mlService.dismissRecommendation(recommendationId, reason);
    return recommendationId;
  }
);

export const acceptRecommendation = createAsyncThunk(
  'ml/acceptRecommendation',
  async (recommendationId: string) => {
    await mlService.acceptRecommendation(recommendationId);
    return recommendationId;
  }
);

// Credit Score
export const predictCreditScore = createAsyncThunk(
  'ml/predictCreditScore',
  async (timeframe: string = '6m') => {
    return await mlService.predictCreditScore(timeframe);
  }
);

export const fetchCreditFactors = createAsyncThunk(
  'ml/fetchCreditFactors',
  async () => {
    return await mlService.getCreditFactors();
  }
);

export const simulateCreditAction = createAsyncThunk(
  'ml/simulateCreditAction',
  async (action: { type: string; value: number }) => {
    return await mlService.simulateCreditAction(action);
  }
);

// Customer Segmentation
export const fetchCustomerSegment = createAsyncThunk(
  'ml/fetchCustomerSegment',
  async () => {
    return await mlService.getCustomerSegment();
  }
);

export const fetchSegmentInsights = createAsyncThunk(
  'ml/fetchSegmentInsights',
  async (segment: string) => {
    const insights = await mlService.getSegmentInsights(segment);
    return { segment, insights };
  }
);

// Transaction Categorization
export const categorizeTransaction = createAsyncThunk(
  'ml/categorizeTransaction',
  async (transaction: {
    description: string;
    amount: number;
    date: string;
    merchantId?: string;
  }) => {
    return await mlService.categorizeTransaction(transaction);
  }
);

export const recategorizeTransaction = createAsyncThunk(
  'ml/recategorizeTransaction',
  async ({ transactionId, newCategory, feedback }: {
    transactionId: string;
    newCategory: string;
    feedback?: string;
  }) => {
    await mlService.recategorizeTransaction(transactionId, newCategory, feedback);
    return { transactionId, newCategory };
  }
);

export const detectRecurringTransactions = createAsyncThunk(
  'ml/detectRecurringTransactions',
  async (minConfidence: number = 0.8) => {
    return await mlService.detectRecurringTransactions(minConfidence);
  }
);

// Batch Predictions
export const createBatchPrediction = createAsyncThunk(
  'ml/createBatchPrediction',
  async (request: any) => {
    return await mlService.createBatchPrediction(request);
  }
);

export const fetchBatchPredictionStatus = createAsyncThunk(
  'ml/fetchBatchPredictionStatus',
  async (jobId: string) => {
    return await mlService.getBatchPredictionStatus(jobId);
  }
);

// Model Performance
export const fetchModelPerformance = createAsyncThunk(
  'ml/fetchModelPerformance',
  async (modelName: string) => {
    const performance = await mlService.getModelPerformance(modelName);
    return { modelName, performance };
  }
);

export const fetchAllModelsPerformance = createAsyncThunk(
  'ml/fetchAllModelsPerformance',
  async () => {
    return await mlService.getAllModelsPerformance();
  }
);

const mlSlice = createSlice({
  name: 'ml',
  initialState,
  reducers: {
    clearError: (state) => {
      state.error = null;
    },
    updateCategorization: (state, action: PayloadAction<{
      transactionId: string;
      categorization: TransactionCategorization;
    }>) => {
      state.categorizations[action.payload.transactionId] = action.payload.categorization;
    },
    removeBatchJob: (state, action: PayloadAction<string>) => {
      delete state.batchJobs[action.payload];
      state.activeBatchJobs = state.activeBatchJobs.filter(id => id !== action.payload);
    },
    clearFraudHistory: (state) => {
      state.fraudDetection.history = [];
    }
  },
  extraReducers: (builder) => {
    // Fraud Detection
    builder
      .addCase(detectFraud.pending, (state) => {
        state.loading.fraud = true;
      })
      .addCase(detectFraud.fulfilled, (state, action) => {
        state.loading.fraud = false;
        state.fraudDetection.lastResult = action.payload;
        state.fraudDetection.recentResults.unshift(action.payload);
        if (state.fraudDetection.recentResults.length > 10) {
          state.fraudDetection.recentResults.pop();
        }
      })
      .addCase(detectFraud.rejected, (state, action) => {
        state.loading.fraud = false;
        state.error = action.error.message || 'Failed to detect fraud';
      });

    builder
      .addCase(fetchFraudHistory.fulfilled, (state, action) => {
        state.fraudDetection.history = action.payload;
      });

    // Spending Insights
    builder
      .addCase(fetchSpendingInsights.pending, (state) => {
        state.loading.insights = true;
      })
      .addCase(fetchSpendingInsights.fulfilled, (state, action) => {
        state.loading.insights = false;
        state.spendingInsights = action.payload;
      })
      .addCase(fetchSpendingInsights.rejected, (state, action) => {
        state.loading.insights = false;
        state.error = action.error.message || 'Failed to fetch spending insights';
      });

    builder
      .addCase(fetchCategoryInsights.fulfilled, (state, action) => {
        state.categoryInsights[action.payload.category] = action.payload.insights;
      });

    builder
      .addCase(predictSpending.fulfilled, (state, action) => {
        state.spendingPredictions = action.payload;
      });

    // Recommendations
    builder
      .addCase(fetchRecommendations.pending, (state) => {
        state.loading.recommendations = true;
      })
      .addCase(fetchRecommendations.fulfilled, (state, action) => {
        state.loading.recommendations = false;
        state.recommendations = action.payload;
      })
      .addCase(fetchRecommendations.rejected, (state, action) => {
        state.loading.recommendations = false;
        state.error = action.error.message || 'Failed to fetch recommendations';
      });

    builder
      .addCase(dismissRecommendation.fulfilled, (state, action) => {
        state.dismissedRecommendations.push(action.payload);
        if (state.recommendations) {
          state.recommendations.recommendations = state.recommendations.recommendations
            .filter(r => r.id !== action.payload);
        }
      });

    builder
      .addCase(acceptRecommendation.fulfilled, (state, action) => {
        state.acceptedRecommendations.push(action.payload);
        if (state.recommendations) {
          state.recommendations.recommendations = state.recommendations.recommendations
            .filter(r => r.id !== action.payload);
        }
      });

    // Credit Score
    builder
      .addCase(predictCreditScore.pending, (state) => {
        state.loading.credit = true;
      })
      .addCase(predictCreditScore.fulfilled, (state, action) => {
        state.loading.credit = false;
        state.creditPrediction = action.payload;
      })
      .addCase(predictCreditScore.rejected, (state, action) => {
        state.loading.credit = false;
        state.error = action.error.message || 'Failed to predict credit score';
      });

    builder
      .addCase(fetchCreditFactors.fulfilled, (state, action) => {
        state.creditFactors = action.payload;
      });

    builder
      .addCase(simulateCreditAction.fulfilled, (state, action) => {
        state.creditSimulation = action.payload;
      });

    // Customer Segmentation
    builder
      .addCase(fetchCustomerSegment.pending, (state) => {
        state.loading.segmentation = true;
      })
      .addCase(fetchCustomerSegment.fulfilled, (state, action) => {
        state.loading.segmentation = false;
        state.customerSegment = action.payload;
      })
      .addCase(fetchCustomerSegment.rejected, (state, action) => {
        state.loading.segmentation = false;
        state.error = action.error.message || 'Failed to fetch customer segment';
      });

    builder
      .addCase(fetchSegmentInsights.fulfilled, (state, action) => {
        state.segmentInsights[action.payload.segment] = action.payload.insights;
      });

    // Transaction Categorization
    builder
      .addCase(categorizeTransaction.pending, (state) => {
        state.loading.categorization = true;
      })
      .addCase(categorizeTransaction.fulfilled, (state, action) => {
        state.loading.categorization = false;
        state.categorizations[action.payload.transactionId] = action.payload;
      });

    builder
      .addCase(recategorizeTransaction.fulfilled, (state, action) => {
        if (state.categorizations[action.payload.transactionId]) {
          state.categorizations[action.payload.transactionId].category = action.payload.newCategory;
        }
      });

    builder
      .addCase(detectRecurringTransactions.fulfilled, (state, action) => {
        state.recurringTransactions = action.payload;
      });

    // Batch Predictions
    builder
      .addCase(createBatchPrediction.pending, (state) => {
        state.loading.batch = true;
      })
      .addCase(createBatchPrediction.fulfilled, (state, action) => {
        state.loading.batch = false;
        state.batchJobs[action.payload.jobId] = action.payload;
        state.activeBatchJobs.push(action.payload.jobId);
      });

    builder
      .addCase(fetchBatchPredictionStatus.fulfilled, (state, action) => {
        state.batchJobs[action.payload.jobId] = action.payload;
        if (action.payload.status === 'COMPLETED' || action.payload.status === 'FAILED') {
          state.activeBatchJobs = state.activeBatchJobs.filter(id => id !== action.payload.jobId);
        }
      });

    // Model Performance
    builder
      .addCase(fetchModelPerformance.fulfilled, (state, action) => {
        state.modelPerformance[action.payload.modelName] = action.payload.performance;
      });

    builder
      .addCase(fetchAllModelsPerformance.pending, (state) => {
        state.loading.models = true;
      })
      .addCase(fetchAllModelsPerformance.fulfilled, (state, action) => {
        state.loading.models = false;
        action.payload.forEach(performance => {
          state.modelPerformance[performance.modelName] = performance;
        });
      });
  }
});

export const { 
  clearError, 
  updateCategorization, 
  removeBatchJob, 
  clearFraudHistory 
} = mlSlice.actions;

export default mlSlice.reducer;