import { useState, useEffect, useCallback } from 'react';

interface BeforeInstallPromptEvent extends Event {
  prompt(): Promise<void>;
  userChoice: Promise<{ outcome: 'accepted' | 'dismissed'; platform: string }>;
}

interface PWAState {
  isInstallable: boolean;
  isInstalled: boolean;
  isOffline: boolean;
  isUpdateAvailable: boolean;
  isOnline: boolean;
}

interface PWAActions {
  installApp: () => Promise<boolean>;
  updateApp: () => Promise<void>;
  showInstallPrompt: () => Promise<boolean>;
  registerForNotifications: () => Promise<boolean>;
  unregisterNotifications: () => Promise<boolean>;
}

interface PWAHook extends PWAState, PWAActions {}

export const usePWA = (): PWAHook => {
  const [state, setState] = useState<PWAState>({
    isInstallable: false,
    isInstalled: false,
    isOffline: !navigator.onLine,
    isUpdateAvailable: false,
    isOnline: navigator.onLine,
  });

  const [deferredPrompt, setDeferredPrompt] = useState<BeforeInstallPromptEvent | null>(null);
  const [registration, setRegistration] = useState<ServiceWorkerRegistration | null>(null);

  // Check if app is already installed
  useEffect(() => {
    const checkInstallStatus = () => {
      const isStandalone = window.matchMedia('(display-mode: standalone)').matches;
      const isInWebAppiOS = (window.navigator as any).standalone === true;
      const isInstalled = isStandalone || isInWebAppiOS;
      
      setState(prev => ({ ...prev, isInstalled }));
    };

    checkInstallStatus();
    window.matchMedia('(display-mode: standalone)').addEventListener('change', checkInstallStatus);

    return () => {
      window.matchMedia('(display-mode: standalone)').removeEventListener('change', checkInstallStatus);
    };
  }, []);

  // Handle online/offline status
  useEffect(() => {
    const handleOnline = () => setState(prev => ({ ...prev, isOnline: true, isOffline: false }));
    const handleOffline = () => setState(prev => ({ ...prev, isOnline: false, isOffline: true }));

    window.addEventListener('online', handleOnline);
    window.addEventListener('offline', handleOffline);

    return () => {
      window.removeEventListener('online', handleOnline);
      window.removeEventListener('offline', handleOffline);
    };
  }, []);

  // Handle install prompt
  useEffect(() => {
    const handleBeforeInstallPrompt = (e: Event) => {
      e.preventDefault();
      const promptEvent = e as BeforeInstallPromptEvent;
      setDeferredPrompt(promptEvent);
      setState(prev => ({ ...prev, isInstallable: true }));
    };

    const handleAppInstalled = () => {
      setDeferredPrompt(null);
      setState(prev => ({ 
        ...prev, 
        isInstallable: false, 
        isInstalled: true 
      }));
    };

    window.addEventListener('beforeinstallprompt', handleBeforeInstallPrompt);
    window.addEventListener('appinstalled', handleAppInstalled);

    return () => {
      window.removeEventListener('beforeinstallprompt', handleBeforeInstallPrompt);
      window.removeEventListener('appinstalled', handleAppInstalled);
    };
  }, []);

  // Handle service worker updates
  useEffect(() => {
    if ('serviceWorker' in navigator) {
      navigator.serviceWorker.ready.then((reg) => {
        setRegistration(reg);

        // Check for updates
        const handleUpdateFound = () => {
          const newWorker = reg.installing;
          if (newWorker) {
            newWorker.addEventListener('statechange', () => {
              if (newWorker.state === 'installed' && navigator.serviceWorker.controller) {
                setState(prev => ({ ...prev, isUpdateAvailable: true }));
              }
            });
          }
        };

        reg.addEventListener('updatefound', handleUpdateFound);

        // Check for waiting service worker
        if (reg.waiting) {
          setState(prev => ({ ...prev, isUpdateAvailable: true }));
        }

        return () => {
          reg.removeEventListener('updatefound', handleUpdateFound);
        };
      });

      // Listen for controlling service worker changes
      navigator.serviceWorker.addEventListener('controllerchange', () => {
        window.location.reload();
      });
    }
  }, []);

  const installApp = useCallback(async (): Promise<boolean> => {
    if (!deferredPrompt) {
      return false;
    }

    try {
      await deferredPrompt.prompt();
      const choiceResult = await deferredPrompt.userChoice;
      
      if (choiceResult.outcome === 'accepted') {
        setDeferredPrompt(null);
        setState(prev => ({ ...prev, isInstallable: false }));
        return true;
      }
      
      return false;
    } catch (error) {
      console.error('Error during app installation:', error);
      return false;
    }
  }, [deferredPrompt]);

  const showInstallPrompt = useCallback(async (): Promise<boolean> => {
    return installApp();
  }, [installApp]);

  const updateApp = useCallback(async (): Promise<void> => {
    if (!registration || !registration.waiting) {
      return;
    }

    try {
      // Send message to waiting service worker to skip waiting
      registration.waiting.postMessage({ type: 'SKIP_WAITING' });
      
      setState(prev => ({ ...prev, isUpdateAvailable: false }));
    } catch (error) {
      console.error('Error updating app:', error);
    }
  }, [registration]);

  const registerForNotifications = useCallback(async (): Promise<boolean> => {
    if (!('Notification' in window) || !registration) {
      return false;
    }

    try {
      const permission = await Notification.requestPermission();
      
      if (permission === 'granted') {
        const subscription = await registration.pushManager.subscribe({
          userVisibleOnly: true,
          applicationServerKey: process.env.REACT_APP_VAPID_PUBLIC_KEY,
        });

        // Send subscription to server
        await fetch('/api/admin/notifications/subscribe', {
          method: 'POST',
          headers: {
            'Content-Type': 'application/json',
          },
          body: JSON.stringify(subscription),
        });

        return true;
      }
      
      return false;
    } catch (error) {
      console.error('Error registering for notifications:', error);
      return false;
    }
  }, [registration]);

  const unregisterNotifications = useCallback(async (): Promise<boolean> => {
    if (!registration) {
      return false;
    }

    try {
      const subscription = await registration.pushManager.getSubscription();
      
      if (subscription) {
        await subscription.unsubscribe();
        
        // Notify server
        await fetch('/api/admin/notifications/unsubscribe', {
          method: 'POST',
          headers: {
            'Content-Type': 'application/json',
          },
          body: JSON.stringify({ endpoint: subscription.endpoint }),
        });
      }
      
      return true;
    } catch (error) {
      console.error('Error unregistering notifications:', error);
      return false;
    }
  }, [registration]);

  return {
    ...state,
    installApp,
    updateApp,
    showInstallPrompt,
    registerForNotifications,
    unregisterNotifications,
  };
};