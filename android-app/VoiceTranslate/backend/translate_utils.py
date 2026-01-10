import os
os.environ['TRANSFORMERS_NO_TF'] = '1'

from transformers import MarianMTModel, MarianTokenizer
import torch

model_cache = {}

def get_model_and_tokenizer(from_lang, to_lang):
    # Support for standard Helsinki-NLP models
    pair = f"{from_lang}-{to_lang}"
    if pair not in model_cache:
        model_name = f"Helsinki-NLP/opus-mt-{from_lang}-{to_lang}"
        print(f"Loading {model_name}...")
        try:
            tokenizer = MarianTokenizer.from_pretrained(model_name)
            model = MarianMTModel.from_pretrained(model_name)
            model_cache[pair] = (model, tokenizer)
        except Exception as e:
            print(f"❌ Could not load {model_name}: {e}")
            return None, None
    
    return model_cache[pair]

def translate_text(text, from_lang, to_lang):
    if not text or from_lang == to_lang:
        return text

    model, tokenizer = get_model_and_tokenizer(from_lang, to_lang)
    if not model or not tokenizer:
        return "[Translation Model Unavailable]"

    try:
        batch = tokenizer([text], return_tensors="pt", padding=True)
        with torch.no_grad():
            generated_ids = model.generate(**batch)
        return tokenizer.batch_decode(generated_ids, skip_special_tokens=True)[0]
    except Exception as e:
        print(f"❌ Translation Error: {e}")
        return "[Translation Failed]"
