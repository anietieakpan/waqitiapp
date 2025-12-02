# Camera Library Migration Guide

## Overview
This guide documents the migration from the deprecated `react-native-camera` library to `react-native-vision-camera`.

## Migration Summary

### 1. Dependencies Updated
- **Removed**: 
  - `react-native-camera` (^4.2.1)
  - `react-native-qrcode-scanner` (^1.5.5) 
- **Added**:
  - `react-native-vision-camera` (^3.6.16)
  - `vision-camera-code-scanner` (^0.2.0)

### 2. Components Created/Updated

#### New Components:
- **VisionCameraScanner** (`src/components/camera/VisionCameraScanner.tsx`)
  - Universal camera component supporting both QR scanning and photo capture
  - Supports multiple barcode formats
  - Built-in permission handling
  - Flash control
  - Camera switching (front/back)
  - Loading states and error handling

#### Updated Components:
- **QRScanner** (`src/components/QRScanner.tsx`)
  - Now uses VisionCameraScanner instead of RNCamera
  - Simplified implementation with better error handling

#### Updated Screens:
- **QRCodeScannerScreen** (`src/screens/payment/QRCodeScannerScreen.tsx`)
  - Uses new VisionCameraScanner component
  - Cleaner implementation with proper callbacks

- **CheckDepositCaptureScreen** (`src/screens/banking/CheckDepositCaptureScreen.tsx`)
  - New screen for check deposit with front/back capture flow
  - Uses VisionCameraScanner in photo mode

- **QRCodeScreen** (`src/screens/payment/QRCodeScreen.tsx`)
  - Updated to use VisionCameraScanner
  - Removed direct RNCamera usage

- **CheckCaptureScreen** (`src/screens/banking/CheckCaptureScreen.tsx`)
  - Updated to use VisionCameraScanner
  - Simplified permission handling

#### Updated Components:
- **CheckImageValidator** (`src/components/banking/CheckImageValidator.tsx`)
  - Removed RNCamera dependency
  - Now works independently of camera implementation

### 3. Test Updates
- Updated all test files to mock `react-native-vision-camera` instead of `react-native-camera`
- Created helper function `simulateQRScan()` for easier testing
- Updated all QR scanning tests to use the new pattern

### 4. Native Setup Required (Post-Installation)

#### iOS Setup:
1. Add camera usage description to Info.plist:
   ```xml
   <key>NSCameraUsageDescription</key>
   <string>$(PRODUCT_NAME) needs access to your camera to scan QR codes and capture check images.</string>
   ```

2. Run `cd ios && pod install`

#### Android Setup:
1. Camera permissions are already defined in AndroidManifest.xml
2. Minimum SDK version should be 21 or higher
3. Add to `android/app/build.gradle`:
   ```gradle
   android {
     ...
     packagingOptions {
       pickFirst '**/libjsc.so'
       pickFirst '**/libc++_shared.so'
     }
   }
   ```

### 5. Usage Examples

#### QR Code Scanning:
```tsx
<VisionCameraScanner
  onScan={(data) => console.log('Scanned:', data)}
  mode="scanner"
  scanTypes={['qr-code']}
  showFrame={true}
/>
```

#### Photo Capture:
```tsx
<VisionCameraScanner
  onCapture={(photo) => console.log('Photo captured:', photo.path)}
  mode="photo"
  showFrame={false}
/>
```

#### Both Modes:
```tsx
<VisionCameraScanner
  onScan={(data) => console.log('Scanned:', data)}
  onCapture={(photo) => console.log('Photo captured:', photo.path)}
  mode="both"
  scanTypes={['qr-code', 'ean-13']}
/>
```

### 6. Benefits of Migration

1. **Better Performance**: Vision Camera is built with performance in mind
2. **Active Maintenance**: Regular updates and bug fixes
3. **Better TypeScript Support**: Full TypeScript definitions
4. **Frame Processors**: Ability to run custom frame processing
5. **Modern Architecture**: Uses newer React Native architecture
6. **Better Permission Handling**: Built-in hooks for permissions

### 7. Breaking Changes

1. **Flash Mode**: Changed from constants to boolean
2. **Camera Ref**: No longer needed for basic operations
3. **Barcode Events**: Changed from `onBarCodeRead` to `onScan` callback
4. **Photo Capture**: Returns PhotoFile object instead of URI string

### 8. Next Steps

1. Install dependencies: `npm install`
2. Install iOS pods: `cd ios && pod install`
3. Clean and rebuild the project
4. Test all camera-related features thoroughly

## Notes
- All camera-related functionality has been migrated
- Tests have been updated to work with new implementation
- No deprecated camera libraries remain in the codebase