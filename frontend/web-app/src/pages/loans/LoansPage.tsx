import React from 'react';
import {
  Container,
  Typography,
  Box,
  Button,
  Grid,
  Card,
  CardContent,
  Chip,
  LinearProgress,
} from '@mui/material';
import AddIcon from '@mui/icons-material/Add';
import { formatCurrency } from '@/utils/formatters';

const LoansPage: React.FC = () => {
  const loans = [
    {
      id: '1',
      type: 'Personal Loan',
      amount: 5000,
      remainingBalance: 3200,
      interestRate: 8.5,
      monthlyPayment: 250,
      nextPaymentDate: '2025-12-15',
      status: 'ACTIVE',
    },
  ];

  return (
    <Container maxWidth="lg" sx={{ mt: 4, mb: 4 }}>
      <Box sx={{ display: 'flex', justifyContent: 'space-between', mb: 4 }}>
        <Box>
          <Typography variant="h4">My Loans</Typography>
          <Typography variant="body2" color="text.secondary">
            Manage your loan applications and repayments
          </Typography>
        </Box>
        <Button variant="contained" startIcon={<AddIcon />}>
          Apply for Loan
        </Button>
      </Box>

      <Grid container spacing={3}>
        {loans.map((loan) => {
          const progress = ((loan.amount - loan.remainingBalance) / loan.amount) * 100;
          return (
            <Grid item xs={12} md={6} key={loan.id}>
              <Card>
                <CardContent>
                  <Box sx={{ display: 'flex', justifyContent: 'space-between', mb: 2 }}>
                    <Typography variant="h6">{loan.type}</Typography>
                    <Chip label={loan.status} color="primary" size="small" />
                  </Box>

                  <Typography variant="h4" gutterBottom>
                    {formatCurrency(loan.remainingBalance)}
                  </Typography>
                  <Typography variant="caption" color="text.secondary">
                    Remaining of {formatCurrency(loan.amount)}
                  </Typography>

                  <Box sx={{ my: 2 }}>
                    <LinearProgress variant="determinate" value={progress} sx={{ height: 8, borderRadius: 4 }} />
                  </Box>

                  <Grid container spacing={2}>
                    <Grid item xs={6}>
                      <Typography variant="caption" color="text.secondary">
                        Monthly Payment
                      </Typography>
                      <Typography variant="body2" fontWeight="bold">
                        {formatCurrency(loan.monthlyPayment)}
                      </Typography>
                    </Grid>
                    <Grid item xs={6}>
                      <Typography variant="caption" color="text.secondary">
                        Interest Rate
                      </Typography>
                      <Typography variant="body2" fontWeight="bold">
                        {loan.interestRate}%
                      </Typography>
                    </Grid>
                  </Grid>

                  <Button fullWidth variant="contained" sx={{ mt: 2 }}>
                    Make Payment
                  </Button>
                </CardContent>
              </Card>
            </Grid>
          );
        })}
      </Grid>
    </Container>
  );
};

export default LoansPage;
