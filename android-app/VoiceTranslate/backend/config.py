"""
Configuration management for Bhasha Setu backend.
Supports environment variables and multiple deployment environments.
"""
import os
from typing import List
from pydantic_settings import BaseSettings, SettingsConfigDict
from pydantic import Field


class AudioConfig(BaseSettings):
    """Audio processing configuration"""
    sample_rate: int = Field(default=16000, description="Audio sample rate in Hz")
    channels: int = Field(default=1, description="Number of audio channels (1=mono)")
    sample_width: int = Field(default=2, description="Sample width in bytes (2=16-bit)")
    
    # Chunk sizes for processing
    min_chunk_duration_ms: int = Field(default=300, description="Minimum audio chunk duration in ms")
    buffer_threshold_duration_ms: int = Field(default=2500, description="Buffer threshold duration in ms")
    
    @property
    def min_chunk_size_bytes(self) -> int:
        """Calculate minimum chunk size in bytes"""
        return int(self.min_chunk_duration_ms / 1000 * self.sample_rate * self.sample_width)
    
    @property
    def buffer_threshold_bytes(self) -> int:
        """Calculate buffer threshold in bytes"""
        return int(self.buffer_threshold_duration_ms / 1000 * self.sample_rate * self.sample_width)


class VADConfig(BaseSettings):
    """Voice Activity Detection configuration"""
    base_threshold: float = Field(default=0.003, description="Base energy threshold for VAD")
    min_duration_seconds: float = Field(default=0.3, description="Minimum speech duration in seconds")
    duplicate_window_seconds: int = Field(default=10, description="Window for duplicate detection in seconds")


class WhisperConfig(BaseSettings):
    """Whisper model configuration"""
    model_size: str = Field(default="small", description="Whisper model size (tiny, base, small, medium, large)")
    device: str = Field(default="cpu", description="Device to run model on (cpu, cuda)")
    compute_type: str = Field(default="int8", description="Compute type (int8, float16, float32)")
    beam_size: int = Field(default=5, description="Beam size for decoding")
    no_speech_threshold: float = Field(default=0.6, description="Threshold for no speech detection")
    vad_filter: bool = Field(default=True, description="Enable internal Silero VAD")
    vad_min_silence_duration_ms: int = Field(default=500, description="Minimum silence duration for VAD in ms")


class TranslationConfig(BaseSettings):
    """Translation model configuration"""
    model_prefix: str = Field(default="Helsinki-NLP/opus-mt", description="Translation model prefix")
    cache_models: bool = Field(default=True, description="Cache loaded models")


class ServerConfig(BaseSettings):
    """Server configuration"""
    host: str = Field(default="0.0.0.0", description="Server host")
    port: int = Field(default=8000, description="Server port")
    cors_origins: List[str] = Field(default=["*"], description="CORS allowed origins")
    log_level: str = Field(default="INFO", description="Logging level")
    temp_dir: str = Field(default="temp_audio", description="Temporary audio directory")
    cleanup_delay_seconds: int = Field(default=1, description="Delay before cleaning up temp files")


class Settings(BaseSettings):
    """Main application settings"""
    model_config = SettingsConfigDict(
        env_file=".env",
        env_file_encoding="utf-8",
        env_nested_delimiter="__",
        case_sensitive=False
    )
    
    # Environment
    environment: str = Field(default="development", description="Deployment environment")
    debug: bool = Field(default=True, description="Debug mode")
    
    # Sub-configurations
    audio: AudioConfig = Field(default_factory=AudioConfig)
    vad: VADConfig = Field(default_factory=VADConfig)
    whisper: WhisperConfig = Field(default_factory=WhisperConfig)
    translation: TranslationConfig = Field(default_factory=TranslationConfig)
    server: ServerConfig = Field(default_factory=ServerConfig)
    
    def __init__(self, **kwargs):
        super().__init__(**kwargs)
        # Ensure temp directory exists
        os.makedirs(self.server.temp_dir, exist_ok=True)


# Global settings instance
settings = Settings()
