import Geolocation from '@react-native-community/geolocation';
import AsyncStorage from '@react-native-async-storage/async-storage';
import { Alert, Platform, PermissionsAndroid } from 'react-native';
import VaultConfigService from '../config/VaultConfigService';

export interface LocationData {
  latitude: number;
  longitude: number;
  accuracy: number;
  timestamp: number;
  address?: string;
}

export interface GeoFence {
  id: string;
  center: {
    latitude: number;
    longitude: number;
  };
  radius: number; // in meters
  type: 'MERCHANT' | 'ATM' | 'FRIEND' | 'CUSTOM';
  metadata?: any;
}

export interface NearbyMerchant {
  merchantId: string;
  businessName: string;
  category: string;
  rating: number;
  distance: number; // in meters
  location: LocationData;
  acceptsWaqiti: boolean;
  currentOffers?: any[];
  averageTransactionAmount?: number;
  imageUrl?: string;
}

export interface LocationBasedOffer {
  offerId: string;
  merchantId: string;
  title: string;
  description: string;
  discountPercent?: number;
  discountAmount?: number;
  validUntil: Date;
  maxRedemptions: number;
  currentRedemptions: number;
  minPurchaseAmount?: number;
  category: string;
  location: LocationData;
  radius: number;
}

export interface SmartReminder {
  id: string;
  type: 'BILL_DUE' | 'RECURRING_PAYMENT' | 'MERCHANT_VISIT' | 'FRIEND_NEARBY';
  title: string;
  message: string;
  location?: LocationData;
  triggerRadius?: number;
  scheduledDate?: Date;
  isLocationBased: boolean;
  metadata?: any;
}

class LocationPaymentService {
  private currentLocation: LocationData | null = null;
  private watchId: number | null = null;
  private geoFences: GeoFence[] = [];
  private isTracking = false;

  async initialize(): Promise<boolean> {
    try {
      const hasPermission = await this.requestLocationPermission();
      if (!hasPermission) {
        console.warn('Location permission denied');
        return false;
      }

      await this.loadGeoFences();
      console.log('Location Payment Service initialized successfully');
      return true;
    } catch (error) {
      console.error('Failed to initialize Location Payment Service:', error);
      return false;
    }
  }

  private async requestLocationPermission(): Promise<boolean> {
    if (Platform.OS === 'android') {
      try {
        const granted = await PermissionsAndroid.requestMultiple([
          PermissionsAndroid.PERMISSIONS.ACCESS_FINE_LOCATION,
          PermissionsAndroid.PERMISSIONS.ACCESS_COARSE_LOCATION,
        ]);

        return (
          granted[PermissionsAndroid.PERMISSIONS.ACCESS_FINE_LOCATION] === 'granted' ||
          granted[PermissionsAndroid.PERMISSIONS.ACCESS_COARSE_LOCATION] === 'granted'
        );
      } catch (error) {
        console.error('Permission request failed:', error);
        return false;
      }
    }
    return true; // iOS handles permissions automatically
  }

  async getCurrentLocation(): Promise<LocationData> {
    return new Promise((resolve, reject) => {
      Geolocation.getCurrentPosition(
        async (position) => {
          const locationData: LocationData = {
            latitude: position.coords.latitude,
            longitude: position.coords.longitude,
            accuracy: position.coords.accuracy,
            timestamp: position.timestamp,
            address: await this.reverseGeocode(position.coords.latitude, position.coords.longitude)
          };
          
          this.currentLocation = locationData;
          resolve(locationData);
        },
        (error) => {
          console.error('Error getting location:', error);
          reject(error);
        },
        {
          enableHighAccuracy: true,
          timeout: 15000,
          maximumAge: 10000,
        }
      );
    });
  }

  async startLocationTracking(): Promise<void> {
    if (this.isTracking) return;

    try {
      this.watchId = Geolocation.watchPosition(
        async (position) => {
          const newLocation: LocationData = {
            latitude: position.coords.latitude,
            longitude: position.coords.longitude,
            accuracy: position.coords.accuracy,
            timestamp: position.timestamp,
            address: await this.reverseGeocode(position.coords.latitude, position.coords.longitude)
          };

          this.currentLocation = newLocation;
          await this.checkGeoFences(newLocation);
          await this.checkLocationBasedOffers(newLocation);
          await this.checkSmartReminders(newLocation);
        },
        (error) => {
          console.error('Location tracking error:', error);
        },
        {
          enableHighAccuracy: true,
          timeout: 10000,
          maximumAge: 5000,
          distanceFilter: 10, // Only trigger on 10m+ movement
        }
      );

      this.isTracking = true;
      console.log('Location tracking started');
    } catch (error) {
      console.error('Failed to start location tracking:', error);
      throw error;
    }
  }

  async stopLocationTracking(): Promise<void> {
    if (this.watchId !== null) {
      Geolocation.clearWatch(this.watchId);
      this.watchId = null;
      this.isTracking = false;
      console.log('Location tracking stopped');
    }
  }

  // Nearby Merchants Discovery
  async findNearbyMerchants(radius: number = 1000): Promise<NearbyMerchant[]> {
    if (!this.currentLocation) {
      await this.getCurrentLocation();
    }

    try {
      const response = await fetch('/api/merchants/nearby', {
        method: 'POST',
        headers: { 'Content-Type': 'application/json' },
        body: JSON.stringify({
          latitude: this.currentLocation!.latitude,
          longitude: this.currentLocation!.longitude,
          radius,
          acceptsWaqiti: true
        })
      });

      const merchants: NearbyMerchant[] = await response.json();
      
      // Calculate distances and sort
      return merchants
        .map(merchant => ({
          ...merchant,
          distance: this.calculateDistance(
            this.currentLocation!.latitude,
            this.currentLocation!.longitude,
            merchant.location.latitude,
            merchant.location.longitude
          )
        }))
        .sort((a, b) => a.distance - b.distance);
    } catch (error) {
      console.error('Error finding nearby merchants:', error);
      return [];
    }
  }

  // Location-Based Offers
  async getLocationBasedOffers(radius: number = 500): Promise<LocationBasedOffer[]> {
    if (!this.currentLocation) {
      await this.getCurrentLocation();
    }

    try {
      const response = await fetch('/api/offers/location-based', {
        method: 'POST',
        headers: { 'Content-Type': 'application/json' },
        body: JSON.stringify({
          latitude: this.currentLocation!.latitude,
          longitude: this.currentLocation!.longitude,
          radius
        })
      });

      const offers: LocationBasedOffer[] = await response.json();
      
      // Filter active offers and calculate distances
      return offers
        .filter(offer => new Date(offer.validUntil) > new Date())
        .filter(offer => offer.currentRedemptions < offer.maxRedemptions)
        .map(offer => ({
          ...offer,
          distance: this.calculateDistance(
            this.currentLocation!.latitude,
            this.currentLocation!.longitude,
            offer.location.latitude,
            offer.location.longitude
          )
        }))
        .sort((a, b) => a.distance - b.distance);
    } catch (error) {
      console.error('Error getting location-based offers:', error);
      return [];
    }
  }

  // Smart Location Reminders
  async createLocationReminder(reminder: Omit<SmartReminder, 'id'>): Promise<string> {
    const id = `reminder_${Date.now()}_${Math.random().toString(36).substr(2, 9)}`;
    const fullReminder: SmartReminder = { id, ...reminder };

    try {
      // Save to local storage
      const existingReminders = await this.getStoredReminders();
      existingReminders.push(fullReminder);
      await AsyncStorage.setItem('location_reminders', JSON.stringify(existingReminders));

      // Create geofence if location-based
      if (reminder.isLocationBased && reminder.location && reminder.triggerRadius) {
        await this.addGeoFence({
          id: `reminder_${id}`,
          center: {
            latitude: reminder.location.latitude,
            longitude: reminder.location.longitude
          },
          radius: reminder.triggerRadius,
          type: 'CUSTOM',
          metadata: { reminderId: id, type: 'reminder' }
        });
      }

      // Sync with backend
      await fetch('/api/reminders/location', {
        method: 'POST',
        headers: { 'Content-Type': 'application/json' },
        body: JSON.stringify(fullReminder)
      });

      console.log('Location reminder created:', id);
      return id;
    } catch (error) {
      console.error('Error creating location reminder:', error);
      throw error;
    }
  }

  // Automatic Check-in for Frequent Locations
  async enableAutoCheckin(merchantId: string, location: LocationData): Promise<void> {
    try {
      await this.addGeoFence({
        id: `checkin_${merchantId}`,
        center: {
          latitude: location.latitude,
          longitude: location.longitude
        },
        radius: 50, // 50 meter radius
        type: 'MERCHANT',
        metadata: { merchantId, autoCheckin: true }
      });

      console.log('Auto check-in enabled for merchant:', merchantId);
    } catch (error) {
      console.error('Error enabling auto check-in:', error);
      throw error;
    }
  }

  // Friend Proximity Detection
  async enableFriendProximityAlerts(): Promise<void> {
    try {
      // Get friends' locations (with permission)
      const response = await fetch('/api/friends/locations');
      const friendsData = await response.json();

      for (const friend of friendsData) {
        if (friend.shareLocation) {
          await this.addGeoFence({
            id: `friend_${friend.userId}`,
            center: {
              latitude: friend.location.latitude,
              longitude: friend.location.longitude
            },
            radius: 200, // 200 meter radius
            type: 'FRIEND',
            metadata: { 
              friendId: friend.userId, 
              friendName: friend.displayName,
              lastUpdated: friend.location.timestamp
            }
          });
        }
      }

      console.log('Friend proximity alerts enabled');
    } catch (error) {
      console.error('Error enabling friend proximity alerts:', error);
    }
  }

  // Location-Based Bill Splitting
  async suggestBillSplit(location: LocationData): Promise<any[]> {
    try {
      // Find friends nearby
      const nearbyFriends = await this.findNearbyFriends(location, 100); // 100m radius
      
      if (nearbyFriends.length === 0) return [];

      // Get recent transactions at this location
      const merchantAtLocation = await this.getMerchantAtLocation(location);
      
      if (!merchantAtLocation) return [];

      return nearbyFriends.map(friend => ({
        friendId: friend.userId,
        friendName: friend.displayName,
        distance: friend.distance,
        suggestedSplitAmount: null, // To be filled when user enters total
        merchantId: merchantAtLocation.merchantId,
        merchantName: merchantAtLocation.businessName
      }));
    } catch (error) {
      console.error('Error suggesting bill split:', error);
      return [];
    }
  }

  // Location Analytics
  async getLocationSpendingAnalytics(): Promise<any> {
    try {
      const response = await fetch('/api/analytics/location-spending');
      const analytics = await response.json();
      
      return {
        topSpendingLocations: analytics.topLocations,
        categorySpending: analytics.categories,
        frequentMerchants: analytics.merchants,
        monthlyLocationTrends: analytics.trends,
        averageSpendingByLocation: analytics.averages
      };
    } catch (error) {
      console.error('Error getting location analytics:', error);
      return null;
    }
  }

  // Private Helper Methods
  private async reverseGeocode(latitude: number, longitude: number): Promise<string> {
    try {
      // Check if Google Maps is configured
      const isConfigured = await VaultConfigService.isGoogleMapsConfigured();
      if (!isConfigured) {
        console.warn('Google Maps API not configured, using coordinates only');
        return `${latitude.toFixed(4)}, ${longitude.toFixed(4)}`;
      }

      // Get API key from Vault
      const apiKey = await VaultConfigService.getGoogleMapsApiKey();
      
      const response = await fetch(
        `https://maps.googleapis.com/maps/api/geocode/json?latlng=${latitude},${longitude}&key=${apiKey}`
      );
      
      const data = await response.json();
      
      // Check for API errors
      if (data.status === 'REQUEST_DENIED') {
        console.error('Google Maps API request denied:', data.error_message);
        throw new Error('Google Maps API access denied');
      }
      
      if (data.status === 'OVER_QUERY_LIMIT') {
        console.warn('Google Maps API quota exceeded');
        throw new Error('Google Maps API quota exceeded');
      }
      
      if (data.results && data.results.length > 0) {
        return data.results[0].formatted_address;
      }
      
      return `${latitude.toFixed(4)}, ${longitude.toFixed(4)}`;
    } catch (error) {
      console.error('Reverse geocoding failed:', error);
      
      // Fallback to coordinates if geocoding fails
      return `${latitude.toFixed(4)}, ${longitude.toFixed(4)}`;
    }
  }

  private calculateDistance(lat1: number, lon1: number, lat2: number, lon2: number): number {
    const R = 6371e3; // Earth's radius in meters
    const φ1 = lat1 * Math.PI/180;
    const φ2 = lat2 * Math.PI/180;
    const Δφ = (lat2-lat1) * Math.PI/180;
    const Δλ = (lon2-lon1) * Math.PI/180;

    const a = Math.sin(Δφ/2) * Math.sin(Δφ/2) +
              Math.cos(φ1) * Math.cos(φ2) *
              Math.sin(Δλ/2) * Math.sin(Δλ/2);
    const c = 2 * Math.atan2(Math.sqrt(a), Math.sqrt(1-a));

    return R * c; // Distance in meters
  }

  private async loadGeoFences(): Promise<void> {
    try {
      const stored = await AsyncStorage.getItem('geofences');
      if (stored) {
        this.geoFences = JSON.parse(stored);
      }
    } catch (error) {
      console.error('Error loading geofences:', error);
    }
  }

  private async saveGeoFences(): Promise<void> {
    try {
      await AsyncStorage.setItem('geofences', JSON.stringify(this.geoFences));
    } catch (error) {
      console.error('Error saving geofences:', error);
    }
  }

  private async addGeoFence(geoFence: GeoFence): Promise<void> {
    this.geoFences.push(geoFence);
    await this.saveGeoFences();
  }

  private async checkGeoFences(location: LocationData): Promise<void> {
    for (const geoFence of this.geoFences) {
      const distance = this.calculateDistance(
        location.latitude,
        location.longitude,
        geoFence.center.latitude,
        geoFence.center.longitude
      );

      if (distance <= geoFence.radius) {
        await this.handleGeoFenceEntry(geoFence, location);
      }
    }
  }

  private async handleGeoFenceEntry(geoFence: GeoFence, location: LocationData): Promise<void> {
    console.log('Entered geofence:', geoFence.id);

    switch (geoFence.type) {
      case 'MERCHANT':
        if (geoFence.metadata?.autoCheckin) {
          await this.performAutoCheckin(geoFence.metadata.merchantId, location);
        }
        break;
      case 'FRIEND':
        await this.notifyFriendProximity(geoFence.metadata);
        break;
      case 'CUSTOM':
        if (geoFence.metadata?.type === 'reminder') {
          await this.triggerLocationReminder(geoFence.metadata.reminderId);
        }
        break;
    }
  }

  private async performAutoCheckin(merchantId: string, location: LocationData): Promise<void> {
    try {
      await fetch('/api/checkins', {
        method: 'POST',
        headers: { 'Content-Type': 'application/json' },
        body: JSON.stringify({
          merchantId,
          location,
          automatic: true,
          timestamp: Date.now()
        })
      });

      // Show subtle notification
      Alert.alert(
        'Auto Check-in',
        'You\'ve been automatically checked in!',
        [{ text: 'OK' }]
      );
    } catch (error) {
      console.error('Auto check-in failed:', error);
    }
  }

  private async notifyFriendProximity(friendData: any): Promise<void> {
    Alert.alert(
      'Friend Nearby!',
      `${friendData.friendName} is nearby. Want to send them money or split a bill?`,
      [
        { text: 'Later', style: 'cancel' },
        { text: 'Send Money', onPress: () => this.openSendMoney(friendData.friendId) },
        { text: 'Split Bill', onPress: () => this.openBillSplit(friendData.friendId) }
      ]
    );
  }

  private async triggerLocationReminder(reminderId: string): Promise<void> {
    const reminders = await this.getStoredReminders();
    const reminder = reminders.find(r => r.id === reminderId);
    
    if (reminder) {
      Alert.alert(
        reminder.title,
        reminder.message,
        [{ text: 'Dismiss' }, { text: 'Take Action', onPress: () => this.handleReminderAction(reminder) }]
      );
    }
  }

  private async checkLocationBasedOffers(location: LocationData): Promise<void> {
    const offers = await this.getLocationBasedOffers(100);
    
    if (offers.length > 0) {
      const topOffer = offers[0];
      Alert.alert(
        'Special Offer Nearby!',
        `${topOffer.title} at ${topOffer.description}`,
        [
          { text: 'Ignore', style: 'cancel' },
          { text: 'View Offer', onPress: () => this.showOfferDetails(topOffer) }
        ]
      );
    }
  }

  private async checkSmartReminders(location: LocationData): Promise<void> {
    // Check for contextual reminders based on location
    const reminders = await this.getStoredReminders();
    const contextualReminders = reminders.filter(r => 
      r.isLocationBased && this.isWithinReminderRange(location, r)
    );

    for (const reminder of contextualReminders) {
      await this.triggerLocationReminder(reminder.id);
    }
  }

  private isWithinReminderRange(location: LocationData, reminder: SmartReminder): boolean {
    if (!reminder.location || !reminder.triggerRadius) return false;
    
    const distance = this.calculateDistance(
      location.latitude,
      location.longitude,
      reminder.location.latitude,
      reminder.location.longitude
    );
    
    return distance <= reminder.triggerRadius;
  }

  private async getStoredReminders(): Promise<SmartReminder[]> {
    try {
      const stored = await AsyncStorage.getItem('location_reminders');
      return stored ? JSON.parse(stored) : [];
    } catch (error) {
      console.error('Error getting stored reminders:', error);
      return [];
    }
  }

  private async findNearbyFriends(location: LocationData, radius: number): Promise<any[]> {
    try {
      const response = await fetch('/api/friends/nearby', {
        method: 'POST',
        headers: { 'Content-Type': 'application/json' },
        body: JSON.stringify({
          latitude: location.latitude,
          longitude: location.longitude,
          radius
        })
      });
      return response.json();
    } catch (error) {
      console.error('Error finding nearby friends:', error);
      return [];
    }
  }

  private async getMerchantAtLocation(location: LocationData): Promise<any> {
    const merchants = await this.findNearbyMerchants(50); // 50m radius
    return merchants.length > 0 ? merchants[0] : null;
  }

  // Navigation helpers
  private openSendMoney(friendId: string): void {
    // Navigate to send money screen with pre-filled recipient
    console.log('Opening send money for friend:', friendId);
  }

  private openBillSplit(friendId: string): void {
    // Navigate to bill split screen with pre-filled participant
    console.log('Opening bill split for friend:', friendId);
  }

  private showOfferDetails(offer: LocationBasedOffer): void {
    // Show offer details modal
    console.log('Showing offer details:', offer);
  }

  private handleReminderAction(reminder: SmartReminder): void {
    // Handle reminder action based on type
    console.log('Handling reminder action:', reminder);
  }
}

export default new LocationPaymentService();