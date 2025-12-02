import { createSlice, createAsyncThunk, PayloadAction } from '@reduxjs/toolkit';
import { socialPaymentService } from '../../services/socialPaymentService';
import { 
  SocialPayment, 
  PaymentFeedItem, 
  PaymentComment, 
  PaymentReaction,
  PaymentVisibility 
} from '../../types/socialPayment';

interface SocialPaymentState {
  feedItems: PaymentFeedItem[];
  publicFeed: PaymentFeedItem[];
  trendingPayments: PaymentFeedItem[];
  selectedPayment: SocialPayment | null;
  comments: Record<string, PaymentComment[]>;
  reactions: Record<string, PaymentReaction[]>;
  userReactions: Record<string, string>; // paymentId -> reactionType
  loading: boolean;
  feedLoading: boolean;
  commentsLoading: boolean;
  error: string | null;
  hasMore: boolean;
  currentPage: number;
  feedFilter: 'all' | 'friends' | 'following';
}

const initialState: SocialPaymentState = {
  feedItems: [],
  publicFeed: [],
  trendingPayments: [],
  selectedPayment: null,
  comments: {},
  reactions: {},
  userReactions: {},
  loading: false,
  feedLoading: false,
  commentsLoading: false,
  error: null,
  hasMore: true,
  currentPage: 1,
  feedFilter: 'all',
};

// Async thunks
export const fetchPaymentFeed = createAsyncThunk(
  'socialPayment/fetchFeed',
  async ({ page = 1, filter = 'all' }: { page?: number; filter?: string }) => {
    const response = await socialPaymentService.getPaymentFeed(page, filter);
    return response;
  }
);

export const fetchPublicFeed = createAsyncThunk(
  'socialPayment/fetchPublicFeed',
  async ({ page = 1 }: { page?: number }) => {
    const response = await socialPaymentService.getPublicFeed(page);
    return response;
  }
);

export const fetchTrendingPayments = createAsyncThunk(
  'socialPayment/fetchTrending',
  async () => {
    const response = await socialPaymentService.getTrendingPayments();
    return response;
  }
);

export const fetchPaymentDetails = createAsyncThunk(
  'socialPayment/fetchDetails',
  async (paymentId: string) => {
    const response = await socialPaymentService.getPaymentDetails(paymentId);
    return response;
  }
);

export const fetchPaymentComments = createAsyncThunk(
  'socialPayment/fetchComments',
  async (paymentId: string) => {
    const response = await socialPaymentService.getPaymentComments(paymentId);
    return { paymentId, comments: response };
  }
);

export const addComment = createAsyncThunk(
  'socialPayment/addComment',
  async ({ paymentId, comment }: { paymentId: string; comment: string }) => {
    const response = await socialPaymentService.addComment(paymentId, comment);
    return { paymentId, comment: response };
  }
);

export const deleteComment = createAsyncThunk(
  'socialPayment/deleteComment',
  async ({ paymentId, commentId }: { paymentId: string; commentId: string }) => {
    await socialPaymentService.deleteComment(paymentId, commentId);
    return { paymentId, commentId };
  }
);

export const addReaction = createAsyncThunk(
  'socialPayment/addReaction',
  async ({ paymentId, reactionType }: { paymentId: string; reactionType: string }) => {
    const response = await socialPaymentService.addReaction(paymentId, reactionType);
    return { paymentId, reaction: response };
  }
);

export const removeReaction = createAsyncThunk(
  'socialPayment/removeReaction',
  async (paymentId: string) => {
    await socialPaymentService.removeReaction(paymentId);
    return paymentId;
  }
);

export const updatePaymentVisibility = createAsyncThunk(
  'socialPayment/updateVisibility',
  async ({ paymentId, visibility }: { paymentId: string; visibility: PaymentVisibility }) => {
    const response = await socialPaymentService.updateVisibility(paymentId, visibility);
    return response;
  }
);

export const reportPayment = createAsyncThunk(
  'socialPayment/report',
  async ({ paymentId, reason }: { paymentId: string; reason: string }) => {
    await socialPaymentService.reportPayment(paymentId, reason);
    return paymentId;
  }
);

const socialPaymentSlice = createSlice({
  name: 'socialPayment',
  initialState,
  reducers: {
    setFeedFilter: (state, action: PayloadAction<'all' | 'friends' | 'following'>) => {
      state.feedFilter = action.payload;
      state.currentPage = 1;
      state.feedItems = [];
    },
    clearFeed: (state) => {
      state.feedItems = [];
      state.currentPage = 1;
      state.hasMore = true;
    },
    updateFeedItem: (state, action: PayloadAction<PaymentFeedItem>) => {
      const index = state.feedItems.findIndex(item => item.id === action.payload.id);
      if (index !== -1) {
        state.feedItems[index] = action.payload;
      }
    },
    clearError: (state) => {
      state.error = null;
    },
  },
  extraReducers: (builder) => {
    builder
      // Fetch payment feed
      .addCase(fetchPaymentFeed.pending, (state) => {
        state.feedLoading = true;
        state.error = null;
      })
      .addCase(fetchPaymentFeed.fulfilled, (state, action) => {
        state.feedLoading = false;
        
        if (action.payload.page === 1) {
          state.feedItems = action.payload.items;
        } else {
          state.feedItems.push(...action.payload.items);
        }
        
        state.currentPage = action.payload.page;
        state.hasMore = action.payload.hasMore;
      })
      .addCase(fetchPaymentFeed.rejected, (state, action) => {
        state.feedLoading = false;
        state.error = action.error.message || 'Failed to fetch payment feed';
      })
      // Fetch public feed
      .addCase(fetchPublicFeed.fulfilled, (state, action) => {
        state.publicFeed = action.payload.items;
      })
      // Fetch trending payments
      .addCase(fetchTrendingPayments.fulfilled, (state, action) => {
        state.trendingPayments = action.payload;
      })
      // Fetch payment details
      .addCase(fetchPaymentDetails.pending, (state) => {
        state.loading = true;
      })
      .addCase(fetchPaymentDetails.fulfilled, (state, action) => {
        state.loading = false;
        state.selectedPayment = action.payload;
      })
      .addCase(fetchPaymentDetails.rejected, (state, action) => {
        state.loading = false;
        state.error = action.error.message || 'Failed to fetch payment details';
      })
      // Fetch comments
      .addCase(fetchPaymentComments.pending, (state) => {
        state.commentsLoading = true;
      })
      .addCase(fetchPaymentComments.fulfilled, (state, action) => {
        state.commentsLoading = false;
        state.comments[action.payload.paymentId] = action.payload.comments;
      })
      .addCase(fetchPaymentComments.rejected, (state) => {
        state.commentsLoading = false;
      })
      // Add comment
      .addCase(addComment.fulfilled, (state, action) => {
        const { paymentId, comment } = action.payload;
        if (!state.comments[paymentId]) {
          state.comments[paymentId] = [];
        }
        state.comments[paymentId].push(comment);
        
        // Update comment count in feed
        const feedItem = state.feedItems.find(item => item.id === paymentId);
        if (feedItem) {
          feedItem.commentCount += 1;
        }
      })
      // Delete comment
      .addCase(deleteComment.fulfilled, (state, action) => {
        const { paymentId, commentId } = action.payload;
        if (state.comments[paymentId]) {
          state.comments[paymentId] = state.comments[paymentId].filter(
            comment => comment.id !== commentId
          );
        }
        
        // Update comment count in feed
        const feedItem = state.feedItems.find(item => item.id === paymentId);
        if (feedItem && feedItem.commentCount > 0) {
          feedItem.commentCount -= 1;
        }
      })
      // Add reaction
      .addCase(addReaction.fulfilled, (state, action) => {
        const { paymentId, reaction } = action.payload;
        
        // Update user's reaction
        state.userReactions[paymentId] = reaction.type;
        
        // Update reactions list
        if (!state.reactions[paymentId]) {
          state.reactions[paymentId] = [];
        }
        state.reactions[paymentId].push(reaction);
        
        // Update reaction count in feed
        const feedItem = state.feedItems.find(item => item.id === paymentId);
        if (feedItem) {
          feedItem.reactionCount += 1;
        }
      })
      // Remove reaction
      .addCase(removeReaction.fulfilled, (state, action) => {
        const paymentId = action.payload;
        
        // Remove user's reaction
        delete state.userReactions[paymentId];
        
        // Update reaction count in feed
        const feedItem = state.feedItems.find(item => item.id === paymentId);
        if (feedItem && feedItem.reactionCount > 0) {
          feedItem.reactionCount -= 1;
        }
      })
      // Update visibility
      .addCase(updatePaymentVisibility.fulfilled, (state, action) => {
        const updatedPayment = action.payload;
        
        // Update in feed
        const feedIndex = state.feedItems.findIndex(item => item.id === updatedPayment.id);
        if (feedIndex !== -1) {
          state.feedItems[feedIndex].visibility = updatedPayment.visibility;
        }
        
        // Update selected payment if applicable
        if (state.selectedPayment?.id === updatedPayment.id) {
          state.selectedPayment.visibility = updatedPayment.visibility;
        }
      });
  },
});

export const {
  setFeedFilter,
  clearFeed,
  updateFeedItem,
  clearError,
} = socialPaymentSlice.actions;

export default socialPaymentSlice.reducer;