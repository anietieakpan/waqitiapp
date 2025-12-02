import { configureStore } from '@reduxjs/toolkit';
import { TypedUseSelectorHook, useDispatch, useSelector } from 'react-redux';

import authReducer from './slices/authSlice';
import dashboardReducer from './slices/dashboardSlice';
import usersReducer from './slices/usersSlice';
import transactionsReducer from './slices/transactionsSlice';
import complianceReducer from './slices/complianceSlice';
import securityReducer from './slices/securitySlice';
import analyticsReducer from './slices/analyticsSlice';
import systemReducer from './slices/systemSlice';
import notificationsReducer from './slices/notificationsSlice';
import settingsReducer from './slices/settingsSlice';

export const store = configureStore({
  reducer: {
    auth: authReducer,
    dashboard: dashboardReducer,
    users: usersReducer,
    transactions: transactionsReducer,
    compliance: complianceReducer,
    security: securityReducer,
    analytics: analyticsReducer,
    system: systemReducer,
    notifications: notificationsReducer,
    settings: settingsReducer,
  },
  middleware: (getDefaultMiddleware) =>
    getDefaultMiddleware({
      serializableCheck: {
        ignoredActions: ['auth/setCredentials'],
      },
    }),
});

export type RootState = ReturnType<typeof store.getState>;
export type AppDispatch = typeof store.dispatch;

export const useAppDispatch = () => useDispatch<AppDispatch>();
export const useAppSelector: TypedUseSelectorHook<RootState> = useSelector;