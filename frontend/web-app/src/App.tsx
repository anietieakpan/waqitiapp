import React, { lazy, Suspense } from 'react';
import { BrowserRouter as Router, Routes, Route, Navigate } from 'react-router-dom';
import { Provider } from 'react-redux';
import { QueryClient, QueryClientProvider } from 'react-query';
import { ThemeProvider, CssBaseline, Box, CircularProgress } from '@mui/material';
import { Toaster } from 'react-hot-toast';

import { store } from '@/store/store';
import { theme } from '@/theme/theme';
import { AuthProvider } from '@/contexts/AuthContext';
import { StripeProvider } from '@/providers/StripeProvider';
import ProtectedRoute from '@/components/auth/ProtectedRoute';

// ============================================================================
// LAZY LOADED PAGES (Performance Optimization)
// ============================================================================
// Auth pages - lazy load as they're only needed before login
const LoginPage = lazy(() => import('@/pages/auth/LoginPage'));
const RegisterPage = lazy(() => import('@/pages/auth/RegisterPage'));

// Layout
const AppLayout = lazy(() => import('@/components/layout/AppLayout'));

// Protected pages - lazy load to reduce initial bundle size
const Dashboard = lazy(() => import('@/pages/dashboard/Dashboard'));
const WalletPage = lazy(() => import('@/pages/wallet/WalletPage'));
const PaymentPage = lazy(() => import('@/pages/payment/PaymentPage'));
const CardsPage = lazy(() => import('@/pages/cards/CardsPage'));
const InvestmentsPage = lazy(() => import('@/pages/investments/InvestmentsPage'));
const CryptoPage = lazy(() => import('@/pages/crypto/CryptoPage'));
const BNPLPage = lazy(() => import('@/pages/bnpl/BNPLPage'));
const SavingsPage = lazy(() => import('@/pages/savings/SavingsPage'));
const LoansPage = lazy(() => import('@/pages/loans/LoansPage'));
const InsurancePage = lazy(() => import('@/pages/insurance/InsurancePage'));
const RewardsPage = lazy(() => import('@/pages/rewards/RewardsPage'));
const KYCPage = lazy(() => import('@/pages/kyc/KYCPage'));
const SecurityPage = lazy(() => import('@/pages/security/SecurityPage'));
const SettingsPage = lazy(() => import('@/pages/settings/SettingsPage'));
const ProfilePage = lazy(() => import('@/pages/profile/ProfilePage'));
const NotificationsPage = lazy(() => import('@/pages/notifications/NotificationsPage'));
const NotFoundPage = lazy(() => import('@/pages/NotFoundPage'));

const queryClient = new QueryClient({
  defaultOptions: {
    queries: {
      retry: 3,
      refetchOnWindowFocus: false,
      staleTime: 5 * 60 * 1000, // 5 minutes
    },
  },
});

// ============================================================================
// LOADING FALLBACK (shown while lazy components load)
// ============================================================================
const LoadingFallback = () => (
  <Box
    display="flex"
    justifyContent="center"
    alignItems="center"
    minHeight="100vh"
    sx={{ backgroundColor: 'background.default' }}
  >
    <CircularProgress size={60} />
  </Box>
);

function App() {
  return (
    <Provider store={store}>
      <QueryClientProvider client={queryClient}>
        <ThemeProvider theme={theme}>
          <CssBaseline />
          <StripeProvider>
            <AuthProvider>
              <Router>
              <div className="App">
                <Suspense fallback={<LoadingFallback />}>
                  <Routes>
                    {/* Public Routes */}
                    <Route path="/login" element={<LoginPage />} />
                    <Route path="/register" element={<RegisterPage />} />

                    {/* Protected Routes with Layout */}
                    <Route
                      path="/"
                      element={
                        <ProtectedRoute>
                          <AppLayout />
                        </ProtectedRoute>
                      }
                    >
                      {/* Redirect root to dashboard */}
                      <Route index element={<Navigate to="/dashboard" replace />} />

                      {/* Main Pages */}
                      <Route path="dashboard" element={<Dashboard />} />
                      <Route path="wallet" element={<WalletPage />} />
                      <Route path="payment/*" element={<PaymentPage />} />
                      <Route path="cards" element={<CardsPage />} />
                      <Route path="investments" element={<InvestmentsPage />} />
                      <Route path="crypto" element={<CryptoPage />} />

                      {/* Financial Services */}
                      <Route path="bnpl" element={<BNPLPage />} />
                      <Route path="savings" element={<SavingsPage />} />
                      <Route path="loans" element={<LoansPage />} />
                      <Route path="insurance" element={<InsurancePage />} />
                      <Route path="rewards" element={<RewardsPage />} />

                      {/* Account */}
                      <Route path="profile" element={<ProfilePage />} />
                      <Route path="notifications" element={<NotificationsPage />} />
                      <Route path="kyc" element={<KYCPage />} />
                      <Route path="security" element={<SecurityPage />} />
                      <Route path="settings/*" element={<SettingsPage />} />
                    </Route>

                    {/* 404 Route */}
                    <Route path="*" element={<NotFoundPage />} />
                  </Routes>
                </Suspense>
              </div>
              </Router>
            </AuthProvider>
          </StripeProvider>
          <Toaster
            position="top-right"
            toastOptions={{
              duration: 4000,
              style: {
                background: '#333',
                color: '#fff',
              },
            }}
          />
        </ThemeProvider>
      </QueryClientProvider>
    </Provider>
  );
}

export default App;