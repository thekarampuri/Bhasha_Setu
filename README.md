# VoiceTranslate (Bhasha Setu)

A real-time bidirectional voice translation system that breaks language barriers through seamless communication.

## Overview

**Bhasha Setu** (Bridge of Languages) enables real-time voice communication between people speaking different languages. The system combines speech recognition, translation, and audio streaming to provide a natural conversation experience.

## Architecture

The project follows a **client-server architecture** with clean separation of concerns:

### Backend (Python/FastAPI)

```
backend/
├── config.py              # Centralized configuration
├── models.py              # Pydantic data models
├── main.py                # FastAPI application
├── services/              # Business logic layer
│   ├── audio_service.py
│   ├── stt_service.py
│   ├── translation_service.py
│   └── websocket_service.py
└── utils/                 # Shared utilities
```

**Key Features:**
- Modular service-based architecture
- Environment-based configuration
- Dynamic Voice Activity Detection
- Hallucination filtering for Whisper
- Duplicate transcript suppression

### Frontend (Android/Kotlin)

```
app/src/main/java/com/example/voicetranslate/
├── data/                  # Data layer (models, repositories)
├── audio/                 # Audio processing
├── util/                  # Constants and utilities
├── MainActivity.kt        # Configuration screen
└── CallActivity.kt        # Call screen
```

**Key Features:**
- Clean architecture with data/domain/presentation layers
- Repository pattern for data access
- Centralized constants
- Real-time audio streaming
- Push-to-Talk mode for noisy environments

## Technology Stack

### Backend
- **Framework**: FastAPI
- **WebSocket**: Native FastAPI WebSocket support
- **STT**: faster-whisper (Whisper model)
- **Translation**: Helsinki-NLP MarianMT models
- **Configuration**: Pydantic Settings
- **Audio Processing**: NumPy, wave

### Frontend
- **Language**: Kotlin
- **Audio**: Android AudioRecord/AudioTrack APIs
- **WebSocket**: OkHttp WebSocket
- **UI**: View Binding, Material Components

## Features

### ✅ Implemented
- Real-time bidirectional voice communication
- Speech-to-text transcription (Whisper)
- Multi-language translation (10+ languages)
- Dynamic Voice Activity Detection
- Push-to-Talk mode
- Duplicate suppression
- Hallucination filtering
- Environment-based configuration
- Modular, maintainable architecture

### 🚧 Future Enhancements
- Text-to-Speech output
- Group calling support
- End-to-end encryption
- Cloud deployment
- Mobile app for iOS
- Web client

## Supported Languages

- English (en)
- Hindi (hi)
- Marathi (mr)
- Bengali (bn)
- Gujarati (gu)
- Kannada (kn)
- Malayalam (ml)
- Punjabi (pa)
- Tamil (ta)
- Telugu (te)

## Quick Start

### Backend Setup

```bash
cd android-app/VoiceTranslate/backend

# Install dependencies
pip install -r requirements.txt

# Configure environment (optional)
cp .env.example .env

# Run server
python main.py
```

Server runs on `http://0.0.0.0:8000`

### Android App Setup

1. Open `android-app/VoiceTranslate` in Android Studio
2. Build and run on device/emulator
3. Configure backend URL in the app
4. Select languages and start call

## Usage

### Two-Device Setup

**Device 1:**
1. Enter backend URL (e.g., `192.168.1.10:8000`)
2. Enter call ID (e.g., `room123`)
3. Select source language: English
4. Select target language: Hindi
5. Tap "Start Call"

**Device 2:**
1. Enter same backend URL
2. Enter same call ID: `room123`
3. Select source language: Hindi
4. Select target language: English
5. Tap "Start Call"

Now both users can speak in their native language and hear translations in real-time!

## Configuration

### Backend Configuration

Edit `.env` file or set environment variables:

```bash
# Whisper model size (tiny, base, small, medium, large)
WHISPER__MODEL_SIZE=small

# Audio buffer duration (ms)
AUDIO__BUFFER_THRESHOLD_DURATION_MS=2500

# VAD threshold
VAD__BASE_THRESHOLD=0.003

# Server port
SERVER__PORT=8000
```

### Android Configuration

Edit `Constants.kt` for audio parameters:

```kotlin
object Audio {
    const val SAMPLE_RATE = 16000
    const val CHUNK_SIZE = 6400
    const val SEND_THRESHOLD = 80000
}
```

## Architecture Improvements (v2.0)

This version includes a complete architecture refactoring:

### Backend
- ✅ Modular service layer (audio, STT, translation, WebSocket)
- ✅ Centralized configuration with Pydantic
- ✅ Environment variable support
- ✅ Structured logging
- ✅ Proper error handling
- ✅ Clean separation of concerns

### Android
- ✅ Data layer with models and repositories
- ✅ Centralized constants
- ✅ Repository pattern for preferences
- ✅ Improved code organization
- ✅ Better separation of UI and business logic

## Project Status

- ✅ Real-time voice call implemented
- ✅ WebSocket communication established
- ✅ AI translation modules active
- ✅ Architecture refactored for maintainability
- ✅ Production-ready configuration system

## Development

### Backend Development

```bash
# Install dev dependencies
pip install -r requirements.txt

# Run with auto-reload
uvicorn main:app --reload --host 0.0.0.0 --port 8000
```

### Android Development

1. Open in Android Studio
2. Enable auto-import for Gradle
3. Use Android Emulator or physical device
4. Check logcat for debugging

## Troubleshooting

See individual README files:
- [Backend README](android-app/VoiceTranslate/backend/README.md)
- [Android README](android-app/VoiceTranslate/README.md)

## Contributing

This project is developed for academic and learning purposes.

## Author

**Akhil K**

## License

See LICENSE file for details.

---

**Note**: This is an educational project demonstrating real-time voice translation using modern AI/ML techniques and clean software architecture principles.
