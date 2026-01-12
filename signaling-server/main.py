"""
WebRTC Signaling Server - Minimal Implementation

Responsibilities:
- Relay SDP offers/answers between peers
- Relay ICE candidates between peers
- Manage 1-to-1 call rooms (max 2 users)
- Auto cleanup on disconnect

No media handling - only signaling.
"""

from fastapi import FastAPI, WebSocket, WebSocketDisconnect
from fastapi.middleware.cors import CORSMiddleware
import json
import uvicorn
from typing import Dict, Optional

app = FastAPI(title="Bhasha Setu Signaling Server")

# CORS for web clients
app.add_middleware(
    CORSMiddleware,
    allow_origins=["*"],
    allow_credentials=True,
    allow_methods=["*"],
    allow_headers=["*"],
)

# Room storage: {call_id: {user_id: WebSocket}}
rooms: Dict[str, Dict[str, WebSocket]] = {}


@app.get("/")
async def health_check():
    """Health check endpoint"""
    return {
        "status": "running",
        "service": "WebRTC Signaling Server",
        "active_rooms": len(rooms)
    }


@app.websocket("/ws/{call_id}/{user_id}")
async def websocket_endpoint(websocket: WebSocket, call_id: str, user_id: str):
    """
    WebSocket endpoint for signaling
    
    URL: ws://server/ws/{call_id}/{user_id}
    - call_id: Room identifier
    - user_id: Unique user identifier (UUID)
    """
    
    from datetime import datetime
    
    def log(msg: str):
        timestamp = datetime.now().strftime("%H:%M:%S.%f")[:-3]
        print(f"[{timestamp}] {msg}")
    
    # Check room capacity (max 2 users)
    if call_id in rooms and len(rooms[call_id]) >= 2:
        await websocket.close(code=1008, reason="Room full")
        log(f"❌ Room {call_id} is full, rejected {user_id[:8]}")
        return
    
    # Accept connection
    await websocket.accept()
    
    # Create room if doesn't exist
    if call_id not in rooms:
        rooms[call_id] = {}
    
    # Determine if this user is the initiator (first to join)
    is_initiator = len(rooms[call_id]) == 0
    
    # Add user to room
    rooms[call_id][user_id] = websocket
    log(f"✅ User {user_id[:8]} joined room {call_id} ({len(rooms[call_id])}/2) - {'INITIATOR' if is_initiator else 'CALLEE'}")
    
    # Notify peer if they exist
    peer_id = get_peer_id(call_id, user_id)
    if peer_id:
        await send_to_peer(call_id, user_id, {
            "type": "peer-joined",
            "callId": call_id,
            "peerId": user_id
        })
        log(f"📢 Notified {peer_id[:8]} that {user_id[:8]} joined")
    
    try:
        # Message relay loop
        while True:
            # Receive message from client
            data = await websocket.receive_text()
            message = json.loads(data)
            
            msg_type = message.get("type")
            log(f"📨 [{user_id[:8]}] → {msg_type}")
            
            # Relay message to peer
            if msg_type in ["offer", "answer", "ice-candidate"]:
                await send_to_peer(call_id, user_id, message)
            
    except WebSocketDisconnect:
        log(f"❌ User {user_id[:8]} disconnected from room {call_id}")
    except Exception as e:
        log(f"⚠️ Error for {user_id[:8]}: {e}")
    finally:
        # Cleanup on disconnect
        cleanup_user(call_id, user_id)


def get_peer_id(call_id: str, user_id: str) -> Optional[str]:
    """Get the peer's user ID in the room"""
    if call_id not in rooms:
        return None
    
    for uid in rooms[call_id].keys():
        if uid != user_id:
            return uid
    
    return None


async def send_to_peer(call_id: str, sender_id: str, message: dict):
    """Send message to the peer (not the sender)"""
    peer_id = get_peer_id(call_id, sender_id)
    
    if peer_id and call_id in rooms and peer_id in rooms[call_id]:
        peer_ws = rooms[call_id][peer_id]
        try:
            await peer_ws.send_text(json.dumps(message))
            from datetime import datetime
            timestamp = datetime.now().strftime("%H:%M:%S.%f")[:-3]
            print(f"[{timestamp}] 📤 Relayed {message.get('type')} from {sender_id[:8]} to {peer_id[:8]}")
        except Exception as e:
            print(f"⚠️ Failed to send to {peer_id[:8]}: {e}")


def cleanup_user(call_id: str, user_id: str):
    """Remove user from room and notify peer"""
    if call_id in rooms:
        # Remove user
        if user_id in rooms[call_id]:
            del rooms[call_id][user_id]
            print(f"🗑️ Removed {user_id} from room {call_id}")
        
        # Notify peer
        peer_id = get_peer_id(call_id, user_id)
        if peer_id:
            try:
                peer_ws = rooms[call_id][peer_id]
                # Use asyncio to send in sync context
                import asyncio
                asyncio.create_task(peer_ws.send_text(json.dumps({
                    "type": "peer-left",
                    "callId": call_id,
                    "peerId": user_id
                })))
                print(f"📢 Notified {peer_id} that {user_id} left")
            except Exception as e:
                print(f"⚠️ Failed to notify peer: {e}")
        
        # Delete room if empty
        if not rooms[call_id]:
            del rooms[call_id]
            print(f"🗑️ Deleted empty room {call_id}")


if __name__ == "__main__":
    print("=" * 60)
    print("WebRTC Signaling Server")
    print("=" * 60)
    print("Mode: SDP/ICE relay only (no media)")
    print("Rooms: 1-to-1 (max 2 users)")
    print("Host: 0.0.0.0")
    print("Port: 8001")
    print("=" * 60)
    uvicorn.run(app, host="0.0.0.0", port=8001)

