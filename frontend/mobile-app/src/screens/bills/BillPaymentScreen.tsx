import React, { useState, useEffect } from 'react';
import {
  View,
  Text,
  StyleSheet,
  TouchableOpacity,
  ScrollView,
  TextInput,
  Alert,
  ActivityIndicator,
  FlatList
} from 'react-native';

interface BillerCategory {
  id: string;
  name: string;
  icon: string;
}

interface Biller {
  id: string;
  name: string;
  categoryId: string;
  logoUrl: string;
}

const BillPaymentScreen = ({ navigation }) => {
  const [loading, setLoading] = useState(false);
  const [categories, setCategories] = useState<BillerCategory[]>([]);
  const [selectedCategory, setSelectedCategory] = useState<string | null>(null);
  const [billers, setBillers] = useState<Biller[]>([]);
  const [accountNumber, setAccountNumber] = useState('');
  const [amount, setAmount] = useState('');
  const [selectedBiller, setSelectedBiller] = useState<Biller | null>(null);

  useEffect(() => {
    loadCategories();
  }, []);

  useEffect(() => {
    if (selectedCategory) {
      loadBillers(selectedCategory);
    }
  }, [selectedCategory]);

  const loadCategories = async () => {
    setLoading(true);
    try {
      const response = await fetch(`${process.env.API_URL}/api/v1/bills/categories`);
      if (response.ok) {
        const data = await response.json();
        setCategories(data);
      }
    } catch (error) {
      Alert.alert('Error', 'Failed to load bill categories');
    } finally {
      setLoading(false);
    }
  };

  const loadBillers = async (categoryId: string) => {
    setLoading(true);
    try {
      const response = await fetch(
        `${process.env.API_URL}/api/v1/bills/billers?categoryId=${categoryId}`
      );
      if (response.ok) {
        const data = await response.json();
        setBillers(data);
      }
    } catch (error) {
      Alert.alert('Error', 'Failed to load billers');
    } finally {
      setLoading(false);
    }
  };

  const handlePayBill = async () => {
    if (!selectedBiller || !accountNumber || !amount) {
      Alert.alert('Error', 'Please fill in all fields');
      return;
    }

    const numAmount = parseFloat(amount);
    if (isNaN(numAmount) || numAmount <= 0) {
      Alert.alert('Error', 'Please enter a valid amount');
      return;
    }

    Alert.alert(
      'Confirm Payment',
      `Pay ${selectedBiller.name}\nAccount: ${accountNumber}\nAmount: $${numAmount.toFixed(2)}`,
      [
        { text: 'Cancel', style: 'cancel' },
        {
          text: 'Pay',
          onPress: async () => {
            setLoading(true);
            try {
              const response = await fetch(`${process.env.API_URL}/api/v1/bills/pay`, {
                method: 'POST',
                headers: {
                  'Content-Type': 'application/json'
                },
                body: JSON.stringify({
                  billerId: selectedBiller.id,
                  accountNumber,
                  amount: numAmount
                })
              });

              if (response.ok) {
                Alert.alert('Success', 'Bill payment successful', [
                  { text: 'OK', onPress: () => navigation.goBack() }
                ]);
              } else {
                const error = await response.json();
                Alert.alert('Error', error.message || 'Payment failed');
              }
            } catch (error) {
              Alert.alert('Error', 'Network error. Please try again.');
            } finally {
              setLoading(false);
            }
          }
        }
      ]
    );
  };

  const renderCategoryCard = ({ item }: { item: BillerCategory }) => (
    <TouchableOpacity
      style={[
        styles.categoryCard,
        selectedCategory === item.id && styles.categoryCardSelected
      ]}
      onPress={() => {
        setSelectedCategory(item.id);
        setSelectedBiller(null);
      }}
    >
      <Text style={styles.categoryIcon}>{item.icon}</Text>
      <Text style={styles.categoryName}>{item.name}</Text>
    </TouchableOpacity>
  );

  const renderBillerCard = ({ item }: { item: Biller }) => (
    <TouchableOpacity
      style={[
        styles.billerCard,
        selectedBiller?.id === item.id && styles.billerCardSelected
      ]}
      onPress={() => setSelectedBiller(item)}
    >
      <Text style={styles.billerName}>{item.name}</Text>
    </TouchableOpacity>
  );

  return (
    <ScrollView style={styles.container}>
      <Text style={styles.title}>Pay Bills</Text>

      <Text style={styles.sectionTitle}>Select Category</Text>
      <FlatList
        data={categories}
        renderItem={renderCategoryCard}
        keyExtractor={(item) => item.id}
        horizontal
        showsHorizontalScrollIndicator={false}
        style={styles.categoryList}
      />

      {selectedCategory && (
        <>
          <Text style={styles.sectionTitle}>Select Biller</Text>
          {loading ? (
            <ActivityIndicator size="large" color="#007AFF" />
          ) : (
            <FlatList
              data={billers}
              renderItem={renderBillerCard}
              keyExtractor={(item) => item.id}
              numColumns={2}
              columnWrapperStyle={styles.billerRow}
            />
          )}
        </>
      )}

      {selectedBiller && (
        <View style={styles.paymentForm}>
          <Text style={styles.sectionTitle}>Payment Details</Text>

          <TextInput
            style={styles.input}
            placeholder="Account Number"
            value={accountNumber}
            onChangeText={setAccountNumber}
            keyboardType="numeric"
          />

          <TextInput
            style={styles.input}
            placeholder="Amount"
            value={amount}
            onChangeText={setAmount}
            keyboardType="decimal-pad"
          />

          <TouchableOpacity
            style={[styles.button, loading && styles.buttonDisabled]}
            onPress={handlePayBill}
            disabled={loading}
          >
            {loading ? (
              <ActivityIndicator color="#FFF" />
            ) : (
              <Text style={styles.buttonText}>Pay Bill</Text>
            )}
          </TouchableOpacity>
        </View>
      )}
    </ScrollView>
  );
};

const styles = StyleSheet.create({
  container: {
    flex: 1,
    padding: 16,
    backgroundColor: '#FFF'
  },
  title: {
    fontSize: 28,
    fontWeight: 'bold',
    marginBottom: 24
  },
  sectionTitle: {
    fontSize: 18,
    fontWeight: '600',
    marginBottom: 12,
    marginTop: 16
  },
  categoryList: {
    marginBottom: 8
  },
  categoryCard: {
    width: 100,
    height: 100,
    backgroundColor: '#F0F0F0',
    borderRadius: 12,
    justifyContent: 'center',
    alignItems: 'center',
    marginRight: 12,
    borderWidth: 2,
    borderColor: 'transparent'
  },
  categoryCardSelected: {
    borderColor: '#007AFF',
    backgroundColor: '#E3F2FD'
  },
  categoryIcon: {
    fontSize: 32,
    marginBottom: 8
  },
  categoryName: {
    fontSize: 12,
    fontWeight: '500',
    textAlign: 'center'
  },
  billerRow: {
    justifyContent: 'space-between',
    marginBottom: 12
  },
  billerCard: {
    flex: 1,
    backgroundColor: '#F0F0F0',
    padding: 16,
    borderRadius: 8,
    marginHorizontal: 4,
    borderWidth: 2,
    borderColor: 'transparent'
  },
  billerCardSelected: {
    borderColor: '#007AFF',
    backgroundColor: '#E3F2FD'
  },
  billerName: {
    fontSize: 14,
    fontWeight: '500',
    textAlign: 'center'
  },
  paymentForm: {
    marginTop: 16
  },
  input: {
    borderWidth: 1,
    borderColor: '#DDD',
    borderRadius: 8,
    padding: 16,
    fontSize: 16,
    marginBottom: 16
  },
  button: {
    backgroundColor: '#007AFF',
    padding: 16,
    borderRadius: 8,
    alignItems: 'center',
    marginTop: 8
  },
  buttonDisabled: {
    opacity: 0.6
  },
  buttonText: {
    color: '#FFF',
    fontSize: 16,
    fontWeight: '600'
  }
});

export default BillPaymentScreen;
