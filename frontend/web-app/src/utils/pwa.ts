// PWA utility functions
export interface PWAInstallPrompt {
  prompt(): void;
  userChoice: Promise<{ outcome: 'accepted' | 'dismissed' }>;
}

class PWAService {
  private deferredPrompt: PWAInstallPrompt | null = null;
  private isInstalled = false;
  private listeners: Map<string, Set<Function>> = new Map();

  constructor() {
    this.init();
  }

  private init() {
    // Check if app is already installed
    this.checkIfInstalled();

    // Listen for the beforeinstallprompt event
    window.addEventListener('beforeinstallprompt', (e) => {
      e.preventDefault();
      this.deferredPrompt = e as any;
      this.emit('installable', true);
    });

    // Listen for the appinstalled event
    window.addEventListener('appinstalled', () => {
      this.isInstalled = true;
      this.deferredPrompt = null;
      this.emit('installed', true);
      console.log('PWA was installed');
    });

    // Listen for service worker updates
    if ('serviceWorker' in navigator) {
      navigator.serviceWorker.addEventListener('controllerchange', () => {
        this.emit('update-available', true);
      });
    }
  }

  private checkIfInstalled() {
    // Check if running in standalone mode (installed PWA)
    if (window.matchMedia('(display-mode: standalone)').matches) {
      this.isInstalled = true;
      return;
    }

    // Check for iOS Safari
    if ((window.navigator as any).standalone === true) {
      this.isInstalled = true;
      return;
    }

    // Check for Android Chrome
    if (document.referrer.includes('android-app://')) {
      this.isInstalled = true;
      return;
    }
  }

  // Check if PWA can be installed
  canInstall(): boolean {
    return this.deferredPrompt !== null && !this.isInstalled;
  }

  // Check if PWA is installed
  isAppInstalled(): boolean {
    return this.isInstalled;
  }

  // Show install prompt
  async showInstallPrompt(): Promise<'accepted' | 'dismissed' | null> {
    if (!this.deferredPrompt) {
      return null;
    }

    this.deferredPrompt.prompt();
    const { outcome } = await this.deferredPrompt.userChoice;
    
    if (outcome === 'accepted') {
      console.log('User accepted the install prompt');
    } else {
      console.log('User dismissed the install prompt');
    }

    this.deferredPrompt = null;
    return outcome;
  }

  // Get install instructions for different platforms
  getInstallInstructions(): {
    platform: string;
    instructions: string[];
  } {
    const userAgent = navigator.userAgent.toLowerCase();
    
    if (userAgent.includes('iphone') || userAgent.includes('ipad')) {
      return {
        platform: 'iOS Safari',
        instructions: [
          'Tap the Share button at the bottom of the screen',
          'Scroll down and tap "Add to Home Screen"',
          'Tap "Add" to install the app',
        ],
      };
    }
    
    if (userAgent.includes('android')) {
      return {
        platform: 'Android Chrome',
        instructions: [
          'Tap the menu button (three dots) in the browser',
          'Tap "Add to Home screen" or "Install app"',
          'Tap "Install" to add the app to your home screen',
        ],
      };
    }
    
    return {
      platform: 'Desktop',
      instructions: [
        'Look for the install icon in your browser\'s address bar',
        'Click the install button or use the browser menu',
        'Follow the prompts to install the app',
      ],
    };
  }

  // Event system for PWA state changes
  on(event: string, callback: Function) {
    if (!this.listeners.has(event)) {
      this.listeners.set(event, new Set());
    }
    this.listeners.get(event)!.add(callback);
  }

  off(event: string, callback: Function) {
    this.listeners.get(event)?.delete(callback);
  }

  private emit(event: string, data: any) {
    this.listeners.get(event)?.forEach(callback => {
      try {
        callback(data);
      } catch (error) {
        console.error('Error in PWA event listener:', error);
      }
    });
  }

  // Check for app updates
  async checkForUpdates(): Promise<boolean> {
    if (!('serviceWorker' in navigator)) {
      return false;
    }

    try {
      const registration = await navigator.serviceWorker.getRegistration();
      if (registration) {
        await registration.update();
        return registration.waiting !== null;
      }
    } catch (error) {
      console.error('Error checking for updates:', error);
    }
    
    return false;
  }

  // Apply app update
  async applyUpdate(): Promise<void> {
    if (!('serviceWorker' in navigator)) {
      return;
    }

    try {
      const registration = await navigator.serviceWorker.getRegistration();
      if (registration?.waiting) {
        registration.waiting.postMessage({ type: 'SKIP_WAITING' });
        window.location.reload();
      }
    } catch (error) {
      console.error('Error applying update:', error);
    }
  }

  // Share content using Web Share API
  async share(data: {
    title?: string;
    text?: string;
    url?: string;
    files?: File[];
  }): Promise<boolean> {
    if (!navigator.share) {
      // Fallback to clipboard
      if (data.url && navigator.clipboard) {
        await navigator.clipboard.writeText(data.url);
        return true;
      }
      return false;
    }

    try {
      await navigator.share(data);
      return true;
    } catch (error) {
      console.error('Error sharing:', error);
      return false;
    }
  }

  // Get device capabilities
  getDeviceCapabilities() {
    return {
      isOnline: navigator.onLine,
      hasCamera: 'mediaDevices' in navigator && 'getUserMedia' in navigator.mediaDevices,
      hasGeolocation: 'geolocation' in navigator,
      hasNotifications: 'Notification' in window,
      hasServiceWorker: 'serviceWorker' in navigator,
      hasIndexedDB: 'indexedDB' in window,
      hasWebShare: 'share' in navigator,
      hasClipboard: 'clipboard' in navigator,
      hasVibration: 'vibrate' in navigator,
      hasWakeLock: 'wakeLock' in navigator,
      hasDeviceMotion: 'DeviceMotionEvent' in window,
      hasDeviceOrientation: 'DeviceOrientationEvent' in window,
      hasBattery: 'getBattery' in navigator,
      hasPaymentRequest: 'PaymentRequest' in window,
      hasCredentialsAPI: 'credentials' in navigator,
      hasWebAuthn: 'credentials' in navigator && 'create' in navigator.credentials,
    };
  }

  // Register for background sync
  async registerBackgroundSync(tag: string): Promise<boolean> {
    if (!('serviceWorker' in navigator) || !('sync' in window.ServiceWorkerRegistration.prototype)) {
      return false;
    }

    try {
      const registration = await navigator.serviceWorker.ready;
      await (registration as any).sync.register(tag);
      return true;
    } catch (error) {
      console.error('Error registering background sync:', error);
      return false;
    }
  }

  // Show notification
  async showNotification(title: string, options?: NotificationOptions): Promise<boolean> {
    if (!('Notification' in window)) {
      return false;
    }

    if (Notification.permission === 'granted') {
      new Notification(title, options);
      return true;
    }

    if (Notification.permission === 'default') {
      const permission = await Notification.requestPermission();
      if (permission === 'granted') {
        new Notification(title, options);
        return true;
      }
    }

    return false;
  }

  // Vibrate device
  vibrate(pattern: number | number[]): boolean {
    if ('vibrate' in navigator) {
      navigator.vibrate(pattern);
      return true;
    }
    return false;
  }

  // Keep screen awake
  async keepScreenAwake(): Promise<WakeLockSentinel | null> {
    if (!('wakeLock' in navigator)) {
      return null;
    }

    try {
      const wakeLock = await (navigator as any).wakeLock.request('screen');
      console.log('Screen wake lock acquired');
      return wakeLock;
    } catch (error) {
      console.error('Error acquiring screen wake lock:', error);
      return null;
    }
  }

  // Get network information
  getNetworkInfo() {
    const connection = (navigator as any).connection || (navigator as any).mozConnection || (navigator as any).webkitConnection;
    
    if (!connection) {
      return {
        online: navigator.onLine,
        type: 'unknown',
        effectiveType: 'unknown',
        downlink: null,
        rtt: null,
      };
    }

    return {
      online: navigator.onLine,
      type: connection.type || 'unknown',
      effectiveType: connection.effectiveType || 'unknown',
      downlink: connection.downlink || null,
      rtt: connection.rtt || null,
      saveData: connection.saveData || false,
    };
  }

  // Add to calendar
  async addToCalendar(event: {
    title: string;
    start: Date;
    end: Date;
    description?: string;
    location?: string;
  }): Promise<boolean> {
    // Create ICS content
    const formatDate = (date: Date) => {
      return date.toISOString().replace(/[-:]/g, '').split('.')[0] + 'Z';
    };

    const icsContent = [
      'BEGIN:VCALENDAR',
      'VERSION:2.0',
      'PRODID:-//Waqiti//Waqiti App//EN',
      'BEGIN:VEVENT',
      `DTSTART:${formatDate(event.start)}`,
      `DTEND:${formatDate(event.end)}`,
      `SUMMARY:${event.title}`,
      event.description ? `DESCRIPTION:${event.description}` : '',
      event.location ? `LOCATION:${event.location}` : '',
      'END:VEVENT',
      'END:VCALENDAR',
    ].filter(Boolean).join('\r\n');

    const blob = new Blob([icsContent], { type: 'text/calendar' });
    const url = URL.createObjectURL(blob);
    
    const link = document.createElement('a');
    link.href = url;
    link.download = `${event.title}.ics`;
    document.body.appendChild(link);
    link.click();
    document.body.removeChild(link);
    URL.revokeObjectURL(url);

    return true;
  }
}

export const pwaService = new PWAService();
export default pwaService;