import os
os.environ['TRANSFORMERS_NO_TF'] = '1'
from transformers import MarianMTModel, MarianTokenizer
import torch

# Load models and tokenizers for Marathi-English translation
# Note: MarianMT models are loaded on demand and cached locally.
models = {}
tokenizers = {}

def get_model_and_tokenizer(from_lang, to_lang):
    pair = f"{from_lang}-{to_lang}"
    if pair not in models:
        model_name = f"Helsinki-NLP/opus-mt-{from_lang}-{to_lang}"
        print(f"Loading translation model: {model_name}...")
        try:
            tokenizers[pair] = MarianTokenizer.from_pretrained(model_name)
            models[pair] = MarianMTModel.from_pretrained(model_name)
            print(f"✅ Model {model_name} loaded.")
        except Exception as e:
            print(f"❌ Error loading model {model_name}: {e}")
            return None, None
    return models[pair], tokenizers[pair]

def translate_text(text, from_lang):
    if not text or text.strip() == "":
        return ""

    # Normalize language codes
    if from_lang == "mar": from_lang = "mr"
    if from_lang == "eng": from_lang = "en"
    
    # Supported pairs: mr-en, en-mr
    if from_lang == "mr":
        to_lang = "en"
    else:
        from_lang = "en"
        to_lang = "mr"

    print(f"[TRANSLATE] {from_lang} -> {to_lang}: {text}")

    model, tokenizer = get_model_and_tokenizer(from_lang, to_lang)
    if not model or not tokenizer:
        return "[Translation Model Load Error]"

    try:
        # Tokenize and translate
        batch = tokenizer([text], return_tensors="pt")
        generated_ids = model.generate(**batch)
        translated_text = tokenizer.batch_decode(generated_ids, skip_special_tokens=True)[0]
        
        print(f"[RESULT] {translated_text}")
        return translated_text
    except Exception as e:
        print(f"❌ Translation Error: {e}")
        return f"[Translation Error: {str(e)}]"

# Pre-load models to avoid delay during first request (optional but recommended)
# get_model_and_tokenizer("mr", "en")
# get_model_and_tokenizer("en", "mr")
