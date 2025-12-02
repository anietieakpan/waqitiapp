import React, { useState, useEffect } from 'react';
import {
  View,
  StyleSheet,
  Alert,
} from 'react-native';
import { useNavigation, useRoute } from '@react-navigation/native';
import { useSelector, useDispatch } from 'react-redux';
import { RootState } from '../store';
import { fetchContacts } from '../store/slices/contactsSlice';
import ContactSelector, { Contact } from '../components/ContactSelector';
import Header from '../components/Header';
import { AnalyticsService } from '../services/AnalyticsService';

/**
 * ContactSelectionScreen
 *
 * Screen for selecting a contact to send payment to
 *
 * Features:
 * - Contact search and filtering
 * - Favorites and recent contacts
 * - Navigation to payment details
 * - Analytics tracking
 *
 * @author Waqiti Engineering Team
 * @version 1.0.0
 */
const ContactSelectionScreen: React.FC = () => {
  const navigation = useNavigation();
  const route = useRoute();
  const dispatch = useDispatch();

  const { contacts, loading } = useSelector((state: RootState) => state.contacts);
  const [selectedContact, setSelectedContact] = useState<Contact | null>(null);

  useEffect(() => {
    AnalyticsService.trackScreenView('ContactSelectionScreen');
    loadContacts();
  }, []);

  const loadContacts = async () => {
    try {
      await dispatch(fetchContacts()).unwrap();
    } catch (error: any) {
      Alert.alert('Error', 'Failed to load contacts');
    }
  };

  const handleSelectContact = (contact: Contact) => {
    setSelectedContact(contact);

    AnalyticsService.trackEvent('contact_selected_for_payment', {
      contactId: contact.id,
      hasWaqitiAccount: !!contact.waqitiUserId,
    });

    // Navigate to payment details screen with selected contact
    navigation.navigate('PaymentDetails' as never, {
      contact,
      mode: 'send',
    } as never);
  };

  const handleAddContact = () => {
    AnalyticsService.trackEvent('add_contact_from_payment');
    navigation.navigate('AddContact' as never);
  };

  return (
    <View style={styles.container}>
      <Header
        title="Select Contact"
        showBack
        rightActions={[
          {
            icon: 'account-plus',
            onPress: handleAddContact,
            label: 'Add Contact',
          },
        ]}
      />

      <ContactSelector
        contacts={contacts}
        selectedContact={selectedContact}
        onSelectContact={handleSelectContact}
        loading={loading}
        showFavorites
        showRecent
        placeholder="Search by name, email, or phone..."
        emptyMessage="No contacts found. Add contacts to send payments."
      />
    </View>
  );
};

const styles = StyleSheet.create({
  container: {
    flex: 1,
    backgroundColor: '#F5F5F5',
  },
});

export default ContactSelectionScreen;
