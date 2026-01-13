# WebRTC Signaling Server

Minimal WebSocket server for WebRTC signaling (SDP/ICE relay only).

## Features

- ✅ WebSocket-based signaling
- ✅ 1-to-1 calls only (max 2 users per room)
- ✅ SDP offer/answer relay
- ✅ ICE candidate relay
- ✅ Auto cleanup on disconnect
- ❌ No media handling (P2P only)

## Setup

### Install Dependencies
```bash
pip install -r requirements.txt
```

### Run Server
```bash
python main.py
```

Server runs on `http://0.0.0.0:8001`

## API

### Health Check
```
GET /
```

Response:
```json
{
  "status": "running",
  "service": "WebRTC Signaling Server",
  "active_rooms": 0
}
```

### WebSocket Endpoint
```
ws://server:8001/ws/{call_id}/{user_id}
```

**Parameters**:
- `call_id`: Room identifier (e.g., "call123")
- `user_id`: User UUID (e.g., "a1b2c3d4-...")

**Example**:
```
ws://localhost:8001/ws/call123/a1b2c3d4-e5f6-7890-abcd-ef1234567890
```

## Message Protocol

### Client → Server → Peer

#### Offer
```json
{
  "type": "offer",
  "callId": "call123",
  "sdp": "v=0\r\no=- ..."
}
```

#### Answer
```json
{
  "type": "answer",
  "callId": "call123",
  "sdp": "v=0\r\no=- ..."
}
```

#### ICE Candidate
```json
{
  "type": "ice-candidate",
  "callId": "call123",
  "candidate": {
    "candidate": "candidate:1 1 UDP ...",
    "sdpMid": "0",
    "sdpMLineIndex": 0
  }
}
```

### Server → Client

#### Peer Joined
```json
{
  "type": "peer-joined",
  "callId": "call123",
  "peerId": "f9e8d7c6-..."
}
```

#### Peer Left
```json
{
  "type": "peer-left",
  "callId": "call123",
  "peerId": "f9e8d7c6-..."
}
```

## Room Management

- **Room Creation**: Automatic when first user joins
- **Room Capacity**: Maximum 2 users (1-to-1 constraint)
- **Room Deletion**: Automatic when empty
- **Third User**: Rejected with "Room full" error

## Logging

```
✅ User joined room
📨 Message type received
📤 Message relayed to peer
📢 Peer notified
❌ User disconnected
🗑️ Room/user cleanup
```

## Testing

### Using websocat (CLI tool)
```bash
# Terminal 1 (User A)
websocat ws://localhost:8001/ws/call123/user_a

# Terminal 2 (User B)
websocat ws://localhost:8001/ws/call123/user_b

# Send messages as JSON
{"type": "offer", "callId": "call123", "sdp": "test"}
```

### Using Browser Console
```javascript
const ws = new WebSocket('ws://localhost:8001/ws/call123/user_a');
ws.onmessage = (e) => console.log('Received:', e.data);
ws.send(JSON.stringify({type: 'offer', callId: 'call123', sdp: 'test'}));
```

## Production Deployment

### Using ngrok (Development)
```bash
ngrok http 8001
```

Use the ngrok URL in the Android app's server URL field (e.g., `abc123.ngrok.io:443`)

### Using Docker
```dockerfile
FROM python:3.11-slim
WORKDIR /app
COPY requirements.txt .
RUN pip install -r requirements.txt
COPY main.py .
CMD ["python", "main.py"]
```

### Environment Variables (Optional)
```bash
export HOST=0.0.0.0
export PORT=8001
```

## Architecture

```
Client A ──► WebSocket ──► Signaling Server ──► WebSocket ──► Client B
         (SDP/ICE)                           (SDP/ICE)
         
         Audio: Client A ◄═══ P2P RTP ═══► Client B
                (Direct connection, no server)
```

## Limitations

- No authentication (Phase 1)
- No encryption (use wss:// in production)
- No persistence (in-memory rooms)
- No scaling (single server instance)

## Next Steps

- Add authentication (Firebase or JWT)
- Add SSL/TLS (wss://)
- Add Redis for multi-server deployment
- Add rate limiting
- Add metrics/monitoring
