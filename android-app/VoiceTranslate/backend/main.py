from fastapi import FastAPI, UploadFile, File
import shutil
import os
from stt import transcribe_and_translate

app = FastAPI()

# Ensure temp directory exists
UPLOAD_DIR = "temp_audio"
os.makedirs(UPLOAD_DIR, exist_ok=True)

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

if __name__ == "__main__":
    import uvicorn
    uvicorn.run(app, host="0.0.0.0", port=8000)
