"""
Data models for Bhasha Setu backend.
"""
from typing import Optional, Literal
from pydantic import BaseModel, Field


class TranscriptionMessage(BaseModel):
    """WebSocket message for transcription results"""
    type: Literal["transcription"] = "transcription"
    source: str = Field(description="Original transcribed text")
    translated: str = Field(description="Translated text")
    sender: str = Field(description="Language code of the sender")


class ErrorMessage(BaseModel):
    """WebSocket message for errors"""
    type: Literal["error"] = "error"
    message: str = Field(description="Error message")
    code: Optional[str] = Field(default=None, description="Error code")


class StatusMessage(BaseModel):
    """WebSocket message for status updates"""
    type: Literal["status"] = "status"
    status: str = Field(description="Status message")
    details: Optional[dict] = Field(default=None, description="Additional details")


class STTResult(BaseModel):
    """Result from speech-to-text processing"""
    success: bool
    source_text: str = ""
    translated_text: str = ""
    error: Optional[str] = None


class AudioChunkMetadata(BaseModel):
    """Metadata for audio chunks"""
    call_id: str
    source_lang: str
    target_lang: str
    chunk_size_bytes: int
    duration_seconds: float
