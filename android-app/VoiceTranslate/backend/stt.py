from faster_whisper import WhisperModel
import os

# Initialize Whisper model
# "base" or "small" is recommended for local CPU testing
# Use device="cuda" if you have an NVIDIA GPU
model_size = "base"
model = WhisperModel(model_size, device="cpu", compute_type="int8")

def transcribe_audio(file_path: str):
    try:
        segments, info = model.transcribe(file_path, beam_size=5)
        text = "".join([segment.text for segment in segments])
        return {
            "success": True,
            "text": text.strip(),
            "language": info.language
        }
    except Exception as e:
        print(f"STT Error: {e}")
        return {
            "success": False,
            "text": str(e),
            "language": None
        }
