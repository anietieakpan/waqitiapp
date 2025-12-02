import React, { useEffect, useState } from 'react';
import {
  Container,
  Typography,
  Box,
  Button,
  CircularProgress,
  Alert,
  Fab,
  Dialog,
  DialogTitle,
  DialogContent,
  DialogActions,
  List,
  ListItem,
  ListItemIcon,
  ListItemText,
  Divider,
} from '@mui/material';
import AddIcon from '@mui/icons-material/Add';
import CreditCardIcon from '@mui/icons-material/CreditCard';
import AccountBalanceWalletIcon from '@mui/icons-material/AccountBalanceWallet';
import { useNavigate } from 'react-router-dom';
import { useAppDispatch, useAppSelector } from '@/hooks/redux';
import {
  fetchCards,
  createVirtualCard,
  createPhysicalCard,
  freezeCard,
  unfreezeCard,
  setSelectedCard,
} from '@/store/slices/cardSlice';
import CardsList from '@/components/cards/CardsList';
import CreateVirtualCardForm from '@/components/cards/CreateVirtualCardForm';
import { Card, CreateVirtualCardRequest } from '@/types/card';
import toast from 'react-hot-toast';

const CardsPage: React.FC = () => {
  const dispatch = useAppDispatch();
  const navigate = useNavigate();
  const { cards, loading, error } = useAppSelector((state) => state.card);

  const [createDialogOpen, setCreateDialogOpen] = useState(false);
  const [virtualCardFormOpen, setVirtualCardFormOpen] = useState(false);

  useEffect(() => {
    dispatch(fetchCards());
  }, [dispatch]);

  const handleCardClick = (card: Card) => {
    dispatch(setSelectedCard(card));
    navigate(`/cards/${card.id}`);
  };

  const handleFreeze = async (cardId: string) => {
    try {
      await dispatch(freezeCard(cardId)).unwrap();
      toast.success('Card frozen successfully');
    } catch (err: any) {
      toast.error(err.message || 'Failed to freeze card');
    }
  };

  const handleUnfreeze = async (cardId: string) => {
    try {
      await dispatch(unfreezeCard(cardId)).unwrap();
      toast.success('Card unfrozen successfully');
    } catch (err: any) {
      toast.error(err.message || 'Failed to unfreeze card');
    }
  };

  const handleCreateVirtualCard = async (data: CreateVirtualCardRequest) => {
    await dispatch(createVirtualCard(data)).unwrap();
    toast.success('Virtual card created successfully!');
  };

  const openCreateDialog = () => {
    setCreateDialogOpen(true);
  };

  const closeCreateDialog = () => {
    setCreateDialogOpen(false);
  };

  const selectVirtualCard = () => {
    closeCreateDialog();
    setVirtualCardFormOpen(true);
  };

  const selectPhysicalCard = () => {
    closeCreateDialog();
    toast.info('Physical card requests coming soon!');
  };

  return (
    <Container maxWidth="lg" sx={{ mt: 4, mb: 4 }}>
      {/* Header */}
      <Box sx={{ display: 'flex', justifyContent: 'space-between', alignItems: 'center', mb: 4 }}>
        <Box>
          <Typography variant="h4" component="h1" gutterBottom>
            My Cards
          </Typography>
          <Typography variant="body2" color="text.secondary">
            Manage your virtual and physical cards
          </Typography>
        </Box>
        <Button variant="contained" startIcon={<AddIcon />} onClick={openCreateDialog}>
          New Card
        </Button>
      </Box>

      {/* Error Alert */}
      {error && (
        <Alert severity="error" sx={{ mb: 3 }}>
          {error}
        </Alert>
      )}

      {/* Loading State */}
      {loading && cards.length === 0 ? (
        <Box sx={{ display: 'flex', justifyContent: 'center', py: 8 }}>
          <CircularProgress />
        </Box>
      ) : (
        /* Cards List */
        <CardsList
          cards={cards}
          onCardClick={handleCardClick}
          onFreeze={handleFreeze}
          onUnfreeze={handleUnfreeze}
        />
      )}

      {/* Create Card Type Selection Dialog */}
      <Dialog open={createDialogOpen} onClose={closeCreateDialog}>
        <DialogTitle>Choose Card Type</DialogTitle>
        <DialogContent>
          <List>
            <ListItem button onClick={selectVirtualCard}>
              <ListItemIcon>
                <AccountBalanceWalletIcon color="primary" />
              </ListItemIcon>
              <ListItemText
                primary="Virtual Card"
                secondary="Instant creation, use online immediately"
              />
            </ListItem>
            <Divider />
            <ListItem button onClick={selectPhysicalCard}>
              <ListItemIcon>
                <CreditCardIcon color="primary" />
              </ListItemIcon>
              <ListItemText
                primary="Physical Card"
                secondary="Shipped to your address in 7-10 days"
              />
            </ListItem>
          </List>
        </DialogContent>
        <DialogActions>
          <Button onClick={closeCreateDialog}>Cancel</Button>
        </DialogActions>
      </Dialog>

      {/* Create Virtual Card Form */}
      <CreateVirtualCardForm
        open={virtualCardFormOpen}
        onClose={() => setVirtualCardFormOpen(false)}
        onSubmit={handleCreateVirtualCard}
        loading={loading}
      />

      {/* Floating Action Button for Mobile */}
      <Fab
        color="primary"
        aria-label="add card"
        sx={{
          position: 'fixed',
          bottom: 16,
          right: 16,
          display: { xs: 'flex', sm: 'none' },
        }}
        onClick={openCreateDialog}
      >
        <AddIcon />
      </Fab>
    </Container>
  );
};

export default CardsPage;
