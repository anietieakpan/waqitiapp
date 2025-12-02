import { configureStore } from '@reduxjs/toolkit';
import authReducer from './auth/authSlice';
import walletReducer from './slices/walletSlice';
import transactionReducer from './slices/transactionSlice';
import notificationReducer from './slices/notificationSlice';
import socialPaymentReducer from './slices/socialPaymentSlice';
import recurringPaymentReducer from './slices/recurringPaymentSlice';
import investmentReducer from './slices/investmentSlice';
import complianceReducer from './slices/complianceSlice';
import mlReducer from './slices/mlSlice';
import eventSourcingReducer from './slices/eventSourcingSlice';
import reportingReducer from './slices/reportingSlice';
import paymentReducer from './slices/paymentSlice';
import userReducer from './slices/userSlice';
import cardReducer from './slices/cardSlice';
import cryptoReducer from './slices/cryptoSlice';
import bnplReducer from './slices/bnplSlice';

export const store = configureStore({
  reducer: {
    auth: authReducer,
    wallet: walletReducer,
    transaction: transactionReducer,
    notification: notificationReducer,
    socialPayment: socialPaymentReducer,
    recurringPayment: recurringPaymentReducer,
    investment: investmentReducer,
    compliance: complianceReducer,
    ml: mlReducer,
    eventSourcing: eventSourcingReducer,
    reporting: reportingReducer,
    payment: paymentReducer,
    user: userReducer,
    card: cardReducer,
    crypto: cryptoReducer,
    bnpl: bnplReducer,
  },
  middleware: (getDefaultMiddleware) =>
    getDefaultMiddleware({
      serializableCheck: {
        ignoredActions: ['persist/PERSIST'],
      },
    }),
});

export type RootState = ReturnType<typeof store.getState>;
export type AppDispatch = typeof store.dispatch;