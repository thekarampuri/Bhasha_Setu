import os
import asyncio
import wave
import uuid
import time
from fastapi import FastAPI, WebSocket, WebSocketDisconnect
from fastapi.middleware.cors import CORSMiddleware
from stt import transcribe_and_translate
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

    async def broadcast_json(self, data: dict, call_id: str):
        if call_id in rooms:
            for uid, ws in rooms[call_id].items():
                try:
                    await ws.send_json(data)
                except:
                    pass

    async def relay_audio(self, data: bytes, call_id: str, sender_id: str):
        if call_id in rooms:
            for uid, ws in rooms[call_id].items():
                if uid != sender_id:
                    try:
                        await ws.send_bytes(data)
                    except:
                        pass

manager = ConnectionManager()

# Ensure temp directory exists
TEMP_DIR = "temp_audio"
os.makedirs(TEMP_DIR, exist_ok=True)

async def process_stt(audio_data: bytes, call_id: str, source_lang: str, target_lang: str):
    """Background task to handle STT and Translation with unique filenames for Windows safety"""
    
    # Validate minimum chunk size (at least 0.5s of audio at 16kHz, 16-bit)
    MIN_CHUNK_SIZE = 16000  # 0.5s * 16000 samples/s * 2 bytes/sample = 16000 bytes
    if len(audio_data) < MIN_CHUNK_SIZE:
        print(f"⏭️ Skipping chunk: too small ({len(audio_data)} bytes)")
        return
    
    # Use unique filename to avoid WinError 32 (file in use)
    task_id = uuid.uuid4().hex
    temp_filename = os.path.join(TEMP_DIR, f"stt_{call_id}_{source_lang}_{task_id}.wav")
    
    try:
        # Write WAV file
        with wave.open(temp_filename, 'wb') as wf:
            wf.setnchannels(1)
            wf.setsampwidth(2)
            wf.setframerate(16000)
            wf.writeframes(audio_data)

        # Run model in a thread - PASS call_id for duplicate detection
        loop = asyncio.get_event_loop()
        result = await loop.run_in_executor(
            None, 
            transcribe_and_translate, 
            temp_filename, 
            source_lang, 
            target_lang,
            call_id  # Add call_id parameter
        )

        if result["success"] and result["source_text"]:
            print(f"📝 [{call_id}] {source_lang}: {result['source_text']} -> {target_lang}: {result['translated_text']}")
            await manager.broadcast_json({
                "type": "transcription",
                "source": result["source_text"],
                "translated": result["translated_text"],
                "sender": source_lang
            }, call_id)
            
    except Exception as e:
        print(f"STT Error: {e}")
    finally:
        # Give Whisper a tiny moment to release the file handle and try to delete safely
        asyncio.create_task(safe_delete(temp_filename))

async def safe_delete(filepath: str, delay: int = 1):
    """Attempt to delete a file after a short delay to handle Windows file locking"""
    await asyncio.sleep(delay)
    try:
        if os.path.exists(filepath):
            os.remove(filepath)
    except Exception as e:
        print(f"Cleanup Error for {filepath}: {e}")

@app.websocket("/ws/call/{call_id}/{source_lang}/{target_lang}")
async def websocket_endpoint(websocket: WebSocket, call_id: str, source_lang: str, target_lang: str):
    user_id = source_lang 
    await manager.connect(websocket, call_id, user_id)
    
    audio_buffer = bytearray()
    # Process every 2-3 seconds of audio for better accuracy
    # 16kHz * 2 bytes/sample * 2.5 seconds = 80000 bytes
    THRESHOLD = 80000

    try:
        while True:
            data = await websocket.receive_bytes()
            # 1. Immediate relay
            await manager.relay_audio(data, call_id, user_id)
            
            # 2. Accumulate for STT
            audio_buffer.extend(data)
            if len(audio_buffer) >= THRESHOLD:
                chunk = bytes(audio_buffer)
                audio_buffer.clear()  # Clear buffer to prevent contamination
                # Use a task to process in background
                asyncio.create_task(process_stt(chunk, call_id, source_lang, target_lang))
                
    except WebSocketDisconnect:
        manager.disconnect(call_id, user_id)
    except Exception as e:
        print(f"Error: {e}")
        manager.disconnect(call_id, user_id)

if __name__ == "__main__":
    uvicorn.run(app, host="0.0.0.0", port=8000)
