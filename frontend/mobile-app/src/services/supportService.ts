import { apiClient } from './apiClient';
import { EventEmitter } from 'events';

export interface FAQItem {
  id: string;
  category: string;
  question: string;
  answer: string;
  tags: string[];
  helpfulCount: number;
  lastUpdated: string;
}

export interface SupportTicket {
  id: string;
  userId: string;
  subject: string;
  description: string;
  category: string;
  priority: 'low' | 'medium' | 'high' | 'urgent';
  status: 'open' | 'in_progress' | 'waiting_for_customer' | 'resolved' | 'closed';
  assignedAgent?: {
    id: string;
    name: string;
    email: string;
  };
  createdAt: string;
  updatedAt: string;
  tags: string[];
  attachments: Array<{
    id: string;
    filename: string;
    url: string;
    contentType: string;
    size: number;
  }>;
}

export interface ChatSession {
  id: string;
  userId: string;
  status: 'connecting' | 'active' | 'ended';
  queuePosition?: number;
  estimatedWaitTime?: number;
  agentInfo?: {
    id: string;
    name: string;
    avatar?: string;
    title: string;
  };
  startedAt: string;
  endedAt?: string;
}

export interface ChatMessage {
  id: string;
  sessionId: string;
  type: 'user' | 'agent' | 'system';
  message: string;
  timestamp: string;
  agentInfo?: {
    name: string;
    avatar?: string;
  };
  attachments?: Array<{
    type: 'image' | 'file';
    url: string;
    name: string;
  }>;
}

export interface FeedbackSubmission {
  category: string;
  subject: string;
  message: string;
  rating?: number;
  attachments?: File[];
}

export interface IssueReport {
  category: string;
  title: string;
  description: string;
  steps: string;
  expectedResult: string;
  actualResult: string;
  deviceInfo: {
    platform: string;
    version: string;
    model: string;
  };
  appVersion: string;
  attachments?: File[];
}

class SupportService extends EventEmitter {
  private chatWebSocket: WebSocket | null = null;
  private currentChatSession: ChatSession | null = null;

  // FAQ Methods
  async getFAQs(category?: string): Promise<{ faqs: FAQItem[] }> {
    const params = category ? { category } : {};
    const response = await apiClient.get('/support/faqs', { params });
    return response.data;
  }

  async searchFAQs(query: string): Promise<{ faqs: FAQItem[] }> {
    const response = await apiClient.get('/support/faqs/search', {
      params: { q: query },
    });
    return response.data;
  }

  async markFAQHelpful(faqId: string): Promise<void> {
    await apiClient.post(`/support/faqs/${faqId}/helpful`);
  }

  // Support Ticket Methods
  async createSupportTicket(ticket: {
    subject: string;
    description: string;
    category: string;
    priority: string;
    attachments?: File[];
  }): Promise<{ ticket: SupportTicket }> {
    const formData = new FormData();
    formData.append('subject', ticket.subject);
    formData.append('description', ticket.description);
    formData.append('category', ticket.category);
    formData.append('priority', ticket.priority);

    if (ticket.attachments) {
      ticket.attachments.forEach((file, index) => {
        formData.append(`attachments[${index}]`, file);
      });
    }

    const response = await apiClient.post('/support/tickets', formData, {
      headers: {
        'Content-Type': 'multipart/form-data',
      },
    });
    return response.data;
  }

  async getSupportTickets(): Promise<{ tickets: SupportTicket[] }> {
    const response = await apiClient.get('/support/tickets');
    return response.data;
  }

  async getSupportTicket(ticketId: string): Promise<{ ticket: SupportTicket }> {
    const response = await apiClient.get(`/support/tickets/${ticketId}`);
    return response.data;
  }

  async updateSupportTicket(
    ticketId: string,
    update: {
      description?: string;
      priority?: string;
      status?: string;
    }
  ): Promise<{ ticket: SupportTicket }> {
    const response = await apiClient.patch(`/support/tickets/${ticketId}`, update);
    return response.data;
  }

  async addTicketComment(
    ticketId: string,
    comment: {
      message: string;
      attachments?: File[];
    }
  ): Promise<void> {
    const formData = new FormData();
    formData.append('message', comment.message);

    if (comment.attachments) {
      comment.attachments.forEach((file, index) => {
        formData.append(`attachments[${index}]`, file);
      });
    }

    await apiClient.post(`/support/tickets/${ticketId}/comments`, formData, {
      headers: {
        'Content-Type': 'multipart/form-data',
      },
    });
  }

  async getTicketComments(ticketId: string): Promise<{ comments: any[] }> {
    const response = await apiClient.get(`/support/tickets/${ticketId}/comments`);
    return response.data;
  }

  // Live Chat Methods
  async startChatSession(): Promise<ChatSession> {
    const response = await apiClient.post('/support/chat/start');
    const session = response.data.session;
    this.currentChatSession = session;
    this.connectChatWebSocket(session.id);
    return session;
  }

  async endChatSession(sessionId: string): Promise<void> {
    await apiClient.post(`/support/chat/${sessionId}/end`);
    this.disconnectChatWebSocket();
    this.currentChatSession = null;
  }

  async sendChatMessage(sessionId: string, message: string): Promise<void> {
    if (this.chatWebSocket && this.chatWebSocket.readyState === WebSocket.OPEN) {
      this.chatWebSocket.send(JSON.stringify({
        type: 'message',
        sessionId,
        message,
      }));
    } else {
      // Fallback to HTTP API
      await apiClient.post(`/support/chat/${sessionId}/messages`, { message });
    }
  }

  async getChatHistory(sessionId: string): Promise<{ messages: ChatMessage[] }> {
    const response = await apiClient.get(`/support/chat/${sessionId}/messages`);
    return response.data;
  }

  private connectChatWebSocket(sessionId: string) {
    const wsUrl = process.env.REACT_APP_WS_URL || 'ws://localhost:8080';
    this.chatWebSocket = new WebSocket(`${wsUrl}/support/chat/${sessionId}`);

    this.chatWebSocket.onopen = () => {
      console.log('Chat WebSocket connected');
    };

    this.chatWebSocket.onmessage = (event) => {
      const data = JSON.parse(event.data);
      this.handleWebSocketMessage(data);
    };

    this.chatWebSocket.onclose = () => {
      console.log('Chat WebSocket disconnected');
      // Attempt to reconnect after 3 seconds
      setTimeout(() => {
        if (this.currentChatSession) {
          this.connectChatWebSocket(this.currentChatSession.id);
        }
      }, 3000);
    };

    this.chatWebSocket.onerror = (error) => {
      console.error('Chat WebSocket error:', error);
    };
  }

  private disconnectChatWebSocket() {
    if (this.chatWebSocket) {
      this.chatWebSocket.close();
      this.chatWebSocket = null;
    }
  }

  private handleWebSocketMessage(data: any) {
    switch (data.type) {
      case 'message':
        this.emit('messageReceived', data.message);
        break;
      case 'agent_typing':
        this.emit('agentTyping', data.typing);
        break;
      case 'session_status':
        this.emit('sessionStatusChange', data.status);
        break;
      case 'queue_update':
        this.emit('queueUpdate', data.queueInfo);
        break;
      default:
        console.log('Unknown WebSocket message type:', data.type);
    }
  }

  // Event Listeners for Chat
  onMessageReceived(callback: (message: ChatMessage) => void) {
    this.on('messageReceived', callback);
  }

  onAgentTyping(callback: (typing: boolean) => void) {
    this.on('agentTyping', callback);
  }

  onSessionStatusChange(callback: (status: string) => void) {
    this.on('sessionStatusChange', callback);
  }

  onQueueUpdate(callback: (queueInfo: any) => void) {
    this.on('queueUpdate', callback);
  }

  // Feedback Methods
  async submitFeedback(feedback: FeedbackSubmission): Promise<void> {
    const formData = new FormData();
    formData.append('category', feedback.category);
    formData.append('subject', feedback.subject);
    formData.append('message', feedback.message);
    
    if (feedback.rating) {
      formData.append('rating', feedback.rating.toString());
    }

    if (feedback.attachments) {
      feedback.attachments.forEach((file, index) => {
        formData.append(`attachments[${index}]`, file);
      });
    }

    await apiClient.post('/support/feedback', formData, {
      headers: {
        'Content-Type': 'multipart/form-data',
      },
    });
  }

  // Issue Reporting
  async reportIssue(issue: IssueReport): Promise<{ ticket: SupportTicket }> {
    const formData = new FormData();
    formData.append('category', issue.category);
    formData.append('title', issue.title);
    formData.append('description', issue.description);
    formData.append('steps', issue.steps);
    formData.append('expectedResult', issue.expectedResult);
    formData.append('actualResult', issue.actualResult);
    formData.append('deviceInfo', JSON.stringify(issue.deviceInfo));
    formData.append('appVersion', issue.appVersion);

    if (issue.attachments) {
      issue.attachments.forEach((file, index) => {
        formData.append(`attachments[${index}]`, file);
      });
    }

    const response = await apiClient.post('/support/issues', formData, {
      headers: {
        'Content-Type': 'multipart/form-data',
      },
    });
    return response.data;
  }

  // Knowledge Base
  async getKnowledgeBaseArticles(category?: string): Promise<{ articles: any[] }> {
    const params = category ? { category } : {};
    const response = await apiClient.get('/support/knowledge-base', { params });
    return response.data;
  }

  async getKnowledgeBaseArticle(articleId: string): Promise<{ article: any }> {
    const response = await apiClient.get(`/support/knowledge-base/${articleId}`);
    return response.data;
  }

  async searchKnowledgeBase(query: string): Promise<{ articles: any[] }> {
    const response = await apiClient.get('/support/knowledge-base/search', {
      params: { q: query },
    });
    return response.data;
  }

  // Analytics
  async trackSupportInteraction(interaction: {
    type: 'faq_view' | 'faq_helpful' | 'ticket_created' | 'chat_started' | 'feedback_submitted';
    metadata?: any;
  }): Promise<void> {
    try {
      await apiClient.post('/support/analytics', {
        ...interaction,
        timestamp: new Date().toISOString(),
      });
    } catch (error) {
      console.error('Failed to track support interaction:', error);
    }
  }

  // Contact Information
  async getContactInformation(): Promise<{
    phone: string;
    email: string;
    hours: string;
    chatAvailable: boolean;
  }> {
    const response = await apiClient.get('/support/contact-info');
    return response.data;
  }

  // System Status
  async getSystemStatus(): Promise<{
    status: 'operational' | 'degraded' | 'maintenance' | 'outage';
    incidents: Array<{
      id: string;
      title: string;
      description: string;
      status: string;
      createdAt: string;
      updatedAt: string;
    }>;
  }> {
    const response = await apiClient.get('/support/system-status');
    return response.data;
  }

  // Utility Methods
  async uploadAttachment(file: File): Promise<{ url: string; id: string }> {
    const formData = new FormData();
    formData.append('file', file);

    const response = await apiClient.post('/support/attachments', formData, {
      headers: {
        'Content-Type': 'multipart/form-data',
      },
    });
    return response.data;
  }

  async deleteAttachment(attachmentId: string): Promise<void> {
    await apiClient.delete(`/support/attachments/${attachmentId}`);
  }

  // Device Information Helper
  getDeviceInfo() {
    return {
      platform: navigator.platform || 'Unknown',
      userAgent: navigator.userAgent,
      language: navigator.language,
      cookieEnabled: navigator.cookieEnabled,
      onLine: navigator.onLine,
      screen: {
        width: screen.width,
        height: screen.height,
        colorDepth: screen.colorDepth,
      },
      viewport: {
        width: window.innerWidth,
        height: window.innerHeight,
      },
    };
  }
}

export const supportService = new SupportService();
export default supportService;