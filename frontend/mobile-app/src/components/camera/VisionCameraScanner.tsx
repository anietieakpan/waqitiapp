import React, { useCallback, useEffect, useState } from 'react';
import {
  View,
  StyleSheet,
  Text,
  TouchableOpacity,
  ActivityIndicator,
  Alert,
  Platform,
} from 'react-native';
import {
  Camera,
  useCameraDevice,
  useCameraPermission,
  useCodeScanner,
  CameraPosition,
  PhotoFile,
} from 'react-native-vision-camera';
import { useIsFocused } from '@react-navigation/native';
import { Icon } from 'react-native-elements';
import { colors } from '../../theme';

interface VisionCameraScannerProps {
  onScan?: (data: string) => void;
  onCapture?: (photo: PhotoFile) => void;
  mode?: 'scanner' | 'photo' | 'both';
  scanTypes?: Array<'qr-code' | 'ean-13' | 'ean-8' | 'code-128' | 'code-39' | 'code-93' | 'aztec' | 'pdf-417'>;
  showFrame?: boolean;
  cameraPosition?: CameraPosition;
}

export const VisionCameraScanner: React.FC<VisionCameraScannerProps> = ({
  onScan,
  onCapture,
  mode = 'scanner',
  scanTypes = ['qr-code'],
  showFrame = true,
  cameraPosition = 'back',
}) => {
  const [isActive, setIsActive] = useState(true);
  const [flashEnabled, setFlashEnabled] = useState(false);
  const [currentPosition, setCurrentPosition] = useState<CameraPosition>(cameraPosition);
  const [isProcessing, setIsProcessing] = useState(false);
  
  const isFocused = useIsFocused();
  const { hasPermission, requestPermission } = useCameraPermission();
  const device = useCameraDevice(currentPosition);

  // Code scanner configuration
  const codeScanner = useCodeScanner({
    codeTypes: scanTypes,
    onCodeScanned: (codes) => {
      if (mode === 'scanner' || mode === 'both') {
        const code = codes[0];
        if (code && onScan && !isProcessing) {
          setIsProcessing(true);
          onScan(code.value);
          setTimeout(() => setIsProcessing(false), 1000);
        }
      }
    },
  });

  // Camera configuration
  const camera = React.useRef<Camera>(null);

  useEffect(() => {
    if (!hasPermission) {
      requestPermission();
    }
  }, [hasPermission, requestPermission]);

  const takePhoto = useCallback(async () => {
    if (!camera.current || isProcessing) return;

    try {
      setIsProcessing(true);
      const photo = await camera.current.takePhoto({
        qualityPrioritization: 'quality',
        flash: flashEnabled ? 'on' : 'off',
        enableAutoRedEyeReduction: true,
      });
      
      if (onCapture) {
        onCapture(photo);
      }
    } catch (error) {
      console.error('Failed to take photo:', error);
      Alert.alert('Error', 'Failed to capture photo');
    } finally {
      setIsProcessing(false);
    }
  }, [flashEnabled, onCapture, isProcessing]);

  const toggleFlash = useCallback(() => {
    setFlashEnabled(prev => !prev);
  }, []);

  const switchCamera = useCallback(() => {
    setCurrentPosition(prev => prev === 'back' ? 'front' : 'back');
  }, []);

  if (!hasPermission) {
    return (
      <View style={styles.container}>
        <Text style={styles.permissionText}>Camera permission required</Text>
        <TouchableOpacity style={styles.button} onPress={requestPermission}>
          <Text style={styles.buttonText}>Grant Permission</Text>
        </TouchableOpacity>
      </View>
    );
  }

  if (device == null) {
    return (
      <View style={styles.container}>
        <ActivityIndicator size="large" color={colors.primary} />
        <Text style={styles.loadingText}>Loading camera...</Text>
      </View>
    );
  }

  return (
    <View style={styles.container}>
      <Camera
        ref={camera}
        style={StyleSheet.absoluteFill}
        device={device}
        isActive={isActive && isFocused}
        codeScanner={mode === 'scanner' || mode === 'both' ? codeScanner : undefined}
        photo={mode === 'photo' || mode === 'both'}
        video={false}
        audio={false}
        torch={flashEnabled ? 'on' : 'off'}
        enableZoomGesture
      />

      {/* Scan Frame Overlay */}
      {showFrame && (mode === 'scanner' || mode === 'both') && (
        <View style={styles.overlay}>
          <View style={styles.scanFrame}>
            <View style={[styles.corner, styles.topLeft]} />
            <View style={[styles.corner, styles.topRight]} />
            <View style={[styles.corner, styles.bottomLeft]} />
            <View style={[styles.corner, styles.bottomRight]} />
          </View>
          <Text style={styles.instructionText}>
            {mode === 'scanner' ? 'Align code within frame' : 'Scan or capture'}
          </Text>
        </View>
      )}

      {/* Controls */}
      <View style={styles.controls}>
        <TouchableOpacity style={styles.controlButton} onPress={toggleFlash}>
          <Icon
            name={flashEnabled ? 'flash-on' : 'flash-off'}
            type="material"
            size={28}
            color="white"
          />
        </TouchableOpacity>

        {(mode === 'photo' || mode === 'both') && (
          <TouchableOpacity
            style={[styles.captureButton, isProcessing && styles.captureButtonDisabled]}
            onPress={takePhoto}
            disabled={isProcessing}
          >
            {isProcessing ? (
              <ActivityIndicator color="white" />
            ) : (
              <View style={styles.captureButtonInner} />
            )}
          </TouchableOpacity>
        )}

        <TouchableOpacity style={styles.controlButton} onPress={switchCamera}>
          <Icon
            name="flip-camera-ios"
            type="material"
            size={28}
            color="white"
          />
        </TouchableOpacity>
      </View>

      {/* Processing Indicator */}
      {isProcessing && (
        <View style={styles.processingOverlay}>
          <ActivityIndicator size="large" color="white" />
          <Text style={styles.processingText}>Processing...</Text>
        </View>
      )}
    </View>
  );
};

const styles = StyleSheet.create({
  container: {
    flex: 1,
    backgroundColor: 'black',
    justifyContent: 'center',
    alignItems: 'center',
  },
  permissionText: {
    color: 'white',
    fontSize: 16,
    marginBottom: 20,
    textAlign: 'center',
  },
  button: {
    backgroundColor: colors.primary,
    paddingHorizontal: 24,
    paddingVertical: 12,
    borderRadius: 8,
  },
  buttonText: {
    color: 'white',
    fontSize: 16,
    fontWeight: '600',
  },
  loadingText: {
    color: 'white',
    marginTop: 16,
    fontSize: 16,
  },
  overlay: {
    ...StyleSheet.absoluteFillObject,
    justifyContent: 'center',
    alignItems: 'center',
  },
  scanFrame: {
    width: 250,
    height: 250,
    position: 'relative',
  },
  corner: {
    position: 'absolute',
    width: 40,
    height: 40,
    borderColor: colors.primary,
    borderWidth: 3,
  },
  topLeft: {
    top: 0,
    left: 0,
    borderRightWidth: 0,
    borderBottomWidth: 0,
  },
  topRight: {
    top: 0,
    right: 0,
    borderLeftWidth: 0,
    borderBottomWidth: 0,
  },
  bottomLeft: {
    bottom: 0,
    left: 0,
    borderRightWidth: 0,
    borderTopWidth: 0,
  },
  bottomRight: {
    bottom: 0,
    right: 0,
    borderLeftWidth: 0,
    borderTopWidth: 0,
  },
  instructionText: {
    position: 'absolute',
    bottom: -40,
    color: 'white',
    fontSize: 16,
    textAlign: 'center',
  },
  controls: {
    position: 'absolute',
    bottom: 50,
    left: 0,
    right: 0,
    flexDirection: 'row',
    justifyContent: 'space-around',
    alignItems: 'center',
    paddingHorizontal: 40,
  },
  controlButton: {
    width: 50,
    height: 50,
    borderRadius: 25,
    backgroundColor: 'rgba(0, 0, 0, 0.5)',
    justifyContent: 'center',
    alignItems: 'center',
  },
  captureButton: {
    width: 70,
    height: 70,
    borderRadius: 35,
    backgroundColor: 'white',
    justifyContent: 'center',
    alignItems: 'center',
  },
  captureButtonDisabled: {
    opacity: 0.5,
  },
  captureButtonInner: {
    width: 60,
    height: 60,
    borderRadius: 30,
    backgroundColor: colors.primary,
  },
  processingOverlay: {
    ...StyleSheet.absoluteFillObject,
    backgroundColor: 'rgba(0, 0, 0, 0.7)',
    justifyContent: 'center',
    alignItems: 'center',
  },
  processingText: {
    color: 'white',
    marginTop: 16,
    fontSize: 16,
  },
});