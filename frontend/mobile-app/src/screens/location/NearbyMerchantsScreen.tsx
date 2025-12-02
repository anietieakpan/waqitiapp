import React, { useState, useEffect, useRef } from 'react';
import {
  View,
  Text,
  StyleSheet,
  FlatList,
  TouchableOpacity,
  Alert,
  RefreshControl,
  Animated,
  Image,
  Linking,
  Platform,
} from 'react-native';
import { SafeAreaView } from 'react-native-safe-area-context';
import Icon from 'react-native-vector-icons/MaterialCommunityIcons';
import LocationPaymentService, { NearbyMerchant } from '../../services/location/LocationPaymentService';
import { useNavigation } from '@react-navigation/native';

const NearbyMerchantsScreen: React.FC = () => {
  const navigation = useNavigation();
  const [merchants, setMerchants] = useState<NearbyMerchant[]>([]);
  const [loading, setLoading] = useState(true);
  const [refreshing, setRefreshing] = useState(false);
  const [searchRadius, setSearchRadius] = useState(1000); // 1km default
  const fadeAnim = useRef(new Animated.Value(0)).current;

  useEffect(() => {
    initializeLocationService();
    startFadeInAnimation();
  }, []);

  const initializeLocationService = async () => {
    try {
      const initialized = await LocationPaymentService.initialize();
      if (initialized) {
        await loadNearbyMerchants();
      } else {
        Alert.alert(
          'Location Required',
          'This feature requires location access. Please enable location services.',
          [{ text: 'OK', onPress: () => navigation.goBack() }]
        );
      }
    } catch (error) {
      console.error('Failed to initialize location service:', error);
      Alert.alert('Error', 'Failed to load nearby merchants');
    } finally {
      setLoading(false);
    }
  };

  const loadNearbyMerchants = async () => {
    try {
      const nearbyMerchants = await LocationPaymentService.findNearbyMerchants(searchRadius);
      setMerchants(nearbyMerchants);
    } catch (error) {
      console.error('Error loading nearby merchants:', error);
      Alert.alert('Error', 'Failed to load nearby merchants');
    }
  };

  const startFadeInAnimation = () => {
    Animated.timing(fadeAnim, {
      toValue: 1,
      duration: 800,
      useNativeDriver: true,
    }).start();
  };

  const onRefresh = async () => {
    setRefreshing(true);
    await loadNearbyMerchants();
    setRefreshing(false);
  };

  const navigateToMerchant = (merchant: NearbyMerchant) => {
    const url = Platform.select({
      ios: `maps:0,0?q=${merchant.location.latitude},${merchant.location.longitude}`,
      android: `geo:0,0?q=${merchant.location.latitude},${merchant.location.longitude}(${merchant.businessName})`
    });

    Linking.openURL(url).catch(() => {
      Alert.alert('Error', 'Could not open maps application');
    });
  };

  const payMerchant = (merchant: NearbyMerchant) => {
    navigation.navigate('NFCPayment', {
      mode: 'customer',
      merchantId: merchant.merchantId,
    });
  };

  const viewOffers = (merchant: NearbyMerchant) => {
    navigation.navigate('MerchantOffers', {
      merchantId: merchant.merchantId,
      businessName: merchant.businessName,
    });
  };

  const formatDistance = (distance: number): string => {
    if (distance < 1000) {
      return `${Math.round(distance)}m`;
    } else {
      return `${(distance / 1000).toFixed(1)}km`;
    }
  };

  const getCategoryIcon = (category: string): string => {
    const categoryIcons: { [key: string]: string } = {
      'restaurant': 'silverware-fork-knife',
      'cafe': 'coffee',
      'retail': 'shopping',
      'gas': 'gas-station',
      'grocery': 'cart',
      'pharmacy': 'pharmacy',
      'entertainment': 'movie',
      'fitness': 'dumbbell',
      'beauty': 'scissors-cutting',
      'automotive': 'car',
      'default': 'store'
    };
    return categoryIcons[category.toLowerCase()] || categoryIcons.default;
  };

  const getRatingStars = (rating: number) => {
    const stars = [];
    const fullStars = Math.floor(rating);
    const hasHalfStar = rating % 1 !== 0;

    for (let i = 0; i < fullStars; i++) {
      stars.push(<Icon key={i} name="star" size={12} color="#FFD700" />);
    }

    if (hasHalfStar) {
      stars.push(<Icon key="half" name="star-half-full" size={12} color="#FFD700" />);
    }

    const emptyStars = 5 - Math.ceil(rating);
    for (let i = 0; i < emptyStars; i++) {
      stars.push(<Icon key={`empty-${i}`} name="star-outline" size={12} color="#DDD" />);
    }

    return stars;
  };

  const changeRadius = (newRadius: number) => {
    setSearchRadius(newRadius);
    setLoading(true);
    setTimeout(() => {
      loadNearbyMerchants().finally(() => setLoading(false));
    }, 500);
  };

  const renderMerchant = ({ item: merchant }: { item: NearbyMerchant }) => (
    <Animated.View style={[styles.merchantCard, { opacity: fadeAnim }]}>
      <View style={styles.merchantHeader}>
        <View style={styles.merchantIcon}>
          <Icon name={getCategoryIcon(merchant.category)} size={24} color="#007AFF" />
        </View>
        <View style={styles.merchantInfo}>
          <Text style={styles.merchantName}>{merchant.businessName}</Text>
          <Text style={styles.merchantCategory}>{merchant.category}</Text>
          <View style={styles.ratingContainer}>
            <View style={styles.starsContainer}>
              {getRatingStars(merchant.rating)}
            </View>
            <Text style={styles.ratingText}>({merchant.rating.toFixed(1)})</Text>
          </View>
        </View>
        <View style={styles.distanceContainer}>
          <Text style={styles.distanceText}>{formatDistance(merchant.distance)}</Text>
          {merchant.acceptsWaqiti && (
            <View style={styles.waqitiBadge}>
              <Icon name="check-circle" size={12} color="#34C759" />
              <Text style={styles.waqitiText}>Waqiti</Text>
            </View>
          )}
        </View>
      </View>

      {merchant.currentOffers && merchant.currentOffers.length > 0 && (
        <View style={styles.offersContainer}>
          <Icon name="tag" size={14} color="#FF9500" />
          <Text style={styles.offersText}>
            {merchant.currentOffers.length} special offer{merchant.currentOffers.length > 1 ? 's' : ''} available
          </Text>
        </View>
      )}

      {merchant.averageTransactionAmount && (
        <View style={styles.avgAmountContainer}>
          <Text style={styles.avgAmountLabel}>Avg. spend:</Text>
          <Text style={styles.avgAmountValue}>${merchant.averageTransactionAmount.toFixed(0)}</Text>
        </View>
      )}

      <View style={styles.actionButtons}>
        <TouchableOpacity 
          style={[styles.actionButton, styles.navigateButton]} 
          onPress={() => navigateToMerchant(merchant)}
        >
          <Icon name="directions" size={16} color="#FFF" />
          <Text style={styles.actionButtonText}>Navigate</Text>
        </TouchableOpacity>

        {merchant.acceptsWaqiti && (
          <TouchableOpacity 
            style={[styles.actionButton, styles.payButton]} 
            onPress={() => payMerchant(merchant)}
          >
            <Icon name="contactless-payment" size={16} color="#FFF" />
            <Text style={styles.actionButtonText}>Pay</Text>
          </TouchableOpacity>
        )}

        {merchant.currentOffers && merchant.currentOffers.length > 0 && (
          <TouchableOpacity 
            style={[styles.actionButton, styles.offersButton]} 
            onPress={() => viewOffers(merchant)}
          >
            <Icon name="tag" size={16} color="#FFF" />
            <Text style={styles.actionButtonText}>Offers</Text>
          </TouchableOpacity>
        )}
      </View>
    </Animated.View>
  );

  const renderRadiusSelector = () => (
    <View style={styles.radiusSelector}>
      <Text style={styles.radiusLabel}>Search radius:</Text>
      <View style={styles.radiusButtons}>
        {[500, 1000, 2000, 5000].map((radius) => (
          <TouchableOpacity
            key={radius}
            style={[
              styles.radiusButton,
              searchRadius === radius && styles.radiusButtonActive
            ]}
            onPress={() => changeRadius(radius)}
          >
            <Text style={[
              styles.radiusButtonText,
              searchRadius === radius && styles.radiusButtonTextActive
            ]}>
              {radius < 1000 ? `${radius}m` : `${radius / 1000}km`}
            </Text>
          </TouchableOpacity>
        ))}
      </View>
    </View>
  );

  const renderEmptyState = () => (
    <View style={styles.emptyState}>
      <Icon name="store-off" size={64} color="#CCC" />
      <Text style={styles.emptyStateTitle}>No merchants found</Text>
      <Text style={styles.emptyStateText}>
        No merchants accepting Waqiti payments found in your area.
        Try expanding your search radius.
      </Text>
      <TouchableOpacity style={styles.expandButton} onPress={() => changeRadius(searchRadius * 2)}>
        <Text style={styles.expandButtonText}>Expand Search</Text>
      </TouchableOpacity>
    </View>
  );

  return (
    <SafeAreaView style={styles.container}>
      {/* Header */}
      <View style={styles.header}>
        <TouchableOpacity onPress={() => navigation.goBack()} style={styles.backButton}>
          <Icon name="arrow-left" size={24} color="#000" />
        </TouchableOpacity>
        <Text style={styles.title}>Nearby Merchants</Text>
        <TouchableOpacity onPress={onRefresh} style={styles.refreshButton}>
          <Icon name="refresh" size={24} color="#007AFF" />
        </TouchableOpacity>
      </View>

      {/* Search Radius Selector */}
      {renderRadiusSelector()}

      {/* Merchants List */}
      {loading ? (
        <View style={styles.loadingContainer}>
          <Icon name="loading" size={32} color="#007AFF" />
          <Text style={styles.loadingText}>Finding nearby merchants...</Text>
        </View>
      ) : (
        <FlatList
          data={merchants}
          renderItem={renderMerchant}
          keyExtractor={(item) => item.merchantId}
          contentContainerStyle={styles.listContainer}
          showsVerticalScrollIndicator={false}
          refreshControl={
            <RefreshControl
              refreshing={refreshing}
              onRefresh={onRefresh}
              colors={['#007AFF']}
              tintColor="#007AFF"
            />
          }
          ListEmptyComponent={renderEmptyState}
        />
      )}

      {/* Footer Info */}
      <View style={styles.footer}>
        <Icon name="information" size={14} color="#666" />
        <Text style={styles.footerText}>
          Showing merchants that accept Waqiti payments within {formatDistance(searchRadius)}
        </Text>
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
  backButton: {
    padding: 8,
  },
  title: {
    fontSize: 18,
    fontWeight: '600',
    color: '#000',
  },
  refreshButton: {
    padding: 8,
  },
  radiusSelector: {
    backgroundColor: '#FFF',
    paddingHorizontal: 16,
    paddingVertical: 12,
    borderBottomWidth: 1,
    borderBottomColor: '#E5E5E7',
  },
  radiusLabel: {
    fontSize: 14,
    fontWeight: '500',
    color: '#666',
    marginBottom: 8,
  },
  radiusButtons: {
    flexDirection: 'row',
    justifyContent: 'space-between',
  },
  radiusButton: {
    flex: 1,
    marginHorizontal: 4,
    paddingVertical: 8,
    borderRadius: 8,
    backgroundColor: '#F0F0F0',
    alignItems: 'center',
  },
  radiusButtonActive: {
    backgroundColor: '#007AFF',
  },
  radiusButtonText: {
    fontSize: 12,
    fontWeight: '500',
    color: '#666',
  },
  radiusButtonTextActive: {
    color: '#FFF',
  },
  listContainer: {
    padding: 16,
  },
  merchantCard: {
    backgroundColor: '#FFF',
    borderRadius: 12,
    padding: 16,
    marginBottom: 12,
    elevation: 2,
    shadowColor: '#000',
    shadowOffset: { width: 0, height: 2 },
    shadowOpacity: 0.1,
    shadowRadius: 4,
  },
  merchantHeader: {
    flexDirection: 'row',
    alignItems: 'flex-start',
    marginBottom: 12,
  },
  merchantIcon: {
    width: 48,
    height: 48,
    borderRadius: 24,
    backgroundColor: '#F0F8FF',
    alignItems: 'center',
    justifyContent: 'center',
    marginRight: 12,
  },
  merchantInfo: {
    flex: 1,
  },
  merchantName: {
    fontSize: 16,
    fontWeight: '600',
    color: '#000',
    marginBottom: 2,
  },
  merchantCategory: {
    fontSize: 14,
    color: '#666',
    marginBottom: 4,
  },
  ratingContainer: {
    flexDirection: 'row',
    alignItems: 'center',
  },
  starsContainer: {
    flexDirection: 'row',
    marginRight: 4,
  },
  ratingText: {
    fontSize: 12,
    color: '#666',
  },
  distanceContainer: {
    alignItems: 'flex-end',
  },
  distanceText: {
    fontSize: 14,
    fontWeight: '500',
    color: '#007AFF',
    marginBottom: 4,
  },
  waqitiBadge: {
    flexDirection: 'row',
    alignItems: 'center',
    backgroundColor: '#E8F5E8',
    paddingHorizontal: 6,
    paddingVertical: 2,
    borderRadius: 8,
  },
  waqitiText: {
    fontSize: 10,
    fontWeight: '500',
    color: '#34C759',
    marginLeft: 2,
  },
  offersContainer: {
    flexDirection: 'row',
    alignItems: 'center',
    backgroundColor: '#FFF8E1',
    paddingHorizontal: 8,
    paddingVertical: 4,
    borderRadius: 6,
    marginBottom: 8,
  },
  offersText: {
    fontSize: 12,
    color: '#F57C00',
    marginLeft: 4,
    fontWeight: '500',
  },
  avgAmountContainer: {
    flexDirection: 'row',
    alignItems: 'center',
    marginBottom: 12,
  },
  avgAmountLabel: {
    fontSize: 12,
    color: '#666',
  },
  avgAmountValue: {
    fontSize: 12,
    fontWeight: '600',
    color: '#000',
    marginLeft: 4,
  },
  actionButtons: {
    flexDirection: 'row',
    justifyContent: 'space-between',
  },
  actionButton: {
    flex: 1,
    flexDirection: 'row',
    alignItems: 'center',
    justifyContent: 'center',
    paddingVertical: 8,
    borderRadius: 8,
    marginHorizontal: 2,
  },
  navigateButton: {
    backgroundColor: '#34C759',
  },
  payButton: {
    backgroundColor: '#007AFF',
  },
  offersButton: {
    backgroundColor: '#FF9500',
  },
  actionButtonText: {
    color: '#FFF',
    fontSize: 12,
    fontWeight: '600',
    marginLeft: 4,
  },
  loadingContainer: {
    flex: 1,
    alignItems: 'center',
    justifyContent: 'center',
  },
  loadingText: {
    marginTop: 12,
    fontSize: 16,
    color: '#666',
  },
  emptyState: {
    alignItems: 'center',
    justifyContent: 'center',
    paddingVertical: 64,
    paddingHorizontal: 32,
  },
  emptyStateTitle: {
    fontSize: 18,
    fontWeight: '600',
    color: '#000',
    marginTop: 16,
    marginBottom: 8,
  },
  emptyStateText: {
    fontSize: 14,
    color: '#666',
    textAlign: 'center',
    lineHeight: 20,
    marginBottom: 24,
  },
  expandButton: {
    backgroundColor: '#007AFF',
    paddingHorizontal: 24,
    paddingVertical: 12,
    borderRadius: 8,
  },
  expandButtonText: {
    color: '#FFF',
    fontSize: 14,
    fontWeight: '600',
  },
  footer: {
    flexDirection: 'row',
    alignItems: 'center',
    justifyContent: 'center',
    backgroundColor: '#FFF',
    paddingVertical: 12,
    paddingHorizontal: 16,
    borderTopWidth: 1,
    borderTopColor: '#E5E5E7',
  },
  footerText: {
    fontSize: 12,
    color: '#666',
    marginLeft: 6,
    textAlign: 'center',
  },
});

export default NearbyMerchantsScreen;