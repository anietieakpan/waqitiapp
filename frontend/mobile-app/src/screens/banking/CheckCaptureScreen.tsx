/**
 * CheckCaptureScreen - Main screen for capturing check images
 * Provides camera interface with guidance overlay and validation
 */

import React, { useState, useRef, useEffect, useCallback } from 'react';
import {
  View,
  Text,
  StyleSheet,
  TouchableOpacity,
  Alert,
  Platform,
  StatusBar,
  Dimensions,
  BackHandler,
  Vibration,
} from 'react-native';
import {
  Surface,
  useTheme,
  IconButton,
  Button,
  Portal,
  Modal,
  ActivityIndicator,
} from 'react-native-paper';
import { PhotoFile } from 'react-native-vision-camera';
import { useNavigation, useRoute, useFocusEffect } from '@react-navigation/native';
import { PERMISSIONS, request, check, RESULTS } from 'react-native-permissions';
import Icon from 'react-native-vector-icons/MaterialCommunityIcons';
import { useSelector } from 'react-redux';

import { RootState } from '../../store/store';
import CheckGuidanceOverlay from '../../components/banking/CheckGuidanceOverlay';
import CheckImageValidator from '../../components/banking/CheckImageValidator';
import { useBiometric } from '../../hooks/useBiometric';
import { showToast } from '../../utils/toast';
import { VisionCameraScanner } from '../../components/camera/VisionCameraScanner';

interface CaptureSession {
  frontImage?: string;
  backImage?: string;
  currentSide: 'front' | 'back';
  sessionId: string;
}

interface RouteParams {
  CheckCapture: {
    resumeSession?: CaptureSession;
  };
}

const { width: screenWidth, height: screenHeight } = Dimensions.get('window');

/**
 * Check Capture Screen Component
 */
const CheckCaptureScreen: React.FC = () => {
  const theme = useTheme();
  const navigation = useNavigation();
  const route = useRoute<any>();
  const { canAuthenticate } = useBiometric();
  
  // Camera ref
  const cameraRef = useRef(null);
  
  // State
  const [hasPermission, setHasPermission] = useState<boolean | null>(null);
  const [cameraReady, setCameraReady] = useState(false);
  const [isCapturing, setIsCapturing] = useState(false);
  const [flashMode, setFlashMode] = useState(false);
  const [showGuidance, setShowGuidance] = useState(true);
  const [showPermissionModal, setShowPermissionModal] = useState(false);
  const [captureSession, setCaptureSession] = useState<CaptureSession>(() => ({
    frontImage: route.params?.resumeSession?.frontImage,
    backImage: route.params?.resumeSession?.backImage,
    currentSide: route.params?.resumeSession?.currentSide || 'front',
    sessionId: route.params?.resumeSession?.sessionId || `session_${Date.now()}`,
  }));
  
  // Redux state
  const user = useSelector((state: RootState) => state.auth.user);
  
  // Request camera permission
  const requestCameraPermission = useCallback(async () => {
    try {
      const permission = Platform.OS === 'ios' 
        ? PERMISSIONS.IOS.CAMERA 
        : PERMISSIONS.ANDROID.CAMERA;
      
      const result = await request(permission);
      
      switch (result) {
        case RESULTS.GRANTED:
          setHasPermission(true);
          break;
        case RESULTS.DENIED:
        case RESULTS.BLOCKED:
          setHasPermission(false);
          setShowPermissionModal(true);
          break;
        case RESULTS.UNAVAILABLE:
          Alert.alert(
            'Camera Unavailable',
            'Camera is not available on this device.',
            [{ text: 'OK', onPress: () => navigation.goBack() }]
          );
          break;
      }
    } catch (error) {
      console.error('Permission request error:', error);
      setHasPermission(false);
    }
  }, [navigation]);
  
  // Check camera permission
  const checkCameraPermission = useCallback(async () => {
    try {
      const permission = Platform.OS === 'ios' 
        ? PERMISSIONS.IOS.CAMERA 
        : PERMISSIONS.ANDROID.CAMERA;
      
      const result = await check(permission);
      
      switch (result) {
        case RESULTS.GRANTED:
          setHasPermission(true);
          break;
        case RESULTS.DENIED:
        case RESULTS.BLOCKED:
          await requestCameraPermission();
          break;
        case RESULTS.UNAVAILABLE:
          Alert.alert(
            'Camera Unavailable',
            'Camera is not available on this device.',
            [{ text: 'OK', onPress: () => navigation.goBack() }]
          );
          break;
      }
    } catch (error) {
      console.error('Permission check error:', error);
      await requestCameraPermission();
    }
  }, [requestCameraPermission]);
  
  // Initial permission check
  useEffect(() => {
    checkCameraPermission();
  }, [checkCameraPermission]);
  
  // Handle back button
  useFocusEffect(
    useCallback(() => {
      const onBackPress = () => {
        if (captureSession.frontImage || captureSession.backImage) {
          Alert.alert(
            'Exit Check Capture',
            'You have captured images. Do you want to save this session and continue later?',
            [
              {
                text: 'Discard',
                style: 'destructive',
                onPress: () => navigation.goBack(),
              },
              {
                text: 'Save Session',
                onPress: () => {
                  // Save session to secure storage or state
                  showToast('Session saved. You can resume later from your drafts.', 'success');
                  navigation.goBack();
                },
              },
              {
                text: 'Continue',
                style: 'cancel',
              },
            ]
          );
          return true;
        }
        return false;
      };
      
      BackHandler.addEventListener('hardwareBackPress', onBackPress);
      return () => BackHandler.removeEventListener('hardwareBackPress', onBackPress);
    }, [navigation, captureSession])
  );
  
  // Handle camera ready
  const handleCameraReady = useCallback(() => {
    setCameraReady(true);
    if (showGuidance) {
      // Auto-hide guidance after 5 seconds
      setTimeout(() => setShowGuidance(false), 5000);
    }
  }, [showGuidance]);
  
  // Toggle flash mode
  const toggleFlash = useCallback(() => {
    setFlashMode(!flashMode);
  }, [flashMode]);
  
  // Get flash icon
  const getFlashIcon = useCallback(() => {
    return flashMode ? 'flash' : 'flash-off';
  }, [flashMode]);
  
  // Handle photo capture
  const handleCapture = useCallback(async (photo: PhotoFile) => {
    try {
      setIsCapturing(true);
      
      // Provide haptic feedback
      Vibration.vibrate(50);
      
      // Update capture session
      const updatedSession = {
        ...captureSession,
        [captureSession.currentSide === 'front' ? 'frontImage' : 'backImage']: photo.path,
      };
      setCaptureSession(updatedSession);
      
      // Navigate to preview screen
      navigation.navigate('CheckPreview', {
        imageUri: photo.path,
        side: captureSession.currentSide,
        session: updatedSession,
      } as never);
      
    } catch (error) {
      console.error('Capture error:', error);
      showToast('Failed to capture image. Please try again.', 'error');
    } finally {
      setIsCapturing(false);
    }
  }, [isCapturing, captureSession, navigation]);
  
  // Switch to next side
  const switchSide = useCallback(() => {
    const nextSide = captureSession.currentSide === 'front' ? 'back' : 'front';
    setCaptureSession(prev => ({ ...prev, currentSide: nextSide }));
    setShowGuidance(true);
    setTimeout(() => setShowGuidance(false), 3000);
  }, [captureSession.currentSide]);
  
  // Handle gallery import
  const handleGalleryImport = useCallback(() => {
    Alert.alert(
      'Import from Gallery',
      'You can import check images from your photo gallery.',
      [
        { text: 'Cancel', style: 'cancel' },
        {
          text: 'Import',
          onPress: () => {
            // TODO: Implement gallery import
            showToast('Gallery import feature coming soon!', 'info');
          },
        },
      ]
    );
  }, []);
  
  // Open settings for permission
  const openSettings = useCallback(() => {
    setShowPermissionModal(false);
    // TODO: Open device settings
    showToast('Please enable camera permission in device settings.', 'info');
  }, []);
  
  // Get current side instruction
  const getCurrentSideInstruction = useCallback(() => {
    return captureSession.currentSide === 'front' 
      ? 'Position the front of your check within the frame'
      : 'Now position the back of your check within the frame';
  }, [captureSession.currentSide]);
  
  // Check if session is complete
  const isSessionComplete = captureSession.frontImage && captureSession.backImage;
  
  // Show loading while checking permissions
  if (hasPermission === null) {
    return (
      <View style={styles.loadingContainer}>
        <ActivityIndicator size="large" color={theme.colors.primary} />
        <Text style={styles.loadingText}>Checking camera permissions...</Text>
      </View>
    );
  }
  
  // Show permission denied screen
  if (hasPermission === false) {
    return (
      <View style={styles.permissionContainer}>
        <Icon name="camera-off" size={64} color={theme.colors.outline} />
        <Text style={styles.permissionTitle}>Camera Permission Required</Text>
        <Text style={styles.permissionMessage}>
          To capture check images, please allow camera access in your device settings.
        </Text>
        <Button
          mode="contained"
          onPress={openSettings}
          style={styles.settingsButton}
        >
          Open Settings
        </Button>
      </View>
    );
  }
  
  return (
    <View style={styles.container}>
      <StatusBar barStyle="light-content" backgroundColor="black" />
      
      {/* Camera View */}
      <VisionCameraScanner
        onCapture={handleCapture}
        mode="photo"
        showFrame={false}
      />
      
      {/* Check Guidance Overlay */}
      {showGuidance && (
        <CheckGuidanceOverlay
          side={captureSession.currentSide}
          onClose={() => setShowGuidance(false)}
        />
      )}
      
      {/* Image Validator */}
      <CheckImageValidator
        cameraRef={cameraRef}
        side={captureSession.currentSide}
        enabled={cameraReady && !showGuidance}
      />
      
      {/* Top Controls */}
      <View style={styles.topControls}>
        <TouchableOpacity
          style={styles.closeButton}
          onPress={() => navigation.goBack()}
        >
          <Icon name="close" size={24} color="white" />
        </TouchableOpacity>
        
        <View style={styles.topCenterControls}>
          <Text style={styles.sideIndicator}>
            {captureSession.currentSide === 'front' ? 'FRONT' : 'BACK'}
          </Text>
          <Text style={styles.instruction}>
            {getCurrentSideInstruction()}
          </Text>
        </View>
        
        <TouchableOpacity
          style={styles.flashButton}
          onPress={toggleFlash}
        >
          <Icon name={getFlashIcon()} size={24} color="white" />
        </TouchableOpacity>
      </View>
      
      {/* Bottom Controls */}
      <View style={styles.bottomControls}>
        <TouchableOpacity
          style={styles.galleryButton}
          onPress={handleGalleryImport}
        >
          <Icon name="image" size={24} color="white" />
          <Text style={styles.galleryButtonText}>Gallery</Text>
        </TouchableOpacity>
        
        {/* Capture button is now handled by VisionCameraScanner */}
        <View style={styles.captureButtonPlaceholder} />
        
        <TouchableOpacity
          style={styles.switchButton}
          onPress={switchSide}
        >
          <Icon name="camera-flip" size={24} color="white" />
          <Text style={styles.switchButtonText}>
            {captureSession.currentSide === 'front' ? 'Back' : 'Front'}
          </Text>
        </TouchableOpacity>
      </View>
      
      {/* Progress Indicator */}
      <View style={styles.progressContainer}>
        <View style={styles.progressDots}>
          <View style={[
            styles.progressDot,
            captureSession.frontImage && styles.progressDotCompleted
          ]} />
          <View style={[
            styles.progressDot,
            captureSession.backImage && styles.progressDotCompleted
          ]} />
        </View>
        <Text style={styles.progressText}>
          {captureSession.frontImage && captureSession.backImage
            ? 'Both sides captured'
            : `${captureSession.frontImage ? 1 : 0} of 2 sides captured`
          }
        </Text>
      </View>
      
      {/* Permission Modal */}
      <Portal>
        <Modal
          visible={showPermissionModal}
          onDismiss={() => setShowPermissionModal(false)}
          contentContainerStyle={styles.permissionModal}
        >
          <Icon name="camera-off" size={48} color={theme.colors.outline} />
          <Text style={styles.modalTitle}>Camera Permission Needed</Text>
          <Text style={styles.modalMessage}>
            Waqiti needs camera access to capture check images for deposits.
          </Text>
          <View style={styles.modalButtons}>
            <Button
              mode="outlined"
              onPress={() => setShowPermissionModal(false)}
              style={styles.modalButton}
            >
              Cancel
            </Button>
            <Button
              mode="contained"
              onPress={openSettings}
              style={styles.modalButton}
            >
              Settings
            </Button>
          </View>
        </Modal>
      </Portal>
    </View>
  );
};

const styles = StyleSheet.create({
  container: {
    flex: 1,
    backgroundColor: 'black',
  },
  loadingContainer: {
    flex: 1,
    justifyContent: 'center',
    alignItems: 'center',
    backgroundColor: '#f5f5f5',
  },
  loadingText: {
    marginTop: 16,
    fontSize: 16,
    color: '#666',
  },
  permissionContainer: {
    flex: 1,
    justifyContent: 'center',
    alignItems: 'center',
    padding: 20,
    backgroundColor: '#f5f5f5',
  },
  permissionTitle: {
    fontSize: 20,
    fontWeight: 'bold',
    marginTop: 16,
    marginBottom: 8,
    textAlign: 'center',
  },
  permissionMessage: {
    fontSize: 16,
    color: '#666',
    textAlign: 'center',
    marginBottom: 24,
    lineHeight: 24,
  },
  settingsButton: {
    borderRadius: 8,
  },
  camera: {
    flex: 1,
  },
  topControls: {
    position: 'absolute',
    top: Platform.OS === 'ios' ? 50 : 20,
    left: 0,
    right: 0,
    flexDirection: 'row',
    alignItems: 'center',
    paddingHorizontal: 20,
    zIndex: 10,
  },
  closeButton: {
    width: 44,
    height: 44,
    borderRadius: 22,
    backgroundColor: 'rgba(0, 0, 0, 0.5)',
    justifyContent: 'center',
    alignItems: 'center',
  },
  topCenterControls: {
    flex: 1,
    alignItems: 'center',
    paddingHorizontal: 16,
  },
  sideIndicator: {
    color: 'white',
    fontSize: 16,
    fontWeight: 'bold',
    letterSpacing: 2,
  },
  instruction: {
    color: 'white',
    fontSize: 14,
    textAlign: 'center',
    marginTop: 4,
    opacity: 0.9,
  },
  flashButton: {
    width: 44,
    height: 44,
    borderRadius: 22,
    backgroundColor: 'rgba(0, 0, 0, 0.5)',
    justifyContent: 'center',
    alignItems: 'center',
  },
  bottomControls: {
    position: 'absolute',
    bottom: Platform.OS === 'ios' ? 40 : 20,
    left: 0,
    right: 0,
    flexDirection: 'row',
    alignItems: 'center',
    justifyContent: 'space-between',
    paddingHorizontal: 40,
    zIndex: 10,
  },
  galleryButton: {
    alignItems: 'center',
    padding: 8,
  },
  galleryButtonText: {
    color: 'white',
    fontSize: 12,
    marginTop: 4,
  },
  captureButton: {
    width: 80,
    height: 80,
    borderRadius: 40,
    backgroundColor: 'white',
    justifyContent: 'center',
    alignItems: 'center',
    borderWidth: 4,
    borderColor: 'rgba(255, 255, 255, 0.3)',
  },
  captureButtonDisabled: {
    backgroundColor: 'rgba(255, 255, 255, 0.5)',
  },
  captureButtonInner: {
    width: 60,
    height: 60,
    borderRadius: 30,
    backgroundColor: 'white',
    borderWidth: 2,
    borderColor: '#ccc',
  },
  captureButtonPlaceholder: {
    width: 80,
    height: 80,
  },
  switchButton: {
    alignItems: 'center',
    padding: 8,
  },
  switchButtonText: {
    color: 'white',
    fontSize: 12,
    marginTop: 4,
  },
  progressContainer: {
    position: 'absolute',
    bottom: Platform.OS === 'ios' ? 140 : 120,
    left: 0,
    right: 0,
    alignItems: 'center',
    zIndex: 10,
  },
  progressDots: {
    flexDirection: 'row',
    marginBottom: 8,
  },
  progressDot: {
    width: 12,
    height: 12,
    borderRadius: 6,
    backgroundColor: 'rgba(255, 255, 255, 0.3)',
    marginHorizontal: 4,
  },
  progressDotCompleted: {
    backgroundColor: '#4CAF50',
  },
  progressText: {
    color: 'white',
    fontSize: 12,
    textAlign: 'center',
  },
  permissionModal: {
    backgroundColor: 'white',
    padding: 20,
    margin: 20,
    borderRadius: 12,
    alignItems: 'center',
  },
  modalTitle: {
    fontSize: 18,
    fontWeight: 'bold',
    marginTop: 16,
    marginBottom: 8,
    textAlign: 'center',
  },
  modalMessage: {
    fontSize: 16,
    color: '#666',
    textAlign: 'center',
    marginBottom: 24,
    lineHeight: 24,
  },
  modalButtons: {
    flexDirection: 'row',
    gap: 12,
  },
  modalButton: {
    flex: 1,
  },
});

export default CheckCaptureScreen;