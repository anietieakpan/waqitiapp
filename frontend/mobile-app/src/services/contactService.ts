import { apiClient } from './apiClient';
import { Contact } from '../types';
import AsyncStorage from '@react-native-async-storage/async-storage';

const CONTACTS_CACHE_KEY = 'contacts_cache';
const CACHE_DURATION = 5 * 60 * 1000; // 5 minutes

export const contactService = {
  async getContacts(): Promise<Contact[]> {
    try {
      // Check cache first
      const cached = await this.getCachedContacts();
      if (cached) {
        return cached;
      }

      const response = await apiClient.get('/contacts');
      const contacts = response.data;
      
      // Cache the results
      await this.cacheContacts(contacts);
      
      return contacts;
    } catch (error) {
      console.error('Failed to fetch contacts:', error);
      throw error;
    }
  },

  async getContactDetails(contactId: string): Promise<Contact> {
    try {
      const response = await apiClient.get(`/contacts/${contactId}`);
      return response.data;
    } catch (error) {
      console.error('Failed to fetch contact details:', error);
      throw error;
    }
  },

  async addContact(userId: string): Promise<Contact> {
    try {
      const response = await apiClient.post('/contacts', { userId });
      
      // Invalidate cache
      await this.invalidateCache();
      
      return response.data;
    } catch (error) {
      console.error('Failed to add contact:', error);
      throw error;
    }
  },

  async removeContact(contactId: string): Promise<void> {
    try {
      await apiClient.delete(`/contacts/${contactId}`);
      
      // Invalidate cache
      await this.invalidateCache();
    } catch (error) {
      console.error('Failed to remove contact:', error);
      throw error;
    }
  },

  async toggleFavorite(contactId: string, isFavorite: boolean): Promise<Contact> {
    try {
      const response = await apiClient.patch(`/contacts/${contactId}/favorite`, {
        isFavorite,
      });
      
      // Invalidate cache
      await this.invalidateCache();
      
      return response.data;
    } catch (error) {
      console.error('Failed to toggle favorite:', error);
      throw error;
    }
  },

  async blockContact(contactId: string): Promise<void> {
    try {
      await apiClient.post(`/contacts/${contactId}/block`);
      
      // Invalidate cache
      await this.invalidateCache();
    } catch (error) {
      console.error('Failed to block contact:', error);
      throw error;
    }
  },

  async unblockContact(contactId: string): Promise<void> {
    try {
      await apiClient.delete(`/contacts/${contactId}/block`);
      
      // Invalidate cache
      await this.invalidateCache();
    } catch (error) {
      console.error('Failed to unblock contact:', error);
      throw error;
    }
  },

  async syncContacts(phoneContacts: Array<{ name: string; phoneNumber: string }>): Promise<Contact[]> {
    try {
      const response = await apiClient.post('/contacts/sync', {
        contacts: phoneContacts,
      });
      
      const syncedContacts = response.data;
      
      // Update cache with synced contacts
      await this.cacheContacts(syncedContacts);
      
      return syncedContacts;
    } catch (error) {
      console.error('Failed to sync contacts:', error);
      throw error;
    }
  },

  async searchContacts(query: string): Promise<Contact[]> {
    try {
      const response = await apiClient.get('/contacts/search', {
        params: { q: query },
      });
      return response.data;
    } catch (error) {
      console.error('Failed to search contacts:', error);
      throw error;
    }
  },

  async getRecentContacts(limit: number = 10): Promise<Contact[]> {
    try {
      const response = await apiClient.get('/contacts/recent', {
        params: { limit },
      });
      return response.data;
    } catch (error) {
      console.error('Failed to fetch recent contacts:', error);
      throw error;
    }
  },

  async getContactsByGroup(groupId: string): Promise<Contact[]> {
    try {
      const response = await apiClient.get(`/contact-groups/${groupId}/contacts`);
      return response.data;
    } catch (error) {
      console.error('Failed to fetch group contacts:', error);
      throw error;
    }
  },

  // Cache management
  async getCachedContacts(): Promise<Contact[] | null> {
    try {
      const cached = await AsyncStorage.getItem(CONTACTS_CACHE_KEY);
      if (!cached) return null;

      const { data, timestamp } = JSON.parse(cached);
      const now = Date.now();

      if (now - timestamp > CACHE_DURATION) {
        await AsyncStorage.removeItem(CONTACTS_CACHE_KEY);
        return null;
      }

      return data;
    } catch (error) {
      console.error('Failed to get cached contacts:', error);
      return null;
    }
  },

  async cacheContacts(contacts: Contact[]): Promise<void> {
    try {
      const cacheData = {
        data: contacts,
        timestamp: Date.now(),
      };
      await AsyncStorage.setItem(CONTACTS_CACHE_KEY, JSON.stringify(cacheData));
    } catch (error) {
      console.error('Failed to cache contacts:', error);
    }
  },

  async invalidateCache(): Promise<void> {
    try {
      await AsyncStorage.removeItem(CONTACTS_CACHE_KEY);
    } catch (error) {
      console.error('Failed to invalidate cache:', error);
    }
  },
};