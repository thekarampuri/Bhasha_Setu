"""
Speech-to-text service for Bhasha Setu backend.
Handles Whisper model management and transcription.
"""
import os
import time
from typing import Dict, Optional
from faster_whisper import WhisperModel
from config import settings
from models import STTResult
from utils.logger import get_logger

os.environ['TRANSFORMERS_NO_TF'] = '1'

logger = get_logger(__name__)


class STTService:
    """Service for speech-to-text operations"""
    
    # Common hallucination / filler phrases to filter out
    HALLUCINATION_FILTERS = [
        # Common YouTube/video artifacts
        "thank you", "thanks for watching", "subscribing", "subscribe",
        "please like and subscribe", "like and subscribe",
        
        # Common Whisper hallucinations
        "subtitle", "subtitles", "amara.org", "www.mooji.org",
        
        # Single characters and short artifacts
        "you", "th", "h", "a", "i", "the", "...", ".",
        
        # Music/sound artifacts
        "music", "[music]", "(music)", "♪", "♫",
        
        # Silence artifacts
        "", " ", "  ", "...",
        
        # Common filler words when alone
        "um", "uh", "hmm", "mm", "ah", "oh", "eh"
    ]
    
    def __init__(self):
        logger.info("Loading Whisper model...")
        self.model = WhisperModel(
            settings.whisper.model_size,
            device=settings.whisper.device,
            compute_type=settings.whisper.compute_type
        )
        logger.info(f"Whisper model '{settings.whisper.model_size}' loaded successfully")
        
        # Track recent transcripts to suppress duplicates
        self.recent_transcripts: Dict[str, Dict[str, float]] = {}
    
    def is_duplicate_transcript(
        self,
        text: str,
        call_id: str,
        window_seconds: Optional[int] = None
    ) -> bool:
        """
        Check if this transcript was recently seen for this call.
        
        Args:
            text: Transcribed text
            call_id: Call identifier
            window_seconds: Time window for duplicate detection
        
        Returns:
            True if duplicate, False otherwise
        """
        if window_seconds is None:
            window_seconds = settings.vad.duplicate_window_seconds
        
        current_time = time.time()
        
        # Clean up old entries
        if call_id in self.recent_transcripts:
            self.recent_transcripts[call_id] = {
                t: timestamp
                for t, timestamp in self.recent_transcripts[call_id].items()
                if current_time - timestamp < window_seconds
            }
        
        # Check if duplicate
        if call_id in self.recent_transcripts and text in self.recent_transcripts[call_id]:
            logger.debug(f"Duplicate transcript suppressed: '{text}'")
            return True
        
        # Store this transcript
        if call_id not in self.recent_transcripts:
            self.recent_transcripts[call_id] = {}
        self.recent_transcripts[call_id][text] = current_time
        
        return False
    
    def is_hallucination(self, text: str) -> bool:
        """
        Check if text is likely a hallucination.
        
        Args:
            text: Text to check
        
        Returns:
            True if likely hallucination, False otherwise
        """
        clean_text = text.lower().strip(" .?!")
        
        if not clean_text or len(clean_text) < 2:
            return True
        
        if clean_text in self.HALLUCINATION_FILTERS:
            logger.debug(f"Filtered hallucination: '{text}'")
            return True
        
        return False
    
    def transcribe(
        self,
        file_path: str,
        source_lang: str
    ) -> str:
        """
        Transcribe audio file using Whisper.
        
        Args:
            file_path: Path to audio file
            source_lang: Source language code
        
        Returns:
            Transcribed text
        """
        try:
            segments, info = self.model.transcribe(
                file_path,
                language=source_lang,
                beam_size=settings.whisper.beam_size,
                condition_on_previous_text=False,
                no_speech_threshold=settings.whisper.no_speech_threshold,
                vad_filter=settings.whisper.vad_filter,
                vad_parameters=dict(
                    min_silence_duration_ms=settings.whisper.vad_min_silence_duration_ms
                )
            )
            
            text = "".join([segment.text for segment in segments]).strip()
            logger.debug(f"Transcribed: '{text}'")
            return text
        
        except Exception as e:
            logger.error(f"Transcription error: {e}")
            raise
    
    def process(
        self,
        file_path: str,
        source_lang: str,
        call_id: str
    ) -> STTResult:
        """
        Process audio file for transcription with filtering.
        
        Args:
            file_path: Path to audio file
            source_lang: Source language code
            call_id: Call identifier
        
        Returns:
            STTResult with transcription
        """
        try:
            # Transcribe
            source_text = self.transcribe(file_path, source_lang)
            
            # Filter hallucinations
            if self.is_hallucination(source_text):
                return STTResult(success=True, source_text="", translated_text="")
            
            # Check for duplicates
            if self.is_duplicate_transcript(source_text, call_id):
                return STTResult(success=True, source_text="", translated_text="")
            
            return STTResult(success=True, source_text=source_text, translated_text="")
        
        except Exception as e:
            logger.error(f"STT processing error: {e}")
            return STTResult(success=False, error=str(e))
