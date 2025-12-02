import React, { useState, useEffect } from 'react';
import { useNavigate, Link } from 'react-router-dom';
import { useDispatch, useSelector } from 'react-redux';
import {
  Container,
  Paper,
  TextField,
  Button,
  Typography,
  Box,
  Alert,
  Divider,
  CircularProgress,
} from '@mui/material';
import { useFormik } from 'formik';
import * as yup from 'yup';
import toast from 'react-hot-toast';

import { RootState, AppDispatch } from '@/store/store';
import { loginUser, clearError } from '@/store/auth/authSlice';
import MFADialog from '@/components/auth/MFADialog';

const validationSchema = yup.object({
  email: yup.string().email('Enter a valid email').required('Email is required'),
  password: yup.string().min(8, 'Password should be at least 8 characters').required('Password is required'),
});

const LoginPage: React.FC = () => {
  const navigate = useNavigate();
  const dispatch = useDispatch<AppDispatch>();
  const { isAuthenticated, isLoading, error, mfaRequired, tempToken } = useSelector(
    (state: RootState) => state.auth
  );
  const [showMFA, setShowMFA] = useState(false);

  useEffect(() => {
    if (isAuthenticated) {
      navigate('/dashboard');
    }
  }, [isAuthenticated, navigate]);

  useEffect(() => {
    if (mfaRequired && tempToken) {
      setShowMFA(true);
    }
  }, [mfaRequired, tempToken]);

  useEffect(() => {
    return () => {
      dispatch(clearError());
    };
  }, [dispatch]);

  const formik = useFormik({
    initialValues: {
      email: '',
      password: '',
    },
    validationSchema,
    onSubmit: async (values) => {
      try {
        const result = await dispatch(loginUser(values));
        if (loginUser.fulfilled.match(result)) {
          if (!result.payload.mfaRequired) {
            toast.success('Login successful!');
            navigate('/dashboard');
          }
        }
      } catch (error) {
        console.error('Login error:', error);
      }
    },
  });

  const handleMFASuccess = () => {
    setShowMFA(false);
    toast.success('Login successful!');
    navigate('/dashboard');
  };

  const handleMFACancel = () => {
    setShowMFA(false);
    // Reset form or redirect as needed
  };

  return (
    <Container component="main" maxWidth="sm">
      <Box
        sx={{
          marginTop: 8,
          display: 'flex',
          flexDirection: 'column',
          alignItems: 'center',
        }}
      >
        <Paper elevation={3} sx={{ padding: 4, width: '100%' }}>
          <Box sx={{ display: 'flex', flexDirection: 'column', alignItems: 'center' }}>
            <Typography component="h1" variant="h4" sx={{ mb: 3 }}>
              Waqiti
            </Typography>
            <Typography component="h2" variant="h5" sx={{ mb: 3 }}>
              Sign In
            </Typography>

            {error && (
              <Alert severity="error" sx={{ width: '100%', mb: 2 }}>
                {error}
              </Alert>
            )}

            <Box component="form" onSubmit={formik.handleSubmit} sx={{ mt: 1, width: '100%' }}>
              <TextField
                margin="normal"
                required
                fullWidth
                id="email"
                label="Email Address"
                name="email"
                autoComplete="email"
                autoFocus
                value={formik.values.email}
                onChange={formik.handleChange}
                onBlur={formik.handleBlur}
                error={formik.touched.email && Boolean(formik.errors.email)}
                helperText={formik.touched.email && formik.errors.email}
              />
              <TextField
                margin="normal"
                required
                fullWidth
                name="password"
                label="Password"
                type="password"
                id="password"
                autoComplete="current-password"
                value={formik.values.password}
                onChange={formik.handleChange}
                onBlur={formik.handleBlur}
                error={formik.touched.password && Boolean(formik.errors.password)}
                helperText={formik.touched.password && formik.errors.password}
              />
              <Button
                type="submit"
                fullWidth
                variant="contained"
                sx={{ mt: 3, mb: 2 }}
                disabled={isLoading}
              >
                {isLoading ? <CircularProgress size={24} /> : 'Sign In'}
              </Button>
              
              <Divider sx={{ my: 2 }} />
              
              <Box sx={{ textAlign: 'center' }}>
                <Link to="/forgot-password" style={{ textDecoration: 'none' }}>
                  <Typography variant="body2" color="primary">
                    Forgot password?
                  </Typography>
                </Link>
              </Box>
              
              <Box sx={{ textAlign: 'center', mt: 2 }}>
                <Typography variant="body2">
                  Don't have an account?{' '}
                  <Link to="/register" style={{ textDecoration: 'none' }}>
                    <Typography component="span" variant="body2" color="primary">
                      Sign up
                    </Typography>
                  </Link>
                </Typography>
              </Box>
            </Box>
          </Box>
        </Paper>
      </Box>

      {showMFA && tempToken && (
        <MFADialog
          open={showMFA}
          tempToken={tempToken}
          onSuccess={handleMFASuccess}
          onCancel={handleMFACancel}
        />
      )}
    </Container>
  );
};

export default LoginPage;