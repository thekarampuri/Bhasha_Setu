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

rooms = {}

class ConnectionManager:
    async def connect(self, websocket: WebSocket, call_id: str, user_id: str):
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
            for uid, ws in rooms[call_id].items():
                if uid != sender_id:
                    try:
                        await ws.send_bytes(data)
                    except:
                        pass

manager = ConnectionManager()

# UPDATED: Path now accepts source and target lang to match your app's request
@app.websocket("/ws/call/{call_id}/{source_lang}/{target_lang}")
async def websocket_endpoint(websocket: WebSocket, call_id: str, source_lang: str, target_lang: str):
    # We use source_lang as the unique user_id for this simple relay test
    user_id = source_lang 
    await manager.connect(websocket, call_id, user_id)
    try:
        while True:
            data = await websocket.receive_bytes()
            await manager.relay_audio(data, call_id, user_id)
    except WebSocketDisconnect:
        manager.disconnect(call_id, user_id)
    except Exception as e:
        print(f"Error: {e}")
        manager.disconnect(call_id, user_id)

if __name__ == "__main__":
    uvicorn.run(app, host="0.0.0.0", port=8000)
