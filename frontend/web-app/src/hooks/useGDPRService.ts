import { useState, useCallback, useEffect } from 'react';
import { useAppDispatch, useAppSelector } from '../store/hooks';
import { gdprService } from '../services/gdprService';
import {
  ConsentRecord,
  DataSubjectRequest,
  GrantConsentDTO,
  CreateRequestDTO,
  UpdateConsentPreferencesDTO,
  ConsentHistory,
  ExportFormat,
  ConsentPurpose,
} from '../types/gdpr';
import { useSnackbar } from 'notistack';

interface GDPRState {
  consents: ConsentRecord[];
  requests: DataSubjectRequest[];
  consentHistory: ConsentHistory | null;
  isLoading: boolean;
  error: Error | null;
}

export const useGDPRService = () => {
  const dispatch = useAppDispatch();
  const { enqueueSnackbar } = useSnackbar();
  
  const [state, setState] = useState<GDPRState>({
    consents: [],
    requests: [],
    consentHistory: null,
    isLoading: false,
    error: null,
  });

  // Load user consents
  const loadConsents = useCallback(async () => {
    setState(prev => ({ ...prev, isLoading: true, error: null }));
    try {
      const consents = await gdprService.getUserConsents();
      setState(prev => ({ ...prev, consents, isLoading: false }));
    } catch (error) {
      setState(prev => ({ ...prev, error: error as Error, isLoading: false }));
      enqueueSnackbar('Failed to load privacy preferences', { variant: 'error' });
    }
  }, [enqueueSnackbar]);

  // Load data requests
  const loadRequests = useCallback(async () => {
    setState(prev => ({ ...prev, isLoading: true, error: null }));
    try {
      const requests = await gdprService.getUserRequests();
      setState(prev => ({ ...prev, requests, isLoading: false }));
    } catch (error) {
      setState(prev => ({ ...prev, error: error as Error, isLoading: false }));
      enqueueSnackbar('Failed to load data requests', { variant: 'error' });
    }
  }, [enqueueSnackbar]);

  // Grant consent
  const grantConsent = useCallback(async (consent: GrantConsentDTO) => {
    setState(prev => ({ ...prev, isLoading: true, error: null }));
    try {
      const newConsent = await gdprService.grantConsent(consent);
      setState(prev => ({
        ...prev,
        consents: [...prev.consents, newConsent],
        isLoading: false,
      }));
      enqueueSnackbar('Consent granted successfully', { variant: 'success' });
      return newConsent;
    } catch (error) {
      setState(prev => ({ ...prev, error: error as Error, isLoading: false }));
      enqueueSnackbar('Failed to grant consent', { variant: 'error' });
      throw error;
    }
  }, [enqueueSnackbar]);

  // Withdraw consent
  const withdrawConsent = useCallback(async (purpose: ConsentPurpose, reason?: string) => {
    setState(prev => ({ ...prev, isLoading: true, error: null }));
    try {
      const updatedConsent = await gdprService.withdrawConsent(purpose, reason);
      setState(prev => ({
        ...prev,
        consents: prev.consents.map(c => 
          c.purpose === purpose ? updatedConsent : c
        ),
        isLoading: false,
      }));
      enqueueSnackbar('Consent withdrawn successfully', { variant: 'success' });
      return updatedConsent;
    } catch (error) {
      setState(prev => ({ ...prev, error: error as Error, isLoading: false }));
      enqueueSnackbar('Failed to withdraw consent', { variant: 'error' });
      throw error;
    }
  }, [enqueueSnackbar]);

  // Update consent preferences
  const updateConsentPreferences = useCallback(async (preferences: UpdateConsentPreferencesDTO) => {
    setState(prev => ({ ...prev, isLoading: true, error: null }));
    try {
      await gdprService.updateConsentPreferences(preferences);
      await loadConsents(); // Reload consents after update
      enqueueSnackbar('Privacy preferences updated', { variant: 'success' });
    } catch (error) {
      setState(prev => ({ ...prev, error: error as Error, isLoading: false }));
      enqueueSnackbar('Failed to update preferences', { variant: 'error' });
      throw error;
    }
  }, [enqueueSnackbar, loadConsents]);

  // Create data request
  const createDataRequest = useCallback(async (request: CreateRequestDTO) => {
    setState(prev => ({ ...prev, isLoading: true, error: null }));
    try {
      const newRequest = await gdprService.createDataRequest(request);
      setState(prev => ({
        ...prev,
        requests: [...prev.requests, newRequest],
        isLoading: false,
      }));
      
      const messages: Record<string, string> = {
        ACCESS: 'Data access request submitted. Check your email for verification.',
        PORTABILITY: 'Data export request submitted. Check your email for verification.',
        ERASURE: 'Data deletion request submitted. Check your email for verification.',
        RECTIFICATION: 'Data correction request submitted. We will review and process it.',
        RESTRICTION: 'Processing restriction request submitted.',
        OBJECTION: 'Objection to processing submitted.',
      };
      
      enqueueSnackbar(messages[request.requestType] || 'Request submitted successfully', { 
        variant: 'success',
        persist: true,
      });
      
      return newRequest;
    } catch (error) {
      setState(prev => ({ ...prev, error: error as Error, isLoading: false }));
      enqueueSnackbar('Failed to submit request', { variant: 'error' });
      throw error;
    }
  }, [enqueueSnackbar]);

  // Export data
  const exportData = useCallback(async (format: ExportFormat, categories?: string[]) => {
    setState(prev => ({ ...prev, isLoading: true, error: null }));
    try {
      const exportResult = await gdprService.exportUserData(format, categories);
      setState(prev => ({ ...prev, isLoading: false }));
      
      enqueueSnackbar('Data export initiated. You will receive an email when ready.', { 
        variant: 'info',
        persist: true,
      });
      
      return exportResult;
    } catch (error) {
      setState(prev => ({ ...prev, error: error as Error, isLoading: false }));
      enqueueSnackbar('Failed to export data', { variant: 'error' });
      throw error;
    }
  }, [enqueueSnackbar]);

  // Download export
  const downloadExport = useCallback(async (exportId: string, filename?: string) => {
    setState(prev => ({ ...prev, isLoading: true, error: null }));
    try {
      await gdprService.downloadExportFile(exportId, filename);
      setState(prev => ({ ...prev, isLoading: false }));
      enqueueSnackbar('Download started', { variant: 'success' });
    } catch (error) {
      setState(prev => ({ ...prev, error: error as Error, isLoading: false }));
      enqueueSnackbar('Failed to download export', { variant: 'error' });
      throw error;
    }
  }, [enqueueSnackbar]);

  // Load consent history
  const loadConsentHistory = useCallback(async () => {
    setState(prev => ({ ...prev, isLoading: true, error: null }));
    try {
      const history = await gdprService.getConsentHistory();
      setState(prev => ({ ...prev, consentHistory: history, isLoading: false }));
    } catch (error) {
      setState(prev => ({ ...prev, error: error as Error, isLoading: false }));
      enqueueSnackbar('Failed to load consent history', { variant: 'error' });
    }
  }, [enqueueSnackbar]);

  // Cancel request
  const cancelRequest = useCallback(async (requestId: string) => {
    setState(prev => ({ ...prev, isLoading: true, error: null }));
    try {
      await gdprService.cancelRequest(requestId);
      setState(prev => ({
        ...prev,
        requests: prev.requests.filter(r => r.id !== requestId),
        isLoading: false,
      }));
      enqueueSnackbar('Request cancelled', { variant: 'success' });
    } catch (error) {
      setState(prev => ({ ...prev, error: error as Error, isLoading: false }));
      enqueueSnackbar('Failed to cancel request', { variant: 'error' });
      throw error;
    }
  }, [enqueueSnackbar]);

  // Withdraw all marketing consents
  const withdrawAllMarketing = useCallback(async () => {
    setState(prev => ({ ...prev, isLoading: true, error: null }));
    try {
      await gdprService.withdrawAllMarketingConsents();
      await loadConsents(); // Reload consents
      enqueueSnackbar('Unsubscribed from all marketing communications', { variant: 'success' });
    } catch (error) {
      setState(prev => ({ ...prev, error: error as Error, isLoading: false }));
      enqueueSnackbar('Failed to unsubscribe from marketing', { variant: 'error' });
      throw error;
    }
  }, [enqueueSnackbar, loadConsents]);

  return {
    ...state,
    loadConsents,
    loadRequests,
    grantConsent,
    withdrawConsent,
    updateConsentPreferences,
    createDataRequest,
    exportData,
    downloadExport,
    loadConsentHistory,
    cancelRequest,
    withdrawAllMarketing,
  };
};