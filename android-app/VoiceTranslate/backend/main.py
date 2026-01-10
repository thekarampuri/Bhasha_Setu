import os
import json
import asyncio
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
        print(f"✅ User connected to room: {call_id}. Total: {len(rooms[call_id])}")

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
                        print(f"Error sending to peer: {e}")

manager = ConnectionManager()

def text_to_pcm_bytes(text, lang):
    """Converts text to raw PCM 16kHz Mono 16-bit bytes"""
    try:
        tts = gTTS(text=text, lang=lang)
        mp3_fp = BytesIO()
        tts.write_to_fp(mp3_fp)
        mp3_fp.seek(0)
        
        # Convert MP3 to PCM 16kHz
        audio = AudioSegment.from_file(mp3_fp, format="mp3")
        audio = audio.set_frame_rate(16000).set_channels(1).set_sample_width(2)
        return audio.raw_data
    except Exception as e:
        print(f"TTS Error: {e}")
        return b""

@app.websocket("/ws/call/{call_id}/{source_lang}/{target_lang}")
async def websocket_endpoint(websocket: WebSocket, call_id: str, source_lang: str, target_lang: str):
    await manager.connect(websocket, call_id)
    audio_buffer = bytearray()
    
    # Threshold for processing (e.g., 3 seconds of 16kHz 16-bit audio = 96000 bytes)
    # We can also use silence detection, but for now, we'll use a chunk-based approach
    CHUNK_THRESHOLD = 96000 

    try:
        while True:
            # Receive binary PCM chunk from Android
            data = await websocket.receive_bytes()
            audio_buffer.extend(data)

            if len(audio_buffer) >= CHUNK_THRESHOLD:
                # Save buffer to temp file for Whisper (needs a file or proper format)
                temp_filename = f"temp_{call_id}_{source_lang}.wav"
                
                # Create a simple WAV from raw PCM for the Whisper model
                audio_segment = AudioSegment(
                    data=bytes(audio_buffer),
                    sample_width=2,
                    frame_rate=16000,
                    channels=1
                )
                audio_segment.export(temp_filename, format="wav")
                
                # STT + Translate
                # Note: transcribe_and_translate might need to be async or run in thread
                result = transcribe_and_translate(temp_filename, source_lang, target_lang)
                
                if result["success"] and result["source_text"]:
                    print(f"[{call_id}] {source_lang}: {result['source_text']} -> {target_lang}: {result['translated_text']}")
                    
                    # Notify the sender of their own transcription (optional)
                    await websocket.send_json({
                        "type": "transcription",
                        "source": result["source_text"],
                        "translated": result["translated_text"]
                    })

                    # TTS to PCM
                    translated_audio = text_to_pcm_bytes(result["translated_text"], target_lang)
                    
                    if translated_audio:
                        # Relay to the other person in the room
                        await manager.send_audio_to_peer(translated_audio, websocket, call_id)
                
                audio_buffer.clear()
                if os.path.exists(temp_filename): os.remove(temp_filename)

    except WebSocketDisconnect:
        manager.disconnect(websocket, call_id)
    except Exception as e:
        print(f"WebSocket Error: {e}")
        manager.disconnect(websocket, call_id)

if __name__ == "__main__":
    import uvicorn
    uvicorn.run(app, host="0.0.0.0", port=8000)
