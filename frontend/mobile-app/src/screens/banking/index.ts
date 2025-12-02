/**
 * Banking Screens Index
 * Centralized exports for all banking-related screens
 */

export { default as CheckCaptureScreen } from './CheckCaptureScreen';
export { default as CheckPreviewScreen } from './CheckPreviewScreen';
export { default as CheckDepositStatusScreen } from './CheckDepositStatusScreen';

// Type exports
export type {
  CaptureSession,
  RouteParams as CheckCaptureRouteParams,
} from './CheckCaptureScreen';

export type {
  CheckDepositForm,
  RouteParams as CheckPreviewRouteParams,
} from './CheckPreviewScreen';

export type {
  DepositStatus,
  ProcessingStep,
  RouteParams as CheckDepositStatusRouteParams,
} from './CheckDepositStatusScreen';