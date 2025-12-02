import React, { useState } from 'react';
import {
  Container,
  Typography,
  Box,
  Card,
  CardContent,
  List,
  ListItem,
  ListItemText,
  ListItemSecondaryAction,
  Switch,
  Button,
  Divider,
  Dialog,
  DialogTitle,
  DialogContent,
  DialogActions,
  TextField,
  Chip,
} from '@mui/material';
import SecurityIcon from '@mui/icons-material/Security';
import PhoneAndroidIcon from '@mui/icons-material/PhoneAndroid';
import FingerprintIcon from '@mui/icons-material/Fingerprint';
import DevicesIcon from '@mui/icons-material/Devices';

const SecurityPage: React.FC = () => {
  const [twoFactorEnabled, setTwoFactorEnabled] = useState(true);
  const [biometricEnabled, setBiometricEnabled] = useState(false);
  const [setup2FADialog, setSetup2FADialog] = useState(false);

  const activeSessions = [
    { id: '1', device: 'MacBook Pro', location: 'New York, US', lastActive: '5 min ago', current: true },
    { id: '2', device: 'iPhone 14', location: 'New York, US', lastActive: '2 hours ago', current: false },
  ];

  return (
    <Container maxWidth="md" sx={{ mt: 4, mb: 4 }}>
      <Typography variant="h4" gutterBottom>
        Security Settings
      </Typography>

      {/* Two-Factor Authentication */}
      <Card sx={{ mb: 3 }}>
        <CardContent>
          <Box sx={{ display: 'flex', alignItems: 'center', mb: 2 }}>
            <SecurityIcon sx={{ mr: 1 }} color="primary" />
            <Typography variant="h6">Two-Factor Authentication</Typography>
          </Box>
          <List>
            <ListItem>
              <ListItemText
                primary="Authenticator App (TOTP)"
                secondary={twoFactorEnabled ? 'Enabled' : 'Disabled'}
              />
              <ListItemSecondaryAction>
                <Switch
                  checked={twoFactorEnabled}
                  onChange={(e) => {
                    if (e.target.checked) {
                      setSetup2FADialog(true);
                    } else {
                      setTwoFactorEnabled(false);
                    }
                  }}
                />
              </ListItemSecondaryAction>
            </ListItem>
            <Divider />
            <ListItem>
              <ListItemText
                primary="SMS Authentication"
                secondary="Receive codes via text message"
              />
              <ListItemSecondaryAction>
                <Button size="small">Setup</Button>
              </ListItemSecondaryAction>
            </ListItem>
          </List>
        </CardContent>
      </Card>

      {/* Biometric */}
      <Card sx={{ mb: 3 }}>
        <CardContent>
          <Box sx={{ display: 'flex', alignItems: 'center', mb: 2 }}>
            <FingerprintIcon sx={{ mr: 1 }} color="primary" />
            <Typography variant="h6">Biometric Authentication</Typography>
          </Box>
          <List>
            <ListItem>
              <ListItemText
                primary="Fingerprint / Face ID"
                secondary="Use biometrics to log in"
              />
              <ListItemSecondaryAction>
                <Switch checked={biometricEnabled} onChange={(e) => setBiometricEnabled(e.target.checked)} />
              </ListItemSecondaryAction>
            </ListItem>
          </List>
        </CardContent>
      </Card>

      {/* Active Sessions */}
      <Card sx={{ mb: 3 }}>
        <CardContent>
          <Box sx={{ display: 'flex', alignItems: 'center', mb: 2 }}>
            <DevicesIcon sx={{ mr: 1 }} color="primary" />
            <Typography variant="h6">Active Sessions</Typography>
          </Box>
          <List>
            {activeSessions.map((session, index) => (
              <React.Fragment key={session.id}>
                {index > 0 && <Divider />}
                <ListItem>
                  <ListItemText
                    primary={
                      <Box sx={{ display: 'flex', alignItems: 'center', gap: 1 }}>
                        {session.device}
                        {session.current && <Chip label="This device" size="small" color="primary" />}
                      </Box>
                    }
                    secondary={
                      <>
                        {session.location} • {session.lastActive}
                      </>
                    }
                  />
                  {!session.current && (
                    <ListItemSecondaryAction>
                      <Button size="small" color="error">
                        Revoke
                      </Button>
                    </ListItemSecondaryAction>
                  )}
                </ListItem>
              </React.Fragment>
            ))}
          </List>
        </CardContent>
      </Card>

      {/* Password */}
      <Card>
        <CardContent>
          <Typography variant="h6" gutterBottom>
            Password
          </Typography>
          <Button variant="outlined">Change Password</Button>
        </CardContent>
      </Card>

      {/* 2FA Setup Dialog */}
      <Dialog open={setup2FADialog} onClose={() => setSetup2FADialog(false)}>
        <DialogTitle>Setup Two-Factor Authentication</DialogTitle>
        <DialogContent>
          <Box sx={{ textAlign: 'center', py: 2 }}>
            <Typography variant="body2" gutterBottom>
              Scan this QR code with your authenticator app
            </Typography>
            <Box
              sx={{
                width: 200,
                height: 200,
                bgcolor: 'grey.200',
                mx: 'auto',
                my: 2,
                display: 'flex',
                alignItems: 'center',
                justifyContent: 'center',
              }}
            >
              QR Code
            </Box>
            <TextField label="Enter 6-digit code" fullWidth margin="normal" />
          </Box>
        </DialogContent>
        <DialogActions>
          <Button onClick={() => setSetup2FADialog(false)}>Cancel</Button>
          <Button
            variant="contained"
            onClick={() => {
              setTwoFactorEnabled(true);
              setSetup2FADialog(false);
            }}
          >
            Verify
          </Button>
        </DialogActions>
      </Dialog>
    </Container>
  );
};

export default SecurityPage;
