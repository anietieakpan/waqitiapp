import React, { useState, useRef } from 'react';
import {
  View,
  Text,
  StyleSheet,
  ScrollView,
  TouchableOpacity,
  Animated,
  Dimensions,
  Image,
  Platform,
} from 'react-native';
import { SafeAreaView } from 'react-native-safe-area-context';
import Icon from 'react-native-vector-icons/MaterialCommunityIcons';
import { useNavigation } from '@react-navigation/native';

const { width: SCREEN_WIDTH, height: SCREEN_HEIGHT } = Dimensions.get('window');

interface TutorialStep {
  id: string;
  title: string;
  description: string;
  icon: string;
  illustration?: string;
  tips?: string[];
  actionText?: string;
  actionIcon?: string;
}

const tutorialSteps: TutorialStep[] = [
  {
    id: 'welcome',
    title: 'Welcome to NFC Payments',
    description: 'Near Field Communication (NFC) lets you make secure, contactless payments and transfers by simply tapping your phone.',
    icon: 'nfc',
    tips: [
      'Fast and secure transactions',
      'Works with most modern smartphones',
      'No internet required for basic functions',
      'Enhanced security with encryption'
    ]
  },
  {
    id: 'setup',
    title: 'Setting Up NFC',
    description: 'First, let\'s make sure NFC is enabled on your device and configure your security preferences.',
    icon: 'cog',
    tips: [
      'Enable NFC in device settings',
      'Set up biometric authentication',
      'Configure payment limits',
      'Choose security level'
    ],
    actionText: 'Open NFC Settings',
    actionIcon: 'settings'
  },
  {
    id: 'merchant-payment',
    title: 'Paying Merchants',
    description: 'Tap your phone on the merchant\'s NFC reader to make a payment. You\'ll see the payment details before confirming.',
    icon: 'store',
    tips: [
      'Hold phone close to NFC reader',
      'Wait for confirmation vibration',
      'Review payment details carefully',
      'Complete with biometric or PIN'
    ],
    actionText: 'Try Demo Payment',
    actionIcon: 'play'
  },
  {
    id: 'p2p-transfer',
    title: 'Peer-to-Peer Transfers',
    description: 'Send money to friends by tapping your phones together. Both users need the Waqiti app installed.',
    icon: 'account-multiple',
    tips: [
      'Both phones need NFC enabled',
      'Tap the back of phones together',
      'Enter amount to send',
      'Both users confirm the transfer'
    ],
    actionText: 'Try Demo Transfer',
    actionIcon: 'play'
  },
  {
    id: 'contact-exchange',
    title: 'Contact Exchange',
    description: 'Quickly exchange contact information and connect with other Waqiti users for easy future payments.',
    icon: 'contacts',
    tips: [
      'Tap phones to share contacts',
      'Choose what info to share',
      'Enable payment permissions',
      'Build your network safely'
    ],
    actionText: 'Try Demo Exchange',
    actionIcon: 'play'
  },
  {
    id: 'security',
    title: 'Security Features',
    description: 'Your NFC payments are protected with multiple layers of security including encryption and biometric authentication.',
    icon: 'shield-check',
    tips: [
      'Hardware-backed encryption',
      'Biometric authentication required',
      'Secure element protection',
      'Fraud detection enabled'
    ]
  },
  {
    id: 'troubleshooting',
    title: 'Troubleshooting',
    description: 'Having issues? Here are common solutions to NFC payment problems.',
    icon: 'help-circle',
    tips: [
      'Ensure NFC is enabled in settings',
      'Remove phone case if thick',
      'Hold phones steady during tap',
      'Check for app updates'
    ],
    actionText: 'Get Help',
    actionIcon: 'support'
  }
];

const NFCTutorialScreen: React.FC = () => {
  const navigation = useNavigation();
  const [currentStep, setCurrentStep] = useState(0);
  const scrollViewRef = useRef<ScrollView>(null);
  const progressAnimation = useRef(new Animated.Value(0)).current;

  const nextStep = () => {
    if (currentStep < tutorialSteps.length - 1) {
      const newStep = currentStep + 1;
      setCurrentStep(newStep);
      
      // Animate progress
      Animated.timing(progressAnimation, {
        toValue: newStep / (tutorialSteps.length - 1),
        duration: 300,
        useNativeDriver: false,
      }).start();
      
      // Scroll to next step
      scrollViewRef.current?.scrollTo({
        x: newStep * SCREEN_WIDTH,
        animated: true,
      });
    } else {
      // Tutorial complete
      navigation.goBack();
    }
  };

  const previousStep = () => {
    if (currentStep > 0) {
      const newStep = currentStep - 1;
      setCurrentStep(newStep);
      
      // Animate progress
      Animated.timing(progressAnimation, {
        toValue: newStep / (tutorialSteps.length - 1),
        duration: 300,
        useNativeDriver: false,
      }).start();
      
      // Scroll to previous step
      scrollViewRef.current?.scrollTo({
        x: newStep * SCREEN_WIDTH,
        animated: true,
      });
    }
  };

  const skipTutorial = () => {
    navigation.goBack();
  };

  const handleStepAction = (step: TutorialStep) => {
    switch (step.id) {
      case 'setup':
        navigation.navigate('NFCSettings');
        break;
      case 'merchant-payment':
        navigation.navigate('NFCPayment', { mode: 'customer', demo: true });
        break;
      case 'p2p-transfer':
        navigation.navigate('NFCPayment', { mode: 'p2p', demo: true });
        break;
      case 'contact-exchange':
        navigation.navigate('NFCPayment', { mode: 'contact', demo: true });
        break;
      case 'troubleshooting':
        navigation.navigate('NFCHelp');
        break;
    }
  };

  const renderStep = (step: TutorialStep, index: number) => (
    <View key={step.id} style={styles.stepContainer}>
      {/* Step Icon */}
      <View style={styles.iconContainer}>
        <View style={styles.iconCircle}>
          <Icon name={step.icon} size={60} color="#007AFF" />
        </View>
      </View>

      {/* Step Content */}
      <View style={styles.contentContainer}>
        <Text style={styles.stepTitle}>{step.title}</Text>
        <Text style={styles.stepDescription}>{step.description}</Text>

        {/* Tips */}
        {step.tips && (
          <View style={styles.tipsContainer}>
            <Text style={styles.tipsTitle}>Key Points:</Text>
            {step.tips.map((tip, tipIndex) => (
              <View key={tipIndex} style={styles.tipItem}>
                <Icon name="check-circle" size={16} color="#34C759" />
                <Text style={styles.tipText}>{tip}</Text>
              </View>
            ))}
          </View>
        )}

        {/* Action Button */}
        {step.actionText && (
          <TouchableOpacity
            style={styles.actionButton}
            onPress={() => handleStepAction(step)}
          >
            {step.actionIcon && (
              <Icon name={step.actionIcon} size={20} color="#FFF" style={styles.actionIcon} />
            )}
            <Text style={styles.actionButtonText}>{step.actionText}</Text>
          </TouchableOpacity>
        )}
      </View>
    </View>
  );

  const renderDeviceSpecificTips = () => (
    <View style={styles.deviceTipsContainer}>
      <Text style={styles.deviceTipsTitle}>
        {Platform.OS === 'ios' ? 'iOS' : 'Android'} Specific Tips:
      </Text>
      {Platform.OS === 'ios' ? (
        <>
          <Text style={styles.deviceTipText}>
            • NFC is available on iPhone 7 and newer
          </Text>
          <Text style={styles.deviceTipText}>
            • Hold the top of your iPhone near the NFC reader
          </Text>
          <Text style={styles.deviceTipText}>
            • You may need to enable NFC in Control Center
          </Text>
        </>
      ) : (
        <>
          <Text style={styles.deviceTipText}>
            • Enable NFC in Settings → Connected devices → NFC
          </Text>
          <Text style={styles.deviceTipText}>
            • NFC antenna is usually on the back of the phone
          </Text>
          <Text style={styles.deviceTipText}>
            • Some phones have NFC quick settings tile
          </Text>
        </>
      )}
    </View>
  );

  return (
    <SafeAreaView style={styles.container}>
      {/* Header */}
      <View style={styles.header}>
        <TouchableOpacity onPress={skipTutorial} style={styles.skipButton}>
          <Text style={styles.skipText}>Skip</Text>
        </TouchableOpacity>
        <Text style={styles.headerTitle}>NFC Tutorial</Text>
        <TouchableOpacity onPress={() => navigation.goBack()} style={styles.closeButton}>
          <Icon name="close" size={24} color="#666" />
        </TouchableOpacity>
      </View>

      {/* Progress Bar */}
      <View style={styles.progressContainer}>
        <View style={styles.progressTrack}>
          <Animated.View
            style={[
              styles.progressBar,
              {
                width: progressAnimation.interpolate({
                  inputRange: [0, 1],
                  outputRange: ['0%', '100%'],
                }),
              },
            ]}
          />
        </View>
        <Text style={styles.progressText}>
          {currentStep + 1} of {tutorialSteps.length}
        </Text>
      </View>

      {/* Tutorial Steps */}
      <ScrollView
        ref={scrollViewRef}
        horizontal
        pagingEnabled
        showsHorizontalScrollIndicator={false}
        scrollEnabled={false}
        style={styles.stepsContainer}
      >
        {tutorialSteps.map((step, index) => renderStep(step, index))}
      </ScrollView>

      {/* Device-specific tips */}
      {currentStep === 1 && renderDeviceSpecificTips()}

      {/* Navigation */}
      <View style={styles.navigationContainer}>
        <TouchableOpacity
          style={[styles.navButton, currentStep === 0 && styles.navButtonDisabled]}
          onPress={previousStep}
          disabled={currentStep === 0}
        >
          <Icon 
            name="chevron-left" 
            size={24} 
            color={currentStep === 0 ? '#C7C7CC' : '#007AFF'} 
          />
          <Text style={[styles.navButtonText, currentStep === 0 && styles.navButtonTextDisabled]}>
            Previous
          </Text>
        </TouchableOpacity>

        <View style={styles.stepIndicator}>
          {tutorialSteps.map((_, index) => (
            <View
              key={index}
              style={[
                styles.stepDot,
                index === currentStep && styles.stepDotActive,
              ]}
            />
          ))}
        </View>

        <TouchableOpacity style={styles.navButton} onPress={nextStep}>
          <Text style={styles.navButtonText}>
            {currentStep === tutorialSteps.length - 1 ? 'Finish' : 'Next'}
          </Text>
          <Icon name="chevron-right" size={24} color="#007AFF" />
        </TouchableOpacity>
      </View>
    </SafeAreaView>
  );
};

const styles = StyleSheet.create({
  container: {
    flex: 1,
    backgroundColor: '#F8F9FA',
  },
  header: {
    flexDirection: 'row',
    alignItems: 'center',
    justifyContent: 'space-between',
    paddingHorizontal: 16,
    paddingVertical: 12,
    borderBottomWidth: 1,
    borderBottomColor: '#E5E5E7',
    backgroundColor: '#FFF',
  },
  skipButton: {
    padding: 8,
  },
  skipText: {
    color: '#007AFF',
    fontSize: 16,
    fontWeight: '500',
  },
  headerTitle: {
    fontSize: 18,
    fontWeight: '600',
    color: '#000',
  },
  closeButton: {
    padding: 8,
  },
  progressContainer: {
    flexDirection: 'row',
    alignItems: 'center',
    paddingHorizontal: 16,
    paddingVertical: 12,
    backgroundColor: '#FFF',
  },
  progressTrack: {
    flex: 1,
    height: 4,
    backgroundColor: '#E5E5E7',
    borderRadius: 2,
    marginRight: 12,
  },
  progressBar: {
    height: '100%',
    backgroundColor: '#007AFF',
    borderRadius: 2,
  },
  progressText: {
    fontSize: 14,
    color: '#666',
    fontWeight: '500',
  },
  stepsContainer: {
    flex: 1,
  },
  stepContainer: {
    width: SCREEN_WIDTH,
    flex: 1,
    paddingHorizontal: 20,
    paddingVertical: 20,
  },
  iconContainer: {
    alignItems: 'center',
    marginBottom: 30,
  },
  iconCircle: {
    width: 120,
    height: 120,
    borderRadius: 60,
    backgroundColor: '#F0F8FF',
    alignItems: 'center',
    justifyContent: 'center',
    borderWidth: 2,
    borderColor: '#007AFF',
  },
  contentContainer: {
    flex: 1,
  },
  stepTitle: {
    fontSize: 24,
    fontWeight: 'bold',
    color: '#000',
    textAlign: 'center',
    marginBottom: 16,
  },
  stepDescription: {
    fontSize: 16,
    color: '#666',
    textAlign: 'center',
    lineHeight: 24,
    marginBottom: 30,
  },
  tipsContainer: {
    backgroundColor: '#FFF',
    borderRadius: 12,
    padding: 16,
    marginBottom: 20,
    borderWidth: 1,
    borderColor: '#E5E5E7',
  },
  tipsTitle: {
    fontSize: 16,
    fontWeight: '600',
    color: '#000',
    marginBottom: 12,
  },
  tipItem: {
    flexDirection: 'row',
    alignItems: 'center',
    marginBottom: 8,
  },
  tipText: {
    fontSize: 14,
    color: '#333',
    marginLeft: 8,
    flex: 1,
  },
  actionButton: {
    flexDirection: 'row',
    alignItems: 'center',
    justifyContent: 'center',
    backgroundColor: '#007AFF',
    borderRadius: 12,
    paddingVertical: 12,
    paddingHorizontal: 24,
    alignSelf: 'center',
  },
  actionIcon: {
    marginRight: 8,
  },
  actionButtonText: {
    color: '#FFF',
    fontSize: 16,
    fontWeight: '600',
  },
  deviceTipsContainer: {
    backgroundColor: '#FFF',
    margin: 16,
    padding: 16,
    borderRadius: 12,
    borderLeftWidth: 4,
    borderLeftColor: '#007AFF',
  },
  deviceTipsTitle: {
    fontSize: 16,
    fontWeight: '600',
    color: '#000',
    marginBottom: 8,
  },
  deviceTipText: {
    fontSize: 14,
    color: '#666',
    marginBottom: 4,
  },
  navigationContainer: {
    flexDirection: 'row',
    alignItems: 'center',
    justifyContent: 'space-between',
    paddingHorizontal: 16,
    paddingVertical: 16,
    backgroundColor: '#FFF',
    borderTopWidth: 1,
    borderTopColor: '#E5E5E7',
  },
  navButton: {
    flexDirection: 'row',
    alignItems: 'center',
    paddingVertical: 8,
    paddingHorizontal: 12,
  },
  navButtonDisabled: {
    opacity: 0.3,
  },
  navButtonText: {
    fontSize: 16,
    fontWeight: '500',
    color: '#007AFF',
    marginHorizontal: 4,
  },
  navButtonTextDisabled: {
    color: '#C7C7CC',
  },
  stepIndicator: {
    flexDirection: 'row',
    alignItems: 'center',
  },
  stepDot: {
    width: 8,
    height: 8,
    borderRadius: 4,
    backgroundColor: '#C7C7CC',
    marginHorizontal: 4,
  },
  stepDotActive: {
    backgroundColor: '#007AFF',
    width: 12,
    height: 12,
    borderRadius: 6,
  },
});

export default NFCTutorialScreen;