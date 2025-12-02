# Notification Sound Assets

This directory contains audio files for different types of notifications in the Waqiti mobile app.

## Sound Files

### Payment Sounds
- `payment_success.mp3` - Played when a payment is successfully completed
- `money_request.mp3` - Played when money is requested from the user

### Security Sounds  
- `security_alert.mp3` - Played for security-related notifications

### General Sounds
- `message_tone.mp3` - Played for chat/message notifications
- `notification_soft.mp3` - Played for promotional/low-priority notifications
- `error_tone.mp3` - Played when an error occurs
- `success_chime.mp3` - Played for successful operations
- `warning_tone.mp3` - Played for warning notifications
- `default_notification.mp3` - Default notification sound

## Audio Specifications

All sound files should meet the following specifications:
- Format: MP3 or AAC
- Duration: 0.5 - 3.0 seconds
- Sample Rate: 44.1 kHz or 22.05 kHz
- Bit Rate: 128 kbps or higher
- Channels: Mono or Stereo

## Platform Compatibility

- **iOS**: Supports MP3, AAC, WAV, and other formats supported by AVAudioPlayer
- **Android**: Supports MP3, AAC, WAV, and other formats supported by MediaPlayer

## Implementation Notes

1. Keep file sizes small (< 100KB each) to minimize app bundle size
2. Use descriptive names that match the SoundManager.ts enum values
3. Test sounds on both iOS and Android devices
4. Consider volume levels - sounds should be noticeable but not jarring
5. Provide fallback to system sounds if files are missing

## Adding New Sounds

1. Add the audio file to this directory
2. Update the `soundAssets` mapping in `SoundManager.ts`
3. Add the new sound type to the `getSoundKeyForType` method
4. Test on both platforms

## Licensing

Ensure all sound files are properly licensed for commercial use or create original sounds for the application.