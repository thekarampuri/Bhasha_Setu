import os
# IMPORTANT: This must be set BEFORE importing transformers
os.environ['TRANSFORMERS_NO_TF'] = '1'

from faster_whisper import WhisperModel
from translate_utils import translate_text

# --- Simplified AI Model Loading ---
# Using only faster-whisper. The 'small' model is a good balance of speed and accuracy.
# This model does NOT require a Hugging Face login.
print("Loading AI models...")
model_size = "small"
whisper_model = WhisperModel(model_size, device="cpu", compute_type="int8")
print(f"✅ Whisper '{model_size}' model loaded.")

def transcribe_and_translate(file_path: str, language_code: str):
    """
    Transcribes audio using Whisper, guided by the language code from the app,
    then translates the result.
    """
    try:
        # 1. Transcribe using Whisper, providing the language for higher accuracy
        # This is more reliable than auto-detection.
        segments, info = whisper_model.transcribe(file_path, language=language_code, beam_size=5)
        
        source_text = "".join([segment.text for segment in segments]).strip()
        
        print(f"Transcription language: '{info.language}' (Forced='{language_code}')")
        print(f"Source text: {source_text}")

        if not source_text:
            return { "success": True, "source_text": "(No speech detected)", "translated_text": "" }

        # 2. Translate the transcribed text
        # The translation model (MarianMT) is handled in translate_utils
        translated_text = translate_text(source_text, language_code)
        target_lang = "en" if language_code != "en" else "mr" # Simple target logic
        
        return {
            "success": True,
            "source_text": source_text,
            "translated_text": translated_text,
            "source_language": language_code,
            "target_language": target_lang
        }
    except Exception as e:
        print(f"❌ STT/Translation Error: {e}")
        return { "success": False, "error": str(e) }
