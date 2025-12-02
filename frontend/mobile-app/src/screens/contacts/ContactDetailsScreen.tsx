import React, { useState } from 'react';
import {
  View,
  Text,
  StyleSheet,
  ScrollView,
  TouchableOpacity,
  Alert,
  Linking,
  Share,
  ActivityIndicator,
} from 'react-native';
import { SafeAreaView } from 'react-native-safe-area-context';
import Icon from 'react-native-vector-icons/MaterialIcons';
import { useNavigation, useRoute } from '@react-navigation/native';
import { useQuery, useMutation, useQueryClient } from 'react-query';
import { useTheme } from '../../contexts/ThemeContext';
import { contactService } from '../../services/contactService';
import { transactionService } from '../../services/transactionService';
import { Contact, Transaction } from '../../types';
import UserAvatar from '../../components/UserAvatar';
import TransactionItem from '../../components/TransactionItem';
import ActionSheet from '../../components/ActionSheet';
import { formatCurrency, formatPhoneNumber, formatDate } from '../../utils/formatters';
import Haptics from 'react-native-haptic-feedback';

const ContactDetailsScreen: React.FC = () => {
  const navigation = useNavigation();
  const route = useRoute();
  const { theme } = useTheme();
  const queryClient = useQueryClient();
  const { contact: initialContact } = route.params as { contact: Contact };
  const [showActionSheet, setShowActionSheet] = useState(false);

  // Fetch updated contact details
  const { data: contact, isLoading } = useQuery(
    ['contact', initialContact.id],
    () => contactService.getContactDetails(initialContact.id),
    {
      initialData: initialContact,
    }
  );

  // Fetch transaction history with this contact
  const { data: transactions } = useQuery(
    ['contactTransactions', contact?.id],
    () => transactionService.getTransactionsWithUser(contact!.id),
    {
      enabled: !!contact,
    }
  );

  // Toggle favorite mutation
  const toggleFavoriteMutation = useMutation(
    () => contactService.toggleFavorite(contact!.id, !contact!.isFavorite),
    {
      onSuccess: () => {
        queryClient.invalidateQueries(['contact', contact!.id]);
        queryClient.invalidateQueries('contacts');
        Haptics.impactAsync(Haptics.ImpactFeedbackStyle.Light);
      },
    }
  );

  // Remove contact mutation
  const removeContactMutation = useMutation(
    () => contactService.removeContact(contact!.id),
    {
      onSuccess: () => {
        queryClient.invalidateQueries('contacts');
        navigation.goBack();
      },
      onError: (error: any) => {
        Alert.alert('Error', error.message || 'Failed to remove contact');
      },
    }
  );

  // Block contact mutation
  const blockContactMutation = useMutation(
    () => contactService.blockContact(contact!.id),
    {
      onSuccess: () => {
        queryClient.invalidateQueries('contacts');
        Alert.alert('Contact Blocked', 'This contact has been blocked successfully.');
        navigation.goBack();
      },
    }
  );

  const handleCall = () => {
    if (contact?.phoneNumber) {
      Linking.openURL(`tel:${contact.phoneNumber}`);
    }
  };

  const handleMessage = () => {
    if (contact?.phoneNumber) {
      Linking.openURL(`sms:${contact.phoneNumber}`);
    }
  };

  const handleEmail = () => {
    if (contact?.email) {
      Linking.openURL(`mailto:${contact.email}`);
    }
  };

  const handleShare = async () => {
    try {
      await Share.share({
        message: `Send money to ${contact?.displayName} on Waqiti! Username: @${contact?.username}`,
        title: 'Share Contact',
      });
    } catch (error) {
      console.error('Error sharing:', error);
    }
  };

  const handleRemoveContact = () => {
    Alert.alert(
      'Remove Contact',
      `Remove ${contact?.displayName} from your contacts?`,
      [
        { text: 'Cancel', style: 'cancel' },
        {
          text: 'Remove',
          style: 'destructive',
          onPress: () => removeContactMutation.mutate(),
        },
      ]
    );
  };

  const handleBlockContact = () => {
    Alert.alert(
      'Block Contact',
      `Block ${contact?.displayName}? They won't be able to send you money or requests.`,
      [
        { text: 'Cancel', style: 'cancel' },
        {
          text: 'Block',
          style: 'destructive',
          onPress: () => blockContactMutation.mutate(),
        },
      ]
    );
  };

  const calculateStats = () => {
    if (!transactions || transactions.length === 0) {
      return { sent: 0, received: 0, total: 0, count: 0 };
    }

    return transactions.reduce(
      (acc, tx) => {
        if (tx.type === 'SENT') {
          acc.sent += tx.amount;
        } else if (tx.type === 'RECEIVED') {
          acc.received += tx.amount;
        }
        acc.total += tx.amount;
        acc.count++;
        return acc;
      },
      { sent: 0, received: 0, total: 0, count: 0 }
    );
  };

  const stats = calculateStats();

  const actionSheetOptions = [
    {
      label: 'Share Contact',
      icon: 'share',
      onPress: handleShare,
    },
    {
      label: 'Remove from Contacts',
      icon: 'person-remove',
      onPress: handleRemoveContact,
      destructive: true,
    },
    {
      label: 'Block Contact',
      icon: 'block',
      onPress: handleBlockContact,
      destructive: true,
    },
  ];

  if (isLoading || !contact) {
    return (
      <SafeAreaView style={[styles.container, { backgroundColor: theme.colors.background }]}>
        <View style={styles.loadingContainer}>
          <ActivityIndicator size="large" color={theme.colors.primary} />
        </View>
      </SafeAreaView>
    );
  }

  return (
    <SafeAreaView style={[styles.container, { backgroundColor: theme.colors.background }]}>
      <ScrollView showsVerticalScrollIndicator={false}>
        {/* Header */}
        <View style={styles.header}>
          <TouchableOpacity
            onPress={() => navigation.goBack()}
            style={styles.backButton}
          >
            <Icon name="arrow-back" size={24} color={theme.colors.text} />
          </TouchableOpacity>
          
          <View style={styles.headerActions}>
            <TouchableOpacity
              onPress={() => toggleFavoriteMutation.mutate()}
              style={styles.headerButton}
            >
              <Icon
                name={contact.isFavorite ? 'star' : 'star-border'}
                size={24}
                color={contact.isFavorite ? '#FFD700' : theme.colors.text}
              />
            </TouchableOpacity>
            
            <TouchableOpacity
              onPress={() => setShowActionSheet(true)}
              style={styles.headerButton}
            >
              <Icon name="more-vert" size={24} color={theme.colors.text} />
            </TouchableOpacity>
          </View>
        </View>

        {/* Profile Section */}
        <View style={styles.profileSection}>
          <UserAvatar user={contact} size={100} style={styles.avatar} />
          
          <View style={styles.nameContainer}>
            <Text style={[styles.displayName, { color: theme.colors.text }]}>
              {contact.displayName}
            </Text>
            {contact.verified && (
              <Icon name="verified" size={20} color={theme.colors.primary} />
            )}
          </View>
          
          {contact.username && (
            <Text style={[styles.username, { color: theme.colors.textSecondary }]}>
              @{contact.username}
            </Text>
          )}
          
          {contact.bio && (
            <Text style={[styles.bio, { color: theme.colors.text }]}>
              {contact.bio}
            </Text>
          )}
        </View>

        {/* Action Buttons */}
        <View style={styles.actionButtons}>
          <TouchableOpacity
            style={[styles.primaryButton, { backgroundColor: theme.colors.primary }]}
            onPress={() => navigation.navigate('SendMoney', { recipient: contact })}
          >
            <Icon name="send" size={20} color="#FFFFFF" />
            <Text style={styles.primaryButtonText}>Send Money</Text>
          </TouchableOpacity>
          
          <TouchableOpacity
            style={[styles.secondaryButton, { borderColor: theme.colors.primary }]}
            onPress={() => navigation.navigate('RequestMoney', { recipient: contact })}
          >
            <Icon name="request-quote" size={20} color={theme.colors.primary} />
            <Text style={[styles.secondaryButtonText, { color: theme.colors.primary }]}>
              Request
            </Text>
          </TouchableOpacity>
        </View>

        {/* Contact Info */}
        <View style={[styles.section, { backgroundColor: theme.colors.surface }]}>
          <Text style={[styles.sectionTitle, { color: theme.colors.text }]}>
            Contact Information
          </Text>
          
          {contact.phoneNumber && (
            <TouchableOpacity
              style={styles.contactRow}
              onPress={handleCall}
            >
              <Icon name="phone" size={20} color={theme.colors.textSecondary} />
              <Text style={[styles.contactText, { color: theme.colors.text }]}>
                {formatPhoneNumber(contact.phoneNumber)}
              </Text>
              <Icon name="chevron-right" size={20} color={theme.colors.textSecondary} />
            </TouchableOpacity>
          )}
          
          {contact.email && (
            <TouchableOpacity
              style={styles.contactRow}
              onPress={handleEmail}
            >
              <Icon name="email" size={20} color={theme.colors.textSecondary} />
              <Text style={[styles.contactText, { color: theme.colors.text }]}>
                {contact.email}
              </Text>
              <Icon name="chevron-right" size={20} color={theme.colors.textSecondary} />
            </TouchableOpacity>
          )}
          
          <View style={styles.contactRow}>
            <Icon name="calendar-today" size={20} color={theme.colors.textSecondary} />
            <Text style={[styles.contactText, { color: theme.colors.text }]}>
              Contact since {formatDate(contact.addedAt, 'MMM yyyy')}
            </Text>
          </View>
        </View>

        {/* Transaction Stats */}
        <View style={[styles.section, { backgroundColor: theme.colors.surface }]}>
          <Text style={[styles.sectionTitle, { color: theme.colors.text }]}>
            Transaction Summary
          </Text>
          
          <View style={styles.statsGrid}>
            <View style={styles.statItem}>
              <Icon name="arrow-upward" size={24} color={theme.colors.success} />
              <Text style={[styles.statAmount, { color: theme.colors.text }]}>
                {formatCurrency(stats.sent)}
              </Text>
              <Text style={[styles.statLabel, { color: theme.colors.textSecondary }]}>
                Sent
              </Text>
            </View>
            
            <View style={styles.statItem}>
              <Icon name="arrow-downward" size={24} color={theme.colors.primary} />
              <Text style={[styles.statAmount, { color: theme.colors.text }]}>
                {formatCurrency(stats.received)}
              </Text>
              <Text style={[styles.statLabel, { color: theme.colors.textSecondary }]}>
                Received
              </Text>
            </View>
            
            <View style={styles.statItem}>
              <Icon name="sync-alt" size={24} color={theme.colors.textSecondary} />
              <Text style={[styles.statAmount, { color: theme.colors.text }]}>
                {stats.count}
              </Text>
              <Text style={[styles.statLabel, { color: theme.colors.textSecondary }]}>
                Transactions
              </Text>
            </View>
          </View>
        </View>

        {/* Recent Transactions */}
        {transactions && transactions.length > 0 && (
          <View style={[styles.section, { backgroundColor: theme.colors.surface }]}>
            <View style={styles.sectionHeader}>
              <Text style={[styles.sectionTitle, { color: theme.colors.text }]}>
                Recent Transactions
              </Text>
              <TouchableOpacity
                onPress={() => navigation.navigate('TransactionHistory', {
                  filter: { userId: contact.id }
                })}
              >
                <Text style={[styles.viewAllText, { color: theme.colors.primary }]}>
                  View All
                </Text>
              </TouchableOpacity>
            </View>
            
            {transactions.slice(0, 5).map((transaction, index) => (
              <React.Fragment key={transaction.id}>
                {index > 0 && <View style={styles.divider} />}
                <TransactionItem
                  transaction={transaction}
                  onPress={() => navigation.navigate('TransactionDetails', { transaction })}
                />
              </React.Fragment>
            ))}
          </View>
        )}

        {/* Quick Actions */}
        <View style={styles.quickActions}>
          {contact.phoneNumber && (
            <>
              <TouchableOpacity
                style={[styles.quickActionButton, { backgroundColor: theme.colors.surface }]}
                onPress={handleCall}
              >
                <Icon name="phone" size={24} color={theme.colors.primary} />
                <Text style={[styles.quickActionText, { color: theme.colors.text }]}>
                  Call
                </Text>
              </TouchableOpacity>
              
              <TouchableOpacity
                style={[styles.quickActionButton, { backgroundColor: theme.colors.surface }]}
                onPress={handleMessage}
              >
                <Icon name="message" size={24} color={theme.colors.primary} />
                <Text style={[styles.quickActionText, { color: theme.colors.text }]}>
                  Message
                </Text>
              </TouchableOpacity>
            </>
          )}
          
          <TouchableOpacity
            style={[styles.quickActionButton, { backgroundColor: theme.colors.surface }]}
            onPress={handleShare}
          >
            <Icon name="share" size={24} color={theme.colors.primary} />
            <Text style={[styles.quickActionText, { color: theme.colors.text }]}>
              Share
            </Text>
          </TouchableOpacity>
        </View>
      </ScrollView>

      <ActionSheet
        visible={showActionSheet}
        onClose={() => setShowActionSheet(false)}
        options={actionSheetOptions}
      />
    </SafeAreaView>
  );
};

const styles = StyleSheet.create({
  container: {
    flex: 1,
  },
  loadingContainer: {
    flex: 1,
    justifyContent: 'center',
    alignItems: 'center',
  },
  header: {
    flexDirection: 'row',
    justifyContent: 'space-between',
    alignItems: 'center',
    paddingHorizontal: 16,
    paddingVertical: 12,
  },
  backButton: {
    padding: 8,
  },
  headerActions: {
    flexDirection: 'row',
    gap: 16,
  },
  headerButton: {
    padding: 4,
  },
  profileSection: {
    alignItems: 'center',
    paddingHorizontal: 16,
    paddingBottom: 24,
  },
  avatar: {
    marginBottom: 16,
  },
  nameContainer: {
    flexDirection: 'row',
    alignItems: 'center',
    gap: 8,
    marginBottom: 4,
  },
  displayName: {
    fontSize: 24,
    fontWeight: 'bold',
  },
  username: {
    fontSize: 16,
    marginBottom: 8,
  },
  bio: {
    fontSize: 14,
    textAlign: 'center',
    paddingHorizontal: 32,
    lineHeight: 20,
  },
  actionButtons: {
    flexDirection: 'row',
    paddingHorizontal: 16,
    paddingBottom: 24,
    gap: 12,
  },
  primaryButton: {
    flex: 1,
    flexDirection: 'row',
    justifyContent: 'center',
    alignItems: 'center',
    paddingVertical: 14,
    borderRadius: 12,
    gap: 8,
  },
  primaryButtonText: {
    color: '#FFFFFF',
    fontSize: 16,
    fontWeight: '600',
  },
  secondaryButton: {
    flex: 1,
    flexDirection: 'row',
    justifyContent: 'center',
    alignItems: 'center',
    paddingVertical: 14,
    borderRadius: 12,
    borderWidth: 1,
    gap: 8,
  },
  secondaryButtonText: {
    fontSize: 16,
    fontWeight: '600',
  },
  section: {
    marginHorizontal: 16,
    marginBottom: 16,
    padding: 16,
    borderRadius: 12,
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
    marginBottom: 16,
  },
  viewAllText: {
    fontSize: 14,
    fontWeight: '500',
  },
  contactRow: {
    flexDirection: 'row',
    alignItems: 'center',
    paddingVertical: 12,
    gap: 12,
  },
  contactText: {
    flex: 1,
    fontSize: 16,
  },
  statsGrid: {
    flexDirection: 'row',
    justifyContent: 'space-around',
  },
  statItem: {
    alignItems: 'center',
  },
  statAmount: {
    fontSize: 20,
    fontWeight: '600',
    marginTop: 8,
  },
  statLabel: {
    fontSize: 14,
    marginTop: 4,
  },
  divider: {
    height: StyleSheet.hairlineWidth,
    backgroundColor: 'rgba(0, 0, 0, 0.1)',
    marginVertical: 8,
  },
  quickActions: {
    flexDirection: 'row',
    paddingHorizontal: 16,
    paddingBottom: 24,
    gap: 12,
  },
  quickActionButton: {
    flex: 1,
    alignItems: 'center',
    paddingVertical: 16,
    borderRadius: 12,
    gap: 8,
  },
  quickActionText: {
    fontSize: 14,
    fontWeight: '500',
  },
});

export default ContactDetailsScreen;