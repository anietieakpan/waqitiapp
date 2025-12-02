import React, { useState } from 'react';
import {
  Box,
  Card,
  CardContent,
  Typography,
  Avatar,
  Button,
  Grid,
  TextField,
  Divider,
  IconButton,
  Alert,
} from '@mui/material';
import EditIcon from '@mui/icons-material/Edit';
import SaveIcon from '@mui/icons-material/Save';
import CancelIcon from '@mui/icons-material/Cancel';
import { useAppSelector, useAppDispatch } from '@/hooks/redux';
import toast from 'react-hot-toast';

interface ProfileFormData {
  name: string;
  email: string;
  phone: string;
  address: string;
  city: string;
  country: string;
  postalCode: string;
}

const ProfilePage: React.FC = () => {
  const { user } = useAppSelector((state) => state.auth);
  const [isEditing, setIsEditing] = useState(false);
  const [loading, setLoading] = useState(false);

  const [formData, setFormData] = useState<ProfileFormData>({
    name: user?.name || '',
    email: user?.email || '',
    phone: user?.phone || '',
    address: user?.address || '',
    city: user?.city || '',
    country: user?.country || '',
    postalCode: user?.postalCode || '',
  });

  const handleChange = (field: keyof ProfileFormData) => (
    event: React.ChangeEvent<HTMLInputElement>
  ) => {
    setFormData({
      ...formData,
      [field]: event.target.value,
    });
  };

  const handleEdit = () => {
    setIsEditing(true);
  };

  const handleCancel = () => {
    setIsEditing(false);
    // Reset form data
    setFormData({
      name: user?.name || '',
      email: user?.email || '',
      phone: user?.phone || '',
      address: user?.address || '',
      city: user?.city || '',
      country: user?.country || '',
      postalCode: user?.postalCode || '',
    });
  };

  const handleSave = async () => {
    setLoading(true);
    try {
      // TODO: Call API to update profile
      // await updateProfile(formData);

      toast.success('Profile updated successfully');
      setIsEditing(false);
    } catch (error) {
      toast.error('Failed to update profile');
      console.error('Error updating profile:', error);
    } finally {
      setLoading(false);
    }
  };

  return (
    <Box>
      {/* Header */}
      <Box sx={{ mb: 4 }}>
        <Typography variant="h4" fontWeight="bold" gutterBottom>
          My Profile
        </Typography>
        <Typography variant="body1" color="text.secondary">
          Manage your personal information and preferences
        </Typography>
      </Box>

      {/* Profile Picture Section */}
      <Card sx={{ mb: 3 }}>
        <CardContent>
          <Box sx={{ display: 'flex', alignItems: 'center', gap: 3 }}>
            <Avatar
              sx={{
                width: 100,
                height: 100,
                bgcolor: 'primary.main',
                fontSize: '2.5rem',
              }}
            >
              {user?.name?.[0]?.toUpperCase() || 'U'}
            </Avatar>
            <Box>
              <Typography variant="h5" fontWeight="bold">
                {user?.name || 'User'}
              </Typography>
              <Typography variant="body2" color="text.secondary" gutterBottom>
                {user?.email || 'user@example.com'}
              </Typography>
              <Button variant="outlined" size="small" sx={{ mt: 1 }}>
                Change Profile Picture
              </Button>
            </Box>
          </Box>
        </CardContent>
      </Card>

      {/* Personal Information */}
      <Card>
        <CardContent>
          <Box
            sx={{
              display: 'flex',
              justifyContent: 'space-between',
              alignItems: 'center',
              mb: 3,
            }}
          >
            <Typography variant="h6" fontWeight="bold">
              Personal Information
            </Typography>
            {!isEditing && (
              <Button
                variant="outlined"
                startIcon={<EditIcon />}
                onClick={handleEdit}
              >
                Edit
              </Button>
            )}
          </Box>

          <Grid container spacing={3}>
            {/* Full Name */}
            <Grid item xs={12} md={6}>
              <TextField
                fullWidth
                label="Full Name"
                value={formData.name}
                onChange={handleChange('name')}
                disabled={!isEditing}
                variant={isEditing ? 'outlined' : 'filled'}
              />
            </Grid>

            {/* Email */}
            <Grid item xs={12} md={6}>
              <TextField
                fullWidth
                label="Email"
                type="email"
                value={formData.email}
                onChange={handleChange('email')}
                disabled={!isEditing}
                variant={isEditing ? 'outlined' : 'filled'}
              />
            </Grid>

            {/* Phone */}
            <Grid item xs={12} md={6}>
              <TextField
                fullWidth
                label="Phone Number"
                value={formData.phone}
                onChange={handleChange('phone')}
                disabled={!isEditing}
                variant={isEditing ? 'outlined' : 'filled'}
              />
            </Grid>

            {/* Address */}
            <Grid item xs={12} md={6}>
              <TextField
                fullWidth
                label="Address"
                value={formData.address}
                onChange={handleChange('address')}
                disabled={!isEditing}
                variant={isEditing ? 'outlined' : 'filled'}
              />
            </Grid>

            {/* City */}
            <Grid item xs={12} md={4}>
              <TextField
                fullWidth
                label="City"
                value={formData.city}
                onChange={handleChange('city')}
                disabled={!isEditing}
                variant={isEditing ? 'outlined' : 'filled'}
              />
            </Grid>

            {/* Country */}
            <Grid item xs={12} md={4}>
              <TextField
                fullWidth
                label="Country"
                value={formData.country}
                onChange={handleChange('country')}
                disabled={!isEditing}
                variant={isEditing ? 'outlined' : 'filled'}
              />
            </Grid>

            {/* Postal Code */}
            <Grid item xs={12} md={4}>
              <TextField
                fullWidth
                label="Postal Code"
                value={formData.postalCode}
                onChange={handleChange('postalCode')}
                disabled={!isEditing}
                variant={isEditing ? 'outlined' : 'filled'}
              />
            </Grid>
          </Grid>

          {/* Action Buttons */}
          {isEditing && (
            <Box sx={{ display: 'flex', gap: 2, mt: 3, justifyContent: 'flex-end' }}>
              <Button
                variant="outlined"
                startIcon={<CancelIcon />}
                onClick={handleCancel}
                disabled={loading}
              >
                Cancel
              </Button>
              <Button
                variant="contained"
                startIcon={<SaveIcon />}
                onClick={handleSave}
                disabled={loading}
              >
                Save Changes
              </Button>
            </Box>
          )}
        </CardContent>
      </Card>

      {/* Account Status */}
      <Card sx={{ mt: 3 }}>
        <CardContent>
          <Typography variant="h6" fontWeight="bold" gutterBottom>
            Account Status
          </Typography>
          <Divider sx={{ my: 2 }} />

          <Grid container spacing={2}>
            <Grid item xs={12} md={6}>
              <Box sx={{ display: 'flex', justifyContent: 'space-between', mb: 2 }}>
                <Typography variant="body2" color="text.secondary">
                  Account Type
                </Typography>
                <Typography variant="body2" fontWeight="medium">
                  Individual
                </Typography>
              </Box>
              <Box sx={{ display: 'flex', justifyContent: 'space-between', mb: 2 }}>
                <Typography variant="body2" color="text.secondary">
                  Member Since
                </Typography>
                <Typography variant="body2" fontWeight="medium">
                  {new Date().toLocaleDateString()}
                </Typography>
              </Box>
            </Grid>

            <Grid item xs={12} md={6}>
              <Box sx={{ display: 'flex', justifyContent: 'space-between', mb: 2 }}>
                <Typography variant="body2" color="text.secondary">
                  Email Verified
                </Typography>
                <Typography variant="body2" fontWeight="medium" color="success.main">
                  Yes
                </Typography>
              </Box>
              <Box sx={{ display: 'flex', justifyContent: 'space-between', mb: 2 }}>
                <Typography variant="body2" color="text.secondary">
                  KYC Status
                </Typography>
                <Typography variant="body2" fontWeight="medium" color="success.main">
                  Verified
                </Typography>
              </Box>
            </Grid>
          </Grid>
        </CardContent>
      </Card>

      {/* Danger Zone */}
      <Card sx={{ mt: 3, borderColor: 'error.main', borderWidth: 1, borderStyle: 'solid' }}>
        <CardContent>
          <Typography variant="h6" fontWeight="bold" color="error" gutterBottom>
            Danger Zone
          </Typography>
          <Typography variant="body2" color="text.secondary" gutterBottom>
            Once you delete your account, there is no going back. Please be certain.
          </Typography>
          <Button variant="outlined" color="error" sx={{ mt: 2 }}>
            Delete Account
          </Button>
        </CardContent>
      </Card>
    </Box>
  );
};

export default ProfilePage;
