/**
 * Redux Store Configuration
 * Combines all slices into a centralized state management store
 */

import { configureStore, combineReducers } from '@reduxjs/toolkit';
import { persistStore, persistReducer } from 'redux-persist';
import AsyncStorage from '@react-native-async-storage/async-storage';
import { FLUSH, REHYDRATE, PAUSE, PERSIST, PURGE, REGISTER } from 'redux-persist';

// Import all slice reducers
import authReducer from './slices/authSlice';
import walletReducer from './slices/walletSlice';
import transactionReducer from './slices/transactionSlice';
import settingsReducer from './slices/settingsSlice';
import securityReducer from './slices/securitySlice';
import paymentsReducer from './slices/paymentsSlice';
import contactsReducer from './slices/contactsSlice';
import cryptoReducer from './slices/cryptoSlice';
import investmentReducer from './slices/investmentSlice';

// Redux persist configuration
const persistConfig = {
  key: 'root',
  storage: AsyncStorage,
  version: 1,
  // Only persist certain slices
  whitelist: [
    'auth',
    'settings', 
    'security',
    'contacts'
  ],
  // Don't persist sensitive or frequently changing data
  blacklist: [
    'payments', // Contains sensitive transaction data
    'crypto',   // Real-time market data
    'investment' // Real-time market data
  ],
};

// Auth slice persist config (minimal persistence for security)
const authPersistConfig = {
  key: 'auth',
  storage: AsyncStorage,
  whitelist: [
    'isOnboardingComplete',
    'biometricEnabled',
    'pinEnabled',
    'deviceId'
  ],
  blacklist: [
    'accessToken',    // Security: Don't persist tokens
    'refreshToken',   // Security: Don't persist tokens
    'user',          // Security: Don't persist user data
    'isAuthenticated' // Will be determined on app startup
  ],
};

// Settings slice persist config
const settingsPersistConfig = {
  key: 'settings',
  storage: AsyncStorage,
  // Persist all settings
};

// Security slice persist config
const securityPersistConfig = {
  key: 'security',
  storage: AsyncStorage,
  whitelist: [
    'biometricEnabled',
    'pinEnabled',
    'autoLockEnabled',
    'sessionTimeout',
    'socialSettings'
  ],
  blacklist: [
    'trustedDevices', // Security: Don't persist device list
    'securityAlerts', // Real-time data
    'loginHistory'    // Sensitive data
  ],
};

// Contacts slice persist config
const contactsPersistConfig = {
  key: 'contacts',
  storage: AsyncStorage,
  whitelist: [
    'phoneContactsSynced',
    'phoneContactsPermission',
    'socialSettings'
  ],
  blacklist: [
    'contacts',      // Will be fetched from server
    'phoneContacts', // Large dataset, cache separately
    'socialFeed'     // Real-time data
  ],
};

// Combine reducers with persistence
const rootReducer = combineReducers({
  auth: persistReducer(authPersistConfig, authReducer),
  wallet: walletReducer,
  transaction: transactionReducer,
  settings: persistReducer(settingsPersistConfig, settingsReducer),
  security: persistReducer(securityPersistConfig, securityReducer),
  payments: paymentsReducer,
  contacts: persistReducer(contactsPersistConfig, contactsReducer),
  crypto: cryptoReducer,
  investment: investmentReducer,
});

const persistedReducer = persistReducer(persistConfig, rootReducer);

// Configure store
export const store = configureStore({
  reducer: persistedReducer,
  middleware: (getDefaultMiddleware) =>
    getDefaultMiddleware({
      serializableCheck: {
        // Ignore redux-persist actions
        ignoredActions: [FLUSH, REHYDRATE, PAUSE, PERSIST, PURGE, REGISTER],
        // Ignore non-serializable values in specific paths
        ignoredPaths: [
          'register', // redux-persist
          'rehydrate', // redux-persist
        ],
      },
      // Enable additional checks in development
      immutableCheck: __DEV__,
      thunk: {
        extraArgument: {
          // Add any extra arguments needed by thunks
        },
      },
    }),
  // Enable Redux DevTools in development
  devTools: __DEV__,
});

// Create persistor
export const persistor = persistStore(store);

// Export types for TypeScript
export type RootState = ReturnType<typeof store.getState>;
export type AppDispatch = typeof store.dispatch;

// Typed hooks for components
import { useDispatch, useSelector, TypedUseSelectorHook } from 'react-redux';

export const useAppDispatch = () => useDispatch<AppDispatch>();
export const useAppSelector: TypedUseSelectorHook<RootState> = useSelector;

// Selector utilities for common state access patterns
export const selectAuth = (state: RootState) => state.auth;
export const selectWallet = (state: RootState) => state.wallet;
export const selectTransactions = (state: RootState) => state.transaction;
export const selectSettings = (state: RootState) => state.settings;
export const selectSecurity = (state: RootState) => state.security;
export const selectPayments = (state: RootState) => state.payments;
export const selectContacts = (state: RootState) => state.contacts;
export const selectCrypto = (state: RootState) => state.crypto;
export const selectInvestment = (state: RootState) => state.investment;

// Derived selectors for computed state
export const selectIsAuthenticated = (state: RootState) => 
  state.auth.isAuthenticated && !!state.auth.accessToken;

export const selectUserProfile = (state: RootState) => state.auth.user;

export const selectTotalBalance = (state: RootState) => {
  const walletBalance = state.wallet.accounts.reduce((total, account) => 
    total + account.balance, 0);
  const cryptoBalance = state.crypto.portfolio?.totalValueUSD || 0;
  const investmentBalance = state.investment.portfolio?.totalValue || 0;
  return walletBalance + cryptoBalance + investmentBalance;
};

export const selectRecentTransactions = (state: RootState) => {
  const paymentTransactions = state.payments.transactions.slice(0, 10);
  const cryptoTransactions = state.crypto.trades.slice(0, 10);
  const investmentTrades = state.investment.trades.slice(0, 10);
  
  return [...paymentTransactions, ...cryptoTransactions, ...investmentTrades]
    .sort((a, b) => new Date(b.createdAt || b.executedAt).getTime() - 
                   new Date(a.createdAt || a.executedAt).getTime())
    .slice(0, 20);
};

export const selectUnreadNotifications = (state: RootState) => {
  const securityAlerts = state.security.unreadAlertsCount || 0;
  const paymentRequests = state.payments.incomingRequests.filter(r => r.status === 'pending').length;
  const contactRequests = state.contacts.sentInvitations.filter(i => i.status === 'sent').length;
  return securityAlerts + paymentRequests + contactRequests;
};

export const selectPortfolioSummary = (state: RootState) => {
  const cryptoValue = state.crypto.portfolio?.totalValueUSD || 0;
  const investmentValue = state.investment.portfolio?.totalValue || 0;
  const walletValue = state.wallet.accounts.reduce((total, account) => 
    total + account.balance, 0);
  
  const totalValue = cryptoValue + investmentValue + walletValue;
  
  const cryptoDayChange = state.crypto.portfolio?.dayChange || 0;
  const investmentDayChange = state.investment.portfolio?.dayChange || 0;
  const totalDayChange = cryptoDayChange + investmentDayChange;
  const totalDayChangePercent = totalValue > 0 ? (totalDayChange / totalValue) * 100 : 0;
  
  return {
    totalValue,
    dayChange: totalDayChange,
    dayChangePercent: totalDayChangePercent,
    breakdown: {
      wallet: { value: walletValue, percentage: totalValue > 0 ? (walletValue / totalValue) * 100 : 0 },
      crypto: { value: cryptoValue, percentage: totalValue > 0 ? (cryptoValue / totalValue) * 100 : 0 },
      investment: { value: investmentValue, percentage: totalValue > 0 ? (investmentValue / totalValue) * 100 : 0 },
    },
  };
};

// Store cleanup and reset utilities
export const resetStore = () => {
  persistor.purge();
  store.dispatch({ type: 'RESET_STORE' });
};

// Development utilities
if (__DEV__) {
  // Log store state changes in development
  store.subscribe(() => {
    // console.log('Store updated:', store.getState());
  });
}

export default store;