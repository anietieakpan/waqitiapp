import React, { useEffect } from 'react';
import { BrowserRouter as Router, Routes, Route, Navigate } from 'react-router-dom';
import { ThemeProvider, CssBaseline } from '@mui/material';
import { LocalizationProvider } from '@mui/x-date-pickers';
import { AdapterDateFns } from '@mui/x-date-pickers/AdapterDateFns';
import { QueryClient, QueryClientProvider } from '@tanstack/react-query';
import { ReactQueryDevtools } from '@tanstack/react-query-devtools';
import { Provider } from 'react-redux';

import { store } from './store';
import { theme } from './theme';
import { useAuth } from './hooks/useAuth';
import { SocketProvider } from './contexts/SocketContext';
import { NotificationProvider } from './contexts/NotificationContext';

// Layouts
import MainLayout from './layouts/MainLayout';
import AuthLayout from './layouts/AuthLayout';

// Pages
import Login from './pages/Login';
import Dashboard from './pages/Dashboard';
import Users from './pages/Users';
import Transactions from './pages/Transactions';
import Compliance from './pages/Compliance';
import Security from './pages/Security';
import Analytics from './pages/Analytics';
import SystemHealth from './pages/SystemHealth';
import AuditLogs from './pages/AuditLogs';
import Settings from './pages/Settings';
import Reports from './pages/Reports';
import RiskManagement from './pages/RiskManagement';
import CustomerSupport from './pages/CustomerSupport';

// Guards
import AuthGuard from './components/guards/AuthGuard';
import PermissionGuard from './components/guards/PermissionGuard';

const queryClient = new QueryClient({
  defaultOptions: {
    queries: {
      staleTime: 5 * 60 * 1000, // 5 minutes
      retry: 3,
      refetchOnWindowFocus: false,
    },
  },
});

function App() {
  const { isAuthenticated, checkAuth } = useAuth();

  useEffect(() => {
    checkAuth();
  }, [checkAuth]);

  return (
    <Provider store={store}>
      <QueryClientProvider client={queryClient}>
        <LocalizationProvider dateAdapter={AdapterDateFns}>
          <ThemeProvider theme={theme}>
            <CssBaseline />
            <NotificationProvider>
              <SocketProvider>
                <Router>
                  <Routes>
                    {/* Public routes */}
                    <Route element={<AuthLayout />}>
                      <Route path="/login" element={<Login />} />
                    </Route>

                    {/* Protected routes */}
                    <Route
                      element={
                        <AuthGuard>
                          <MainLayout />
                        </AuthGuard>
                      }
                    >
                      <Route index element={<Navigate to="/dashboard" replace />} />
                      <Route path="/dashboard" element={<Dashboard />} />
                      
                      <Route
                        path="/users"
                        element={
                          <PermissionGuard permission="users.view">
                            <Users />
                          </PermissionGuard>
                        }
                      />
                      
                      <Route
                        path="/transactions"
                        element={
                          <PermissionGuard permission="transactions.view">
                            <Transactions />
                          </PermissionGuard>
                        }
                      />
                      
                      <Route
                        path="/compliance"
                        element={
                          <PermissionGuard permission="compliance.view">
                            <Compliance />
                          </PermissionGuard>
                        }
                      />
                      
                      <Route
                        path="/security"
                        element={
                          <PermissionGuard permission="security.view">
                            <Security />
                          </PermissionGuard>
                        }
                      />
                      
                      <Route
                        path="/analytics"
                        element={
                          <PermissionGuard permission="analytics.view">
                            <Analytics />
                          </PermissionGuard>
                        }
                      />
                      
                      <Route
                        path="/system-health"
                        element={
                          <PermissionGuard permission="system.view">
                            <SystemHealth />
                          </PermissionGuard>
                        }
                      />
                      
                      <Route
                        path="/audit-logs"
                        element={
                          <PermissionGuard permission="audit.view">
                            <AuditLogs />
                          </PermissionGuard>
                        }
                      />
                      
                      <Route
                        path="/risk-management"
                        element={
                          <PermissionGuard permission="risk.view">
                            <RiskManagement />
                          </PermissionGuard>
                        }
                      />
                      
                      <Route
                        path="/support"
                        element={
                          <PermissionGuard permission="support.view">
                            <CustomerSupport />
                          </PermissionGuard>
                        }
                      />
                      
                      <Route
                        path="/reports"
                        element={
                          <PermissionGuard permission="reports.view">
                            <Reports />
                          </PermissionGuard>
                        }
                      />
                      
                      <Route path="/settings" element={<Settings />} />
                    </Route>

                    {/* Catch all */}
                    <Route path="*" element={<Navigate to="/dashboard" replace />} />
                  </Routes>
                </Router>
              </SocketProvider>
            </NotificationProvider>
            <ReactQueryDevtools initialIsOpen={false} />
          </ThemeProvider>
        </LocalizationProvider>
      </QueryClientProvider>
    </Provider>
  );
}

export default App;