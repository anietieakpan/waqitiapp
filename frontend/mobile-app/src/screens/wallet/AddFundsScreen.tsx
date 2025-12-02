import React, {useState} from 'react';
import {View, Text, StyleSheet, TextInput, TouchableOpacity, Alert, ActivityIndicator} from 'react-native';

const AddFundsScreen = ({navigation}) => {
  const [amount, setAmount] = useState('');
  const [loading, setLoading] = useState(false);

  const handleAddFunds = async () => {
    if (!amount || parseFloat(amount) <= 0) {
      Alert.alert('Error', 'Please enter valid amount');
      return;
    }
    setLoading(true);
    try {
      const res = await fetch(`${process.env.API_URL}/api/v1/wallets/add-funds`, {
        method: 'POST',
        headers: {'Content-Type': 'application/json'},
        body: JSON.stringify({amount: parseFloat(amount)})
      });
      if (res.ok) {
        Alert.alert('Success', 'Funds added successfully');
        navigation.goBack();
      } else {
        Alert.alert('Error', 'Failed to add funds');
      }
    } catch (error) {
      Alert.alert('Error', error.message);
    } finally {
      setLoading(false);
    }
  };

  return (
    <View style={styles.container}>
      <Text style={styles.title}>Add Funds</Text>
      <TextInput 
        style={styles.input} 
        placeholder="Amount" 
        value={amount} 
        onChangeText={setAmount} 
        keyboardType="decimal-pad" 
      />
      <TouchableOpacity style={styles.button} onPress={handleAddFunds} disabled={loading}>
        {loading ? <ActivityIndicator color="#FFF" /> : <Text style={styles.buttonText}>Add Funds</Text>}
      </TouchableOpacity>
    </View>
  );
};

const styles = StyleSheet.create({
  container: {flex: 1, padding: 16, backgroundColor: '#FFF'},
  title: {fontSize: 24, fontWeight: 'bold', marginBottom: 24},
  input: {borderWidth: 1, borderColor: '#DDD', borderRadius: 8, padding: 16, fontSize: 18, marginBottom: 24},
  button: {backgroundColor: '#007AFF', padding: 16, borderRadius: 8, alignItems: 'center'},
  buttonText: {color: '#FFF', fontSize: 16, fontWeight: '600'}
});

export default AddFundsScreen;
