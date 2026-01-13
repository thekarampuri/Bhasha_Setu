# VoiceTranslate Android App

Android client for the Bhasha Setu real-time voice communication system.

> [!WARNING]
> **Status**: Under Reconstruction - Rebuilding with WebRTC  
> The previous AudioRecord + WebSocket PCM relay implementation has been removed. The project is being rebuilt from scratch using WebRTC for peer-to-peer voice communication.

## Planned Features (WebRTC Implementation)

- **Real-time Voice Communication**: WebRTC peer-to-peer audio streaming
- **Room-based Calls**: Join calls using shared Call IDs
- **Audio Controls**: Mute and speaker controls
- **Multi-language Support**: Translation features (10+ languages planned)

## Architecture

The app follows a clean architecture pattern with clear separation of concerns:

```
android/src/main/java/com/example/voicetranslate/
├── data/                  # Data layer
│   ├── model/             # Data models
│   │   ├── CallConfig.kt
│   │   ├── CallState.kt
│   │   ├── TranscriptionMessage.kt
│   │   │   ├── AudioConfig.kt
│   │   └── Language.kt
│   └── repository/        # Repository interfaces
│       ├── CallRepository.kt
│       └── PreferencesRepository.kt
├── audio/                 # Audio processing
│   ├── CallManager.kt     # WebSocket and audio management
│   ├── VoiceActivityDetector.kt
│   └── WavRecorder.kt
├── util/                  # Utilities
│   └── Constants.kt       # Centralized constants
├── MainActivity.kt        # Language selection screen
└── CallActivity.kt        # Call screen with transcription
```

## Setup

### Prerequisites

- Android Studio Arctic Fox or later
- Android SDK 24+
- Kotlin 1.5+
- **Backend Server**: The signaling server must be running (see [`../signaling-server/README.md`](../signaling-server/README.md))

### Building

1. Open the project in Android Studio
2. Sync Gradle files
3. Build and run on device or emulator

### Configuration

The app requires the signaling server URL. You can configure this in the main screen:

1. Enter server URL (e.g., `192.168.1.10:8001` for local or ngrok URL for remote)
2. Enter a call ID (shared between participants)
3. Select source and target languages
4. Tap "Start Call"

## Usage

### Starting a Call

1. Launch the app
2. Configure server URL and call ID
3. Select your language (source) and the other person's language (target)
4. Tap "Start Call"
5. Grant microphone permission if prompted

### During a Call

- **Mute**: Tap "Mute" to stop sending audio
- **Speaker**: Tap "Speaker" to toggle speakerphone
- **End Call**: Tap the red "End Call" button

> [!NOTE]
> Language selection is currently used for user identification. Translation features will be added in Phase 2.

## Architecture Details

### Data Layer

- **Models**: Immutable data classes representing app entities
- **Repositories**: Interfaces for data operations (preferences, call management)

### Audio Layer

- **CallManager**: Manages WebSocket connection and audio streaming
- **VoiceActivityDetector**: Detects speech vs silence to reduce false transcriptions
- **WavRecorder**: Records audio to WAV format

### UI Layer

- **MainActivity**: Language selection and configuration
- **CallActivity**: Call screen with real-time transcription display

### Constants

All hardcoded values are centralized in `Constants.kt`:
- Audio configuration (sample rate, chunk sizes)
- VAD parameters
- Network configuration
- Logging tags

## Supported Languages

- English
- Hindi
- Marathi
- Bengali
- Gujarati
- Kannada
- Malayalam
- Punjabi
- Tamil
- Telugu

## Permissions

The app requires the following permissions:
- `RECORD_AUDIO`: For capturing voice input
- `INTERNET`: For WebSocket communication

## Troubleshooting

### No Audio Transmission

- Check microphone permission
- Verify backend URL is correct
- Ensure both devices use the same call ID

### Poor Transcription Quality

- Use Push-to-Talk mode in noisy environments
- Speak clearly and at moderate pace
- Ensure good network connection

### Connection Issues

- Verify backend server is running
- Check firewall settings
- Ensure devices are on the same network (for local testing)

## Development

### Adding New Languages

1. Add language to `Language.SUPPORTED_LANGUAGES` in `Language.kt`
2. Ensure backend has corresponding translation model

### Customizing Audio Parameters

Edit values in `Constants.Audio`:
- `SAMPLE_RATE`: Audio sample rate (default: 16000 Hz)
- `CHUNK_SIZE`: Capture chunk size
- `SEND_THRESHOLD`: Buffer size for transcription

## License

See LICENSE file in the root directory.
