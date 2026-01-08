import os
# IMPORTANT: This must be set BEFORE importing transformers
os.environ['TRANSFORMERS_NO_TF'] = '1'

import torch
import torchaudio
from transformers import AutoModel, AutoProcessor
from translate_utils import translate_text

# --- AI Model Loading ---
# Using a two-stage pipeline for higher accuracy:
# 1. Language Identification (LID) Model: Fast and lightweight.
# 2. Automatic Speech Recognition (ASR) Model: Powerful, but needs a language hint.

print("Loading AI models. This may take a few minutes on the first run...")

# 1. Language ID Model (to detect en/mr)
# Using a specific version of protobuf for compatibility if needed.
try:
    from transformers import Wav2Vec2Processor
    lid_processor = Wav2Vec2Processor.from_pretrained("facebook/mms-lid-401M")
    lid_model = AutoModel.from_pretrained("facebook/mms-lid-401M")
except ImportError:
    print("Falling back to AutoProcessor for LID model.")
    lid_processor = AutoProcessor.from_pretrained("facebook/mms-lid-401M")
    lid_model = AutoModel.from_pretrained("facebook/mms-lid-401M")

print("✅ Language ID model loaded.")

# 2. ASR Model (to transcribe speech to text)
asr_model = AutoModel.from_pretrained("ai4bharat/indic-conformer-600m-multilingual", trust_remote_code=True)
print("✅ ASR Conformer model loaded.")

# Map LID output codes to ASR input codes
lang_map = {
    "eng": "en",
    "mar": "mr"
}
target_sample_rate = 16000

def transcribe_and_translate(file_path: str):
    try:
        # 1. Load and prepare audio file
        waveform, sr = torchaudio.load(file_path)
        waveform = torch.mean(waveform, dim=0, keepdim=True) # to mono
        if sr != target_sample_rate:
            resampler = torchaudio.transforms.Resample(orig_freq=sr, new_freq=target_sample_rate)
            waveform = resampler(waveform)

        # 2. Stage 1: Identify the language spoken in the audio
        with torch.no_grad():
            inputs = lid_processor(waveform.squeeze(), sampling_rate=target_sample_rate, return_tensors="pt")
            outputs = lid_model(**inputs)
            logits = outputs.logits
        
        predicted_id = torch.argmax(logits, dim=-1)
        detected_lang_code = lid_processor.batch_decode(predicted_id)[0]
        
        source_lang = lang_map.get(detected_lang_code, "en") # Default to 'en'
        print(f"Detected language: '{source_lang}'")

        # 3. Stage 2: Transcribe using the powerful ASR model with the detected language
        source_text = asr_model(waveform, source_lang, "ctc")
        print(f"Source text: {source_text}")
        
        if not source_text:
            return { "success": True, "source_text": "(No speech detected)", "translated_text": "" }

        # 4. Stage 3: Translate the transcribed text
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
        print(f"❌ STT/Translation Error: {e}")
        return { "success": False, "error": str(e) }
