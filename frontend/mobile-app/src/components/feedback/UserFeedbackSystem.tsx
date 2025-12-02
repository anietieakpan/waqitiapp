/**
 * User Feedback System - Comprehensive feedback collection and display
 * Integrates with error handling, success notifications, and user guidance
 */

import React, { useState, useEffect, useRef } from 'react';
import {
  View,
  Text,
  StyleSheet,
  Animated,
  TouchableOpacity,
  ScrollView,
  KeyboardAvoidingView,
  Platform,
  Vibration,
  TextInput,
} from 'react-native';
import {
  Portal,
  Modal,
  Card,
  Title,
  Paragraph,
  Button,
  Snackbar,
  ActivityIndicator,
  ProgressBar,
  Chip,
  List,
  RadioButton,
  Checkbox,
  FAB,
} from 'react-native-paper';
import Icon from 'react-native-vector-icons/MaterialCommunityIcons';
import LinearGradient from 'react-native-linear-gradient';
import LottieView from 'lottie-react-native';

import ErrorHandlingService, { 
  AppError, 
  ErrorSeverity, 
  ErrorCategory 
} from '../../services/ErrorHandlingService';
import { useAppSelector } from '../../hooks/redux';

// Feedback types
export enum FeedbackType {
  SUCCESS = 'success',
  ERROR = 'error',
  WARNING = 'warning',
  INFO = 'info',
  PROGRESS = 'progress',
  CONFIRMATION = 'confirmation',
  TUTORIAL = 'tutorial',
}

// Feedback priority
export enum FeedbackPriority {
  LOW = 1,
  MEDIUM = 2,
  HIGH = 3,
  CRITICAL = 4,
}

// Feedback interface
export interface UserFeedback {
  id: string;
  type: FeedbackType;
  priority: FeedbackPriority;
  title?: string;
  message: string;
  details?: string;
  icon?: string;
  animation?: string;
  duration?: number;
  persistent?: boolean;
  dismissible?: boolean;
  actions?: FeedbackAction[];
  progress?: {
    value: number;
    total?: number;
    label?: string;
  };
  metadata?: Record<string, any>;
}

export interface FeedbackAction {
  label: string;
  onPress: () => void | Promise<void>;
  style?: 'primary' | 'secondary' | 'destructive';
  icon?: string;
}

interface UserFeedbackSystemProps {
  maxConcurrentFeedbacks?: number;
  defaultDuration?: number;
  enableHapticFeedback?: boolean;
  enableSoundFeedback?: boolean;
}

const UserFeedbackSystem: React.FC<UserFeedbackSystemProps> = ({
  maxConcurrentFeedbacks = 3,
  defaultDuration = 4000,
  enableHapticFeedback = true,
  enableSoundFeedback = false,
}) => {
  // State
  const [feedbackQueue, setFeedbackQueue] = useState<UserFeedback[]>([]);
  const [activeFeedbacks, setActiveFeedbacks] = useState<UserFeedback[]>([]);
  const [expandedFeedback, setExpandedFeedback] = useState<string | null>(null);
  const [userResponse, setUserResponse] = useState<string>('');
  const [selectedRating, setSelectedRating] = useState<number>(0);
  
  // Animations
  const slideAnim = useRef(new Animated.Value(-100)).current;
  const fadeAnim = useRef(new Animated.Value(0)).current;
  const scaleAnim = useRef(new Animated.Value(0.9)).current;
  const pulseAnim = useRef(new Animated.Value(1)).current;
  
  // Error listener
  useEffect(() => {
    const unsubscribe = ErrorHandlingService.addErrorListener(handleError);
    return unsubscribe;
  }, []);

  // Process feedback queue
  useEffect(() => {
    processQueue();
  }, [feedbackQueue, activeFeedbacks]);

  // Start pulse animation for critical feedbacks
  useEffect(() => {
    const hasCritical = activeFeedbacks.some(f => f.priority === FeedbackPriority.CRITICAL);
    
    if (hasCritical) {
      Animated.loop(
        Animated.sequence([
          Animated.timing(pulseAnim, {
            toValue: 1.1,
            duration: 500,
            useNativeDriver: true,
          }),
          Animated.timing(pulseAnim, {
            toValue: 1,
            duration: 500,
            useNativeDriver: true,
          }),
        ])
      ).start();
    } else {
      pulseAnim.setValue(1);
    }
  }, [activeFeedbacks]);

  /**
   * Handle error from ErrorHandlingService
   */
  const handleError = (error: AppError) => {
    const feedback: UserFeedback = {
      id: error.id,
      type: mapErrorSeverityToFeedbackType(error.severity),
      priority: mapErrorSeverityToPriority(error.severity),
      title: 'Error',
      message: error.userMessage || error.message,
      details: error.suggestions?.join('\n'),
      icon: getIconForErrorCategory(error.category),
      persistent: error.severity === ErrorSeverity.CRITICAL,
      dismissible: error.severity !== ErrorSeverity.CRITICAL,
      actions: error.actions?.map(action => ({
        label: action.label,
        onPress: action.action,
        style: action.type === 'destructive' ? 'destructive' : 
               action.type === 'primary' ? 'primary' : 'secondary',
      })),
    };

    showFeedback(feedback);
  };

  /**
   * Show feedback
   */
  const showFeedback = (feedback: UserFeedback) => {
    // Apply defaults
    const completeFeedback: UserFeedback = {
      ...feedback,
      id: feedback.id || generateId(),
      duration: feedback.duration || (feedback.persistent ? undefined : defaultDuration),
      dismissible: feedback.dismissible !== false,
    };

    // Haptic feedback
    if (enableHapticFeedback) {
      triggerHapticFeedback(completeFeedback.type);
    }

    // Add to queue or active based on priority
    if (completeFeedback.priority >= FeedbackPriority.HIGH || 
        activeFeedbacks.length < maxConcurrentFeedbacks) {
      addToActive(completeFeedback);
    } else {
      setFeedbackQueue(prev => [...prev, completeFeedback]);
    }
  };

  /**
   * Add feedback to active list
   */
  const addToActive = (feedback: UserFeedback) => {
    setActiveFeedbacks(prev => {
      // Remove lower priority feedbacks if needed
      if (prev.length >= maxConcurrentFeedbacks) {
        const sorted = [...prev].sort((a, b) => a.priority - b.priority);
        const toRemove = sorted[0];
        
        if (toRemove.priority < feedback.priority) {
          // Move to queue
          setFeedbackQueue(queue => [toRemove, ...queue]);
          return [...prev.filter(f => f.id !== toRemove.id), feedback];
        } else {
          // Add to queue instead
          setFeedbackQueue(queue => [...queue, feedback]);
          return prev;
        }
      }
      
      return [...prev, feedback];
    });

    // Animate entrance
    animateIn();

    // Auto-dismiss if not persistent
    if (!feedback.persistent && feedback.duration) {
      setTimeout(() => {
        dismissFeedback(feedback.id);
      }, feedback.duration);
    }
  };

  /**
   * Process feedback queue
   */
  const processQueue = () => {
    if (feedbackQueue.length > 0 && activeFeedbacks.length < maxConcurrentFeedbacks) {
      const next = feedbackQueue[0];
      setFeedbackQueue(prev => prev.slice(1));
      addToActive(next);
    }
  };

  /**
   * Dismiss feedback
   */
  const dismissFeedback = (id: string) => {
    animateOut(() => {
      setActiveFeedbacks(prev => prev.filter(f => f.id !== id));
    });
  };

  /**
   * Animate in
   */
  const animateIn = () => {
    Animated.parallel([
      Animated.spring(slideAnim, {
        toValue: 0,
        tension: 40,
        friction: 8,
        useNativeDriver: true,
      }),
      Animated.timing(fadeAnim, {
        toValue: 1,
        duration: 300,
        useNativeDriver: true,
      }),
      Animated.spring(scaleAnim, {
        toValue: 1,
        tension: 40,
        friction: 8,
        useNativeDriver: true,
      }),
    ]).start();
  };

  /**
   * Animate out
   */
  const animateOut = (callback?: () => void) => {
    Animated.parallel([
      Animated.timing(slideAnim, {
        toValue: -100,
        duration: 200,
        useNativeDriver: true,
      }),
      Animated.timing(fadeAnim, {
        toValue: 0,
        duration: 200,
        useNativeDriver: true,
      }),
      Animated.timing(scaleAnim, {
        toValue: 0.9,
        duration: 200,
        useNativeDriver: true,
      }),
    ]).start(callback);
  };

  /**
   * Trigger haptic feedback
   */
  const triggerHapticFeedback = (type: FeedbackType) => {
    if (!enableHapticFeedback) return;

    switch (type) {
      case FeedbackType.SUCCESS:
        Vibration.vibrate(100);
        break;
      case FeedbackType.ERROR:
        Vibration.vibrate([0, 100, 100, 100]);
        break;
      case FeedbackType.WARNING:
        Vibration.vibrate([0, 50, 50, 50]);
        break;
      case FeedbackType.INFO:
        Vibration.vibrate(50);
        break;
    }
  };

  /**
   * Get feedback colors
   */
  const getFeedbackColors = (type: FeedbackType): string[] => {
    switch (type) {
      case FeedbackType.SUCCESS:
        return ['#4CAF50', '#45a049'];
      case FeedbackType.ERROR:
        return ['#F44336', '#da190b'];
      case FeedbackType.WARNING:
        return ['#FF9800', '#FB8C00'];
      case FeedbackType.INFO:
        return ['#2196F3', '#1976D2'];
      case FeedbackType.PROGRESS:
        return ['#9C27B0', '#7B1FA2'];
      case FeedbackType.CONFIRMATION:
        return ['#00BCD4', '#0097A7'];
      case FeedbackType.TUTORIAL:
        return ['#FFC107', '#FFA000'];
      default:
        return ['#757575', '#616161'];
    }
  };

  /**
   * Get feedback icon
   */
  const getFeedbackIcon = (feedback: UserFeedback): string => {
    if (feedback.icon) return feedback.icon;

    switch (feedback.type) {
      case FeedbackType.SUCCESS:
        return 'check-circle';
      case FeedbackType.ERROR:
        return 'alert-circle';
      case FeedbackType.WARNING:
        return 'alert';
      case FeedbackType.INFO:
        return 'information';
      case FeedbackType.PROGRESS:
        return 'progress-clock';
      case FeedbackType.CONFIRMATION:
        return 'help-circle';
      case FeedbackType.TUTORIAL:
        return 'school';
      default:
        return 'information';
    }
  };

  /**
   * Render feedback item
   */
  const renderFeedbackItem = (feedback: UserFeedback) => {
    const isExpanded = expandedFeedback === feedback.id;
    const colors = getFeedbackColors(feedback.type);
    const icon = getFeedbackIcon(feedback);

    return (
      <Animated.View
        key={feedback.id}
        style={[
          styles.feedbackContainer,
          {
            opacity: fadeAnim,
            transform: [
              { translateY: slideAnim },
              { scale: feedback.priority === FeedbackPriority.CRITICAL ? pulseAnim : scaleAnim },
            ],
          },
        ]}
      >
        <TouchableOpacity
          activeOpacity={0.9}
          onPress={() => setExpandedFeedback(isExpanded ? null : feedback.id)}
        >
          <LinearGradient
            colors={colors}
            style={styles.feedbackGradient}
            start={{ x: 0, y: 0 }}
            end={{ x: 1, y: 0 }}
          >
            <View style={styles.feedbackContent}>
              <View style={styles.feedbackHeader}>
                <Icon name={icon} size={24} color="white" />
                
                <View style={styles.feedbackText}>
                  {feedback.title && (
                    <Text style={styles.feedbackTitle}>{feedback.title}</Text>
                  )}
                  <Text style={styles.feedbackMessage}>{feedback.message}</Text>
                </View>

                {feedback.dismissible && (
                  <TouchableOpacity
                    onPress={() => dismissFeedback(feedback.id)}
                    style={styles.dismissButton}
                  >
                    <Icon name="close" size={20} color="white" />
                  </TouchableOpacity>
                )}
              </View>

              {feedback.progress && (
                <View style={styles.progressContainer}>
                  <ProgressBar
                    progress={feedback.progress.value / (feedback.progress.total || 100)}
                    color="white"
                    style={styles.progressBar}
                  />
                  {feedback.progress.label && (
                    <Text style={styles.progressLabel}>{feedback.progress.label}</Text>
                  )}
                </View>
              )}

              {isExpanded && feedback.details && (
                <View style={styles.detailsContainer}>
                  <Text style={styles.detailsText}>{feedback.details}</Text>
                </View>
              )}

              {feedback.actions && feedback.actions.length > 0 && (
                <View style={styles.actionsContainer}>
                  {feedback.actions.map((action, index) => (
                    <TouchableOpacity
                      key={index}
                      onPress={action.onPress}
                      style={[
                        styles.actionButton,
                        action.style === 'primary' && styles.primaryAction,
                        action.style === 'destructive' && styles.destructiveAction,
                      ]}
                    >
                      {action.icon && (
                        <Icon name={action.icon} size={16} color="white" style={styles.actionIcon} />
                      )}
                      <Text style={styles.actionText}>{action.label}</Text>
                    </TouchableOpacity>
                  ))}
                </View>
              )}
            </View>
          </LinearGradient>
        </TouchableOpacity>
      </Animated.View>
    );
  };

  /**
   * Render feedback rating
   */
  const renderFeedbackRating = () => (
    <View style={styles.ratingContainer}>
      <Text style={styles.ratingTitle}>How was your experience?</Text>
      <View style={styles.ratingStars}>
        {[1, 2, 3, 4, 5].map((star) => (
          <TouchableOpacity
            key={star}
            onPress={() => setSelectedRating(star)}
          >
            <Icon
              name={star <= selectedRating ? 'star' : 'star-outline'}
              size={32}
              color={star <= selectedRating ? '#FFC107' : '#CCC'}
            />
          </TouchableOpacity>
        ))}
      </View>
      {selectedRating > 0 && (
        <TextInput
          style={styles.feedbackInput}
          placeholder="Tell us more (optional)"
          value={userResponse}
          onChangeText={setUserResponse}
          multiline
          numberOfLines={3}
        />
      )}
    </View>
  );

  // Helper functions
  const mapErrorSeverityToFeedbackType = (severity: ErrorSeverity): FeedbackType => {
    switch (severity) {
      case ErrorSeverity.LOW:
        return FeedbackType.INFO;
      case ErrorSeverity.MEDIUM:
        return FeedbackType.WARNING;
      case ErrorSeverity.HIGH:
      case ErrorSeverity.CRITICAL:
        return FeedbackType.ERROR;
      default:
        return FeedbackType.INFO;
    }
  };

  const mapErrorSeverityToPriority = (severity: ErrorSeverity): FeedbackPriority => {
    switch (severity) {
      case ErrorSeverity.LOW:
        return FeedbackPriority.LOW;
      case ErrorSeverity.MEDIUM:
        return FeedbackPriority.MEDIUM;
      case ErrorSeverity.HIGH:
        return FeedbackPriority.HIGH;
      case ErrorSeverity.CRITICAL:
        return FeedbackPriority.CRITICAL;
      default:
        return FeedbackPriority.MEDIUM;
    }
  };

  const getIconForErrorCategory = (category: ErrorCategory): string => {
    switch (category) {
      case ErrorCategory.NETWORK:
        return 'wifi-off';
      case ErrorCategory.AUTHENTICATION:
        return 'lock-alert';
      case ErrorCategory.PAYMENT:
        return 'credit-card-off';
      case ErrorCategory.VALIDATION:
        return 'alert-circle-outline';
      case ErrorCategory.BIOMETRIC:
        return 'fingerprint-off';
      case ErrorCategory.OCR:
        return 'image-off';
      case ErrorCategory.PERMISSION:
        return 'shield-alert';
      case ErrorCategory.STORAGE:
        return 'database-alert';
      case ErrorCategory.SYSTEM:
        return 'alert-octagon';
      default:
        return 'alert-circle';
    }
  };

  const generateId = (): string => {
    return `feedback_${Date.now()}_${Math.random().toString(36).substr(2, 9)}`;
  };

  // Public API through context or ref
  React.useImperativeHandle(ref, () => ({
    showSuccess: (message: string, title?: string, actions?: FeedbackAction[]) => {
      showFeedback({
        id: generateId(),
        type: FeedbackType.SUCCESS,
        priority: FeedbackPriority.MEDIUM,
        title,
        message,
        actions,
      });
    },
    showError: (message: string, title?: string, actions?: FeedbackAction[]) => {
      showFeedback({
        id: generateId(),
        type: FeedbackType.ERROR,
        priority: FeedbackPriority.HIGH,
        title,
        message,
        actions,
      });
    },
    showWarning: (message: string, title?: string, actions?: FeedbackAction[]) => {
      showFeedback({
        id: generateId(),
        type: FeedbackType.WARNING,
        priority: FeedbackPriority.MEDIUM,
        title,
        message,
        actions,
      });
    },
    showInfo: (message: string, title?: string, actions?: FeedbackAction[]) => {
      showFeedback({
        id: generateId(),
        type: FeedbackType.INFO,
        priority: FeedbackPriority.LOW,
        title,
        message,
        actions,
      });
    },
    showProgress: (message: string, progress: number, total?: number) => {
      showFeedback({
        id: generateId(),
        type: FeedbackType.PROGRESS,
        priority: FeedbackPriority.MEDIUM,
        message,
        progress: { value: progress, total },
        persistent: true,
      });
    },
    dismissAll: () => {
      setActiveFeedbacks([]);
      setFeedbackQueue([]);
    },
  }));

  return (
    <Portal>
      <View style={styles.container} pointerEvents="box-none">
        {activeFeedbacks.map(renderFeedbackItem)}
      </View>
    </Portal>
  );
};

const styles = StyleSheet.create({
  container: {
    position: 'absolute',
    top: 100,
    left: 0,
    right: 0,
    zIndex: 9999,
  },
  feedbackContainer: {
    marginHorizontal: 16,
    marginVertical: 8,
  },
  feedbackGradient: {
    borderRadius: 12,
    elevation: 6,
    shadowColor: '#000',
    shadowOffset: { width: 0, height: 3 },
    shadowOpacity: 0.3,
    shadowRadius: 4,
  },
  feedbackContent: {
    padding: 16,
  },
  feedbackHeader: {
    flexDirection: 'row',
    alignItems: 'flex-start',
  },
  feedbackText: {
    flex: 1,
    marginLeft: 12,
  },
  feedbackTitle: {
    fontSize: 16,
    fontWeight: 'bold',
    color: 'white',
    marginBottom: 4,
  },
  feedbackMessage: {
    fontSize: 14,
    color: 'white',
    lineHeight: 20,
  },
  dismissButton: {
    padding: 4,
  },
  progressContainer: {
    marginTop: 12,
  },
  progressBar: {
    height: 4,
    borderRadius: 2,
    backgroundColor: 'rgba(255, 255, 255, 0.3)',
  },
  progressLabel: {
    fontSize: 12,
    color: 'white',
    marginTop: 4,
  },
  detailsContainer: {
    marginTop: 12,
    padding: 12,
    backgroundColor: 'rgba(0, 0, 0, 0.1)',
    borderRadius: 8,
  },
  detailsText: {
    fontSize: 12,
    color: 'white',
    lineHeight: 18,
  },
  actionsContainer: {
    flexDirection: 'row',
    marginTop: 12,
    gap: 8,
  },
  actionButton: {
    flexDirection: 'row',
    alignItems: 'center',
    paddingHorizontal: 12,
    paddingVertical: 6,
    backgroundColor: 'rgba(255, 255, 255, 0.2)',
    borderRadius: 16,
  },
  primaryAction: {
    backgroundColor: 'rgba(255, 255, 255, 0.3)',
  },
  destructiveAction: {
    backgroundColor: 'rgba(255, 0, 0, 0.3)',
  },
  actionIcon: {
    marginRight: 4,
  },
  actionText: {
    fontSize: 12,
    color: 'white',
    fontWeight: '600',
  },
  ratingContainer: {
    padding: 16,
  },
  ratingTitle: {
    fontSize: 16,
    fontWeight: 'bold',
    marginBottom: 12,
    textAlign: 'center',
  },
  ratingStars: {
    flexDirection: 'row',
    justifyContent: 'center',
    marginBottom: 16,
  },
  feedbackInput: {
    borderWidth: 1,
    borderColor: '#DDD',
    borderRadius: 8,
    padding: 12,
    minHeight: 80,
    textAlignVertical: 'top',
  },
});

// Create ref for imperative API
const UserFeedbackSystemWithRef = React.forwardRef<any, UserFeedbackSystemProps>(
  (props, ref) => <UserFeedbackSystem {...props} ref={ref} />
);

export default UserFeedbackSystemWithRef;