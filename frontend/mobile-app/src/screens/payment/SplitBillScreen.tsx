import React, { useState } from 'react';
import {
  View,
  ScrollView,
  StyleSheet,
  TouchableOpacity,
  KeyboardAvoidingView,
  Platform,
} from 'react-native';
import {
  Text,
  TextInput,
  Button,
  useTheme,
  Surface,
  Avatar,
  IconButton,
  Chip,
  Menu,
  Divider,
} from 'react-native-paper';
import { useNavigation } from '@react-navigation/native';
import { SafeAreaView } from 'react-native-safe-area-context';
import { useForm, Controller } from 'react-hook-form';
import Icon from 'react-native-vector-icons/MaterialCommunityIcons';
import Header from '../../components/common/Header';
import { formatCurrency } from '../../utils/formatters';

interface SplitBillFormData {
  totalAmount: string;
  description: string;
}

interface SplitMember {
  id: string;
  name: string;
  avatar?: string;
  amount: number;
  isOwner?: boolean;
}

/**
 * Split Bill Screen - Split expenses among multiple people
 */
const SplitBillScreen: React.FC = () => {
  const theme = useTheme();
  const navigation = useNavigation();
  
  const [splitMembers, setSplitMembers] = useState<SplitMember[]>([
    { id: '1', name: 'You', isOwner: true, amount: 0 },
  ]);
  const [splitMethod, setSplitMethod] = useState<'equal' | 'custom'>('equal');
  const [menuVisible, setMenuVisible] = useState(false);

  const {
    control,
    handleSubmit,
    watch,
    formState: { errors, isValid },
  } = useForm<SplitBillFormData>({
    mode: 'onChange',
    defaultValues: {
      totalAmount: '',
      description: '',
    },
  });

  const watchedAmount = watch('totalAmount');
  const totalAmount = parseFloat(watchedAmount) || 0;

  const calculateSplit = () => {
    if (splitMethod === 'equal') {
      const amountPerPerson = totalAmount / splitMembers.length;
      setSplitMembers(prev =>
        prev.map(member => ({ ...member, amount: amountPerPerson }))
      );
    }
  };

  React.useEffect(() => {
    calculateSplit();
  }, [totalAmount, splitMembers.length, splitMethod]);

  const addMember = () => {
    navigation.navigate('ContactSelection', { action: 'split' } as never);
  };

  const removeMember = (memberId: string) => {
    setSplitMembers(prev => prev.filter(member => member.id !== memberId));
  };

  const updateMemberAmount = (memberId: string, amount: number) => {
    setSplitMembers(prev =>
      prev.map(member =>
        member.id === memberId ? { ...member, amount } : member
      )
    );
  };

  const handleSplitBill = (data: SplitBillFormData) => {
    const splitData = {
      totalAmount: parseFloat(data.totalAmount),
      description: data.description,
      members: splitMembers,
      currency: 'USD', // Default currency
    };

    navigation.navigate('PaymentConfirmation', {
      paymentData: {
        type: 'split',
        ...splitData,
      },
    } as never);
  };

  const validateAmount = (amount: string) => {
    const numAmount = parseFloat(amount);
    if (isNaN(numAmount) || numAmount <= 0) {
      return 'Please enter a valid amount';
    }
    return true;
  };

  const renderMember = (member: SplitMember) => (
    <Surface key={member.id} style={styles.memberCard} elevation={1}>
      <View style={styles.memberInfo}>
        <Avatar.Text
          size={40}
          label={member.name.split(' ').map(n => n[0]).join('')}
          style={styles.memberAvatar}
        />
        <View style={styles.memberDetails}>
          <Text style={styles.memberName}>{member.name}</Text>
          {member.isOwner && <Chip mode="outlined" compact>Owner</Chip>}
        </View>
      </View>

      <View style={styles.memberAmount}>
        {splitMethod === 'custom' ? (
          <TextInput
            mode="outlined"
            keyboardType="decimal-pad"
            value={member.amount.toString()}
            onChangeText={(text) =>
              updateMemberAmount(member.id, parseFloat(text) || 0)
            }
            style={styles.amountInput}
            contentStyle={styles.amountInputContent}
            dense
          />
        ) : (
          <Text style={styles.splitAmount}>
            {formatCurrency(member.amount, 'USD')}
          </Text>
        )}

        {!member.isOwner && (
          <IconButton
            icon="close"
            size={20}
            onPress={() => removeMember(member.id)}
          />
        )}
      </View>
    </Surface>
  );

  const totalSplit = splitMembers.reduce((sum, member) => sum + member.amount, 0);
  const isBalanced = Math.abs(totalSplit - totalAmount) < 0.01;

  return (
    <KeyboardAvoidingView
      style={styles.container}
      behavior={Platform.OS === 'ios' ? 'padding' : 'height'}
    >
      <SafeAreaView style={styles.container}>
        <Header
          title="Split Bill"
          leftAction={
            <IconButton
              icon="arrow-left"
              size={24}
              onPress={() => navigation.goBack()}
            />
          }
          rightAction={
            <Menu
              visible={menuVisible}
              onDismiss={() => setMenuVisible(false)}
              anchor={
                <IconButton
                  icon="dots-vertical"
                  size={24}
                  onPress={() => setMenuVisible(true)}
                />
              }
            >
              <Menu.Item
                onPress={() => {
                  setSplitMethod('equal');
                  setMenuVisible(false);
                }}
                title="Split Equally"
                leadingIcon="equal"
              />
              <Menu.Item
                onPress={() => {
                  setSplitMethod('custom');
                  setMenuVisible(false);
                }}
                title="Custom Split"
                leadingIcon="pencil"
              />
            </Menu>
          }
        />

        <ScrollView
          style={styles.scrollView}
          contentContainerStyle={styles.scrollContent}
          showsVerticalScrollIndicator={false}
        >
          {/* Amount Input */}
          <Surface style={styles.amountCard} elevation={2}>
            <Text style={styles.cardTitle}>Total Amount</Text>
            <Controller
              control={control}
              name="totalAmount"
              rules={{
                required: 'Amount is required',
                validate: validateAmount,
              }}
              render={({ field: { onChange, onBlur, value } }) => (
                <TextInput
                  label="Enter amount"
                  value={value}
                  onBlur={onBlur}
                  onChangeText={onChange}
                  mode="outlined"
                  keyboardType="decimal-pad"
                  style={styles.amountInputLarge}
                  contentStyle={styles.amountInputLargeContent}
                  left={<TextInput.Icon icon="currency-usd" />}
                  error={!!errors.totalAmount}
                />
              )}
            />
            {errors.totalAmount && (
              <Text style={styles.errorText}>{errors.totalAmount.message}</Text>
            )}
          </Surface>

          {/* Description */}
          <Surface style={styles.descriptionCard} elevation={2}>
            <Text style={styles.cardTitle}>Description</Text>
            <Controller
              control={control}
              name="description"
              render={({ field: { onChange, onBlur, value } }) => (
                <TextInput
                  label="What's this for?"
                  value={value}
                  onBlur={onBlur}
                  onChangeText={onChange}
                  mode="outlined"
                  placeholder="Dinner, groceries, etc."
                  style={styles.descriptionInput}
                />
              )}
            />
          </Surface>

          {/* Split Method */}
          <Surface style={styles.splitMethodCard} elevation={2}>
            <View style={styles.splitMethodHeader}>
              <Text style={styles.cardTitle}>Split Method</Text>
              <Chip
                mode={splitMethod === 'equal' ? 'flat' : 'outlined'}
                selected={splitMethod === 'equal'}
                onPress={() => setSplitMethod('equal')}
                style={styles.methodChip}
              >
                Equal
              </Chip>
              <Chip
                mode={splitMethod === 'custom' ? 'flat' : 'outlined'}
                selected={splitMethod === 'custom'}
                onPress={() => setSplitMethod('custom')}
                style={styles.methodChip}
              >
                Custom
              </Chip>
            </View>
          </Surface>

          {/* Members List */}
          <Surface style={styles.membersCard} elevation={2}>
            <View style={styles.membersHeader}>
              <Text style={styles.cardTitle}>
                Split among {splitMembers.length} people
              </Text>
              <TouchableOpacity
                style={styles.addMemberButton}
                onPress={addMember}
              >
                <Icon name="plus" size={20} color={theme.colors.primary} />
                <Text style={styles.addMemberText}>Add</Text>
              </TouchableOpacity>
            </View>

            <View style={styles.membersList}>
              {splitMembers.map(renderMember)}
            </View>

            {/* Split Summary */}
            <View style={styles.splitSummary}>
              <Divider style={styles.summaryDivider} />
              <View style={styles.summaryRow}>
                <Text style={styles.summaryLabel}>Total Split:</Text>
                <Text style={styles.summaryValue}>
                  {formatCurrency(totalSplit, 'USD')}
                </Text>
              </View>
              <View style={styles.summaryRow}>
                <Text style={styles.summaryLabel}>Total Bill:</Text>
                <Text style={styles.summaryValue}>
                  {formatCurrency(totalAmount, 'USD')}
                </Text>
              </View>
              {!isBalanced && totalAmount > 0 && (
                <View style={styles.balanceWarning}>
                  <Icon name="alert-circle" size={16} color={theme.colors.error} />
                  <Text style={styles.balanceWarningText}>
                    Split doesn't match total amount
                  </Text>
                </View>
              )}
            </View>
          </Surface>

          {/* Split Preview */}
          {totalAmount > 0 && (
            <Surface style={styles.previewCard} elevation={2}>
              <Text style={styles.cardTitle}>Preview</Text>
              <Text style={styles.previewText}>
                Each person will be requested to pay their share. 
                You'll pay your portion now.
              </Text>
            </Surface>
          )}
        </ScrollView>

        {/* Continue Button */}
        <View style={styles.bottomContainer}>
          <Button
            mode="contained"
            onPress={handleSubmit(handleSplitBill)}
            disabled={!isValid || !isBalanced || totalAmount <= 0}
            style={styles.continueButton}
            contentStyle={styles.buttonContent}
          >
            Continue Split
          </Button>
        </View>
      </SafeAreaView>
    </KeyboardAvoidingView>
  );
};

const styles = StyleSheet.create({
  container: {
    flex: 1,
    backgroundColor: '#f5f5f5',
  },
  scrollView: {
    flex: 1,
  },
  scrollContent: {
    padding: 16,
    paddingBottom: 100,
  },
  cardTitle: {
    fontSize: 16,
    fontWeight: '600',
    color: '#333',
    marginBottom: 16,
  },
  amountCard: {
    borderRadius: 16,
    padding: 20,
    marginBottom: 16,
    backgroundColor: 'white',
  },
  amountInputLarge: {
    backgroundColor: 'white',
    marginBottom: 8,
  },
  amountInputLargeContent: {
    fontSize: 24,
    fontWeight: 'bold',
  },
  errorText: {
    color: '#d32f2f',
    fontSize: 12,
    marginTop: 4,
  },
  descriptionCard: {
    borderRadius: 16,
    padding: 20,
    marginBottom: 16,
    backgroundColor: 'white',
  },
  descriptionInput: {
    backgroundColor: 'white',
  },
  splitMethodCard: {
    borderRadius: 16,
    padding: 20,
    marginBottom: 16,
    backgroundColor: 'white',
  },
  splitMethodHeader: {
    flexDirection: 'row',
    alignItems: 'center',
    gap: 8,
  },
  methodChip: {
    marginLeft: 'auto',
  },
  membersCard: {
    borderRadius: 16,
    padding: 20,
    marginBottom: 16,
    backgroundColor: 'white',
  },
  membersHeader: {
    flexDirection: 'row',
    justifyContent: 'space-between',
    alignItems: 'center',
    marginBottom: 16,
  },
  addMemberButton: {
    flexDirection: 'row',
    alignItems: 'center',
    paddingHorizontal: 12,
    paddingVertical: 6,
    borderRadius: 20,
    backgroundColor: '#f0f0f0',
  },
  addMemberText: {
    marginLeft: 4,
    fontSize: 14,
    color: '#2196F3',
    fontWeight: '500',
  },
  membersList: {
    gap: 12,
  },
  memberCard: {
    flexDirection: 'row',
    alignItems: 'center',
    padding: 12,
    borderRadius: 12,
    backgroundColor: '#f8f8f8',
  },
  memberInfo: {
    flex: 1,
    flexDirection: 'row',
    alignItems: 'center',
  },
  memberAvatar: {
    marginRight: 12,
  },
  memberDetails: {
    flex: 1,
  },
  memberName: {
    fontSize: 16,
    fontWeight: '500',
    color: '#333',
    marginBottom: 4,
  },
  memberAmount: {
    flexDirection: 'row',
    alignItems: 'center',
  },
  amountInput: {
    width: 100,
    backgroundColor: 'white',
    marginRight: 8,
  },
  amountInputContent: {
    textAlign: 'center',
  },
  splitAmount: {
    fontSize: 16,
    fontWeight: 'bold',
    color: '#4CAF50',
    marginRight: 8,
    minWidth: 80,
    textAlign: 'right',
  },
  splitSummary: {
    marginTop: 16,
  },
  summaryDivider: {
    marginBottom: 12,
  },
  summaryRow: {
    flexDirection: 'row',
    justifyContent: 'space-between',
    marginBottom: 8,
  },
  summaryLabel: {
    fontSize: 14,
    color: '#666',
  },
  summaryValue: {
    fontSize: 14,
    fontWeight: '500',
    color: '#333',
  },
  balanceWarning: {
    flexDirection: 'row',
    alignItems: 'center',
    marginTop: 8,
    padding: 8,
    backgroundColor: '#ffebee',
    borderRadius: 8,
  },
  balanceWarningText: {
    marginLeft: 8,
    fontSize: 12,
    color: '#d32f2f',
  },
  previewCard: {
    borderRadius: 16,
    padding: 20,
    backgroundColor: 'white',
  },
  previewText: {
    fontSize: 14,
    color: '#666',
    lineHeight: 20,
  },
  bottomContainer: {
    position: 'absolute',
    bottom: 0,
    left: 0,
    right: 0,
    padding: 16,
    backgroundColor: 'white',
    borderTopWidth: 1,
    borderTopColor: '#e0e0e0',
  },
  continueButton: {
    marginBottom: 0,
  },
  buttonContent: {
    height: 52,
  },
});

export default SplitBillScreen;