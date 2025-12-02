import React, { useState, useEffect } from 'react';
import {
  View,
  Text,
  StyleSheet,
  ScrollView,
  TouchableOpacity,
  TextInput,
  Alert,
  KeyboardAvoidingView,
  Platform,
  ActivityIndicator,
} from 'react-native';
import { RouteProp } from '@react-navigation/native';
import { StackNavigationProp } from '@react-navigation/stack';
import { Ionicons } from '@expo/vector-icons';
import { LinearGradient } from 'expo-linear-gradient';
import QRCode from 'react-native-qrcode-svg';

import { useAppDispatch, useAppSelector } from '../store/hooks';
import { requestMoney, resetTransactionState } from '../store/slices/transactionSlice';
import { getContacts } from '../store/slices/contactSlice';
import { RootStackParamList } from '../navigation/types';
import { COLORS, FONTS, SIZES } from '../constants/theme';
import { ContactItem } from '../components/ContactItem';
import { AmountInput } from '../components/AmountInput';
import { hapticFeedback } from '../utils/haptics';
import { showToast } from '../utils/toast';
import { formatCurrency } from '../utils/currency';

type RequestMoneyScreenNavigationProp = StackNavigationProp<RootStackParamList, 'RequestMoney'>;
type RequestMoneyScreenRouteProp = RouteProp<RootStackParamList, 'RequestMoney'>;

interface Props {
  navigation: RequestMoneyScreenNavigationProp;
  route: RequestMoneyScreenRouteProp;
}

interface RequestMode {
  id: string;
  title: string;
  description: string;
  icon: string;
}

export const RequestMoneyScreen: React.FC<Props> = ({ navigation, route }) => {
  const dispatch = useAppDispatch();
  const { loading, error, lastRequest } = useAppSelector((state) => state.transaction);
  const { contacts, loading: contactsLoading } = useAppSelector((state) => state.contact);
  const { user } = useAppSelector((state) => state.auth);

  const [requestMode, setRequestMode] = useState<string>('contact'); // 'contact' | 'qr' | 'link'
  const [selectedContacts, setSelectedContacts] = useState<any[]>([]);
  const [amount, setAmount] = useState('');
  const [note, setNote] = useState('');
  const [searchQuery, setSearchQuery] = useState('');
  const [shareableLink, setShareableLink] = useState('');

  const requestModes: RequestMode[] = [
    {
      id: 'contact',
      title: 'From Contact',
      description: 'Request from someone in your contacts',
      icon: 'people-outline',
    },
    {
      id: 'qr',
      title: 'QR Code',
      description: 'Generate a QR code for easy scanning',
      icon: 'qr-code-outline',
    },
    {
      id: 'link',
      title: 'Share Link',
      description: 'Generate a shareable payment link',
      icon: 'link-outline',
    },
  ];

  useEffect(() => {
    dispatch(getContacts());
    
    return () => {
      dispatch(resetTransactionState());
    };
  }, [dispatch]);

  useEffect(() => {
    if (lastRequest && !loading && !error) {
      hapticFeedback.success();
      showToast('Money request sent successfully!');
      navigation.navigate('RequestSuccess', { request: lastRequest });
    }
  }, [lastRequest, loading, error, navigation]);

  useEffect(() => {
    if (error) {
      hapticFeedback.error();
      showToast(error);
    }
  }, [error]);

  const filteredContacts = contacts.filter(contact =>
    contact.name.toLowerCase().includes(searchQuery.toLowerCase()) ||
    contact.username?.toLowerCase().includes(searchQuery.toLowerCase())
  );

  const handleContactToggle = (contact: any) => {
    const isSelected = selectedContacts.some(c => c.id === contact.id);
    
    if (isSelected) {
      setSelectedContacts(prev => prev.filter(c => c.id !== contact.id));
    } else {
      setSelectedContacts(prev => [...prev, contact]);
    }
    
    hapticFeedback.light();
  };

  const handleRequestMoney = () => {
    if (!amount || parseFloat(amount) <= 0) {
      showToast('Please enter a valid amount');
      return;
    }

    if (requestMode === 'contact' && selectedContacts.length === 0) {
      showToast('Please select at least one contact');
      return;
    }

    const requestData = {
      amount: parseFloat(amount),
      note: note.trim(),
      recipients: selectedContacts.map(contact => contact.id),
      type: requestMode,
    };

    dispatch(requestMoney(requestData));
    hapticFeedback.light();
  };

  const generateQRCode = () => {
    if (!amount || parseFloat(amount) <= 0) {
      showToast('Please enter an amount first');
      return;
    }

    const qrData = {
      type: 'payment_request',
      userId: user?.id,
      username: user?.username,
      amount: parseFloat(amount),
      note: note.trim(),
      timestamp: Date.now(),
    };

    return JSON.stringify(qrData);
  };

  const generateShareableLink = () => {
    if (!amount || parseFloat(amount) <= 0) {
      showToast('Please enter an amount first');
      return;
    }

    const baseUrl = 'https://waqiti.app/pay';
    const params = new URLSearchParams({
      u: user?.username || '',
      a: amount,
      n: note.trim(),
    });

    const link = `${baseUrl}?${params.toString()}`;
    setShareableLink(link);
    
    return link;
  };

  const handleShareLink = async () => {
    const link = generateShareableLink();
    if (!link) return;

    try {
      // This would use react-native-share in a real implementation
      showToast('Link copied to clipboard!');
      hapticFeedback.success();
    } catch (error) {
      showToast('Failed to share link');
    }
  };

  const renderModeSelector = () => (
    <View style={styles.modeSelector}>
      <Text style={styles.modeSelectorTitle}>How do you want to request money?</Text>
      
      {requestModes.map((mode) => (
        <TouchableOpacity
          key={mode.id}
          style={[
            styles.modeOption,
            requestMode === mode.id && styles.modeOptionSelected,
          ]}
          onPress={() => {
            setRequestMode(mode.id);
            hapticFeedback.light();
          }}
        >
          <View style={styles.modeOptionContent}>
            <View style={[
              styles.modeIcon,
              requestMode === mode.id && styles.modeIconSelected,
            ]}>
              <Ionicons
                name={mode.icon as any}
                size={24}
                color={requestMode === mode.id ? COLORS.white : COLORS.primary}
              />
            </View>
            <View style={styles.modeText}>
              <Text style={[
                styles.modeTitle,
                requestMode === mode.id && styles.modeTitleSelected,
              ]}>
                {mode.title}
              </Text>
              <Text style={[
                styles.modeDescription,
                requestMode === mode.id && styles.modeDescriptionSelected,
              ]}>
                {mode.description}
              </Text>
            </View>
          </View>
          {requestMode === mode.id && (
            <Ionicons name="checkmark-circle" size={20} color={COLORS.primary} />
          )}
        </TouchableOpacity>
      ))}
    </View>
  );

  const renderAmountSection = () => (
    <View style={styles.amountSection}>
      <Text style={styles.sectionTitle}>Request Amount</Text>
      
      <AmountInput
        value={amount}
        onChangeText={setAmount}
        placeholder="0.00"
        style={styles.amountInput}
      />

      <View style={styles.noteContainer}>
        <Text style={styles.noteLabel}>What's this for? (optional)</Text>
        <TextInput
          style={styles.noteInput}
          placeholder="Add a note..."
          value={note}
          onChangeText={setNote}
          multiline
          maxLength={200}
          placeholderTextColor={COLORS.gray}
        />
      </View>
    </View>
  );

  const renderContactSelection = () => (
    <View style={styles.contactSection}>
      <Text style={styles.sectionTitle}>
        Request from Contacts ({selectedContacts.length} selected)
      </Text>
      
      <View style={styles.searchContainer}>
        <Ionicons name="search" size={20} color={COLORS.gray} style={styles.searchIcon} />
        <TextInput
          style={styles.searchInput}
          placeholder="Search contacts..."
          value={searchQuery}
          onChangeText={setSearchQuery}
          placeholderTextColor={COLORS.gray}
        />
      </View>

      <ScrollView style={styles.contactsList} showsVerticalScrollIndicator={false}>
        {contactsLoading ? (
          <ActivityIndicator size="large" color={COLORS.primary} style={styles.loader} />
        ) : filteredContacts.length > 0 ? (
          filteredContacts.map((contact) => (
            <ContactItem
              key={contact.id}
              contact={contact}
              onPress={() => handleContactToggle(contact)}
              selected={selectedContacts.some(c => c.id === contact.id)}
              multiSelect
            />
          ))
        ) : (
          <View style={styles.emptyState}>
            <Ionicons name="people-outline" size={48} color={COLORS.gray} />
            <Text style={styles.emptyStateText}>
              {searchQuery ? 'No contacts found' : 'No contacts available'}
            </Text>
          </View>
        )}
      </ScrollView>
    </View>
  );

  const renderQRCodeGeneration = () => (
    <View style={styles.qrSection}>
      <Text style={styles.sectionTitle}>QR Code Payment Request</Text>
      
      {amount && parseFloat(amount) > 0 ? (
        <View style={styles.qrContainer}>
          <View style={styles.qrCodeWrapper}>
            <QRCode
              value={generateQRCode()}
              size={200}
              backgroundColor={COLORS.white}
              color={COLORS.text}
            />
          </View>
          
          <View style={styles.qrInfo}>
            <Text style={styles.qrAmount}>{formatCurrency(parseFloat(amount))}</Text>
            {note && <Text style={styles.qrNote}>{note}</Text>}
            <Text style={styles.qrInstructions}>
              Show this QR code to the person you want to request money from
            </Text>
          </View>

          <TouchableOpacity style={styles.shareQRButton} onPress={() => {
            // This would implement QR code sharing
            showToast('QR code saved to gallery');
            hapticFeedback.success();
          }}>
            <Ionicons name="share-outline" size={20} color={COLORS.primary} />
            <Text style={styles.shareQRText}>Share QR Code</Text>
          </TouchableOpacity>
        </View>
      ) : (
        <View style={styles.qrPlaceholder}>
          <Ionicons name="qr-code-outline" size={64} color={COLORS.gray} />
          <Text style={styles.qrPlaceholderText}>
            Enter an amount to generate QR code
          </Text>
        </View>
      )}
    </View>
  );

  const renderLinkGeneration = () => (
    <View style={styles.linkSection}>
      <Text style={styles.sectionTitle}>Shareable Payment Link</Text>
      
      {amount && parseFloat(amount) > 0 ? (
        <View style={styles.linkContainer}>
          <View style={styles.linkPreview}>
            <Text style={styles.linkAmount}>{formatCurrency(parseFloat(amount))}</Text>
            {note && <Text style={styles.linkNote}>For: {note}</Text>}
            <Text style={styles.linkUsername}>To: @{user?.username}</Text>
          </View>

          {shareableLink && (
            <View style={styles.linkDisplay}>
              <Text style={styles.linkText} numberOfLines={2}>
                {shareableLink}
              </Text>
            </View>
          )}

          <TouchableOpacity style={styles.generateLinkButton} onPress={handleShareLink}>
            <LinearGradient
              colors={[COLORS.primary, COLORS.secondary]}
              style={styles.generateLinkGradient}
            >
              <Ionicons name="link-outline" size={20} color={COLORS.white} />
              <Text style={styles.generateLinkText}>
                {shareableLink ? 'Share Link' : 'Generate Link'}
              </Text>
            </LinearGradient>
          </TouchableOpacity>

          <Text style={styles.linkInstructions}>
            Anyone with this link can send you the requested amount
          </Text>
        </View>
      ) : (
        <View style={styles.linkPlaceholder}>
          <Ionicons name="link-outline" size={64} color={COLORS.gray} />
          <Text style={styles.linkPlaceholderText}>
            Enter an amount to generate payment link
          </Text>
        </View>
      )}
    </View>
  );

  const renderActionButton = () => {
    if (requestMode === 'qr' || requestMode === 'link') {
      return null; // These modes have their own action buttons
    }

    return (
      <TouchableOpacity
        style={[
          styles.requestButton,
          (!amount || (requestMode === 'contact' && selectedContacts.length === 0) || loading) && 
          styles.requestButtonDisabled
        ]}
        onPress={handleRequestMoney}
        disabled={!amount || (requestMode === 'contact' && selectedContacts.length === 0) || loading}
      >
        <LinearGradient
          colors={[COLORS.primary, COLORS.secondary]}
          style={styles.requestButtonGradient}
        >
          {loading ? (
            <ActivityIndicator size="small" color={COLORS.white} />
          ) : (
            <>
              <Ionicons name="cash-outline" size={20} color={COLORS.white} />
              <Text style={styles.requestButtonText}>Send Request</Text>
            </>
          )}
        </LinearGradient>
      </TouchableOpacity>
    );
  };

  return (
    <KeyboardAvoidingView
      style={styles.container}
      behavior={Platform.OS === 'ios' ? 'padding' : 'height'}
    >
      <View style={styles.header}>
        <TouchableOpacity
          style={styles.backButton}
          onPress={() => navigation.goBack()}
        >
          <Ionicons name="arrow-back" size={24} color={COLORS.text} />
        </TouchableOpacity>
        <Text style={styles.headerTitle}>Request Money</Text>
        <View style={styles.headerRight} />
      </View>

      <ScrollView style={styles.content} showsVerticalScrollIndicator={false}>
        {renderModeSelector()}
        {renderAmountSection()}
        
        {requestMode === 'contact' && renderContactSelection()}
        {requestMode === 'qr' && renderQRCodeGeneration()}
        {requestMode === 'link' && renderLinkGeneration()}
      </ScrollView>

      {renderActionButton()}
    </KeyboardAvoidingView>
  );
};

const styles = StyleSheet.create({
  container: {
    flex: 1,
    backgroundColor: COLORS.background,
  },
  header: {
    flexDirection: 'row',
    alignItems: 'center',
    justifyContent: 'space-between',
    paddingHorizontal: SIZES.padding,
    paddingTop: SIZES.statusBarHeight,
    paddingBottom: SIZES.base,
    backgroundColor: COLORS.white,
    borderBottomWidth: 1,
    borderBottomColor: COLORS.lightGray,
  },
  backButton: {
    padding: SIZES.base,
  },
  headerTitle: {
    fontSize: SIZES.h3,
    fontFamily: FONTS.semiBold,
    color: COLORS.text,
  },
  headerRight: {
    width: 40,
  },
  content: {
    flex: 1,
    paddingHorizontal: SIZES.padding,
  },
  modeSelector: {
    paddingVertical: SIZES.padding,
  },
  modeSelectorTitle: {
    fontSize: SIZES.h4,
    fontFamily: FONTS.semiBold,
    color: COLORS.text,
    marginBottom: SIZES.padding,
  },
  modeOption: {
    flexDirection: 'row',
    alignItems: 'center',
    justifyContent: 'space-between',
    backgroundColor: COLORS.white,
    borderRadius: SIZES.radius,
    padding: SIZES.padding,
    marginBottom: SIZES.base,
    borderWidth: 1,
    borderColor: COLORS.lightGray,
  },
  modeOptionSelected: {
    borderColor: COLORS.primary,
    backgroundColor: 'rgba(102, 126, 234, 0.05)',
  },
  modeOptionContent: {
    flexDirection: 'row',
    alignItems: 'center',
    flex: 1,
  },
  modeIcon: {
    width: 48,
    height: 48,
    borderRadius: 24,
    backgroundColor: 'rgba(102, 126, 234, 0.1)',
    alignItems: 'center',
    justifyContent: 'center',
    marginRight: SIZES.base,
  },
  modeIconSelected: {
    backgroundColor: COLORS.primary,
  },
  modeText: {
    flex: 1,
  },
  modeTitle: {
    fontSize: SIZES.body2,
    fontFamily: FONTS.semiBold,
    color: COLORS.text,
    marginBottom: 2,
  },
  modeTitleSelected: {
    color: COLORS.primary,
  },
  modeDescription: {
    fontSize: SIZES.body4,
    fontFamily: FONTS.regular,
    color: COLORS.gray,
  },
  modeDescriptionSelected: {
    color: COLORS.text,
  },
  amountSection: {
    paddingVertical: SIZES.padding,
    borderTopWidth: 1,
    borderTopColor: COLORS.lightGray,
  },
  sectionTitle: {
    fontSize: SIZES.h4,
    fontFamily: FONTS.semiBold,
    color: COLORS.text,
    marginBottom: SIZES.padding,
  },
  amountInput: {
    marginBottom: SIZES.padding,
  },
  noteContainer: {
    marginBottom: SIZES.padding,
  },
  noteLabel: {
    fontSize: SIZES.body3,
    fontFamily: FONTS.medium,
    color: COLORS.text,
    marginBottom: SIZES.base / 2,
  },
  noteInput: {
    backgroundColor: COLORS.white,
    borderRadius: SIZES.radius,
    padding: SIZES.base,
    fontSize: SIZES.body3,
    fontFamily: FONTS.regular,
    color: COLORS.text,
    borderWidth: 1,
    borderColor: COLORS.lightGray,
    minHeight: 80,
    textAlignVertical: 'top',
  },
  contactSection: {
    paddingVertical: SIZES.padding,
    borderTopWidth: 1,
    borderTopColor: COLORS.lightGray,
    flex: 1,
  },
  searchContainer: {
    flexDirection: 'row',
    alignItems: 'center',
    backgroundColor: COLORS.lightGray,
    borderRadius: SIZES.radius,
    paddingHorizontal: SIZES.base,
    marginBottom: SIZES.padding,
  },
  searchIcon: {
    marginRight: SIZES.base,
  },
  searchInput: {
    flex: 1,
    fontSize: SIZES.body3,
    fontFamily: FONTS.regular,
    color: COLORS.text,
    paddingVertical: SIZES.base,
  },
  contactsList: {
    flex: 1,
    maxHeight: 300,
  },
  loader: {
    marginTop: SIZES.padding,
  },
  emptyState: {
    alignItems: 'center',
    paddingVertical: SIZES.padding,
  },
  emptyStateText: {
    fontSize: SIZES.body3,
    fontFamily: FONTS.regular,
    color: COLORS.gray,
    marginTop: SIZES.base,
  },
  qrSection: {
    paddingVertical: SIZES.padding,
    borderTopWidth: 1,
    borderTopColor: COLORS.lightGray,
    alignItems: 'center',
  },
  qrContainer: {
    alignItems: 'center',
  },
  qrCodeWrapper: {
    backgroundColor: COLORS.white,
    padding: SIZES.padding,
    borderRadius: SIZES.radius,
    borderWidth: 1,
    borderColor: COLORS.lightGray,
    marginBottom: SIZES.padding,
  },
  qrInfo: {
    alignItems: 'center',
    marginBottom: SIZES.padding,
  },
  qrAmount: {
    fontSize: SIZES.h2,
    fontFamily: FONTS.bold,
    color: COLORS.text,
    marginBottom: SIZES.base / 2,
  },
  qrNote: {
    fontSize: SIZES.body3,
    fontFamily: FONTS.regular,
    color: COLORS.gray,
    marginBottom: SIZES.base,
  },
  qrInstructions: {
    fontSize: SIZES.body4,
    fontFamily: FONTS.regular,
    color: COLORS.gray,
    textAlign: 'center',
    paddingHorizontal: SIZES.padding,
  },
  shareQRButton: {
    flexDirection: 'row',
    alignItems: 'center',
    paddingVertical: SIZES.base,
    paddingHorizontal: SIZES.padding,
    backgroundColor: 'rgba(102, 126, 234, 0.1)',
    borderRadius: SIZES.radius,
  },
  shareQRText: {
    fontSize: SIZES.body3,
    fontFamily: FONTS.medium,
    color: COLORS.primary,
    marginLeft: SIZES.base / 2,
  },
  qrPlaceholder: {
    alignItems: 'center',
    paddingVertical: SIZES.padding * 2,
  },
  qrPlaceholderText: {
    fontSize: SIZES.body3,
    fontFamily: FONTS.regular,
    color: COLORS.gray,
    marginTop: SIZES.base,
    textAlign: 'center',
  },
  linkSection: {
    paddingVertical: SIZES.padding,
    borderTopWidth: 1,
    borderTopColor: COLORS.lightGray,
  },
  linkContainer: {
    alignItems: 'center',
  },
  linkPreview: {
    backgroundColor: COLORS.white,
    borderRadius: SIZES.radius,
    padding: SIZES.padding,
    borderWidth: 1,
    borderColor: COLORS.lightGray,
    marginBottom: SIZES.padding,
    alignItems: 'center',
    width: '100%',
  },
  linkAmount: {
    fontSize: SIZES.h2,
    fontFamily: FONTS.bold,
    color: COLORS.text,
    marginBottom: SIZES.base / 2,
  },
  linkNote: {
    fontSize: SIZES.body3,
    fontFamily: FONTS.regular,
    color: COLORS.gray,
    marginBottom: SIZES.base / 2,
  },
  linkUsername: {
    fontSize: SIZES.body4,
    fontFamily: FONTS.medium,
    color: COLORS.primary,
  },
  linkDisplay: {
    backgroundColor: COLORS.lightGray,
    borderRadius: SIZES.radius,
    padding: SIZES.base,
    marginBottom: SIZES.padding,
    width: '100%',
  },
  linkText: {
    fontSize: SIZES.body4,
    fontFamily: FONTS.regular,
    color: COLORS.text,
  },
  generateLinkButton: {
    borderRadius: SIZES.radius,
    overflow: 'hidden',
    marginBottom: SIZES.base,
  },
  generateLinkGradient: {
    flexDirection: 'row',
    alignItems: 'center',
    justifyContent: 'center',
    paddingVertical: SIZES.base,
    paddingHorizontal: SIZES.padding,
  },
  generateLinkText: {
    fontSize: SIZES.body3,
    fontFamily: FONTS.semiBold,
    color: COLORS.white,
    marginLeft: SIZES.base / 2,
  },
  linkInstructions: {
    fontSize: SIZES.body4,
    fontFamily: FONTS.regular,
    color: COLORS.gray,
    textAlign: 'center',
    paddingHorizontal: SIZES.padding,
  },
  linkPlaceholder: {
    alignItems: 'center',
    paddingVertical: SIZES.padding * 2,
  },
  linkPlaceholderText: {
    fontSize: SIZES.body3,
    fontFamily: FONTS.regular,
    color: COLORS.gray,
    marginTop: SIZES.base,
    textAlign: 'center',
  },
  requestButton: {
    marginHorizontal: SIZES.padding,
    marginVertical: SIZES.base,
    borderRadius: SIZES.radius,
    overflow: 'hidden',
  },
  requestButtonDisabled: {
    opacity: 0.6,
  },
  requestButtonGradient: {
    flexDirection: 'row',
    alignItems: 'center',
    justifyContent: 'center',
    paddingVertical: SIZES.base,
  },
  requestButtonText: {
    fontSize: SIZES.body2,
    fontFamily: FONTS.semiBold,
    color: COLORS.white,
    marginLeft: SIZES.base / 2,
  },
});

export default RequestMoneyScreen;