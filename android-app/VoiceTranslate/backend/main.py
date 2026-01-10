from fastapi import FastAPI, UploadFile, File, Form
import shutil
import os
from stt import transcribe_and_translate

app = FastAPI()

UPLOAD_DIR = "temp_audio"
os.makedirs(UPLOAD_DIR, exist_ok=True)

@app.post("/translate")
async def translate_endpoint(
    file: UploadFile = File(...), 
    source_lang: str = Form(...),
    target_lang: str = Form(...)
):
    temp_file = os.path.join(UPLOAD_DIR, file.filename)
    
    with open(temp_file, "wb") as buffer:
        shutil.copyfileobj(file.file, buffer)
    
    # Process using Whisper and Translation model
    result = transcribe_and_translate(temp_file, source_lang, target_lang)
    
    os.remove(temp_file)
    return result

if __name__ == "__main__":
    import uvicorn
    uvicorn.run(app, host="0.0.0.0", port=8000)
