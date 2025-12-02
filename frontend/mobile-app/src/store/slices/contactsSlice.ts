/**
 * Contacts Slice - Contact management and social features
 */

import { createSlice, createAsyncThunk, PayloadAction } from '@reduxjs/toolkit';
import AsyncStorage from '@react-native-async-storage/async-storage';
import { ApiService } from '../../services/ApiService';
import { logError, logInfo } from '../../utils/Logger';
import { PermissionsAndroid, Platform } from 'react-native';
import Contacts from 'react-native-contacts';

// Types
export interface Contact {
  id: string;
  firstName: string;
  lastName: string;
  displayName: string;
  email?: string;
  phoneNumber?: string;
  avatar?: string;
  isWaqitiUser: boolean;
  userId?: string; // If they're a Waqiti user
  relationship?: 'family' | 'friend' | 'colleague' | 'business' | 'other';
  tags?: string[];
  notes?: string;
  lastInteraction?: string;
  transactionCount?: number;
  totalSent?: number;
  totalReceived?: number;
  isFavorite: boolean;
  isBlocked: boolean;
  createdAt: string;
  updatedAt: string;
}

export interface ContactGroup {
  id: string;
  name: string;
  description?: string;
  color?: string;
  memberIds: string[];
  createdBy: string;
  createdAt: string;
  updatedAt: string;
}

export interface ContactInvitation {
  id: string;
  email?: string;
  phoneNumber?: string;
  message?: string;
  status: 'sent' | 'accepted' | 'declined' | 'expired';
  sentAt: string;
  respondedAt?: string;
}

export interface SocialActivity {
  id: string;
  type: 'payment' | 'request' | 'split' | 'achievement' | 'milestone';
  userId: string;
  userName: string;
  userAvatar?: string;
  description: string;
  amount?: number;
  currency?: string;
  timestamp: string;
  isVisible: boolean;
  likes: number;
  comments: Array<{
    id: string;
    userId: string;
    userName: string;
    message: string;
    timestamp: string;
  }>;
  hasLiked: boolean;
}

export interface ContactSuggestion {
  id: string;
  type: 'phone_contacts' | 'social_media' | 'mutual_friends' | 'frequent_recipients';
  firstName: string;
  lastName: string;
  email?: string;
  phoneNumber?: string;
  avatar?: string;
  mutualFriends?: number;
  reason: string;
  confidence: number; // 0-100
}

export interface ContactsState {
  // Contacts
  contacts: Contact[];
  contactGroups: ContactGroup[];
  favoriteContacts: Contact[];
  recentContacts: Contact[];
  blockedContacts: Contact[];
  
  // Phone book integration
  phoneContacts: Array<{
    id: string;
    name: string;
    phoneNumber: string;
    email?: string;
    isWaqitiUser: boolean;
  }>;
  phoneContactsPermission: 'granted' | 'denied' | 'not_requested';
  phoneContactsSynced: boolean;
  
  // Invitations
  sentInvitations: ContactInvitation[];
  
  // Suggestions
  contactSuggestions: ContactSuggestion[];
  
  // Social features
  socialFeed: SocialActivity[];
  socialSettings: {
    shareTransactions: boolean;
    shareAchievements: boolean;
    allowTagging: boolean;
    privacyLevel: 'public' | 'friends' | 'private';
  };
  
  // Search and filters
  searchQuery: string;
  filters: {
    relationship?: string;
    isWaqitiUser?: boolean;
    hasTransacted?: boolean;
    tags?: string[];
  };
  
  // Statistics
  statistics: {
    totalContacts: number;
    waqitiUsers: number;
    phoneContacts: number;
    activeContacts: number; // Contacts with recent transactions
    topTransactionPartner?: Contact;
  };
  
  // UI state
  isLoading: boolean;
  isSyncing: boolean;
  error: string | null;
  selectedContactId: string | null;
}

// Storage keys
const STORAGE_KEYS = {
  CONTACTS: '@waqiti_contacts',
  PHONE_CONTACTS: '@waqiti_phone_contacts',
  SOCIAL_SETTINGS: '@waqiti_social_settings',
  CONTACTS_SYNCED: '@waqiti_contacts_synced',
};

// Initial state
const initialState: ContactsState = {
  contacts: [],
  contactGroups: [],
  favoriteContacts: [],
  recentContacts: [],
  blockedContacts: [],
  phoneContacts: [],
  phoneContactsPermission: 'not_requested',
  phoneContactsSynced: false,
  sentInvitations: [],
  contactSuggestions: [],
  socialFeed: [],
  socialSettings: {
    shareTransactions: false,
    shareAchievements: true,
    allowTagging: true,
    privacyLevel: 'friends',
  },
  searchQuery: '',
  filters: {},
  statistics: {
    totalContacts: 0,
    waqitiUsers: 0,
    phoneContacts: 0,
    activeContacts: 0,
  },
  isLoading: false,
  isSyncing: false,
  error: null,
  selectedContactId: null,
};

// Async thunks
export const fetchContacts = createAsyncThunk(
  'contacts/fetch',
  async (_, { rejectWithValue }) => {
    try {
      const response = await ApiService.get('/contacts');
      
      // Cache contacts locally
      await AsyncStorage.setItem(STORAGE_KEYS.CONTACTS, JSON.stringify(response.data.contacts));
      
      return response.data;
    } catch (error: any) {
      logError('Failed to fetch contacts', {
        feature: 'contacts_slice',
        action: 'fetch_contacts_failed'
      }, error);
      
      // Try to load from cache
      try {
        const cachedContacts = await AsyncStorage.getItem(STORAGE_KEYS.CONTACTS);
        if (cachedContacts) {
          return { contacts: JSON.parse(cachedContacts), fromCache: true };
        }
      } catch (cacheError) {
        logError('Failed to load cached contacts', { feature: 'contacts_slice' }, cacheError as Error);
      }
      
      return rejectWithValue(error.message || 'Failed to fetch contacts');
    }
  }
);

export const addContact = createAsyncThunk(
  'contacts/add',
  async (contactData: {
    firstName: string;
    lastName: string;
    email?: string;
    phoneNumber?: string;
    relationship?: string;
    tags?: string[];
    notes?: string;
  }, { rejectWithValue }) => {
    try {
      const response = await ApiService.post('/contacts', contactData);
      
      // Track event
      await ApiService.trackEvent('contact_added', {
        relationship: contactData.relationship,
        hasEmail: !!contactData.email,
        hasPhone: !!contactData.phoneNumber,
        timestamp: new Date().toISOString(),
      });
      
      return response.data;
    } catch (error: any) {
      logError('Failed to add contact', {
        feature: 'contacts_slice',
        action: 'add_contact_failed'
      }, error);
      return rejectWithValue(error.message || 'Failed to add contact');
    }
  }
);

export const updateContact = createAsyncThunk(
  'contacts/update',
  async (data: { contactId: string; updates: Partial<Contact> }, { rejectWithValue }) => {
    try {
      const response = await ApiService.put(`/contacts/${data.contactId}`, data.updates);
      
      // Track event
      await ApiService.trackEvent('contact_updated', {
        contactId: data.contactId,
        fieldsUpdated: Object.keys(data.updates),
        timestamp: new Date().toISOString(),
      });
      
      return response.data;
    } catch (error: any) {
      logError('Failed to update contact', {
        feature: 'contacts_slice',
        action: 'update_contact_failed'
      }, error);
      return rejectWithValue(error.message || 'Failed to update contact');
    }
  }
);

export const deleteContact = createAsyncThunk(
  'contacts/delete',
  async (contactId: string, { rejectWithValue }) => {
    try {
      await ApiService.delete(`/contacts/${contactId}`);
      
      // Track event
      await ApiService.trackEvent('contact_deleted', {
        contactId,
        timestamp: new Date().toISOString(),
      });
      
      return contactId;
    } catch (error: any) {
      logError('Failed to delete contact', {
        feature: 'contacts_slice',
        action: 'delete_contact_failed'
      }, error);
      return rejectWithValue(error.message || 'Failed to delete contact');
    }
  }
);

export const requestContactsPermission = createAsyncThunk(
  'contacts/requestPermission',
  async (_, { rejectWithValue }) => {
    try {
      if (Platform.OS === 'android') {
        const granted = await PermissionsAndroid.request(
          PermissionsAndroid.PERMISSIONS.READ_CONTACTS,
          {
            title: 'Contacts Permission',
            message: 'Waqiti would like to access your contacts to help you find friends and send payments.',
            buttonNeutral: 'Ask Me Later',
            buttonNegative: 'Cancel',
            buttonPositive: 'OK',
          }
        );
        return granted === PermissionsAndroid.RESULTS.GRANTED ? 'granted' : 'denied';
      } else {
        // iOS permission is handled by the Contacts library
        return 'granted';
      }
    } catch (error: any) {
      logError('Failed to request contacts permission', {
        feature: 'contacts_slice',
        action: 'request_permission_failed'
      }, error);
      return rejectWithValue('Failed to request contacts permission');
    }
  }
);

export const syncPhoneContacts = createAsyncThunk(
  'contacts/syncPhone',
  async (_, { rejectWithValue }) => {
    try {
      // Get phone contacts
      const phoneContacts = await Contacts.getAll();
      
      // Filter and format contacts
      const formattedContacts = phoneContacts
        .filter(contact => contact.phoneNumbers.length > 0 || contact.emailAddresses.length > 0)
        .map(contact => ({
          id: contact.recordID,
          name: `${contact.givenName || ''} ${contact.familyName || ''}`.trim() || 'Unknown',
          phoneNumber: contact.phoneNumbers[0]?.number || '',
          email: contact.emailAddresses[0]?.email || '',
          isWaqitiUser: false, // Will be determined by server
        }));
      
      // Cache phone contacts
      await AsyncStorage.setItem(STORAGE_KEYS.PHONE_CONTACTS, JSON.stringify(formattedContacts));
      
      // Send to server to check which ones are Waqiti users
      const response = await ApiService.post('/contacts/sync-phone', {
        contacts: formattedContacts.map(c => ({
          phoneNumber: c.phoneNumber,
          email: c.email,
        })),
      });
      
      // Mark sync as completed
      await AsyncStorage.setItem(STORAGE_KEYS.CONTACTS_SYNCED, 'true');
      
      // Track event
      await ApiService.trackEvent('phone_contacts_synced', {
        totalContacts: formattedContacts.length,
        waqitiUsers: response.data.waqitiUsers || 0,
        timestamp: new Date().toISOString(),
      });
      
      return {
        phoneContacts: formattedContacts,
        waqitiUsers: response.data.waqitiUsers || [],
        suggestions: response.data.suggestions || [],
      };
    } catch (error: any) {
      logError('Failed to sync phone contacts', {
        feature: 'contacts_slice',
        action: 'sync_phone_contacts_failed'
      }, error);
      return rejectWithValue(error.message || 'Failed to sync phone contacts');
    }
  }
);

export const sendInvitation = createAsyncThunk(
  'contacts/sendInvitation',
  async (data: {
    email?: string;
    phoneNumber?: string;
    message?: string;
  }, { rejectWithValue }) => {
    try {
      const response = await ApiService.post('/contacts/invite', data);
      
      // Track event
      await ApiService.trackEvent('invitation_sent', {
        method: data.email ? 'email' : 'sms',
        timestamp: new Date().toISOString(),
      });
      
      return response.data;
    } catch (error: any) {
      logError('Failed to send invitation', {
        feature: 'contacts_slice',
        action: 'send_invitation_failed'
      }, error);
      return rejectWithValue(error.message || 'Failed to send invitation');
    }
  }
);

export const createContactGroup = createAsyncThunk(
  'contacts/createGroup',
  async (groupData: {
    name: string;
    description?: string;
    color?: string;
    memberIds: string[];
  }, { rejectWithValue }) => {
    try {
      const response = await ApiService.post('/contacts/groups', groupData);
      
      // Track event
      await ApiService.trackEvent('contact_group_created', {
        memberCount: groupData.memberIds.length,
        timestamp: new Date().toISOString(),
      });
      
      return response.data;
    } catch (error: any) {
      logError('Failed to create contact group', {
        feature: 'contacts_slice',
        action: 'create_group_failed'
      }, error);
      return rejectWithValue(error.message || 'Failed to create contact group');
    }
  }
);

export const fetchSocialFeed = createAsyncThunk(
  'contacts/fetchSocialFeed',
  async (params: { page?: number; limit?: number } = {}, { rejectWithValue }) => {
    try {
      const response = await ApiService.get('/social/feed', { params });
      return response.data;
    } catch (error: any) {
      logError('Failed to fetch social feed', {
        feature: 'contacts_slice',
        action: 'fetch_social_feed_failed'
      }, error);
      return rejectWithValue(error.message || 'Failed to fetch social feed');
    }
  }
);

export const likeSocialActivity = createAsyncThunk(
  'contacts/likeSocialActivity',
  async (activityId: string, { rejectWithValue }) => {
    try {
      const response = await ApiService.post(`/social/activities/${activityId}/like`);
      
      // Track event
      await ApiService.trackEvent('social_activity_liked', {
        activityId,
        timestamp: new Date().toISOString(),
      });
      
      return { activityId, ...response.data };
    } catch (error: any) {
      logError('Failed to like social activity', {
        feature: 'contacts_slice',
        action: 'like_activity_failed'
      }, error);
      return rejectWithValue(error.message || 'Failed to like activity');
    }
  }
);

export const commentOnSocialActivity = createAsyncThunk(
  'contacts/commentOnActivity',
  async (data: { activityId: string; message: string }, { rejectWithValue }) => {
    try {
      const response = await ApiService.post(`/social/activities/${data.activityId}/comment`, {
        message: data.message,
      });
      
      // Track event
      await ApiService.trackEvent('social_activity_commented', {
        activityId: data.activityId,
        timestamp: new Date().toISOString(),
      });
      
      return { activityId: data.activityId, comment: response.data };
    } catch (error: any) {
      logError('Failed to comment on social activity', {
        feature: 'contacts_slice',
        action: 'comment_activity_failed'
      }, error);
      return rejectWithValue(error.message || 'Failed to comment on activity');
    }
  }
);

// Contacts slice
const contactsSlice = createSlice({
  name: 'contacts',
  initialState,
  reducers: {
    clearError: (state) => {
      state.error = null;
    },
    setSearchQuery: (state, action: PayloadAction<string>) => {
      state.searchQuery = action.payload;
    },
    setFilters: (state, action: PayloadAction<ContactsState['filters']>) => {
      state.filters = action.payload;
    },
    clearFilters: (state) => {
      state.filters = {};
      state.searchQuery = '';
    },
    selectContact: (state, action: PayloadAction<string | null>) => {
      state.selectedContactId = action.payload;
    },
    toggleFavorite: (state, action: PayloadAction<string>) => {
      const contact = state.contacts.find(c => c.id === action.payload);
      if (contact) {
        contact.isFavorite = !contact.isFavorite;
        
        // Update favorites list
        if (contact.isFavorite) {
          state.favoriteContacts.push(contact);
        } else {
          state.favoriteContacts = state.favoriteContacts.filter(c => c.id !== contact.id);
        }
      }
    },
    blockContact: (state, action: PayloadAction<string>) => {
      const contact = state.contacts.find(c => c.id === action.payload);
      if (contact) {
        contact.isBlocked = true;
        state.blockedContacts.push(contact);
      }
    },
    unblockContact: (state, action: PayloadAction<string>) => {
      const contact = state.contacts.find(c => c.id === action.payload);
      if (contact) {
        contact.isBlocked = false;
        state.blockedContacts = state.blockedContacts.filter(c => c.id !== contact.id);
      }
    },
    updateSocialSettings: (state, action: PayloadAction<Partial<ContactsState['socialSettings']>>) => {
      state.socialSettings = { ...state.socialSettings, ...action.payload };
      AsyncStorage.setItem(STORAGE_KEYS.SOCIAL_SETTINGS, JSON.stringify(state.socialSettings));
    },
    addToRecentContacts: (state, action: PayloadAction<string>) => {
      const contact = state.contacts.find(c => c.id === action.payload);
      if (contact) {
        // Remove if already in recent
        state.recentContacts = state.recentContacts.filter(c => c.id !== contact.id);
        // Add to beginning
        state.recentContacts.unshift(contact);
        // Keep only last 10
        if (state.recentContacts.length > 10) {
          state.recentContacts = state.recentContacts.slice(0, 10);
        }
      }
    },
    dismissSuggestion: (state, action: PayloadAction<string>) => {
      state.contactSuggestions = state.contactSuggestions.filter(s => s.id !== action.payload);
    },
    updateStatistics: (state, action: PayloadAction<Partial<ContactsState['statistics']>>) => {
      state.statistics = { ...state.statistics, ...action.payload };
    },
  },
  extraReducers: (builder) => {
    // Fetch contacts
    builder
      .addCase(fetchContacts.pending, (state) => {
        state.isLoading = true;
        state.error = null;
      })
      .addCase(fetchContacts.fulfilled, (state, action) => {
        state.isLoading = false;
        state.contacts = action.payload.contacts;
        state.contactGroups = action.payload.groups || [];
        state.favoriteContacts = action.payload.contacts.filter((c: Contact) => c.isFavorite);
        state.statistics = action.payload.statistics || state.statistics;
      })
      .addCase(fetchContacts.rejected, (state, action) => {
        state.isLoading = false;
        state.error = action.payload as string;
      });

    // Add contact
    builder
      .addCase(addContact.pending, (state) => {
        state.isLoading = true;
        state.error = null;
      })
      .addCase(addContact.fulfilled, (state, action) => {
        state.isLoading = false;
        state.contacts.push(action.payload);
        state.statistics.totalContacts += 1;
        if (action.payload.isWaqitiUser) {
          state.statistics.waqitiUsers += 1;
        }
      })
      .addCase(addContact.rejected, (state, action) => {
        state.isLoading = false;
        state.error = action.payload as string;
      });

    // Update contact
    builder
      .addCase(updateContact.fulfilled, (state, action) => {
        const index = state.contacts.findIndex(c => c.id === action.payload.id);
        if (index !== -1) {
          state.contacts[index] = action.payload;
        }
      });

    // Delete contact
    builder
      .addCase(deleteContact.fulfilled, (state, action) => {
        state.contacts = state.contacts.filter(c => c.id !== action.payload);
        state.favoriteContacts = state.favoriteContacts.filter(c => c.id !== action.payload);
        state.recentContacts = state.recentContacts.filter(c => c.id !== action.payload);
        state.statistics.totalContacts = Math.max(0, state.statistics.totalContacts - 1);
      });

    // Request contacts permission
    builder
      .addCase(requestContactsPermission.fulfilled, (state, action) => {
        state.phoneContactsPermission = action.payload;
      });

    // Sync phone contacts
    builder
      .addCase(syncPhoneContacts.pending, (state) => {
        state.isSyncing = true;
        state.error = null;
      })
      .addCase(syncPhoneContacts.fulfilled, (state, action) => {
        state.isSyncing = false;
        state.phoneContacts = action.payload.phoneContacts;
        state.phoneContactsSynced = true;
        state.contactSuggestions = action.payload.suggestions;
        state.statistics.phoneContacts = action.payload.phoneContacts.length;
        
        // Mark which phone contacts are Waqiti users
        action.payload.waqitiUsers.forEach((waqitiUser: any) => {
          const phoneContact = state.phoneContacts.find(
            pc => pc.phoneNumber === waqitiUser.phoneNumber || pc.email === waqitiUser.email
          );
          if (phoneContact) {
            phoneContact.isWaqitiUser = true;
          }
        });
      })
      .addCase(syncPhoneContacts.rejected, (state, action) => {
        state.isSyncing = false;
        state.error = action.payload as string;
      });

    // Send invitation
    builder
      .addCase(sendInvitation.fulfilled, (state, action) => {
        state.sentInvitations.push(action.payload);
      });

    // Create contact group
    builder
      .addCase(createContactGroup.fulfilled, (state, action) => {
        state.contactGroups.push(action.payload);
      });

    // Fetch social feed
    builder
      .addCase(fetchSocialFeed.fulfilled, (state, action) => {
        state.socialFeed = action.payload.activities;
      });

    // Like social activity
    builder
      .addCase(likeSocialActivity.fulfilled, (state, action) => {
        const activity = state.socialFeed.find(a => a.id === action.payload.activityId);
        if (activity) {
          activity.hasLiked = action.payload.hasLiked;
          activity.likes = action.payload.likes;
        }
      });

    // Comment on social activity
    builder
      .addCase(commentOnSocialActivity.fulfilled, (state, action) => {
        const activity = state.socialFeed.find(a => a.id === action.payload.activityId);
        if (activity) {
          activity.comments.push(action.payload.comment);
        }
      });
  },
});

export const {
  clearError,
  setSearchQuery,
  setFilters,
  clearFilters,
  selectContact,
  toggleFavorite,
  blockContact,
  unblockContact,
  updateSocialSettings,
  addToRecentContacts,
  dismissSuggestion,
  updateStatistics,
} = contactsSlice.actions;

export default contactsSlice.reducer;