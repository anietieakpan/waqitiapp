import React, { useState, useEffect } from 'react';
import {
  View,
  Text,
  StyleSheet,
  Animated,
  TouchableOpacity,
  ScrollView
} from 'react-native';
import Icon from 'react-native-vector-icons/MaterialIcons';
import { VoiceCommand } from '../../services/voice/VoicePaymentService';

interface VoiceCommandDisplayProps {
  command: VoiceCommand | null;
  isListening: boolean;
  onExecute?: (command: VoiceCommand) => void;
  onCancel?: () => void;
  onEdit?: (command: VoiceCommand) => void;
}

const VoiceCommandDisplay: React.FC<VoiceCommandDisplayProps> = ({
  command,
  isListening,
  onExecute,
  onCancel,
  onEdit
}) => {
  const [fadeAnim] = useState(new Animated.Value(0));
  const [slideAnim] = useState(new Animated.Value(50));

  useEffect(() => {
    if (command) {
      // Animate in when command is recognized
      Animated.parallel([
        Animated.timing(fadeAnim, {
          toValue: 1,
          duration: 300,
          useNativeDriver: true,
        }),
        Animated.timing(slideAnim, {
          toValue: 0,
          duration: 300,
          useNativeDriver: true,
        }),
      ]).start();
    } else {
      // Animate out when command is cleared
      Animated.parallel([
        Animated.timing(fadeAnim, {
          toValue: 0,
          duration: 200,
          useNativeDriver: true,
        }),
        Animated.timing(slideAnim, {
          toValue: 50,
          duration: 200,
          useNativeDriver: true,
        }),
      ]).start();
    }
  }, [command]);

  const formatCommand = (cmd: VoiceCommand): string => {
    switch (cmd.action) {
      case 'send':
        return `Send $${cmd.amount} to ${cmd.recipient?.name}`;
      case 'request':
        return `Request $${cmd.amount} from ${cmd.recipient?.name}`;
      case 'check_balance':
        return 'Check account balance';
      case 'check_transactions':
        return 'Show recent transactions';
      case 'split_bill':
        return `Split $${cmd.amount} - ${cmd.description}`;
      default:
        return cmd.rawText;
    }
  };

  const getActionIcon = (action: string): string => {
    switch (action) {
      case 'send': return 'arrow-upward';
      case 'request': return 'arrow-downward';
      case 'check_balance': return 'account-balance';
      case 'check_transactions': return 'history';
      case 'split_bill': return 'group';
      default: return 'mic';
    }
  };

  const getConfidenceColor = (confidence: number): string => {
    if (confidence >= 0.8) return '#4CAF50'; // Green
    if (confidence >= 0.6) return '#FF9800'; // Orange
    return '#F44336'; // Red
  };

  const getConfidenceText = (confidence: number): string => {
    if (confidence >= 0.8) return 'High confidence';
    if (confidence >= 0.6) return 'Medium confidence';
    return 'Low confidence';
  };

  if (isListening && !command) {
    return (
      <View style={styles.listeningContainer}>
        <View style={styles.listeningIndicator}>
          <Icon name="mic" size={24} color="#667eea" />
          <Text style={styles.listeningText}>Listening for your command...</Text>
        </View>
        <View style={styles.waveform}>
          {[...Array(5)].map((_, index) => (
            <View key={index} style={styles.waveBar} />
          ))}
        </View>
        <ScrollView style={styles.examplesContainer}>
          <Text style={styles.examplesTitle}>Try saying:</Text>
          {[
            'Send $20 to John',
            'Request $30 from Sarah',
            'Check my balance',
            'Show recent transactions',
            'Split $50 with friends'
          ].map((example, index) => (
            <Text key={index} style={styles.exampleText}>• {example}</Text>
          ))}
        </ScrollView>
      </View>
    );
  }

  if (!command) {
    return null;
  }

  return (
    <Animated.View
      style={[
        styles.container,
        {
          opacity: fadeAnim,
          transform: [{ translateY: slideAnim }],
        },
      ]}
    >
      <View style={styles.commandCard}>
        {/* Header */}
        <View style={styles.commandHeader}>
          <View style={styles.actionInfo}>
            <Icon
              name={getActionIcon(command.action)}
              size={24}
              color="#667eea"
              style={styles.actionIcon}
            />
            <Text style={styles.actionTitle}>
              {command.action.replace('_', ' ').toUpperCase()}
            </Text>
          </View>
          <View style={styles.confidenceIndicator}>
            <View
              style={[
                styles.confidenceDot,
                { backgroundColor: getConfidenceColor(command.confidence) }
              ]}
            />
            <Text style={[
              styles.confidenceText,
              { color: getConfidenceColor(command.confidence) }
            ]}>
              {getConfidenceText(command.confidence)}
            </Text>
          </View>
        </View>

        {/* Command Details */}
        <View style={styles.commandDetails}>
          <Text style={styles.commandText}>{formatCommand(command)}</Text>
          
          {command.description && (
            <Text style={styles.descriptionText}>{command.description}</Text>
          )}

          {/* Raw Speech Display */}
          <View style={styles.rawTextContainer}>
            <Icon name="format-quote" size={16} color="#999" />
            <Text style={styles.rawText}>"{command.rawText}"</Text>
          </View>
        </View>

        {/* Action Buttons */}
        <View style={styles.actionButtons}>
          <TouchableOpacity
            style={[styles.actionButton, styles.cancelButton]}
            onPress={onCancel}
          >
            <Icon name="close" size={20} color="#F44336" />
            <Text style={styles.cancelButtonText}>Cancel</Text>
          </TouchableOpacity>

          {command.confidence < 0.8 && (
            <TouchableOpacity
              style={[styles.actionButton, styles.editButton]}
              onPress={() => onEdit?.(command)}
            >
              <Icon name="edit" size={20} color="#FF9800" />
              <Text style={styles.editButtonText}>Edit</Text>
            </TouchableOpacity>
          )}

          <TouchableOpacity
            style={[styles.actionButton, styles.executeButton]}
            onPress={() => onExecute?.(command)}
          >
            <Icon name="check" size={20} color="#FFFFFF" />
            <Text style={styles.executeButtonText}>Execute</Text>
          </TouchableOpacity>
        </View>

        {/* Confidence Warning */}
        {command.confidence < 0.7 && (
          <View style={styles.warningContainer}>
            <Icon name="warning" size={16} color="#FF9800" />
            <Text style={styles.warningText}>
              I'm not very confident about this command. Please review before executing.
            </Text>
          </View>
        )}
      </View>
    </Animated.View>
  );
};

const styles = StyleSheet.create({
  container: {
    padding: 16,
  },
  listeningContainer: {
    padding: 20,
    alignItems: 'center',
  },
  listeningIndicator: {
    flexDirection: 'row',
    alignItems: 'center',
    marginBottom: 16,
  },
  listeningText: {
    fontSize: 16,
    color: '#667eea',
    marginLeft: 8,
    fontWeight: '500',
  },
  waveform: {
    flexDirection: 'row',
    alignItems: 'center',
    height: 40,
    marginBottom: 20,
  },
  waveBar: {
    width: 4,
    height: 20,
    backgroundColor: '#667eea',
    marginHorizontal: 2,
    borderRadius: 2,
    // Animation would be added here in a real implementation
  },
  examplesContainer: {
    maxHeight: 120,
    width: '100%',
  },
  examplesTitle: {
    fontSize: 14,
    fontWeight: '600',
    color: '#333',
    marginBottom: 8,
  },
  exampleText: {
    fontSize: 13,
    color: '#666',
    marginBottom: 4,
    paddingLeft: 8,
  },
  commandCard: {
    backgroundColor: '#FFFFFF',
    borderRadius: 12,
    padding: 16,
    shadowColor: '#000',
    shadowOffset: {
      width: 0,
      height: 2,
    },
    shadowOpacity: 0.1,
    shadowRadius: 3.84,
    elevation: 5,
  },
  commandHeader: {
    flexDirection: 'row',
    justifyContent: 'space-between',
    alignItems: 'center',
    marginBottom: 12,
  },
  actionInfo: {
    flexDirection: 'row',
    alignItems: 'center',
  },
  actionIcon: {
    marginRight: 8,
  },
  actionTitle: {
    fontSize: 14,
    fontWeight: '600',
    color: '#333',
    letterSpacing: 0.5,
  },
  confidenceIndicator: {
    flexDirection: 'row',
    alignItems: 'center',
  },
  confidenceDot: {
    width: 8,
    height: 8,
    borderRadius: 4,
    marginRight: 6,
  },
  confidenceText: {
    fontSize: 12,
    fontWeight: '500',
  },
  commandDetails: {
    marginBottom: 16,
  },
  commandText: {
    fontSize: 18,
    fontWeight: '600',
    color: '#333',
    marginBottom: 8,
  },
  descriptionText: {
    fontSize: 14,
    color: '#666',
    marginBottom: 8,
  },
  rawTextContainer: {
    flexDirection: 'row',
    alignItems: 'center',
    backgroundColor: '#F8F9FA',
    padding: 8,
    borderRadius: 6,
    marginTop: 8,
  },
  rawText: {
    fontSize: 13,
    color: '#666',
    fontStyle: 'italic',
    marginLeft: 4,
    flex: 1,
  },
  actionButtons: {
    flexDirection: 'row',
    justifyContent: 'space-between',
    marginTop: 8,
  },
  actionButton: {
    flexDirection: 'row',
    alignItems: 'center',
    paddingHorizontal: 16,
    paddingVertical: 10,
    borderRadius: 8,
    flex: 1,
    justifyContent: 'center',
    marginHorizontal: 4,
  },
  cancelButton: {
    backgroundColor: '#FFEBEE',
    borderWidth: 1,
    borderColor: '#FFCDD2',
  },
  cancelButtonText: {
    color: '#F44336',
    fontSize: 14,
    fontWeight: '600',
    marginLeft: 4,
  },
  editButton: {
    backgroundColor: '#FFF8E1',
    borderWidth: 1,
    borderColor: '#FFECB3',
  },
  editButtonText: {
    color: '#FF9800',
    fontSize: 14,
    fontWeight: '600',
    marginLeft: 4,
  },
  executeButton: {
    backgroundColor: '#667eea',
  },
  executeButtonText: {
    color: '#FFFFFF',
    fontSize: 14,
    fontWeight: '600',
    marginLeft: 4,
  },
  warningContainer: {
    flexDirection: 'row',
    alignItems: 'center',
    backgroundColor: '#FFF8E1',
    padding: 12,
    borderRadius: 8,
    marginTop: 12,
    borderLeftWidth: 4,
    borderLeftColor: '#FF9800',
  },
  warningText: {
    fontSize: 13,
    color: '#E65100',
    marginLeft: 8,
    flex: 1,
    lineHeight: 18,
  },
});

export default VoiceCommandDisplay;