import os
os.environ['TRANSFORMERS_NO_TF'] = '1'

from faster_whisper import WhisperModel
from translate_utils import translate_text

print("Loading Whisper model...")
model_size = "small"
whisper_model = WhisperModel(model_size, device="cpu", compute_type="int8")
print(f"✅ Whisper model loaded.")

def transcribe_and_translate(file_path: str, source_lang: str, target_lang: str):
    try:
        # 1. Transcribe with Whisper using specified source language
        segments, info = whisper_model.transcribe(file_path, language=source_lang, beam_size=5)
        source_text = "".join([segment.text for segment in segments]).strip()
        
        if not source_text:
            return { "success": True, "source_text": "", "translated_text": "" }

        # 2. Translate using Hugging Face model via translate_utils
        translated_text = translate_text(source_text, source_lang, target_lang)
        
        return {
            "success": True,
            "source_text": source_text,
            "translated_text": translated_text
        }
    except Exception as e:
        print(f"❌ Error: {e}")
        return { "success": False, "error": str(e) }
