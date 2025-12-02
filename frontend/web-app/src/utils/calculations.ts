/**
 * Comprehensive calculation utilities for the Waqiti application
 */

// Percentage calculations
export const calculatePercentageChange = (oldValue: number, newValue: number): number => {
  if (oldValue === 0) {
    return newValue > 0 ? 100 : 0;
  }
  return ((newValue - oldValue) / Math.abs(oldValue)) * 100;
};

export const calculatePercentageOf = (value: number, total: number): number => {
  if (total === 0) return 0;
  return (value / total) * 100;
};

// Financial calculations
export const calculateCompoundInterest = (
  principal: number,
  rate: number,
  compoundFrequency: number,
  timeInYears: number
): number => {
  return principal * Math.pow(1 + rate / compoundFrequency, compoundFrequency * timeInYears);
};

export const calculateSimpleInterest = (
  principal: number,
  rate: number,
  timeInYears: number
): number => {
  return principal * (1 + rate * timeInYears);
};

export const calculateMonthlyPayment = (
  principal: number,
  annualRate: number,
  monthsToPayOff: number
): number => {
  const monthlyRate = annualRate / 12;
  if (monthlyRate === 0) return principal / monthsToPayOff;
  
  return (
    (principal * monthlyRate * Math.pow(1 + monthlyRate, monthsToPayOff)) /
    (Math.pow(1 + monthlyRate, monthsToPayOff) - 1)
  );
};

// Transaction fee calculations
export const calculateTransactionFee = (
  amount: number,
  feeStructure: {
    percentage?: number;
    fixed?: number;
    minimum?: number;
    maximum?: number;
  }
): number => {
  const { percentage = 0, fixed = 0, minimum = 0, maximum = Infinity } = feeStructure;
  
  let fee = (amount * percentage / 100) + fixed;
  fee = Math.max(fee, minimum);
  fee = Math.min(fee, maximum);
  
  return Math.round(fee * 100) / 100; // Round to 2 decimal places
};

// Exchange rate calculations
export const convertCurrency = (
  amount: number,
  fromRate: number,
  toRate: number
): number => {
  if (fromRate === 0) return 0;
  return (amount / fromRate) * toRate;
};

export const calculateExchangeRateSpread = (
  buyRate: number,
  sellRate: number
): number => {
  if (sellRate === 0) return 0;
  return ((buyRate - sellRate) / sellRate) * 100;
};

// Investment calculations
export const calculateReturn = (
  initialInvestment: number,
  currentValue: number
): { absolute: number; percentage: number } => {
  const absolute = currentValue - initialInvestment;
  const percentage = calculatePercentageChange(initialInvestment, currentValue);
  
  return { absolute, percentage };
};

export const calculateAnnualizedReturn = (
  initialValue: number,
  finalValue: number,
  timeInYears: number
): number => {
  if (initialValue === 0 || timeInYears === 0) return 0;
  return (Math.pow(finalValue / initialValue, 1 / timeInYears) - 1) * 100;
};

// Risk calculations
export const calculateRiskScore = (
  factors: {
    amount: number;
    velocity: number; // transactions per day
    geography: 'domestic' | 'international';
    paymentMethod: 'bank' | 'card' | 'crypto';
    userHistory: number; // days since account creation
    previousDisputes: number;
  }
): number => {
  let score = 0;
  
  // Amount risk (0-30 points)
  if (factors.amount > 10000) score += 30;
  else if (factors.amount > 5000) score += 20;
  else if (factors.amount > 1000) score += 10;
  else if (factors.amount > 100) score += 5;
  
  // Velocity risk (0-25 points)
  if (factors.velocity > 50) score += 25;
  else if (factors.velocity > 20) score += 15;
  else if (factors.velocity > 10) score += 10;
  else if (factors.velocity > 5) score += 5;
  
  // Geography risk (0-15 points)
  if (factors.geography === 'international') score += 15;
  
  // Payment method risk (0-20 points)
  if (factors.paymentMethod === 'crypto') score += 20;
  else if (factors.paymentMethod === 'card') score += 10;
  else if (factors.paymentMethod === 'bank') score += 5;
  
  // User history risk (0-10 points)
  if (factors.userHistory < 7) score += 10;
  else if (factors.userHistory < 30) score += 5;
  else if (factors.userHistory < 90) score += 2;
  
  // Dispute history (0-20 points)
  score += Math.min(factors.previousDisputes * 5, 20);
  
  return Math.min(score, 100); // Cap at 100
};

// Budgeting calculations
export const calculateBudgetVariance = (
  budgeted: number,
  actual: number
): { amount: number; percentage: number; status: 'under' | 'over' | 'ontrack' } => {
  const amount = actual - budgeted;
  const percentage = calculatePercentageChange(budgeted, actual);
  
  let status: 'under' | 'over' | 'ontrack' = 'ontrack';
  if (Math.abs(percentage) > 10) {
    status = amount > 0 ? 'over' : 'under';
  }
  
  return { amount, percentage, status };
};

export const calculateSavingsRate = (
  income: number,
  expenses: number
): number => {
  if (income === 0) return 0;
  return ((income - expenses) / income) * 100;
};

// Goal calculations
export const calculateGoalProgress = (
  currentAmount: number,
  targetAmount: number,
  targetDate: Date
): {
  percentageComplete: number;
  amountRemaining: number;
  daysRemaining: number;
  dailyRequiredSavings: number;
  monthlyRequiredSavings: number;
  onTrack: boolean;
} => {
  const percentageComplete = calculatePercentageOf(currentAmount, targetAmount);
  const amountRemaining = Math.max(0, targetAmount - currentAmount);
  
  const now = new Date();
  const daysRemaining = Math.max(0, Math.ceil((targetDate.getTime() - now.getTime()) / (1000 * 60 * 60 * 24)));
  
  const dailyRequiredSavings = daysRemaining > 0 ? amountRemaining / daysRemaining : 0;
  const monthlyRequiredSavings = dailyRequiredSavings * 30;
  
  // Calculate if on track (assuming linear progress)
  const expectedProgress = daysRemaining > 0 ? 
    ((new Date().getTime() - (targetDate.getTime() - (daysRemaining * 24 * 60 * 60 * 1000))) / 
     (targetDate.getTime() - (targetDate.getTime() - (daysRemaining * 24 * 60 * 60 * 1000)))) * 100 : 100;
  
  const onTrack = percentageComplete >= expectedProgress * 0.9; // 10% tolerance
  
  return {
    percentageComplete,
    amountRemaining,
    daysRemaining,
    dailyRequiredSavings,
    monthlyRequiredSavings,
    onTrack,
  };
};

// Statistical calculations
export const calculateAverage = (values: number[]): number => {
  if (values.length === 0) return 0;
  return values.reduce((sum, value) => sum + value, 0) / values.length;
};

export const calculateMedian = (values: number[]): number => {
  if (values.length === 0) return 0;
  
  const sorted = [...values].sort((a, b) => a - b);
  const middle = Math.floor(sorted.length / 2);
  
  if (sorted.length % 2 === 0) {
    return (sorted[middle - 1] + sorted[middle]) / 2;
  }
  return sorted[middle];
};

export const calculateStandardDeviation = (values: number[]): number => {
  if (values.length === 0) return 0;
  
  const average = calculateAverage(values);
  const squaredDifferences = values.map(value => Math.pow(value - average, 2));
  const variance = calculateAverage(squaredDifferences);
  
  return Math.sqrt(variance);
};

// Spending pattern calculations
export const calculateSpendingVelocity = (
  transactions: Array<{ amount: number; date: Date }>,
  periodDays: number = 30
): number => {
  const cutoffDate = new Date();
  cutoffDate.setDate(cutoffDate.getDate() - periodDays);
  
  const recentTransactions = transactions.filter(tx => tx.date >= cutoffDate);
  const totalSpent = recentTransactions.reduce((sum, tx) => sum + tx.amount, 0);
  
  return totalSpent / periodDays;
};

export const calculateSpendingTrend = (
  weeklySpending: number[]
): 'increasing' | 'decreasing' | 'stable' => {
  if (weeklySpending.length < 2) return 'stable';
  
  const recentAvg = calculateAverage(weeklySpending.slice(-2));
  const previousAvg = calculateAverage(weeklySpending.slice(-4, -2));
  
  const change = calculatePercentageChange(previousAvg, recentAvg);
  
  if (change > 10) return 'increasing';
  if (change < -10) return 'decreasing';
  return 'stable';
};

// Merchant analysis
export const calculateMerchantFrequency = (
  transactions: Array<{ merchantId: string; amount: number; date: Date }>,
  merchantId: string,
  periodDays: number = 30
): { frequency: number; averageAmount: number; totalSpent: number } => {
  const cutoffDate = new Date();
  cutoffDate.setDate(cutoffDate.getDate() - periodDays);
  
  const merchantTransactions = transactions.filter(
    tx => tx.merchantId === merchantId && tx.date >= cutoffDate
  );
  
  const frequency = merchantTransactions.length;
  const totalSpent = merchantTransactions.reduce((sum, tx) => sum + tx.amount, 0);
  const averageAmount = frequency > 0 ? totalSpent / frequency : 0;
  
  return { frequency, averageAmount, totalSpent };
};

// Cashflow calculations
export const calculateCashFlow = (
  income: number[],
  expenses: number[]
): { netCashFlow: number[]; cumulativeCashFlow: number[]; averageMonthly: number } => {
  const minLength = Math.min(income.length, expenses.length);
  const netCashFlow: number[] = [];
  const cumulativeCashFlow: number[] = [];
  
  let cumulative = 0;
  for (let i = 0; i < minLength; i++) {
    const net = income[i] - expenses[i];
    netCashFlow.push(net);
    cumulative += net;
    cumulativeCashFlow.push(cumulative);
  }
  
  const averageMonthly = calculateAverage(netCashFlow);
  
  return { netCashFlow, cumulativeCashFlow, averageMonthly };
};

// Debt calculations
export const calculateDebtToIncomeRatio = (
  monthlyDebtPayments: number,
  monthlyIncome: number
): number => {
  if (monthlyIncome === 0) return 0;
  return (monthlyDebtPayments / monthlyIncome) * 100;
};

export const calculatePayoffTime = (
  currentBalance: number,
  monthlyPayment: number,
  annualInterestRate: number
): { months: number; totalInterest: number; totalPaid: number } => {
  if (monthlyPayment <= 0) return { months: 0, totalInterest: 0, totalPaid: 0 };
  
  const monthlyRate = annualInterestRate / 12 / 100;
  let balance = currentBalance;
  let months = 0;
  let totalInterest = 0;
  
  while (balance > 0.01 && months < 600) { // Cap at 50 years
    const interestPayment = balance * monthlyRate;
    const principalPayment = Math.min(monthlyPayment - interestPayment, balance);
    
    if (principalPayment <= 0) break; // Payment too small to cover interest
    
    balance -= principalPayment;
    totalInterest += interestPayment;
    months++;
  }
  
  const totalPaid = currentBalance + totalInterest;
  
  return { months, totalInterest, totalPaid };
};

// Utility functions
export const roundToDecimals = (value: number, decimals: number = 2): number => {
  return Math.round(value * Math.pow(10, decimals)) / Math.pow(10, decimals);
};

export const clamp = (value: number, min: number, max: number): number => {
  return Math.min(Math.max(value, min), max);
};

export const interpolate = (value: number, inMin: number, inMax: number, outMin: number, outMax: number): number => {
  return ((value - inMin) / (inMax - inMin)) * (outMax - outMin) + outMin;
};