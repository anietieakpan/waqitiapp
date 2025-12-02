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
} from '@mui/material';
import AddIcon from '@mui/icons-material/Add';
import SavingsIcon from '@mui/icons-material/Savings';
import { formatCurrency } from '@/utils/formatters';

interface SavingsGoal {
  id: string;
  name: string;
  targetAmount: number;
  currentAmount: number;
  targetDate: string;
  autoSaveEnabled: boolean;
  autoSaveAmount?: number;
  interestRate: number;
}

const SavingsPage: React.FC = () => {
  const [goals, setGoals] = useState<SavingsGoal[]>([
    {
      id: '1',
      name: 'Emergency Fund',
      targetAmount: 10000,
      currentAmount: 3500,
      targetDate: '2025-12-31',
      autoSaveEnabled: true,
      autoSaveAmount: 200,
      interestRate: 2.5,
    },
  ]);

  return (
    <Container maxWidth="lg" sx={{ mt: 4, mb: 4 }}>
      <Box sx={{ display: 'flex', justifyContent: 'space-between', mb: 4 }}>
        <Box>
          <Typography variant="h4">Savings Goals</Typography>
          <Typography variant="body2" color="text.secondary">
            Track and achieve your savings goals
          </Typography>
        </Box>
        <Button variant="contained" startIcon={<AddIcon />}>
          New Goal
        </Button>
      </Box>

      <Grid container spacing={3}>
        {goals.map((goal) => {
          const progress = (goal.currentAmount / goal.targetAmount) * 100;
          return (
            <Grid item xs={12} md={6} key={goal.id}>
              <Card>
                <CardContent>
                  <Box sx={{ display: 'flex', alignItems: 'center', mb: 2 }}>
                    <SavingsIcon color="primary" sx={{ mr: 1 }} />
                    <Typography variant="h6">{goal.name}</Typography>
                  </Box>

                  <Box sx={{ mb: 2 }}>
                    <Box sx={{ display: 'flex', justifyContent: 'space-between', mb: 1 }}>
                      <Typography variant="body2" color="text.secondary">
                        {formatCurrency(goal.currentAmount)} of {formatCurrency(goal.targetAmount)}
                      </Typography>
                      <Typography variant="body2" fontWeight="bold">
                        {progress.toFixed(0)}%
                      </Typography>
                    </Box>
                    <LinearProgress variant="determinate" value={progress} sx={{ height: 8, borderRadius: 4 }} />
                  </Box>

                  {goal.autoSaveEnabled && (
                    <Chip
                      label={`Auto-save: ${formatCurrency(goal.autoSaveAmount || 0)}/month`}
                      size="small"
                      color="success"
                      sx={{ mb: 2 }}
                    />
                  )}

                  <Grid container spacing={2}>
                    <Grid item xs={6}>
                      <Typography variant="caption" color="text.secondary">
                        Target Date
                      </Typography>
                      <Typography variant="body2">
                        {new Date(goal.targetDate).toLocaleDateString()}
                      </Typography>
                    </Grid>
                    <Grid item xs={6}>
                      <Typography variant="caption" color="text.secondary">
                        Interest Rate
                      </Typography>
                      <Typography variant="body2">{goal.interestRate}% APY</Typography>
                    </Grid>
                  </Grid>

                  <Button fullWidth variant="outlined" sx={{ mt: 2 }}>
                    Add Funds
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

export default SavingsPage;
