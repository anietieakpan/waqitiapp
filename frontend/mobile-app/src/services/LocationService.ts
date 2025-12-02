import Geolocation from '@react-native-community/geolocation';
import { Platform, PermissionsAndroid } from 'react-native';
import { check, request, PERMISSIONS, RESULTS } from 'react-native-permissions';
import AsyncStorage from '@react-native-async-storage/async-storage';
import { store } from '../store';
import { updateUserLocation, setNearbyMerchants } from '../store/slices/locationSlice';
import { ApiService } from './ApiService';
import { EventEmitter } from 'eventemitter3';
import haversine from 'haversine';

interface Location {
  latitude: number;
  longitude: number;
  accuracy?: number;
  altitude?: number;
  heading?: number;
  speed?: number;
  timestamp: number;
}

interface NearbyMerchant {
  id: string;
  name: string;
  category: string;
  location: {
    latitude: number;
    longitude: number;
    address?: string;
  };
  distance: number;
  rating?: number;
  acceptsWaqiti: boolean;
  promotions?: Array<{
    id: string;
    title: string;
    discount: number;
    validUntil: string;
  }>;
}

interface LocationServiceConfig {
  enableHighAccuracy: boolean;
  distanceFilter: number;
  interval: number;
  fastestInterval: number;
  showLocationDialog: boolean;
  forceRequestLocation: boolean;
}

class LocationService extends EventEmitter {
  private static instance: LocationService;
  private watchId: number | null = null;
  private isTracking: boolean = false;
  private lastLocation: Location | null = null;
  private locationHistory: Location[] = [];
  private nearbyMerchants: Map<string, NearbyMerchant> = new Map();
  private merchantUpdateInterval: NodeJS.Timeout | null = null;
  
  private config: LocationServiceConfig = {
    enableHighAccuracy: true,
    distanceFilter: 50, // Update every 50 meters
    interval: 10000, // 10 seconds
    fastestInterval: 5000, // 5 seconds
    showLocationDialog: true,
    forceRequestLocation: false,
  };

  private readonly MAX_HISTORY_SIZE = 100;
  private readonly NEARBY_RADIUS_METERS = 1000; // 1km
  private readonly LOCATION_CACHE_KEY = '@location_cache';
  private readonly PERMISSION_CACHE_KEY = '@location_permission';

  static getInstance(): LocationService {
    if (!LocationService.instance) {
      LocationService.instance = new LocationService();
    }
    return LocationService.instance;
  }

  constructor() {
    super();
    this.setupGeolocation();
  }

  private setupGeolocation(): void {
    Geolocation.setRNConfiguration({
      skipPermissionRequests: false,
      authorizationLevel: 'whenInUse',
      enableBackgroundLocationUpdates: false,
      locationProvider: 'auto',
    });
  }

  async initialize(): Promise<void> {
    try {
      // Load cached location
      await this.loadCachedLocation();
      
      // Check permission status
      const hasPermission = await this.checkLocationPermission();
      
      if (hasPermission) {
        // Get current location
        await this.getCurrentLocation();
        
        // Start merchant updates
        this.startMerchantUpdates();
      }
      
      this.emit('initialized', { hasPermission });
    } catch (error) {
      console.error('Failed to initialize location service:', error);
      this.emit('error', error);
    }
  }

  async checkLocationPermission(): Promise<boolean> {
    try {
      // Check cached permission
      const cachedPermission = await AsyncStorage.getItem(this.PERMISSION_CACHE_KEY);
      if (cachedPermission === 'granted') {
        return true;
      }

      let permission;
      if (Platform.OS === 'ios') {
        permission = PERMISSIONS.IOS.LOCATION_WHEN_IN_USE;
      } else {
        permission = PERMISSIONS.ANDROID.ACCESS_FINE_LOCATION;
      }

      const result = await check(permission);
      
      if (result === RESULTS.GRANTED) {
        await AsyncStorage.setItem(this.PERMISSION_CACHE_KEY, 'granted');
        return true;
      }

      return false;
    } catch (error) {
      console.error('Permission check failed:', error);
      return false;
    }
  }

  async requestLocationPermission(): Promise<boolean> {
    try {
      let permission;
      let rationale;

      if (Platform.OS === 'ios') {
        permission = PERMISSIONS.IOS.LOCATION_WHEN_IN_USE;
      } else {
        permission = PERMISSIONS.ANDROID.ACCESS_FINE_LOCATION;
        rationale = {
          title: 'Location Permission',
          message: 'Waqiti needs access to your location to show nearby merchants and enable location-based payments.',
          buttonNeutral: 'Ask Me Later',
          buttonNegative: 'Cancel',
          buttonPositive: 'OK',
        };
      }

      const result = await request(permission, rationale);
      
      if (result === RESULTS.GRANTED) {
        await AsyncStorage.setItem(this.PERMISSION_CACHE_KEY, 'granted');
        this.emit('permissionGranted');
        
        // Initialize after permission granted
        await this.initialize();
        
        return true;
      } else if (result === RESULTS.BLOCKED) {
        this.emit('permissionBlocked');
      } else {
        this.emit('permissionDenied');
      }

      return false;
    } catch (error) {
      console.error('Permission request failed:', error);
      return false;
    }
  }

  async getCurrentLocation(): Promise<Location | null> {
    return new Promise((resolve, reject) => {
      Geolocation.getCurrentPosition(
        async (position) => {
          const location: Location = {
            latitude: position.coords.latitude,
            longitude: position.coords.longitude,
            accuracy: position.coords.accuracy,
            altitude: position.coords.altitude || undefined,
            heading: position.coords.heading || undefined,
            speed: position.coords.speed || undefined,
            timestamp: position.timestamp,
          };

          this.lastLocation = location;
          await this.cacheLocation(location);
          
          // Update Redux store
          store.dispatch(updateUserLocation(location));
          
          // Add to history
          this.addToHistory(location);
          
          this.emit('locationUpdate', location);
          resolve(location);
        },
        (error) => {
          console.error('Failed to get current location:', error);
          this.emit('error', error);
          reject(error);
        },
        {
          enableHighAccuracy: this.config.enableHighAccuracy,
          timeout: 20000,
          maximumAge: 1000,
        }
      );
    });
  }

  startLocationTracking(): void {
    if (this.isTracking) return;

    this.watchId = Geolocation.watchPosition(
      async (position) => {
        const location: Location = {
          latitude: position.coords.latitude,
          longitude: position.coords.longitude,
          accuracy: position.coords.accuracy,
          altitude: position.coords.altitude || undefined,
          heading: position.coords.heading || undefined,
          speed: position.coords.speed || undefined,
          timestamp: position.timestamp,
        };

        // Check if location changed significantly
        if (this.hasLocationChangedSignificantly(location)) {
          this.lastLocation = location;
          await this.cacheLocation(location);
          
          // Update Redux store
          store.dispatch(updateUserLocation(location));
          
          // Add to history
          this.addToHistory(location);
          
          // Update nearby merchants
          await this.updateNearbyMerchants(location);
          
          this.emit('locationUpdate', location);
        }
      },
      (error) => {
        console.error('Location tracking error:', error);
        this.emit('error', error);
      },
      {
        enableHighAccuracy: this.config.enableHighAccuracy,
        distanceFilter: this.config.distanceFilter,
        interval: this.config.interval,
        fastestInterval: this.config.fastestInterval,
      }
    );

    this.isTracking = true;
    this.emit('trackingStarted');
  }

  stopLocationTracking(): void {
    if (this.watchId !== null) {
      Geolocation.clearWatch(this.watchId);
      this.watchId = null;
    }

    this.isTracking = false;
    this.emit('trackingStopped');
  }

  private hasLocationChangedSignificantly(newLocation: Location): boolean {
    if (!this.lastLocation) return true;

    const distance = this.calculateDistance(
      this.lastLocation,
      newLocation
    );

    return distance >= this.config.distanceFilter;
  }

  private calculateDistance(from: Location, to: Location): number {
    return haversine(
      { latitude: from.latitude, longitude: from.longitude },
      { latitude: to.latitude, longitude: to.longitude },
      { unit: 'meter' }
    );
  }

  private addToHistory(location: Location): void {
    this.locationHistory.push(location);
    
    // Keep only recent history
    if (this.locationHistory.length > this.MAX_HISTORY_SIZE) {
      this.locationHistory = this.locationHistory.slice(-this.MAX_HISTORY_SIZE);
    }
  }

  private async cacheLocation(location: Location): Promise<void> {
    try {
      await AsyncStorage.setItem(
        this.LOCATION_CACHE_KEY,
        JSON.stringify(location)
      );
    } catch (error) {
      console.error('Failed to cache location:', error);
    }
  }

  private async loadCachedLocation(): Promise<void> {
    try {
      const cached = await AsyncStorage.getItem(this.LOCATION_CACHE_KEY);
      if (cached) {
        this.lastLocation = JSON.parse(cached);
        store.dispatch(updateUserLocation(this.lastLocation));
      }
    } catch (error) {
      console.error('Failed to load cached location:', error);
    }
  }

  private startMerchantUpdates(): void {
    // Update merchants immediately
    if (this.lastLocation) {
      this.updateNearbyMerchants(this.lastLocation);
    }

    // Update periodically
    this.merchantUpdateInterval = setInterval(() => {
      if (this.lastLocation) {
        this.updateNearbyMerchants(this.lastLocation);
      }
    }, 60000); // Every minute
  }

  private async updateNearbyMerchants(location: Location): Promise<void> {
    try {
      const response = await ApiService.getNearbyMerchants({
        latitude: location.latitude,
        longitude: location.longitude,
        radius: this.NEARBY_RADIUS_METERS,
      });

      const merchants: NearbyMerchant[] = response.merchants.map((merchant: any) => ({
        ...merchant,
        distance: this.calculateDistance(location, merchant.location),
      }));

      // Sort by distance
      merchants.sort((a, b) => a.distance - b.distance);

      // Update map
      this.nearbyMerchants.clear();
      merchants.forEach(merchant => {
        this.nearbyMerchants.set(merchant.id, merchant);
      });

      // Update Redux store
      store.dispatch(setNearbyMerchants(merchants));
      
      this.emit('nearbyMerchantsUpdated', merchants);
    } catch (error) {
      console.error('Failed to update nearby merchants:', error);
    }
  }

  async searchNearbyPaymentLocations(
    category?: string,
    maxDistance?: number
  ): Promise<NearbyMerchant[]> {
    const merchants = Array.from(this.nearbyMerchants.values());
    
    let filtered = merchants;
    
    if (category) {
      filtered = filtered.filter(m => m.category === category);
    }
    
    if (maxDistance) {
      filtered = filtered.filter(m => m.distance <= maxDistance);
    }
    
    return filtered;
  }

  async enableNearbyPayments(): Promise<void> {
    if (!this.isTracking) {
      this.startLocationTracking();
    }
    
    // Enable nearby payment notifications
    await ApiService.updateUserSettings({
      nearbyPaymentsEnabled: true,
      nearbyPaymentRadius: this.NEARBY_RADIUS_METERS,
    });
    
    this.emit('nearbyPaymentsEnabled');
  }

  async disableNearbyPayments(): Promise<void> {
    await ApiService.updateUserSettings({
      nearbyPaymentsEnabled: false,
    });
    
    this.emit('nearbyPaymentsDisabled');
  }

  getLastLocation(): Location | null {
    return this.lastLocation;
  }

  getLocationHistory(): Location[] {
    return [...this.locationHistory];
  }

  getNearbyMerchants(): NearbyMerchant[] {
    return Array.from(this.nearbyMerchants.values());
  }

  isLocationTrackingActive(): boolean {
    return this.isTracking;
  }

  updateConfig(config: Partial<LocationServiceConfig>): void {
    this.config = { ...this.config, ...config };
    
    // Restart tracking with new config if active
    if (this.isTracking) {
      this.stopLocationTracking();
      this.startLocationTracking();
    }
  }

  async calculateDistanceToMerchant(merchantId: string): Promise<number | null> {
    const merchant = this.nearbyMerchants.get(merchantId);
    if (!merchant || !this.lastLocation) return null;
    
    return this.calculateDistance(this.lastLocation, merchant.location);
  }

  async getDirectionsToMerchant(merchantId: string): Promise<string> {
    const merchant = this.nearbyMerchants.get(merchantId);
    if (!merchant || !this.lastLocation) {
      throw new Error('Merchant or location not found');
    }
    
    // Generate deep link for maps app
    const origin = `${this.lastLocation.latitude},${this.lastLocation.longitude}`;
    const destination = `${merchant.location.latitude},${merchant.location.longitude}`;
    
    if (Platform.OS === 'ios') {
      return `maps://app?saddr=${origin}&daddr=${destination}`;
    } else {
      return `google.navigation:q=${destination}`;
    }
  }

  destroy(): void {
    this.stopLocationTracking();
    
    if (this.merchantUpdateInterval) {
      clearInterval(this.merchantUpdateInterval);
    }
    
    this.removeAllListeners();
  }
}

export default LocationService.getInstance();