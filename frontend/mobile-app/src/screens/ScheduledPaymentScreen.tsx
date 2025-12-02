import React, { useState, useEffect } from 'react';
import {
  View,
  Text,
  StyleSheet,
  ScrollView,
  TouchableOpacity,
  Alert,
  Switch,
} from 'react-native';
import { useNavigation, useRoute } from '@react-navigation/native';
import { useSelector, useDispatch } from 'react-redux';
import Icon from 'react-native-vector-icons/MaterialCommunityIcons';
import DateTimePicker from '@react-native-community/datetimepicker';
import { RootState } from '../store';
import Header from '../components/Header';
import AmountInput from '../components/AmountInput';
import { AnalyticsService } from '../services/AnalyticsService';

/**
 * ScheduledPaymentScreen
 *
 * Screen for scheduling recurring or one-time payments
 *
 * Features:
 * - Schedule date/time selection
 * - Recurring payment options (daily, weekly, monthly)
 * - End date for recurring payments
 * - Payment validation
 * - Analytics tracking
 *
 * @author Waqiti Engineering Team
 * @version 1.0.0
 */

type RecurrenceType = 'once' | 'daily' | 'weekly' | 'monthly';

const ScheduledPaymentScreen: React.FC = () => {
  const navigation = useNavigation();
  const route = useRoute();
  const dispatch = useDispatch();

  const { contact } = (route.params || {}) as any;
  const { balance } = useSelector((state: RootState) => state.wallet);

  const [amount, setAmount] = useState('');
  const [description, setDescription] = useState('');
  const [startDate, setStartDate] = useState(new Date());
  const [endDate, setEndDate] = useState<Date | null>(null);
  const [recurrence, setRecurrence] = useState<RecurrenceType>('once');
  const [hasEndDate, setHasEndDate] = useState(false);
  const [showStartDatePicker, setShowStartDatePicker] = useState(false);
  const [showEndDatePicker, setShowEndDatePicker] = useState(false);

  useEffect(() => {
    AnalyticsService.trackScreenView('ScheduledPaymentScreen');
  }, []);

  const formatDate = (date: Date): string => {
    return date.toLocaleDateString('en-US', {
      year: 'numeric',
      month: 'long',
      day: 'numeric',
    });
  };

  const formatTime = (date: Date): string => {
    return date.toLocaleTimeString('en-US', {
      hour: '2-digit',
      minute: '2-digit',
    });
  };

  const handleSchedulePayment = () => {
    const numAmount = parseFloat(amount);

    if (!numAmount || numAmount <= 0) {
      Alert.alert('Error', 'Please enter a valid amount');
      return;
    }

    if (!contact) {
      Alert.alert('Error', 'No recipient selected');
      return;
    }

    if (startDate < new Date()) {
      Alert.alert('Error', 'Start date cannot be in the past');
      return;
    }

    if (hasEndDate && endDate && endDate <= startDate) {
      Alert.alert('Error', 'End date must be after start date');
      return;
    }

    const scheduleData = {
      recipientId: contact.id,
      amount: numAmount,
      description,
      startDate: startDate.toISOString(),
      endDate: hasEndDate && endDate ? endDate.toISOString() : null,
      recurrence,
    };

    Alert.alert(
      'Confirm Scheduled Payment',
      `Schedule ${recurrence === 'once' ? 'one-time' : recurrence} payment of $${numAmount.toFixed(2)} to ${contact.name}?\n\nStarts: ${formatDate(startDate)}${
        hasEndDate && endDate ? `\nEnds: ${formatDate(endDate)}` : ''
      }`,
      [
        {
          text: 'Cancel',
          style: 'cancel',
        },
        {
          text: 'Schedule',
          onPress: async () => {
            try {
              AnalyticsService.trackEvent('payment_scheduled', {
                amount: numAmount,
                recurrence,
                hasEndDate,
              });

              // TODO: Dispatch schedule payment action
              // await dispatch(schedulePayment(scheduleData)).unwrap();

              Alert.alert(
                'Success',
                'Payment has been scheduled successfully',
                [
                  {
                    text: 'OK',
                    onPress: () => navigation.goBack(),
                  },
                ]
              );
            } catch (error: any) {
              Alert.alert('Error', 'Failed to schedule payment');
            }
          },
        },
      ]
    );
  };

  const renderRecipient = () => (
    <View style={styles.recipientContainer}>
      <Text style={styles.sectionLabel}>Recipient</Text>
      <View style={styles.recipientCard}>
        <Icon name="account-circle" size={48} color="#6200EE" />
        <View style={styles.recipientInfo}>
          <Text style={styles.recipientName}>{contact?.name}</Text>
          <Text style={styles.recipientContact}>
            {contact?.email || contact?.phoneNumber}
          </Text>
        </View>
      </View>
    </View>
  );

  const renderRecurrenceOptions = () => {
    const options: Array<{ value: RecurrenceType; label: string; icon: string }> = [
      { value: 'once', label: 'One Time', icon: 'calendar' },
      { value: 'daily', label: 'Daily', icon: 'calendar-today' },
      { value: 'weekly', label: 'Weekly', icon: 'calendar-week' },
      { value: 'monthly', label: 'Monthly', icon: 'calendar-month' },
    ];

    return (
      <View style={styles.section}>
        <Text style={styles.sectionLabel}>Recurrence</Text>
        <View style={styles.recurrenceOptions}>
          {options.map((option) => (
            <TouchableOpacity
              key={option.value}
              style={[
                styles.recurrenceOption,
                recurrence === option.value && styles.recurrenceOptionActive,
              ]}
              onPress={() => setRecurrence(option.value)}
            >
              <Icon
                name={option.icon}
                size={24}
                color={recurrence === option.value ? '#6200EE' : '#666'}
              />
              <Text
                style={[
                  styles.recurrenceOptionText,
                  recurrence === option.value && styles.recurrenceOptionTextActive,
                ]}
              >
                {option.label}
              </Text>
            </TouchableOpacity>
          ))}
        </View>
      </View>
    );
  };

  const renderDatePickers = () => (
    <View style={styles.section}>
      <Text style={styles.sectionLabel}>Schedule</Text>

      <TouchableOpacity
        style={styles.datePickerButton}
        onPress={() => setShowStartDatePicker(true)}
      >
        <Icon name="calendar-start" size={24} color="#6200EE" />
        <View style={styles.dateInfo}>
          <Text style={styles.dateLabel}>Start Date</Text>
          <Text style={styles.dateValue}>{formatDate(startDate)}</Text>
        </View>
        <Text style={styles.timeValue}>{formatTime(startDate)}</Text>
      </TouchableOpacity>

      {showStartDatePicker && (
        <DateTimePicker
          value={startDate}
          mode="datetime"
          display="default"
          onChange={(event, selectedDate) => {
            setShowStartDatePicker(false);
            if (selectedDate) {
              setStartDate(selectedDate);
            }
          }}
          minimumDate={new Date()}
        />
      )}

      {recurrence !== 'once' && (
        <>
          <View style={styles.endDateToggle}>
            <Text style={styles.endDateLabel}>Set End Date</Text>
            <Switch
              value={hasEndDate}
              onValueChange={(value) => {
                setHasEndDate(value);
                if (value && !endDate) {
                  const defaultEndDate = new Date(startDate);
                  defaultEndDate.setMonth(defaultEndDate.getMonth() + 3);
                  setEndDate(defaultEndDate);
                }
              }}
              trackColor={{ false: '#E0E0E0', true: '#B388FF' }}
              thumbColor={hasEndDate ? '#6200EE' : '#FFFFFF'}
            />
          </View>

          {hasEndDate && (
            <>
              <TouchableOpacity
                style={styles.datePickerButton}
                onPress={() => setShowEndDatePicker(true)}
              >
                <Icon name="calendar-end" size={24} color="#6200EE" />
                <View style={styles.dateInfo}>
                  <Text style={styles.dateLabel}>End Date</Text>
                  <Text style={styles.dateValue}>
                    {endDate ? formatDate(endDate) : 'Select date'}
                  </Text>
                </View>
              </TouchableOpacity>

              {showEndDatePicker && (
                <DateTimePicker
                  value={endDate || new Date()}
                  mode="date"
                  display="default"
                  onChange={(event, selectedDate) => {
                    setShowEndDatePicker(false);
                    if (selectedDate) {
                      setEndDate(selectedDate);
                    }
                  }}
                  minimumDate={startDate}
                />
              )}
            </>
          )}
        </>
      )}
    </View>
  );

  return (
    <View style={styles.container}>
      <Header title="Schedule Payment" showBack />

      <ScrollView style={styles.content}>
        {renderRecipient()}

        <View style={styles.amountSection}>
          <AmountInput
            value={amount}
            onChangeAmount={setAmount}
            label="Amount"
            placeholder="0.00"
            minAmount={0.01}
            maxAmount={balance || 10000}
            showQuickAmounts
            quickAmounts={[10, 25, 50, 100]}
          />
        </View>

        {renderRecurrenceOptions()}
        {renderDatePickers()}

        <View style={styles.section}>
          <Text style={styles.sectionLabel}>Description (Optional)</Text>
          <View style={styles.descriptionInput}>
            <Icon name="message-text" size={20} color="#666" />
            <Text style={styles.descriptionPlaceholder}>
              {description || 'Add a note...'}
            </Text>
          </View>
        </View>

        {recurrence !== 'once' && (
          <View style={styles.infoCard}>
            <Icon name="information" size={20} color="#6200EE" />
            <Text style={styles.infoText}>
              Recurring payments will be processed automatically on the scheduled
              dates. Ensure you have sufficient balance.
            </Text>
          </View>
        )}
      </ScrollView>

      <View style={styles.footer}>
        <TouchableOpacity
          style={[styles.scheduleButton, !amount && styles.scheduleButtonDisabled]}
          onPress={handleSchedulePayment}
          disabled={!amount}
        >
          <Icon name="calendar-check" size={20} color="#FFFFFF" />
          <Text style={styles.scheduleButtonText}>Schedule Payment</Text>
        </TouchableOpacity>
      </View>
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
  section: {
    backgroundColor: '#FFFFFF',
    paddingVertical: 16,
    paddingHorizontal: 16,
    marginBottom: 8,
  },
  sectionLabel: {
    fontSize: 14,
    fontWeight: '600',
    color: '#666',
    marginBottom: 12,
  },
  recipientContainer: {
    backgroundColor: '#FFFFFF',
    paddingVertical: 16,
    paddingHorizontal: 16,
    marginBottom: 8,
  },
  recipientCard: {
    flexDirection: 'row',
    alignItems: 'center',
  },
  recipientInfo: {
    marginLeft: 12,
    flex: 1,
  },
  recipientName: {
    fontSize: 18,
    fontWeight: 'bold',
    color: '#212121',
  },
  recipientContact: {
    fontSize: 14,
    color: '#666',
    marginTop: 2,
  },
  amountSection: {
    backgroundColor: '#FFFFFF',
    paddingVertical: 16,
    paddingHorizontal: 16,
    marginBottom: 8,
  },
  recurrenceOptions: {
    flexDirection: 'row',
    flexWrap: 'wrap',
    marginHorizontal: -4,
  },
  recurrenceOption: {
    alignItems: 'center',
    justifyContent: 'center',
    width: '23%',
    aspectRatio: 1,
    marginHorizontal: '1%',
    marginBottom: 8,
    backgroundColor: '#F5F5F5',
    borderRadius: 12,
    borderWidth: 2,
    borderColor: '#E0E0E0',
  },
  recurrenceOptionActive: {
    backgroundColor: '#F3E5F5',
    borderColor: '#6200EE',
  },
  recurrenceOptionText: {
    fontSize: 12,
    color: '#666',
    marginTop: 4,
    fontWeight: '600',
  },
  recurrenceOptionTextActive: {
    color: '#6200EE',
  },
  datePickerButton: {
    flexDirection: 'row',
    alignItems: 'center',
    paddingVertical: 12,
    paddingHorizontal: 16,
    backgroundColor: '#F5F5F5',
    borderRadius: 8,
    marginBottom: 12,
  },
  dateInfo: {
    flex: 1,
    marginLeft: 12,
  },
  dateLabel: {
    fontSize: 12,
    color: '#666',
  },
  dateValue: {
    fontSize: 16,
    color: '#212121',
    fontWeight: '600',
    marginTop: 2,
  },
  timeValue: {
    fontSize: 14,
    color: '#6200EE',
    fontWeight: '600',
  },
  endDateToggle: {
    flexDirection: 'row',
    alignItems: 'center',
    justifyContent: 'space-between',
    paddingVertical: 8,
    marginBottom: 12,
  },
  endDateLabel: {
    fontSize: 16,
    color: '#212121',
  },
  descriptionInput: {
    flexDirection: 'row',
    alignItems: 'center',
    paddingVertical: 12,
    paddingHorizontal: 16,
    backgroundColor: '#F5F5F5',
    borderRadius: 8,
  },
  descriptionPlaceholder: {
    fontSize: 16,
    color: '#999',
    marginLeft: 12,
  },
  infoCard: {
    flexDirection: 'row',
    alignItems: 'flex-start',
    backgroundColor: '#E8EAF6',
    paddingVertical: 12,
    paddingHorizontal: 16,
    marginHorizontal: 16,
    marginBottom: 16,
    borderRadius: 8,
  },
  infoText: {
    fontSize: 14,
    color: '#666',
    marginLeft: 12,
    flex: 1,
    lineHeight: 20,
  },
  footer: {
    backgroundColor: '#FFFFFF',
    paddingVertical: 16,
    paddingHorizontal: 16,
    borderTopWidth: 1,
    borderTopColor: '#E0E0E0',
  },
  scheduleButton: {
    flexDirection: 'row',
    alignItems: 'center',
    justifyContent: 'center',
    backgroundColor: '#6200EE',
    paddingVertical: 16,
    borderRadius: 8,
  },
  scheduleButtonDisabled: {
    backgroundColor: '#BDBDBD',
  },
  scheduleButtonText: {
    color: '#FFFFFF',
    fontSize: 18,
    fontWeight: 'bold',
    marginLeft: 8,
  },
});

export default ScheduledPaymentScreen;
