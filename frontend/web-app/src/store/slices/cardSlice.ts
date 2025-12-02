import { createSlice, createAsyncThunk, PayloadAction } from '@reduxjs/toolkit';
import { cardService } from '@/services/cardService';
import {
  Card,
  CardState,
  CreateVirtualCardRequest,
  CreatePhysicalCardRequest,
  UpdateCardLimitsRequest,
  CardTransaction,
} from '@/types/card';

const initialState: CardState = {
  cards: [],
  selectedCard: null,
  transactions: [],
  loading: false,
  error: null,
};

// Async thunks
export const fetchCards = createAsyncThunk('card/fetchCards', async () => {
  return await cardService.getCards();
});

export const fetchCard = createAsyncThunk('card/fetchCard', async (cardId: string) => {
  return await cardService.getCard(cardId);
});

export const createVirtualCard = createAsyncThunk(
  'card/createVirtual',
  async (request: CreateVirtualCardRequest) => {
    return await cardService.createVirtualCard(request);
  }
);

export const createPhysicalCard = createAsyncThunk(
  'card/createPhysical',
  async (request: CreatePhysicalCardRequest) => {
    return await cardService.createPhysicalCard(request);
  }
);

export const freezeCard = createAsyncThunk('card/freeze', async (cardId: string) => {
  await cardService.freezeCard(cardId);
  return cardId;
});

export const unfreezeCard = createAsyncThunk('card/unfreeze', async (cardId: string) => {
  await cardService.unfreezeCard(cardId);
  return cardId;
});

export const blockCard = createAsyncThunk(
  'card/block',
  async ({ cardId, reason }: { cardId: string; reason: string }) => {
    await cardService.blockCard(cardId, reason);
    return cardId;
  }
);

export const updateCardLimits = createAsyncThunk(
  'card/updateLimits',
  async ({ cardId, request }: { cardId: string; request: UpdateCardLimitsRequest }) => {
    return await cardService.updateLimits(cardId, request);
  }
);

export const fetchCardTransactions = createAsyncThunk(
  'card/fetchTransactions',
  async (cardId: string) => {
    return await cardService.getCardTransactions(cardId);
  }
);

export const cancelCard = createAsyncThunk(
  'card/cancel',
  async ({ cardId, reason }: { cardId: string; reason: string }) => {
    await cardService.cancelCard(cardId, reason);
    return cardId;
  }
);

const cardSlice = createSlice({
  name: 'card',
  initialState,
  reducers: {
    setSelectedCard: (state, action: PayloadAction<Card | null>) => {
      state.selectedCard = action.payload;
    },
    clearError: (state) => {
      state.error = null;
    },
  },
  extraReducers: (builder) => {
    // Fetch cards
    builder
      .addCase(fetchCards.pending, (state) => {
        state.loading = true;
        state.error = null;
      })
      .addCase(fetchCards.fulfilled, (state, action) => {
        state.loading = false;
        state.cards = action.payload;
      })
      .addCase(fetchCards.rejected, (state, action) => {
        state.loading = false;
        state.error = action.error.message || 'Failed to fetch cards';
      });

    // Fetch single card
    builder
      .addCase(fetchCard.pending, (state) => {
        state.loading = true;
        state.error = null;
      })
      .addCase(fetchCard.fulfilled, (state, action) => {
        state.loading = false;
        state.selectedCard = action.payload;
      })
      .addCase(fetchCard.rejected, (state, action) => {
        state.loading = false;
        state.error = action.error.message || 'Failed to fetch card';
      });

    // Create virtual card
    builder
      .addCase(createVirtualCard.pending, (state) => {
        state.loading = true;
        state.error = null;
      })
      .addCase(createVirtualCard.fulfilled, (state, action) => {
        state.loading = false;
        state.cards.push(action.payload);
      })
      .addCase(createVirtualCard.rejected, (state, action) => {
        state.loading = false;
        state.error = action.error.message || 'Failed to create virtual card';
      });

    // Create physical card
    builder
      .addCase(createPhysicalCard.pending, (state) => {
        state.loading = true;
        state.error = null;
      })
      .addCase(createPhysicalCard.fulfilled, (state, action) => {
        state.loading = false;
        state.cards.push(action.payload);
      })
      .addCase(createPhysicalCard.rejected, (state, action) => {
        state.loading = false;
        state.error = action.error.message || 'Failed to create physical card';
      });

    // Freeze card
    builder
      .addCase(freezeCard.pending, (state) => {
        state.loading = true;
      })
      .addCase(freezeCard.fulfilled, (state, action) => {
        state.loading = false;
        const card = state.cards.find((c) => c.id === action.payload);
        if (card) {
          card.status = 'FROZEN';
        }
        if (state.selectedCard?.id === action.payload) {
          state.selectedCard.status = 'FROZEN';
        }
      })
      .addCase(freezeCard.rejected, (state, action) => {
        state.loading = false;
        state.error = action.error.message || 'Failed to freeze card';
      });

    // Unfreeze card
    builder
      .addCase(unfreezeCard.pending, (state) => {
        state.loading = true;
      })
      .addCase(unfreezeCard.fulfilled, (state, action) => {
        state.loading = false;
        const card = state.cards.find((c) => c.id === action.payload);
        if (card) {
          card.status = 'ACTIVE';
        }
        if (state.selectedCard?.id === action.payload) {
          state.selectedCard.status = 'ACTIVE';
        }
      })
      .addCase(unfreezeCard.rejected, (state, action) => {
        state.loading = false;
        state.error = action.error.message || 'Failed to unfreeze card';
      });

    // Block card
    builder
      .addCase(blockCard.pending, (state) => {
        state.loading = true;
      })
      .addCase(blockCard.fulfilled, (state, action) => {
        state.loading = false;
        const card = state.cards.find((c) => c.id === action.payload);
        if (card) {
          card.status = 'BLOCKED';
        }
        if (state.selectedCard?.id === action.payload) {
          state.selectedCard.status = 'BLOCKED';
        }
      })
      .addCase(blockCard.rejected, (state, action) => {
        state.loading = false;
        state.error = action.error.message || 'Failed to block card';
      });

    // Update limits
    builder
      .addCase(updateCardLimits.pending, (state) => {
        state.loading = true;
      })
      .addCase(updateCardLimits.fulfilled, (state, action) => {
        state.loading = false;
        const index = state.cards.findIndex((c) => c.id === action.payload.id);
        if (index !== -1) {
          state.cards[index] = action.payload;
        }
        if (state.selectedCard?.id === action.payload.id) {
          state.selectedCard = action.payload;
        }
      })
      .addCase(updateCardLimits.rejected, (state, action) => {
        state.loading = false;
        state.error = action.error.message || 'Failed to update limits';
      });

    // Fetch transactions
    builder
      .addCase(fetchCardTransactions.pending, (state) => {
        state.loading = true;
      })
      .addCase(fetchCardTransactions.fulfilled, (state, action) => {
        state.loading = false;
        state.transactions = action.payload;
      })
      .addCase(fetchCardTransactions.rejected, (state, action) => {
        state.loading = false;
        state.error = action.error.message || 'Failed to fetch transactions';
      });

    // Cancel card
    builder
      .addCase(cancelCard.pending, (state) => {
        state.loading = true;
      })
      .addCase(cancelCard.fulfilled, (state, action) => {
        state.loading = false;
        state.cards = state.cards.filter((c) => c.id !== action.payload);
        if (state.selectedCard?.id === action.payload) {
          state.selectedCard = null;
        }
      })
      .addCase(cancelCard.rejected, (state, action) => {
        state.loading = false;
        state.error = action.error.message || 'Failed to cancel card';
      });
  },
});

export const { setSelectedCard, clearError } = cardSlice.actions;
export default cardSlice.reducer;
