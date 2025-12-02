export interface Contact {
  id: string;
  userId: string;
  displayName: string;
  firstName: string;
  lastName: string;
  username?: string;
  email?: string;
  phoneNumber?: string;
  avatar?: string;
  bio?: string;
  verified: boolean;
  isFavorite: boolean;
  isBlocked: boolean;
  addedAt: Date;
  lastTransactionDate?: Date;
  mutualContacts?: number;
}

export interface ContactGroup {
  id: string;
  name: string;
  description?: string;
  memberCount: number;
  createdAt: Date;
  updatedAt: Date;
}

export interface ContactInvite {
  id: string;
  phoneNumber: string;
  email?: string;
  name: string;
  status: 'pending' | 'sent' | 'accepted' | 'expired';
  invitedAt: Date;
  expiresAt: Date;
}