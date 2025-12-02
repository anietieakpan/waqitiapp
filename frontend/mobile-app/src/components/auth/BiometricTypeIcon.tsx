/**
 * BiometricTypeIcon - Dynamic biometric type icons and UI support
 * Provides appropriate icons and UI elements for different biometric types
 * with support for states, animations, and accessibility.
 */

import React, { useRef, useEffect } from 'react';
import {
  View,
  Text,
  StyleSheet,
  Animated,
  ViewStyle,
  AccessibilityInfo,
} from 'react-native';
import { BiometryType } from 'react-native-biometrics';

// Icon state types
export type IconState = 'idle' | 'active' | 'processing' | 'success' | 'error' | 'disabled';

// Icon size presets
export type IconSize = 'small' | 'medium' | 'large' | 'xlarge';

const sizeMap: Record<IconSize, number> = {
  small: 16,
  medium: 24,
  large: 32,
  xlarge: 48,
};

// Color schemes for different states
const stateColors = {
  idle: '#666666',
  active: '#007AFF',
  processing: '#5856D6',
  success: '#34C759',
  error: '#FF3B30',
  disabled: '#C7C7CC',
};

// Unicode icons for different biometric types
const biometricIcons = {
  TouchID: '👆',
  FaceID: '👤',
  Biometrics: '🔐',
  Iris: '👁️',
  Voice: '🎤',
  fallback: '🔑',
};

// Icon descriptions for accessibility
const iconDescriptions = {
  TouchID: 'Touch ID fingerprint sensor',
  FaceID: 'Face ID camera',
  Biometrics: 'Biometric sensor',
  Iris: 'Iris scanner',
  Voice: 'Voice recognition',
  fallback: 'Alternative authentication',
};

export interface BiometricTypeIconProps {
  biometryType?: BiometryType | 'fallback' | null;
  type?: BiometryType | 'fallback' | 'unknown' | null;
  state?: IconState;
  size?: IconSize | number;
  color?: string;
  animated?: boolean;
  showLabel?: boolean;
  customLabel?: string;
  style?: ViewStyle;
  accessibilityLabel?: string;
  onStateChange?: (state: IconState) => void;
}

export const BiometricTypeIcon: React.FC<BiometricTypeIconProps> = ({
  biometryType,
  type,
  state = 'idle',
  size = 'medium',
  color,
  animated = false,
  showLabel = false,
  customLabel,
  style,
  accessibilityLabel,
  onStateChange,
}) => {
  const scaleValue = useRef(new Animated.Value(1)).current;
  const rotationValue = useRef(new Animated.Value(0)).current;
  const opacityValue = useRef(new Animated.Value(1)).current;
  const pulseValue = useRef(new Animated.Value(1)).current;

  const iconSize = typeof size === 'number' ? size : sizeMap[size];
  const iconColor = color || stateColors[state];
  const iconKey = type || biometryType || 'Biometrics';
  const icon = biometricIcons[iconKey as keyof typeof biometricIcons] || biometricIcons.Biometrics;

  // Get display label
  const getDisplayLabel = (): string => {
    if (customLabel) return customLabel;
    
    const typeToCheck = type || biometryType;
    switch (typeToCheck) {
      case 'TouchID':
        return 'Touch ID';
      case 'FaceID':
        return 'Face ID';
      case 'Biometrics':
        return 'Fingerprint';
      case 'Iris':
        return 'Iris Scan';
      case 'Voice':
        return 'Voice ID';
      case 'fallback':
        return 'PIN/Password';
      case 'unknown':
        return 'Unknown';
      default:
        return 'Biometric';
    }
  };

  // Animation effects based on state
  useEffect(() => {
    const animations: Animated.CompositeAnimation[] = [];

    switch (state) {
      case 'active':
        if (animated) {
          animations.push(
            Animated.spring(scaleValue, {
              toValue: 1.1,
              useNativeDriver: true,
            })
          );
        }
        break;

      case 'processing':
        if (animated) {
          // Pulse animation
          const pulseAnimation = Animated.loop(
            Animated.sequence([
              Animated.timing(pulseValue, {
                toValue: 1.2,
                duration: 800,
                useNativeDriver: true,
              }),
              Animated.timing(pulseValue, {
                toValue: 1,
                duration: 800,
                useNativeDriver: true,
              }),
            ])
          );

          // Rotation for non-face biometric types
          if (biometryType !== 'FaceID') {
            const rotationAnimation = Animated.loop(
              Animated.timing(rotationValue, {
                toValue: 1,
                duration: 2000,
                useNativeDriver: true,
              })
            );
            animations.push(rotationAnimation);
          }

          animations.push(pulseAnimation);
        }
        break;

      case 'success':
        if (animated) {
          animations.push(
            Animated.sequence([
              Animated.spring(scaleValue, {
                toValue: 1.3,
                useNativeDriver: true,
              }),
              Animated.spring(scaleValue, {
                toValue: 1,
                useNativeDriver: true,
              }),
            ])
          );
        }
        break;

      case 'error':
        if (animated) {
          // Shake animation
          animations.push(
            Animated.sequence([
              Animated.timing(scaleValue, {
                toValue: 1.1,
                duration: 100,
                useNativeDriver: true,
              }),
              Animated.timing(scaleValue, {
                toValue: 0.9,
                duration: 100,
                useNativeDriver: true,
              }),
              Animated.timing(scaleValue, {
                toValue: 1.1,
                duration: 100,
                useNativeDriver: true,
              }),
              Animated.timing(scaleValue, {
                toValue: 1,
                duration: 100,
                useNativeDriver: true,
              }),
            ])
          );
        }
        break;

      case 'disabled':
        animations.push(
          Animated.timing(opacityValue, {
            toValue: 0.5,
            duration: 200,
            useNativeDriver: true,
          })
        );
        break;

      case 'idle':
      default:
        animations.push(
          Animated.parallel([
            Animated.timing(scaleValue, {
              toValue: 1,
              duration: 200,
              useNativeDriver: true,
            }),
            Animated.timing(opacityValue, {
              toValue: 1,
              duration: 200,
              useNativeDriver: true,
            }),
            Animated.timing(pulseValue, {
              toValue: 1,
              duration: 200,
              useNativeDriver: true,
            }),
          ])
        );
        break;
    }

    if (animations.length > 0) {
      const compositeAnimation = animations.length === 1 
        ? animations[0] 
        : Animated.parallel(animations);
      
      compositeAnimation.start();
    }

    // Notify state change
    if (onStateChange) {
      onStateChange(state);
    }

    // Cleanup function to stop animations
    return () => {
      animations.forEach(animation => animation.stop());
    };
  }, [state, animated, biometryType, scaleValue, rotationValue, opacityValue, pulseValue, onStateChange]);

  // Reset rotation value when animation stops
  useEffect(() => {
    if (state !== 'processing') {
      rotationValue.setValue(0);
    }
  }, [state, rotationValue]);

  // Rotation interpolation
  const rotation = rotationValue.interpolate({
    inputRange: [0, 1],
    outputRange: ['0deg', '360deg'],
  });

  // Prepare accessibility props
  const accessibilityProps = {
    accessible: true,
    accessibilityLabel: accessibilityLabel || iconDescriptions[iconKey as keyof typeof iconDescriptions] || 'Biometric authentication',
    accessibilityRole: 'image' as const,
    accessibilityState: {
      disabled: state === 'disabled',
      busy: state === 'processing',
    },
    accessibilityHint: `Current state: ${state}`,
  };

  return (
    <View style={[styles.container, style]} {...accessibilityProps}>
      <Animated.View
        style={[
          styles.iconContainer,
          {
            transform: [
              { scale: Animated.multiply(scaleValue, pulseValue) },
              { rotate: rotation },
            ],
            opacity: opacityValue,
          },
        ]}
      >
        <Text
          style={[
            styles.icon,
            {
              fontSize: iconSize,
              color: iconColor,
            },
          ]}
        >
          {icon}
        </Text>
      </Animated.View>

      {showLabel && (
        <Text
          style={[
            styles.label,
            {
              color: iconColor,
              opacity: state === 'disabled' ? 0.5 : 1,
            },
          ]}
        >
          {getDisplayLabel()}
        </Text>
      )}

      {/* State indicator */}
      {state !== 'idle' && (
        <View
          style={[
            styles.stateIndicator,
            {
              backgroundColor: stateColors[state],
            },
          ]}
        />
      )}
    </View>
  );
};

// Specialized components for common use cases
export const FingerprintIcon: React.FC<Omit<BiometricTypeIconProps, 'biometryType'>> = (props) => (
  <BiometricTypeIcon {...props} biometryType="TouchID" />
);

export const FaceIDIcon: React.FC<Omit<BiometricTypeIconProps, 'biometryType'>> = (props) => (
  <BiometricTypeIcon {...props} biometryType="FaceID" />
);

export const BiometricsIcon: React.FC<Omit<BiometricTypeIconProps, 'biometryType'>> = (props) => (
  <BiometricTypeIcon {...props} biometryType="Biometrics" />
);

export const FallbackIcon: React.FC<Omit<BiometricTypeIconProps, 'biometryType'>> = (props) => (
  <BiometricTypeIcon {...props} biometryType="fallback" />
);

// Helper component for displaying multiple biometric options
export interface BiometricOptionsProps {
  availableTypes: BiometryType[];
  selectedType?: BiometryType | null;
  onTypeSelect?: (type: BiometryType) => void;
  size?: IconSize;
  animated?: boolean;
  style?: ViewStyle;
}

export const BiometricOptions: React.FC<BiometricOptionsProps> = ({
  availableTypes,
  selectedType,
  onTypeSelect,
  size = 'medium',
  animated = true,
  style,
}) => {
  return (
    <View style={[styles.optionsContainer, style]}>
      {availableTypes.map((type) => (
        <BiometricTypeIcon
          key={type}
          biometryType={type}
          size={size}
          state={selectedType === type ? 'active' : 'idle'}
          animated={animated}
          showLabel
          style={styles.optionItem}
          onStateChange={(state) => {
            if (state === 'active' && onTypeSelect) {
              onTypeSelect(type);
            }
          }}
        />
      ))}
    </View>
  );
};

const styles = StyleSheet.create({
  container: {
    alignItems: 'center',
    justifyContent: 'center',
    position: 'relative',
  },
  iconContainer: {
    alignItems: 'center',
    justifyContent: 'center',
  },
  icon: {
    textAlign: 'center',
  },
  label: {
    fontSize: 12,
    fontWeight: '500',
    marginTop: 4,
    textAlign: 'center',
  },
  stateIndicator: {
    position: 'absolute',
    top: -2,
    right: -2,
    width: 8,
    height: 8,
    borderRadius: 4,
    borderWidth: 1,
    borderColor: '#FFFFFF',
  },
  optionsContainer: {
    flexDirection: 'row',
    justifyContent: 'center',
    alignItems: 'center',
    gap: 16,
  },
  optionItem: {
    padding: 8,
  },
});

export default BiometricTypeIcon;