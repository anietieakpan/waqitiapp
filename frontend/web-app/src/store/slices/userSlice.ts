import { createSlice, createAsyncThunk, PayloadAction } from '@reduxjs/toolkit';
import { userService } from '../../services/userService';
import { User } from '../../types/user';

interface UserState {
  currentUser: User | null;
  userProfile: User | null;
  loading: boolean;
  profileLoading: boolean;
  updateLoading: boolean;
  
  // KYC related
  kycStatus: 'not_started' | 'pending' | 'approved' | 'rejected';
  kycDocuments: any[];
  kycLoading: boolean;
  
  // Verification
  emailVerified: boolean;
  phoneVerified: boolean;
  identityVerified: boolean;
  
  // Security
  mfaEnabled: boolean;
  mfaSetupLoading: boolean;
  
  // Settings
  preferences: {
    notifications: {
      email: boolean;
      push: boolean;
      sms: boolean;
    };
    privacy: {
      profileVisibility: 'public' | 'friends' | 'private';
      transactionVisibility: 'public' | 'friends' | 'private';
    };
    security: {
      requireMfaForTransactions: boolean;
      requireMfaForHighValue: boolean;
      sessionTimeout: number;
    };
  };
  
  // Limits and restrictions
  dailyLimits: {
    send: number;
    receive: number;
    withdraw: number;
  };
  
  error: string | null;
}

const initialState: UserState = {
  currentUser: null,
  userProfile: null,
  loading: false,
  profileLoading: false,
  updateLoading: false,
  
  kycStatus: 'not_started',
  kycDocuments: [],
  kycLoading: false,
  
  emailVerified: false,
  phoneVerified: false,
  identityVerified: false,
  
  mfaEnabled: false,
  mfaSetupLoading: false,
  
  preferences: {
    notifications: {
      email: true,
      push: true,
      sms: false,
    },
    privacy: {
      profileVisibility: 'friends',
      transactionVisibility: 'friends',
    },
    security: {
      requireMfaForTransactions: false,
      requireMfaForHighValue: true,
      sessionTimeout: 30,
    },
  },
  
  dailyLimits: {
    send: 2500,
    receive: 10000,
    withdraw: 1000,
  },
  
  error: null,
};

// Async thunks
export const fetchCurrentUser = createAsyncThunk(
  'user/fetchCurrentUser',
  async (_, { rejectWithValue }) => {
    try {
      return await userService.getCurrentUser();
    } catch (error: any) {
      return rejectWithValue(error.response?.data?.message || error.message);
    }
  }
);

export const updateUserProfile = createAsyncThunk(
  'user/updateUserProfile',
  async (updates: Partial<User>, { rejectWithValue }) => {
    try {
      return await userService.updateProfile(updates);
    } catch (error: any) {
      return rejectWithValue(error.response?.data?.message || error.message);
    }
  }
);

export const updateUserPreferences = createAsyncThunk(
  'user/updateUserPreferences',
  async (preferences: any, { rejectWithValue }) => {
    try {
      return await userService.updatePreferences(preferences);
    } catch (error: any) {
      return rejectWithValue(error.response?.data?.message || error.message);
    }
  }
);

export const uploadKycDocument = createAsyncThunk(
  'user/uploadKycDocument',
  async (document: { type: string; file: File }, { rejectWithValue }) => {
    try {
      return await userService.uploadKycDocument(document);
    } catch (error: any) {
      return rejectWithValue(error.response?.data?.message || error.message);
    }
  }
);

export const submitKycApplication = createAsyncThunk(
  'user/submitKycApplication',
  async (_, { rejectWithValue }) => {
    try {
      return await userService.submitKycApplication();
    } catch (error: any) {
      return rejectWithValue(error.response?.data?.message || error.message);
    }
  }
);

export const verifyEmail = createAsyncThunk(
  'user/verifyEmail',
  async (token: string, { rejectWithValue }) => {
    try {
      return await userService.verifyEmail(token);
    } catch (error: any) {
      return rejectWithValue(error.response?.data?.message || error.message);
    }
  }
);

export const verifyPhone = createAsyncThunk(
  'user/verifyPhone',
  async (code: string, { rejectWithValue }) => {
    try {
      return await userService.verifyPhone(code);
    } catch (error: any) {
      return rejectWithValue(error.response?.data?.message || error.message);
    }
  }
);

export const setupMfa = createAsyncThunk(
  'user/setupMfa',
  async (params: { method: string }, { rejectWithValue }) => {
    try {
      return await userService.setupMfa(params);
    } catch (error: any) {
      return rejectWithValue(error.response?.data?.message || error.message);
    }
  }
);

export const disableMfa = createAsyncThunk(
  'user/disableMfa',
  async (_, { rejectWithValue }) => {
    try {
      return await userService.disableMfa();
    } catch (error: any) {
      return rejectWithValue(error.response?.data?.message || error.message);
    }
  }
);

export const changePassword = createAsyncThunk(
  'user/changePassword',
  async (params: { currentPassword: string; newPassword: string }, { rejectWithValue }) => {
    try {
      return await userService.changePassword(params);
    } catch (error: any) {
      return rejectWithValue(error.response?.data?.message || error.message);
    }
  }
);

export const deleteAccount = createAsyncThunk(
  'user/deleteAccount',
  async (password: string, { rejectWithValue }) => {
    try {
      return await userService.deleteAccount(password);
    } catch (error: any) {
      return rejectWithValue(error.response?.data?.message || error.message);
    }
  }
);

const userSlice = createSlice({
  name: 'user',
  initialState,
  reducers: {
    clearError: (state) => {
      state.error = null;
    },
    
    setCurrentUser: (state, action: PayloadAction<User | null>) => {
      state.currentUser = action.payload;
    },
    
    updateKycStatus: (state, action: PayloadAction<UserState['kycStatus']>) => {
      state.kycStatus = action.payload;
    },
    
    setEmailVerified: (state, action: PayloadAction<boolean>) => {
      state.emailVerified = action.payload;
    },
    
    setPhoneVerified: (state, action: PayloadAction<boolean>) => {
      state.phoneVerified = action.payload;
    },
    
    setMfaEnabled: (state, action: PayloadAction<boolean>) => {
      state.mfaEnabled = action.payload;
    },
    
    updatePreferences: (state, action: PayloadAction<Partial<UserState['preferences']>>) => {
      state.preferences = { ...state.preferences, ...action.payload };
    },
    
    updateDailyLimits: (state, action: PayloadAction<Partial<UserState['dailyLimits']>>) => {
      state.dailyLimits = { ...state.dailyLimits, ...action.payload };
    },
    
    addKycDocument: (state, action: PayloadAction<any>) => {
      state.kycDocuments.push(action.payload);
    },
    
    removeKycDocument: (state, action: PayloadAction<string>) => {
      state.kycDocuments = state.kycDocuments.filter(doc => doc.id !== action.payload);
    },
  },
  
  extraReducers: (builder) => {
    builder
      // Fetch current user
      .addCase(fetchCurrentUser.pending, (state) => {
        state.loading = true;
        state.error = null;
      })
      .addCase(fetchCurrentUser.fulfilled, (state, action) => {
        state.loading = false;
        state.currentUser = action.payload;
        state.kycStatus = action.payload.kycStatus || 'not_started';
        state.emailVerified = action.payload.emailVerified || false;
        state.phoneVerified = action.payload.phoneVerified || false;
        state.identityVerified = action.payload.identityVerified || false;
        state.mfaEnabled = action.payload.mfaEnabled || false;
      })
      .addCase(fetchCurrentUser.rejected, (state, action) => {
        state.loading = false;
        state.error = action.payload as string;
      })
      
      // Update user profile
      .addCase(updateUserProfile.pending, (state) => {
        state.updateLoading = true;
        state.error = null;
      })
      .addCase(updateUserProfile.fulfilled, (state, action) => {
        state.updateLoading = false;
        state.currentUser = action.payload;
      })
      .addCase(updateUserProfile.rejected, (state, action) => {
        state.updateLoading = false;
        state.error = action.payload as string;
      })
      
      // Update preferences
      .addCase(updateUserPreferences.fulfilled, (state, action) => {
        state.preferences = action.payload;
      })
      .addCase(updateUserPreferences.rejected, (state, action) => {
        state.error = action.payload as string;
      })
      
      // Upload KYC document
      .addCase(uploadKycDocument.pending, (state) => {
        state.kycLoading = true;
      })
      .addCase(uploadKycDocument.fulfilled, (state, action) => {
        state.kycLoading = false;
        state.kycDocuments.push(action.payload);
      })
      .addCase(uploadKycDocument.rejected, (state, action) => {
        state.kycLoading = false;
        state.error = action.payload as string;
      })
      
      // Submit KYC application
      .addCase(submitKycApplication.pending, (state) => {
        state.kycLoading = true;
      })
      .addCase(submitKycApplication.fulfilled, (state) => {
        state.kycLoading = false;
        state.kycStatus = 'pending';
      })
      .addCase(submitKycApplication.rejected, (state, action) => {
        state.kycLoading = false;
        state.error = action.payload as string;
      })
      
      // Verify email
      .addCase(verifyEmail.fulfilled, (state) => {
        state.emailVerified = true;
      })
      .addCase(verifyEmail.rejected, (state, action) => {
        state.error = action.payload as string;
      })
      
      // Verify phone
      .addCase(verifyPhone.fulfilled, (state) => {
        state.phoneVerified = true;
      })
      .addCase(verifyPhone.rejected, (state, action) => {
        state.error = action.payload as string;
      })
      
      // Setup MFA
      .addCase(setupMfa.pending, (state) => {
        state.mfaSetupLoading = true;
      })
      .addCase(setupMfa.fulfilled, (state) => {
        state.mfaSetupLoading = false;
        state.mfaEnabled = true;
      })
      .addCase(setupMfa.rejected, (state, action) => {
        state.mfaSetupLoading = false;
        state.error = action.payload as string;
      })
      
      // Disable MFA
      .addCase(disableMfa.fulfilled, (state) => {
        state.mfaEnabled = false;
      })
      .addCase(disableMfa.rejected, (state, action) => {
        state.error = action.payload as string;
      })
      
      // Change password
      .addCase(changePassword.fulfilled, (state) => {
        // Password changed successfully
      })
      .addCase(changePassword.rejected, (state, action) => {
        state.error = action.payload as string;
      });
  },
});

export const {
  clearError,
  setCurrentUser,
  updateKycStatus,
  setEmailVerified,
  setPhoneVerified,
  setMfaEnabled,
  updatePreferences,
  updateDailyLimits,
  addKycDocument,
  removeKycDocument,
} = userSlice.actions;

export default userSlice.reducer;