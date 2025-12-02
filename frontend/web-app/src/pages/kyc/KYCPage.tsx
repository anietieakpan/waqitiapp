import React, { useState } from 'react';
import {
  Container,
  Typography,
  Box,
  Card,
  CardContent,
  Stepper,
  Step,
  StepLabel,
  Button,
  TextField,
  Grid,
  Alert,
  LinearProgress,
} from '@mui/material';
import CheckCircleIcon from '@mui/icons-material/CheckCircle';
import PendingIcon from '@mui/icons-material/Pending';
import UploadFileIcon from '@mui/icons-material/UploadFile';

const KYCPage: React.FC = () => {
  const [activeStep, setActiveStep] = useState(0);
  const kycStatus = 'PENDING'; // VERIFIED, PENDING, REJECTED

  const steps = ['Personal Info', 'Upload Documents', 'Verification'];

  return (
    <Container maxWidth="md" sx={{ mt: 4, mb: 4 }}>
      <Typography variant="h4" gutterBottom>
        Identity Verification (KYC)
      </Typography>

      <Card sx={{ mb: 3 }}>
        <CardContent>
          <Box sx={{ display: 'flex', alignItems: 'center', justifyContent: 'space-between', mb: 2 }}>
            <Typography variant="h6">Verification Status</Typography>
            {kycStatus === 'VERIFIED' ? (
              <CheckCircleIcon color="success" fontSize="large" />
            ) : (
              <PendingIcon color="warning" fontSize="large" />
            )}
          </Box>
          {kycStatus === 'PENDING' && (
            <Alert severity="info">
              Your documents are being reviewed. This usually takes 1-2 business days.
            </Alert>
          )}
          {kycStatus === 'VERIFIED' && (
            <Alert severity="success">Your identity has been verified!</Alert>
          )}
        </CardContent>
      </Card>

      <Card>
        <CardContent>
          <Stepper activeStep={activeStep} sx={{ mb: 4 }}>
            {steps.map((label) => (
              <Step key={label}>
                <StepLabel>{label}</StepLabel>
              </Step>
            ))}
          </Stepper>

          {activeStep === 0 && (
            <Grid container spacing={2}>
              <Grid item xs={12} sm={6}>
                <TextField label="First Name" fullWidth required />
              </Grid>
              <Grid item xs={12} sm={6}>
                <TextField label="Last Name" fullWidth required />
              </Grid>
              <Grid item xs={12} sm={6}>
                <TextField label="Date of Birth" type="date" fullWidth required InputLabelProps={{ shrink: true }} />
              </Grid>
              <Grid item xs={12} sm={6}>
                <TextField label="Social Security Number" fullWidth required />
              </Grid>
              <Grid item xs={12}>
                <TextField label="Address" fullWidth required />
              </Grid>
            </Grid>
          )}

          {activeStep === 1 && (
            <Box>
              <Typography variant="h6" gutterBottom>
                Upload Identity Documents
              </Typography>
              <Grid container spacing={2}>
                <Grid item xs={12}>
                  <Card variant="outlined" sx={{ p: 3, textAlign: 'center', cursor: 'pointer' }}>
                    <UploadFileIcon sx={{ fontSize: 60, color: 'text.secondary' }} />
                    <Typography variant="body1" sx={{ mt: 2 }}>
                      Upload Government-issued ID
                    </Typography>
                    <Typography variant="caption" color="text.secondary">
                      Driver's License, Passport, or National ID
                    </Typography>
                  </Card>
                </Grid>
                <Grid item xs={12}>
                  <Card variant="outlined" sx={{ p: 3, textAlign: 'center', cursor: 'pointer' }}>
                    <UploadFileIcon sx={{ fontSize: 60, color: 'text.secondary' }} />
                    <Typography variant="body1" sx={{ mt: 2 }}>
                      Upload Proof of Address
                    </Typography>
                    <Typography variant="caption" color="text.secondary">
                      Utility bill or bank statement (less than 3 months old)
                    </Typography>
                  </Card>
                </Grid>
              </Grid>
            </Box>
          )}

          {activeStep === 2 && (
            <Box sx={{ textAlign: 'center', py: 4 }}>
              <CheckCircleIcon color="success" sx={{ fontSize: 80 }} />
              <Typography variant="h5" sx={{ mt: 2 }}>
                Documents Submitted!
              </Typography>
              <Typography color="text.secondary" sx={{ mt: 1 }}>
                We'll review your documents and notify you within 1-2 business days.
              </Typography>
            </Box>
          )}

          <Box sx={{ display: 'flex', justifyContent: 'space-between', mt: 4 }}>
            <Button disabled={activeStep === 0} onClick={() => setActiveStep((prev) => prev - 1)}>
              Back
            </Button>
            <Button
              variant="contained"
              onClick={() => setActiveStep((prev) => (prev < 2 ? prev + 1 : prev))}
            >
              {activeStep === 2 ? 'Done' : 'Next'}
            </Button>
          </Box>
        </CardContent>
      </Card>
    </Container>
  );
};

export default KYCPage;
