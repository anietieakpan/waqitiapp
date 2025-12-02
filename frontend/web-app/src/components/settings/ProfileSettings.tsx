import React, { useState } from 'react';
import {
  Box,
  Typography,
  TextField,
  Button,
  Grid,
  Card,
  CardContent,
  Avatar,
  IconButton,
  Alert,
  Divider,
  Chip,
  Dialog,
  DialogTitle,
  DialogContent,
  DialogActions,
  CircularProgress,
  InputAdornment,
} from '@mui/material';
import EditIcon from '@mui/icons-material/Edit';
import SaveIcon from '@mui/icons-material/Save';
import CancelIcon from '@mui/icons-material/Cancel';
import CameraAltIcon from '@mui/icons-material/CameraAlt';
import VerifiedIcon from '@mui/icons-material/Verified';
import WarningIcon from '@mui/icons-material/Warning';
import PersonIcon from '@mui/icons-material/Person';
import EmailIcon from '@mui/icons-material/Email';
import PhoneIcon from '@mui/icons-material/Phone';
import LocationOnIcon from '@mui/icons-material/LocationOn';
import CalendarTodayIcon from '@mui/icons-material/CalendarToday';
import BadgeIcon from '@mui/icons-material/Badge';;
import { useForm, Controller } from 'react-hook-form';
import { yupResolver } from '@hookform/resolvers/yup';
import * as yup from 'yup';
import { format } from 'date-fns';
import { useAuth } from '@/contexts/AuthContext';
import toast from 'react-hot-toast';

const schema = yup.object().shape({
  firstName: yup.string().required('First name is required'),
  lastName: yup.string().required('Last name is required'),
  email: yup.string().email('Invalid email').required('Email is required'),
  phoneNumber: yup.string().required('Phone number is required'),
  dateOfBirth: yup.string().required('Date of birth is required'),
  address: yup.object().shape({
    street: yup.string().required('Street address is required'),
    city: yup.string().required('City is required'),
    postalCode: yup.string().required('Postal code is required'),
    country: yup.string().required('Country is required'),
  }),
});

interface ProfileForm {
  firstName: string;
  lastName: string;
  email: string;
  phoneNumber: string;
  dateOfBirth: string;
  address: {
    street: string;
    city: string;
    postalCode: string;
    country: string;
  };
}

const ProfileSettings: React.FC = () => {
  const { user, updateProfile } = useAuth();
  const [isEditing, setIsEditing] = useState(false);
  const [loading, setLoading] = useState(false);
  const [showAvatarDialog, setShowAvatarDialog] = useState(false);
  const [avatarFile, setAvatarFile] = useState<File | null>(null);
  const [avatarPreview, setAvatarPreview] = useState<string | null>(null);

  const {
    control,
    handleSubmit,
    reset,
    formState: { errors, isDirty },
  } = useForm<ProfileForm>({
    resolver: yupResolver(schema),
    defaultValues: {
      firstName: user?.firstName || '',
      lastName: user?.lastName || '',
      email: user?.email || '',
      phoneNumber: user?.phoneNumber || '',
      dateOfBirth: '1990-01-01', // Mock data
      address: {
        street: '123 Main St',
        city: 'New York',
        postalCode: '10001',
        country: 'USA',
      },
    },
  });

  const handleSaveProfile = async (data: ProfileForm) => {
    setLoading(true);
    try {
      await updateProfile({
        firstName: data.firstName,
        lastName: data.lastName,
        phoneNumber: data.phoneNumber,
      });
      toast.success('Profile updated successfully!');
      setIsEditing(false);
    } catch (error: any) {
      toast.error(error.message || 'Failed to update profile');
    } finally {
      setLoading(false);
    }
  };

  const handleCancelEdit = () => {
    reset();
    setIsEditing(false);
  };

  const handleAvatarChange = (event: React.ChangeEvent<HTMLInputElement>) => {
    const file = event.target.files?.[0];
    if (file) {
      if (file.size > 5 * 1024 * 1024) {
        toast.error('Image size should be less than 5MB');
        return;
      }
      
      setAvatarFile(file);
      const reader = new FileReader();
      reader.onloadend = () => {
        setAvatarPreview(reader.result as string);
      };
      reader.readAsDataURL(file);
      setShowAvatarDialog(true);
    }
  };

  const handleUploadAvatar = async () => {
    if (!avatarFile) return;
    
    setLoading(true);
    try {
      // In a real app, upload to server
      toast.success('Avatar updated successfully!');
      setShowAvatarDialog(false);
    } catch (error) {
      toast.error('Failed to upload avatar');
    } finally {
      setLoading(false);
    }
  };

  const getKycStatusColor = () => {
    switch (user?.kycStatus) {
      case 'approved':
        return 'success';
      case 'pending':
        return 'warning';
      case 'rejected':
        return 'error';
      default:
        return 'default';
    }
  };

  return (
    <Box>
      <Typography variant="h5" gutterBottom>
        Profile Settings
      </Typography>
      <Typography variant="body2" color="text.secondary" paragraph>
        Manage your personal information and account details
      </Typography>

      {/* Profile Photo Section */}
      <Card sx={{ mb: 3 }}>
        <CardContent>
          <Box display="flex" alignItems="center" justifyContent="space-between">
            <Box display="flex" alignItems="center">
              <Box position="relative">
                <Avatar
                  sx={{ width: 100, height: 100 }}
                  src={user?.avatar}
                >
                  {user?.firstName?.charAt(0)}{user?.lastName?.charAt(0)}
                </Avatar>
                <input
                  accept="image/*"
                  id="avatar-upload"
                  type="file"
                  hidden
                  onChange={handleAvatarChange}
                />
                <label htmlFor="avatar-upload">
                  <IconButton
                    component="span"
                    sx={{
                      position: 'absolute',
                      bottom: 0,
                      right: 0,
                      bgcolor: 'background.paper',
                      boxShadow: 2,
                      '&:hover': { bgcolor: 'background.paper' },
                    }}
                  >
                    <CameraAlt />
                  </IconButton>
                </label>
              </Box>
              <Box ml={3}>
                <Typography variant="h6">
                  {user?.firstName} {user?.lastName}
                </Typography>
                <Typography variant="body2" color="text.secondary">
                  {user?.email}
                </Typography>
                <Box display="flex" gap={1} mt={1}>
                  {user?.verified && (
                    <Chip
                      icon={<Verified />}
                      label="Verified"
                      color="primary"
                      size="small"
                    />
                  )}
                  <Chip
                    label={`KYC: ${user?.kycStatus?.toUpperCase()}`}
                    color={getKycStatusColor() as any}
                    size="small"
                  />
                </Box>
              </Box>
            </Box>
          </Box>
        </CardContent>
      </Card>

      {/* Account Status */}
      {user?.kycStatus === 'not_started' && (
        <Alert severity="warning" sx={{ mb: 3 }}>
          <Typography variant="body2">
            Complete your KYC verification to unlock all features
          </Typography>
          <Button size="small" variant="outlined" sx={{ mt: 1 }}>
            Start Verification
          </Button>
        </Alert>
      )}

      {/* Personal Information Form */}
      <Card>
        <CardContent>
          <Box display="flex" justifyContent="space-between" alignItems="center" mb={3}>
            <Typography variant="h6">Personal Information</Typography>
            {!isEditing ? (
              <Button
                startIcon={<Edit />}
                onClick={() => setIsEditing(true)}
              >
                Edit
              </Button>
            ) : (
              <Box>
                <Button
                  startIcon={<Cancel />}
                  onClick={handleCancelEdit}
                  disabled={loading}
                  sx={{ mr: 1 }}
                >
                  Cancel
                </Button>
                <Button
                  variant="contained"
                  startIcon={loading ? <CircularProgress size={20} /> : <Save />}
                  onClick={handleSubmit(handleSaveProfile)}
                  disabled={loading || !isDirty}
                >
                  Save
                </Button>
              </Box>
            )}
          </Box>

          <form onSubmit={handleSubmit(handleSaveProfile)}>
            <Grid container spacing={3}>
              <Grid item xs={12} sm={6}>
                <Controller
                  name="firstName"
                  control={control}
                  render={({ field }) => (
                    <TextField
                      {...field}
                      fullWidth
                      label="First Name"
                      disabled={!isEditing}
                      error={!!errors.firstName}
                      helperText={errors.firstName?.message}
                      InputProps={{
                        startAdornment: (
                          <InputAdornment position="start">
                            <Person />
                          </InputAdornment>
                        ),
                      }}
                    />
                  )}
                />
              </Grid>
              <Grid item xs={12} sm={6}>
                <Controller
                  name="lastName"
                  control={control}
                  render={({ field }) => (
                    <TextField
                      {...field}
                      fullWidth
                      label="Last Name"
                      disabled={!isEditing}
                      error={!!errors.lastName}
                      helperText={errors.lastName?.message}
                      InputProps={{
                        startAdornment: (
                          <InputAdornment position="start">
                            <Person />
                          </InputAdornment>
                        ),
                      }}
                    />
                  )}
                />
              </Grid>
              <Grid item xs={12} sm={6}>
                <Controller
                  name="email"
                  control={control}
                  render={({ field }) => (
                    <TextField
                      {...field}
                      fullWidth
                      label="Email"
                      disabled
                      error={!!errors.email}
                      helperText={errors.email?.message || "Email cannot be changed"}
                      InputProps={{
                        startAdornment: (
                          <InputAdornment position="start">
                            <Email />
                          </InputAdornment>
                        ),
                      }}
                    />
                  )}
                />
              </Grid>
              <Grid item xs={12} sm={6}>
                <Controller
                  name="phoneNumber"
                  control={control}
                  render={({ field }) => (
                    <TextField
                      {...field}
                      fullWidth
                      label="Phone Number"
                      disabled={!isEditing}
                      error={!!errors.phoneNumber}
                      helperText={errors.phoneNumber?.message}
                      InputProps={{
                        startAdornment: (
                          <InputAdornment position="start">
                            <Phone />
                          </InputAdornment>
                        ),
                      }}
                    />
                  )}
                />
              </Grid>
              <Grid item xs={12} sm={6}>
                <Controller
                  name="dateOfBirth"
                  control={control}
                  render={({ field }) => (
                    <TextField
                      {...field}
                      fullWidth
                      label="Date of Birth"
                      type="date"
                      disabled={!isEditing}
                      error={!!errors.dateOfBirth}
                      helperText={errors.dateOfBirth?.message}
                      InputLabelProps={{ shrink: true }}
                      InputProps={{
                        startAdornment: (
                          <InputAdornment position="start">
                            <CalendarToday />
                          </InputAdornment>
                        ),
                      }}
                    />
                  )}
                />
              </Grid>
            </Grid>

            <Divider sx={{ my: 3 }} />

            <Typography variant="h6" gutterBottom>
              Address Information
            </Typography>

            <Grid container spacing={3}>
              <Grid item xs={12}>
                <Controller
                  name="address.street"
                  control={control}
                  render={({ field }) => (
                    <TextField
                      {...field}
                      fullWidth
                      label="Street Address"
                      disabled={!isEditing}
                      error={!!errors.address?.street}
                      helperText={errors.address?.street?.message}
                      InputProps={{
                        startAdornment: (
                          <InputAdornment position="start">
                            <LocationOn />
                          </InputAdornment>
                        ),
                      }}
                    />
                  )}
                />
              </Grid>
              <Grid item xs={12} sm={6}>
                <Controller
                  name="address.city"
                  control={control}
                  render={({ field }) => (
                    <TextField
                      {...field}
                      fullWidth
                      label="City"
                      disabled={!isEditing}
                      error={!!errors.address?.city}
                      helperText={errors.address?.city?.message}
                    />
                  )}
                />
              </Grid>
              <Grid item xs={12} sm={3}>
                <Controller
                  name="address.postalCode"
                  control={control}
                  render={({ field }) => (
                    <TextField
                      {...field}
                      fullWidth
                      label="Postal Code"
                      disabled={!isEditing}
                      error={!!errors.address?.postalCode}
                      helperText={errors.address?.postalCode?.message}
                    />
                  )}
                />
              </Grid>
              <Grid item xs={12} sm={3}>
                <Controller
                  name="address.country"
                  control={control}
                  render={({ field }) => (
                    <TextField
                      {...field}
                      fullWidth
                      label="Country"
                      disabled={!isEditing}
                      error={!!errors.address?.country}
                      helperText={errors.address?.country?.message}
                    />
                  )}
                />
              </Grid>
            </Grid>
          </form>
        </CardContent>
      </Card>

      {/* Account Information */}
      <Card sx={{ mt: 3 }}>
        <CardContent>
          <Typography variant="h6" gutterBottom>
            Account Information
          </Typography>
          <Grid container spacing={2}>
            <Grid item xs={12} sm={6}>
              <Typography variant="body2" color="text.secondary">
                Account ID
              </Typography>
              <Typography variant="body1" sx={{ fontFamily: 'monospace' }}>
                {user?.id}
              </Typography>
            </Grid>
            <Grid item xs={12} sm={6}>
              <Typography variant="body2" color="text.secondary">
                Member Since
              </Typography>
              <Typography variant="body1">
                {user?.createdAt ? format(new Date(user.createdAt), 'MMMM dd, yyyy') : '-'}
              </Typography>
            </Grid>
            <Grid item xs={12} sm={6}>
              <Typography variant="body2" color="text.secondary">
                Last Login
              </Typography>
              <Typography variant="body1">
                {user?.lastLoginAt ? format(new Date(user.lastLoginAt), 'MMMM dd, yyyy HH:mm') : '-'}
              </Typography>
            </Grid>
            <Grid item xs={12} sm={6}>
              <Typography variant="body2" color="text.secondary">
                Account Type
              </Typography>
              <Typography variant="body1">
                Personal Account
              </Typography>
            </Grid>
          </Grid>
        </CardContent>
      </Card>

      {/* Avatar Upload Dialog */}
      <Dialog
        open={showAvatarDialog}
        onClose={() => setShowAvatarDialog(false)}
        maxWidth="sm"
        fullWidth
      >
        <DialogTitle>Update Profile Photo</DialogTitle>
        <DialogContent>
          <Box textAlign="center" py={3}>
            {avatarPreview && (
              <Avatar
                src={avatarPreview}
                sx={{ width: 200, height: 200, mx: 'auto' }}
              />
            )}
          </Box>
        </DialogContent>
        <DialogActions>
          <Button onClick={() => setShowAvatarDialog(false)}>
            Cancel
          </Button>
          <Button
            variant="contained"
            onClick={handleUploadAvatar}
            disabled={loading}
            startIcon={loading ? <CircularProgress size={20} /> : null}
          >
            Upload
          </Button>
        </DialogActions>
      </Dialog>
    </Box>
  );
};

export default ProfileSettings;