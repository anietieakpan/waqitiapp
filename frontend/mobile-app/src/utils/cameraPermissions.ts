/**
 * Camera Permissions Utility
 * Handles camera permission requests and checks for check deposit functionality
 */

import { Platform, Alert, Linking } from 'react-native';
import { 
  PERMISSIONS, 
  RESULTS, 
  check, 
  request, 
  openSettings,
  PermissionStatus,
} from 'react-native-permissions';

export enum CameraPermissionStatus {
  GRANTED = 'granted',
  DENIED = 'denied',
  BLOCKED = 'blocked',
  UNAVAILABLE = 'unavailable',
  LIMITED = 'limited',
}

export interface CameraPermissionResult {
  status: CameraPermissionStatus;
  canUseCamera: boolean;
  shouldShowRationale: boolean;
  message?: string;
}

/**
 * Get the appropriate camera permission for the current platform
 */
const getCameraPermission = () => {
  return Platform.OS === 'ios' 
    ? PERMISSIONS.IOS.CAMERA 
    : PERMISSIONS.ANDROID.CAMERA;
};

/**
 * Convert react-native-permissions result to our enum
 */
const convertPermissionResult = (result: PermissionStatus): CameraPermissionStatus => {
  switch (result) {
    case RESULTS.GRANTED:
      return CameraPermissionStatus.GRANTED;
    case RESULTS.DENIED:
      return CameraPermissionStatus.DENIED;
    case RESULTS.BLOCKED:
      return CameraPermissionStatus.BLOCKED;
    case RESULTS.UNAVAILABLE:
      return CameraPermissionStatus.UNAVAILABLE;
    case RESULTS.LIMITED:
      return CameraPermissionStatus.LIMITED;
    default:
      return CameraPermissionStatus.UNAVAILABLE;
  }
};

/**
 * Check current camera permission status
 */
export const checkCameraPermission = async (): Promise<CameraPermissionResult> => {
  try {
    const permission = getCameraPermission();
    const result = await check(permission);
    const status = convertPermissionResult(result);
    
    const canUseCamera = status === CameraPermissionStatus.GRANTED;
    const shouldShowRationale = status === CameraPermissionStatus.DENIED;
    
    let message: string | undefined;
    switch (status) {
      case CameraPermissionStatus.DENIED:
        message = 'Camera permission is required to capture check images. Please grant permission when prompted.';
        break;
      case CameraPermissionStatus.BLOCKED:
        message = 'Camera permission has been permanently denied. Please enable it in your device settings.';
        break;
      case CameraPermissionStatus.UNAVAILABLE:
        message = 'Camera is not available on this device.';
        break;
      case CameraPermissionStatus.LIMITED:
        message = 'Camera access is limited. Some features may not work properly.';
        break;
    }
    
    return {
      status,
      canUseCamera,
      shouldShowRationale,
      message,
    };
  } catch (error) {
    console.error('Error checking camera permission:', error);
    return {
      status: CameraPermissionStatus.UNAVAILABLE,
      canUseCamera: false,
      shouldShowRationale: false,
      message: 'Failed to check camera permission.',
    };
  }
};

/**
 * Request camera permission from the user
 */
export const requestCameraPermission = async (): Promise<CameraPermissionResult> => {
  try {
    const permission = getCameraPermission();
    const result = await request(permission);
    const status = convertPermissionResult(result);
    
    const canUseCamera = status === CameraPermissionStatus.GRANTED;
    const shouldShowRationale = status === CameraPermissionStatus.DENIED;
    
    let message: string | undefined;
    switch (status) {
      case CameraPermissionStatus.DENIED:
        message = 'Camera permission was denied. You can try again or enable it in settings.';
        break;
      case CameraPermissionStatus.BLOCKED:
        message = 'Camera permission has been permanently denied. Please enable it in your device settings.';
        break;
      case CameraPermissionStatus.UNAVAILABLE:
        message = 'Camera is not available on this device.';
        break;
      case CameraPermissionStatus.GRANTED:
        message = 'Camera permission granted successfully.';
        break;
    }
    
    return {
      status,
      canUseCamera,
      shouldShowRationale,
      message,
    };
  } catch (error) {
    console.error('Error requesting camera permission:', error);
    return {
      status: CameraPermissionStatus.UNAVAILABLE,
      canUseCamera: false,
      shouldShowRationale: false,
      message: 'Failed to request camera permission.',
    };
  }
};

/**
 * Show a user-friendly alert for camera permission issues
 */
export const showCameraPermissionAlert = (
  result: CameraPermissionResult,
  options?: {
    onRetry?: () => void;
    onOpenSettings?: () => void;
    onCancel?: () => void;
  }
): void => {
  const { status, message } = result;
  const { onRetry, onOpenSettings, onCancel } = options || {};
  
  switch (status) {
    case CameraPermissionStatus.DENIED:
      Alert.alert(
        'Camera Permission Required',
        message || 'Waqiti needs camera access to capture check images for deposits.',
        [
          {
            text: 'Cancel',
            style: 'cancel',
            onPress: onCancel,
          },
          {
            text: 'Try Again',
            onPress: onRetry || (() => requestCameraPermission()),
          },
        ]
      );
      break;
      
    case CameraPermissionStatus.BLOCKED:
      Alert.alert(
        'Camera Permission Blocked',
        message || 'Camera permission has been permanently denied. Please enable it in your device settings to use check deposit.',
        [
          {
            text: 'Cancel',
            style: 'cancel',
            onPress: onCancel,
          },
          {
            text: 'Open Settings',
            onPress: onOpenSettings || (() => openCameraSettings()),
          },
        ]
      );
      break;
      
    case CameraPermissionStatus.UNAVAILABLE:
      Alert.alert(
        'Camera Unavailable',
        message || 'Camera is not available on this device. Check deposit requires a working camera.',
        [
          {
            text: 'OK',
            onPress: onCancel,
          },
        ]
      );
      break;
      
    case CameraPermissionStatus.LIMITED:
      Alert.alert(
        'Limited Camera Access',
        message || 'Camera access is limited. Some check deposit features may not work properly.',
        [
          {
            text: 'Continue Anyway',
            onPress: onRetry,
          },
          {
            text: 'Open Settings',
            onPress: onOpenSettings || (() => openCameraSettings()),
          },
        ]
      );
      break;
  }
};

/**
 * Open device settings for the app
 */
export const openCameraSettings = async (): Promise<void> => {
  try {
    await openSettings();
  } catch (error) {
    console.error('Error opening settings:', error);
    
    // Fallback: try to open general settings
    try {
      if (Platform.OS === 'ios') {
        await Linking.openURL('app-settings:');
      } else {
        await Linking.openURL('package:com.waqiti.mobile');
      }
    } catch (fallbackError) {
      console.error('Error opening fallback settings:', fallbackError);
      Alert.alert(
        'Settings Unavailable',
        'Unable to open settings automatically. Please go to Settings > Apps > Waqiti > Permissions to enable camera access.',
        [{ text: 'OK' }]
      );
    }
  }
};

/**
 * Check if we should request camera permission
 * Returns true if permission should be requested
 */
export const shouldRequestCameraPermission = async (): Promise<boolean> => {
  const result = await checkCameraPermission();
  return result.shouldShowRationale || result.status === CameraPermissionStatus.DENIED;
};

/**
 * Ensure camera permission is granted before proceeding
 * Returns true if permission is available, false otherwise
 */
export const ensureCameraPermission = async (
  options?: {
    showAlert?: boolean;
    onRetry?: () => void;
    onCancel?: () => void;
  }
): Promise<boolean> => {
  const { showAlert = true, onRetry, onCancel } = options || {};
  
  try {
    // First check current permission
    let result = await checkCameraPermission();
    
    // If not granted, try to request it
    if (!result.canUseCamera && result.shouldShowRationale) {
      result = await requestCameraPermission();
    }
    
    // If still not granted and should show alert
    if (!result.canUseCamera && showAlert) {
      showCameraPermissionAlert(result, { onRetry, onCancel });
    }
    
    return result.canUseCamera;
  } catch (error) {
    console.error('Error ensuring camera permission:', error);
    
    if (showAlert) {
      Alert.alert(
        'Permission Error',
        'An error occurred while checking camera permission. Please try again.',
        [
          {
            text: 'Cancel',
            style: 'cancel',
            onPress: onCancel,
          },
          {
            text: 'Retry',
            onPress: onRetry,
          },
        ]
      );
    }
    
    return false;
  }
};

/**
 * Get user-friendly permission status text
 */
export const getPermissionStatusText = (status: CameraPermissionStatus): string => {
  switch (status) {
    case CameraPermissionStatus.GRANTED:
      return 'Camera access granted';
    case CameraPermissionStatus.DENIED:
      return 'Camera access denied';
    case CameraPermissionStatus.BLOCKED:
      return 'Camera access permanently denied';
    case CameraPermissionStatus.UNAVAILABLE:
      return 'Camera not available';
    case CameraPermissionStatus.LIMITED:
      return 'Limited camera access';
    default:
      return 'Unknown permission status';
  }
};

/**
 * Check if device has a camera
 */
export const hasCamera = (): boolean => {
  // This is a basic check - in a real app you might want to use
  // a more sophisticated method to detect camera availability
  return Platform.OS === 'ios' || Platform.OS === 'android';
};

export default {
  checkCameraPermission,
  requestCameraPermission,
  showCameraPermissionAlert,
  openCameraSettings,
  shouldRequestCameraPermission,
  ensureCameraPermission,
  getPermissionStatusText,
  hasCamera,
  CameraPermissionStatus,
};