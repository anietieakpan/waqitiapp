import React, { useState } from 'react';
import {
  Dialog,
  DialogTitle,
  DialogContent,
  DialogActions,
  Button,
  Typography,
  Box,
  Alert,
  List,
  ListItem,
  ListItemIcon,
  ListItemText,
  Checkbox,
  TextField,
  FormControlLabel,
  Stepper,
  Step,
  StepLabel,
  StepContent,
  Divider,
  Card,
  CardContent,
} from '@mui/material';
import WarningIcon from '@mui/icons-material/Warning';
import DeleteIcon from '@mui/icons-material/Delete';
import InfoIcon from '@mui/icons-material/Info';
import CheckIcon from '@mui/icons-material/CheckCircle';
import BlockIcon from '@mui/icons-material/Block';;

interface DataErasureDialogProps {
  open: boolean;
  onClose: () => void;
  onConfirm: (categories: string[]) => Promise<void>;
  categories: Array<{ id: string; label: string; icon: React.ReactNode }>;
}

const erasureSteps = [
  'Review Implications',
  'Select Data',
  'Confirm Identity',
  'Final Confirmation',
];

const DataErasureDialog: React.FC<DataErasureDialogProps> = ({
  open,
  onClose,
  onConfirm,
  categories,
}) => {
  const [activeStep, setActiveStep] = useState(0);
  const [selectedCategories, setSelectedCategories] = useState<string[]>([]);
  const [confirmationText, setConfirmationText] = useState('');
  const [understood, setUnderstood] = useState(false);
  const [isProcessing, setIsProcessing] = useState(false);

  const CONFIRMATION_PHRASE = 'DELETE MY DATA';

  const handleNext = () => {
    setActiveStep((prevStep) => prevStep + 1);
  };

  const handleBack = () => {
    setActiveStep((prevStep) => prevStep - 1);
  };

  const handleReset = () => {
    setActiveStep(0);
    setSelectedCategories([]);
    setConfirmationText('');
    setUnderstood(false);
  };

  const handleCategoryToggle = (categoryId: string) => {
    setSelectedCategories(prev =>
      prev.includes(categoryId)
        ? prev.filter(id => id !== categoryId)
        : [...prev, categoryId]
    );
  };

  const handleConfirm = async () => {
    setIsProcessing(true);
    try {
      await onConfirm(selectedCategories);
      onClose();
      handleReset();
    } catch (error) {
      console.error('Erasure request failed:', error);
    } finally {
      setIsProcessing(false);
    }
  };

  const canProceedStep1 = understood;
  const canProceedStep2 = selectedCategories.length > 0;
  const canProceedStep3 = confirmationText === CONFIRMATION_PHRASE;

  const renderStepContent = (step: number) => {
    switch (step) {
      case 0:
        return (
          <Box>
            <Alert severity="error" sx={{ mb: 3 }}>
              <Typography variant="subtitle2" gutterBottom>
                This action cannot be undone!
              </Typography>
              <Typography variant="body2">
                Requesting data deletion will permanently remove your selected data 
                from our systems where legally permissible.
              </Typography>
            </Alert>

            <Typography variant="subtitle1" gutterBottom>
              Important Information:
            </Typography>

            <List>
              <ListItem>
                <ListItemIcon>
                  <WarningIcon color="warning" />
                </ListItemIcon>
                <ListItemText
                  primary="Account Closure"
                  secondary="Your Waqiti account will be closed and you will lose access to all services"
                />
              </ListItem>
              <ListItem>
                <ListItemIcon>
                  <BlockIcon color="error" />
                </ListItemIcon>
                <ListItemText
                  primary="Service Termination"
                  secondary="All active services, subscriptions, and features will be terminated"
                />
              </ListItem>
              <ListItem>
                <ListItemIcon>
                  <InfoIcon color="info" />
                </ListItemIcon>
                <ListItemText
                  primary="Legal Retention"
                  secondary="Some data may be retained for legal compliance (e.g., financial records for 7 years)"
                />
              </ListItem>
              <ListItem>
                <ListItemIcon>
                  <CheckIcon color="success" />
                </ListItemIcon>
                <ListItemText
                  primary="30-Day Processing"
                  secondary="Your request will be processed within 30 days as required by GDPR"
                />
              </ListItem>
            </List>

            <FormControlLabel
              control={
                <Checkbox
                  checked={understood}
                  onChange={(e) => setUnderstood(e.target.checked)}
                />
              }
              label="I understand the implications of deleting my data"
              sx={{ mt: 2 }}
            />
          </Box>
        );

      case 1:
        return (
          <Box>
            <Typography variant="subtitle1" gutterBottom>
              Select the data you want to delete:
            </Typography>

            <Alert severity="info" sx={{ mb: 2 }}>
              You can choose specific categories or delete all your data.
            </Alert>

            <List>
              {categories.map((category) => (
                <ListItem
                  key={category.id}
                  dense
                  button
                  onClick={() => handleCategoryToggle(category.id)}
                  sx={{
                    border: 1,
                    borderColor: 'divider',
                    borderRadius: 1,
                    mb: 1,
                    bgcolor: selectedCategories.includes(category.id) 
                      ? 'action.selected' 
                      : 'transparent',
                  }}
                >
                  <ListItemIcon>
                    <Checkbox
                      edge="start"
                      checked={selectedCategories.includes(category.id)}
                      tabIndex={-1}
                      disableRipple
                    />
                  </ListItemIcon>
                  <ListItemIcon sx={{ minWidth: 40 }}>
                    {category.icon}
                  </ListItemIcon>
                  <ListItemText 
                    primary={category.label}
                    secondary={category.id === 'FINANCIAL_DATA' ? 
                      'May be retained for legal compliance' : undefined}
                  />
                </ListItem>
              ))}
            </List>

            <Button
              variant="outlined"
              fullWidth
              sx={{ mt: 2 }}
              onClick={() => setSelectedCategories(categories.map(c => c.id))}
            >
              Select All Data
            </Button>
          </Box>
        );

      case 2:
        return (
          <Box>
            <Typography variant="subtitle1" gutterBottom>
              Confirm Your Identity
            </Typography>

            <Alert severity="warning" sx={{ mb: 3 }}>
              To protect your account, please type the following phrase exactly:
            </Alert>

            <Card sx={{ mb: 3, bgcolor: 'error.light', color: 'error.contrastText' }}>
              <CardContent>
                <Typography variant="h6" align="center">
                  {CONFIRMATION_PHRASE}
                </Typography>
              </CardContent>
            </Card>

            <TextField
              fullWidth
              label="Type the phrase above"
              value={confirmationText}
              onChange={(e) => setConfirmationText(e.target.value.toUpperCase())}
              error={confirmationText !== '' && confirmationText !== CONFIRMATION_PHRASE}
              helperText={
                confirmationText !== '' && confirmationText !== CONFIRMATION_PHRASE
                  ? 'Phrase does not match'
                  : 'Type exactly as shown above'
              }
            />
          </Box>
        );

      case 3:
        return (
          <Box>
            <Alert severity="error" sx={{ mb: 3 }}>
              <Typography variant="subtitle2" gutterBottom>
                Final Warning
              </Typography>
              <Typography variant="body2">
                You are about to submit a request to permanently delete your data. 
                This action cannot be reversed.
              </Typography>
            </Alert>

            <Typography variant="subtitle1" gutterBottom>
              Summary of your request:
            </Typography>

            <List>
              <ListItem>
                <ListItemText
                  primary="Data categories to delete"
                  secondary={`${selectedCategories.length} categories selected`}
                />
              </ListItem>
              <ListItem>
                <ListItemText
                  primary="Processing time"
                  secondary="Within 30 days"
                />
              </ListItem>
              <ListItem>
                <ListItemText
                  primary="Notification"
                  secondary="You will receive email confirmation"
                />
              </ListItem>
            </List>

            <Divider sx={{ my: 2 }} />

            <Typography variant="body2" color="text.secondary">
              Selected categories:
            </Typography>
            <Box sx={{ mt: 1 }}>
              {selectedCategories.map(catId => {
                const category = categories.find(c => c.id === catId);
                return category ? (
                  <Typography key={catId} variant="body2">
                    • {category.label}
                  </Typography>
                ) : null;
              })}
            </Box>
          </Box>
        );

      default:
        return null;
    }
  };

  return (
    <Dialog open={open} onClose={onClose} maxWidth="sm" fullWidth>
      <DialogTitle>
        <Box sx={{ display: 'flex', alignItems: 'center' }}>
          <DeleteIcon sx={{ mr: 1, color: 'error.main' }} />
          Request Data Deletion
        </Box>
      </DialogTitle>
      <DialogContent>
        <Stepper activeStep={activeStep} orientation="vertical">
          {erasureSteps.map((label, index) => (
            <Step key={label}>
              <StepLabel>{label}</StepLabel>
              <StepContent>
                {renderStepContent(index)}
                <Box sx={{ mt: 2 }}>
                  {index < erasureSteps.length - 1 ? (
                    <>
                      <Button
                        variant="contained"
                        onClick={handleNext}
                        disabled={
                          (index === 0 && !canProceedStep1) ||
                          (index === 1 && !canProceedStep2) ||
                          (index === 2 && !canProceedStep3)
                        }
                      >
                        Continue
                      </Button>
                      <Button
                        onClick={handleBack}
                        disabled={index === 0}
                        sx={{ ml: 1 }}
                      >
                        Back
                      </Button>
                    </>
                  ) : (
                    <>
                      <Button
                        variant="contained"
                        color="error"
                        onClick={handleConfirm}
                        disabled={isProcessing}
                        startIcon={<DeleteIcon />}
                      >
                        {isProcessing ? 'Processing...' : 'Submit Deletion Request'}
                      </Button>
                      <Button
                        onClick={handleBack}
                        disabled={isProcessing}
                        sx={{ ml: 1 }}
                      >
                        Back
                      </Button>
                    </>
                  )}
                </Box>
              </StepContent>
            </Step>
          ))}
        </Stepper>
      </DialogContent>
      <DialogActions>
        <Button onClick={onClose} disabled={isProcessing}>
          Cancel
        </Button>
      </DialogActions>
    </Dialog>
  );
};

export default DataErasureDialog;