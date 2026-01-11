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

def is_audio_silent(file_path, threshold=0.015, min_duration_seconds=0.5):
    """Enhanced VAD with duration and energy checks"""
    try:
        with wave.open(file_path, 'rb') as wf:
            frames = wf.readframes(wf.getnframes())
            sample_rate = wf.getframerate()
            audio = np.frombuffer(frames, dtype=np.int16).astype(np.float32) / 32768.0
            
            # Check minimum duration
            duration = len(audio) / sample_rate
            if duration < min_duration_seconds:
                print(f"⏭️ Skipping audio: too short ({duration:.2f}s)")
                return True
            
            if len(audio) == 0:
                return True
            
            # Calculate RMS energy
            energy = np.sqrt(np.mean(audio**2))
            
            # Additional check: peak amplitude
            peak = np.max(np.abs(audio))
            
            is_silent = energy < threshold or peak < (threshold * 2)
            if is_silent:
                print(f"🔇 Silent audio detected: energy={energy:.4f}, peak={peak:.4f}")
            
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

