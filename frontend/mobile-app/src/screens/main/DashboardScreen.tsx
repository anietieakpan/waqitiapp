import React, { useEffect, useState, useCallback } from 'react';
import {
  View,
  ScrollView,
  StyleSheet,
  RefreshControl,
  TouchableOpacity,
  Dimensions,
} from 'react-native';
import {
  Text,
  Card,
  Avatar,
  IconButton,
  useTheme,
  Surface,
  Chip,
  ProgressBar,
} from 'react-native-paper';
import { useNavigation } from '@react-navigation/native';
import { useSelector, useDispatch } from 'react-redux';
import LinearGradient from 'react-native-linear-gradient';
import Icon from 'react-native-vector-icons/MaterialCommunityIcons';
import { AreaChart, Grid, YAxis } from 'react-native-svg-charts';
import * as shape from 'd3-shape';
import { useQuery } from 'react-query';

import { RootState, AppDispatch } from '../../store/store';
import { fetchWalletBalance } from '../../store/slices/walletSlice';
import { fetchRecentTransactions } from '../../store/slices/transactionSlice';
import { fetchNotifications } from '../../store/slices/notificationSlice';
import Header from '../../components/common/Header';
import QuickActionCard from '../../components/dashboard/QuickActionCard';
import RecentTransactionItem from '../../components/dashboard/RecentTransactionItem';
import PromoCard from '../../components/dashboard/PromoCard';
import { formatCurrency } from '../../utils/formatters';
import { useAuth } from '../../hooks/useAuth';
import { dashboardService } from '../../services/dashboardService';

const { width: screenWidth } = Dimensions.get('window');

interface DashboardStats {
  totalSent: number;
  totalReceived: number;
  monthlySpending: number;
  savingsGoalProgress: number;
}

/**
 * Main Dashboard Screen - Entry point for authenticated users
 */
const DashboardScreen: React.FC = () => {
  const theme = useTheme();
  const navigation = useNavigation();
  const dispatch = useDispatch<AppDispatch>();
  const { user } = useAuth();
  
  const [refreshing, setRefreshing] = useState(false);
  
  // Redux state
  const wallet = useSelector((state: RootState) => state.wallet);
  const transactions = useSelector((state: RootState) => state.transaction.recentTransactions);
  const notifications = useSelector((state: RootState) => state.notification.unreadCount);
  
  // Fetch dashboard stats
  const { data: dashboardStats, refetch: refetchStats } = useQuery<DashboardStats>(
    'dashboardStats',
    dashboardService.getDashboardStats,
    {
      refetchInterval: 30000, // Refresh every 30 seconds
    }
  );
  
  // Fetch spending chart data
  const { data: chartData } = useQuery(
    'spendingChart',
    dashboardService.getSpendingChartData,
    {
      refetchInterval: 60000, // Refresh every minute
    }
  );

  useEffect(() => {
    loadDashboardData();
  }, []);

  const loadDashboardData = async () => {
    dispatch(fetchWalletBalance());
    dispatch(fetchRecentTransactions());
    dispatch(fetchNotifications());
  };

  const onRefresh = useCallback(async () => {
    setRefreshing(true);
    await Promise.all([
      loadDashboardData(),
      refetchStats(),
    ]);
    setRefreshing(false);
  }, []);

  const handleQuickAction = (action: string) => {
    switch (action) {
      case 'send':
        navigation.navigate('SendMoney' as never);
        break;
      case 'request':
        navigation.navigate('RequestMoney' as never);
        break;
      case 'scan':
        navigation.navigate('ScanQR' as never);
        break;
      case 'nearby':
        navigation.navigate('NearbyPayment' as never);
        break;
    }
  };

  return (
    <View style={styles.container}>
      <Header
        title={`Hello, ${user?.firstName || 'User'} 👋`}
        subtitle="Welcome back to Waqiti"
        rightAction={
          <View style={styles.headerActions}>
            <IconButton
              icon="magnify"
              size={24}
              onPress={() => navigation.navigate('Search' as never)}
            />
            <View>
              <IconButton
                icon="bell"
                size={24}
                onPress={() => navigation.navigate('Notifications' as never)}
              />
              {notifications > 0 && (
                <View style={styles.notificationBadge}>
                  <Text style={styles.notificationBadgeText}>{notifications}</Text>
                </View>
              )}
            </View>
          </View>
        }
      />
      
      <ScrollView
        style={styles.scrollView}
        showsVerticalScrollIndicator={false}
        refreshControl={
          <RefreshControl refreshing={refreshing} onRefresh={onRefresh} />
        }
      >
        {/* Wallet Balance Card */}
        <LinearGradient
          colors={[theme.colors.primary, theme.colors.secondary]}
          style={styles.balanceCard}
          start={{ x: 0, y: 0 }}
          end={{ x: 1, y: 1 }}
        >
          <View style={styles.balanceHeader}>
            <Text style={styles.balanceLabel}>Total Balance</Text>
            <TouchableOpacity onPress={() => navigation.navigate('Wallet' as never)}>
              <Icon name="eye" size={20} color="white" />
            </TouchableOpacity>
          </View>
          
          <Text style={styles.balanceAmount}>
            {formatCurrency(wallet.balance, wallet.currency)}
          </Text>
          
          <View style={styles.balanceStats}>
            <View style={styles.balanceStat}>
              <Icon name="arrow-up" size={16} color="#4CAF50" />
              <Text style={styles.balanceStatText}>
                +{formatCurrency(dashboardStats?.totalReceived || 0)}
              </Text>
            </View>
            <View style={styles.balanceStat}>
              <Icon name="arrow-down" size={16} color="#FF5252" />
              <Text style={styles.balanceStatText}>
                -{formatCurrency(dashboardStats?.totalSent || 0)}
              </Text>
            </View>
          </View>
          
          {/* Mini Spending Chart */}
          {chartData && (
            <View style={styles.chartContainer}>
              <AreaChart
                style={styles.chart}
                data={chartData}
                contentInset={{ top: 10, bottom: 10 }}
                curve={shape.curveNatural}
                svg={{ fill: 'rgba(255, 255, 255, 0.3)' }}
              />
            </View>
          )}
        </LinearGradient>

        {/* Quick Actions */}
        <View style={styles.section}>
          <Text style={styles.sectionTitle}>Quick Actions</Text>
          <View style={styles.quickActions}>
            <QuickActionCard
              icon="send"
              label="Send"
              onPress={() => handleQuickAction('send')}
              color={theme.colors.primary}
            />
            <QuickActionCard
              icon="call-received"
              label="Request"
              onPress={() => handleQuickAction('request')}
              color={theme.colors.secondary}
            />
            <QuickActionCard
              icon="qrcode-scan"
              label="Scan"
              onPress={() => handleQuickAction('scan')}
              color={theme.colors.tertiary}
            />
            <QuickActionCard
              icon="near-me"
              label="Nearby"
              onPress={() => handleQuickAction('nearby')}
              color={theme.colors.error}
            />
          </View>
        </View>

        {/* Savings Goal Progress */}
        {dashboardStats?.savingsGoalProgress !== undefined && (
          <Card style={styles.savingsCard}>
            <Card.Content>
              <View style={styles.savingsHeader}>
                <Text style={styles.savingsTitle}>Monthly Savings Goal</Text>
                <Text style={styles.savingsAmount}>
                  {dashboardStats.savingsGoalProgress}%
                </Text>
              </View>
              <ProgressBar
                progress={dashboardStats.savingsGoalProgress / 100}
                color={theme.colors.primary}
                style={styles.progressBar}
              />
              <Text style={styles.savingsSubtext}>
                Keep going! You're doing great this month.
              </Text>
            </Card.Content>
          </Card>
        )}

        {/* Recent Transactions */}
        <View style={styles.section}>
          <View style={styles.sectionHeader}>
            <Text style={styles.sectionTitle}>Recent Transactions</Text>
            <TouchableOpacity
              onPress={() => navigation.navigate('TransactionHistory' as never)}
            >
              <Text style={styles.seeAllText}>See all</Text>
            </TouchableOpacity>
          </View>
          
          {transactions.length > 0 ? (
            <Surface style={styles.transactionsList}>
              {transactions.slice(0, 5).map((transaction) => (
                <RecentTransactionItem
                  key={transaction.id}
                  transaction={transaction}
                  onPress={() => navigation.navigate('TransactionDetails', {
                    transactionId: transaction.id
                  } as never)}
                />
              ))}
            </Surface>
          ) : (
            <Card style={styles.emptyCard}>
              <Card.Content style={styles.emptyContent}>
                <Icon name="history" size={48} color={theme.colors.onSurfaceDisabled} />
                <Text style={styles.emptyText}>No transactions yet</Text>
                <Text style={styles.emptySubtext}>
                  Start by sending or requesting money
                </Text>
              </Card.Content>
            </Card>
          )}
        </View>

        {/* Promo Cards */}
        <View style={styles.section}>
          <ScrollView
            horizontal
            showsHorizontalScrollIndicator={false}
            contentContainerStyle={styles.promoScroll}
          >
            <PromoCard
              title="Invite Friends"
              description="Get $10 for each friend who joins"
              image={require('../../assets/images/referral-promo.png')}
              onPress={() => navigation.navigate('Referral' as never)}
            />
            <PromoCard
              title="Business Account"
              description="Accept payments for your business"
              image={require('../../assets/images/business-promo.png')}
              onPress={() => navigation.navigate('BusinessDashboard' as never)}
            />
            <PromoCard
              title="Cashback Rewards"
              description="Earn up to 3% on every payment"
              image={require('../../assets/images/rewards-promo.png')}
              onPress={() => navigation.navigate('Rewards' as never)}
            />
          </ScrollView>
        </View>

        {/* Bottom Spacing */}
        <View style={styles.bottomSpacer} />
      </ScrollView>
    </View>
  );
};

const styles = StyleSheet.create({
  container: {
    flex: 1,
    backgroundColor: '#f5f5f5',
  },
  headerActions: {
    flexDirection: 'row',
    alignItems: 'center',
  },
  notificationBadge: {
    position: 'absolute',
    top: 8,
    right: 8,
    backgroundColor: '#FF5252',
    borderRadius: 10,
    width: 20,
    height: 20,
    justifyContent: 'center',
    alignItems: 'center',
  },
  notificationBadgeText: {
    color: 'white',
    fontSize: 12,
    fontWeight: 'bold',
  },
  scrollView: {
    flex: 1,
  },
  balanceCard: {
    margin: 16,
    padding: 20,
    borderRadius: 16,
    elevation: 4,
  },
  balanceHeader: {
    flexDirection: 'row',
    justifyContent: 'space-between',
    alignItems: 'center',
    marginBottom: 8,
  },
  balanceLabel: {
    color: 'white',
    fontSize: 14,
    opacity: 0.8,
  },
  balanceAmount: {
    color: 'white',
    fontSize: 36,
    fontWeight: 'bold',
    marginBottom: 16,
  },
  balanceStats: {
    flexDirection: 'row',
    gap: 24,
  },
  balanceStat: {
    flexDirection: 'row',
    alignItems: 'center',
    gap: 4,
  },
  balanceStatText: {
    color: 'white',
    fontSize: 14,
  },
  chartContainer: {
    height: 60,
    marginTop: 16,
    marginHorizontal: -20,
  },
  chart: {
    flex: 1,
  },
  section: {
    marginTop: 24,
    paddingHorizontal: 16,
  },
  sectionHeader: {
    flexDirection: 'row',
    justifyContent: 'space-between',
    alignItems: 'center',
    marginBottom: 16,
  },
  sectionTitle: {
    fontSize: 18,
    fontWeight: '600',
    color: '#333',
  },
  seeAllText: {
    fontSize: 14,
    color: '#2196F3',
  },
  quickActions: {
    flexDirection: 'row',
    justifyContent: 'space-between',
    gap: 12,
  },
  savingsCard: {
    margin: 16,
    elevation: 2,
  },
  savingsHeader: {
    flexDirection: 'row',
    justifyContent: 'space-between',
    alignItems: 'center',
    marginBottom: 12,
  },
  savingsTitle: {
    fontSize: 16,
    fontWeight: '500',
  },
  savingsAmount: {
    fontSize: 18,
    fontWeight: 'bold',
    color: '#4CAF50',
  },
  progressBar: {
    height: 8,
    borderRadius: 4,
    marginBottom: 8,
  },
  savingsSubtext: {
    fontSize: 12,
    color: '#666',
  },
  transactionsList: {
    borderRadius: 12,
    elevation: 1,
    backgroundColor: 'white',
  },
  emptyCard: {
    elevation: 1,
  },
  emptyContent: {
    alignItems: 'center',
    paddingVertical: 32,
  },
  emptyText: {
    fontSize: 16,
    fontWeight: '500',
    color: '#666',
    marginTop: 12,
  },
  emptySubtext: {
    fontSize: 14,
    color: '#999',
    marginTop: 4,
  },
  promoScroll: {
    gap: 12,
  },
  bottomSpacer: {
    height: 24,
  },
});