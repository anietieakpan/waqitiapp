/**
 * LanguageSelector Component
 * Allows users to select their preferred language
 */

import React, { useState } from 'react';
import {
  View,
  Text,
  TouchableOpacity,
  StyleSheet,
  Modal,
  ScrollView,
  ActivityIndicator,
  Alert,
} from 'react-native';
import Icon from 'react-native-vector-icons/MaterialIcons';
import { useLocalization, SUPPORTED_LANGUAGES, LanguageCode } from '../../contexts/LocalizationContext';

export const LanguageSelector: React.FC = () => {
  const { currentLanguage, changeLanguage, t, loading, error } = useLocalization();
  const [modalVisible, setModalVisible] = useState(false);
  const [changingLanguage, setChangingLanguage] = useState(false);

  const handleLanguageChange = async (languageCode: LanguageCode) => {
    try {
      setChangingLanguage(true);
      setModalVisible(false);
      await changeLanguage(languageCode);
    } catch (err) {
      Alert.alert(
        t('error.title'),
        t('error.languageChangeFailed'),
        [{ text: t('common.ok') }]
      );
    } finally {
      setChangingLanguage(false);
    }
  };

  const currentLanguageInfo = SUPPORTED_LANGUAGES[currentLanguage];

  return (
    <>
      <TouchableOpacity
        style={styles.container}
        onPress={() => setModalVisible(true)}
        disabled={loading || changingLanguage}
      >
        <View style={styles.labelContainer}>
          <Icon name="language" size={24} color="#666" style={styles.icon} />
          <Text style={styles.label}>{t('settings.language')}</Text>
        </View>
        <View style={styles.valueContainer}>
          {changingLanguage ? (
            <ActivityIndicator size="small" color="#007AFF" />
          ) : (
            <>
              <Text style={styles.flag}>{currentLanguageInfo.flag}</Text>
              <Text style={styles.value}>{currentLanguageInfo.name}</Text>
              <Icon name="chevron-right" size={24} color="#999" />
            </>
          )}
        </View>
      </TouchableOpacity>

      <Modal
        animationType="slide"
        transparent={true}
        visible={modalVisible}
        onRequestClose={() => setModalVisible(false)}
      >
        <View style={styles.modalOverlay}>
          <View style={styles.modalContent}>
            <View style={styles.modalHeader}>
              <Text style={styles.modalTitle}>{t('settings.selectLanguage')}</Text>
              <TouchableOpacity
                onPress={() => setModalVisible(false)}
                style={styles.closeButton}
              >
                <Icon name="close" size={24} color="#666" />
              </TouchableOpacity>
            </View>

            <ScrollView style={styles.languageList}>
              {Object.entries(SUPPORTED_LANGUAGES).map(([code, info]) => {
                const isSelected = code === currentLanguage;
                return (
                  <TouchableOpacity
                    key={code}
                    style={[
                      styles.languageItem,
                      isSelected && styles.languageItemSelected,
                    ]}
                    onPress={() => handleLanguageChange(code as LanguageCode)}
                  >
                    <Text style={styles.languageFlag}>{info.flag}</Text>
                    <Text
                      style={[
                        styles.languageName,
                        isSelected && styles.languageNameSelected,
                      ]}
                    >
                      {info.name}
                    </Text>
                    {isSelected && (
                      <Icon name="check" size={24} color="#007AFF" />
                    )}
                  </TouchableOpacity>
                );
              })}
            </ScrollView>
          </View>
        </View>
      </Modal>
    </>
  );
};

const styles = StyleSheet.create({
  container: {
    flexDirection: 'row',
    alignItems: 'center',
    justifyContent: 'space-between',
    paddingVertical: 16,
    paddingHorizontal: 20,
    backgroundColor: '#fff',
    borderBottomWidth: StyleSheet.hairlineWidth,
    borderBottomColor: '#e1e1e1',
  },
  labelContainer: {
    flexDirection: 'row',
    alignItems: 'center',
    flex: 1,
  },
  icon: {
    marginRight: 12,
  },
  label: {
    fontSize: 16,
    color: '#333',
  },
  valueContainer: {
    flexDirection: 'row',
    alignItems: 'center',
  },
  flag: {
    fontSize: 20,
    marginRight: 8,
  },
  value: {
    fontSize: 16,
    color: '#666',
    marginRight: 8,
  },
  modalOverlay: {
    flex: 1,
    backgroundColor: 'rgba(0, 0, 0, 0.5)',
    justifyContent: 'flex-end',
  },
  modalContent: {
    backgroundColor: '#fff',
    borderTopLeftRadius: 20,
    borderTopRightRadius: 20,
    paddingBottom: 34, // Account for safe area
    maxHeight: '80%',
  },
  modalHeader: {
    flexDirection: 'row',
    alignItems: 'center',
    justifyContent: 'space-between',
    paddingHorizontal: 20,
    paddingVertical: 16,
    borderBottomWidth: StyleSheet.hairlineWidth,
    borderBottomColor: '#e1e1e1',
  },
  modalTitle: {
    fontSize: 18,
    fontWeight: '600',
    color: '#333',
  },
  closeButton: {
    padding: 4,
  },
  languageList: {
    paddingVertical: 8,
  },
  languageItem: {
    flexDirection: 'row',
    alignItems: 'center',
    paddingVertical: 16,
    paddingHorizontal: 20,
  },
  languageItemSelected: {
    backgroundColor: '#f0f8ff',
  },
  languageFlag: {
    fontSize: 28,
    marginRight: 16,
  },
  languageName: {
    fontSize: 16,
    color: '#333',
    flex: 1,
  },
  languageNameSelected: {
    color: '#007AFF',
    fontWeight: '500',
  },
});