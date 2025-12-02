import React, { useEffect, useRef } from 'react';
import {
  View,
  Text,
  StyleSheet,
  TouchableOpacity,
  Animated,
  Share,
} from 'react-native';
import { useNavigation, useRoute } from '@react-navigation/native';
import Icon from 'react-native-vector-icons/MaterialCommunityIcons';
import LottieView from 'lottie-react-native';
import { AnalyticsService } from '../services/AnalyticsService';

/**
 * PaymentSuccessScreen
 *
 * Success confirmation screen shown after successful payment
 *
 * Features:
 * - Success animation
 * - Transaction details
 * - Share receipt
 * - Quick actions (new payment, view transaction)
 * - Analytics tracking
 *
 * @author Waqiti Engineering Team
 * @version 1.0.0
 */
const PaymentSuccessScreen: React.FC = () => {
  const navigation = useNavigation();
  const route = useRoute();

  // Get transaction data from params
  const {
    transactionId,
    amount,
    recipient,
    timestamp = new Date().toISOString(),
  } = (route.params || {}) as any;

  const fadeAnim = useRef(new Animated.Value(0)).current;
  const scaleAnim = useRef(new Animated.Value(0)).current;

  useEffect(() => {
    AnalyticsService.trackScreenView('PaymentSuccessScreen', {
      transactionId,
      amount,
    });

    // Trigger success animation
    Animated.parallel([
      Animated.timing(fadeAnim, {
        toValue: 1,
        duration: 500,
        useNativeDriver: true,
      }),
      Animated.spring(scaleAnim, {
        toValue: 1,
        friction: 8,
        tension: 40,
        useNativeDriver: true,
      }),
    ]).start();

    // Track successful payment
    AnalyticsService.trackEvent('payment_completed', {
      transactionId,
      amount,
      recipient,
    });
  }, []);

  const formatCurrency = (value: number): string => {
    return new Intl.NumberFormat('en-US', {
      style: 'currency',
      currency: 'USD',
    }).format(value);
  };

  const formatTimestamp = (isoString: string): string => {
    const date = new Date(isoString);
    return date.toLocaleString('en-US', {
      month: 'short',
      day: 'numeric',
      year: 'numeric',
      hour: '2-digit',
      minute: '2-digit',
    });
  };

  const handleShareReceipt = async () => {
    const receiptMessage = `Payment Receipt\n\nAmount: ${formatCurrency(amount)}\nTo: ${recipient}\nTransaction ID: ${transactionId}\nDate: ${formatTimestamp(timestamp)}\n\nPowered by Waqiti`;

    try {
      await Share.share({
        message: receiptMessage,
        title: 'Payment Receipt',
      });

      AnalyticsService.trackEvent('receipt_shared', {
        transactionId,
      });
    } catch (error) {
      console.error('Share failed:', error);
    }
  };

  const handleViewTransaction = () => {
    AnalyticsService.trackEvent('view_transaction_from_success', {
      transactionId,
    });

    navigation.navigate('TransactionDetails' as never, {
      transactionId,
    } as never);
  };

  const handleNewPayment = () => {
    AnalyticsService.trackEvent('new_payment_from_success');

    navigation.navigate('Home' as never);
  };

  const handleDone = () => {
    AnalyticsService.trackEvent('payment_success_done');

    // Navigate to home or activity screen
    navigation.navigate('Activity' as never);
  };

  return (
    <View style={styles.container}>
      <View style={styles.content}>
        {/* Success Animation */}
        <Animated.View
          style={[
            styles.animationContainer,
            {
              opacity: fadeAnim,
              transform: [{ scale: scaleAnim }],
            },
          ]}
        >
          <View style={styles.successIconContainer}>
            <Icon name="check-circle" size={100} color="#4CAF50" />
          </View>

          {/* Or use Lottie animation */}
          {/* <LottieView
            source={require('../assets/animations/success.json')}
            autoPlay
            loop={false}
            style={styles.lottieAnimation}
          /> */}
        </Animated.View>

        {/* Success Message */}
        <Animated.View style={[styles.messageContainer, { opacity: fadeAnim }]}>
          <Text style={styles.successTitle}>Payment Successful!</Text>
          <Text style={styles.successSubtitle}>
            Your payment has been sent successfully
          </Text>
        </Animated.View>

        {/* Transaction Details Card */}
        <Animated.View
          style={[
            styles.detailsCard,
            {
              opacity: fadeAnim,
              transform: [{ translateY: Animated.multiply(fadeAnim, -20) }],
            },
          ]}
        >
          <View style={styles.detailRow}>
            <Text style={styles.detailLabel}>Amount</Text>
            <Text style={styles.detailValue}>{formatCurrency(amount)}</Text>
          </View>

          <View style={styles.divider} />

          <View style={styles.detailRow}>
            <Text style={styles.detailLabel}>To</Text>
            <Text style={styles.detailValue}>{recipient}</Text>
          </View>

          <View style={styles.divider} />

          <View style={styles.detailRow}>
            <Text style={styles.detailLabel}>Transaction ID</Text>
            <Text style={styles.detailValueSmall} numberOfLines={1}>
              {transactionId}
            </Text>
          </View>

          <View style={styles.divider} />

          <View style={styles.detailRow}>
            <Text style={styles.detailLabel}>Date & Time</Text>
            <Text style={styles.detailValue}>{formatTimestamp(timestamp)}</Text>
          </View>

          <View style={styles.statusContainer}>
            <View style={styles.statusBadge}>
              <Icon name="check-circle" size={16} color="#4CAF50" />
              <Text style={styles.statusText}>Completed</Text>
            </View>
          </View>
        </Animated.View>

        {/* Action Buttons */}
        <Animated.View style={[styles.actionsContainer, { opacity: fadeAnim }]}>
          <TouchableOpacity
            style={styles.actionButton}
            onPress={handleShareReceipt}
          >
            <Icon name="share-variant" size={24} color="#6200EE" />
            <Text style={styles.actionButtonText}>Share Receipt</Text>
          </TouchableOpacity>

          <TouchableOpacity
            style={styles.actionButton}
            onPress={handleViewTransaction}
          >
            <Icon name="file-document" size={24} color="#6200EE" />
            <Text style={styles.actionButtonText}>View Details</Text>
          </TouchableOpacity>
        </Animated.View>
      </View>

      {/* Bottom Actions */}
      <Animated.View style={[styles.bottomActions, { opacity: fadeAnim }]}>
        <TouchableOpacity
          style={styles.secondaryButton}
          onPress={handleNewPayment}
        >
          <Icon name="plus-circle" size={20} color="#6200EE" />
          <Text style={styles.secondaryButtonText}>New Payment</Text>
        </TouchableOpacity>

        <TouchableOpacity style={styles.primaryButton} onPress={handleDone}>
          <Text style={styles.primaryButtonText}>Done</Text>
        </TouchableOpacity>
      </Animated.View>
    </View>
  );
};

const styles = StyleSheet.create({
  container: {
    flex: 1,
    backgroundColor: '#FFFFFF',
  },
  content: {
    flex: 1,
    justifyContent: 'center',
    alignItems: 'center',
    paddingHorizontal: 24,
  },
  animationContainer: {
    marginBottom: 32,
  },
  successIconContainer: {
    alignItems: 'center',
  },
  lottieAnimation: {
    width: 200,
    height: 200,
  },
  messageContainer: {
    alignItems: 'center',
    marginBottom: 40,
  },
  successTitle: {
    fontSize: 28,
    fontWeight: 'bold',
    color: '#212121',
    marginBottom: 8,
  },
  successSubtitle: {
    fontSize: 16,
    color: '#666',
    textAlign: 'center',
  },
  detailsCard: {
    width: '100%',
    backgroundColor: '#F5F5F5',
    borderRadius: 16,
    padding: 20,
    marginBottom: 24,
  },
  detailRow: {
    flexDirection: 'row',
    justifyContent: 'space-between',
    alignItems: 'center',
    paddingVertical: 12,
  },
  detailLabel: {
    fontSize: 14,
    color: '#666',
    flex: 1,
  },
  detailValue: {
    fontSize: 16,
    fontWeight: '600',
    color: '#212121',
    textAlign: 'right',
    flex: 1,
  },
  detailValueSmall: {
    fontSize: 14,
    fontWeight: '600',
    color: '#212121',
    textAlign: 'right',
    flex: 1,
  },
  divider: {
    height: 1,
    backgroundColor: '#E0E0E0',
  },
  statusContainer: {
    alignItems: 'center',
    marginTop: 16,
    paddingTop: 16,
    borderTopWidth: 1,
    borderTopColor: '#E0E0E0',
  },
  statusBadge: {
    flexDirection: 'row',
    alignItems: 'center',
    backgroundColor: '#E8F5E9',
    paddingHorizontal: 16,
    paddingVertical: 8,
    borderRadius: 20,
  },
  statusText: {
    fontSize: 14,
    fontWeight: 'bold',
    color: '#4CAF50',
    marginLeft: 6,
  },
  actionsContainer: {
    flexDirection: 'row',
    width: '100%',
    justifyContent: 'space-around',
    marginBottom: 16,
  },
  actionButton: {
    alignItems: 'center',
    paddingVertical: 12,
    paddingHorizontal: 20,
  },
  actionButtonText: {
    fontSize: 13,
    fontWeight: '600',
    color: '#6200EE',
    marginTop: 6,
  },
  bottomActions: {
    flexDirection: 'row',
    paddingHorizontal: 24,
    paddingBottom: 32,
    paddingTop: 16,
    borderTopWidth: 1,
    borderTopColor: '#F5F5F5',
  },
  secondaryButton: {
    flexDirection: 'row',
    alignItems: 'center',
    justifyContent: 'center',
    flex: 1,
    paddingVertical: 16,
    borderRadius: 24,
    borderWidth: 2,
    borderColor: '#6200EE',
    marginRight: 8,
  },
  secondaryButtonText: {
    fontSize: 16,
    fontWeight: 'bold',
    color: '#6200EE',
    marginLeft: 8,
  },
  primaryButton: {
    flex: 1,
    alignItems: 'center',
    justifyContent: 'center',
    paddingVertical: 16,
    borderRadius: 24,
    backgroundColor: '#6200EE',
    marginLeft: 8,
  },
  primaryButtonText: {
    fontSize: 16,
    fontWeight: 'bold',
    color: '#FFFFFF',
  },
});

export default PaymentSuccessScreen;
