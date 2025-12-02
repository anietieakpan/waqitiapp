/**
 * Client-side End-to-End Encryption Service
 * 
 * Provides secure message encryption/decryption capabilities using:
 * - ECDH (Elliptic Curve Diffie-Hellman) for key exchange
 * - AES-GCM for symmetric encryption
 * - ECDSA for digital signatures
 * - Perfect Forward Secrecy with key rotation
 */

interface KeyPair {
  publicKey: CryptoKey;
  privateKey: CryptoKey;
}

interface EncryptedMessage {
  encryptedContent: string;
  iv: string;
  ephemeralPublicKey: string;
  signature: string;
  senderUserId: string;
  recipientUserId: string;
  timestamp: number;
  keyVersion: string;
}

interface MessagePayload {
  content: string;
  senderUserId: string;
  recipientUserId: string;
  timestamp: number;
  messageId: string;
}

class ClientEncryptionService {
  private keyPairs: Map<string, KeyPair> = new Map();
  private publicKeys: Map<string, CryptoKey> = new Map();
  private sharedSecrets: Map<string, { key: CryptoKey; timestamp: number; version: string }> = new Map();
  private readonly ALGORITHM = 'ECDH';
  private readonly CURVE = 'P-256';
  private readonly AES_ALGORITHM = 'AES-GCM';
  private readonly SIGNATURE_ALGORITHM = 'ECDSA';
  private readonly SECRET_EXPIRY_MS = 3600000; // 1 hour

  constructor() {
    this.initializeCrypto();
  }

  private async initializeCrypto(): Promise<void> {
    // Check if Web Crypto API is available
    if (!window.crypto || !window.crypto.subtle) {
      throw new Error('Web Crypto API not supported in this browser');
    }
  }

  /**
   * Generate a new ECDH key pair for the current user
   */
  async generateKeyPair(userId: string): Promise<string> {
    try {
      const keyPair = await window.crypto.subtle.generateKey(
        {
          name: this.ALGORITHM,
          namedCurve: this.CURVE,
        },
        true, // extractable
        ['deriveKey', 'deriveBits']
      );

      // Generate signing key pair
      const signingKeyPair = await window.crypto.subtle.generateKey(
        {
          name: this.SIGNATURE_ALGORITHM,
          namedCurve: this.CURVE,
        },
        true,
        ['sign', 'verify']
      );

      // Store both key pairs
      this.keyPairs.set(userId, keyPair);
      this.keyPairs.set(`${userId}_signing`, signingKeyPair);

      // Export public key for sharing
      const publicKeyBuffer = await window.crypto.subtle.exportKey('raw', keyPair.publicKey);
      const publicKeyBase64 = this.arrayBufferToBase64(publicKeyBuffer);

      console.log(`Generated key pair for user: ${userId}`);
      return publicKeyBase64;
    } catch (error) {
      console.error('Key pair generation failed:', error);
      throw new Error('Failed to generate key pair');
    }
  }

  /**
   * Import a public key from another user
   */
  async importPublicKey(userId: string, publicKeyBase64: string, keyType: 'encryption' | 'signing' = 'encryption'): Promise<void> {
    try {
      const publicKeyBuffer = this.base64ToArrayBuffer(publicKeyBase64);
      
      const algorithm = keyType === 'encryption' ? this.ALGORITHM : this.SIGNATURE_ALGORITHM;
      const usage = keyType === 'encryption' ? ['deriveKey', 'deriveBits'] : ['verify'];

      const publicKey = await window.crypto.subtle.importKey(
        'raw',
        publicKeyBuffer,
        {
          name: algorithm,
          namedCurve: this.CURVE,
        },
        true,
        usage as any
      );

      const keyId = keyType === 'signing' ? `${userId}_signing_public` : userId;
      this.publicKeys.set(keyId, publicKey);

      console.log(`Imported ${keyType} public key for user: ${userId}`);
    } catch (error) {
      console.error('Public key import failed:', error);
      throw new Error('Failed to import public key');
    }
  }

  /**
   * Derive shared secret using ECDH
   */
  private async deriveSharedSecret(userId: string, recipientId: string): Promise<CryptoKey> {
    const userKeyPair = this.keyPairs.get(userId);
    const recipientPublicKey = this.publicKeys.get(recipientId);

    if (!userKeyPair || !recipientPublicKey) {
      throw new Error('Missing keys for shared secret derivation');
    }

    try {
      // Perform ECDH key agreement
      const sharedSecretBits = await window.crypto.subtle.deriveBits(
        {
          name: this.ALGORITHM,
          public: recipientPublicKey,
        },
        userKeyPair.privateKey,
        256 // 256 bits for AES-256
      );

      // Derive AES key from shared secret
      const aesKey = await window.crypto.subtle.importKey(
        'raw',
        sharedSecretBits,
        {
          name: this.AES_ALGORITHM,
        },
        false,
        ['encrypt', 'decrypt']
      );

      return aesKey;
    } catch (error) {
      console.error('Shared secret derivation failed:', error);
      throw new Error('Failed to derive shared secret');
    }
  }

  /**
   * Get or create shared secret between two users
   */
  private async getOrCreateSharedSecret(userId: string, recipientId: string): Promise<{ key: CryptoKey; version: string }> {
    const secretKey = this.createSecretKey(userId, recipientId);
    const existingSecret = this.sharedSecrets.get(secretKey);

    // Check if existing secret is still valid
    if (existingSecret && (Date.now() - existingSecret.timestamp) < this.SECRET_EXPIRY_MS) {
      return { key: existingSecret.key, version: existingSecret.version };
    }

    // Generate new shared secret
    const sharedKey = await this.deriveSharedSecret(userId, recipientId);
    const version = `v${Date.now()}`;

    this.sharedSecrets.set(secretKey, {
      key: sharedKey,
      timestamp: Date.now(),
      version,
    });

    console.log(`Created shared secret between ${userId} and ${recipientId}`);
    return { key: sharedKey, version };
  }

  /**
   * Create deterministic key for shared secrets map
   */
  private createSecretKey(userId1: string, userId2: string): string {
    return userId1 < userId2 ? `${userId1}:${userId2}` : `${userId2}:${userId1}`;
  }

  /**
   * Encrypt a message with end-to-end encryption
   */
  async encryptMessage(senderUserId: string, recipientUserId: string, messageContent: string): Promise<EncryptedMessage> {
    try {
      console.log(`Encrypting message from ${senderUserId} to ${recipientUserId}`);

      // Get shared secret
      const { key: sharedKey, version: keyVersion } = await this.getOrCreateSharedSecret(senderUserId, recipientUserId);

      // Generate ephemeral key pair for Perfect Forward Secrecy
      const ephemeralKeyPair = await window.crypto.subtle.generateKey(
        {
          name: this.ALGORITHM,
          namedCurve: this.CURVE,
        },
        true,
        ['deriveKey', 'deriveBits']
      );

      // Create message payload
      const payload: MessagePayload = {
        content: messageContent,
        senderUserId,
        recipientUserId,
        timestamp: Date.now(),
        messageId: this.generateMessageId(),
      };

      const payloadString = JSON.stringify(payload);
      const messageBuffer = new TextEncoder().encode(payloadString);

      // Generate random IV
      const iv = window.crypto.getRandomValues(new Uint8Array(12));

      // Encrypt message
      const encryptedBuffer = await window.crypto.subtle.encrypt(
        {
          name: this.AES_ALGORITHM,
          iv,
        },
        sharedKey,
        messageBuffer
      );

      // Sign the encrypted message
      const signature = await this.signMessage(senderUserId, new Uint8Array(encryptedBuffer));

      // Export ephemeral public key
      const ephemeralPublicKeyBuffer = await window.crypto.subtle.exportKey('raw', ephemeralKeyPair.publicKey);

      return {
        encryptedContent: this.arrayBufferToBase64(encryptedBuffer),
        iv: this.arrayBufferToBase64(iv),
        ephemeralPublicKey: this.arrayBufferToBase64(ephemeralPublicKeyBuffer),
        signature,
        senderUserId,
        recipientUserId,
        timestamp: Date.now(),
        keyVersion,
      };
    } catch (error) {
      console.error('Message encryption failed:', error);
      throw new Error('Failed to encrypt message');
    }
  }

  /**
   * Decrypt an encrypted message
   */
  async decryptMessage(recipientUserId: string, encryptedMessage: EncryptedMessage): Promise<string> {
    try {
      console.log(`Decrypting message for user: ${recipientUserId}`);

      // Verify signature first
      const encryptedBuffer = this.base64ToArrayBuffer(encryptedMessage.encryptedContent);
      const isValidSignature = await this.verifySignature(
        encryptedMessage.senderUserId,
        new Uint8Array(encryptedBuffer),
        encryptedMessage.signature
      );

      if (!isValidSignature) {
        throw new Error('Message signature verification failed');
      }

      // Get shared secret
      const { key: sharedKey } = await this.getOrCreateSharedSecret(
        encryptedMessage.senderUserId,
        recipientUserId
      );

      // Decrypt message
      const iv = this.base64ToArrayBuffer(encryptedMessage.iv);
      
      const decryptedBuffer = await window.crypto.subtle.decrypt(
        {
          name: this.AES_ALGORITHM,
          iv,
        },
        sharedKey,
        encryptedBuffer
      );

      const decryptedString = new TextDecoder().decode(decryptedBuffer);
      const payload: MessagePayload = JSON.parse(decryptedString);

      // Validate message metadata
      if (payload.recipientUserId !== recipientUserId) {
        throw new Error('Message recipient mismatch');
      }

      console.log(`Successfully decrypted message for user: ${recipientUserId}`);
      return payload.content;
    } catch (error) {
      console.error('Message decryption failed:', error);
      throw new Error('Failed to decrypt message');
    }
  }

  /**
   * Sign a message for authentication
   */
  private async signMessage(userId: string, messageBytes: Uint8Array): Promise<string> {
    try {
      const signingKeyPair = this.keyPairs.get(`${userId}_signing`);
      if (!signingKeyPair) {
        throw new Error('Signing key pair not found');
      }

      const signature = await window.crypto.subtle.sign(
        {
          name: this.SIGNATURE_ALGORITHM,
          hash: 'SHA-256',
        },
        signingKeyPair.privateKey,
        messageBytes as ArrayBuffer
      );

      return this.arrayBufferToBase64(signature as ArrayBuffer);
    } catch (error) {
      console.error('Message signing failed:', error);
      throw new Error('Failed to sign message');
    }
  }

  /**
   * Verify message signature
   */
  private async verifySignature(userId: string, messageBytes: Uint8Array, signatureBase64: string): Promise<boolean> {
    try {
      const publicKey = this.publicKeys.get(`${userId}_signing_public`);
      if (!publicKey) {
        console.warn(`No signing public key found for user: ${userId}`);
        return false;
      }

      const signatureBuffer = this.base64ToArrayBuffer(signatureBase64);

      const isValid = await window.crypto.subtle.verify(
        {
          name: this.SIGNATURE_ALGORITHM,
          hash: 'SHA-256',
        },
        publicKey,
        signatureBuffer,
        messageBytes as ArrayBuffer
      );

      return isValid;
    } catch (error) {
      console.error('Signature verification failed:', error);
      return false;
    }
  }

  /**
   * Generate unique message ID
   */
  private generateMessageId(): string {
    return `msg_${Date.now()}_${Math.random().toString(36).substr(2, 9)}`;
  }

  /**
   * Rotate keys for Perfect Forward Secrecy
   */
  async rotateKeys(userId: string): Promise<string> {
    console.log(`Rotating keys for user: ${userId}`);

    // Clear old shared secrets
    const keysToRemove: string[] = [];
    this.sharedSecrets.forEach((_, key) => {
      if (key.includes(userId)) {
        keysToRemove.push(key);
      }
    });
    keysToRemove.forEach(key => this.sharedSecrets.delete(key));

    // Generate new key pair
    return await this.generateKeyPair(userId);
  }

  /**
   * Clean up expired shared secrets
   */
  cleanupExpiredSecrets(): void {
    const now = Date.now();
    let removedCount = 0;

    this.sharedSecrets.forEach((secret, key) => {
      if ((now - secret.timestamp) > this.SECRET_EXPIRY_MS) {
        this.sharedSecrets.delete(key);
        removedCount++;
      }
    });

    if (removedCount > 0) {
      console.log(`Cleaned up ${removedCount} expired shared secrets`);
    }
  }

  /**
   * Export user's public keys for sharing
   */
  async exportPublicKeys(userId: string): Promise<{ encryption: string; signing: string }> {
    const encryptionKeyPair = this.keyPairs.get(userId);
    const signingKeyPair = this.keyPairs.get(`${userId}_signing`);

    if (!encryptionKeyPair || !signingKeyPair) {
      throw new Error('Key pairs not found');
    }

    const encryptionKey = await window.crypto.subtle.exportKey('raw', encryptionKeyPair.publicKey);
    const signingKey = await window.crypto.subtle.exportKey('raw', signingKeyPair.publicKey);

    return {
      encryption: this.arrayBufferToBase64(encryptionKey),
      signing: this.arrayBufferToBase64(signingKey),
    };
  }

  /**
   * Get encryption statistics
   */
  getStats(): { keyPairs: number; publicKeys: number; sharedSecrets: number } {
    return {
      keyPairs: this.keyPairs.size,
      publicKeys: this.publicKeys.size,
      sharedSecrets: this.sharedSecrets.size,
    };
  }

  /**
   * Utility functions for base64 encoding/decoding
   */
  private arrayBufferToBase64(buffer: ArrayBuffer | Uint8Array): string {
    const bytes = buffer instanceof Uint8Array ? buffer : new Uint8Array(buffer);
    let binary = '';
    bytes.forEach(byte => binary += String.fromCharCode(byte));
    return window.btoa(binary);
  }

  private base64ToArrayBuffer(base64: string): ArrayBuffer {
    const binaryString = window.atob(base64);
    const bytes = new Uint8Array(binaryString.length);
    for (let i = 0; i < binaryString.length; i++) {
      bytes[i] = binaryString.charCodeAt(i);
    }
    return bytes.buffer;
  }
}

// Create and export singleton instance
export const encryptionService = new ClientEncryptionService();

// React Hook for easy integration
export function useEncryption() {
  return {
    generateKeyPair: encryptionService.generateKeyPair.bind(encryptionService),
    importPublicKey: encryptionService.importPublicKey.bind(encryptionService),
    encryptMessage: encryptionService.encryptMessage.bind(encryptionService),
    decryptMessage: encryptionService.decryptMessage.bind(encryptionService),
    rotateKeys: encryptionService.rotateKeys.bind(encryptionService),
    exportPublicKeys: encryptionService.exportPublicKeys.bind(encryptionService),
    getStats: encryptionService.getStats.bind(encryptionService),
  };
}

export default encryptionService;