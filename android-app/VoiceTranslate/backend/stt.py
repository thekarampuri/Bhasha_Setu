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
    "thank you", "thanks for watching", "subscribing", "subtitle", 
    "please like and subscribe", "you", "th", "h", "..."
]

def is_audio_silent(file_path, threshold=0.01):
    """Simple energy-based VAD (Voice Activity Detection)"""
    try:
        with wave.open(file_path, 'rb') as wf:
            frames = wf.readframes(wf.getnframes())
            audio = np.frombuffer(frames, dtype=np.int16).astype(np.float32) / 32768.0
            if len(audio) == 0: return True
            energy = np.sqrt(np.mean(audio**2))
            return energy < threshold
    except Exception as e:
        print(f"VAD Error: {e}")
        return True

def transcribe_and_translate(file_path: str, source_lang: str, target_lang: str):
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
        if not clean_text or clean_text in HALLUCINATION_FILTERS or len(clean_text) < 2:
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
