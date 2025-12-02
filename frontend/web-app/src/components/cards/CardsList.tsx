import React from 'react';
import {
  Grid,
  Card as MuiCard,
  CardContent,
  CardActions,
  Typography,
  Chip,
  IconButton,
  Box,
  Button,
} from '@mui/material';
import CreditCardIcon from '@mui/icons-material/CreditCard';
import FreezeIcon from '@mui/icons-material/AcUnit';
import BlockIcon from '@mui/icons-material/Block';
import MoreVertIcon from '@mui/icons-material/MoreVert';
import VisibilityIcon from '@mui/icons-material/Visibility';
import { Card, CardStatus } from '@/types/card';
import { formatCurrency } from '@/utils/formatters';

interface CardsListProps {
  cards: Card[];
  onCardClick: (card: Card) => void;
  onFreeze: (cardId: string) => void;
  onUnfreeze: (cardId: string) => void;
}

const getStatusColor = (status: CardStatus) => {
  switch (status) {
    case CardStatus.ACTIVE:
      return 'success';
    case CardStatus.FROZEN:
      return 'info';
    case CardStatus.BLOCKED:
    case CardStatus.CANCELLED:
      return 'error';
    case CardStatus.EXPIRED:
      return 'warning';
    default:
      return 'default';
  }
};

const getCardBrandColor = (brand: string) => {
  switch (brand.toUpperCase()) {
    case 'VISA':
      return '#1A1F71';
    case 'MASTERCARD':
      return '#EB001B';
    case 'AMEX':
      return '#006FCF';
    default:
      return '#757575';
  }
};

const CardsList: React.FC<CardsListProps> = ({ cards, onCardClick, onFreeze, onUnfreeze }) => {
  if (cards.length === 0) {
    return (
      <Box sx={{ textAlign: 'center', py: 8 }}>
        <CreditCardIcon sx={{ fontSize: 80, color: 'text.secondary', mb: 2 }} />
        <Typography variant="h6" color="text.secondary">
          No cards yet
        </Typography>
        <Typography variant="body2" color="text.secondary">
          Create your first card to get started
        </Typography>
      </Box>
    );
  }

  return (
    <Grid container spacing={3}>
      {cards.map((card) => (
        <Grid item xs={12} sm={6} md={4} key={card.id}>
          <MuiCard
            sx={{
              height: '100%',
              background: `linear-gradient(135deg, ${getCardBrandColor(card.brand)} 0%, ${getCardBrandColor(card.brand)}DD 100%)`,
              color: 'white',
              cursor: 'pointer',
              transition: 'transform 0.2s',
              '&:hover': {
                transform: 'translateY(-4px)',
              },
            }}
            onClick={() => onCardClick(card)}
          >
            <CardContent>
              <Box sx={{ display: 'flex', justifyContent: 'space-between', mb: 2 }}>
                <Typography variant="body2" sx={{ opacity: 0.9 }}>
                  {card.type}
                </Typography>
                <Chip
                  label={card.status}
                  size="small"
                  color={getStatusColor(card.status)}
                  sx={{ fontWeight: 'bold' }}
                />
              </Box>

              <CreditCardIcon sx={{ fontSize: 40, mb: 2, opacity: 0.9 }} />

              <Typography variant="h5" sx={{ letterSpacing: 2, mb: 2 }}>
                •••• •••• •••• {card.last4}
              </Typography>

              <Box sx={{ display: 'flex', justifyContent: 'space-between' }}>
                <Box>
                  <Typography variant="caption" sx={{ opacity: 0.7, display: 'block' }}>
                    CARDHOLDER
                  </Typography>
                  <Typography variant="body2" sx={{ fontWeight: 'bold' }}>
                    {card.cardholderName}
                  </Typography>
                </Box>
                <Box>
                  <Typography variant="caption" sx={{ opacity: 0.7, display: 'block' }}>
                    EXPIRES
                  </Typography>
                  <Typography variant="body2" sx={{ fontWeight: 'bold' }}>
                    {String(card.expiryMonth).padStart(2, '0')}/{String(card.expiryYear).slice(-2)}
                  </Typography>
                </Box>
              </Box>

              {card.dailySpendLimit && (
                <Box sx={{ mt: 2, pt: 2, borderTop: '1px solid rgba(255,255,255,0.2)' }}>
                  <Typography variant="caption" sx={{ opacity: 0.7 }}>
                    Daily Limit: {formatCurrency(card.remainingDailyLimit || 0)} /{' '}
                    {formatCurrency(card.dailySpendLimit)}
                  </Typography>
                </Box>
              )}
            </CardContent>

            <CardActions sx={{ justifyContent: 'space-between', px: 2, pb: 2 }}>
              <Button
                size="small"
                startIcon={<VisibilityIcon />}
                sx={{ color: 'white' }}
                onClick={(e) => {
                  e.stopPropagation();
                  onCardClick(card);
                }}
              >
                Details
              </Button>
              {card.status === CardStatus.ACTIVE && (
                <IconButton
                  size="small"
                  sx={{ color: 'white' }}
                  onClick={(e) => {
                    e.stopPropagation();
                    onFreeze(card.id);
                  }}
                >
                  <FreezeIcon />
                </IconButton>
              )}
              {card.status === CardStatus.FROZEN && (
                <Button
                  size="small"
                  sx={{ color: 'white' }}
                  onClick={(e) => {
                    e.stopPropagation();
                    onUnfreeze(card.id);
                  }}
                >
                  Unfreeze
                </Button>
              )}
            </CardActions>
          </MuiCard>
        </Grid>
      ))}
    </Grid>
  );
};

export default CardsList;
