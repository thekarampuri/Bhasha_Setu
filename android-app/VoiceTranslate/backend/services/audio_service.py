"""
Audio processing service for Bhasha Setu backend.
Handles audio file operations, VAD, and audio analysis.
"""
import os
import wave
import uuid
import numpy as np
from typing import Tuple, Optional
from config import settings
from utils.logger import get_logger

logger = get_logger(__name__)


class AudioService:
    """Service for audio processing operations"""
    
    def __init__(self):
        self.temp_dir = settings.server.temp_dir
        os.makedirs(self.temp_dir, exist_ok=True)
        
        # Audio statistics for dynamic VAD
        self.audio_stats = {
            'recent_energies': [],
            'recent_peaks': [],
            'baseline_energy': 0.005,
            'baseline_peak': 0.01
        }
    
    def save_audio_chunk(
        self,
        audio_data: bytes,
        call_id: str,
        source_lang: str
    ) -> Optional[str]:
        """
        Save audio chunk to a WAV file.
        
        Args:
            audio_data: Raw PCM audio data
            call_id: Call identifier
            source_lang: Source language code
        
        Returns:
            Path to saved file, or None if failed
        """
        # Validate minimum chunk size
        min_size = settings.audio.min_chunk_size_bytes
        if len(audio_data) < min_size:
            logger.debug(
                f"Skipping chunk: too small ({len(audio_data)} bytes, "
                f"minimum: {min_size})"
            )
            return None
        
        # Generate unique filename
        task_id = uuid.uuid4().hex
        filename = f"stt_{call_id}_{source_lang}_{task_id}.wav"
        filepath = os.path.join(self.temp_dir, filename)
        
        try:
            with wave.open(filepath, 'wb') as wf:
                wf.setnchannels(settings.audio.channels)
                wf.setsampwidth(settings.audio.sample_width)
                wf.setframerate(settings.audio.sample_rate)
                wf.writeframes(audio_data)
            
            logger.debug(f"Saved audio to {filepath} ({len(audio_data)} bytes)")
            return filepath
        
        except Exception as e:
            logger.error(f"Failed to save audio chunk: {e}")
            return None
    
    def update_audio_stats(self, energy: float, peak: float) -> None:
        """
        Update rolling statistics for dynamic VAD threshold adjustment.
        
        Args:
            energy: RMS energy of audio
            peak: Peak amplitude of audio
        """
        self.audio_stats['recent_energies'].append(energy)
        self.audio_stats['recent_peaks'].append(peak)
        
        # Keep only last 20 samples
        if len(self.audio_stats['recent_energies']) > 20:
            self.audio_stats['recent_energies'].pop(0)
            self.audio_stats['recent_peaks'].pop(0)
        
        # Update baseline (median of recent values)
        if len(self.audio_stats['recent_energies']) >= 5:
            self.audio_stats['baseline_energy'] = np.median(
                self.audio_stats['recent_energies']
            )
            self.audio_stats['baseline_peak'] = np.median(
                self.audio_stats['recent_peaks']
            )
    
    def is_audio_silent(self, file_path: str) -> bool:
        """
        Dynamic VAD with adaptive thresholds and peak checks for soft speech detection.
        
        Args:
            file_path: Path to audio file
        
        Returns:
            True if audio is silent, False if speech detected
        """
        try:
            with wave.open(file_path, 'rb') as wf:
                frames = wf.readframes(wf.getnframes())
                sample_rate = wf.getframerate()
                audio = np.frombuffer(frames, dtype=np.int16).astype(np.float32) / 32768.0
                
                # Check minimum duration
                duration = len(audio) / sample_rate
                min_duration = settings.vad.min_duration_seconds
                
                if duration < min_duration:
                    logger.debug(f"Skipping audio: too short ({duration:.2f}s)")
                    return True
                
                if len(audio) == 0:
                    return True
                
                # Calculate RMS energy
                energy = np.sqrt(np.mean(audio**2))
                
                # Calculate peak amplitude
                peak = np.max(np.abs(audio))
                
                # Update statistics for adaptive thresholds
                self.update_audio_stats(energy, peak)
                
                # Dynamic threshold adjustment
                base_threshold = settings.vad.base_threshold
                adaptive_energy_threshold = min(
                    base_threshold,
                    self.audio_stats['baseline_energy'] * 0.5
                )
                adaptive_peak_threshold = min(
                    base_threshold * 2,
                    self.audio_stats['baseline_peak'] * 0.5
                )
                
                # Soft speech detection: pass if EITHER energy OR peak exceeds threshold
                has_energy = energy > adaptive_energy_threshold
                has_peak = peak > adaptive_peak_threshold
                
                is_silent = not (has_energy or has_peak)
                
                # Log analysis
                logger.debug(
                    f"Audio analysis: duration={duration:.2f}s, "
                    f"energy={energy:.4f}, peak={peak:.4f}"
                )
                logger.debug(
                    f"Thresholds: energy={adaptive_energy_threshold:.4f}, "
                    f"peak={adaptive_peak_threshold:.4f}"
                )
                logger.debug(
                    f"Baseline: energy={self.audio_stats['baseline_energy']:.4f}, "
                    f"peak={self.audio_stats['baseline_peak']:.4f}"
                )
                
                if is_silent:
                    logger.debug("Audio rejected as silent")
                else:
                    logger.info(
                        f"Audio passed VAD check (energy={has_energy}, peak={has_peak})"
                    )
                
                return is_silent
        
        except Exception as e:
            logger.error(f"VAD Error: {e}")
            return True
    
    def get_audio_duration(self, file_path: str) -> Optional[float]:
        """
        Get duration of audio file in seconds.
        
        Args:
            file_path: Path to audio file
        
        Returns:
            Duration in seconds, or None if failed
        """
        try:
            with wave.open(file_path, 'rb') as wf:
                frames = wf.getnframes()
                rate = wf.getframerate()
                return frames / float(rate)
        except Exception as e:
            logger.error(f"Failed to get audio duration: {e}")
            return None
