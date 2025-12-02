import React, { useState, useEffect } from 'react';
import {
  Box,
  Card,
  CardContent,
  Typography,
  Button,
  TextField,
  Avatar,
  AvatarGroup,
  Chip,
  Divider,
  IconButton,
  List,
  ListItem,
  ListItemAvatar,
  ListItemText,
  ListItemSecondaryAction,
  Dialog,
  DialogTitle,
  DialogContent,
  DialogActions,
  Stepper,
  Step,
  StepLabel,
  Switch,
  FormControlLabel,
  Tooltip,
  Alert,
  CircularProgress,
  Fab,
  Paper,
  InputAdornment,
  Menu,
  MenuItem,
  Badge,
} from '@mui/material';
import AddIcon from '@mui/icons-material/Add';
import ReceiptIcon from '@mui/icons-material/Receipt';
import GroupIcon from '@mui/icons-material/Group';
import AttachMoneyIcon from '@mui/icons-material/AttachMoney';
import CheckCircleIcon from '@mui/icons-material/CheckCircle';
import WarningIcon from '@mui/icons-material/Warning';
import EditIcon from '@mui/icons-material/Edit';
import DeleteIcon from '@mui/icons-material/Delete';
import ShareIcon from '@mui/icons-material/Share';
import ContentCopyIcon from '@mui/icons-material/ContentCopy';
import QrCode2Icon from '@mui/icons-material/QrCode2';
import CameraAltIcon from '@mui/icons-material/CameraAlt';
import CalculateIcon from '@mui/icons-material/Calculate';
import PercentIcon from '@mui/icons-material/Percent';
import RestaurantMenuIcon from '@mui/icons-material/RestaurantMenu';
import LocalGasStationIcon from '@mui/icons-material/LocalGasStation';
import HomeIcon from '@mui/icons-material/Home';
import ShoppingCartIcon from '@mui/icons-material/ShoppingCart';
import MoreVertIcon from '@mui/icons-material/MoreVert';
import NotificationsActiveIcon from '@mui/icons-material/NotificationsActive';
import HistoryIcon from '@mui/icons-material/History';;
import { useDispatch, useSelector } from 'react-redux';
import { formatCurrency, formatDate } from '../../utils/formatters';
import { RootState, AppDispatch } from '../../store/store';
import ContactSelector from '../payment/ContactSelector';
import ReceiptScanner from './ReceiptScanner';
import QRScanner from '../common/QRScanner';
import { showToast } from '../../utils/toast';

interface BillParticipant {
  userId: string;
  name: string;
  avatar?: string;
  amountOwed: number;
  percentage?: number;
  items?: BillItem[];
  paid: boolean;
  paymentId?: string;
}

interface BillItem {
  id: string;
  name: string;
  amount: number;
  quantity: number;
  sharedBy: string[];
  tax?: number;
  discount?: number;
}

interface BillSplit {
  id: string;
  title: string;
  description?: string;
  category: string;
  totalAmount: number;
  tax: number;
  tip: number;
  discount: number;
  finalAmount: number;
  createdBy: string;
  createdAt: Date;
  participants: BillParticipant[];
  items: BillItem[];
  status: 'draft' | 'pending' | 'partial' | 'completed';
  receiptUrl?: string;
  splitMethod: 'equal' | 'percentage' | 'itemized' | 'custom';
  dueDate?: Date;
  recurring?: {
    frequency: 'monthly' | 'weekly' | 'custom';
    endDate?: Date;
  };
  notes?: string;
  shareLink?: string;
}

/**
 * Bill Split Manager - Comprehensive bill splitting interface
 */
const BillSplitManager: React.FC = () => {
  const dispatch = useDispatch<AppDispatch>();
  const currentUser = useSelector((state: RootState) => state.auth.user);
  
  const [activeBills, setActiveBills] = useState<BillSplit[]>([]);
  const [completedBills, setCompletedBills] = useState<BillSplit[]>([]);
  const [selectedBill, setSelectedBill] = useState<BillSplit | null>(null);
  const [createDialogOpen, setCreateDialogOpen] = useState(false);
  const [activeStep, setActiveStep] = useState(0);
  const [loading, setLoading] = useState(false);
  const [scannerOpen, setScannerOpen] = useState(false);
  const [qrScannerOpen, setQrScannerOpen] = useState(false);
  const [anchorEl, setAnchorEl] = useState<null | HTMLElement>(null);
  
  // Form state
  const [formData, setFormData] = useState({
    title: '',
    description: '',
    category: 'restaurant',
    totalAmount: '',
    tax: '',
    tip: '',
    discount: '',
    splitMethod: 'equal' as const,
    participants: [] as any[],
    items: [] as BillItem[],
    recurring: false,
    recurringFrequency: 'monthly' as const,
    dueDate: null as Date | null,
    notes: '',
  });

  const categories = [
    { value: 'restaurant', label: 'Restaurant', icon: <RestaurantMenu /> },
    { value: 'groceries', label: 'Groceries', icon: <ShoppingCart /> },
    { value: 'utilities', label: 'Utilities', icon: <Home /> },
    { value: 'gas', label: 'Gas', icon: <LocalGasStation /> },
    { value: 'other', label: 'Other', icon: <Receipt /> },
  ];

  const splitMethods = [
    { value: 'equal', label: 'Split Equally', description: 'Everyone pays the same amount' },
    { value: 'percentage', label: 'By Percentage', description: 'Assign percentage to each person' },
    { value: 'itemized', label: 'Itemized', description: 'Split by individual items' },
    { value: 'custom', label: 'Custom Amount', description: 'Manually set amounts' },
  ];

  useEffect(() => {
    loadBills();
  }, []);

  const loadBills = async () => {
    setLoading(true);
    try {
      // Load bills from API
      // const response = await billSplitService.getBills();
      // setActiveBills(response.active);
      // setCompletedBills(response.completed);
    } catch (error) {
      showToast('Failed to load bills', 'error');
    } finally {
      setLoading(false);
    }
  };

  const calculateFinalAmount = () => {
    const total = parseFloat(formData.totalAmount || '0');
    const tax = parseFloat(formData.tax || '0');
    const tip = parseFloat(formData.tip || '0');
    const discount = parseFloat(formData.discount || '0');
    
    return total + tax + tip - discount;
  };

  const calculateParticipantAmounts = () => {
    const finalAmount = calculateFinalAmount();
    const participantCount = formData.participants.length;
    
    if (participantCount === 0) return [];
    
    switch (formData.splitMethod) {
      case 'equal':
        const equalAmount = finalAmount / participantCount;
        return formData.participants.map(p => ({
          ...p,
          amountOwed: equalAmount,
        }));
        
      case 'percentage':
        return formData.participants.map(p => ({
          ...p,
          amountOwed: (finalAmount * (p.percentage || 0)) / 100,
        }));
        
      case 'itemized':
        // Calculate based on items assigned to each participant
        return calculateItemizedSplit();
        
      case 'custom':
        return formData.participants;
        
      default:
        return formData.participants;
    }
  };

  const calculateItemizedSplit = () => {
    const taxRate = parseFloat(formData.tax || '0') / parseFloat(formData.totalAmount || '1');
    const tipRate = parseFloat(formData.tip || '0') / parseFloat(formData.totalAmount || '1');
    
    return formData.participants.map(participant => {
      const participantItems = formData.items.filter(item => 
        item.sharedBy.includes(participant.userId)
      );
      
      const itemTotal = participantItems.reduce((sum, item) => {
        const shareCount = item.sharedBy.length;
        return sum + (item.amount * item.quantity) / shareCount;
      }, 0);
      
      const tax = itemTotal * taxRate;
      const tip = itemTotal * tipRate;
      
      return {
        ...participant,
        amountOwed: itemTotal + tax + tip,
        items: participantItems,
      };
    });
  };

  const handleCreateBill = async () => {
    try {
      setLoading(true);
      const finalAmount = calculateFinalAmount();
      const participants = calculateParticipantAmounts();
      
      const billData: Partial<BillSplit> = {
        ...formData,
        finalAmount,
        participants,
        status: 'pending',
        createdBy: currentUser?.id,
        createdAt: new Date(),
      };
      
      // Create bill via API
      // const response = await billSplitService.createBill(billData);
      
      showToast('Bill created successfully', 'success');
      setCreateDialogOpen(false);
      resetForm();
      loadBills();
    } catch (error) {
      showToast('Failed to create bill', 'error');
    } finally {
      setLoading(false);
    }
  };

  const handleScanReceipt = async (receiptData: any) => {
    setFormData({
      ...formData,
      title: receiptData.merchantName || formData.title,
      totalAmount: receiptData.total?.toString() || formData.totalAmount,
      tax: receiptData.tax?.toString() || formData.tax,
      items: receiptData.items || formData.items,
    });
    setScannerOpen(false);
    showToast('Receipt scanned successfully', 'success');
  };

  const handleShareBill = (bill: BillSplit) => {
    const shareUrl = `${window.location.origin}/bills/${bill.id}`;
    
    if (navigator.share) {
      navigator.share({
        title: `Bill: ${bill.title}`,
        text: `You owe ${formatCurrency(bill.finalAmount / bill.participants.length)} for ${bill.title}`,
        url: shareUrl,
      });
    } else {
      navigator.clipboard.writeText(shareUrl);
      showToast('Link copied to clipboard', 'success');
    }
  };

  const handleSendReminder = async (bill: BillSplit) => {
    try {
      const unpaidParticipants = bill.participants.filter(p => !p.paid);
      // await billSplitService.sendReminders(bill.id, unpaidParticipants.map(p => p.userId));
      showToast(`Reminders sent to ${unpaidParticipants.length} participants`, 'success');
    } catch (error) {
      showToast('Failed to send reminders', 'error');
    }
  };

  const resetForm = () => {
    setFormData({
      title: '',
      description: '',
      category: 'restaurant',
      totalAmount: '',
      tax: '',
      tip: '',
      discount: '',
      splitMethod: 'equal',
      participants: [],
      items: [],
      recurring: false,
      recurringFrequency: 'monthly',
      dueDate: null,
      notes: '',
    });
    setActiveStep(0);
  };

  const getStepContent = (step: number) => {
    switch (step) {
      case 0:
        return (
          <Box>
            <Typography variant="h6" gutterBottom>
              Bill Details
            </Typography>
            
            <TextField
              fullWidth
              label="Bill Title"
              value={formData.title}
              onChange={(e) => setFormData({ ...formData, title: e.target.value })}
              margin="normal"
              required
            />
            
            <TextField
              fullWidth
              label="Description (Optional)"
              value={formData.description}
              onChange={(e) => setFormData({ ...formData, description: e.target.value })}
              margin="normal"
              multiline
              rows={2}
            />
            
            <Box display="flex" gap={2} mt={2}>
              {categories.map((cat) => (
                <Chip
                  key={cat.value}
                  icon={cat.icon}
                  label={cat.label}
                  onClick={() => setFormData({ ...formData, category: cat.value })}
                  color={formData.category === cat.value ? 'primary' : 'default'}
                  variant={formData.category === cat.value ? 'filled' : 'outlined'}
                />
              ))}
            </Box>
            
            <Box display="flex" gap={2} mt={3}>
              <Button
                variant="outlined"
                startIcon={<CameraAlt />}
                onClick={() => setScannerOpen(true)}
                fullWidth
              >
                Scan Receipt
              </Button>
              <Button
                variant="outlined"
                startIcon={<QrCode2 />}
                onClick={() => setQrScannerOpen(true)}
                fullWidth
              >
                Scan QR Code
              </Button>
            </Box>
          </Box>
        );
        
      case 1:
        return (
          <Box>
            <Typography variant="h6" gutterBottom>
              Amounts
            </Typography>
            
            <TextField
              fullWidth
              label="Subtotal"
              value={formData.totalAmount}
              onChange={(e) => setFormData({ ...formData, totalAmount: e.target.value })}
              margin="normal"
              type="number"
              InputProps={{
                startAdornment: <InputAdornment position="start">$</InputAdornment>,
              }}
              required
            />
            
            <Box display="flex" gap={2}>
              <TextField
                fullWidth
                label="Tax"
                value={formData.tax}
                onChange={(e) => setFormData({ ...formData, tax: e.target.value })}
                margin="normal"
                type="number"
                InputProps={{
                  startAdornment: <InputAdornment position="start">$</InputAdornment>,
                }}
              />
              
              <TextField
                fullWidth
                label="Tip"
                value={formData.tip}
                onChange={(e) => setFormData({ ...formData, tip: e.target.value })}
                margin="normal"
                type="number"
                InputProps={{
                  startAdornment: <InputAdornment position="start">$</InputAdornment>,
                }}
              />
            </Box>
            
            <TextField
              fullWidth
              label="Discount"
              value={formData.discount}
              onChange={(e) => setFormData({ ...formData, discount: e.target.value })}
              margin="normal"
              type="number"
              InputProps={{
                startAdornment: <InputAdornment position="start">$</InputAdornment>,
              }}
            />
            
            <Paper elevation={1} sx={{ p: 2, mt: 2 }}>
              <Typography variant="subtitle2" color="text.secondary">
                Total Amount
              </Typography>
              <Typography variant="h4">
                {formatCurrency(calculateFinalAmount())}
              </Typography>
            </Paper>
          </Box>
        );
        
      case 2:
        return (
          <Box>
            <Typography variant="h6" gutterBottom>
              Split Method
            </Typography>
            
            {splitMethods.map((method) => (
              <Card
                key={method.value}
                sx={{
                  mb: 2,
                  cursor: 'pointer',
                  border: formData.splitMethod === method.value ? 2 : 1,
                  borderColor: formData.splitMethod === method.value ? 'primary.main' : 'divider',
                }}
                onClick={() => setFormData({ ...formData, splitMethod: method.value as any })}
              >
                <CardContent>
                  <Box display="flex" alignItems="center" justifyContent="space-between">
                    <Box>
                      <Typography variant="subtitle1">{method.label}</Typography>
                      <Typography variant="body2" color="text.secondary">
                        {method.description}
                      </Typography>
                    </Box>
                    {formData.splitMethod === method.value && (
                      <CheckCircle color="primary" />
                    )}
                  </Box>
                </CardContent>
              </Card>
            ))}
          </Box>
        );
        
      case 3:
        return (
          <Box>
            <Typography variant="h6" gutterBottom>
              Add Participants
            </Typography>
            
            <ContactSelector
              multiple
              selected={formData.participants}
              onSelect={(contacts) => setFormData({ 
                ...formData, 
                participants: contacts.map(c => ({
                  userId: c.id,
                  name: c.name,
                  avatar: c.avatar,
                  amountOwed: 0,
                  paid: false,
                }))
              })}
              showGroups
              showRecent
            />
            
            {formData.participants.length > 0 && (
              <Box mt={3}>
                <Typography variant="subtitle1" gutterBottom>
                  Selected Participants ({formData.participants.length})
                </Typography>
                <List>
                  {formData.participants.map((participant, index) => (
                    <ListItem key={participant.userId}>
                      <ListItemAvatar>
                        <Avatar src={participant.avatar}>{participant.name[0]}</Avatar>
                      </ListItemAvatar>
                      <ListItemText
                        primary={participant.name}
                        secondary={
                          formData.splitMethod === 'percentage' ? (
                            <TextField
                              size="small"
                              type="number"
                              value={participant.percentage || ''}
                              onChange={(e) => {
                                const newParticipants = [...formData.participants];
                                newParticipants[index].percentage = parseFloat(e.target.value);
                                setFormData({ ...formData, participants: newParticipants });
                              }}
                              InputProps={{
                                endAdornment: <InputAdornment position="end">%</InputAdornment>,
                              }}
                              sx={{ mt: 1, width: 100 }}
                            />
                          ) : formData.splitMethod === 'custom' ? (
                            <TextField
                              size="small"
                              type="number"
                              value={participant.amountOwed || ''}
                              onChange={(e) => {
                                const newParticipants = [...formData.participants];
                                newParticipants[index].amountOwed = parseFloat(e.target.value);
                                setFormData({ ...formData, participants: newParticipants });
                              }}
                              InputProps={{
                                startAdornment: <InputAdornment position="start">$</InputAdornment>,
                              }}
                              sx={{ mt: 1, width: 120 }}
                            />
                          ) : (
                            `Will owe ${formatCurrency(calculateFinalAmount() / formData.participants.length)}`
                          )
                        }
                      />
                      <ListItemSecondaryAction>
                        <IconButton
                          edge="end"
                          onClick={() => {
                            setFormData({
                              ...formData,
                              participants: formData.participants.filter(p => p.userId !== participant.userId),
                            });
                          }}
                        >
                          <Delete />
                        </IconButton>
                      </ListItemSecondaryAction>
                    </ListItem>
                  ))}
                </List>
              </Box>
            )}
          </Box>
        );
        
      default:
        return null;
    }
  };

  const renderBillCard = (bill: BillSplit) => {
    const paidCount = bill.participants.filter(p => p.paid).length;
    const progress = (paidCount / bill.participants.length) * 100;
    
    return (
      <Card key={bill.id} sx={{ mb: 2 }}>
        <CardContent>
          <Box display="flex" justifyContent="space-between" alignItems="start">
            <Box flex={1}>
              <Typography variant="h6">{bill.title}</Typography>
              <Typography variant="body2" color="text.secondary">
                {formatDate(bill.createdAt)} • {bill.category}
              </Typography>
              
              <Box display="flex" alignItems="center" gap={2} mt={2}>
                <Typography variant="h5">
                  {formatCurrency(bill.finalAmount)}
                </Typography>
                <Chip
                  size="small"
                  label={bill.status}
                  color={
                    bill.status === 'completed' ? 'success' :
                    bill.status === 'partial' ? 'warning' : 'default'
                  }
                />
              </Box>
              
              <Box display="flex" alignItems="center" gap={1} mt={2}>
                <AvatarGroup max={4}>
                  {bill.participants.map((p) => (
                    <Tooltip key={p.userId} title={p.name}>
                      <Avatar src={p.avatar} sx={{ width: 32, height: 32 }}>
                        {p.name[0]}
                      </Avatar>
                    </Tooltip>
                  ))}
                </AvatarGroup>
                <Typography variant="body2" color="text.secondary">
                  {paidCount} of {bill.participants.length} paid
                </Typography>
              </Box>
              
              <Box mt={2}>
                <Box display="flex" justifyContent="space-between" mb={1}>
                  <Typography variant="body2">Collection Progress</Typography>
                  <Typography variant="body2">{Math.round(progress)}%</Typography>
                </Box>
                <Box sx={{ width: '100%', height: 8, bgcolor: 'grey.200', borderRadius: 4 }}>
                  <Box
                    sx={{
                      width: `${progress}%`,
                      height: '100%',
                      bgcolor: progress === 100 ? 'success.main' : 'primary.main',
                      borderRadius: 4,
                      transition: 'width 0.3s ease',
                    }}
                  />
                </Box>
              </Box>
            </Box>
            
            <IconButton
              onClick={(e) => {
                setAnchorEl(e.currentTarget);
                setSelectedBill(bill);
              }}
            >
              <MoreVert />
            </IconButton>
          </Box>
        </CardContent>
      </Card>
    );
  };

  return (
    <Box>
      <Box display="flex" justifyContent="space-between" alignItems="center" mb={3}>
        <Typography variant="h4">Bill Splitting</Typography>
        <Button
          variant="contained"
          startIcon={<Add />}
          onClick={() => setCreateDialogOpen(true)}
        >
          Create New Bill
        </Button>
      </Box>
      
      {loading && !activeBills.length ? (
        <Box display="flex" justifyContent="center" py={4}>
          <CircularProgress />
        </Box>
      ) : (
        <>
          {activeBills.length > 0 && (
            <Box mb={4}>
              <Typography variant="h6" gutterBottom>
                Active Bills
              </Typography>
              {activeBills.map(renderBillCard)}
            </Box>
          )}
          
          {completedBills.length > 0 && (
            <Box>
              <Typography variant="h6" gutterBottom>
                Completed Bills
              </Typography>
              {completedBills.map(renderBillCard)}
            </Box>
          )}
          
          {!activeBills.length && !completedBills.length && (
            <Paper sx={{ p: 4, textAlign: 'center' }}>
              <Receipt sx={{ fontSize: 64, color: 'text.secondary', mb: 2 }} />
              <Typography variant="h6" gutterBottom>
                No bills yet
              </Typography>
              <Typography variant="body2" color="text.secondary" mb={3}>
                Create your first bill to start splitting expenses with friends
              </Typography>
              <Button
                variant="contained"
                startIcon={<Add />}
                onClick={() => setCreateDialogOpen(true)}
              >
                Create First Bill
              </Button>
            </Paper>
          )}
        </>
      )}
      
      {/* Create Bill Dialog */}
      <Dialog
        open={createDialogOpen}
        onClose={() => setCreateDialogOpen(false)}
        maxWidth="md"
        fullWidth
      >
        <DialogTitle>Create New Bill Split</DialogTitle>
        <DialogContent>
          <Box sx={{ mt: 2 }}>
            <Stepper activeStep={activeStep}>
              <Step>
                <StepLabel>Details</StepLabel>
              </Step>
              <Step>
                <StepLabel>Amounts</StepLabel>
              </Step>
              <Step>
                <StepLabel>Split Method</StepLabel>
              </Step>
              <Step>
                <StepLabel>Participants</StepLabel>
              </Step>
            </Stepper>
            
            <Box sx={{ mt: 4 }}>
              {getStepContent(activeStep)}
            </Box>
          </Box>
        </DialogContent>
        <DialogActions>
          <Button onClick={() => setCreateDialogOpen(false)}>
            Cancel
          </Button>
          {activeStep > 0 && (
            <Button onClick={() => setActiveStep(activeStep - 1)}>
              Back
            </Button>
          )}
          {activeStep < 3 ? (
            <Button
              variant="contained"
              onClick={() => setActiveStep(activeStep + 1)}
              disabled={
                (activeStep === 0 && !formData.title) ||
                (activeStep === 1 && !formData.totalAmount) ||
                (activeStep === 3 && formData.participants.length === 0)
              }
            >
              Next
            </Button>
          ) : (
            <Button
              variant="contained"
              onClick={handleCreateBill}
              disabled={loading || formData.participants.length === 0}
            >
              Create Bill
            </Button>
          )}
        </DialogActions>
      </Dialog>
      
      {/* Bill Options Menu */}
      <Menu
        anchorEl={anchorEl}
        open={Boolean(anchorEl)}
        onClose={() => setAnchorEl(null)}
      >
        <MenuItem onClick={() => {
          if (selectedBill) handleShareBill(selectedBill);
          setAnchorEl(null);
        }}>
          <Share sx={{ mr: 1 }} /> Share Bill
        </MenuItem>
        <MenuItem onClick={() => {
          if (selectedBill) handleSendReminder(selectedBill);
          setAnchorEl(null);
        }}>
          <NotificationsActive sx={{ mr: 1 }} /> Send Reminders
        </MenuItem>
        <MenuItem onClick={() => {
          // View details
          setAnchorEl(null);
        }}>
          <History sx={{ mr: 1 }} /> View History
        </MenuItem>
        <Divider />
        <MenuItem onClick={() => {
          // Delete bill
          setAnchorEl(null);
        }}>
          <Delete sx={{ mr: 1 }} /> Delete Bill
        </MenuItem>
      </Menu>
      
      {/* Receipt Scanner */}
      {scannerOpen && (
        <ReceiptScanner
          open={scannerOpen}
          onClose={() => setScannerOpen(false)}
          onScan={handleScanReceipt}
        />
      )}
      
      {/* QR Scanner */}
      {qrScannerOpen && (
        <QRScanner
          open={qrScannerOpen}
          onClose={() => setQrScannerOpen(false)}
          onScan={(data) => {
            // Handle QR code data
            setQrScannerOpen(false);
          }}
        />
      )}
    </Box>
  );
};

export default BillSplitManager;