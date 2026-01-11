# Signaling Server

WebSocket-based audio relay service for Bhasha Setu real-time voice translation system.

## Overview

The signaling server is a lightweight FastAPI application that manages WebSocket connections and relays audio streams between call participants. It operates as a simple room-based relay without performing any speech processing.

## Features

- **Room-based Audio Relay**: Participants join rooms using a `call_id` and audio is relayed to all other participants in the same room
- **WebSocket Communication**: Real-time bidirectional audio streaming
- **Connection Management**: Automatic cleanup when participants disconnect
- **CORS Enabled**: Supports cross-origin requests for web clients

## Architecture

```
Client A ──► WebSocket ──► Signaling Server ──► WebSocket ──► Client B
         (audio chunks)                      (relayed audio)
```

The server:
1. Accepts WebSocket connections at `/ws/call/{call_id}/{source_lang}/{target_lang}`
2. Groups connections by `call_id` into rooms
3. Relays incoming audio bytes to all other participants in the same room
4. Tracks connection statistics and logs activity

## Setup

### Prerequisites

- Python 3.8+
- pip

### Installation

1. Install dependencies:
```bash
pip install -r requirements.txt
```

2. Run the server:
```bash
python main.py
```

The server will start on `http://0.0.0.0:8000`

## API Endpoints

### GET `/`

Health check endpoint that returns server status.

**Response:**
```json
{
  "status": "running",
  "service": "Bhasha Setu Audio Relay",
  "version": "1.0.0",
  "active_rooms": 0
}
```

### WebSocket `/ws/call/{call_id}/{source_lang}/{target_lang}`

WebSocket endpoint for audio streaming.

**Parameters:**
- `call_id`: Unique identifier for the call room
- `source_lang`: Source language code (also used as user_id)
- `target_lang`: Target language code

**Usage:**
```javascript
const ws = new WebSocket('ws://localhost:8000/ws/call/room123/en/hi');
ws.binaryType = 'arraybuffer';

// Send audio chunks
ws.send(audioData);

// Receive relayed audio
ws.onmessage = (event) => {
  const audioData = event.data;
  // Play audio
};
```

## Configuration

The server can be configured by modifying `main.py`:

- **Host**: Default `0.0.0.0` (all interfaces)
- **Port**: Default `8000`
- **CORS**: Currently allows all origins (`*`)

For production deployment, create a `.env` file based on `.env.example` (to be added).

## Logging

The server logs:
- Connection events (join/leave)
- Room statistics
- Audio relay activity (periodic)
- Errors and disconnections

## Development

### Project Structure

```
signaling-server/
├── main.py              # FastAPI application
├── requirements.txt     # Python dependencies
├── README.md           # This file
└── .env.example        # Environment template (to be added)
```

### Future Enhancements

- Environment-based configuration
- Authentication and authorization
- Rate limiting
- Metrics and monitoring
- Docker support

## Related Components

- **Android App**: `../android/` - Mobile client
- **Media Server**: Phase 2 - Future STT/TTS/Translation services
- **Documentation**: `../docs/` - Architecture and flow diagrams

## License

See the root LICENSE file for details.
