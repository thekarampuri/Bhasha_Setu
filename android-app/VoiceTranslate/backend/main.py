from fastapi import FastAPI, WebSocket, WebSocketDisconnect
from fastapi.middleware.cors import CORSMiddleware
import asyncio

app = FastAPI()

# 1. Allow all origins for WebSocket and REST
app.add_middleware(
    CORSMiddleware,
    allow_origins=["*"],
    allow_credentials=True,
    allow_methods=["*"],
    allow_headers=["*"],
)

# Store connections: { call_id: { user_id: websocket } }
rooms = {}

class ConnectionManager:
    async def connect(self, websocket: WebSocket, call_id: str, user_id: str):
        # Explicitly accept the connection
        await websocket.accept()
        if call_id not in rooms:
            rooms[call_id] = {}
        rooms[call_id][user_id] = websocket
        print(f"✅ User {user_id} joined room {call_id}")

    def disconnect(self, call_id: str, user_id: str):
        if call_id in rooms:
            if user_id in rooms[call_id]:
                del rooms[call_id][user_id]
            if not rooms[call_id]:
                del rooms[call_id]
        print(f"❌ User {user_id} left room {call_id}")

    async def relay_audio(self, data: bytes, call_id: str, sender_id: str):
        if call_id in rooms:
            for user_id, websocket in rooms[call_id].items():
                if user_id != sender_id:
                    try:
                        await websocket.send_bytes(data)
                    except Exception as e:
                        print(f"Error relaying to {user_id}: {e}")

manager = ConnectionManager()

# Path MUST match exactly: /ws/call/{call_id}/{user_id}
@app.websocket("/ws/call/{call_id}/{user_id}")
async def websocket_endpoint(websocket: WebSocket, call_id: str, user_id: str):
    await manager.connect(websocket, call_id, user_id)
    try:
        while True:
            # Receive raw PCM bytes from Android
            data = await websocket.receive_bytes()
            # Immediately relay to peer
            await manager.relay_audio(data, call_id, user_id)
    except WebSocketDisconnect:
        manager.disconnect(call_id, user_id)
    except Exception as e:
        print(f"WebSocket Error for {user_id}: {e}")
        manager.disconnect(call_id, user_id)

if __name__ == "__main__":
    import uvicorn
    # Listen on all interfaces (0.0.0.0) so physical devices can connect
    uvicorn.run(app, host="0.0.0.0", port=8000)
