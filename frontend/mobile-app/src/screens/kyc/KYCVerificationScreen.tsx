import React, { useState } from 'react';
import {
  View,
  Text,
  StyleSheet,
  TouchableOpacity,
  ScrollView,
  Alert,
  ActivityIndicator,
  Image
} from 'react-native';
import { launchImageLibrary, launchCamera } from 'react-native-image-picker';

const KYCVerificationScreen = ({ navigation }) => {
  const [loading, setLoading] = useState(false);
  const [idFront, setIdFront] = useState(null);
  const [idBack, setIdBack] = useState(null);
  const [selfie, setSelfie] = useState(null);

  const pickImage = async (type: 'idFront' | 'idBack' | 'selfie', useCamera: boolean) => {
    const options = {
      mediaType: 'photo' as const,
      quality: 0.8 as const,
      maxWidth: 1920,
      maxHeight: 1080
    };

    const picker = useCamera ? launchCamera : launchImageLibrary;

    try {
      const result = await picker(options);

      if (result.assets && result.assets.length > 0) {
        const asset = result.assets[0];
        switch (type) {
          case 'idFront':
            setIdFront(asset.uri);
            break;
          case 'idBack':
            setIdBack(asset.uri);
            break;
          case 'selfie':
            setSelfie(asset.uri);
            break;
        }
      }
    } catch (error) {
      Alert.alert('Error', 'Failed to pick image');
    }
  };

  const submitKYC = async () => {
    if (!idFront || !idBack || !selfie) {
      Alert.alert('Error', 'Please upload all required documents');
      return;
    }

    setLoading(true);
    try {
      const formData = new FormData();
      formData.append('idFront', {
        uri: idFront,
        type: 'image/jpeg',
        name: 'id_front.jpg'
      });
      formData.append('idBack', {
        uri: idBack,
        type: 'image/jpeg',
        name: 'id_back.jpg'
      });
      formData.append('selfie', {
        uri: selfie,
        type: 'image/jpeg',
        name: 'selfie.jpg'
      });

      const response = await fetch(`${process.env.API_URL}/api/v1/kyc/submit`, {
        method: 'POST',
        headers: {
          'Content-Type': 'multipart/form-data'
        },
        body: formData
      });

      if (response.ok) {
        Alert.alert('Success', 'KYC verification submitted. We will review within 24 hours.', [
          { text: 'OK', onPress: () => navigation.goBack() }
        ]);
      } else {
        const error = await response.json();
        Alert.alert('Error', error.message || 'Failed to submit KYC');
      }
    } catch (error) {
      Alert.alert('Error', 'Network error. Please try again.');
    } finally {
      setLoading(false);
    }
  };

  const renderImagePicker = (
    label: string,
    image: string | null,
    type: 'idFront' | 'idBack' | 'selfie'
  ) => (
    <View style={styles.section}>
      <Text style={styles.label}>{label}</Text>
      {image ? (
        <Image source={{ uri: image }} style={styles.preview} />
      ) : (
        <View style={styles.placeholder}>
          <Text style={styles.placeholderText}>No image selected</Text>
        </View>
      )}
      <View style={styles.buttonRow}>
        <TouchableOpacity
          style={styles.secondaryButton}
          onPress={() => pickImage(type, true)}
        >
          <Text style={styles.secondaryButtonText}>Take Photo</Text>
        </TouchableOpacity>
        <TouchableOpacity
          style={styles.secondaryButton}
          onPress={() => pickImage(type, false)}
        >
          <Text style={styles.secondaryButtonText}>Choose from Gallery</Text>
        </TouchableOpacity>
      </View>
    </View>
  );

  return (
    <ScrollView style={styles.container}>
      <Text style={styles.title}>KYC Verification</Text>
      <Text style={styles.subtitle}>
        Please upload clear photos of your government-issued ID and a selfie for verification.
      </Text>

      {renderImagePicker('ID Card (Front)', idFront, 'idFront')}
      {renderImagePicker('ID Card (Back)', idBack, 'idBack')}
      {renderImagePicker('Selfie with ID', selfie, 'selfie')}

      <TouchableOpacity
        style={[styles.button, loading && styles.buttonDisabled]}
        onPress={submitKYC}
        disabled={loading}
      >
        {loading ? (
          <ActivityIndicator color="#FFF" />
        ) : (
          <Text style={styles.buttonText}>Submit for Verification</Text>
        )}
      </TouchableOpacity>
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
    fontSize: 24,
    fontWeight: 'bold',
    marginBottom: 8
  },
  subtitle: {
    fontSize: 14,
    color: '#666',
    marginBottom: 24
  },
  section: {
    marginBottom: 24
  },
  label: {
    fontSize: 16,
    fontWeight: '600',
    marginBottom: 12
  },
  preview: {
    width: '100%',
    height: 200,
    borderRadius: 8,
    marginBottom: 12
  },
  placeholder: {
    width: '100%',
    height: 200,
    borderRadius: 8,
    borderWidth: 2,
    borderStyle: 'dashed',
    borderColor: '#DDD',
    justifyContent: 'center',
    alignItems: 'center',
    marginBottom: 12
  },
  placeholderText: {
    color: '#999',
    fontSize: 14
  },
  buttonRow: {
    flexDirection: 'row',
    justifyContent: 'space-between',
    gap: 12
  },
  secondaryButton: {
    flex: 1,
    backgroundColor: '#F0F0F0',
    padding: 12,
    borderRadius: 8,
    alignItems: 'center'
  },
  secondaryButtonText: {
    color: '#007AFF',
    fontSize: 14,
    fontWeight: '600'
  },
  button: {
    backgroundColor: '#007AFF',
    padding: 16,
    borderRadius: 8,
    alignItems: 'center',
    marginTop: 16
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

export default KYCVerificationScreen;
