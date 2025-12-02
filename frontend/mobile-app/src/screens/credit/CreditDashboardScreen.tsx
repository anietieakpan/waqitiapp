/**
 * Credit Dashboard Screen
 * Main screen for credit building features - score monitoring, recommendations, and account management
 */
import React, { useState, useEffect, useCallback } from 'react';
import {
  View,
  ScrollView,
  Text,
  TouchableOpacity,
  Alert,
  RefreshControl,
  StyleSheet,
  Dimensions,
  ActivityIndicator,
} from 'react-native';
import { SafeAreaView } from 'react-native-safe-area-context';
import { LinearGradient } from 'expo-linear-gradient';
import { Ionicons, MaterialIcons, FontAwesome5 } from '@expo/vector-icons';
import { useFocusEffect } from '@react-navigation/native';
import { LineChart } from 'react-native-chart-kit';
import CreditBuildingService, {
  CreditScore,
  CreditBuildingAccount,
  CreditRecommendation,
  CreditBuildingProgress,
  CreditAlert,
} from '../../services/credit/CreditBuildingService';
import { theme } from '../../theme';
import { format } from 'date-fns';

const { width } = Dimensions.get('window');

interface CreditDashboardScreenProps {
  navigation: any;
}

export const CreditDashboardScreen: React.FC<CreditDashboardScreenProps> = ({ navigation }) => {
  const [creditScore, setCreditScore] = useState<CreditScore | null>(null);
  const [accounts, setAccounts] = useState<CreditBuildingAccount[]>([]);
  const [recommendations, setRecommendations] = useState<CreditRecommendation[]>([]);
  const [progress, setProgress] = useState<CreditBuildingProgress | null>(null);
  const [alerts, setAlerts] = useState<CreditAlert[]>([]);
  const [isLoading, setIsLoading] = useState(true);
  const [refreshing, setRefreshing] = useState(false);
  const [selectedTab, setSelectedTab] = useState<'score' | 'accounts' | 'tips'>('score');

  // Initialize and load data
  useFocusEffect(
    useCallback(() => {
      initializeService();
      loadDashboardData();
    }, [])
  );

  const initializeService = async () => {
    try {
      await CreditBuildingService.initialize();
    } catch (error) {
      console.error('Failed to initialize credit service:', error);
    }
  };

  const loadDashboardData = async () => {
    try {
      setIsLoading(true);
      
      const [
        scoreData,
        accountsData,
        recommendationsData,
        progressData,
        alertsData,
      ] = await Promise.all([
        CreditBuildingService.getCreditScore(),
        CreditBuildingService.getCreditBuildingAccounts(),
        CreditBuildingService.getRecommendations(),
        CreditBuildingService.getCreditBuildingProgress(),
        CreditBuildingService.getAlerts(true),
      ]);

      setCreditScore(scoreData);
      setAccounts(accountsData);
      setRecommendations(recommendationsData.slice(0, 3)); // Top 3 recommendations
      setProgress(progressData);
      setAlerts(alertsData);

    } catch (error) {
      console.error('Failed to load dashboard data:', error);
      Alert.alert('Error', 'Failed to load credit information. Please try again.');
    } finally {
      setIsLoading(false);
    }
  };

  const handleRefresh = async () => {
    setRefreshing(true);
    await loadDashboardData();
    setRefreshing(false);
  };

  const renderCreditScoreCard = () => {
    if (!creditScore) return null;

    const scorePercentage = ((creditScore.score - 300) / 550) * 100;

    return (
      <LinearGradient
        colors={[creditScore.range.color, creditScore.range.color + 'CC']}
        style={styles.creditScoreCard}
        start={{ x: 0, y: 0 }}
        end={{ x: 1, y: 1 }}
      >
        <View style={styles.scoreHeader}>
          <Text style={styles.scoreLabel}>Your Credit Score</Text>
          <TouchableOpacity onPress={() => navigation.navigate('CreditScoreDetails')}>
            <Ionicons name="information-circle-outline" size={24} color="#FFFFFF" />
          </TouchableOpacity>
        </View>

        <View style={styles.scoreContainer}>
          <Text style={styles.scoreValue}>{creditScore.score}</Text>
          <Text style={styles.scoreCategory}>{creditScore.range.category}</Text>
        </View>

        <View style={styles.scoreBar}>
          <View style={[styles.scoreProgress, { width: `${scorePercentage}%` }]} />
        </View>

        <View style={styles.scoreRangeLabels}>
          <Text style={styles.scoreRangeText}>300</Text>
          <Text style={styles.scoreRangeText}>850</Text>
        </View>

        <View style={styles.scoreChange}>
          <Ionicons
            name={creditScore.change.direction === 'up' ? 'trending-up' : 
                  creditScore.change.direction === 'down' ? 'trending-down' : 'remove'}
            size={20}
            color="#FFFFFF"
          />
          <Text style={styles.scoreChangeText}>
            {creditScore.change.direction !== 'stable' && 
              `${creditScore.change.direction === 'up' ? '+' : ''}${creditScore.change.value} points`}
            {creditScore.change.direction === 'stable' && 'No change'}
            {' '}in last {creditScore.change.period === '30d' ? '30 days' : creditScore.change.period}
          </Text>
        </View>

        <TouchableOpacity
          style={styles.viewAllScoresButton}
          onPress={() => navigation.navigate('AllCreditScores')}
        >
          <Text style={styles.viewAllScoresText}>View All 3 Bureau Scores</Text>
          <Ionicons name="chevron-forward" size={16} color="#FFFFFF" />
        </TouchableOpacity>
      </LinearGradient>
    );
  };

  const renderProgressChart = () => {
    if (!progress || progress.projectedTimeline.length < 2) return null;

    const chartData = {
      labels: progress.projectedTimeline.slice(0, 5).map(point => 
        format(new Date(point.date), 'MMM')
      ),
      datasets: [{
        data: progress.projectedTimeline.slice(0, 5).map(point => point.projectedScore),
      }],
    };

    return (
      <View style={styles.progressSection}>
        <Text style={styles.sectionTitle}>Your Progress</Text>
        <View style={styles.progressStats}>
          <View style={styles.progressStat}>
            <Text style={styles.progressStatLabel}>Started</Text>
            <Text style={styles.progressStatValue}>{progress.startScore}</Text>
          </View>
          <View style={styles.progressStat}>
            <Text style={styles.progressStatLabel}>Current</Text>
            <Text style={styles.progressStatValue}>{progress.currentScore}</Text>
          </View>
          <View style={styles.progressStat}>
            <Text style={styles.progressStatLabel}>Target</Text>
            <Text style={styles.progressStatValue}>{progress.targetScore}</Text>
          </View>
        </View>

        <LineChart
          data={chartData}
          width={width - 40}
          height={180}
          chartConfig={{
            backgroundColor: theme.colors.surface,
            backgroundGradientFrom: theme.colors.surface,
            backgroundGradientTo: theme.colors.surface,
            decimalPlaces: 0,
            color: (opacity = 1) => theme.colors.primary,
            labelColor: (opacity = 1) => theme.colors.textLight,
            style: { borderRadius: 16 },
            propsForDots: {
              r: '6',
              strokeWidth: '2',
              stroke: theme.colors.primary,
            },
          }}
          bezier
          style={styles.chart}
        />
      </View>
    );
  };

  const renderCreditAccounts = () => {
    return (
      <View style={styles.accountsSection}>
        <View style={styles.sectionHeader}>
          <Text style={styles.sectionTitle}>Credit Building Accounts</Text>
          <TouchableOpacity
            onPress={() => navigation.navigate('OpenCreditAccount')}
            style={styles.addButton}
          >
            <Ionicons name="add" size={20} color={theme.colors.primary} />
          </TouchableOpacity>
        </View>

        {accounts.length === 0 ? (
          <TouchableOpacity
            style={styles.emptyAccountsCard}
            onPress={() => navigation.navigate('OpenCreditAccount')}
          >
            <MaterialIcons name="account-balance" size={48} color={theme.colors.primary} />
            <Text style={styles.emptyAccountsTitle}>Start Building Credit</Text>
            <Text style={styles.emptyAccountsText}>
              Open a credit building account to improve your score
            </Text>
            <View style={styles.openAccountButton}>
              <Text style={styles.openAccountButtonText}>Get Started</Text>
            </View>
          </TouchableOpacity>
        ) : (
          accounts.map(account => (
            <TouchableOpacity
              key={account.id}
              style={styles.accountCard}
              onPress={() => navigation.navigate('CreditAccountDetails', { accountId: account.id })}
            >
              <View style={styles.accountHeader}>
                <Text style={styles.accountType}>
                  {account.type.replace(/_/g, ' ').replace(/\b\w/g, l => l.toUpperCase())}
                </Text>
                <View style={[styles.accountStatus, 
                  { backgroundColor: account.status === 'active' ? '#4CAF50' : '#FF9800' }
                ]}>
                  <Text style={styles.accountStatusText}>{account.status}</Text>
                </View>
              </View>

              {account.accountDetails.currentBalance !== undefined && (
                <View style={styles.accountBalance}>
                  <Text style={styles.accountBalanceLabel}>Balance</Text>
                  <Text style={styles.accountBalanceValue}>
                    ${account.accountDetails.currentBalance.toFixed(2)}
                  </Text>
                </View>
              )}

              {account.accountDetails.nextPaymentDue && (
                <View style={styles.accountPayment}>
                  <Text style={styles.accountPaymentLabel}>Next Payment</Text>
                  <Text style={styles.accountPaymentValue}>
                    ${account.accountDetails.paymentAmount?.toFixed(2)} on{' '}
                    {format(new Date(account.accountDetails.nextPaymentDue), 'MMM d')}
                  </Text>
                </View>
              )}

              <View style={styles.accountImpact}>
                <FontAwesome5 name="chart-line" size={14} color={theme.colors.success} />
                <Text style={styles.accountImpactText}>
                  +{account.impact.estimatedScoreIncrease} points in {account.impact.timeToImpact}
                </Text>
              </View>
            </TouchableOpacity>
          ))
        )}
      </View>
    );
  };

  const renderRecommendations = () => {
    return (
      <View style={styles.recommendationsSection}>
        <View style={styles.sectionHeader}>
          <Text style={styles.sectionTitle}>Recommendations</Text>
          <TouchableOpacity onPress={() => navigation.navigate('AllRecommendations')}>
            <Text style={styles.viewAllLink}>View All</Text>
          </TouchableOpacity>
        </View>

        {recommendations.map(rec => (
          <TouchableOpacity
            key={rec.id}
            style={styles.recommendationCard}
            onPress={() => navigation.navigate('RecommendationDetails', { recommendation: rec })}
          >
            <View style={[styles.recommendationPriority, 
              { backgroundColor: rec.priority === 'high' ? '#FF5252' : 
                               rec.priority === 'medium' ? '#FFC107' : '#4CAF50' }
            ]}>
              <Text style={styles.recommendationPriorityText}>{rec.priority}</Text>
            </View>

            <View style={styles.recommendationContent}>
              <Text style={styles.recommendationTitle}>{rec.title}</Text>
              <Text style={styles.recommendationDescription} numberOfLines={2}>
                {rec.description}
              </Text>
              <View style={styles.recommendationImpact}>
                <Text style={styles.recommendationImpactText}>
                  {rec.estimatedImpact.scoreIncrease} • {rec.estimatedImpact.timeframe}
                </Text>
                <Ionicons name="chevron-forward" size={16} color={theme.colors.textLight} />
              </View>
            </View>
          </TouchableOpacity>
        ))}
      </View>
    );
  };

  const renderAlerts = () => {
    if (alerts.length === 0) return null;

    return (
      <View style={styles.alertsSection}>
        {alerts.map(alert => (
          <TouchableOpacity
            key={alert.id}
            style={[styles.alertCard, { borderLeftColor: 
              alert.severity === 'critical' ? '#FF5252' :
              alert.severity === 'warning' ? '#FFC107' : '#2196F3' 
            }]}
            onPress={() => {
              CreditBuildingService.markAlertAsRead(alert.id);
              setAlerts(prev => prev.filter(a => a.id !== alert.id));
            }}
          >
            <MaterialIcons 
              name={alert.type === 'score_change' ? 'trending-up' :
                    alert.type === 'payment_due' ? 'payment' :
                    alert.type === 'milestone' ? 'emoji-events' : 'notifications'}
              size={20}
              color={theme.colors.primary}
            />
            <View style={styles.alertContent}>
              <Text style={styles.alertTitle}>{alert.title}</Text>
              <Text style={styles.alertMessage}>{alert.message}</Text>
            </View>
          </TouchableOpacity>
        ))}
      </View>
    );
  };

  if (isLoading) {
    return (
      <SafeAreaView style={styles.container}>
        <View style={styles.loadingContainer}>
          <ActivityIndicator size="large" color={theme.colors.primary} />
          <Text style={styles.loadingText}>Loading your credit information...</Text>
        </View>
      </SafeAreaView>
    );
  }

  return (
    <SafeAreaView style={styles.container}>
      <ScrollView
        style={styles.scrollView}
        refreshControl={
          <RefreshControl refreshing={refreshing} onRefresh={handleRefresh} />
        }
        showsVerticalScrollIndicator={false}
      >
        <View style={styles.header}>
          <Text style={styles.title}>Credit Building</Text>
          <TouchableOpacity onPress={() => navigation.navigate('CreditSettings')}>
            <Ionicons name="settings-outline" size={24} color={theme.colors.text} />
          </TouchableOpacity>
        </View>

        {renderAlerts()}
        {renderCreditScoreCard()}
        {renderProgressChart()}

        <View style={styles.tabContainer}>
          <TouchableOpacity
            style={[styles.tab, selectedTab === 'accounts' && styles.activeTab]}
            onPress={() => setSelectedTab('accounts')}
          >
            <Text style={[styles.tabText, selectedTab === 'accounts' && styles.activeTabText]}>
              Accounts
            </Text>
          </TouchableOpacity>
          <TouchableOpacity
            style={[styles.tab, selectedTab === 'tips' && styles.activeTab]}
            onPress={() => setSelectedTab('tips')}
          >
            <Text style={[styles.tabText, selectedTab === 'tips' && styles.activeTabText]}>
              Tips
            </Text>
          </TouchableOpacity>
        </View>

        {selectedTab === 'accounts' ? renderCreditAccounts() : renderRecommendations()}

        <TouchableOpacity
          style={styles.simulatorButton}
          onPress={() => navigation.navigate('CreditSimulator')}
        >
          <MaterialIcons name="science" size={24} color={theme.colors.white} />
          <Text style={styles.simulatorButtonText}>Credit Score Simulator</Text>
        </TouchableOpacity>
      </ScrollView>
    </SafeAreaView>
  );
};

const styles = StyleSheet.create({
  container: {
    flex: 1,
    backgroundColor: theme.colors.background,
  },
  scrollView: {
    flex: 1,
  },
  loadingContainer: {
    flex: 1,
    justifyContent: 'center',
    alignItems: 'center',
  },
  loadingText: {
    marginTop: 16,
    fontSize: 16,
    color: theme.colors.textLight,
  },
  header: {
    flexDirection: 'row',
    justifyContent: 'space-between',
    alignItems: 'center',
    paddingHorizontal: 20,
    paddingVertical: 16,
  },
  title: {
    fontSize: 28,
    fontWeight: 'bold',
    color: theme.colors.text,
  },
  creditScoreCard: {
    marginHorizontal: 20,
    marginBottom: 20,
    padding: 20,
    borderRadius: 16,
  },
  scoreHeader: {
    flexDirection: 'row',
    justifyContent: 'space-between',
    alignItems: 'center',
    marginBottom: 20,
  },
  scoreLabel: {
    fontSize: 16,
    color: '#FFFFFF',
    opacity: 0.9,
  },
  scoreContainer: {
    alignItems: 'center',
    marginBottom: 20,
  },
  scoreValue: {
    fontSize: 64,
    fontWeight: 'bold',
    color: '#FFFFFF',
  },
  scoreCategory: {
    fontSize: 20,
    color: '#FFFFFF',
    marginTop: 4,
  },
  scoreBar: {
    height: 8,
    backgroundColor: 'rgba(255, 255, 255, 0.3)',
    borderRadius: 4,
    marginBottom: 8,
  },
  scoreProgress: {
    height: '100%',
    backgroundColor: '#FFFFFF',
    borderRadius: 4,
  },
  scoreRangeLabels: {
    flexDirection: 'row',
    justifyContent: 'space-between',
    marginBottom: 20,
  },
  scoreRangeText: {
    fontSize: 12,
    color: '#FFFFFF',
    opacity: 0.8,
  },
  scoreChange: {
    flexDirection: 'row',
    alignItems: 'center',
    justifyContent: 'center',
    gap: 8,
    marginBottom: 16,
  },
  scoreChangeText: {
    fontSize: 14,
    color: '#FFFFFF',
  },
  viewAllScoresButton: {
    flexDirection: 'row',
    alignItems: 'center',
    justifyContent: 'center',
    gap: 4,
  },
  viewAllScoresText: {
    fontSize: 14,
    color: '#FFFFFF',
    fontWeight: '600',
  },
  progressSection: {
    paddingHorizontal: 20,
    marginBottom: 24,
  },
  progressStats: {
    flexDirection: 'row',
    justifyContent: 'space-around',
    marginBottom: 20,
  },
  progressStat: {
    alignItems: 'center',
  },
  progressStatLabel: {
    fontSize: 12,
    color: theme.colors.textLight,
    marginBottom: 4,
  },
  progressStatValue: {
    fontSize: 24,
    fontWeight: 'bold',
    color: theme.colors.text,
  },
  chart: {
    marginVertical: 8,
    borderRadius: 16,
  },
  tabContainer: {
    flexDirection: 'row',
    marginHorizontal: 20,
    marginBottom: 16,
    backgroundColor: theme.colors.surface,
    borderRadius: 12,
    padding: 4,
  },
  tab: {
    flex: 1,
    paddingVertical: 12,
    alignItems: 'center',
    borderRadius: 8,
  },
  activeTab: {
    backgroundColor: theme.colors.primary,
  },
  tabText: {
    fontSize: 14,
    fontWeight: '600',
    color: theme.colors.textLight,
  },
  activeTabText: {
    color: theme.colors.white,
  },
  sectionHeader: {
    flexDirection: 'row',
    justifyContent: 'space-between',
    alignItems: 'center',
    marginBottom: 16,
  },
  sectionTitle: {
    fontSize: 20,
    fontWeight: 'bold',
    color: theme.colors.text,
  },
  viewAllLink: {
    fontSize: 14,
    color: theme.colors.primary,
    fontWeight: '600',
  },
  addButton: {
    width: 32,
    height: 32,
    borderRadius: 16,
    backgroundColor: theme.colors.primaryLight + '20',
    justifyContent: 'center',
    alignItems: 'center',
  },
  accountsSection: {
    paddingHorizontal: 20,
    marginBottom: 24,
  },
  emptyAccountsCard: {
    backgroundColor: theme.colors.surface,
    padding: 24,
    borderRadius: 16,
    alignItems: 'center',
  },
  emptyAccountsTitle: {
    fontSize: 18,
    fontWeight: '600',
    color: theme.colors.text,
    marginTop: 16,
    marginBottom: 8,
  },
  emptyAccountsText: {
    fontSize: 14,
    color: theme.colors.textLight,
    textAlign: 'center',
    marginBottom: 20,
  },
  openAccountButton: {
    backgroundColor: theme.colors.primary,
    paddingHorizontal: 24,
    paddingVertical: 12,
    borderRadius: 8,
  },
  openAccountButtonText: {
    color: theme.colors.white,
    fontSize: 16,
    fontWeight: '600',
  },
  accountCard: {
    backgroundColor: theme.colors.surface,
    padding: 16,
    borderRadius: 12,
    marginBottom: 12,
  },
  accountHeader: {
    flexDirection: 'row',
    justifyContent: 'space-between',
    alignItems: 'center',
    marginBottom: 12,
  },
  accountType: {
    fontSize: 16,
    fontWeight: '600',
    color: theme.colors.text,
  },
  accountStatus: {
    paddingHorizontal: 8,
    paddingVertical: 4,
    borderRadius: 4,
  },
  accountStatusText: {
    fontSize: 12,
    color: '#FFFFFF',
    fontWeight: '600',
  },
  accountBalance: {
    flexDirection: 'row',
    justifyContent: 'space-between',
    marginBottom: 8,
  },
  accountBalanceLabel: {
    fontSize: 14,
    color: theme.colors.textLight,
  },
  accountBalanceValue: {
    fontSize: 14,
    fontWeight: '600',
    color: theme.colors.text,
  },
  accountPayment: {
    flexDirection: 'row',
    justifyContent: 'space-between',
    marginBottom: 12,
  },
  accountPaymentLabel: {
    fontSize: 14,
    color: theme.colors.textLight,
  },
  accountPaymentValue: {
    fontSize: 14,
    color: theme.colors.text,
  },
  accountImpact: {
    flexDirection: 'row',
    alignItems: 'center',
    gap: 6,
  },
  accountImpactText: {
    fontSize: 13,
    color: theme.colors.success,
    fontWeight: '500',
  },
  recommendationsSection: {
    paddingHorizontal: 20,
    marginBottom: 24,
  },
  recommendationCard: {
    flexDirection: 'row',
    backgroundColor: theme.colors.surface,
    borderRadius: 12,
    padding: 16,
    marginBottom: 12,
    gap: 12,
  },
  recommendationPriority: {
    width: 60,
    height: 24,
    borderRadius: 12,
    justifyContent: 'center',
    alignItems: 'center',
  },
  recommendationPriorityText: {
    fontSize: 11,
    color: '#FFFFFF',
    fontWeight: '600',
    textTransform: 'uppercase',
  },
  recommendationContent: {
    flex: 1,
  },
  recommendationTitle: {
    fontSize: 16,
    fontWeight: '600',
    color: theme.colors.text,
    marginBottom: 4,
  },
  recommendationDescription: {
    fontSize: 14,
    color: theme.colors.textLight,
    lineHeight: 20,
    marginBottom: 8,
  },
  recommendationImpact: {
    flexDirection: 'row',
    alignItems: 'center',
    justifyContent: 'space-between',
  },
  recommendationImpactText: {
    fontSize: 13,
    color: theme.colors.primary,
    fontWeight: '500',
  },
  alertsSection: {
    paddingHorizontal: 20,
    marginBottom: 16,
  },
  alertCard: {
    flexDirection: 'row',
    backgroundColor: theme.colors.surface,
    padding: 12,
    borderRadius: 8,
    marginBottom: 8,
    borderLeftWidth: 4,
    gap: 12,
    alignItems: 'center',
  },
  alertContent: {
    flex: 1,
  },
  alertTitle: {
    fontSize: 14,
    fontWeight: '600',
    color: theme.colors.text,
    marginBottom: 2,
  },
  alertMessage: {
    fontSize: 13,
    color: theme.colors.textLight,
    lineHeight: 18,
  },
  simulatorButton: {
    flexDirection: 'row',
    alignItems: 'center',
    justifyContent: 'center',
    backgroundColor: theme.colors.primary,
    marginHorizontal: 20,
    marginBottom: 20,
    padding: 16,
    borderRadius: 12,
    gap: 8,
  },
  simulatorButtonText: {
    fontSize: 16,
    fontWeight: '600',
    color: theme.colors.white,
  },
});