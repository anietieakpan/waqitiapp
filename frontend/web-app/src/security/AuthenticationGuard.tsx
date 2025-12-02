import React, { useEffect, useState, useCallback } from 'react';
import { useAuth } from '../contexts/AuthContext';
import monitoringService from '../services/monitoringService';
import { SecureStorage } from './CSPDirectives';

interface SessionInfo {
  startTime: number;
  lastActivity: number;
  deviceFingerprint: string;
  ipAddress?: string;
  userAgent: string;
}

interface AuthenticationGuardProps {
  children: React.ReactNode;
  requireAuth?: boolean;
  inactivityTimeout?: number; // minutes
  maxSessionDuration?: number; // hours
  enableDeviceTracking?: boolean;
  onSessionExpired?: () => void;
  onSuspiciousActivity?: (details: any) => void;
}

const AuthenticationGuard: React.FC<AuthenticationGuardProps> = ({
  children,
  requireAuth = true,
  inactivityTimeout = 30, // 30 minutes
  maxSessionDuration = 12, // 12 hours
  enableDeviceTracking = true,
  onSessionExpired,
  onSuspiciousActivity,
}) => {
  const { user, isAuthenticated, logout } = useAuth();
  const [sessionInfo, setSessionInfo] = useState<SessionInfo | null>(null);
  const [isValidating, setIsValidating] = useState(true);
  const [suspiciousActivityDetected, setSuspiciousActivityDetected] = useState(false);

  // Generate device fingerprint
  const generateDeviceFingerprint = useCallback(async (): Promise<string> => {
    const canvas = document.createElement('canvas');
    const ctx = canvas.getContext('2d');
    ctx!.textBaseline = 'top';
    ctx!.font = '14px Arial';
    ctx!.fillText('Device fingerprint', 2, 2);
    
    const fingerprint = [
      navigator.userAgent,
      navigator.language,
      screen.width + 'x' + screen.height,
      new Date().getTimezoneOffset(),
      !!window.sessionStorage,
      !!window.localStorage,
      !!window.indexedDB,
      typeof(Worker) !== 'undefined',
      navigator.platform,
      canvas.toDataURL(),
    ].join('|');

    // Create a simple hash
    let hash = 0;
    for (let i = 0; i < fingerprint.length; i++) {
      const char = fingerprint.charCodeAt(i);
      hash = ((hash << 5) - hash) + char;
      hash = hash & hash; // Convert to 32-bit integer
    }
    
    return Math.abs(hash).toString(36);
  }, []);

  // Initialize or validate session
  const initializeSession = useCallback(async () => {
    if (!isAuthenticated || !user) {
      setIsValidating(false);
      return;
    }

    try {
      const deviceFingerprint = await generateDeviceFingerprint();
      const now = Date.now();
      
      // Check for existing session
      const existingSession = await SecureStorage.getItem('session_info');
      
      if (existingSession) {
        const parsed: SessionInfo = JSON.parse(existingSession);
        
        // Validate session integrity
        if (enableDeviceTracking && parsed.deviceFingerprint !== deviceFingerprint) {
          // Device fingerprint mismatch - potential session hijacking
          monitoringService.trackError({
            message: 'Device fingerprint mismatch detected',
            userId: user.id,
            sessionId: monitoringService.sessionId,
            page: window.location.pathname,
            timestamp: now,
            userAgent: navigator.userAgent,
            url: window.location.href,
            severity: 'critical',
          });
          
          setSuspiciousActivityDetected(true);
          onSuspiciousActivity?.({ 
            type: 'device_mismatch',
            expected: parsed.deviceFingerprint,
            actual: deviceFingerprint,
          });
          
          await logout();
          return;
        }

        // Check session expiration
        const sessionAge = now - parsed.startTime;
        const inactivityDuration = now - parsed.lastActivity;
        
        if (sessionAge > maxSessionDuration * 60 * 60 * 1000) {
          // Session too old
          monitoringService.trackEvent('session_expired', 'security', {
            reason: 'max_duration_exceeded',
            sessionAge: sessionAge / 1000 / 60 / 60, // hours
          });
          
          onSessionExpired?.();
          await logout();
          return;
        }
        
        if (inactivityDuration > inactivityTimeout * 60 * 1000) {
          // Session inactive too long
          monitoringService.trackEvent('session_expired', 'security', {
            reason: 'inactivity_timeout',
            inactivityDuration: inactivityDuration / 1000 / 60, // minutes
          });
          
          onSessionExpired?.();
          await logout();
          return;
        }

        // Update last activity
        const updatedSession: SessionInfo = {
          ...parsed,
          lastActivity: now,
        };
        
        await SecureStorage.setItem('session_info', JSON.stringify(updatedSession));
        setSessionInfo(updatedSession);
      } else {
        // Create new session
        const newSession: SessionInfo = {
          startTime: now,
          lastActivity: now,
          deviceFingerprint,
          userAgent: navigator.userAgent,
        };
        
        await SecureStorage.setItem('session_info', JSON.stringify(newSession));
        setSessionInfo(newSession);
        
        // Track session start
        monitoringService.trackEvent('session_started', 'security', {
          deviceFingerprint,
          userAgent: navigator.userAgent,
        });
      }
    } catch (error) {
      console.error('Session validation error:', error);
      monitoringService.trackError({
        message: 'Session validation failed',
        stack: error instanceof Error ? error.stack : undefined,
        userId: user?.id,
        sessionId: monitoringService.sessionId,
        page: window.location.pathname,
        timestamp: Date.now(),
        userAgent: navigator.userAgent,
        url: window.location.href,
        severity: 'high',
      });
    } finally {
      setIsValidating(false);
    }
  }, [
    isAuthenticated,
    user,
    generateDeviceFingerprint,
    enableDeviceTracking,
    maxSessionDuration,
    inactivityTimeout,
    onSessionExpired,
    onSuspiciousActivity,
    logout,
  ]);

  // Update activity timestamp
  const updateActivity = useCallback(async () => {
    if (!sessionInfo) return;

    const now = Date.now();
    const updatedSession: SessionInfo = {
      ...sessionInfo,
      lastActivity: now,
    };

    await SecureStorage.setItem('session_info', JSON.stringify(updatedSession));
    setSessionInfo(updatedSession);
  }, [sessionInfo]);

  // Set up activity tracking
  useEffect(() => {
    const events = ['mousedown', 'mousemove', 'keypress', 'scroll', 'touchstart', 'click'];
    let activityTimeout: NodeJS.Timeout;

    const handleActivity = () => {
      clearTimeout(activityTimeout);
      activityTimeout = setTimeout(updateActivity, 1000); // Debounce updates
    };

    events.forEach(event => {
      document.addEventListener(event, handleActivity, true);
    });

    return () => {
      clearTimeout(activityTimeout);
      events.forEach(event => {
        document.removeEventListener(event, handleActivity, true);
      });
    };
  }, [updateActivity]);

  // Periodic session validation
  useEffect(() => {
    if (!isAuthenticated) return;

    const interval = setInterval(() => {
      initializeSession();
    }, 60000); // Check every minute

    return () => clearInterval(interval);
  }, [isAuthenticated, initializeSession]);

  // Initialize session on mount and auth changes
  useEffect(() => {
    initializeSession();
  }, [initializeSession]);

  // Monitor for suspicious activity patterns
  useEffect(() => {
    if (!sessionInfo || !user) return;

    const checkSuspiciousActivity = () => {
      const now = Date.now();
      const sessionDuration = now - sessionInfo.startTime;
      
      // Check for rapid page navigation (potential bot activity)
      const navigationCount = parseInt(sessionStorage.getItem('navigation_count') || '0');
      sessionStorage.setItem('navigation_count', (navigationCount + 1).toString());
      
      if (navigationCount > 50 && sessionDuration < 5 * 60 * 1000) {
        // More than 50 navigations in 5 minutes
        monitoringService.trackEvent('suspicious_activity', 'security', {
          type: 'rapid_navigation',
          navigationCount,
          sessionDuration: sessionDuration / 1000,
        });
        
        setSuspiciousActivityDetected(true);
        onSuspiciousActivity?.({
          type: 'rapid_navigation',
          navigationCount,
          sessionDuration,
        });
      }
    };

    checkSuspiciousActivity();
  }, [sessionInfo, user, onSuspiciousActivity]);

  // Handle page visibility changes
  useEffect(() => {
    const handleVisibilityChange = () => {
      if (document.hidden) {
        // Page became hidden, validate session when it becomes visible again
        const handleFocus = () => {
          initializeSession();
          window.removeEventListener('focus', handleFocus);
        };
        
        window.addEventListener('focus', handleFocus);
      }
    };

    document.addEventListener('visibilitychange', handleVisibilityChange);
    
    return () => {
      document.removeEventListener('visibilitychange', handleVisibilityChange);
    };
  }, [initializeSession]);

  // Clean up session on unmount
  useEffect(() => {
    return () => {
      if (sessionInfo) {
        const sessionDuration = Date.now() - sessionInfo.startTime;
        monitoringService.trackEvent('session_ended', 'security', {
          duration: sessionDuration / 1000,
          reason: 'component_unmount',
        });
      }
    };
  }, [sessionInfo]);

  // Loading state
  if (isValidating) {
    return (
      <div style={{
        display: 'flex',
        justifyContent: 'center',
        alignItems: 'center',
        height: '100vh',
        backgroundColor: '#f5f5f5',
      }}>
        <div>Validating session...</div>
      </div>
    );
  }

  // Suspicious activity detected
  if (suspiciousActivityDetected) {
    return (
      <div style={{
        display: 'flex',
        flexDirection: 'column',
        justifyContent: 'center',
        alignItems: 'center',
        height: '100vh',
        backgroundColor: '#f5f5f5',
        padding: '20px',
        textAlign: 'center',
      }}>
        <h2>Security Alert</h2>
        <p>Suspicious activity has been detected on your account.</p>
        <p>For your security, please log in again.</p>
        <button
          onClick={() => window.location.href = '/login'}
          style={{
            marginTop: '20px',
            padding: '10px 20px',
            backgroundColor: '#1976d2',
            color: 'white',
            border: 'none',
            borderRadius: '4px',
            cursor: 'pointer',
          }}
        >
          Go to Login
        </button>
      </div>
    );
  }

  // Authentication required but user not authenticated
  if (requireAuth && !isAuthenticated) {
    return (
      <div style={{
        display: 'flex',
        flexDirection: 'column',
        justifyContent: 'center',
        alignItems: 'center',
        height: '100vh',
        backgroundColor: '#f5f5f5',
        padding: '20px',
        textAlign: 'center',
      }}>
        <h2>Authentication Required</h2>
        <p>Please log in to access this application.</p>
        <button
          onClick={() => window.location.href = '/login'}
          style={{
            marginTop: '20px',
            padding: '10px 20px',
            backgroundColor: '#1976d2',
            color: 'white',
            border: 'none',
            borderRadius: '4px',
            cursor: 'pointer',
          }}
        >
          Go to Login
        </button>
      </div>
    );
  }

  return <>{children}</>;
};

export default AuthenticationGuard;