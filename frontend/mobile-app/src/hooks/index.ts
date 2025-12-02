/**
 * Hooks Exports - Central export point for all custom hooks
 */

// Biometric Hook
export { useBiometric } from './useBiometric';

// Auth Hook (re-export from context)
export { useAuth } from '../contexts/AuthContext';

// Security Hook (re-export from context)
export { useSecurity } from '../contexts/SecurityContext';

// Notification Hook (re-export from context)
export { useNotification } from '../contexts/NotificationContext';

// Localization Hook (re-export from context)
export { useLocalization } from '../contexts/LocalizationContext';