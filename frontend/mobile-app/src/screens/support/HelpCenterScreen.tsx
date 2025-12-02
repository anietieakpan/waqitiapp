import React, { useState, useEffect } from 'react';
import {
  View,
  Text,
  StyleSheet,
  ScrollView,
  TouchableOpacity,
  TextInput,
  Alert,
  Linking,
  Platform,
} from 'react-native';
import { SafeAreaView } from 'react-native-safe-area-context';
import { useNavigation } from '@react-navigation/native';
import Icon from 'react-native-vector-icons/MaterialIcons';
import { useTheme } from '../../contexts/ThemeContext';
import { useAuth } from '../../contexts/AuthContext';
import { supportService } from '../../services/supportService';

interface FAQItem {
  id: string;
  category: string;
  question: string;
  answer: string;
  isExpanded?: boolean;
}

interface SupportContact {
  type: 'phone' | 'email' | 'chat';
  label: string;
  value: string;
  icon: string;
  available: boolean;
  hours?: string;
}

const HelpCenterScreen: React.FC = () => {
  const navigation = useNavigation();
  const { theme } = useTheme();
  const { user } = useAuth();
  const [searchQuery, setSearchQuery] = useState('');
  const [faqs, setFaqs] = useState<FAQItem[]>([]);
  const [filteredFaqs, setFilteredFaqs] = useState<FAQItem[]>([]);
  const [loading, setLoading] = useState(true);
  const [selectedCategory, setSelectedCategory] = useState<string>('all');

  const supportContacts: SupportContact[] = [
    {
      type: 'phone',
      label: 'Call Support',
      value: '+1-800-WAQITI-1',
      icon: 'phone',
      available: true,
      hours: '24/7',
    },
    {
      type: 'email',
      label: 'Email Support',
      value: 'support@example.com',
      icon: 'email',
      available: true,
      hours: 'Response within 24 hours',
    },
    {
      type: 'chat',
      label: 'Live Chat',
      value: 'chat',
      icon: 'chat',
      available: true,
      hours: 'Mon-Fri 9AM-6PM EST',
    },
  ];

  const categories = [
    { key: 'all', label: 'All Topics', icon: 'help' },
    { key: 'payments', label: 'Payments', icon: 'payment' },
    { key: 'security', label: 'Security', icon: 'security' },
    { key: 'account', label: 'Account', icon: 'account-circle' },
    { key: 'fees', label: 'Fees', icon: 'receipt' },
    { key: 'transfers', label: 'Transfers', icon: 'swap-horiz' },
  ];

  useEffect(() => {
    loadFAQs();
  }, []);

  useEffect(() => {
    filterFAQs();
  }, [searchQuery, selectedCategory, faqs]);

  const loadFAQs = async () => {
    try {
      setLoading(true);
      const response = await supportService.getFAQs();
      setFaqs(response.faqs);
    } catch (error) {
      console.error('Failed to load FAQs:', error);
      // Load fallback FAQs
      setFaqs(getFallbackFAQs());
    } finally {
      setLoading(false);
    }
  };

  const filterFAQs = () => {
    let filtered = faqs;

    // Filter by category
    if (selectedCategory !== 'all') {
      filtered = filtered.filter(faq => faq.category === selectedCategory);
    }

    // Filter by search query
    if (searchQuery.trim()) {
      const query = searchQuery.toLowerCase();
      filtered = filtered.filter(
        faq =>
          faq.question.toLowerCase().includes(query) ||
          faq.answer.toLowerCase().includes(query)
      );
    }

    setFilteredFaqs(filtered);
  };

  const toggleFAQ = (id: string) => {
    setFilteredFaqs(prev =>
      prev.map(faq =>
        faq.id === id ? { ...faq, isExpanded: !faq.isExpanded } : faq
      )
    );
  };

  const handleContactSupport = (contact: SupportContact) => {
    switch (contact.type) {
      case 'phone':
        Linking.openURL(`tel:${contact.value.replace(/[^0-9+]/g, '')}`);
        break;
      case 'email':
        Linking.openURL(`mailto:${contact.value}`);
        break;
      case 'chat':
        navigation.navigate('LiveChat');
        break;
    }
  };

  const submitFeedback = () => {
    navigation.navigate('Feedback');
  };

  const reportIssue = () => {
    navigation.navigate('ReportIssue');
  };

  const getFallbackFAQs = (): FAQItem[] => [
    {
      id: '1',
      category: 'payments',
      question: 'How do I send money to someone?',
      answer: 'To send money: 1) Tap "Send" on your home screen, 2) Select or add a recipient, 3) Enter the amount, 4) Add a note (optional), 5) Review and confirm. The money will be sent instantly.',
    },
    {
      id: '2',
      category: 'payments',
      question: 'How long do payments take to process?',
      answer: 'Most payments are processed instantly. Bank transfers may take 1-3 business days. You\'ll receive a notification when your payment is completed.',
    },
    {
      id: '3',
      category: 'security',
      question: 'Is my money safe with Waqiti?',
      answer: 'Yes, your money is protected by bank-level security. We use 256-bit encryption, two-factor authentication, and are regulated by financial authorities.',
    },
    {
      id: '4',
      category: 'account',
      question: 'How do I verify my account?',
      answer: 'Account verification requires: 1) Email verification, 2) Phone number verification, 3) Identity verification with government ID. This process typically takes 1-2 business days.',
    },
    {
      id: '5',
      category: 'fees',
      question: 'What fees does Waqiti charge?',
      answer: 'Waqiti is free for standard transfers. We charge small fees for instant transfers (1.5%) and international transfers (varies by country).',
    },
    {
      id: '6',
      category: 'transfers',
      question: 'Can I cancel a payment?',
      answer: 'You can cancel a payment only if it hasn\'t been claimed yet. Once claimed, payments cannot be cancelled. Contact support if you need assistance.',
    },
  ];

  const renderSearchBar = () => (
    <View style={[styles.searchContainer, { backgroundColor: theme.colors.surface }]}>
      <Icon name="search" size={20} color={theme.colors.textSecondary} style={styles.searchIcon} />
      <TextInput
        style={[styles.searchInput, { color: theme.colors.text }]}
        placeholder="Search for help..."
        placeholderTextColor={theme.colors.textSecondary}
        value={searchQuery}
        onChangeText={setSearchQuery}
      />
      {searchQuery.length > 0 && (
        <TouchableOpacity onPress={() => setSearchQuery('')}>
          <Icon name="clear" size={20} color={theme.colors.textSecondary} />
        </TouchableOpacity>
      )}
    </View>
  );

  const renderCategories = () => (
    <ScrollView
      horizontal
      showsHorizontalScrollIndicator={false}
      style={styles.categoriesContainer}
      contentContainerStyle={styles.categoriesContent}
    >
      {categories.map(category => (
        <TouchableOpacity
          key={category.key}
          style={[
            styles.categoryButton,
            {
              backgroundColor: selectedCategory === category.key
                ? theme.colors.primary
                : theme.colors.surface,
            },
          ]}
          onPress={() => setSelectedCategory(category.key)}
        >
          <Icon
            name={category.icon}
            size={20}
            color={selectedCategory === category.key ? '#FFFFFF' : theme.colors.text}
          />
          <Text
            style={[
              styles.categoryText,
              {
                color: selectedCategory === category.key ? '#FFFFFF' : theme.colors.text,
              },
            ]}
          >
            {category.label}
          </Text>
        </TouchableOpacity>
      ))}
    </ScrollView>
  );

  const renderFAQItem = (faq: FAQItem) => (
    <TouchableOpacity
      key={faq.id}
      style={[styles.faqItem, { backgroundColor: theme.colors.surface }]}
      onPress={() => toggleFAQ(faq.id)}
      activeOpacity={0.7}
    >
      <View style={styles.faqHeader}>
        <Text style={[styles.faqQuestion, { color: theme.colors.text }]}>
          {faq.question}
        </Text>
        <Icon
          name={faq.isExpanded ? 'expand-less' : 'expand-more'}
          size={24}
          color={theme.colors.textSecondary}
        />
      </View>
      {faq.isExpanded && (
        <Text style={[styles.faqAnswer, { color: theme.colors.textSecondary }]}>
          {faq.answer}
        </Text>
      )}
    </TouchableOpacity>
  );

  const renderQuickActions = () => (
    <View style={styles.quickActionsContainer}>
      <Text style={[styles.sectionTitle, { color: theme.colors.text }]}>
        Quick Actions
      </Text>
      <View style={styles.quickActionsGrid}>
        <TouchableOpacity
          style={[styles.quickActionButton, { backgroundColor: theme.colors.surface }]}
          onPress={reportIssue}
        >
          <Icon name="report-problem" size={32} color="#F44336" />
          <Text style={[styles.quickActionText, { color: theme.colors.text }]}>
            Report Issue
          </Text>
        </TouchableOpacity>
        
        <TouchableOpacity
          style={[styles.quickActionButton, { backgroundColor: theme.colors.surface }]}
          onPress={submitFeedback}
        >
          <Icon name="feedback" size={32} color="#2196F3" />
          <Text style={[styles.quickActionText, { color: theme.colors.text }]}>
            Send Feedback
          </Text>
        </TouchableOpacity>
        
        <TouchableOpacity
          style={[styles.quickActionButton, { backgroundColor: theme.colors.surface }]}
          onPress={() => navigation.navigate('TransactionHistory')}
        >
          <Icon name="history" size={32} color="#FF9800" />
          <Text style={[styles.quickActionText, { color: theme.colors.text }]}>
            Transaction History
          </Text>
        </TouchableOpacity>
        
        <TouchableOpacity
          style={[styles.quickActionButton, { backgroundColor: theme.colors.surface }]}
          onPress={() => navigation.navigate('SecuritySettings')}
        >
          <Icon name="security" size={32} color="#4CAF50" />
          <Text style={[styles.quickActionText, { color: theme.colors.text }]}>
            Security Settings
          </Text>
        </TouchableOpacity>
      </View>
    </View>
  );

  const renderContactSupport = () => (
    <View style={styles.contactContainer}>
      <Text style={[styles.sectionTitle, { color: theme.colors.text }]}>
        Contact Support
      </Text>
      {supportContacts.map((contact, index) => (
        <TouchableOpacity
          key={index}
          style={[styles.contactItem, { backgroundColor: theme.colors.surface }]}
          onPress={() => handleContactSupport(contact)}
        >
          <View style={[styles.contactIcon, { backgroundColor: theme.colors.primary + '20' }]}>
            <Icon name={contact.icon} size={24} color={theme.colors.primary} />
          </View>
          <View style={styles.contactInfo}>
            <Text style={[styles.contactLabel, { color: theme.colors.text }]}>
              {contact.label}
            </Text>
            <Text style={[styles.contactValue, { color: theme.colors.textSecondary }]}>
              {contact.value}
            </Text>
            {contact.hours && (
              <Text style={[styles.contactHours, { color: theme.colors.textSecondary }]}>
                {contact.hours}
              </Text>
            )}
          </View>
          <View style={[
            styles.availabilityIndicator,
            { backgroundColor: contact.available ? '#4CAF50' : '#F44336' }
          ]} />
        </TouchableOpacity>
      ))}
    </View>
  );

  return (
    <SafeAreaView style={[styles.container, { backgroundColor: theme.colors.background }]}>
      <View style={styles.header}>
        <TouchableOpacity
          onPress={() => navigation.goBack()}
          style={styles.backButton}
        >
          <Icon name="arrow-back" size={24} color={theme.colors.text} />
        </TouchableOpacity>
        
        <Text style={[styles.headerTitle, { color: theme.colors.text }]}>
          Help Center
        </Text>
        
        <TouchableOpacity
          onPress={() => navigation.navigate('SupportTickets')}
          style={styles.ticketsButton}
        >
          <Icon name="confirmation-number" size={24} color={theme.colors.text} />
        </TouchableOpacity>
      </View>

      <ScrollView style={styles.content} showsVerticalScrollIndicator={false}>
        {renderSearchBar()}
        {renderCategories()}
        {renderQuickActions()}

        <View style={styles.faqContainer}>
          <Text style={[styles.sectionTitle, { color: theme.colors.text }]}>
            Frequently Asked Questions
          </Text>
          {loading ? (
            <View style={styles.loadingContainer}>
              <Text style={[styles.loadingText, { color: theme.colors.textSecondary }]}>
                Loading FAQs...
              </Text>
            </View>
          ) : filteredFaqs.length > 0 ? (
            filteredFaqs.map(renderFAQItem)
          ) : (
            <View style={styles.emptyContainer}>
              <Icon name="help-outline" size={48} color={theme.colors.textSecondary} />
              <Text style={[styles.emptyText, { color: theme.colors.textSecondary }]}>
                No FAQs found matching your search
              </Text>
            </View>
          )}
        </View>

        {renderContactSupport()}
      </ScrollView>
    </SafeAreaView>
  );
};

const styles = StyleSheet.create({
  container: {
    flex: 1,
  },
  header: {
    flexDirection: 'row',
    alignItems: 'center',
    justifyContent: 'space-between',
    paddingHorizontal: 16,
    paddingVertical: 12,
  },
  backButton: {
    padding: 8,
  },
  headerTitle: {
    fontSize: 20,
    fontWeight: '600',
    flex: 1,
    textAlign: 'center',
    marginHorizontal: 16,
  },
  ticketsButton: {
    padding: 8,
  },
  content: {
    flex: 1,
    paddingHorizontal: 16,
  },
  searchContainer: {
    flexDirection: 'row',
    alignItems: 'center',
    paddingHorizontal: 16,
    paddingVertical: 12,
    borderRadius: 12,
    marginBottom: 16,
  },
  searchIcon: {
    marginRight: 8,
  },
  searchInput: {
    flex: 1,
    fontSize: 16,
  },
  categoriesContainer: {
    marginBottom: 24,
  },
  categoriesContent: {
    paddingRight: 16,
  },
  categoryButton: {
    flexDirection: 'row',
    alignItems: 'center',
    paddingHorizontal: 16,
    paddingVertical: 8,
    borderRadius: 20,
    marginRight: 8,
  },
  categoryText: {
    fontSize: 14,
    fontWeight: '500',
    marginLeft: 4,
  },
  quickActionsContainer: {
    marginBottom: 24,
  },
  sectionTitle: {
    fontSize: 18,
    fontWeight: '600',
    marginBottom: 16,
  },
  quickActionsGrid: {
    flexDirection: 'row',
    flexWrap: 'wrap',
    justifyContent: 'space-between',
  },
  quickActionButton: {
    width: '48%',
    aspectRatio: 1,
    borderRadius: 12,
    justifyContent: 'center',
    alignItems: 'center',
    marginBottom: 12,
  },
  quickActionText: {
    fontSize: 12,
    fontWeight: '500',
    marginTop: 8,
    textAlign: 'center',
  },
  faqContainer: {
    marginBottom: 24,
  },
  faqItem: {
    borderRadius: 12,
    padding: 16,
    marginBottom: 8,
  },
  faqHeader: {
    flexDirection: 'row',
    justifyContent: 'space-between',
    alignItems: 'center',
  },
  faqQuestion: {
    fontSize: 16,
    fontWeight: '500',
    flex: 1,
    marginRight: 8,
  },
  faqAnswer: {
    fontSize: 14,
    lineHeight: 20,
    marginTop: 12,
  },
  contactContainer: {
    marginBottom: 32,
  },
  contactItem: {
    flexDirection: 'row',
    alignItems: 'center',
    padding: 16,
    borderRadius: 12,
    marginBottom: 8,
  },
  contactIcon: {
    width: 48,
    height: 48,
    borderRadius: 24,
    justifyContent: 'center',
    alignItems: 'center',
    marginRight: 12,
  },
  contactInfo: {
    flex: 1,
  },
  contactLabel: {
    fontSize: 16,
    fontWeight: '500',
    marginBottom: 2,
  },
  contactValue: {
    fontSize: 14,
    marginBottom: 2,
  },
  contactHours: {
    fontSize: 12,
  },
  availabilityIndicator: {
    width: 8,
    height: 8,
    borderRadius: 4,
  },
  loadingContainer: {
    padding: 32,
    alignItems: 'center',
  },
  loadingText: {
    fontSize: 16,
  },
  emptyContainer: {
    padding: 32,
    alignItems: 'center',
  },
  emptyText: {
    fontSize: 16,
    marginTop: 8,
    textAlign: 'center',
  },
});

export default HelpCenterScreen;