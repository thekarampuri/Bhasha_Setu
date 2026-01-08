import os
# IMPORTANT: This must be set BEFORE importing transformers
os.environ['TRANSFORMERS_NO_TF'] = '1'

from transformers import MarianMTModel, MarianTokenizer
import torch

# --- Model Cache ---
model_cache = {}

def get_model_and_tokenizer(from_lang, to_lang):
    pair = f"{from_lang}-{to_lang}"
    if pair not in model_cache:
        model_name = f"Helsinki-NLP/opus-mt-{from_lang}-{to_lang}"
        print(f"Loading translation model for the first time: {model_name}...")
        try:
            tokenizer = MarianTokenizer.from_pretrained(model_name)
            model = MarianMTModel.from_pretrained(model_name)
            model_cache[pair] = (model, tokenizer)
            print(f"✅ Model {model_name} loaded and cached.")
        except Exception as e:
            print(f"❌ FATAL: Could not load model {model_name}. Error: {e}")
            model_cache[pair] = (None, None)
    
    return model_cache[pair]

def translate_text(text, from_lang):
    if not text or not text.strip():
        return ""

    if from_lang not in ["mr", "en"]:
        from_lang = "en"

    to_lang = "en" if from_lang == "mr" else "mr"

    print(f"[TRANSLATE] {from_lang} -> {to_lang}: {text}")

    model, tokenizer = get_model_and_tokenizer(from_lang, to_lang)
    if not model or not tokenizer:
        return "[Translation Model Not Available]"

    try:
        batch = tokenizer([text], return_tensors="pt", padding=True)
        
        with torch.no_grad():
            # Use improved generation parameters for better quality
            generated_ids = model.generate(
                **batch, 
                max_length=128, 
                num_beams=4, 
                early_stopping=True
            )
            print(f"[DEBUG] Generated Token IDs: {generated_ids}")

        translated_text = tokenizer.batch_decode(generated_ids, skip_special_tokens=True)[0]
        
        print(f"[RESULT] {translated_text}")
        return translated_text
    except Exception as e:
        print(f"❌ Translation Runtime Error: {e}")
        return "[Translation Failed]"
