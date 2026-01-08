from fastapi import FastAPI, UploadFile, File, Form
import shutil
import os
from stt import transcribe_and_translate

app = FastAPI()

UPLOAD_DIR = "temp_audio"
os.makedirs(UPLOAD_DIR, exist_ok=True)

@app.post("/stt")
async def speech_to_text(file: UploadFile = File(...), language: str = Form(...)):
    """
    Accepts an audio file and a language code, performs STT and translation.
    """
    temp_file = os.path.join(UPLOAD_DIR, file.filename)
    
    with open(temp_file, "wb") as buffer:
        shutil.copyfileobj(file.file, buffer)
    
    # Pass the provided language code to the processing function
    result = transcribe_and_translate(temp_file, language)
    
    os.remove(temp_file)
    
    return result

if __name__ == "__main__":
    import uvicorn
    uvicorn.run(app, host="0.0.0.0", port=8000)
