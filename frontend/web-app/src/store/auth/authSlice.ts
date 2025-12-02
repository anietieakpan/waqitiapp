import { createSlice, createAsyncThunk, PayloadAction } from '@reduxjs/toolkit';
import { authAPI } from '@/services/authAPI';

export interface User {
  id: string;
  email: string;
  firstName: string;
  lastName: string;
  phoneNumber?: string;
  kycStatus: 'PENDING' | 'VERIFIED' | 'REJECTED';
  mfaEnabled: boolean;
  status: 'ACTIVE' | 'INACTIVE' | 'SUSPENDED';
}

export interface AuthState {
  user: User | null;
  isAuthenticated: boolean;
  isLoading: boolean;
  error: string | null;
  mfaRequired: boolean;
  tempToken: string | null;
}

// SECURITY FIX: Tokens now in httpOnly cookies (XSS protection)
// No tokens stored in Redux state or localStorage
const initialState: AuthState = {
  user: null,
  isAuthenticated: localStorage.getItem('isAuthenticated') === 'true',
  isLoading: false,
  error: null,
  mfaRequired: false,
  tempToken: null,
};

// Async thunks
export const loginUser = createAsyncThunk(
  'auth/loginUser',
  async (credentials: { email: string; password: string }, { rejectWithValue }) => {
    try {
      const response = await authAPI.login(credentials);
      return response.data;
    } catch (error: any) {
      return rejectWithValue(error.response?.data?.message || 'Login failed');
    }
  }
);

export const registerUser = createAsyncThunk(
  'auth/registerUser',
  async (userData: {
    email: string;
    password: string;
    firstName: string;
    lastName: string;
    phoneNumber?: string;
  }, { rejectWithValue }) => {
    try {
      const response = await authAPI.register(userData);
      return response.data;
    } catch (error: any) {
      return rejectWithValue(error.response?.data?.message || 'Registration failed');
    }
  }
);

export const verifyMFA = createAsyncThunk(
  'auth/verifyMFA',
  async (data: { tempToken: string; code: string }, { rejectWithValue }) => {
    try {
      const response = await authAPI.verifyMFA(data);
      return response.data;
    } catch (error: any) {
      return rejectWithValue(error.response?.data?.message || 'MFA verification failed');
    }
  }
);

export const refreshToken = createAsyncThunk(
  'auth/refreshToken',
  async (_, { rejectWithValue }) => {
    try {
      // Refresh token is in httpOnly cookie - backend handles it
      const response = await authAPI.refreshToken();
      return response.data;
    } catch (error: any) {
      return rejectWithValue(error.response?.data?.message || 'Token refresh failed');
    }
  }
);

export const logoutUser = createAsyncThunk(
  'auth/logoutUser',
  async () => {
    try {
      // Backend clears httpOnly cookies
      await authAPI.logout();
    } catch (error) {
      console.warn('Logout API call failed:', error);
    }
    localStorage.removeItem('isAuthenticated');
    return null;
  }
);

const authSlice = createSlice({
  name: 'auth',
  initialState,
  reducers: {
    clearError: (state) => {
      state.error = null;
    },
    setAuthenticated: (state, action: PayloadAction<boolean>) => {
      state.isAuthenticated = action.payload;
      localStorage.setItem('isAuthenticated', String(action.payload));
    },
    clearAuth: (state) => {
      state.isAuthenticated = false;
      state.user = null;
      localStorage.removeItem('isAuthenticated');
    },
  },
  extraReducers: (builder) => {
    builder
      // Login
      .addCase(loginUser.pending, (state) => {
        state.isLoading = true;
        state.error = null;
      })
      .addCase(loginUser.fulfilled, (state, action) => {
        state.isLoading = false;
        if (action.payload.mfaRequired) {
          state.mfaRequired = true;
          state.tempToken = action.payload.tempToken;
        } else {
          state.user = action.payload.user;
          state.isAuthenticated = true;
          localStorage.setItem('isAuthenticated', 'true');
          // Tokens are in httpOnly cookies - not in state
        }
      })
      .addCase(loginUser.rejected, (state, action) => {
        state.isLoading = false;
        state.error = action.payload as string;
      })
      
      // Register
      .addCase(registerUser.pending, (state) => {
        state.isLoading = true;
        state.error = null;
      })
      .addCase(registerUser.fulfilled, (state) => {
        state.isLoading = false;
        // Registration successful, user needs to verify email
      })
      .addCase(registerUser.rejected, (state, action) => {
        state.isLoading = false;
        state.error = action.payload as string;
      })
      
      // MFA Verification
      .addCase(verifyMFA.pending, (state) => {
        state.isLoading = true;
        state.error = null;
      })
      .addCase(verifyMFA.fulfilled, (state, action) => {
        state.isLoading = false;
        state.mfaRequired = false;
        state.tempToken = null;
        state.user = action.payload.user;
        state.isAuthenticated = true;
        localStorage.setItem('isAuthenticated', 'true');
        // Tokens are in httpOnly cookies
      })
      .addCase(verifyMFA.rejected, (state, action) => {
        state.isLoading = false;
        state.error = action.payload as string;
      })
      
      // Token Refresh
      .addCase(refreshToken.fulfilled, (state, action) => {
        // Tokens updated in httpOnly cookies by backend
        state.isAuthenticated = true;
      })
      .addCase(refreshToken.rejected, (state) => {
        state.isAuthenticated = false;
        state.user = null;
        localStorage.removeItem('isAuthenticated');
      })
      
      // Logout
      .addCase(logoutUser.fulfilled, (state) => {
        state.user = null;
        state.isAuthenticated = false;
        state.mfaRequired = false;
        state.tempToken = null;
        state.error = null;
      });
  },
});

export const { clearError, setAuthenticated, clearAuth } = authSlice.actions;
export default authSlice.reducer;