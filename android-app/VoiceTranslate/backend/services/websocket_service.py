"""
WebSocket service for Bhasha Setu backend.
Handles WebSocket connections, rooms, and message broadcasting.
"""
from typing import Dict
from fastapi import WebSocket
from models import TranscriptionMessage, ErrorMessage, StatusMessage
from utils.logger import get_logger

logger = get_logger(__name__)


class WebSocketService:
    """Service for WebSocket connection management"""
    
    def __init__(self):
        # rooms: {call_id: {user_id: websocket}}
        self.rooms: Dict[str, Dict[str, WebSocket]] = {}
        logger.info("WebSocket service initialized")
    
    async def connect(
        self,
        websocket: WebSocket,
        call_id: str,
        user_id: str
    ) -> None:
        """
        Accept WebSocket connection and add to room.
        
        Args:
            websocket: WebSocket connection
            call_id: Call identifier
            user_id: User identifier
        """
        await websocket.accept()
        
        if call_id not in self.rooms:
            self.rooms[call_id] = {}
        
        self.rooms[call_id][user_id] = websocket
        logger.info(f"User {user_id} joined room {call_id}")
        
        # Send status message
        await self.send_status(
            websocket,
            f"Connected to call {call_id}",
            {"user_id": user_id, "room_size": len(self.rooms[call_id])}
        )
    
    def disconnect(self, call_id: str, user_id: str) -> None:
        """
        Remove user from room.
        
        Args:
            call_id: Call identifier
            user_id: User identifier
        """
        if call_id in self.rooms:
            if user_id in self.rooms[call_id]:
                del self.rooms[call_id][user_id]
                logger.info(f"User {user_id} left room {call_id}")
            
            # Clean up empty rooms
            if not self.rooms[call_id]:
                del self.rooms[call_id]
                logger.info(f"Room {call_id} closed (empty)")
    
    async def broadcast_transcription(
        self,
        call_id: str,
        source: str,
        translated: str,
        sender: str
    ) -> None:
        """
        Broadcast transcription to all users in a room.
        
        Args:
            call_id: Call identifier
            source: Original transcribed text
            translated: Translated text
            sender: Language code of sender
        """
        if call_id not in self.rooms:
            logger.warning(f"Attempted to broadcast to non-existent room: {call_id}")
            return
        
        message = TranscriptionMessage(
            source=source,
            translated=translated,
            sender=sender
        )
        
        await self._broadcast_json(call_id, message.model_dump())
        logger.debug(f"Broadcasted transcription to room {call_id}: {source}")
    
    async def broadcast_error(
        self,
        call_id: str,
        error_message: str,
        error_code: str = None
    ) -> None:
        """
        Broadcast error to all users in a room.
        
        Args:
            call_id: Call identifier
            error_message: Error message
            error_code: Optional error code
        """
        if call_id not in self.rooms:
            return
        
        message = ErrorMessage(message=error_message, code=error_code)
        await self._broadcast_json(call_id, message.model_dump())
        logger.warning(f"Broadcasted error to room {call_id}: {error_message}")
    
    async def send_status(
        self,
        websocket: WebSocket,
        status: str,
        details: dict = None
    ) -> None:
        """
        Send status message to a specific WebSocket.
        
        Args:
            websocket: WebSocket connection
            status: Status message
            details: Optional details
        """
        message = StatusMessage(status=status, details=details)
        try:
            await websocket.send_json(message.model_dump())
        except Exception as e:
            logger.error(f"Failed to send status message: {e}")
    
    async def relay_audio(
        self,
        data: bytes,
        call_id: str,
        sender_id: str
    ) -> None:
        """
        Relay audio data to all users except sender.
        
        Args:
            data: Audio data bytes
            call_id: Call identifier
            sender_id: Sender user identifier
        """
        if call_id not in self.rooms:
            return
        
        for uid, ws in self.rooms[call_id].items():
            if uid != sender_id:
                try:
                    await ws.send_bytes(data)
                except Exception as e:
                    logger.error(f"Failed to relay audio to user {uid}: {e}")
    
    async def _broadcast_json(self, call_id: str, data: dict) -> None:
        """
        Broadcast JSON data to all users in a room.
        
        Args:
            call_id: Call identifier
            data: JSON data to broadcast
        """
        if call_id not in self.rooms:
            return
        
        for uid, ws in self.rooms[call_id].items():
            try:
                await ws.send_json(data)
            except Exception as e:
                logger.error(f"Failed to broadcast to user {uid}: {e}")
    
    def get_room_size(self, call_id: str) -> int:
        """
        Get number of users in a room.
        
        Args:
            call_id: Call identifier
        
        Returns:
            Number of users in room
        """
        return len(self.rooms.get(call_id, {}))
    
    def get_active_rooms(self) -> list:
        """Get list of active room IDs"""
        return list(self.rooms.keys())
