from faster_whisper import WhisperModel
import os
from translate_utils import translate_text

# Initialize Whisper model
model_size = "base"
model = WhisperModel(model_size, device="cpu", compute_type="int8")

def transcribe_and_translate(file_path: str):
    try:
        print(f"Processing audio: {file_path}")
        segments, info = model.transcribe(file_path, beam_size=5)
        source_text = "".join([segment.text for segment in segments]).strip()
        
        print(f"Detected language: {info.language} with probability {info.language_probability}")
        print(f"Source text: {source_text}")

        if not source_text:
            return {
                "success": True,
                "source_text": "No speech detected",
                "translated_text": "",
                "source_language": info.language,
                "target_language": "en"
            }

        # Determine translation direction
        source_lang = info.language
        if source_lang not in ["mr", "en"]:
            source_lang = "en" 

        translated_text = translate_text(source_text, source_lang)
        target_lang = "en" if source_lang == "mr" else "mr"
        
        print(f"Translated text: {translated_text}")

        return {
            "success": True,
            "source_text": source_text,
            "translated_text": translated_text,
            "source_language": source_lang,
            "target_language": target_lang
        }
    except Exception as e:
        print(f"STT/Translation Error: {e}")
        return {
            "success": False,
            "error": str(e)
        }
