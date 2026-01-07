from faster_whisper import WhisperModel
import os
from translate_utils import translate_text

# Initialize Whisper model
model_size = "base"
model = WhisperModel(model_size, device="cpu", compute_type="int8")

def transcribe_and_translate(file_path: str):
    try:
        segments, info = model.transcribe(file_path, beam_size=5)
        source_text = "".join([segment.text for segment in segments]).strip()
        
        if not source_text:
            return {
                "success": True,
                "source_text": "",
                "translated_text": "",
                "source_language": info.language,
                "target_language": "en" if info.language == "mr" else "mr"
            }

        # Determine translation direction
        # Whisper language detection codes are usually 2-letter
        source_lang = info.language
        
        # We only support mr <-> en for now
        if source_lang not in ["mr", "en"]:
            # Fallback or attempt to translate from English if detection failed
            source_lang = "en" 

        translated_text = translate_text(source_text, source_lang)
        target_lang = "en" if source_lang == "mr" else "mr"

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
