import { createSlice, createAsyncThunk } from '@reduxjs/toolkit';
import { bnplService } from '@/services/bnplService';
import { BNPLState, BNPLPlan, CreateBNPLRequest } from '@/types/bnpl';

const initialState: BNPLState = {
  plans: [],
  selectedPlan: null,
  installments: [],
  loading: false,
  error: null,
};

export const fetchBNPLPlans = createAsyncThunk('bnpl/fetchPlans', async () => {
  return await bnplService.getPlans();
});

export const createBNPLPlan = createAsyncThunk('bnpl/create', async (request: CreateBNPLRequest) => {
  return await bnplService.createPlan(request);
});

export const fetchInstallments = createAsyncThunk('bnpl/fetchInstallments', async (planId: string) => {
  return await bnplService.getInstallments(planId);
});

export const payInstallment = createAsyncThunk(
  'bnpl/payInstallment',
  async ({ planId, installmentId }: { planId: string; installmentId: string }) => {
    await bnplService.payInstallment(planId, installmentId);
    return { planId, installmentId };
  }
);

const bnplSlice = createSlice({
  name: 'bnpl',
  initialState,
  reducers: {
    setSelectedPlan: (state, action) => {
      state.selectedPlan = action.payload;
    },
  },
  extraReducers: (builder) => {
    builder
      .addCase(fetchBNPLPlans.pending, (state) => {
        state.loading = true;
      })
      .addCase(fetchBNPLPlans.fulfilled, (state, action) => {
        state.loading = false;
        state.plans = action.payload;
      })
      .addCase(fetchBNPLPlans.rejected, (state, action) => {
        state.loading = false;
        state.error = action.error.message || 'Failed to fetch BNPL plans';
      })
      .addCase(createBNPLPlan.fulfilled, (state, action) => {
        state.plans.unshift(action.payload);
      })
      .addCase(fetchInstallments.fulfilled, (state, action) => {
        state.installments = action.payload;
      })
      .addCase(payInstallment.fulfilled, (state, action) => {
        const installment = state.installments.find((i) => i.id === action.payload.installmentId);
        if (installment) {
          installment.status = 'PAID';
          installment.paidDate = new Date().toISOString();
        }
      });
  },
});

export const { setSelectedPlan } = bnplSlice.actions;
export default bnplSlice.reducer;
