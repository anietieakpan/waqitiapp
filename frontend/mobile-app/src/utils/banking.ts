/**
 * Banking Utilities Index
 * Centralized exports for all banking-related utilities
 */

// Camera permissions
export {
  default as cameraPermissions,
  checkCameraPermission,
  requestCameraPermission,
  showCameraPermissionAlert,
  openCameraSettings,
  shouldRequestCameraPermission,
  ensureCameraPermission,
  getPermissionStatusText,
  hasCamera,
  CameraPermissionStatus,
} from './cameraPermissions';

export type {
  CameraPermissionResult,
} from './cameraPermissions';

// Image optimization
export {
  default as imageOptimization,
  optimizeCheckImage,
  validateCheckImage,
  createCheckThumbnail,
  rotateImage,
  enhanceCheckImage,
  convertToGrayscale,
  batchOptimizeImages,
  cleanupTempImages,
  getImageInfo,
  formatFileSize,
  getCompressionPercentage,
  isImageProcessingAvailable,
  DEFAULT_CHECK_OPTIONS,
} from './imageOptimization';

export type {
  ImageOptimizationOptions,
  OptimizedImageResult,
  ImageValidationResult,
} from './imageOptimization';

// Additional banking utilities can be added here in the future
// For example:
// - MICR parsing utilities
// - Check validation algorithms
// - Fraud detection helpers
// - Account number validation
// - Routing number validation