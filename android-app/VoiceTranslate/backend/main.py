"""
Main FastAPI application for Bhasha Setu backend.
Handles WebSocket endpoints and orchestrates services.
"""
import asyncio
from fastapi import FastAPI, WebSocket, WebSocketDisconnect
from fastapi.middleware.cors import CORSMiddleware
import uvicorn

from config import settings
from services.audio_service import AudioService
from services.stt_service import STTService
from services.translation_service import TranslationService
from services.websocket_service import WebSocketService
from utils.logger import setup_logger, get_logger
from utils.file_utils import safe_delete

# Setup logging
setup_logger("uvicorn", level=settings.server.log_level)
setup_logger("fastapi", level=settings.server.log_level)
logger = setup_logger(__name__, level=settings.server.log_level)

# Initialize FastAPI app
app = FastAPI(
    title="Bhasha Setu API",
    description="Real-time voice translation backend",
    version="2.0.0"
)

# Configure CORS
app.add_middleware(
    CORSMiddleware,
    allow_origins=settings.server.cors_origins,
    allow_credentials=True,
    allow_methods=["*"],
    allow_headers=["*"],
)

# Initialize services
audio_service = AudioService()
stt_service = STTService()
translation_service = TranslationService()
websocket_service = WebSocketService()

logger.info("Bhasha Setu backend initialized")
logger.info(f"Environment: {settings.environment}")
logger.info(f"Whisper model: {settings.whisper.model_size}")


async def process_stt(
    audio_data: bytes,
    call_id: str,
    source_lang: str,
    target_lang: str
) -> None:
    """
    Background task to handle STT and Translation.
    
    Args:
        audio_data: Raw PCM audio data
        call_id: Call identifier
        source_lang: Source language code
        target_lang: Target language code
    """
    logger.debug(
        f"Received audio chunk: {len(audio_data)} bytes for call {call_id}"
    )
    
    # Save audio chunk to file
    temp_filename = audio_service.save_audio_chunk(
        audio_data,
        call_id,
        source_lang
    )
    
    if temp_filename is None:
        return
    
    try:
        logger.info(f"Processing audio file: {temp_filename}")
        
        # Check if audio is silent
        if audio_service.is_audio_silent(temp_filename):
            logger.debug("Audio is silent, skipping transcription")
            return
        
        # Run STT in thread pool
        loop = asyncio.get_event_loop()
        result = await loop.run_in_executor(
            None,
            stt_service.process,
            temp_filename,
            source_lang,
            call_id
        )
        
        # Check if transcription succeeded and has content
        if not result.success:
            logger.error(f"STT failed: {result.error}")
            await websocket_service.broadcast_error(
                call_id,
                f"Transcription failed: {result.error}",
                "STT_ERROR"
            )
            return
        
        if not result.source_text:
            logger.debug("Transcription returned empty (filtered or silent)")
            return
        
        # Translate
        translated_text = translation_service.translate(
            result.source_text,
            source_lang,
            target_lang
        )
        
        # Broadcast result
        logger.info(
            f"[{call_id}] {source_lang}: {result.source_text} "
            f"-> {target_lang}: {translated_text}"
        )
        
        await websocket_service.broadcast_transcription(
            call_id,
            result.source_text,
            translated_text,
            source_lang
        )
    
    except Exception as e:
        logger.error(f"STT processing error: {e}", exc_info=True)
        await websocket_service.broadcast_error(
            call_id,
            "Internal processing error",
            "PROCESSING_ERROR"
        )
    
    finally:
        # Schedule file cleanup
        asyncio.create_task(
            safe_delete(temp_filename, delay=settings.server.cleanup_delay_seconds)
        )


@app.websocket("/ws/call/{call_id}/{source_lang}/{target_lang}")
async def websocket_endpoint(
    websocket: WebSocket,
    call_id: str,
    source_lang: str,
    target_lang: str
):
    """
    WebSocket endpoint for real-time voice call with translation.
    
    Args:
        websocket: WebSocket connection
        call_id: Unique call identifier
        source_lang: Source language code
        target_lang: Target language code
    """
    user_id = source_lang  # Use source language as user identifier
    
    await websocket_service.connect(websocket, call_id, user_id)
    
    logger.info(
        f"WebSocket connected: call_id={call_id}, user_id={user_id}, "
        f"source={source_lang}, target={target_lang}"
    )
    
    # Audio buffer for accumulating chunks
    audio_buffer = bytearray()
    threshold = settings.audio.buffer_threshold_bytes
    
    logger.info(
        f"Audio buffer threshold: {threshold} bytes "
        f"({threshold / (settings.audio.sample_rate * settings.audio.sample_width):.2f} seconds)"
    )
    
    try:
        while True:
            # Receive audio data
            data = await websocket.receive_bytes()
            logger.debug(f"Received {len(data)} bytes from {user_id}")
            
            # 1. Immediate relay for real-time audio
            await websocket_service.relay_audio(data, call_id, user_id)
            
            # 2. Accumulate for STT
            audio_buffer.extend(data)
            
            if len(audio_buffer) >= threshold:
                chunk = bytes(audio_buffer)
                logger.info(
                    f"Buffer threshold reached: {len(chunk)} bytes, "
                    f"sending for STT processing"
                )
                audio_buffer.clear()  # Clear buffer to prevent contamination
                
                # Process in background
                asyncio.create_task(
                    process_stt(chunk, call_id, source_lang, target_lang)
                )
    
    except WebSocketDisconnect:
        logger.info(f"WebSocket disconnected: call_id={call_id}, user_id={user_id}")
        websocket_service.disconnect(call_id, user_id)
    
    except Exception as e:
        logger.error(f"WebSocket error: {e}", exc_info=True)
        websocket_service.disconnect(call_id, user_id)


@app.get("/")
async def root():
    """Root endpoint"""
    return {
        "service": "Bhasha Setu API",
        "version": "2.0.0",
        "status": "running",
        "active_rooms": websocket_service.get_active_rooms()
    }


@app.get("/health")
async def health():
    """Health check endpoint"""
    return {
        "status": "healthy",
        "environment": settings.environment,
        "active_rooms": len(websocket_service.get_active_rooms())
    }


if __name__ == "__main__":
    uvicorn.run(
        app,
        host=settings.server.host,
        port=settings.server.port,
        log_level=settings.server.log_level.lower()
    )
