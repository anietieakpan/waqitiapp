import React, {useState} from 'react';
import {View, Text, StyleSheet, TouchableOpacity, Share} from 'react-native';
import QRCode from 'react-native-qrcode-svg';

const ReceiveMoneyScreen = () => {
  const userId = 'user123'; // TODO: Get from auth context
  const paymentLink = `waqiti://pay/${userId}`;

  const handleShare = async () => {
    await Share.share({message: `Send me money via Waqiti: ${paymentLink}`});
  };

  return (
    <View style={styles.container}>
      <Text style={styles.title}>Receive Money</Text>
      <View style={styles.qrContainer}>
        <QRCode value={paymentLink} size={200} />
      </View>
      <Text style={styles.userId}>@{userId}</Text>
      <TouchableOpacity style={styles.shareButton} onPress={handleShare}>
        <Text style={styles.shareButtonText}>Share Payment Link</Text>
      </TouchableOpacity>
    </View>
  );
};

const styles = StyleSheet.create({
  container: {flex: 1, alignItems: 'center', justifyContent: 'center', padding: 16, backgroundColor: '#FFF'},
  title: {fontSize: 24, fontWeight: 'bold', marginBottom: 32},
  qrContainer: {padding: 20, backgroundColor: '#FFF', borderRadius: 12, elevation: 2},
  userId: {fontSize: 18, marginTop: 24, color: '#666'},
  shareButton: {marginTop: 32, backgroundColor: '#007AFF', paddingHorizontal: 32, paddingVertical: 16, borderRadius: 8},
  shareButtonText: {color: '#FFF', fontSize: 16, fontWeight: '600'}
});

export default ReceiveMoneyScreen;
