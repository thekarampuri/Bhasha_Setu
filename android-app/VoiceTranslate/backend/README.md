# Bhasha Setu Backend

Real-time voice translation backend service built with FastAPI and WebSockets.

## Features

- **Real-time Audio Streaming**: WebSocket-based bidirectional audio communication
- **Speech-to-Text**: Whisper model integration for accurate transcription
- **Translation**: Helsinki-NLP translation models for multiple language pairs
- **Voice Activity Detection**: Dynamic VAD with adaptive thresholds
- **Modular Architecture**: Clean separation of concerns with service layer pattern

## Architecture

```
backend/
├── config.py              # Configuration management with Pydantic
├── models.py              # Data models for requests/responses
├── main.py                # FastAPI application and endpoints
├── services/              # Business logic layer
│   ├── audio_service.py   # Audio processing and VAD
│   ├── stt_service.py     # Speech-to-text with Whisper
│   ├── translation_service.py  # Translation with MarianMT
│   └── websocket_service.py    # WebSocket connection management
└── utils/                 # Utility functions
    ├── logger.py          # Centralized logging
    └── file_utils.py      # File operations
```

## Setup

### Prerequisites

- Python 3.8+
- pip

### Installation

1. Install dependencies:
```bash
pip install -r requirements.txt
```

2. Create environment configuration:
```bash
cp .env.example .env
```

3. Edit `.env` to configure your settings (optional - defaults work for development)

### Running the Server

```bash
python main.py
```

The server will start on `http://0.0.0.0:8000` by default.

## Configuration

All configuration is managed through environment variables. See `.env.example` for available options.

### Key Configuration Options

- `WHISPER__MODEL_SIZE`: Whisper model size (tiny, base, small, medium, large)
- `AUDIO__BUFFER_THRESHOLD_DURATION_MS`: Audio buffer duration for transcription
- `VAD__BASE_THRESHOLD`: Voice activity detection threshold
- `SERVER__PORT`: Server port (default: 8000)

## API Endpoints

### WebSocket

**`/ws/call/{call_id}/{source_lang}/{target_lang}`**

Real-time audio streaming and translation endpoint.

- `call_id`: Unique identifier for the call session
- `source_lang`: Source language code (e.g., "en", "hi", "mr")
- `target_lang`: Target language code

### HTTP

**`GET /`** - Service information and active rooms

**`GET /health`** - Health check endpoint

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

## Development

### Project Structure

The backend follows a clean architecture pattern:

1. **Config Layer** (`config.py`): Centralized configuration management
2. **Model Layer** (`models.py`): Data validation with Pydantic
3. **Service Layer** (`services/`): Business logic implementation
4. **API Layer** (`main.py`): FastAPI endpoints and routing
5. **Utils Layer** (`utils/`): Shared utilities

### Adding New Services

1. Create a new service file in `services/`
2. Implement the service class with business logic
3. Import and initialize in `main.py`
4. Use dependency injection for service dependencies

## Troubleshooting

### Model Download Issues

If translation models fail to download, check your internet connection and ensure you have sufficient disk space.

### Audio Processing Issues

- Adjust `VAD__BASE_THRESHOLD` if speech is not being detected
- Increase `AUDIO__BUFFER_THRESHOLD_DURATION_MS` for longer sentences
- Check `WHISPER__MODEL_SIZE` - larger models are more accurate but slower

## License

See LICENSE file in the root directory.
