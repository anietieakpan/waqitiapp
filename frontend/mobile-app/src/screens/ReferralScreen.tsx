import React, { useState, useEffect } from 'react';
import {
  View,
  Text,
  StyleSheet,
  ScrollView,
  TouchableOpacity,
  Share,
  Alert,
  FlatList,
} from 'react-native';
import { useNavigation } from '@react-navigation/native';
import { useSelector } from 'react-redux';
import Icon from 'react-native-vector-icons/MaterialCommunityIcons';
import Clipboard from '@react-native-clipboard/clipboard';
import QRCode from 'react-native-qrcode-svg';
import { RootState } from '../store';
import Header from '../components/Header';
import { AnalyticsService } from '../services/AnalyticsService';

/**
 * ReferralScreen
 *
 * Screen for managing referral program
 *
 * Features:
 * - Referral code display
 * - QR code generation
 * - Share functionality
 * - Referral stats
 * - Referral history
 * - Rewards tracking
 *
 * @author Waqiti Engineering Team
 * @version 1.0.0
 */

interface Referral {
  id: string;
  name: string;
  email: string;
  status: 'pending' | 'completed' | 'rewarded';
  signupDate: string;
  rewardAmount: number;
  firstTransactionDate?: string;
}

const ReferralScreen: React.FC = () => {
  const navigation = useNavigation();
  const { user } = useSelector((state: RootState) => state.auth);

  // Mock data - TODO: Replace with Redux state
  const [referralCode, setReferralCode] = useState('WAQITI2025JOHN');
  const [totalReferrals, setTotalReferrals] = useState(12);
  const [completedReferrals, setCompletedReferrals] = useState(8);
  const [totalEarned, setTotalEarned] = useState(240); // $240 earned
  const [pendingRewards, setPendingRewards] = useState(80); // $80 pending

  const [referrals, setReferrals] = useState<Referral[]>([
    {
      id: '1',
      name: 'Jane Smith',
      email: 'jane@example.com',
      status: 'rewarded',
      signupDate: '2025-10-20T10:00:00Z',
      rewardAmount: 30,
      firstTransactionDate: '2025-10-21T15:30:00Z',
    },
    {
      id: '2',
      name: 'Mike Johnson',
      email: 'mike@example.com',
      status: 'completed',
      signupDate: '2025-10-22T14:20:00Z',
      rewardAmount: 30,
      firstTransactionDate: '2025-10-23T09:15:00Z',
    },
    {
      id: '3',
      name: 'Sarah Williams',
      email: 'sarah@example.com',
      status: 'pending',
      signupDate: '2025-10-24T08:45:00Z',
      rewardAmount: 0,
    },
  ]);

  useEffect(() => {
    AnalyticsService.trackScreenView('ReferralScreen');
  }, []);

  const handleCopyCode = () => {
    Clipboard.setString(referralCode);
    Alert.alert('Copied!', 'Referral code copied to clipboard');
    AnalyticsService.trackEvent('referral_code_copied', {
      code: referralCode,
    });
  };

  const handleShareReferral = async () => {
    const referralMessage = `Join Waqiti and get $30 free! Use my referral code: ${referralCode}\n\nDownload: https://waqiti.com/app`;

    try {
      await Share.share({
        message: referralMessage,
        title: 'Join Waqiti',
      });

      AnalyticsService.trackEvent('referral_shared', {
        code: referralCode,
      });
    } catch (error) {
      console.error('Share failed:', error);
    }
  };

  const formatCurrency = (amount: number): string => {
    return new Intl.NumberFormat('en-US', {
      style: 'currency',
      currency: 'USD',
    }).format(amount);
  };

  const getStatusColor = (status: string): string => {
    switch (status) {
      case 'rewarded':
        return '#4CAF50';
      case 'completed':
        return '#2196F3';
      case 'pending':
        return '#FF9800';
      default:
        return '#999';
    }
  };

  const getStatusText = (status: string): string => {
    switch (status) {
      case 'rewarded':
        return 'Reward Received';
      case 'completed':
        return 'Reward Pending';
      case 'pending':
        return 'Awaiting First Transaction';
      default:
        return 'Unknown';
    }
  };

  const renderStatsCard = () => (
    <View style={styles.statsCard}>
      <View style={styles.statItem}>
        <Icon name="account-multiple" size={32} color="#6200EE" />
        <Text style={styles.statValue}>{totalReferrals}</Text>
        <Text style={styles.statLabel}>Total Referrals</Text>
      </View>

      <View style={styles.statDivider} />

      <View style={styles.statItem}>
        <Icon name="check-circle" size={32} color="#4CAF50" />
        <Text style={styles.statValue}>{completedReferrals}</Text>
        <Text style={styles.statLabel}>Completed</Text>
      </View>

      <View style={styles.statDivider} />

      <View style={styles.statItem}>
        <Icon name="currency-usd" size={32} color="#FFD700" />
        <Text style={styles.statValue}>{formatCurrency(totalEarned)}</Text>
        <Text style={styles.statLabel}>Total Earned</Text>
      </View>
    </View>
  );

  const renderReferralCode = () => (
    <View style={styles.codeCard}>
      <Text style={styles.codeLabel}>Your Referral Code</Text>

      <View style={styles.codeContainer}>
        <View style={styles.codeBox}>
          <Text style={styles.codeText}>{referralCode}</Text>
        </View>
        <TouchableOpacity style={styles.copyButton} onPress={handleCopyCode}>
          <Icon name="content-copy" size={20} color="#6200EE" />
        </TouchableOpacity>
      </View>

      <View style={styles.qrContainer}>
        <QRCode
          value={`https://waqiti.com/r/${referralCode}`}
          size={120}
          color="#212121"
          backgroundColor="#FFFFFF"
        />
      </View>

      <TouchableOpacity style={styles.shareButton} onPress={handleShareReferral}>
        <Icon name="share-variant" size={20} color="#FFFFFF" />
        <Text style={styles.shareButtonText}>Share Referral Link</Text>
      </TouchableOpacity>
    </View>
  );

  const renderRewardInfo = () => (
    <View style={styles.rewardInfoCard}>
      <View style={styles.rewardInfoHeader}>
        <Icon name="gift" size={24} color="#6200EE" />
        <Text style={styles.rewardInfoTitle}>How It Works</Text>
      </View>

      <View style={styles.rewardStep}>
        <View style={styles.stepNumber}>
          <Text style={styles.stepNumberText}>1</Text>
        </View>
        <View style={styles.stepContent}>
          <Text style={styles.stepTitle}>Share Your Code</Text>
          <Text style={styles.stepDescription}>
            Send your unique referral code to friends
          </Text>
        </View>
      </View>

      <View style={styles.rewardStep}>
        <View style={styles.stepNumber}>
          <Text style={styles.stepNumberText}>2</Text>
        </View>
        <View style={styles.stepContent}>
          <Text style={styles.stepTitle}>Friend Signs Up</Text>
          <Text style={styles.stepDescription}>
            They create an account using your code
          </Text>
        </View>
      </View>

      <View style={styles.rewardStep}>
        <View style={styles.stepNumber}>
          <Text style={styles.stepNumberText}>3</Text>
        </View>
        <View style={styles.stepContent}>
          <Text style={styles.stepTitle}>Both Get Rewarded</Text>
          <Text style={styles.stepDescription}>
            You both get $30 after their first transaction
          </Text>
        </View>
      </View>

      {pendingRewards > 0 && (
        <View style={styles.pendingRewardsBox}>
          <Icon name="clock-outline" size={20} color="#FF9800" />
          <Text style={styles.pendingRewardsText}>
            {formatCurrency(pendingRewards)} pending from {totalReferrals - completedReferrals} referrals
          </Text>
        </View>
      )}
    </View>
  );

  const renderReferralItem = ({ item }: { item: Referral }) => (
    <View style={styles.referralItem}>
      <View style={styles.referralIcon}>
        <Icon name="account" size={24} color="#6200EE" />
      </View>

      <View style={styles.referralInfo}>
        <Text style={styles.referralName}>{item.name}</Text>
        <Text style={styles.referralEmail}>{item.email}</Text>
        <Text style={styles.referralDate}>
          Signed up {new Date(item.signupDate).toLocaleDateString()}
        </Text>
      </View>

      <View style={styles.referralStatus}>
        <View
          style={[
            styles.statusBadge,
            { backgroundColor: getStatusColor(item.status) + '20' },
          ]}
        >
          <Icon
            name={
              item.status === 'rewarded'
                ? 'check-circle'
                : item.status === 'completed'
                ? 'clock-check'
                : 'clock-outline'
            }
            size={14}
            color={getStatusColor(item.status)}
          />
          <Text
            style={[
              styles.statusText,
              { color: getStatusColor(item.status) },
            ]}
          >
            {getStatusText(item.status)}
          </Text>
        </View>
        {item.rewardAmount > 0 && (
          <Text style={styles.rewardAmount}>
            {formatCurrency(item.rewardAmount)}
          </Text>
        )}
      </View>
    </View>
  );

  return (
    <View style={styles.container}>
      <Header title="Referral Program" showBack />

      <ScrollView style={styles.content}>
        {renderStatsCard()}
        {renderReferralCode()}
        {renderRewardInfo()}

        <View style={styles.referralsSection}>
          <View style={styles.referralsHeader}>
            <Text style={styles.referralsTitle}>Your Referrals</Text>
            <Text style={styles.referralsCount}>
              {referrals.length} total
            </Text>
          </View>

          <FlatList
            data={referrals}
            renderItem={renderReferralItem}
            keyExtractor={(item) => item.id}
            scrollEnabled={false}
            ListEmptyComponent={
              <View style={styles.emptyState}>
                <Icon name="account-plus" size={64} color="#E0E0E0" />
                <Text style={styles.emptyText}>
                  No referrals yet. Start inviting friends!
                </Text>
              </View>
            }
          />
        </View>

        <View style={styles.termsCard}>
          <TouchableOpacity
            style={styles.termsButton}
            onPress={() => navigation.navigate('ReferralTerms' as never)}
          >
            <Icon name="file-document" size={20} color="#666" />
            <Text style={styles.termsText}>Terms & Conditions</Text>
            <Icon name="chevron-right" size={20} color="#666" />
          </TouchableOpacity>
        </View>
      </ScrollView>
    </View>
  );
};

const styles = StyleSheet.create({
  container: {
    flex: 1,
    backgroundColor: '#F5F5F5',
  },
  content: {
    flex: 1,
  },
  statsCard: {
    flexDirection: 'row',
    backgroundColor: '#FFFFFF',
    marginHorizontal: 16,
    marginTop: 16,
    marginBottom: 8,
    borderRadius: 12,
    padding: 20,
    elevation: 2,
    shadowColor: '#000',
    shadowOffset: { width: 0, height: 1 },
    shadowOpacity: 0.1,
    shadowRadius: 2,
  },
  statItem: {
    flex: 1,
    alignItems: 'center',
  },
  statValue: {
    fontSize: 24,
    fontWeight: 'bold',
    color: '#212121',
    marginTop: 8,
    marginBottom: 4,
  },
  statLabel: {
    fontSize: 12,
    color: '#666',
    textAlign: 'center',
  },
  statDivider: {
    width: 1,
    backgroundColor: '#E0E0E0',
    marginHorizontal: 8,
  },
  codeCard: {
    backgroundColor: '#FFFFFF',
    marginHorizontal: 16,
    marginBottom: 8,
    borderRadius: 12,
    padding: 20,
    alignItems: 'center',
    elevation: 2,
    shadowColor: '#000',
    shadowOffset: { width: 0, height: 1 },
    shadowOpacity: 0.1,
    shadowRadius: 2,
  },
  codeLabel: {
    fontSize: 14,
    fontWeight: '600',
    color: '#666',
    marginBottom: 12,
  },
  codeContainer: {
    flexDirection: 'row',
    alignItems: 'center',
    marginBottom: 20,
  },
  codeBox: {
    backgroundColor: '#F5F5F5',
    paddingHorizontal: 20,
    paddingVertical: 12,
    borderRadius: 8,
    borderWidth: 2,
    borderColor: '#6200EE',
    borderStyle: 'dashed',
  },
  codeText: {
    fontSize: 20,
    fontWeight: 'bold',
    color: '#6200EE',
    letterSpacing: 2,
  },
  copyButton: {
    marginLeft: 12,
    padding: 12,
    backgroundColor: '#F3E5F5',
    borderRadius: 8,
  },
  qrContainer: {
    padding: 16,
    backgroundColor: '#FFFFFF',
    borderRadius: 8,
    marginBottom: 20,
  },
  shareButton: {
    flexDirection: 'row',
    alignItems: 'center',
    justifyContent: 'center',
    backgroundColor: '#6200EE',
    paddingHorizontal: 24,
    paddingVertical: 14,
    borderRadius: 24,
    width: '100%',
  },
  shareButtonText: {
    color: '#FFFFFF',
    fontSize: 16,
    fontWeight: 'bold',
    marginLeft: 8,
  },
  rewardInfoCard: {
    backgroundColor: '#FFFFFF',
    marginHorizontal: 16,
    marginBottom: 8,
    borderRadius: 12,
    padding: 20,
    elevation: 2,
    shadowColor: '#000',
    shadowOffset: { width: 0, height: 1 },
    shadowOpacity: 0.1,
    shadowRadius: 2,
  },
  rewardInfoHeader: {
    flexDirection: 'row',
    alignItems: 'center',
    marginBottom: 20,
  },
  rewardInfoTitle: {
    fontSize: 18,
    fontWeight: 'bold',
    color: '#212121',
    marginLeft: 12,
  },
  rewardStep: {
    flexDirection: 'row',
    alignItems: 'flex-start',
    marginBottom: 16,
  },
  stepNumber: {
    width: 32,
    height: 32,
    borderRadius: 16,
    backgroundColor: '#6200EE',
    justifyContent: 'center',
    alignItems: 'center',
    marginRight: 12,
  },
  stepNumberText: {
    fontSize: 16,
    fontWeight: 'bold',
    color: '#FFFFFF',
  },
  stepContent: {
    flex: 1,
  },
  stepTitle: {
    fontSize: 16,
    fontWeight: 'bold',
    color: '#212121',
    marginBottom: 4,
  },
  stepDescription: {
    fontSize: 14,
    color: '#666',
    lineHeight: 20,
  },
  pendingRewardsBox: {
    flexDirection: 'row',
    alignItems: 'center',
    backgroundColor: '#FFF3E0',
    padding: 12,
    borderRadius: 8,
    marginTop: 8,
  },
  pendingRewardsText: {
    fontSize: 14,
    fontWeight: '600',
    color: '#FF9800',
    marginLeft: 8,
    flex: 1,
  },
  referralsSection: {
    backgroundColor: '#FFFFFF',
    marginHorizontal: 16,
    marginBottom: 8,
    borderRadius: 12,
    padding: 16,
    elevation: 2,
    shadowColor: '#000',
    shadowOffset: { width: 0, height: 1 },
    shadowOpacity: 0.1,
    shadowRadius: 2,
  },
  referralsHeader: {
    flexDirection: 'row',
    justifyContent: 'space-between',
    alignItems: 'center',
    marginBottom: 16,
  },
  referralsTitle: {
    fontSize: 18,
    fontWeight: 'bold',
    color: '#212121',
  },
  referralsCount: {
    fontSize: 14,
    color: '#666',
  },
  referralItem: {
    flexDirection: 'row',
    alignItems: 'center',
    paddingVertical: 12,
    borderBottomWidth: 1,
    borderBottomColor: '#F5F5F5',
  },
  referralIcon: {
    width: 48,
    height: 48,
    borderRadius: 24,
    backgroundColor: '#F3E5F5',
    justifyContent: 'center',
    alignItems: 'center',
    marginRight: 12,
  },
  referralInfo: {
    flex: 1,
  },
  referralName: {
    fontSize: 16,
    fontWeight: 'bold',
    color: '#212121',
    marginBottom: 2,
  },
  referralEmail: {
    fontSize: 13,
    color: '#666',
    marginBottom: 2,
  },
  referralDate: {
    fontSize: 12,
    color: '#999',
  },
  referralStatus: {
    alignItems: 'flex-end',
  },
  statusBadge: {
    flexDirection: 'row',
    alignItems: 'center',
    paddingHorizontal: 8,
    paddingVertical: 4,
    borderRadius: 12,
    marginBottom: 4,
  },
  statusText: {
    fontSize: 11,
    fontWeight: 'bold',
    marginLeft: 4,
  },
  rewardAmount: {
    fontSize: 14,
    fontWeight: 'bold',
    color: '#4CAF50',
  },
  emptyState: {
    alignItems: 'center',
    paddingVertical: 32,
  },
  emptyText: {
    fontSize: 14,
    color: '#999',
    marginTop: 12,
  },
  termsCard: {
    backgroundColor: '#FFFFFF',
    marginHorizontal: 16,
    marginBottom: 16,
    borderRadius: 12,
    overflow: 'hidden',
    elevation: 1,
    shadowColor: '#000',
    shadowOffset: { width: 0, height: 1 },
    shadowOpacity: 0.05,
    shadowRadius: 1,
  },
  termsButton: {
    flexDirection: 'row',
    alignItems: 'center',
    justifyContent: 'space-between',
    padding: 16,
  },
  termsText: {
    fontSize: 14,
    color: '#666',
    marginLeft: 12,
    flex: 1,
  },
});

export default ReferralScreen;
