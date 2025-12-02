import React, { useState, useRef } from 'react';
import {
  View,
  ScrollView,
  StyleSheet,
  Dimensions,
  Animated,
  TouchableOpacity,
} from 'react-native';
import {
  Text,
  Button,
  useTheme,
  Surface,
} from 'react-native-paper';
import { useNavigation } from '@react-navigation/native';
import LinearGradient from 'react-native-linear-gradient';
import Icon from 'react-native-vector-icons/MaterialCommunityIcons';
import { SafeAreaView } from 'react-native-safe-area-context';

const { width: screenWidth, height: screenHeight } = Dimensions.get('window');

interface OnboardingSlide {
  id: number;
  title: string;
  subtitle: string;
  description: string;
  icon: string;
  gradient: string[];
  features: string[];
}

const onboardingData: OnboardingSlide[] = [
  {
    id: 1,
    title: 'Welcome to Waqiti',
    subtitle: 'Your Smart Payment Solution',
    description: 'Send money instantly to friends, family, and businesses with just a few taps. Experience the future of payments.',
    icon: 'wallet',
    gradient: ['#667eea', '#764ba2'],
    features: ['Instant transfers', 'Secure transactions', 'Global reach']
  },
  {
    id: 2,
    title: 'Smart & Secure',
    subtitle: 'Bank-Level Security',
    description: 'Your money and data are protected with advanced encryption, biometric authentication, and fraud detection.',
    icon: 'shield-check',
    gradient: ['#f093fb', '#f5576c'],
    features: ['Biometric login', 'Encrypted data', 'Fraud protection']
  },
  {
    id: 3,
    title: 'Social Payments',
    subtitle: 'Pay & Share Experiences',
    description: 'Split bills, request money, and share payment experiences with your social network. Make payments social.',
    icon: 'account-group',
    gradient: ['#4facfe', '#00f2fe'],
    features: ['Split bills', 'Social feed', 'Group payments']
  },
  {
    id: 4,
    title: 'Business Tools',
    subtitle: 'Grow Your Business',
    description: 'Accept payments, create invoices, track analytics, and manage your business finances all in one place.',
    icon: 'briefcase',
    gradient: ['#43e97b', '#38f9d7'],
    features: ['Accept payments', 'Invoice creation', 'Business analytics']
  }
];

/**
 * Onboarding Screen - First-time user experience
 */
const OnboardingScreen: React.FC = () => {
  const theme = useTheme();
  const navigation = useNavigation();
  const scrollViewRef = useRef<ScrollView>(null);
  const [currentSlide, setCurrentSlide] = useState(0);
  const scrollX = useRef(new Animated.Value(0)).current;

  const handleScroll = Animated.event(
    [{ nativeEvent: { contentOffset: { x: scrollX } } }],
    {
      useNativeDriver: false,
      listener: (event: any) => {
        const slide = Math.round(event.nativeEvent.contentOffset.x / screenWidth);
        setCurrentSlide(slide);
      },
    }
  );

  const nextSlide = () => {
    if (currentSlide < onboardingData.length - 1) {
      const nextIndex = currentSlide + 1;
      scrollViewRef.current?.scrollTo({
        x: nextIndex * screenWidth,
        animated: true,
      });
      setCurrentSlide(nextIndex);
    } else {
      handleGetStarted();
    }
  };

  const prevSlide = () => {
    if (currentSlide > 0) {
      const prevIndex = currentSlide - 1;
      scrollViewRef.current?.scrollTo({
        x: prevIndex * screenWidth,
        animated: true,
      });
      setCurrentSlide(prevIndex);
    }
  };

  const goToSlide = (index: number) => {
    scrollViewRef.current?.scrollTo({
      x: index * screenWidth,
      animated: true,
    });
    setCurrentSlide(index);
  };

  const handleGetStarted = () => {
    navigation.navigate('Register' as never);
  };

  const handleSignIn = () => {
    navigation.navigate('Login' as never);
  };

  const renderSlide = (slide: OnboardingSlide) => (
    <View key={slide.id} style={styles.slide}>
      <LinearGradient
        colors={slide.gradient}
        style={styles.slideGradient}
        start={{ x: 0, y: 0 }}
        end={{ x: 1, y: 1 }}
      >
        <SafeAreaView style={styles.slideContainer}>
          <View style={styles.iconContainer}>
            <Surface style={styles.iconSurface} elevation={4}>
              <Icon name={slide.icon} size={80} color={theme.colors.primary} />
            </Surface>
          </View>

          <View style={styles.contentContainer}>
            <Text style={styles.title}>{slide.title}</Text>
            <Text style={styles.subtitle}>{slide.subtitle}</Text>
            <Text style={styles.description}>{slide.description}</Text>

            <View style={styles.featuresContainer}>
              {slide.features.map((feature, index) => (
                <View key={index} style={styles.featureItem}>
                  <Icon name="check-circle" size={20} color="#4CAF50" />
                  <Text style={styles.featureText}>{feature}</Text>
                </View>
              ))}
            </View>
          </View>
        </SafeAreaView>
      </LinearGradient>
    </View>
  );

  const renderPagination = () => (
    <View style={styles.pagination}>
      {onboardingData.map((_, index) => (
        <TouchableOpacity
          key={index}
          style={[
            styles.paginationDot,
            currentSlide === index && styles.paginationDotActive,
          ]}
          onPress={() => goToSlide(index)}
        />
      ))}
    </View>
  );

  return (
    <View style={styles.container}>
      <ScrollView
        ref={scrollViewRef}
        horizontal
        pagingEnabled
        showsHorizontalScrollIndicator={false}
        onScroll={handleScroll}
        scrollEventThrottle={16}
      >
        {onboardingData.map(renderSlide)}
      </ScrollView>

      {renderPagination()}

      <SafeAreaView style={styles.bottomContainer}>
        <View style={styles.navigationContainer}>
          {currentSlide > 0 && (
            <TouchableOpacity
              style={styles.navButton}
              onPress={prevSlide}
            >
              <Text style={styles.navButtonText}>Back</Text>
            </TouchableOpacity>
          )}

          <View style={styles.actionButtons}>
            {currentSlide === onboardingData.length - 1 ? (
              <>
                <Button
                  mode="outlined"
                  onPress={handleSignIn}
                  style={styles.signInButton}
                  labelStyle={styles.signInButtonText}
                >
                  Sign In
                </Button>
                <Button
                  mode="contained"
                  onPress={handleGetStarted}
                  style={styles.getStartedButton}
                >
                  Get Started
                </Button>
              </>
            ) : (
              <>
                <TouchableOpacity
                  style={styles.skipButton}
                  onPress={handleGetStarted}
                >
                  <Text style={styles.skipButtonText}>Skip</Text>
                </TouchableOpacity>
                <Button
                  mode="contained"
                  onPress={nextSlide}
                  style={styles.nextButton}
                >
                  Next
                </Button>
              </>
            )}
          </View>
        </View>
      </SafeAreaView>
    </View>
  );
};

const styles = StyleSheet.create({
  container: {
    flex: 1,
    backgroundColor: '#f5f5f5',
  },
  slide: {
    width: screenWidth,
    height: screenHeight,
  },
  slideGradient: {
    flex: 1,
  },
  slideContainer: {
    flex: 1,
    justifyContent: 'space-between',
    paddingHorizontal: 24,
    paddingTop: 60,
    paddingBottom: 120,
  },
  iconContainer: {
    alignItems: 'center',
    marginTop: 60,
  },
  iconSurface: {
    width: 160,
    height: 160,
    borderRadius: 80,
    justifyContent: 'center',
    alignItems: 'center',
    backgroundColor: 'rgba(255, 255, 255, 0.9)',
  },
  contentContainer: {
    alignItems: 'center',
    paddingHorizontal: 20,
  },
  title: {
    fontSize: 32,
    fontWeight: 'bold',
    color: 'white',
    textAlign: 'center',
    marginBottom: 8,
  },
  subtitle: {
    fontSize: 18,
    color: 'rgba(255, 255, 255, 0.9)',
    textAlign: 'center',
    marginBottom: 16,
  },
  description: {
    fontSize: 16,
    color: 'rgba(255, 255, 255, 0.8)',
    textAlign: 'center',
    lineHeight: 24,
    marginBottom: 32,
  },
  featuresContainer: {
    alignItems: 'flex-start',
  },
  featureItem: {
    flexDirection: 'row',
    alignItems: 'center',
    marginBottom: 12,
  },
  featureText: {
    color: 'white',
    fontSize: 16,
    marginLeft: 12,
  },
  pagination: {
    position: 'absolute',
    bottom: 180,
    width: '100%',
    flexDirection: 'row',
    justifyContent: 'center',
    alignItems: 'center',
  },
  paginationDot: {
    width: 12,
    height: 12,
    borderRadius: 6,
    backgroundColor: 'rgba(255, 255, 255, 0.3)',
    marginHorizontal: 6,
  },
  paginationDotActive: {
    backgroundColor: 'white',
    transform: [{ scale: 1.2 }],
  },
  bottomContainer: {
    position: 'absolute',
    bottom: 0,
    width: '100%',
    backgroundColor: 'white',
    paddingHorizontal: 24,
    paddingTop: 20,
    paddingBottom: 10,
    borderTopLeftRadius: 24,
    borderTopRightRadius: 24,
    elevation: 8,
  },
  navigationContainer: {
    flexDirection: 'row',
    justifyContent: 'space-between',
    alignItems: 'center',
  },
  navButton: {
    padding: 12,
  },
  navButtonText: {
    fontSize: 16,
    color: '#666',
  },
  actionButtons: {
    flexDirection: 'row',
    alignItems: 'center',
    gap: 12,
  },
  skipButton: {
    padding: 12,
  },
  skipButtonText: {
    fontSize: 16,
    color: '#666',
  },
  nextButton: {
    minWidth: 100,
  },
  signInButton: {
    borderColor: '#2196F3',
  },
  signInButtonText: {
    color: '#2196F3',
  },
  getStartedButton: {
    minWidth: 120,
  },
});

export default OnboardingScreen;