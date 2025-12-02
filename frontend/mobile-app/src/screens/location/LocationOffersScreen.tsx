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
  Dimensions,
} from 'react-native';
import { SafeAreaView } from 'react-native-safe-area-context';
import Icon from 'react-native-vector-icons/MaterialCommunityIcons';
import LocationPaymentService, { LocationBasedOffer } from '../../services/location/LocationPaymentService';
import { useNavigation } from '@react-navigation/native';

const { width } = Dimensions.get('window');

const LocationOffersScreen: React.FC = () => {
  const navigation = useNavigation();
  const [offers, setOffers] = useState<LocationBasedOffer[]>([]);
  const [loading, setLoading] = useState(true);
  const [refreshing, setRefreshing] = useState(false);
  const [searchRadius, setSearchRadius] = useState(500); // 500m default for offers
  const fadeAnim = useRef(new Animated.Value(0)).current;

  useEffect(() => {
    initializeLocationService();
    startFadeInAnimation();
  }, []);

  const initializeLocationService = async () => {
    try {
      const initialized = await LocationPaymentService.initialize();
      if (initialized) {
        await loadLocationOffers();
      } else {
        Alert.alert(
          'Location Required',
          'This feature requires location access to show nearby offers.',
          [{ text: 'OK', onPress: () => navigation.goBack() }]
        );
      }
    } catch (error) {
      console.error('Failed to initialize location service:', error);
      Alert.alert('Error', 'Failed to load location offers');
    } finally {
      setLoading(false);
    }
  };

  const loadLocationOffers = async () => {
    try {
      const locationOffers = await LocationPaymentService.getLocationBasedOffers(searchRadius);
      setOffers(locationOffers);
    } catch (error) {
      console.error('Error loading location offers:', error);
      Alert.alert('Error', 'Failed to load offers');
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
    await loadLocationOffers();
    setRefreshing(false);
  };

  const redeemOffer = (offer: LocationBasedOffer) => {
    Alert.alert(
      'Redeem Offer',
      `Are you sure you want to redeem "${offer.title}" at this location?`,
      [
        { text: 'Cancel', style: 'cancel' },
        { 
          text: 'Redeem', 
          onPress: () => processOfferRedemption(offer)
        }
      ]
    );
  };

  const processOfferRedemption = async (offer: LocationBasedOffer) => {
    try {
      // Navigate to payment screen with offer details
      navigation.navigate('NFCPayment', {
        mode: 'customer',
        merchantId: offer.merchantId,
        offerId: offer.offerId,
        amount: offer.minPurchaseAmount || 0,
      });
    } catch (error) {
      console.error('Error processing offer redemption:', error);
      Alert.alert('Error', 'Failed to process offer redemption');
    }
  };

  const getDirections = (offer: LocationBasedOffer) => {
    navigation.navigate('NearbyMerchants');
  };

  const formatDistance = (distance: number): string => {
    if (distance < 1000) {
      return `${Math.round(distance)}m away`;
    } else {
      return `${(distance / 1000).toFixed(1)}km away`;
    }
  };

  const formatExpiryTime = (validUntil: Date): string => {
    const now = new Date();
    const expiry = new Date(validUntil);
    const diffMs = expiry.getTime() - now.getTime();
    const diffHours = Math.floor(diffMs / (1000 * 60 * 60));
    const diffDays = Math.floor(diffHours / 24);

    if (diffDays > 0) {
      return `Expires in ${diffDays} day${diffDays > 1 ? 's' : ''}`;
    } else if (diffHours > 0) {
      return `Expires in ${diffHours} hour${diffHours > 1 ? 's' : ''}`;
    } else {
      const diffMinutes = Math.floor(diffMs / (1000 * 60));
      return `Expires in ${diffMinutes} minute${diffMinutes > 1 ? 's' : ''}`;
    }
  };

  const getDiscountText = (offer: LocationBasedOffer): string => {
    if (offer.discountPercent) {
      return `${offer.discountPercent}% OFF`;
    } else if (offer.discountAmount) {
      return `$${offer.discountAmount} OFF`;
    }
    return 'SPECIAL OFFER';
  };

  const getCategoryColor = (category: string): string => {
    const categoryColors: { [key: string]: string } = {
      'restaurant': '#FF6B6B',
      'cafe': '#8B4513',
      'retail': '#4ECDC4',
      'entertainment': '#45B7D1',
      'fitness': '#96CEB4',
      'beauty': '#FFEAA7',
      'automotive': '#DDA0DD',
      'grocery': '#98D8C8',
      'default': '#A8A8A8'
    };
    return categoryColors[category.toLowerCase()] || categoryColors.default;
  };

  const getAvailabilityColor = (offer: LocationBasedOffer): string => {
    const availabilityRatio = (offer.maxRedemptions - offer.currentRedemptions) / offer.maxRedemptions;
    if (availabilityRatio > 0.5) return '#34C759';
    if (availabilityRatio > 0.2) return '#FF9500';
    return '#FF3B30';
  };

  const getAvailabilityText = (offer: LocationBasedOffer): string => {
    const remaining = offer.maxRedemptions - offer.currentRedemptions;
    if (remaining <= 0) return 'Sold Out';
    if (remaining === 1) return '1 left';
    if (remaining <= 5) return `${remaining} left`;
    return 'Available';
  };

  const changeRadius = (newRadius: number) => {
    setSearchRadius(newRadius);
    setLoading(true);
    setTimeout(() => {
      loadLocationOffers().finally(() => setLoading(false));
    }, 500);
  };

  const renderOffer = ({ item: offer }: { item: LocationBasedOffer }) => {
    const isExpiringSoon = new Date(offer.validUntil).getTime() - Date.now() < 24 * 60 * 60 * 1000;
    const isLowStock = (offer.maxRedemptions - offer.currentRedemptions) <= 5;

    return (
      <Animated.View style={[styles.offerCard, { opacity: fadeAnim }]}>
        {/* Discount Badge */}
        <View style={[styles.discountBadge, { backgroundColor: getCategoryColor(offer.category) }]}>
          <Text style={styles.discountText}>{getDiscountText(offer)}</Text>
        </View>

        {/* Urgency Indicator */}
        {(isExpiringSoon || isLowStock) && (
          <View style={styles.urgencyIndicator}>
            <Icon name="clock-alert" size={12} color="#FF3B30" />
            <Text style={styles.urgencyText}>
              {isExpiringSoon ? 'Expires Soon' : 'Limited'}
            </Text>
          </View>
        )}

        <View style={styles.offerHeader}>
          <View style={styles.offerTitleContainer}>
            <Text style={styles.offerTitle}>{offer.title}</Text>
            <Text style={styles.offerDescription}>{offer.description}</Text>
          </View>
          
          <View style={styles.availabilityContainer}>
            <View style={[styles.availabilityDot, { backgroundColor: getAvailabilityColor(offer) }]} />
            <Text style={[styles.availabilityText, { color: getAvailabilityColor(offer) }]}>
              {getAvailabilityText(offer)}
            </Text>
          </View>
        </View>

        {/* Offer Details */}
        <View style={styles.offerDetails}>
          <View style={styles.detailRow}>
            <Icon name="map-marker" size={14} color="#666" />
            <Text style={styles.detailText}>{formatDistance(offer.distance)}</Text>
          </View>
          
          <View style={styles.detailRow}>
            <Icon name="clock" size={14} color="#666" />
            <Text style={styles.detailText}>{formatExpiryTime(offer.validUntil)}</Text>
          </View>
          
          {offer.minPurchaseAmount && (
            <View style={styles.detailRow}>
              <Icon name="cash" size={14} color="#666" />
              <Text style={styles.detailText}>Min. purchase: ${offer.minPurchaseAmount}</Text>
            </View>
          )}
        </View>

        {/* Category Tag */}
        <View style={styles.categoryContainer}>
          <View style={[styles.categoryTag, { backgroundColor: getCategoryColor(offer.category) + '20' }]}>
            <Text style={[styles.categoryText, { color: getCategoryColor(offer.category) }]}>
              {offer.category}
            </Text>
          </View>
        </View>

        {/* Action Buttons */}
        <View style={styles.actionButtons}>
          <TouchableOpacity 
            style={[styles.actionButton, styles.directionsButton]} 
            onPress={() => getDirections(offer)}
          >
            <Icon name="directions" size={16} color="#007AFF" />
            <Text style={[styles.actionButtonText, { color: '#007AFF' }]}>Directions</Text>
          </TouchableOpacity>

          <TouchableOpacity 
            style={[
              styles.actionButton, 
              styles.redeemButton,
              (offer.maxRedemptions - offer.currentRedemptions) <= 0 && styles.disabledButton
            ]} 
            onPress={() => redeemOffer(offer)}
            disabled={(offer.maxRedemptions - offer.currentRedemptions) <= 0}
          >
            <Icon 
              name={(offer.maxRedemptions - offer.currentRedemptions) <= 0 ? "close-circle" : "tag"} 
              size={16} 
              color="#FFF" 
            />
            <Text style={styles.actionButtonText}>
              {(offer.maxRedemptions - offer.currentRedemptions) <= 0 ? 'Sold Out' : 'Redeem'}
            </Text>
          </TouchableOpacity>
        </View>
      </Animated.View>
    );
  };

  const renderRadiusSelector = () => (
    <View style={styles.radiusSelector}>
      <Text style={styles.radiusLabel}>Search radius:</Text>
      <View style={styles.radiusButtons}>
        {[250, 500, 1000, 2000].map((radius) => (
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
      <Icon name="tag-off" size={64} color="#CCC" />
      <Text style={styles.emptyStateTitle}>No offers available</Text>
      <Text style={styles.emptyStateText}>
        No special offers found in your area right now.
        Check back later or expand your search radius.
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
        <Text style={styles.title}>Location Offers</Text>
        <TouchableOpacity onPress={onRefresh} style={styles.refreshButton}>
          <Icon name="refresh" size={24} color="#007AFF" />
        </TouchableOpacity>
      </View>

      {/* Search Radius Selector */}
      {renderRadiusSelector()}

      {/* Active Offers Count */}
      {!loading && offers.length > 0 && (
        <View style={styles.offersCount}>
          <Icon name="tag-multiple" size={16} color="#007AFF" />
          <Text style={styles.offersCountText}>
            {offers.length} offer{offers.length > 1 ? 's' : ''} available nearby
          </Text>
        </View>
      )}

      {/* Offers List */}
      {loading ? (
        <View style={styles.loadingContainer}>
          <Icon name="loading" size={32} color="#007AFF" />
          <Text style={styles.loadingText}>Finding offers near you...</Text>
        </View>
      ) : (
        <FlatList
          data={offers}
          renderItem={renderOffer}
          keyExtractor={(item) => item.offerId}
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
          Offers update automatically based on your location
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
  offersCount: {
    flexDirection: 'row',
    alignItems: 'center',
    justifyContent: 'center',
    backgroundColor: '#F0F8FF',
    paddingVertical: 8,
  },
  offersCountText: {
    fontSize: 14,
    fontWeight: '500',
    color: '#007AFF',
    marginLeft: 6,
  },
  listContainer: {
    padding: 16,
  },
  offerCard: {
    backgroundColor: '#FFF',
    borderRadius: 16,
    padding: 16,
    marginBottom: 16,
    elevation: 3,
    shadowColor: '#000',
    shadowOffset: { width: 0, height: 2 },
    shadowOpacity: 0.15,
    shadowRadius: 6,
    position: 'relative',
    overflow: 'hidden',
  },
  discountBadge: {
    position: 'absolute',
    top: 0,
    right: 0,
    paddingHorizontal: 12,
    paddingVertical: 6,
    borderBottomLeftRadius: 12,
    zIndex: 1,
  },
  discountText: {
    color: '#FFF',
    fontSize: 12,
    fontWeight: 'bold',
  },
  urgencyIndicator: {
    position: 'absolute',
    top: 12,
    left: 12,
    flexDirection: 'row',
    alignItems: 'center',
    backgroundColor: '#FFE5E5',
    paddingHorizontal: 6,
    paddingVertical: 2,
    borderRadius: 8,
    zIndex: 1,
  },
  urgencyText: {
    fontSize: 10,
    color: '#FF3B30',
    fontWeight: '600',
    marginLeft: 2,
  },
  offerHeader: {
    flexDirection: 'row',
    justifyContent: 'space-between',
    alignItems: 'flex-start',
    marginBottom: 12,
    marginTop: 8,
  },
  offerTitleContainer: {
    flex: 1,
    marginRight: 12,
  },
  offerTitle: {
    fontSize: 18,
    fontWeight: '700',
    color: '#000',
    marginBottom: 4,
  },
  offerDescription: {
    fontSize: 14,
    color: '#666',
    lineHeight: 20,
  },
  availabilityContainer: {
    flexDirection: 'row',
    alignItems: 'center',
  },
  availabilityDot: {
    width: 8,
    height: 8,
    borderRadius: 4,
    marginRight: 6,
  },
  availabilityText: {
    fontSize: 12,
    fontWeight: '600',
  },
  offerDetails: {
    marginBottom: 12,
  },
  detailRow: {
    flexDirection: 'row',
    alignItems: 'center',
    marginBottom: 4,
  },
  detailText: {
    fontSize: 12,
    color: '#666',
    marginLeft: 6,
  },
  categoryContainer: {
    marginBottom: 16,
  },
  categoryTag: {
    alignSelf: 'flex-start',
    paddingHorizontal: 8,
    paddingVertical: 4,
    borderRadius: 12,
  },
  categoryText: {
    fontSize: 11,
    fontWeight: '600',
    textTransform: 'uppercase',
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
    paddingVertical: 12,
    borderRadius: 10,
    marginHorizontal: 4,
  },
  directionsButton: {
    backgroundColor: '#F0F8FF',
    borderWidth: 1,
    borderColor: '#007AFF',
  },
  redeemButton: {
    backgroundColor: '#007AFF',
  },
  disabledButton: {
    backgroundColor: '#CCC',
  },
  actionButtonText: {
    color: '#FFF',
    fontSize: 14,
    fontWeight: '600',
    marginLeft: 6,
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

export default LocationOffersScreen;