import { createSlice, createAsyncThunk, PayloadAction } from '@reduxjs/toolkit';
import { authService } from '../../services/authService';
import { User, LoginCredentials, AuthState } from '../../types/auth';

const initialState: AuthState = {
  user: null,
  token: localStorage.getItem('admin_token'),
  isAuthenticated: false,
  isLoading: false,
  error: null,
  permissions: [],
};

export const login = createAsyncThunk(
  'auth/login',
  async (credentials: LoginCredentials) => {
    const response = await authService.login(credentials);
    localStorage.setItem('admin_token', response.token);
    return response;
  }
);

export const logout = createAsyncThunk('auth/logout', async () => {
  await authService.logout();
  localStorage.removeItem('admin_token');
});

export const checkAuth = createAsyncThunk('auth/checkAuth', async () => {
  const token = localStorage.getItem('admin_token');
  if (!token) {
    throw new Error('No token found');
  }
  return await authService.verifyToken(token);
});

export const refreshToken = createAsyncThunk('auth/refreshToken', async () => {
  const response = await authService.refreshToken();
  localStorage.setItem('admin_token', response.token);
  return response;
});

const authSlice = createSlice({
  name: 'auth',
  initialState,
  reducers: {
    clearError: (state) => {
      state.error = null;
    },
    updatePermissions: (state, action: PayloadAction<string[]>) => {
      state.permissions = action.payload;
    },
  },
  extraReducers: (builder) => {
    // Login
    builder
      .addCase(login.pending, (state) => {
        state.isLoading = true;
        state.error = null;
      })
      .addCase(login.fulfilled, (state, action) => {
        state.isLoading = false;
        state.isAuthenticated = true;
        state.user = action.payload.user;
        state.token = action.payload.token;
        state.permissions = action.payload.permissions;
      })
      .addCase(login.rejected, (state, action) => {
        state.isLoading = false;
        state.error = action.error.message || 'Login failed';
      });

    // Logout
    builder
      .addCase(logout.fulfilled, (state) => {
        state.user = null;
        state.token = null;
        state.isAuthenticated = false;
        state.permissions = [];
      });

    // Check Auth
    builder
      .addCase(checkAuth.pending, (state) => {
        state.isLoading = true;
      })
      .addCase(checkAuth.fulfilled, (state, action) => {
        state.isLoading = false;
        state.isAuthenticated = true;
        state.user = action.payload.user;
        state.permissions = action.payload.permissions;
      })
      .addCase(checkAuth.rejected, (state) => {
        state.isLoading = false;
        state.isAuthenticated = false;
        state.user = null;
        state.token = null;
      });

    // Refresh Token
    builder
      .addCase(refreshToken.fulfilled, (state, action) => {
        state.token = action.payload.token;
      });
  },
});

export const { clearError, updatePermissions } = authSlice.actions;
export default authSlice.reducer;