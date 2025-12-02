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
  Modal,
  TextInput,
  Switch,
  ScrollView,
} from 'react-native';
import { SafeAreaView } from 'react-native-safe-area-context';
import Icon from 'react-native-vector-icons/MaterialCommunityIcons';
import LocationPaymentService, { SmartReminder } from '../../services/location/LocationPaymentService';
import { useNavigation } from '@react-navigation/native';

const LocationRemindersScreen: React.FC = () => {
  const navigation = useNavigation();
  const [reminders, setReminders] = useState<SmartReminder[]>([]);
  const [loading, setLoading] = useState(true);
  const [refreshing, setRefreshing] = useState(false);
  const [showCreateModal, setShowCreateModal] = useState(false);
  const [newReminder, setNewReminder] = useState({
    type: 'BILL_DUE' as SmartReminder['type'],
    title: '',
    message: '',
    isLocationBased: false,
    triggerRadius: 100,
    scheduledDate: null as Date | null,
  });
  const fadeAnim = useRef(new Animated.Value(0)).current;

  useEffect(() => {
    loadReminders();
    startFadeInAnimation();
  }, []);

  const loadReminders = async () => {
    try {
      // Since reminders are stored locally, we'll load them from AsyncStorage
      // In a real implementation, this would also sync with the backend
      const storedReminders = await getStoredReminders();
      setReminders(storedReminders);
    } catch (error) {
      console.error('Error loading reminders:', error);
      Alert.alert('Error', 'Failed to load reminders');
    } finally {
      setLoading(false);
    }
  };

  const getStoredReminders = async (): Promise<SmartReminder[]> => {
    // This would normally use AsyncStorage, but for demo purposes we'll return mock data
    return [
      {
        id: '1',
        type: 'BILL_DUE',
        title: 'Rent Payment Due',
        message: 'Monthly rent payment is due in 3 days',
        isLocationBased: false,
        scheduledDate: new Date(Date.now() + 3 * 24 * 60 * 60 * 1000),
        metadata: { amount: 1200, recipient: 'Property Manager' }
      },
      {
        id: '2',
        type: 'MERCHANT_VISIT',
        title: 'Coffee Shop Reminder',
        message: 'Remember to use your loyalty points at this coffee shop',
        isLocationBased: true,
        location: {
          latitude: 40.7128,
          longitude: -74.0060,
          accuracy: 10,
          timestamp: Date.now(),
          address: 'Local Coffee Shop'
        },
        triggerRadius: 50,
        metadata: { merchantId: 'coffee-123', loyaltyPoints: 150 }
      }
    ];
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
    await loadReminders();
    setRefreshing(false);
  };

  const createReminder = async () => {
    try {
      if (!newReminder.title.trim() || !newReminder.message.trim()) {
        Alert.alert('Error', 'Please fill in all required fields');
        return;
      }

      const reminderId = await LocationPaymentService.createLocationReminder({
        type: newReminder.type,
        title: newReminder.title,
        message: newReminder.message,
        isLocationBased: newReminder.isLocationBased,
        triggerRadius: newReminder.isLocationBased ? newReminder.triggerRadius : undefined,
        scheduledDate: newReminder.scheduledDate,
        location: newReminder.isLocationBased ? await LocationPaymentService.getCurrentLocation() : undefined,
      });

      setShowCreateModal(false);
      resetNewReminder();
      await loadReminders();

      Alert.alert('Success', 'Reminder created successfully');
    } catch (error) {
      console.error('Error creating reminder:', error);
      Alert.alert('Error', 'Failed to create reminder');
    }
  };

  const deleteReminder = (reminderId: string) => {
    Alert.alert(
      'Delete Reminder',
      'Are you sure you want to delete this reminder?',
      [
        { text: 'Cancel', style: 'cancel' },
        { 
          text: 'Delete', 
          style: 'destructive',
          onPress: () => performDeleteReminder(reminderId)
        }
      ]
    );
  };

  const performDeleteReminder = async (reminderId: string) => {
    try {
      // Remove from local state
      setReminders(prev => prev.filter(r => r.id !== reminderId));
      
      // In a real implementation, this would also delete from backend and remove geofences
      Alert.alert('Success', 'Reminder deleted');
    } catch (error) {
      console.error('Error deleting reminder:', error);
      Alert.alert('Error', 'Failed to delete reminder');
    }
  };

  const resetNewReminder = () => {
    setNewReminder({
      type: 'BILL_DUE',
      title: '',
      message: '',
      isLocationBased: false,
      triggerRadius: 100,
      scheduledDate: null,
    });
  };

  const getTypeIcon = (type: SmartReminder['type']): string => {
    switch (type) {
      case 'BILL_DUE': return 'receipt';
      case 'RECURRING_PAYMENT': return 'repeat';
      case 'MERCHANT_VISIT': return 'store';
      case 'FRIEND_NEARBY': return 'account-group';
      default: return 'bell';
    }
  };

  const getTypeColor = (type: SmartReminder['type']): string => {
    switch (type) {
      case 'BILL_DUE': return '#FF3B30';
      case 'RECURRING_PAYMENT': return '#007AFF';
      case 'MERCHANT_VISIT': return '#34C759';
      case 'FRIEND_NEARBY': return '#FF9500';
      default: return '#8E8E93';
    }
  };

  const formatReminderTime = (reminder: SmartReminder): string => {
    if (reminder.isLocationBased) {
      return `When near location (${reminder.triggerRadius}m)`;
    } else if (reminder.scheduledDate) {
      const date = new Date(reminder.scheduledDate);
      const now = new Date();
      const diffMs = date.getTime() - now.getTime();
      const diffDays = Math.floor(diffMs / (1000 * 60 * 60 * 24));
      
      if (diffDays > 0) {
        return `In ${diffDays} day${diffDays > 1 ? 's' : ''}`;
      } else if (diffDays === 0) {
        return 'Today';
      } else {
        return 'Overdue';
      }
    }
    return 'No schedule';
  };

  const renderReminder = ({ item: reminder }: { item: SmartReminder }) => (
    <Animated.View style={[styles.reminderCard, { opacity: fadeAnim }]}>
      <View style={styles.reminderHeader}>
        <View style={[styles.typeIcon, { backgroundColor: getTypeColor(reminder.type) + '20' }]}>
          <Icon name={getTypeIcon(reminder.type)} size={24} color={getTypeColor(reminder.type)} />
        </View>
        
        <View style={styles.reminderInfo}>
          <Text style={styles.reminderTitle}>{reminder.title}</Text>
          <Text style={styles.reminderMessage}>{reminder.message}</Text>
          
          <View style={styles.reminderDetails}>
            <View style={styles.detailItem}>
              <Icon 
                name={reminder.isLocationBased ? 'map-marker' : 'clock'} 
                size={12} 
                color="#666" 
              />
              <Text style={styles.detailText}>{formatReminderTime(reminder)}</Text>
            </View>
            
            {reminder.location?.address && (
              <View style={styles.detailItem}>
                <Icon name="home" size={12} color="#666" />
                <Text style={styles.detailText}>{reminder.location.address}</Text>
              </View>
            )}
          </View>
        </View>

        <TouchableOpacity 
          style={styles.deleteButton}
          onPress={() => deleteReminder(reminder.id)}
        >
          <Icon name="delete" size={20} color="#FF3B30" />
        </TouchableOpacity>
      </View>

      {reminder.metadata && (
        <View style={styles.metadataContainer}>
          {reminder.metadata.amount && (
            <View style={styles.metadataItem}>
              <Text style={styles.metadataLabel}>Amount:</Text>
              <Text style={styles.metadataValue}>${reminder.metadata.amount}</Text>
            </View>
          )}
          {reminder.metadata.recipient && (
            <View style={styles.metadataItem}>
              <Text style={styles.metadataLabel}>To:</Text>
              <Text style={styles.metadataValue}>{reminder.metadata.recipient}</Text>
            </View>
          )}
        </View>
      )}
    </Animated.View>
  );

  const renderCreateModal = () => (
    <Modal
      visible={showCreateModal}
      animationType="slide"
      presentationStyle="pageSheet"
      onRequestClose={() => setShowCreateModal(false)}
    >
      <SafeAreaView style={styles.modalContainer}>
        <View style={styles.modalHeader}>
          <TouchableOpacity onPress={() => setShowCreateModal(false)}>
            <Text style={styles.cancelButton}>Cancel</Text>
          </TouchableOpacity>
          <Text style={styles.modalTitle}>New Reminder</Text>
          <TouchableOpacity onPress={createReminder}>
            <Text style={styles.saveButton}>Save</Text>
          </TouchableOpacity>
        </View>

        <ScrollView style={styles.modalContent}>
          {/* Reminder Type */}
          <View style={styles.inputGroup}>
            <Text style={styles.inputLabel}>Type</Text>
            <View style={styles.typeSelector}>
              {[
                { type: 'BILL_DUE', label: 'Bill Due' },
                { type: 'RECURRING_PAYMENT', label: 'Recurring Payment' },
                { type: 'MERCHANT_VISIT', label: 'Merchant Visit' },
                { type: 'FRIEND_NEARBY', label: 'Friend Nearby' },
              ].map(({ type, label }) => (
                <TouchableOpacity
                  key={type}
                  style={[
                    styles.typeOption,
                    newReminder.type === type && styles.typeOptionSelected
                  ]}
                  onPress={() => setNewReminder({ ...newReminder, type: type as SmartReminder['type'] })}
                >
                  <Icon 
                    name={getTypeIcon(type as SmartReminder['type'])} 
                    size={16} 
                    color={newReminder.type === type ? '#FFF' : '#666'} 
                  />
                  <Text style={[
                    styles.typeOptionText,
                    newReminder.type === type && styles.typeOptionTextSelected
                  ]}>
                    {label}
                  </Text>
                </TouchableOpacity>
              ))}
            </View>
          </View>

          {/* Title */}
          <View style={styles.inputGroup}>
            <Text style={styles.inputLabel}>Title *</Text>
            <TextInput
              style={styles.textInput}
              value={newReminder.title}
              onChangeText={(text) => setNewReminder({ ...newReminder, title: text })}
              placeholder="Enter reminder title"
              maxLength={100}
            />
          </View>

          {/* Message */}
          <View style={styles.inputGroup}>
            <Text style={styles.inputLabel}>Message *</Text>
            <TextInput
              style={[styles.textInput, styles.multilineInput]}
              value={newReminder.message}
              onChangeText={(text) => setNewReminder({ ...newReminder, message: text })}
              placeholder="Enter reminder message"
              multiline
              numberOfLines={3}
              maxLength={500}
            />
          </View>

          {/* Location-based toggle */}
          <View style={styles.inputGroup}>
            <View style={styles.switchRow}>
              <Text style={styles.inputLabel}>Location-based reminder</Text>
              <Switch
                value={newReminder.isLocationBased}
                onValueChange={(value) => setNewReminder({ ...newReminder, isLocationBased: value })}
                trackColor={{ false: '#E5E5E7', true: '#007AFF' }}
              />
            </View>
            <Text style={styles.helpText}>
              Trigger this reminder when you're near a specific location
            </Text>
          </View>

          {/* Trigger radius (if location-based) */}
          {newReminder.isLocationBased && (
            <View style={styles.inputGroup}>
              <Text style={styles.inputLabel}>Trigger radius (meters)</Text>
              <View style={styles.radiusSelector}>
                {[50, 100, 200, 500].map((radius) => (
                  <TouchableOpacity
                    key={radius}
                    style={[
                      styles.radiusOption,
                      newReminder.triggerRadius === radius && styles.radiusOptionSelected
                    ]}
                    onPress={() => setNewReminder({ ...newReminder, triggerRadius: radius })}
                  >
                    <Text style={[
                      styles.radiusOptionText,
                      newReminder.triggerRadius === radius && styles.radiusOptionTextSelected
                    ]}>
                      {radius}m
                    </Text>
                  </TouchableOpacity>
                ))}
              </View>
            </View>
          )}

          {/* Scheduled date (if not location-based) */}
          {!newReminder.isLocationBased && (
            <View style={styles.inputGroup}>
              <Text style={styles.inputLabel}>Scheduled date (optional)</Text>
              <TouchableOpacity style={styles.dateButton}>
                <Icon name="calendar" size={16} color="#666" />
                <Text style={styles.dateButtonText}>
                  {newReminder.scheduledDate 
                    ? newReminder.scheduledDate.toLocaleDateString()
                    : 'Select date'
                  }
                </Text>
              </TouchableOpacity>
            </View>
          )}
        </ScrollView>
      </SafeAreaView>
    </Modal>
  );

  const renderEmptyState = () => (
    <View style={styles.emptyState}>
      <Icon name="bell-off" size={64} color="#CCC" />
      <Text style={styles.emptyStateTitle}>No reminders set</Text>
      <Text style={styles.emptyStateText}>
        Create smart reminders that trigger based on location or time to never miss important payments or visits.
      </Text>
      <TouchableOpacity style={styles.createFirstButton} onPress={() => setShowCreateModal(true)}>
        <Text style={styles.createFirstButtonText}>Create First Reminder</Text>
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
        <Text style={styles.title}>Smart Reminders</Text>
        <TouchableOpacity onPress={() => setShowCreateModal(true)} style={styles.addButton}>
          <Icon name="plus" size={24} color="#007AFF" />
        </TouchableOpacity>
      </View>

      {/* Reminders List */}
      {loading ? (
        <View style={styles.loadingContainer}>
          <Icon name="loading" size={32} color="#007AFF" />
          <Text style={styles.loadingText}>Loading reminders...</Text>
        </View>
      ) : (
        <FlatList
          data={reminders}
          renderItem={renderReminder}
          keyExtractor={(item) => item.id}
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
          Location-based reminders require background location access
        </Text>
      </View>

      {/* Create Modal */}
      {renderCreateModal()}
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
  addButton: {
    padding: 8,
  },
  listContainer: {
    padding: 16,
    flexGrow: 1,
  },
  reminderCard: {
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
  reminderHeader: {
    flexDirection: 'row',
    alignItems: 'flex-start',
  },
  typeIcon: {
    width: 48,
    height: 48,
    borderRadius: 24,
    alignItems: 'center',
    justifyContent: 'center',
    marginRight: 12,
  },
  reminderInfo: {
    flex: 1,
  },
  reminderTitle: {
    fontSize: 16,
    fontWeight: '600',
    color: '#000',
    marginBottom: 4,
  },
  reminderMessage: {
    fontSize: 14,
    color: '#666',
    lineHeight: 20,
    marginBottom: 8,
  },
  reminderDetails: {
    gap: 4,
  },
  detailItem: {
    flexDirection: 'row',
    alignItems: 'center',
  },
  detailText: {
    fontSize: 12,
    color: '#666',
    marginLeft: 6,
  },
  deleteButton: {
    padding: 4,
  },
  metadataContainer: {
    marginTop: 12,
    paddingTop: 12,
    borderTopWidth: 1,
    borderTopColor: '#F0F0F0',
    flexDirection: 'row',
    flexWrap: 'wrap',
    gap: 12,
  },
  metadataItem: {
    flexDirection: 'row',
    alignItems: 'center',
  },
  metadataLabel: {
    fontSize: 12,
    color: '#666',
    marginRight: 4,
  },
  metadataValue: {
    fontSize: 12,
    fontWeight: '600',
    color: '#000',
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
    flex: 1,
    alignItems: 'center',
    justifyContent: 'center',
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
  createFirstButton: {
    backgroundColor: '#007AFF',
    paddingHorizontal: 24,
    paddingVertical: 12,
    borderRadius: 8,
  },
  createFirstButtonText: {
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
  // Modal styles
  modalContainer: {
    flex: 1,
    backgroundColor: '#F8F9FA',
  },
  modalHeader: {
    flexDirection: 'row',
    alignItems: 'center',
    justifyContent: 'space-between',
    paddingHorizontal: 16,
    paddingVertical: 12,
    borderBottomWidth: 1,
    borderBottomColor: '#E5E5E7',
    backgroundColor: '#FFF',
  },
  cancelButton: {
    fontSize: 16,
    color: '#FF3B30',
  },
  modalTitle: {
    fontSize: 18,
    fontWeight: '600',
    color: '#000',
  },
  saveButton: {
    fontSize: 16,
    color: '#007AFF',
    fontWeight: '600',
  },
  modalContent: {
    flex: 1,
    padding: 16,
  },
  inputGroup: {
    marginBottom: 24,
  },
  inputLabel: {
    fontSize: 16,
    fontWeight: '500',
    color: '#000',
    marginBottom: 8,
  },
  textInput: {
    backgroundColor: '#FFF',
    borderWidth: 1,
    borderColor: '#E5E5E7',
    borderRadius: 8,
    paddingHorizontal: 12,
    paddingVertical: 12,
    fontSize: 16,
  },
  multilineInput: {
    height: 80,
    textAlignVertical: 'top',
  },
  switchRow: {
    flexDirection: 'row',
    justifyContent: 'space-between',
    alignItems: 'center',
  },
  helpText: {
    fontSize: 14,
    color: '#666',
    marginTop: 4,
  },
  typeSelector: {
    flexDirection: 'row',
    flexWrap: 'wrap',
    gap: 8,
  },
  typeOption: {
    flexDirection: 'row',
    alignItems: 'center',
    backgroundColor: '#F0F0F0',
    paddingHorizontal: 12,
    paddingVertical: 8,
    borderRadius: 20,
  },
  typeOptionSelected: {
    backgroundColor: '#007AFF',
  },
  typeOptionText: {
    fontSize: 14,
    color: '#666',
    marginLeft: 6,
  },
  typeOptionTextSelected: {
    color: '#FFF',
  },
  radiusSelector: {
    flexDirection: 'row',
    gap: 8,
  },
  radiusOption: {
    flex: 1,
    backgroundColor: '#F0F0F0',
    paddingVertical: 12,
    borderRadius: 8,
    alignItems: 'center',
  },
  radiusOptionSelected: {
    backgroundColor: '#007AFF',
  },
  radiusOptionText: {
    fontSize: 14,
    color: '#666',
  },
  radiusOptionTextSelected: {
    color: '#FFF',
  },
  dateButton: {
    flexDirection: 'row',
    alignItems: 'center',
    backgroundColor: '#FFF',
    borderWidth: 1,
    borderColor: '#E5E5E7',
    borderRadius: 8,
    paddingHorizontal: 12,
    paddingVertical: 12,
  },
  dateButtonText: {
    fontSize: 16,
    color: '#666',
    marginLeft: 8,
  },
});

export default LocationRemindersScreen;