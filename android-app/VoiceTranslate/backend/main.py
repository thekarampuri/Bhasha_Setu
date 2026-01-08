from fastapi import FastAPI, UploadFile, File
import shutil
import os
from stt import transcribe_and_translate
from translate_utils import get_model_and_tokenizer, translate_text
from pydantic import BaseModel

app = FastAPI()

class TranslationRequest(BaseModel):
    text: str
    from_lang: str

# Ensure temp directory exists
UPLOAD_DIR = "temp_audio"
os.makedirs(UPLOAD_DIR, exist_ok=True)

@app.on_event("startup")
async def startup_event():
    print("Pre-loading translation models...")
    get_model_and_tokenizer("mr", "en")
    get_model_and_tokenizer("en", "mr")
    print("✅ Models pre-loaded.")

@app.post("/stt")
async def speech_to_text(file: UploadFile = File(...)):
    temp_file = os.path.join(UPLOAD_DIR, file.filename)
    
    # Save the uploaded file
    with open(temp_file, "wb") as buffer:
        shutil.copyfileobj(file.file, buffer)
    
    # Transcribe and translate
    result = transcribe_and_translate(temp_file)
    
    # Clean up
    os.remove(temp_file)
    
    return result

@app.post("/translate")
async def translate(request: TranslationRequest):
    result = translate_text(request.text, request.from_lang)
    return {"translated_text": result}

if __name__ == "__main__":
    import uvicorn
    uvicorn.run(app, host="0.0.0.0", port=8000)
