import React, { useState, useEffect, useRef } from 'react';
import {
  View,
  Text,
  StyleSheet,
  FlatList,
  TextInput,
  TouchableOpacity,
  Alert,
  KeyboardAvoidingView,
  Platform,
  Image,
} from 'react-native';
import { SafeAreaView } from 'react-native-safe-area-context';
import { useNavigation } from '@react-navigation/native';
import Icon from 'react-native-vector-icons/MaterialIcons';
import { useTheme } from '../../contexts/ThemeContext';
import { useAuth } from '../../contexts/AuthContext';
import { supportService } from '../../services/supportService';
import { formatRelativeTime } from '../../utils/formatters';

interface ChatMessage {
  id: string;
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

interface ChatSession {
  id: string;
  status: 'connecting' | 'active' | 'ended';
  queuePosition?: number;
  estimatedWaitTime?: number;
  agentInfo?: {
    name: string;
    avatar?: string;
    title: string;
  };
}

const LiveChatScreen: React.FC = () => {
  const navigation = useNavigation();
  const { theme } = useTheme();
  const { user } = useAuth();
  const [messages, setMessages] = useState<ChatMessage[]>([]);
  const [inputMessage, setInputMessage] = useState('');
  const [chatSession, setChatSession] = useState<ChatSession | null>(null);
  const [isTyping, setIsTyping] = useState(false);
  const [connectionStatus, setConnectionStatus] = useState<'connecting' | 'connected' | 'disconnected'>('connecting');
  const flatListRef = useRef<FlatList>(null);

  useEffect(() => {
    initializeChat();
    return () => {
      if (chatSession) {
        supportService.endChatSession(chatSession.id);
      }
    };
  }, []);

  useEffect(() => {
    if (messages.length > 0) {
      scrollToBottom();
    }
  }, [messages]);

  const initializeChat = async () => {
    try {
      setConnectionStatus('connecting');
      const session = await supportService.startChatSession();
      setChatSession(session);
      setConnectionStatus('connected');
      
      // Add initial system message
      const welcomeMessage: ChatMessage = {
        id: 'welcome',
        type: 'system',
        message: 'Welcome to Waqiti Support! An agent will be with you shortly.',
        timestamp: new Date().toISOString(),
      };
      setMessages([welcomeMessage]);

      // Set up real-time message listening
      supportService.onMessageReceived((message: ChatMessage) => {
        setMessages(prev => [...prev, message]);
        setIsTyping(false);
      });

      supportService.onAgentTyping((typing: boolean) => {
        setIsTyping(typing);
      });

      supportService.onSessionStatusChange((status: string) => {
        setChatSession(prev => prev ? { ...prev, status: status as any } : null);
      });

    } catch (error) {
      console.error('Failed to initialize chat:', error);
      setConnectionStatus('disconnected');
      Alert.alert(
        'Connection Error',
        'Unable to connect to support chat. Please try again later.',
        [
          { text: 'Retry', onPress: initializeChat },
          { text: 'Cancel', onPress: () => navigation.goBack() },
        ]
      );
    }
  };

  const sendMessage = async () => {
    if (!inputMessage.trim() || !chatSession) return;

    const message: ChatMessage = {
      id: Date.now().toString(),
      type: 'user',
      message: inputMessage.trim(),
      timestamp: new Date().toISOString(),
    };

    setMessages(prev => [...prev, message]);
    setInputMessage('');

    try {
      await supportService.sendChatMessage(chatSession.id, message.message);
    } catch (error) {
      console.error('Failed to send message:', error);
      Alert.alert('Error', 'Failed to send message. Please try again.');
    }
  };

  const endChat = () => {
    Alert.alert(
      'End Chat',
      'Are you sure you want to end this chat session?',
      [
        { text: 'Cancel', style: 'cancel' },
        {
          text: 'End Chat',
          style: 'destructive',
          onPress: async () => {
            if (chatSession) {
              await supportService.endChatSession(chatSession.id);
            }
            navigation.goBack();
          },
        },
      ]
    );
  };

  const scrollToBottom = () => {
    flatListRef.current?.scrollToEnd({ animated: true });
  };

  const renderConnectionStatus = () => {
    if (connectionStatus === 'connecting') {
      return (
        <View style={[styles.statusBar, { backgroundColor: '#FF9800' }]}>
          <Icon name="wifi-tethering" size={16} color="#FFFFFF" />
          <Text style={styles.statusText}>Connecting to support...</Text>
        </View>
      );
    }

    if (chatSession?.status === 'connecting' && chatSession.queuePosition) {
      return (
        <View style={[styles.statusBar, { backgroundColor: '#2196F3' }]}>
          <Icon name="queue" size={16} color="#FFFFFF" />
          <Text style={styles.statusText}>
            Position in queue: {chatSession.queuePosition}
            {chatSession.estimatedWaitTime && ` • ~${chatSession.estimatedWaitTime} min wait`}
          </Text>
        </View>
      );
    }

    if (chatSession?.status === 'active' && chatSession.agentInfo) {
      return (
        <View style={[styles.statusBar, { backgroundColor: '#4CAF50' }]}>
          <Icon name="person" size={16} color="#FFFFFF" />
          <Text style={styles.statusText}>
            Connected to {chatSession.agentInfo.name} • {chatSession.agentInfo.title}
          </Text>
        </View>
      );
    }

    return null;
  };

  const renderMessage = ({ item }: { item: ChatMessage }) => {
    const isUser = item.type === 'user';
    const isSystem = item.type === 'system';

    if (isSystem) {
      return (
        <View style={styles.systemMessageContainer}>
          <Text style={[styles.systemMessage, { color: theme.colors.textSecondary }]}>
            {item.message}
          </Text>
        </View>
      );
    }

    return (
      <View style={[
        styles.messageContainer,
        isUser ? styles.userMessageContainer : styles.agentMessageContainer,
      ]}>
        {!isUser && item.agentInfo?.avatar && (
          <Image source={{ uri: item.agentInfo.avatar }} style={styles.agentAvatar} />
        )}
        
        <View style={[
          styles.messageBubble,
          {
            backgroundColor: isUser ? theme.colors.primary : theme.colors.surface,
            alignSelf: isUser ? 'flex-end' : 'flex-start',
          },
        ]}>
          {!isUser && item.agentInfo && (
            <Text style={[styles.agentName, { color: theme.colors.textSecondary }]}>
              {item.agentInfo.name}
            </Text>
          )}
          
          <Text style={[
            styles.messageText,
            { color: isUser ? '#FFFFFF' : theme.colors.text },
          ]}>
            {item.message}
          </Text>
          
          <Text style={[
            styles.messageTime,
            { color: isUser ? 'rgba(255,255,255,0.7)' : theme.colors.textSecondary },
          ]}>
            {formatRelativeTime(new Date(item.timestamp))}
          </Text>
        </View>
      </View>
    );
  };

  const renderTypingIndicator = () => {
    if (!isTyping) return null;

    return (
      <View style={styles.typingContainer}>
        <View style={[styles.typingBubble, { backgroundColor: theme.colors.surface }]}>
          <View style={styles.typingDots}>
            <View style={[styles.typingDot, { backgroundColor: theme.colors.textSecondary }]} />
            <View style={[styles.typingDot, { backgroundColor: theme.colors.textSecondary }]} />
            <View style={[styles.typingDot, { backgroundColor: theme.colors.textSecondary }]} />
          </View>
        </View>
      </View>
    );
  };

  const renderChatInput = () => (
    <View style={[styles.inputContainer, { backgroundColor: theme.colors.surface }]}>
      <TouchableOpacity style={styles.attachButton}>
        <Icon name="attach-file" size={24} color={theme.colors.textSecondary} />
      </TouchableOpacity>
      
      <TextInput
        style={[styles.textInput, { color: theme.colors.text }]}
        placeholder="Type your message..."
        placeholderTextColor={theme.colors.textSecondary}
        value={inputMessage}
        onChangeText={setInputMessage}
        multiline
        maxLength={1000}
      />
      
      <TouchableOpacity
        style={[
          styles.sendButton,
          {
            backgroundColor: inputMessage.trim() ? theme.colors.primary : theme.colors.border,
          },
        ]}
        onPress={sendMessage}
        disabled={!inputMessage.trim()}
      >
        <Icon
          name="send"
          size={20}
          color={inputMessage.trim() ? '#FFFFFF' : theme.colors.textSecondary}
        />
      </TouchableOpacity>
    </View>
  );

  return (
    <SafeAreaView style={[styles.container, { backgroundColor: theme.colors.background }]}>
      <View style={styles.header}>
        <TouchableOpacity onPress={() => navigation.goBack()} style={styles.backButton}>
          <Icon name="arrow-back" size={24} color={theme.colors.text} />
        </TouchableOpacity>
        
        <Text style={[styles.headerTitle, { color: theme.colors.text }]}>
          Live Chat Support
        </Text>
        
        <TouchableOpacity onPress={endChat} style={styles.endChatButton}>
          <Text style={[styles.endChatText, { color: '#F44336' }]}>End</Text>
        </TouchableOpacity>
      </View>

      {renderConnectionStatus()}

      <KeyboardAvoidingView
        style={styles.chatContainer}
        behavior={Platform.OS === 'ios' ? 'padding' : 'height'}
        keyboardVerticalOffset={Platform.OS === 'ios' ? 90 : 0}
      >
        <FlatList
          ref={flatListRef}
          data={messages}
          renderItem={renderMessage}
          keyExtractor={item => item.id}
          style={styles.messagesList}
          contentContainerStyle={styles.messagesContent}
          showsVerticalScrollIndicator={false}
          onContentSizeChange={scrollToBottom}
        />
        
        {renderTypingIndicator()}
        {renderChatInput()}
      </KeyboardAvoidingView>

      {connectionStatus === 'disconnected' && (
        <View style={styles.disconnectedContainer}>
          <Icon name="wifi-off" size={48} color={theme.colors.textSecondary} />
          <Text style={[styles.disconnectedText, { color: theme.colors.text }]}>
            Connection Lost
          </Text>
          <TouchableOpacity
            style={[styles.reconnectButton, { backgroundColor: theme.colors.primary }]}
            onPress={initializeChat}
          >
            <Text style={styles.reconnectButtonText}>Reconnect</Text>
          </TouchableOpacity>
        </View>
      )}
    </SafeAreaView>
  );
};

const styles = StyleSheet.create({
  container: {
    flex: 1,
  },
  header: {
    flexDirection: 'row',
    alignItems: 'center',
    justifyContent: 'space-between',
    paddingHorizontal: 16,
    paddingVertical: 12,
  },
  backButton: {
    padding: 8,
  },
  headerTitle: {
    fontSize: 18,
    fontWeight: '600',
    flex: 1,
    textAlign: 'center',
    marginHorizontal: 16,
  },
  endChatButton: {
    padding: 8,
  },
  endChatText: {
    fontSize: 16,
    fontWeight: '500',
  },
  statusBar: {
    flexDirection: 'row',
    alignItems: 'center',
    justifyContent: 'center',
    paddingVertical: 8,
    paddingHorizontal: 16,
  },
  statusText: {
    color: '#FFFFFF',
    fontSize: 12,
    fontWeight: '500',
    marginLeft: 4,
  },
  chatContainer: {
    flex: 1,
  },
  messagesList: {
    flex: 1,
  },
  messagesContent: {
    paddingVertical: 16,
  },
  messageContainer: {
    flexDirection: 'row',
    marginBottom: 12,
    paddingHorizontal: 16,
  },
  userMessageContainer: {
    justifyContent: 'flex-end',
  },
  agentMessageContainer: {
    justifyContent: 'flex-start',
  },
  agentAvatar: {
    width: 32,
    height: 32,
    borderRadius: 16,
    marginRight: 8,
    alignSelf: 'flex-end',
  },
  messageBubble: {
    maxWidth: '80%',
    paddingHorizontal: 12,
    paddingVertical: 8,
    borderRadius: 16,
  },
  agentName: {
    fontSize: 12,
    fontWeight: '500',
    marginBottom: 2,
  },
  messageText: {
    fontSize: 16,
    lineHeight: 20,
  },
  messageTime: {
    fontSize: 10,
    marginTop: 4,
    alignSelf: 'flex-end',
  },
  systemMessageContainer: {
    alignItems: 'center',
    marginVertical: 8,
    paddingHorizontal: 16,
  },
  systemMessage: {
    fontSize: 12,
    textAlign: 'center',
    fontStyle: 'italic',
  },
  typingContainer: {
    paddingHorizontal: 16,
    marginBottom: 8,
  },
  typingBubble: {
    alignSelf: 'flex-start',
    paddingHorizontal: 16,
    paddingVertical: 12,
    borderRadius: 16,
    maxWidth: '80%',
  },
  typingDots: {
    flexDirection: 'row',
    alignItems: 'center',
  },
  typingDot: {
    width: 6,
    height: 6,
    borderRadius: 3,
    marginHorizontal: 2,
  },
  inputContainer: {
    flexDirection: 'row',
    alignItems: 'flex-end',
    paddingHorizontal: 16,
    paddingVertical: 12,
    borderTopWidth: 1,
    borderTopColor: 'rgba(0,0,0,0.1)',
  },
  attachButton: {
    padding: 8,
    marginRight: 8,
  },
  textInput: {
    flex: 1,
    fontSize: 16,
    maxHeight: 100,
    paddingVertical: 8,
    paddingHorizontal: 12,
    textAlignVertical: 'top',
  },
  sendButton: {
    width: 40,
    height: 40,
    borderRadius: 20,
    justifyContent: 'center',
    alignItems: 'center',
    marginLeft: 8,
  },
  disconnectedContainer: {
    position: 'absolute',
    top: 0,
    left: 0,
    right: 0,
    bottom: 0,
    justifyContent: 'center',
    alignItems: 'center',
    backgroundColor: 'rgba(0,0,0,0.8)',
  },
  disconnectedText: {
    fontSize: 18,
    fontWeight: '600',
    marginTop: 16,
    marginBottom: 24,
  },
  reconnectButton: {
    paddingHorizontal: 24,
    paddingVertical: 12,
    borderRadius: 8,
  },
  reconnectButtonText: {
    color: '#FFFFFF',
    fontSize: 16,
    fontWeight: '500',
  },
});

export default LiveChatScreen;