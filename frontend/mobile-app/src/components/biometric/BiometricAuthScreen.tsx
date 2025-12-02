import React, { useState, useEffect, useCallback } from 'react';
import {
  View,
  Text,
  TouchableOpacity,
  Alert,
  Platform,
  Image,
  ActivityIndicator,
  Animated,
  Vibration,
} from 'react-native';
import TouchID from 'react-native-touch-id';
import { Camera, useCameraDevices } from 'react-native-vision-camera';
import { check, request, PERMISSIONS, RESULTS } from 'react-native-permissions';
import { useDispatch, useSelector } from 'react-redux';
import { useNavigation } from '@react-navigation/native';
import styled from 'styled-components/native';
import LottieView from 'lottie-react-native';

import { BiometricService } from '../../services/BiometricService';
import { AuthService } from '../../services/AuthService';
import { showSuccessToast, showErrorToast } from '../../utils/toast';
import { logBiometricEvent } from '../../utils/analytics';
import { RootState } from '../../store';
import { setUserAuthenticated, setBiometricEnabled } from '../../store/slices/authSlice';

// Styled Components
const Container = styled.View`
  flex: 1;
  background-color: ${({ theme }) => theme.colors.background.primary};
  padding: 20px;
  justify-content: center;
  align-items: center;
`;

const Title = styled.Text`
  font-size: 28px;
  font-weight: bold;
  color: ${({ theme }) => theme.colors.text.primary};
  text-align: center;
  margin-bottom: 10px;
`;

const Subtitle = styled.Text`
  font-size: 16px;
  color: ${({ theme }) => theme.colors.text.secondary};
  text-align: center;
  margin-bottom: 40px;
  line-height: 24px;
`;

const BiometricContainer = styled.View`
  align-items: center;
  margin-bottom: 40px;
`;

const BiometricIcon = styled.View`
  width: 120px;
  height: 120px;
  border-radius: 60px;
  background-color: ${({ theme }) => theme.colors.background.secondary};
  justify-content: center;
  align-items: center;
  margin-bottom: 20px;
  elevation: 8;
  shadow-color: #000;
  shadow-offset: 0px 4px;
  shadow-opacity: 0.1;
  shadow-radius: 8px;
`;

const StatusText = styled.Text`
  font-size: 18px;
  color: ${({ theme }) => theme.colors.text.primary};
  text-align: center;
  margin-bottom: 10px;
`;

const InstructionText = styled.Text`
  font-size: 14px;
  color: ${({ theme }) => theme.colors.text.secondary};
  text-align: center;
  margin-bottom: 30px;
`;

const BiometricButton = styled.TouchableOpacity`
  background-color: ${({ theme }) => theme.colors.primary.main};
  padding: 16px 32px;
  border-radius: 25px;
  margin: 10px;
  min-width: 200px;
  align-items: center;
  elevation: 4;
  shadow-color: #000;
  shadow-offset: 0px 2px;
  shadow-opacity: 0.1;
  shadow-radius: 4px;
`;

const ButtonText = styled.Text`
  color: white;
  font-size: 16px;
  font-weight: 600;
`;

const SecondaryButton = styled.TouchableOpacity`
  background-color: transparent;
  border: 2px solid ${({ theme }) => theme.colors.primary.main};
  padding: 14px 32px;
  border-radius: 25px;
  margin: 10px;
  min-width: 200px;
  align-items: center;
`;

const SecondaryButtonText = styled.Text`
  color: ${({ theme }) => theme.colors.primary.main};
  font-size: 16px;
  font-weight: 600;
`;

const SkipButton = styled.TouchableOpacity`
  padding: 10px;
  margin-top: 20px;
`;

const SkipText = styled.Text`
  color: ${({ theme }) => theme.colors.text.secondary};
  font-size: 14px;
  text-decoration: underline;
`;

const LivenessContainer = styled.View`
  width: 280px;
  height: 280px;
  border-radius: 140px;
  border: 3px solid ${({ theme }) => theme.colors.primary.main};
  justify-content: center;
  align-items: center;
  margin-bottom: 20px;
`;

const ErrorContainer = styled.View`
  background-color: ${({ theme }) => theme.colors.error.light};
  padding: 15px;
  border-radius: 10px;
  margin: 10px 0;
  align-items: center;
`;

const ErrorText = styled.Text`
  color: ${({ theme }) => theme.colors.error.main};
  font-size: 14px;
  text-align: center;
`;

// Interface Definitions
interface BiometricAuthScreenProps {
  onAuthSuccess: (authData: AuthData) => void;
  onSkip?: () => void;
  allowSkip?: boolean;
  biometricType?: 'fingerprint' | 'face' | 'voice' | 'multimodal';
  title?: string;
  subtitle?: string;
}

interface AuthData {
  authToken: string;
  credentialId: string;
  biometricType: string;
  confidence: number;
}

interface BiometricCapabilities {
  fingerprintSupported: boolean;
  faceIdSupported: boolean;
  voiceSupported: boolean;
  platformAuthenticatorAvailable: boolean;
}

// TouchID Configuration
const touchIdConfig = {
  title: 'Authenticate with Biometric',
  subTitle: 'Use your fingerprint or Face ID to access your account',
  imageColor: '#e00606',
  imageErrorColor: '#ff0000',
  sensorDescription: 'Touch sensor',
  sensorErrorDescription: 'Failed',
  cancelText: 'Cancel',
  fallbackLabel: 'Use Password',
  unifiedErrors: false,
  passcodeFallback: false,
};

const BiometricAuthScreen: React.FC<BiometricAuthScreenProps> = ({
  onAuthSuccess,
  onSkip,
  allowSkip = false,
  biometricType = 'multimodal',
  title = 'Secure Biometric Authentication',
  subtitle = 'Use your biometric to securely access your Waqiti account',
}) => {
  const dispatch = useDispatch();
  const navigation = useNavigation();
  const { user, isAuthenticated } = useSelector((state: RootState) => state.auth);
  
  // State Management
  const [authState, setAuthState] = useState<'idle' | 'authenticating' | 'success' | 'error'>('idle');
  const [authMethod, setAuthMethod] = useState<string>('');
  const [errorMessage, setErrorMessage] = useState<string>('');
  const [capabilities, setCapabilities] = useState<BiometricCapabilities | null>(null);
  const [isRegistering, setIsRegistering] = useState(false);
  const [livenessCheck, setLivenessCheck] = useState(false);
  const [permissionsGranted, setPermissionsGranted] = useState(false);
  
  // Camera Setup
  const devices = useCameraDevices();
  const frontCamera = devices.front;
  
  // Animations
  const pulseAnimation = new Animated.Value(1);
  const fadeAnimation = new Animated.Value(0);

  useEffect(() => {
    initializeBiometricAuth();
    startPulseAnimation();
    
    Animated.timing(fadeAnimation, {
      toValue: 1,
      duration: 800,
      useNativeDriver: true,
    }).start();
  }, []);

  const startPulseAnimation = () => {
    Animated.loop(
      Animated.sequence([
        Animated.timing(pulseAnimation, {
          toValue: 1.1,
          duration: 1000,
          useNativeDriver: true,
        }),
        Animated.timing(pulseAnimation, {
          toValue: 1,
          duration: 1000,
          useNativeDriver: true,
        }),
      ])
    ).start();
  };

  const initializeBiometricAuth = async () => {
    try {
      // Check permissions
      await requestPermissions();
      
      // Check biometric capabilities
      const caps = await BiometricService.checkBiometricCapabilities();
      setCapabilities(caps);
      
      // Auto-authenticate if biometric is available and user preference
      if (caps.fingerprintSupported || caps.faceIdSupported) {
        const autoAuth = await BiometricService.getAutoAuthPreference();
        if (autoAuth) {
          setTimeout(() => authenticateWithFingerprint(), 1000);
        }
      }
    } catch (error) {
      console.error('Failed to initialize biometric auth:', error);
      setErrorMessage('Failed to initialize biometric authentication');
    }
  };

  const requestPermissions = async () => {
    try {
      let permissions = [];
      
      if (Platform.OS === 'ios') {
        permissions = [PERMISSIONS.IOS.CAMERA, PERMISSIONS.IOS.MICROPHONE];
      } else {
        permissions = [
          PERMISSIONS.ANDROID.CAMERA,
          PERMISSIONS.ANDROID.RECORD_AUDIO,
          PERMISSIONS.ANDROID.USE_FINGERPRINT,
          PERMISSIONS.ANDROID.USE_BIOMETRIC,
        ];
      }

      const results = await Promise.all(
        permissions.map(permission => request(permission))
      );

      const allGranted = results.every(result => result === RESULTS.GRANTED);
      setPermissionsGranted(allGranted);
      
      if (!allGranted) {
        setErrorMessage('Camera and microphone permissions are required for biometric authentication');
      }
    } catch (error) {
      console.error('Permission request failed:', error);
    }
  };

  const authenticateWithFingerprint = useCallback(async () => {
    if (!capabilities?.fingerprintSupported) {
      setErrorMessage('Fingerprint authentication is not available on this device');
      return;
    }

    setAuthState('authenticating');
    setAuthMethod('fingerprint');
    setErrorMessage('');

    try {
      // Check TouchID/FaceID availability
      const biometryType = await TouchID.isSupported(touchIdConfig);
      
      // Perform authentication
      await TouchID.authenticate('Use your fingerprint to authenticate', touchIdConfig);
      
      // Generate authentication data
      const authData = await BiometricService.authenticateWithFingerprint({
        userId: user?.id,
        biometricType: 'FINGERPRINT',
        challenge: await BiometricService.generateChallenge(),
        timestamp: Date.now(),
      });

      handleAuthSuccess(authData);
      
    } catch (error: any) {
      console.error('Fingerprint authentication failed:', error);
      handleAuthError(error.message || 'Fingerprint authentication failed');
      
      // Log authentication failure
      logBiometricEvent('authentication_failed', {
        method: 'fingerprint',
        error: error.message,
        userId: user?.id,
      });
    }
  }, [capabilities, user]);

  const authenticateWithFaceID = useCallback(async () => {
    if (!capabilities?.faceIdSupported || !frontCamera) {
      setErrorMessage('Face ID authentication is not available on this device');
      return;
    }

    setAuthState('authenticating');
    setAuthMethod('face');
    setErrorMessage('');
    setLivenessCheck(true);

    try {
      // Start liveness detection
      const livenessResult = await BiometricService.performLivenessDetection({
        challengeType: 'BLINK_AND_SMILE',
        timeout: 30000,
      });

      if (!livenessResult.isAlive) {
        throw new Error('Liveness detection failed. Please try again.');
      }

      // Capture face image for authentication
      const faceData = await BiometricService.captureFaceImage();
      
      // Authenticate with face
      const authData = await BiometricService.authenticateWithFace({
        userId: user?.id,
        biometricType: 'FACE_ID',
        faceData: faceData,
        livenessData: livenessResult,
        challenge: await BiometricService.generateChallenge(),
        timestamp: Date.now(),
      });

      handleAuthSuccess(authData);
      
    } catch (error: any) {
      console.error('Face ID authentication failed:', error);
      handleAuthError(error.message || 'Face ID authentication failed');
      
      logBiometricEvent('authentication_failed', {
        method: 'face_id',
        error: error.message,
        userId: user?.id,
      });
    } finally {
      setLivenessCheck(false);
    }
  }, [capabilities, frontCamera, user]);

  const authenticateWithVoice = useCallback(async () => {
    if (!capabilities?.voiceSupported) {
      setErrorMessage('Voice authentication is not available on this device');
      return;
    }

    setAuthState('authenticating');
    setAuthMethod('voice');
    setErrorMessage('');

    try {
      // Record voice sample
      const voiceData = await BiometricService.recordVoiceSample({
        duration: 5000,
        phrase: 'My voice is my password, verify me',
        quality: 'high',
      });

      // Authenticate with voice
      const authData = await BiometricService.authenticateWithVoice({
        userId: user?.id,
        biometricType: 'VOICE',
        voiceData: voiceData,
        challenge: await BiometricService.generateChallenge(),
        timestamp: Date.now(),
      });

      handleAuthSuccess(authData);
      
    } catch (error: any) {
      console.error('Voice authentication failed:', error);
      handleAuthError(error.message || 'Voice authentication failed');
      
      logBiometricEvent('authentication_failed', {
        method: 'voice',
        error: error.message,
        userId: user?.id,
      });
    }
  }, [capabilities, user]);

  const authenticateWithMultimodal = useCallback(async () => {
    setAuthState('authenticating');
    setAuthMethod('multimodal');
    setErrorMessage('');

    try {
      // Perform multiple biometric checks in sequence
      const authResults = [];

      // Face ID first
      if (capabilities?.faceIdSupported) {
        const faceResult = await BiometricService.authenticateWithFace({
          userId: user?.id,
          biometricType: 'FACE_ID',
          challenge: await BiometricService.generateChallenge(),
          timestamp: Date.now(),
        });
        authResults.push(faceResult);
      }

      // Fingerprint second
      if (capabilities?.fingerprintSupported) {
        await TouchID.authenticate('Confirm with fingerprint', touchIdConfig);
        const fingerprintResult = await BiometricService.authenticateWithFingerprint({
          userId: user?.id,
          biometricType: 'FINGERPRINT',
          challenge: await BiometricService.generateChallenge(),
          timestamp: Date.now(),
        });
        authResults.push(fingerprintResult);
      }

      // Combine results for enhanced security
      const combinedAuth = await BiometricService.combineMultimodalAuth(authResults);
      
      if (combinedAuth.confidence < 0.8) {
        throw new Error('Multimodal authentication confidence too low');
      }

      handleAuthSuccess(combinedAuth);
      
    } catch (error: any) {
      console.error('Multimodal authentication failed:', error);
      handleAuthError(error.message || 'Multimodal authentication failed');
      
      logBiometricEvent('authentication_failed', {
        method: 'multimodal',
        error: error.message,
        userId: user?.id,
      });
    }
  }, [capabilities, user]);

  const handleAuthSuccess = (authData: AuthData) => {
    setAuthState('success');
    Vibration.vibrate(100);
    
    // Log successful authentication
    logBiometricEvent('authentication_success', {
      method: authData.biometricType,
      confidence: authData.confidence,
      userId: user?.id,
    });

    // Update app state
    dispatch(setUserAuthenticated(true));
    dispatch(setBiometricEnabled(true));
    
    // Show success feedback
    showSuccessToast('Authentication successful');
    
    // Call success callback
    setTimeout(() => {
      onAuthSuccess(authData);
    }, 1000);
  };

  const handleAuthError = (error: string) => {
    setAuthState('error');
    setErrorMessage(error);
    Vibration.vibrate([100, 200, 100]);
    
    showErrorToast(error);
    
    // Reset to idle after 3 seconds
    setTimeout(() => {
      setAuthState('idle');
      setErrorMessage('');
    }, 3000);
  };

  const handleRegisterBiometric = async () => {
    setIsRegistering(true);
    
    try {
      const success = await BiometricService.registerBiometric({
        userId: user?.id,
        biometricType: 'FINGERPRINT', // Start with fingerprint
        deviceInfo: await BiometricService.getDeviceInfo(),
      });
      
      if (success) {
        showSuccessToast('Biometric registered successfully');
        // Refresh capabilities
        const caps = await BiometricService.checkBiometricCapabilities();
        setCapabilities(caps);
      }
    } catch (error: any) {
      showErrorToast(error.message || 'Failed to register biometric');
    } finally {
      setIsRegistering(false);
    }
  };

  const renderBiometricIcon = () => {
    let iconSource;
    let animationSource;
    
    switch (authMethod) {
      case 'fingerprint':
        iconSource = require('../../assets/icons/fingerprint.png');
        animationSource = require('../../assets/animations/fingerprint-scan.json');
        break;
      case 'face':
        iconSource = require('../../assets/icons/face-id.png');
        animationSource = require('../../assets/animations/face-scan.json');
        break;
      case 'voice':
        iconSource = require('../../assets/icons/voice.png');
        animationSource = require('../../assets/animations/voice-wave.json');
        break;
      default:
        iconSource = require('../../assets/icons/biometric.png');
        animationSource = require('../../assets/animations/biometric-pulse.json');
    }

    return (
      <Animated.View
        style={{
          transform: [{ scale: pulseAnimation }],
          opacity: fadeAnimation,
        }}
      >
        <BiometricIcon>
          {authState === 'authenticating' ? (
            <LottieView
              source={animationSource}
              autoPlay
              loop
              style={{ width: 80, height: 80 }}
            />
          ) : (
            <Image
              source={iconSource}
              style={{ width: 60, height: 60, tintColor: '#007AFF' }}
              resizeMode="contain"
            />
          )}
        </BiometricIcon>
      </Animated.View>
    );
  };

  const renderStatusMessage = () => {
    switch (authState) {
      case 'authenticating':
        return (
          <>
            <StatusText>Authenticating...</StatusText>
            <InstructionText>
              {authMethod === 'fingerprint' && 'Place your finger on the sensor'}
              {authMethod === 'face' && 'Look at the camera and follow instructions'}
              {authMethod === 'voice' && 'Speak your passphrase clearly'}
              {authMethod === 'multimodal' && 'Follow the authentication steps'}
            </InstructionText>
          </>
        );
      case 'success':
        return <StatusText style={{ color: '#28a745' }}>Authentication Successful!</StatusText>;
      case 'error':
        return (
          <ErrorContainer>
            <ErrorText>{errorMessage}</ErrorText>
          </ErrorContainer>
        );
      default:
        return (
          <>
            <StatusText>Choose Authentication Method</StatusText>
            <InstructionText>Select your preferred biometric authentication method</InstructionText>
          </>
        );
    }
  };

  const renderAuthButtons = () => {
    if (authState === 'authenticating' || authState === 'success') {
      return <ActivityIndicator size="large" color="#007AFF" />;
    }

    return (
      <View style={{ alignItems: 'center' }}>
        {capabilities?.fingerprintSupported && (
          <BiometricButton onPress={authenticateWithFingerprint}>
            <ButtonText>Use Fingerprint</ButtonText>
          </BiometricButton>
        )}
        
        {capabilities?.faceIdSupported && (
          <BiometricButton onPress={authenticateWithFaceID}>
            <ButtonText>Use Face ID</ButtonText>
          </BiometricButton>
        )}
        
        {capabilities?.voiceSupported && (
          <SecondaryButton onPress={authenticateWithVoice}>
            <SecondaryButtonText>Use Voice</SecondaryButtonText>
          </SecondaryButton>
        )}
        
        {(capabilities?.fingerprintSupported && capabilities?.faceIdSupported) && (
          <BiometricButton onPress={authenticateWithMultimodal}>
            <ButtonText>Enhanced Security</ButtonText>
          </BiometricButton>
        )}
        
        {!capabilities?.fingerprintSupported && !capabilities?.faceIdSupported && (
          <SecondaryButton onPress={handleRegisterBiometric} disabled={isRegistering}>
            <SecondaryButtonText>
              {isRegistering ? 'Registering...' : 'Register Biometric'}
            </SecondaryButtonText>
          </SecondaryButton>
        )}
      </View>
    );
  };

  return (
    <Container>
      <Animated.View style={{ opacity: fadeAnimation, alignItems: 'center' }}>
        <Title>{title}</Title>
        <Subtitle>{subtitle}</Subtitle>
        
        <BiometricContainer>
          {renderBiometricIcon()}
          {renderStatusMessage()}
        </BiometricContainer>
        
        {livenessCheck && (
          <LivenessContainer>
            <Camera
              style={{ width: 260, height: 260, borderRadius: 130 }}
              device={frontCamera}
              isActive={true}
              photo={true}
            />
          </LivenessContainer>
        )}
        
        {renderAuthButtons()}
        
        {allowSkip && authState !== 'authenticating' && (
          <SkipButton onPress={onSkip}>
            <SkipText>Skip for now</SkipText>
          </SkipButton>
        )}
      </Animated.View>
    </Container>
  );
};

export default BiometricAuthScreen;