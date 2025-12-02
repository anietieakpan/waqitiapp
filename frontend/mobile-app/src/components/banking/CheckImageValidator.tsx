/**
 * CheckImageValidator - Real-time image quality validation component
 * Provides real-time feedback on check image quality and positioning
 */

import React, { useEffect, useState, useRef, useCallback } from 'react';
import {
  View,
  Text,
  StyleSheet,
  Dimensions,
  Animated,
} from 'react-native';
import { useTheme } from 'react-native-paper';
// Removed react-native-camera dependency
import Icon from 'react-native-vector-icons/MaterialCommunityIcons';

interface ValidationResult {
  isValid: boolean;
  issues: ValidationIssue[];
  quality: 'poor' | 'fair' | 'good' | 'excellent';
  confidence: number;
}

interface ValidationIssue {
  type: 'lighting' | 'focus' | 'positioning' | 'orientation' | 'occlusion' | 'glare';
  severity: 'low' | 'medium' | 'high';
  message: string;
}

interface CheckImageValidatorProps {
  cameraRef?: React.RefObject<any>;
  side: 'front' | 'back';
  enabled?: boolean;
  onValidationChange?: (result: ValidationResult) => void;
}

const { width: screenWidth, height: screenHeight } = Dimensions.get('window');

/**
 * Check Image Validator Component
 */
const CheckImageValidator: React.FC<CheckImageValidatorProps> = ({
  cameraRef,
  side,
  enabled = true,
  onValidationChange,
}) => {
  const theme = useTheme();
  
  // State
  const [validationResult, setValidationResult] = useState<ValidationResult>({
    isValid: false,
    issues: [],
    quality: 'poor',
    confidence: 0,
  });
  const [isAnalyzing, setIsAnalyzing] = useState(false);
  
  // Animation refs
  const fadeAnim = useRef(new Animated.Value(0)).current;
  const pulseAnim = useRef(new Animated.Value(1)).current;
  
  // Validation timer ref
  const validationTimer = useRef<NodeJS.Timeout>();
  
  // Start pulse animation for analyzing state
  const startPulseAnimation = useCallback(() => {
    Animated.loop(
      Animated.sequence([
        Animated.timing(pulseAnim, {
          toValue: 1.2,
          duration: 800,
          useNativeDriver: true,
        }),
        Animated.timing(pulseAnim, {
          toValue: 1,
          duration: 800,
          useNativeDriver: true,
        }),
      ])
    ).start();
  }, [pulseAnim]);
  
  // Stop pulse animation
  const stopPulseAnimation = useCallback(() => {
    pulseAnim.stopAnimation();
    pulseAnim.setValue(1);
  }, [pulseAnim]);
  
  // Simulate image analysis (in real implementation, this would use ML/CV)
  const analyzeImage = useCallback(async (): Promise<ValidationResult> => {
    // Simulate analysis delay
    await new Promise(resolve => setTimeout(resolve, 500));
    
    const issues: ValidationIssue[] = [];
    let quality: ValidationResult['quality'] = 'excellent';
    let confidence = 95;
    
    // Simulate various validation checks
    const checks = {
      lighting: Math.random() > 0.2, // 80% chance good lighting
      focus: Math.random() > 0.15, // 85% chance good focus
      positioning: Math.random() > 0.25, // 75% chance good positioning
      orientation: Math.random() > 0.1, // 90% chance correct orientation
      glare: Math.random() > 0.3, // 70% chance no glare
      occlusion: Math.random() > 0.05, // 95% chance no occlusion
    };
    
    // Check lighting
    if (!checks.lighting) {
      issues.push({
        type: 'lighting',
        severity: 'medium',
        message: 'Insufficient lighting. Try moving to a brighter area.',
      });
      quality = quality === 'excellent' ? 'good' : quality === 'good' ? 'fair' : 'poor';
      confidence -= 15;
    }
    
    // Check focus
    if (!checks.focus) {
      issues.push({
        type: 'focus',
        severity: 'high',
        message: 'Image appears blurry. Hold the camera steady.',
      });
      quality = quality === 'excellent' ? 'fair' : 'poor';
      confidence -= 25;
    }
    
    // Check positioning
    if (!checks.positioning) {
      issues.push({
        type: 'positioning',
        severity: 'medium',
        message: 'Check not properly positioned. Center it in the frame.',
      });
      quality = quality === 'excellent' ? 'good' : quality === 'good' ? 'fair' : 'poor';
      confidence -= 20;
    }
    
    // Check orientation
    if (!checks.orientation) {
      issues.push({
        type: 'orientation',
        severity: 'high',
        message: 'Check appears rotated. Align it horizontally.',
      });
      quality = 'poor';
      confidence -= 30;
    }
    
    // Check for glare
    if (!checks.glare) {
      issues.push({
        type: 'glare',
        severity: 'medium',
        message: 'Glare detected. Adjust angle to reduce reflection.',
      });
      quality = quality === 'excellent' ? 'good' : quality === 'good' ? 'fair' : 'poor';
      confidence -= 15;
    }
    
    // Check for occlusion
    if (!checks.occlusion) {
      issues.push({
        type: 'occlusion',
        severity: 'high',
        message: 'Part of the check is obscured. Ensure full visibility.',
      });
      quality = 'poor';
      confidence -= 35;
    }
    
    const isValid = issues.length === 0 || issues.every(issue => issue.severity === 'low');
    
    return {
      isValid,
      issues,
      quality,
      confidence: Math.max(0, Math.min(100, confidence)),
    };
  }, []);
  
  // Perform validation
  const performValidation = useCallback(async () => {
    if (!enabled) return;
    
    setIsAnalyzing(true);
    startPulseAnimation();
    
    try {
      const result = await analyzeImage();
      setValidationResult(result);
      onValidationChange?.(result);
    } catch (error) {
      console.error('Validation error:', error);
    } finally {
      setIsAnalyzing(false);
      stopPulseAnimation();
    }
  }, [enabled, cameraRef, analyzeImage, onValidationChange, startPulseAnimation, stopPulseAnimation]);
  
  // Start continuous validation
  useEffect(() => {
    if (!enabled) return;
    
    // Initial validation
    performValidation();
    
    // Set up continuous validation
    validationTimer.current = setInterval(() => {
      performValidation();
    }, 2000); // Validate every 2 seconds
    
    return () => {
      if (validationTimer.current) {
        clearInterval(validationTimer.current);
      }
    };
  }, [enabled, performValidation]);
  
  // Fade in animation
  useEffect(() => {
    Animated.timing(fadeAnim, {
      toValue: enabled ? 1 : 0,
      duration: 300,
      useNativeDriver: true,
    }).start();
  }, [enabled, fadeAnim]);
  
  // Get quality color
  const getQualityColor = () => {
    switch (validationResult.quality) {
      case 'excellent':
        return '#4CAF50';
      case 'good':
        return '#8BC34A';
      case 'fair':
        return '#FFC107';
      case 'poor':
        return '#F44336';
      default:
        return '#9E9E9E';
    }
  };
  
  // Get quality icon
  const getQualityIcon = () => {
    switch (validationResult.quality) {
      case 'excellent':
        return 'check-circle';
      case 'good':
        return 'check';
      case 'fair':
        return 'alert';
      case 'poor':
        return 'close-circle';
      default:
        return 'help-circle';
    }
  };
  
  // Get issue icon
  const getIssueIcon = (type: ValidationIssue['type']) => {
    switch (type) {
      case 'lighting':
        return 'brightness-6';
      case 'focus':
        return 'blur';
      case 'positioning':
        return 'crop';
      case 'orientation':
        return 'rotate-90-degrees-ccw';
      case 'glare':
        return 'weather-sunny';
      case 'occlusion':
        return 'eye-off';
      default:
        return 'alert';
    }
  };
  
  if (!enabled) return null;
  
  return (
    <Animated.View
      style={[
        styles.container,
        {
          opacity: fadeAnim,
        },
      ]}
    >
      {/* Quality Indicator */}
      <Animated.View
        style={[
          styles.qualityIndicator,
          {
            backgroundColor: getQualityColor(),
            transform: [{ scale: isAnalyzing ? pulseAnim : 1 }],
          },
        ]}
      >
        <Icon
          name={isAnalyzing ? 'loading' : getQualityIcon()}
          size={20}
          color="white"
        />
        <Text style={styles.qualityText}>
          {isAnalyzing ? 'Analyzing...' : validationResult.quality.toUpperCase()}
        </Text>
        <Text style={styles.confidenceText}>
          {validationResult.confidence}%
        </Text>
      </Animated.View>
      
      {/* Issues List */}
      {validationResult.issues.length > 0 && !isAnalyzing && (
        <View style={styles.issuesContainer}>
          {validationResult.issues.slice(0, 2).map((issue, index) => (
            <View
              key={index}
              style={[
                styles.issueItem,
                {
                  backgroundColor: issue.severity === 'high' 
                    ? 'rgba(244, 67, 54, 0.9)'
                    : issue.severity === 'medium'
                    ? 'rgba(255, 193, 7, 0.9)'
                    : 'rgba(96, 125, 139, 0.9)',
                },
              ]}
            >
              <Icon
                name={getIssueIcon(issue.type)}
                size={16}
                color="white"
              />
              <Text style={styles.issueText}>
                {issue.message}
              </Text>
            </View>
          ))}
          {validationResult.issues.length > 2 && (
            <View style={styles.moreIssuesItem}>
              <Text style={styles.moreIssuesText}>
                +{validationResult.issues.length - 2} more issues
              </Text>
            </View>
          )}
        </View>
      )}
      
      {/* Success State */}
      {validationResult.isValid && !isAnalyzing && (
        <View style={styles.successContainer}>
          <Icon name="check-circle" size={24} color="#4CAF50" />
          <Text style={styles.successText}>
            Perfect! Ready to capture
          </Text>
        </View>
      )}
      
      {/* Side-specific tips */}
      <View style={styles.tipsContainer}>
        <Text style={styles.tipsText}>
          {side === 'front' 
            ? 'Ensure signature, date, and amount are clearly visible'
            : 'Include your endorsement signature on the back'
          }
        </Text>
      </View>
    </Animated.View>
  );
};

const styles = StyleSheet.create({
  container: {
    position: 'absolute',
    top: 120,
    left: 16,
    right: 16,
    zIndex: 100,
  },
  qualityIndicator: {
    flexDirection: 'row',
    alignItems: 'center',
    justifyContent: 'center',
    paddingHorizontal: 16,
    paddingVertical: 8,
    borderRadius: 20,
    marginBottom: 8,
    alignSelf: 'center',
    minWidth: 160,
  },
  qualityText: {
    color: 'white',
    fontSize: 12,
    fontWeight: 'bold',
    marginLeft: 6,
    letterSpacing: 0.5,
  },
  confidenceText: {
    color: 'white',
    fontSize: 10,
    marginLeft: 8,
    opacity: 0.9,
  },
  issuesContainer: {
    marginBottom: 8,
  },
  issueItem: {
    flexDirection: 'row',
    alignItems: 'center',
    paddingHorizontal: 12,
    paddingVertical: 8,
    borderRadius: 8,
    marginBottom: 4,
  },
  issueText: {
    color: 'white',
    fontSize: 12,
    marginLeft: 8,
    flex: 1,
    lineHeight: 16,
  },
  moreIssuesItem: {
    alignItems: 'center',
    paddingVertical: 4,
  },
  moreIssuesText: {
    color: 'rgba(255, 255, 255, 0.8)',
    fontSize: 10,
    fontStyle: 'italic',
  },
  successContainer: {
    flexDirection: 'row',
    alignItems: 'center',
    justifyContent: 'center',
    backgroundColor: 'rgba(76, 175, 80, 0.9)',
    paddingHorizontal: 16,
    paddingVertical: 8,
    borderRadius: 20,
    marginBottom: 8,
    alignSelf: 'center',
  },
  successText: {
    color: 'white',
    fontSize: 12,
    fontWeight: '600',
    marginLeft: 6,
  },
  tipsContainer: {
    backgroundColor: 'rgba(0, 0, 0, 0.7)',
    paddingHorizontal: 12,
    paddingVertical: 6,
    borderRadius: 12,
    alignSelf: 'center',
    maxWidth: screenWidth - 32,
  },
  tipsText: {
    color: 'rgba(255, 255, 255, 0.9)',
    fontSize: 10,
    textAlign: 'center',
    lineHeight: 14,
  },
});

export default CheckImageValidator;