import React, { useState, useEffect } from 'react';
import {
  View,
  Text,
  StyleSheet,
  ScrollView,
  TouchableOpacity,
  FlatList,
  RefreshControl,
  Image,
} from 'react-native';
import { useNavigation } from '@react-navigation/native';
import { useSelector, useDispatch } from 'react-redux';
import Icon from 'react-native-vector-icons/MaterialCommunityIcons';
import { RootState } from '../store';
import Header from '../components/Header';
import { AnalyticsService } from '../services/AnalyticsService';

/**
 * RewardsScreen
 *
 * Screen for managing rewards, points, and redemptions
 *
 * Features:
 * - Points balance display
 * - Rewards catalog
 * - Redemption history
 * - Points earning opportunities
 * - Tier/status display
 *
 * @author Waqiti Engineering Team
 * @version 1.0.0
 */

interface Reward {
  id: string;
  name: string;
  description: string;
  pointsCost: number;
  category: 'cashback' | 'gift_card' | 'discount' | 'merchandise';
  imageUrl?: string;
  availability: 'available' | 'limited' | 'out_of_stock';
  expiresAt?: string;
  terms?: string;
}

interface RewardTransaction {
  id: string;
  type: 'earned' | 'redeemed' | 'expired';
  points: number;
  description: string;
  timestamp: string;
  relatedReward?: Reward;
}

const RewardsScreen: React.FC = () => {
  const navigation = useNavigation();
  const dispatch = useDispatch();
  const { user } = useSelector((state: RootState) => state.auth);

  const [refreshing, setRefreshing] = useState(false);
  const [selectedTab, setSelectedTab] = useState<'catalog' | 'history'>('catalog');
  const [selectedCategory, setSelectedCategory] = useState<'all' | 'cashback' | 'gift_card' | 'discount' | 'merchandise'>('all');

  // Mock data - TODO: Replace with Redux state
  const [pointsBalance, setPointsBalance] = useState(12450);
  const [tier, setTier] = useState<'bronze' | 'silver' | 'gold' | 'platinum'>('gold');
  const [nextTierPoints, setNextTierPoints] = useState(2550);

  const [rewards, setRewards] = useState<Reward[]>([
    {
      id: '1',
      name: '$10 Cashback',
      description: 'Instant cashback to your wallet',
      pointsCost: 1000,
      category: 'cashback',
      availability: 'available',
    },
    {
      id: '2',
      name: '$25 Amazon Gift Card',
      description: 'Digital gift card delivered instantly',
      pointsCost: 2400,
      category: 'gift_card',
      availability: 'available',
    },
    {
      id: '3',
      name: '20% Off Next Transaction',
      description: 'Valid for transactions up to $100',
      pointsCost: 500,
      category: 'discount',
      availability: 'limited',
      expiresAt: '2025-11-24T23:59:59Z',
    },
    {
      id: '4',
      name: 'Waqiti Premium T-Shirt',
      description: 'Limited edition merchandise',
      pointsCost: 3000,
      category: 'merchandise',
      availability: 'limited',
    },
  ]);

  const [rewardHistory, setRewardHistory] = useState<RewardTransaction[]>([
    {
      id: '1',
      type: 'earned',
      points: 50,
      description: 'Payment received from John Doe',
      timestamp: new Date().toISOString(),
    },
    {
      id: '2',
      type: 'redeemed',
      points: -1000,
      description: '$10 Cashback redeemed',
      timestamp: new Date(Date.now() - 86400000).toISOString(),
    },
  ]);

  useEffect(() => {
    AnalyticsService.trackScreenView('RewardsScreen');
    loadRewards();
  }, []);

  const loadRewards = async () => {
    // TODO: Load rewards from API
  };

  const handleRefresh = async () => {
    setRefreshing(true);
    await loadRewards();
    setRefreshing(false);
  };

  const handleRedeemReward = (reward: Reward) => {
    if (pointsBalance < reward.pointsCost) {
      alert(`You need ${reward.pointsCost - pointsBalance} more points to redeem this reward`);
      return;
    }

    AnalyticsService.trackEvent('reward_redemption_initiated', {
      rewardId: reward.id,
      pointsCost: reward.pointsCost,
    });

    // TODO: Show confirmation modal
    navigation.navigate('RewardRedemption' as never, { reward } as never);
  };

  const getTierColor = (tierName: string): string => {
    switch (tierName) {
      case 'bronze':
        return '#CD7F32';
      case 'silver':
        return '#C0C0C0';
      case 'gold':
        return '#FFD700';
      case 'platinum':
        return '#E5E4E2';
      default:
        return '#666';
    }
  };

  const getTierIcon = (tierName: string): string => {
    switch (tierName) {
      case 'bronze':
        return 'medal';
      case 'silver':
        return 'medal';
      case 'gold':
        return 'medal';
      case 'platinum':
        return 'crown';
      default:
        return 'star';
    }
  };

  const formatPoints = (points: number): string => {
    return points.toLocaleString();
  };

  const renderPointsBalance = () => (
    <View style={styles.balanceCard}>
      <View style={styles.balanceHeader}>
        <View style={styles.balanceInfo}>
          <Text style={styles.balanceLabel}>Available Points</Text>
          <Text style={styles.balanceValue}>{formatPoints(pointsBalance)}</Text>
        </View>
        <View style={[styles.tierBadge, { backgroundColor: getTierColor(tier) }]}>
          <Icon name={getTierIcon(tier)} size={24} color="#FFFFFF" />
          <Text style={styles.tierText}>{tier.toUpperCase()}</Text>
        </View>
      </View>

      <View style={styles.progressContainer}>
        <View style={styles.progressHeader}>
          <Text style={styles.progressLabel}>Progress to Platinum</Text>
          <Text style={styles.progressValue}>{formatPoints(nextTierPoints)} more</Text>
        </View>
        <View style={styles.progressBar}>
          <View
            style={[
              styles.progressFill,
              {
                width: `${((15000 - nextTierPoints) / 15000) * 100}%`,
                backgroundColor: getTierColor(tier),
              },
            ]}
          />
        </View>
      </View>

      <View style={styles.quickActions}>
        <TouchableOpacity
          style={styles.quickActionButton}
          onPress={() => navigation.navigate('EarnPoints' as never)}
        >
          <Icon name="plus-circle" size={20} color="#6200EE" />
          <Text style={styles.quickActionText}>Earn Points</Text>
        </TouchableOpacity>

        <TouchableOpacity
          style={styles.quickActionButton}
          onPress={() => navigation.navigate('PointsHistory' as never)}
        >
          <Icon name="history" size={20} color="#6200EE" />
          <Text style={styles.quickActionText}>History</Text>
        </TouchableOpacity>
      </View>
    </View>
  );

  const renderCategoryFilter = () => (
    <ScrollView
      horizontal
      showsHorizontalScrollIndicator={false}
      style={styles.categoryFilter}
      contentContainerStyle={styles.categoryFilterContent}
    >
      {['all', 'cashback', 'gift_card', 'discount', 'merchandise'].map((cat) => (
        <TouchableOpacity
          key={cat}
          style={[
            styles.categoryButton,
            selectedCategory === cat && styles.categoryButtonActive,
          ]}
          onPress={() => setSelectedCategory(cat as any)}
        >
          <Text
            style={[
              styles.categoryButtonText,
              selectedCategory === cat && styles.categoryButtonTextActive,
            ]}
          >
            {cat.replace('_', ' ').replace(/\b\w/g, (l) => l.toUpperCase())}
          </Text>
        </TouchableOpacity>
      ))}
    </ScrollView>
  );

  const renderRewardCard = ({ item }: { item: Reward }) => {
    const canAfford = pointsBalance >= item.pointsCost;

    return (
      <TouchableOpacity
        style={[
          styles.rewardCard,
          !canAfford && styles.rewardCardDisabled,
        ]}
        onPress={() => canAfford && handleRedeemReward(item)}
        disabled={!canAfford || item.availability === 'out_of_stock'}
      >
        <View style={styles.rewardImageContainer}>
          {item.imageUrl ? (
            <Image source={{ uri: item.imageUrl }} style={styles.rewardImage} />
          ) : (
            <View style={[styles.rewardImagePlaceholder, { backgroundColor: getTierColor(tier) + '20' }]}>
              <Icon name="gift" size={40} color={getTierColor(tier)} />
            </View>
          )}
          {item.availability === 'limited' && (
            <View style={styles.limitedBadge}>
              <Text style={styles.limitedBadgeText}>Limited</Text>
            </View>
          )}
        </View>

        <View style={styles.rewardInfo}>
          <Text style={styles.rewardName} numberOfLines={2}>
            {item.name}
          </Text>
          <Text style={styles.rewardDescription} numberOfLines={2}>
            {item.description}
          </Text>

          <View style={styles.rewardFooter}>
            <View style={styles.pointsCostContainer}>
              <Icon name="star" size={16} color="#FFD700" />
              <Text style={styles.pointsCost}>{formatPoints(item.pointsCost)}</Text>
            </View>

            {canAfford ? (
              <View style={styles.redeemButton}>
                <Text style={styles.redeemButtonText}>Redeem</Text>
              </View>
            ) : (
              <View style={styles.insufficientBadge}>
                <Text style={styles.insufficientText}>
                  {formatPoints(item.pointsCost - pointsBalance)} more
                </Text>
              </View>
            )}
          </View>
        </View>
      </TouchableOpacity>
    );
  };

  const renderHistoryItem = ({ item }: { item: RewardTransaction }) => (
    <View style={styles.historyItem}>
      <View
        style={[
          styles.historyIcon,
          {
            backgroundColor:
              item.type === 'earned'
                ? '#E8F5E9'
                : item.type === 'redeemed'
                ? '#E3F2FD'
                : '#FFEBEE',
          },
        ]}
      >
        <Icon
          name={
            item.type === 'earned'
              ? 'plus-circle'
              : item.type === 'redeemed'
              ? 'gift'
              : 'clock-alert'
          }
          size={24}
          color={
            item.type === 'earned'
              ? '#4CAF50'
              : item.type === 'redeemed'
              ? '#2196F3'
              : '#F44336'
          }
        />
      </View>

      <View style={styles.historyInfo}>
        <Text style={styles.historyDescription}>{item.description}</Text>
        <Text style={styles.historyTimestamp}>
          {new Date(item.timestamp).toLocaleDateString('en-US', {
            month: 'short',
            day: 'numeric',
            hour: '2-digit',
            minute: '2-digit',
          })}
        </Text>
      </View>

      <Text
        style={[
          styles.historyPoints,
          { color: item.points > 0 ? '#4CAF50' : '#F44336' },
        ]}
      >
        {item.points > 0 ? '+' : ''}
        {formatPoints(item.points)}
      </Text>
    </View>
  );

  const filteredRewards =
    selectedCategory === 'all'
      ? rewards
      : rewards.filter((r) => r.category === selectedCategory);

  return (
    <View style={styles.container}>
      <Header
        title="Rewards"
        showBack
        rightActions={[
          {
            icon: 'information',
            onPress: () => navigation.navigate('RewardsInfo' as never),
          },
        ]}
      />

      <ScrollView
        style={styles.content}
        refreshControl={
          <RefreshControl refreshing={refreshing} onRefresh={handleRefresh} />
        }
      >
        {renderPointsBalance()}

        <View style={styles.tabContainer}>
          <TouchableOpacity
            style={[
              styles.tab,
              selectedTab === 'catalog' && styles.tabActive,
            ]}
            onPress={() => setSelectedTab('catalog')}
          >
            <Text
              style={[
                styles.tabText,
                selectedTab === 'catalog' && styles.tabTextActive,
              ]}
            >
              Rewards Catalog
            </Text>
          </TouchableOpacity>

          <TouchableOpacity
            style={[
              styles.tab,
              selectedTab === 'history' && styles.tabActive,
            ]}
            onPress={() => setSelectedTab('history')}
          >
            <Text
              style={[
                styles.tabText,
                selectedTab === 'history' && styles.tabTextActive,
              ]}
            >
              History
            </Text>
          </TouchableOpacity>
        </View>

        {selectedTab === 'catalog' ? (
          <>
            {renderCategoryFilter()}
            <FlatList
              data={filteredRewards}
              renderItem={renderRewardCard}
              keyExtractor={(item) => item.id}
              numColumns={2}
              scrollEnabled={false}
              contentContainerStyle={styles.rewardsList}
            />
          </>
        ) : (
          <FlatList
            data={rewardHistory}
            renderItem={renderHistoryItem}
            keyExtractor={(item) => item.id}
            scrollEnabled={false}
            contentContainerStyle={styles.historyList}
          />
        )}
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
  balanceCard: {
    backgroundColor: '#6200EE',
    marginHorizontal: 16,
    marginTop: 16,
    marginBottom: 8,
    borderRadius: 16,
    padding: 20,
    elevation: 4,
    shadowColor: '#000',
    shadowOffset: { width: 0, height: 2 },
    shadowOpacity: 0.2,
    shadowRadius: 4,
  },
  balanceHeader: {
    flexDirection: 'row',
    justifyContent: 'space-between',
    alignItems: 'flex-start',
    marginBottom: 20,
  },
  balanceInfo: {},
  balanceLabel: {
    fontSize: 14,
    color: '#FFFFFF',
    opacity: 0.9,
    marginBottom: 8,
  },
  balanceValue: {
    fontSize: 36,
    fontWeight: 'bold',
    color: '#FFFFFF',
  },
  tierBadge: {
    flexDirection: 'row',
    alignItems: 'center',
    paddingHorizontal: 12,
    paddingVertical: 6,
    borderRadius: 20,
  },
  tierText: {
    color: '#FFFFFF',
    fontSize: 12,
    fontWeight: 'bold',
    marginLeft: 6,
  },
  progressContainer: {
    marginBottom: 16,
  },
  progressHeader: {
    flexDirection: 'row',
    justifyContent: 'space-between',
    marginBottom: 8,
  },
  progressLabel: {
    fontSize: 13,
    color: '#FFFFFF',
    opacity: 0.9,
  },
  progressValue: {
    fontSize: 13,
    color: '#FFFFFF',
    fontWeight: 'bold',
  },
  progressBar: {
    height: 6,
    backgroundColor: 'rgba(255, 255, 255, 0.3)',
    borderRadius: 3,
    overflow: 'hidden',
  },
  progressFill: {
    height: '100%',
    borderRadius: 3,
  },
  quickActions: {
    flexDirection: 'row',
    justifyContent: 'space-around',
    paddingTop: 16,
    borderTopWidth: 1,
    borderTopColor: 'rgba(255, 255, 255, 0.2)',
  },
  quickActionButton: {
    flexDirection: 'row',
    alignItems: 'center',
    paddingHorizontal: 16,
    paddingVertical: 8,
    backgroundColor: '#FFFFFF',
    borderRadius: 20,
  },
  quickActionText: {
    fontSize: 14,
    fontWeight: '600',
    color: '#6200EE',
    marginLeft: 6,
  },
  tabContainer: {
    flexDirection: 'row',
    backgroundColor: '#FFFFFF',
    marginHorizontal: 16,
    marginTop: 16,
    marginBottom: 8,
    borderRadius: 8,
    padding: 4,
  },
  tab: {
    flex: 1,
    paddingVertical: 10,
    alignItems: 'center',
    borderRadius: 6,
  },
  tabActive: {
    backgroundColor: '#6200EE',
  },
  tabText: {
    fontSize: 14,
    fontWeight: '600',
    color: '#666',
  },
  tabTextActive: {
    color: '#FFFFFF',
  },
  categoryFilter: {
    marginVertical: 8,
  },
  categoryFilterContent: {
    paddingHorizontal: 16,
  },
  categoryButton: {
    paddingHorizontal: 16,
    paddingVertical: 8,
    backgroundColor: '#FFFFFF',
    borderRadius: 20,
    marginRight: 8,
    borderWidth: 1,
    borderColor: '#E0E0E0',
  },
  categoryButtonActive: {
    backgroundColor: '#6200EE',
    borderColor: '#6200EE',
  },
  categoryButtonText: {
    fontSize: 14,
    fontWeight: '600',
    color: '#666',
  },
  categoryButtonTextActive: {
    color: '#FFFFFF',
  },
  rewardsList: {
    paddingHorizontal: 12,
    paddingBottom: 16,
  },
  rewardCard: {
    flex: 1,
    backgroundColor: '#FFFFFF',
    margin: 4,
    borderRadius: 12,
    overflow: 'hidden',
    elevation: 2,
    shadowColor: '#000',
    shadowOffset: { width: 0, height: 1 },
    shadowOpacity: 0.1,
    shadowRadius: 2,
  },
  rewardCardDisabled: {
    opacity: 0.6,
  },
  rewardImageContainer: {
    position: 'relative',
  },
  rewardImage: {
    width: '100%',
    height: 120,
  },
  rewardImagePlaceholder: {
    width: '100%',
    height: 120,
    justifyContent: 'center',
    alignItems: 'center',
  },
  limitedBadge: {
    position: 'absolute',
    top: 8,
    right: 8,
    backgroundColor: '#FF9800',
    paddingHorizontal: 8,
    paddingVertical: 4,
    borderRadius: 10,
  },
  limitedBadgeText: {
    fontSize: 10,
    fontWeight: 'bold',
    color: '#FFFFFF',
  },
  rewardInfo: {
    padding: 12,
  },
  rewardName: {
    fontSize: 15,
    fontWeight: 'bold',
    color: '#212121',
    marginBottom: 4,
  },
  rewardDescription: {
    fontSize: 12,
    color: '#666',
    marginBottom: 12,
    height: 32,
  },
  rewardFooter: {
    flexDirection: 'row',
    justifyContent: 'space-between',
    alignItems: 'center',
  },
  pointsCostContainer: {
    flexDirection: 'row',
    alignItems: 'center',
  },
  pointsCost: {
    fontSize: 16,
    fontWeight: 'bold',
    color: '#212121',
    marginLeft: 4,
  },
  redeemButton: {
    backgroundColor: '#6200EE',
    paddingHorizontal: 12,
    paddingVertical: 6,
    borderRadius: 16,
  },
  redeemButtonText: {
    fontSize: 12,
    fontWeight: 'bold',
    color: '#FFFFFF',
  },
  insufficientBadge: {
    backgroundColor: '#FFEBEE',
    paddingHorizontal: 8,
    paddingVertical: 4,
    borderRadius: 12,
  },
  insufficientText: {
    fontSize: 10,
    fontWeight: '600',
    color: '#F44336',
  },
  historyList: {
    paddingHorizontal: 16,
    paddingTop: 8,
  },
  historyItem: {
    flexDirection: 'row',
    alignItems: 'center',
    backgroundColor: '#FFFFFF',
    paddingVertical: 16,
    paddingHorizontal: 16,
    marginBottom: 8,
    borderRadius: 8,
  },
  historyIcon: {
    width: 48,
    height: 48,
    borderRadius: 24,
    justifyContent: 'center',
    alignItems: 'center',
    marginRight: 12,
  },
  historyInfo: {
    flex: 1,
  },
  historyDescription: {
    fontSize: 15,
    fontWeight: '600',
    color: '#212121',
    marginBottom: 4,
  },
  historyTimestamp: {
    fontSize: 12,
    color: '#999',
  },
  historyPoints: {
    fontSize: 16,
    fontWeight: 'bold',
  },
});

export default RewardsScreen;
