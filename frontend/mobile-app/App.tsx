import React, { useEffect, useRef } from 'react';
import { StatusBar, Platform } from 'react-native';
import { NavigationContainer, NavigationContainerRef } from '@react-navigation/native';
import { SafeAreaProvider } from 'react-native-safe-area-context';
import { Provider as PaperProvider } from 'react-native-paper';
import { Provider as ReduxProvider } from 'react-redux';
import { PersistGate } from 'redux-persist/integration/react';
import { QueryClient, QueryClientProvider } from 'react-query';
import { GestureHandlerRootView } from 'react-native-gesture-handler';
import SplashScreen from 'react-native-splash-screen';
import { enableScreens } from 'react-native-screens';
import NetInfo from '@react-native-community/netinfo';
import { NetworkProvider } from 'react-native-offline';
import CodePush from 'react-native-code-push';
import * as Sentry from '@sentry/react-native';
import { logError, logInfo } from './src/utils/Logger';

import { store, persistor } from './src/store/store';
import { theme } from './src/theme';
import RootNavigator from './src/navigation/RootNavigator';
import { AuthProvider } from './src/contexts/AuthContext';
import { NotificationProvider } from './src/contexts/NotificationContext';
import { BiometricProvider } from './src/contexts/BiometricContext';
import { SecurityProvider } from './src/contexts/SecurityContext';
import { LocalizationProvider } from './src/contexts/LocalizationContext';
import { SecurityDialogProvider } from './src/components/security/SecurityDialogManager';
import ErrorBoundary from './src/components/common/ErrorBoundary';
import { setupNotifications } from './src/services/notificationService';
import { setupAnalytics, trackScreenView } from './src/services/analyticsService';
import { setupCrashlytics } from './src/services/crashlyticsService';
import { initializeServices } from './src/services/initializationService';
import LoadingScreen from './src/screens/LoadingScreen';
import Config from 'react-native-config';

// Enable screens for better performance
enableScreens();

// Initialize Sentry
Sentry.init({
  dsn: Config.SENTRY_DSN,
  environment: Config.ENVIRONMENT,
  tracesSampleRate: Config.ENVIRONMENT === 'production' ? 0.1 : 1.0,
  integrations: [
    new Sentry.ReactNativeTracing({
      routingInstrumentation: new Sentry.ReactNavigationInstrumentation(),
      tracingOrigins: ['localhost', Config.API_URL, /^\//],
    }),
  ],
});

// Create a query client
const queryClient = new QueryClient({
  defaultOptions: {
    queries: {
      retry: 3,
      retryDelay: (attemptIndex) => Math.min(1000 * 2 ** attemptIndex, 30000),
      staleTime: 5 * 60 * 1000, // 5 minutes
      cacheTime: 10 * 60 * 1000, // 10 minutes
    },
  },
});

// CodePush options
const codePushOptions = {
  checkFrequency: CodePush.CheckFrequency.ON_APP_RESUME,
  installMode: CodePush.InstallMode.ON_NEXT_RESTART,
  minimumBackgroundDuration: 60 * 10, // 10 minutes
};

/**
 * Main App component wrapped with CodePush for OTA updates
 */
const App: React.FC = () => {
  const navigationRef = useRef<NavigationContainerRef<any>>(null);
  const routeNameRef = useRef<string>();

  useEffect(() => {
    // Initialize services
    const initApp = async () => {
      try {
        // Hide splash screen
        SplashScreen.hide();

        // Initialize core services
        await initializeServices();
        
        // Setup notifications
        await setupNotifications();
        
        // Setup analytics
        await setupAnalytics();
        
        // Setup crashlytics
        await setupCrashlytics();
        
        // Check for CodePush updates
        if (Config.ENVIRONMENT === 'production') {
          CodePush.sync({
            updateDialog: {
              title: 'Update Available',
              optionalUpdateMessage: 'A new version is available. Would you like to update?',
              optionalInstallButtonLabel: 'Update',
              optionalIgnoreButtonLabel: 'Later',
            },
            installMode: CodePush.InstallMode.IMMEDIATE,
          });
        }
      } catch (error) {
        logError('App initialization failed', {
          feature: 'app_initialization',
          action: 'init_error'
        }, error as Error);
        Sentry.captureException(error);
      }
    };

    initApp();

    // Setup network listener
    const unsubscribe = NetInfo.addEventListener(state => {
      logInfo('Network state changed', {
        feature: 'network_monitoring',
        action: 'state_change',
        metadata: { isConnected: state.isConnected, type: state.type }
      });
    });

    return () => {
      unsubscribe();
    };
  }, []);

  return (
    <ErrorBoundary>
      <GestureHandlerRootView style={{ flex: 1 }}>
        <ReduxProvider store={store}>
          <PersistGate loading={<LoadingScreen />} persistor={persistor}>
            <QueryClientProvider client={queryClient}>
              <NetworkProvider>
                <SafeAreaProvider>
                  <PaperProvider theme={theme}>
                    <LocalizationProvider>
                      <AuthProvider>
                        <SecurityProvider>
                          <SecurityDialogProvider>
                            <BiometricProvider>
                              <NotificationProvider>
                              <NavigationContainer
                                ref={navigationRef}
                                onReady={() => {
                                  routeNameRef.current = navigationRef.current?.getCurrentRoute()?.name;
                                }}
                                onStateChange={async () => {
                                  const previousRouteName = routeNameRef.current;
                                  const currentRouteName = navigationRef.current?.getCurrentRoute()?.name;

                                  if (previousRouteName !== currentRouteName && currentRouteName) {
                                    await trackScreenView(currentRouteName);
                                  }

                                  routeNameRef.current = currentRouteName;
                                }}
                              >
                                <StatusBar
                                  barStyle={theme.dark ? 'light-content' : 'dark-content'}
                                  backgroundColor={theme.colors.background}
                                />
                                <RootNavigator />
                              </NavigationContainer>
                              </NotificationProvider>
                            </BiometricProvider>
                          </SecurityDialogProvider>
                        </SecurityProvider>
                      </AuthProvider>
                    </LocalizationProvider>
                  </PaperProvider>
                </SafeAreaProvider>
              </NetworkProvider>
            </QueryClientProvider>
          </PersistGate>
        </ReduxProvider>
      </GestureHandlerRootView>
    </ErrorBoundary>
  );
};

// Wrap App with CodePush HOC for production
export default Config.ENVIRONMENT === 'production' 
  ? Sentry.wrap(CodePush(codePushOptions)(App))
  : Sentry.wrap(App);