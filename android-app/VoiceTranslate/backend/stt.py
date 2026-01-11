import os
import numpy as np
import wave
import torch

os.environ['TRANSFORMERS_NO_TF'] = '1'

from faster_whisper import WhisperModel
from translate_utils import translate_text

print("Loading Whisper model...")
model_size = "small"
# Added condition_on_previous_text=False type settings via model loading if possible, 
# but usually it's a transcribe parameter.
whisper_model = WhisperModel(model_size, device="cpu", compute_type="int8")
print(f"✅ Whisper model loaded.")

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

# Track recent transcripts to suppress duplicates
recent_transcripts = {}
DUPLICATE_WINDOW_SECONDS = 10

# Track audio statistics for dynamic VAD
audio_stats = {
    'recent_energies': [],
    'recent_peaks': [],
    'baseline_energy': 0.005,
    'baseline_peak': 0.01
}

def update_audio_stats(energy, peak):
    """Update rolling statistics for dynamic VAD threshold adjustment"""
    audio_stats['recent_energies'].append(energy)
    audio_stats['recent_peaks'].append(peak)
    
    # Keep only last 20 samples
    if len(audio_stats['recent_energies']) > 20:
        audio_stats['recent_energies'].pop(0)
        audio_stats['recent_peaks'].pop(0)
    
    # Update baseline (median of recent values)
    if len(audio_stats['recent_energies']) >= 5:
        audio_stats['baseline_energy'] = np.median(audio_stats['recent_energies'])
        audio_stats['baseline_peak'] = np.median(audio_stats['recent_peaks'])

def is_audio_silent(file_path, base_threshold=0.003, min_duration_seconds=0.3):
    """Dynamic VAD with adaptive thresholds and peak checks for soft speech detection"""
    try:
        with wave.open(file_path, 'rb') as wf:
            frames = wf.readframes(wf.getnframes())
            sample_rate = wf.getframerate()
            audio = np.frombuffer(frames, dtype=np.int16).astype(np.float32) / 32768.0
            
            # Check minimum duration (reduced to 0.3s for faster response)
            duration = len(audio) / sample_rate
            if duration < min_duration_seconds:
                print(f"⏭️ Skipping audio: too short ({duration:.2f}s)")
                return True
            
            if len(audio) == 0:
                return True
            
            # Calculate RMS energy
            energy = np.sqrt(np.mean(audio**2))
            
            # Calculate peak amplitude
            peak = np.max(np.abs(audio))
            
            # Update statistics for adaptive thresholds
            update_audio_stats(energy, peak)
            
            # DYNAMIC threshold adjustment
            # Use baseline + small margin, or base_threshold, whichever is lower
            adaptive_energy_threshold = min(base_threshold, audio_stats['baseline_energy'] * 0.5)
            adaptive_peak_threshold = min(base_threshold * 2, audio_stats['baseline_peak'] * 0.5)
            
            # Soft speech detection: pass if EITHER energy OR peak exceeds threshold
            has_energy = energy > adaptive_energy_threshold
            has_peak = peak > adaptive_peak_threshold
            
            is_silent = not (has_energy or has_peak)
            
            # ALWAYS log energy levels for debugging
            print(f"🎤 Audio analysis: duration={duration:.2f}s, energy={energy:.4f}, peak={peak:.4f}")
            print(f"   Thresholds: energy={adaptive_energy_threshold:.4f}, peak={adaptive_peak_threshold:.4f}")
            print(f"   Baseline: energy={audio_stats['baseline_energy']:.4f}, peak={audio_stats['baseline_peak']:.4f}")
            
            if is_silent:
                print(f"🔇 Audio rejected as silent")
            else:
                print(f"✅ Audio passed VAD check (energy={has_energy}, peak={has_peak})")
            
            return is_silent
    except Exception as e:
        print(f"VAD Error: {e}")
        return True

def is_duplicate_transcript(text, call_id, window_seconds=DUPLICATE_WINDOW_SECONDS):
    """Check if this transcript was recently seen for this call"""
    import time
    current_time = time.time()
    
    # Clean up old entries
    if call_id in recent_transcripts:
        recent_transcripts[call_id] = {
            t: timestamp for t, timestamp in recent_transcripts[call_id].items()
            if current_time - timestamp < window_seconds
        }
    
    # Check if duplicate
    if call_id in recent_transcripts and text in recent_transcripts[call_id]:
        print(f"🔁 Duplicate transcript suppressed: '{text}'")
        return True
    
    # Store this transcript
    if call_id not in recent_transcripts:
        recent_transcripts[call_id] = {}
    recent_transcripts[call_id][text] = current_time
    
    return False

def transcribe_and_translate(file_path: str, source_lang: str, target_lang: str, call_id: str = "default"):
    try:
        # 1. Voice Activity Detection / Silence Check
        if is_audio_silent(file_path):
            return { "success": True, "source_text": "", "translated_text": "" }

        # 2. Transcribe with Whisper
        # Added: condition_on_previous_text=False to prevent hallucination loops
        # Added: no_speech_threshold=0.6 to ignore non-speech parts
        segments, info = whisper_model.transcribe(
            file_path, 
            language=source_lang, 
            beam_size=5,
            condition_on_previous_text=False,
            no_speech_threshold=0.6,
            vad_filter=True, # Use internal Silero VAD
            vad_parameters=dict(min_silence_duration_ms=500)
        )
        
        source_text = "".join([segment.text for segment in segments]).strip()
        
        # 3. Post-processing filters
        clean_text = source_text.lower().strip(" .?!")
        
        # Filter out hallucinations
        if not clean_text or clean_text in HALLUCINATION_FILTERS or len(clean_text) < 2:
            print(f"🚫 Filtered hallucination: '{source_text}'")
            return { "success": True, "source_text": "", "translated_text": "" }
        
        # Check for duplicates
        if is_duplicate_transcript(source_text, call_id):
            return { "success": True, "source_text": "", "translated_text": "" }

        # 4. Translate using Hugging Face model
        translated_text = translate_text(source_text, source_lang, target_lang)
        
        return {
            "success": True,
            "source_text": source_text,
            "translated_text": translated_text
        }
    except Exception as e:
        print(f"❌ STT Error: {e}")
        return { "success": False, "error": str(e) }

