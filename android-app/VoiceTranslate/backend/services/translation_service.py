"""
Translation service for Bhasha Setu backend.
Handles translation model management and text translation.
"""
import os
import torch
from typing import Dict, Tuple, Optional
from transformers import MarianMTModel, MarianTokenizer
from config import settings
from utils.logger import get_logger

os.environ['TRANSFORMERS_NO_TF'] = '1'

logger = get_logger(__name__)


class TranslationService:
    """Service for translation operations"""
    
    def __init__(self):
        self.model_cache: Dict[str, Tuple[MarianMTModel, MarianTokenizer]] = {}
        logger.info("Translation service initialized")
    
    def get_model_and_tokenizer(
        self,
        from_lang: str,
        to_lang: str
    ) -> Optional[Tuple[MarianMTModel, MarianTokenizer]]:
        """
        Get or load translation model and tokenizer.
        
        Args:
            from_lang: Source language code
            to_lang: Target language code
        
        Returns:
            Tuple of (model, tokenizer), or None if failed
        """
        pair = f"{from_lang}-{to_lang}"
        
        # Return cached model if available
        if pair in self.model_cache:
            return self.model_cache[pair]
        
        # Load new model
        model_name = f"{settings.translation.model_prefix}-{from_lang}-{to_lang}"
        logger.info(f"Loading translation model: {model_name}")
        
        try:
            tokenizer = MarianTokenizer.from_pretrained(model_name)
            model = MarianMTModel.from_pretrained(model_name)
            
            if settings.translation.cache_models:
                self.model_cache[pair] = (model, tokenizer)
                logger.info(f"Cached translation model: {pair}")
            
            return (model, tokenizer)
        
        except Exception as e:
            logger.error(f"Failed to load translation model {model_name}: {e}")
            return None
    
    def translate(
        self,
        text: str,
        from_lang: str,
        to_lang: str
    ) -> str:
        """
        Translate text from source to target language.
        
        Args:
            text: Text to translate
            from_lang: Source language code
            to_lang: Target language code
        
        Returns:
            Translated text
        """
        # No translation needed if same language or empty text
        if not text or from_lang == to_lang:
            return text
        
        # Get model and tokenizer
        result = self.get_model_and_tokenizer(from_lang, to_lang)
        if result is None:
            logger.error(f"Translation model unavailable for {from_lang}-{to_lang}")
            return "[Translation Model Unavailable]"
        
        model, tokenizer = result
        
        try:
            # Translate
            batch = tokenizer([text], return_tensors="pt", padding=True)
            with torch.no_grad():
                generated_ids = model.generate(**batch)
            translated = tokenizer.batch_decode(generated_ids, skip_special_tokens=True)[0]
            
            logger.debug(f"Translated '{text}' -> '{translated}' ({from_lang}-{to_lang})")
            return translated
        
        except Exception as e:
            logger.error(f"Translation error: {e}")
            return "[Translation Failed]"
    
    def clear_cache(self) -> None:
        """Clear all cached models"""
        self.model_cache.clear()
        logger.info("Translation model cache cleared")
    
    def get_cached_models(self) -> list:
        """Get list of cached model pairs"""
        return list(self.model_cache.keys())
