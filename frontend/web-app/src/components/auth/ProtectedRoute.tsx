import React, { ReactNode } from 'react';
import { Navigate, useLocation } from 'react-router-dom';
import { Box, CircularProgress } from '@mui/material';
import { useAuth } from '@/contexts/AuthContext';

interface ProtectedRouteProps {
  children: ReactNode;
  requiredKycStatus?: 'approved' | 'pending' | 'rejected' | 'not_started';
  requiredVerification?: boolean;
  requiredMfa?: boolean;
  fallbackPath?: string;
}

const ProtectedRoute: React.FC<ProtectedRouteProps> = ({
  children,
  requiredKycStatus,
  requiredVerification = false,
  requiredMfa = false,
  fallbackPath = '/login',
}) => {
  const { isAuthenticated, user, loading } = useAuth();
  const location = useLocation();

  // Show loading spinner while checking auth status
  if (loading) {
    return (
      <Box
        display="flex"
        justifyContent="center"
        alignItems="center"
        minHeight="100vh"
      >
        <CircularProgress />
      </Box>
    );
  }

  // Not authenticated
  if (!isAuthenticated || !user) {
    return <Navigate to={fallbackPath} state={{ from: location }} replace />;
  }

  // Check email verification requirement
  if (requiredVerification && !user.verified) {
    return <Navigate to="/verify-email" state={{ from: location }} replace />;
  }

  // Check MFA requirement
  if (requiredMfa && !user.mfaEnabled) {
    return <Navigate to="/setup-mfa" state={{ from: location }} replace />;
  }

  // Check KYC status requirement
  if (requiredKycStatus && user.kycStatus !== requiredKycStatus) {
    if (user.kycStatus === 'not_started') {
      return <Navigate to="/kyc/start" state={{ from: location }} replace />;
    } else if (user.kycStatus === 'pending') {
      return <Navigate to="/kyc/pending" state={{ from: location }} replace />;
    } else if (user.kycStatus === 'rejected') {
      return <Navigate to="/kyc/rejected" state={{ from: location }} replace />;
    }
  }

  // All checks passed, render children
  return <>{children}</>;
};

export default ProtectedRoute;