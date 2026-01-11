import asyncio
from fastapi import FastAPI, WebSocket, WebSocketDisconnect
from fastapi.middleware.cors import CORSMiddleware
import uvicorn

app = FastAPI()

app.add_middleware(
    CORSMiddleware,
    allow_origins=["*"],
    allow_credentials=True,
    allow_methods=["*"],
    allow_headers=["*"],
)

# Simple room-based connection manager
rooms = {}

class ConnectionManager:
    async def connect(self, websocket: WebSocket, call_id: str, user_id: str):
        await websocket.accept()
        if call_id not in rooms:
            rooms[call_id] = {}
        rooms[call_id][user_id] = websocket
        print(f"✅ User {user_id} joined room {call_id}")
        print(f"📊 Room {call_id} now has {len(rooms[call_id])} user(s)")

    def disconnect(self, call_id: str, user_id: str):
        if call_id in rooms:
            if user_id in rooms[call_id]:
                del rooms[call_id][user_id]
                print(f"❌ User {user_id} left room {call_id}")
            if not rooms[call_id]:
                del rooms[call_id]
                print(f"🗑️ Room {call_id} deleted (empty)")

    async def relay_audio(self, data: bytes, call_id: str, sender_id: str):
        """Relay audio to all other users in the room"""
        if call_id in rooms:
            recipient_count = 0
            for uid, ws in rooms[call_id].items():
                if uid != sender_id:
                    try:
                        await ws.send_bytes(data)
                        recipient_count += 1
                    except Exception as e:
                        print(f"⚠️ Failed to send to {uid}: {e}")
            return recipient_count
        return 0

manager = ConnectionManager()

@app.get("/")
async def root():
    return {
        "status": "running",
        "service": "Bhasha Setu Audio Relay",
        "version": "1.0.0",
        "active_rooms": len(rooms)
    }

@app.websocket("/ws/call/{call_id}/{source_lang}/{target_lang}")
async def websocket_endpoint(websocket: WebSocket, call_id: str, source_lang: str, target_lang: str):
    user_id = source_lang 
    await manager.connect(websocket, call_id, user_id)
    
    print(f"🔌 WebSocket connected: call_id={call_id}, user_id={user_id}, source={source_lang}, target={target_lang}")
    
    total_bytes_received = 0
    chunk_count = 0
    
    try:
        while True:
            data = await websocket.receive_bytes()
            total_bytes_received += len(data)
            chunk_count += 1
            
            # Log every 50 chunks (~5 seconds at 100ms chunks)
            if chunk_count % 50 == 0:
                print(f"📡 [{user_id}] Received {chunk_count} chunks, {total_bytes_received:,} bytes total")
            
            # Relay audio to other participants
            recipients = await manager.relay_audio(data, call_id, user_id)
            
            # Log first few relays to confirm it's working
            if chunk_count <= 5:
                print(f"🔊 Relayed {len(data)} bytes from {user_id} to {recipients} recipient(s)")
                
    except WebSocketDisconnect:
        print(f"❌ WebSocket disconnected: call_id={call_id}, user_id={user_id}")
        print(f"📊 Session stats: {chunk_count} chunks, {total_bytes_received:,} bytes received")
        manager.disconnect(call_id, user_id)
    except Exception as e:
        print(f"❌ Error in WebSocket: {e}")
        import traceback
        traceback.print_exc()
        manager.disconnect(call_id, user_id)

if __name__ == "__main__":
    print("=" * 60)
    print("Bhasha Setu - Audio Relay Server")
    print("=" * 60)
    print("Mode: Audio relay only (no STT/TTS)")
    print("Host: 0.0.0.0")
    print("Port: 8000")
    print("=" * 60)
    uvicorn.run(app, host="0.0.0.0", port=8000)
