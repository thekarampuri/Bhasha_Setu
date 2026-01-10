import os
import json
import asyncio
import wave
import threading
from fastapi import FastAPI, WebSocket, WebSocketDisconnect
from stt import transcribe_and_translate
from gtts import gTTS
from io import BytesIO
from pydub import AudioSegment

app = FastAPI()

# Room structure: { call_id: [websocket1, websocket2] }
rooms = {}

class ConnectionManager:
    async def connect(self, websocket: WebSocket, call_id: str):
        await websocket.accept()
        if call_id not in rooms:
            rooms[call_id] = []
        rooms[call_id].append(websocket)
        print(f"✅ User connected to room: {call_id}")

    def disconnect(self, websocket: WebSocket, call_id: str):
        if call_id in rooms:
            if websocket in rooms[call_id]:
                rooms[call_id].remove(websocket)
            if not rooms[call_id]:
                del rooms[call_id]
        print(f"❌ User disconnected from room: {call_id}")

    async def send_audio_to_peer(self, audio_data: bytes, sender_ws: WebSocket, call_id: str):
        if call_id in rooms:
            for connection in rooms[call_id]:
                if connection != sender_ws:
                    try:
                        await connection.send_bytes(audio_data)
                    except Exception as e:
                        print(f"Error relaying to peer: {e}")

manager = ConnectionManager()

def text_to_pcm_bytes(text, lang):
    """Converts text to raw PCM 16kHz Mono 16-bit bytes"""
    try:
        tts = gTTS(text=text, lang=lang)
        mp3_fp = BytesIO()
        tts.write_to_fp(mp3_fp)
        mp3_fp.seek(0)
        audio = AudioSegment.from_file(mp3_fp, format="mp3")
        audio = audio.set_frame_rate(16000).set_channels(1).set_sample_width(2)
        return audio.raw_data
    except Exception as e:
        print(f"TTS Error: {e}")
        return b""

async def process_audio_chunk(audio_data, call_id, source_lang, target_lang, websocket):
    """Handles STT -> Translate -> TTS in a non-blocking way"""
    temp_filename = f"temp_{call_id}_{source_lang}.wav"
    
    # Save raw PCM to WAV using built-in wave module (no ffmpeg needed for this part)
    with wave.open(temp_filename, 'wb') as wf:
        wf.setnchannels(1)
        wf.setsampwidth(2) # 16-bit
        wf.setframerate(16000)
        wf.writeframes(audio_data)

    # Run Whisper & Translation (This is CPU intensive)
    # We run it in a thread to avoid blocking the event loop
    loop = asyncio.get_event_loop()
    result = await loop.run_in_executor(None, transcribe_and_translate, temp_filename, source_lang, target_lang)
    
    if result["success"] and result["source_text"]:
        print(f"🎤 [{call_id}] {source_lang}: '{result['source_text']}' -> {target_lang}: '{result['translated_text']}'")
        
        # Send text back to sender
        await websocket.send_json({
            "source": result["source_text"],
            "translated": result["translated_text"]
        })

        # TTS and Relay
        translated_audio = text_to_pcm_bytes(result["translated_text"], target_lang)
        if translated_audio:
            await manager.send_audio_to_peer(translated_audio, websocket, call_id)
    else:
        print(f"⏳ [{call_id}] No speech detected in chunk.")

    if os.path.exists(temp_filename):
        os.remove(temp_filename)

@app.websocket("/ws/call/{call_id}/{source_lang}/{target_lang}")
async def websocket_endpoint(websocket: WebSocket, call_id: str, source_lang: str, target_lang: str):
    await manager.connect(websocket, call_id)
    audio_buffer = bytearray()
    
    # 1.5 seconds of audio (16000 * 2 * 1.5 = 48000 bytes)
    # Lower threshold = faster response but less context for Whisper
    CHUNK_THRESHOLD = 48000 

    try:
        while True:
            data = await websocket.receive_bytes()
            audio_buffer.extend(data)

            if len(audio_buffer) >= CHUNK_THRESHOLD:
                # Copy buffer and clear it immediately so we can keep receiving
                chunk_to_process = bytes(audio_buffer)
                audio_buffer.clear()
                
                # Start processing in background task
                asyncio.create_task(process_audio_chunk(chunk_to_process, call_id, source_lang, target_lang, websocket))

    except WebSocketDisconnect:
        manager.disconnect(websocket, call_id)
    except Exception as e:
        print(f"WebSocket Error: {e}")
        manager.disconnect(websocket, call_id)

if __name__ == "__main__":
    import uvicorn
    uvicorn.run(app, host="0.0.0.0", port=8000)
