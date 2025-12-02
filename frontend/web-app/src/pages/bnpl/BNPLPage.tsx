import React, { useEffect, useState } from 'react';
import {
  Container,
  Typography,
  Box,
  Button,
  Grid,
  Card,
  CardContent,
  LinearProgress,
  Chip,
  Alert,
  Dialog,
  DialogTitle,
  DialogContent,
  DialogActions,
  TextField,
} from '@mui/material';
import AddIcon from '@mui/icons-material/Add';
import { useAppDispatch, useAppSelector } from '@/hooks/redux';
import { fetchBNPLPlans, createBNPLPlan } from '@/store/slices/bnplSlice';
import { formatCurrency, formatDate } from '@/utils/formatters';
import { BNPLStatus } from '@/types/bnpl';
import toast from 'react-hot-toast';

const BNPLPage: React.FC = () => {
  const dispatch = useAppDispatch();
  const { plans, loading, error } = useAppSelector((state) => state.bnpl);
  const [applyDialogOpen, setApplyDialogOpen] = useState(false);

  useEffect(() => {
    dispatch(fetchBNPLPlans());
  }, [dispatch]);

  const getStatusColor = (status: BNPLStatus) => {
    switch (status) {
      case BNPLStatus.ACTIVE:
        return 'primary';
      case BNPLStatus.COMPLETED:
        return 'success';
      case BNPLStatus.DEFAULTED:
        return 'error';
      default:
        return 'default';
    }
  };

  return (
    <Container maxWidth="lg" sx={{ mt: 4, mb: 4 }}>
      <Box sx={{ display: 'flex', justifyContent: 'space-between', mb: 4 }}>
        <Box>
          <Typography variant="h4">Buy Now, Pay Later</Typography>
          <Typography variant="body2" color="text.secondary">
            Manage your payment plans
          </Typography>
        </Box>
        <Button variant="contained" startIcon={<AddIcon />} onClick={() => setApplyDialogOpen(true)}>
          Apply for BNPL
        </Button>
      </Box>

      {error && <Alert severity="error" sx={{ mb: 3 }}>{error}</Alert>}

      {plans.length === 0 ? (
        <Card>
          <CardContent sx={{ textAlign: 'center', py: 8 }}>
            <Typography variant="h6" color="text.secondary">
              No payment plans yet
            </Typography>
          </CardContent>
        </Card>
      ) : (
        <Grid container spacing={3}>
          {plans.map((plan) => {
            const progress = (plan.paidInstallments / plan.numberOfInstallments) * 100;
            return (
              <Grid item xs={12} md={6} key={plan.id}>
                <Card>
                  <CardContent>
                    <Box sx={{ display: 'flex', justifyContent: 'space-between', mb: 2 }}>
                      <Typography variant="h6">{plan.merchantName}</Typography>
                      <Chip label={plan.status} color={getStatusColor(plan.status)} size="small" />
                    </Box>

                    <Typography variant="h4" gutterBottom>
                      {formatCurrency(plan.totalAmount)}
                    </Typography>

                    <Box sx={{ mb: 2 }}>
                      <Box sx={{ display: 'flex', justifyContent: 'space-between', mb: 1 }}>
                        <Typography variant="body2" color="text.secondary">
                          Progress
                        </Typography>
                        <Typography variant="body2" fontWeight="bold">
                          {plan.paidInstallments}/{plan.numberOfInstallments} payments
                        </Typography>
                      </Box>
                      <LinearProgress variant="determinate" value={progress} sx={{ height: 8, borderRadius: 4 }} />
                    </Box>

                    <Grid container spacing={2}>
                      <Grid item xs={6}>
                        <Typography variant="caption" color="text.secondary">
                          Next Payment
                        </Typography>
                        <Typography variant="body2" fontWeight="bold">
                          {formatCurrency(plan.nextPaymentAmount)}
                        </Typography>
                      </Grid>
                      <Grid item xs={6}>
                        <Typography variant="caption" color="text.secondary">
                          Due Date
                        </Typography>
                        <Typography variant="body2" fontWeight="bold">
                          {formatDate(plan.nextPaymentDate)}
                        </Typography>
                      </Grid>
                    </Grid>

                    <Button fullWidth variant="outlined" sx={{ mt: 2 }}>
                      View Details
                    </Button>
                  </CardContent>
                </Card>
              </Grid>
            );
          })}
        </Grid>
      )}
    </Container>
  );
};

export default BNPLPage;
