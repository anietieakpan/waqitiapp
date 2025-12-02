import React, { useState, useEffect } from 'react';
import {
  View,
  Text,
  TouchableOpacity,
  StyleSheet,
  Animated,
  Alert,
  Platform
} from 'react-native';
import Icon from 'react-native-vector-icons/MaterialIcons';
import LinearGradient from 'react-native-linear-gradient';
import VoicePaymentService, { VoiceCommand } from '../../services/voice/VoicePaymentService';

interface VoicePaymentButtonProps {
  onCommandRecognized?: (command: VoiceCommand) => void;
  onError?: (error: string) => void;
  style?: any;
  size?: 'small' | 'medium' | 'large';
  variant?: 'primary' | 'secondary' | 'minimal';
}

const VoicePaymentButton: React.FC<VoicePaymentButtonProps> = ({
  onCommandRecognized,
  onError,
  style,
  size = 'medium',
  variant = 'primary'
}) => {
  const [isListening, setIsListening] = useState(false);
  const [isAvailable, setIsAvailable] = useState(false);
  const [animatedValue] = useState(new Animated.Value(1));
  const [pulseAnim] = useState(new Animated.Value(0));

  useEffect(() => {
    checkVoiceAvailability();
  }, []);

  useEffect(() => {
    if (isListening) {
      startPulseAnimation();
    } else {
      stopPulseAnimation();
    }
  }, [isListening]);

  const checkVoiceAvailability = async () => {
    try {
      const available = await VoicePaymentService.isAvailable();
      setIsAvailable(available);
    } catch (error) {
      console.error('Failed to check voice availability:', error);
      setIsAvailable(false);
    }
  };

  const startPulseAnimation = () => {
    Animated.loop(
      Animated.sequence([
        Animated.timing(pulseAnim, {
          toValue: 1,
          duration: 1000,
          useNativeDriver: true,
        }),
        Animated.timing(pulseAnim, {
          toValue: 0,
          duration: 1000,
          useNativeDriver: true,
        }),
      ])
    ).start();
  };

  const stopPulseAnimation = () => {
    pulseAnim.stopAnimation();
    pulseAnim.setValue(0);
  };

  const handlePress = async () => {
    if (!isAvailable) {
      Alert.alert(
        'Voice Not Available',
        'Voice payments are not available on this device or are disabled in settings.'
      );
      return;
    }

    try {
      if (isListening) {
        await stopListening();
      } else {
        await startListening();
      }
    } catch (error) {
      console.error('Voice operation failed:', error);
      onError?.(error.message);
      Alert.alert('Voice Error', 'Failed to start voice recognition. Please try again.');
    }
  };

  const startListening = async () => {
    try {
      setIsListening(true);
      
      // Animate button press
      Animated.sequence([
        Animated.timing(animatedValue, {
          toValue: 0.95,
          duration: 100,
          useNativeDriver: true,
        }),
        Animated.timing(animatedValue, {
          toValue: 1,
          duration: 100,
          useNativeDriver: true,
        }),
      ]).start();

      await VoicePaymentService.startListening();

      // Setup timeout to stop listening after 10 seconds
      setTimeout(async () => {
        if (isListening) {
          await stopListening();
        }
      }, 10000);

    } catch (error) {
      setIsListening(false);
      throw error;
    }
  };

  const stopListening = async () => {
    try {
      await VoicePaymentService.stopListening();
      setIsListening(false);
    } catch (error) {
      console.error('Failed to stop listening:', error);
      setIsListening(false);
    }
  };

  const getButtonSize = () => {
    switch (size) {
      case 'small':
        return { width: 48, height: 48 };
      case 'large':
        return { width: 80, height: 80 };
      default:
        return { width: 64, height: 64 };
    }
  };

  const getIconSize = () => {
    switch (size) {
      case 'small':
        return 20;
      case 'large':
        return 36;
      default:
        return 28;
    }
  };

  const renderButton = () => {
    const buttonSize = getButtonSize();
    const iconSize = getIconSize();

    if (variant === 'minimal') {
      return (
        <Animated.View
          style={[
            styles.minimalButton,
            buttonSize,
            {
              transform: [{ scale: animatedValue }],
              opacity: isAvailable ? 1 : 0.5,
            },
            style,
          ]}
        >
          <Icon
            name={isListening ? 'mic' : 'mic-none'}
            size={iconSize}
            color={isListening ? '#FF4444' : '#666666'}
          />
        </Animated.View>
      );
    }

    const gradientColors = variant === 'primary' 
      ? ['#667eea', '#764ba2']
      : ['#f093fb', '#f5576c'];

    return (
      <Animated.View
        style={[
          {
            transform: [{ scale: animatedValue }],
            opacity: isAvailable ? 1 : 0.5,
          },
          style,
        ]}
      >
        <LinearGradient
          colors={gradientColors}
          style={[styles.gradientButton, buttonSize]}
          start={{ x: 0, y: 0 }}
          end={{ x: 1, y: 1 }}
        >
          {isListening && (
            <Animated.View
              style={[
                styles.pulseCircle,
                buttonSize,
                {
                  opacity: pulseAnim.interpolate({
                    inputRange: [0, 1],
                    outputRange: [0.3, 0],
                  }),
                  transform: [
                    {
                      scale: pulseAnim.interpolate({
                        inputRange: [0, 1],
                        outputRange: [1, 1.5],
                      }),
                    },
                  ],
                },
              ]}
            />
          )}
          
          <Icon
            name={isListening ? 'mic' : 'mic-none'}
            size={iconSize}
            color="#FFFFFF"
          />
          
          {isListening && (
            <View style={styles.listeningIndicator}>
              <Text style={styles.listeningText}>Listening...</Text>
            </View>
          )}
        </LinearGradient>
      </Animated.View>
    );
  };

  if (!isAvailable && variant !== 'minimal') {
    return null; // Don't render if voice is not available
  }

  return (
    <TouchableOpacity
      onPress={handlePress}
      disabled={!isAvailable}
      style={styles.container}
      activeOpacity={0.8}
    >
      {renderButton()}
    </TouchableOpacity>
  );
};

const styles = StyleSheet.create({
  container: {
    alignItems: 'center',
    justifyContent: 'center',
  },
  gradientButton: {
    borderRadius: 32,
    alignItems: 'center',
    justifyContent: 'center',
    shadowColor: '#000',
    shadowOffset: {
      width: 0,
      height: 4,
    },
    shadowOpacity: 0.3,
    shadowRadius: 4.65,
    elevation: 8,
    position: 'relative',
  },
  minimalButton: {
    borderRadius: 24,
    alignItems: 'center',
    justifyContent: 'center',
    backgroundColor: '#F5F5F5',
    borderWidth: 1,
    borderColor: '#E0E0E0',
  },
  pulseCircle: {
    position: 'absolute',
    borderRadius: 40,
    backgroundColor: '#FFFFFF',
  },
  listeningIndicator: {
    position: 'absolute',
    bottom: -24,
    alignItems: 'center',
  },
  listeningText: {
    fontSize: 12,
    color: '#666666',
    fontWeight: '500',
  },
});

export default VoicePaymentButton;