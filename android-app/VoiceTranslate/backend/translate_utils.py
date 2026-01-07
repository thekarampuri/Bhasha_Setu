import argostranslate.package
import argostranslate.translate
import os

def install_translation_packages():
    try:
        print("--- Translation Engine Initialization ---")
        
        # 1. Update the package index
        print("Updating Argos Translate package index...")
        argostranslate.package.update_package_index()
        
        # 2. Get available packages
        available_packages = argostranslate.package.get_available_packages()
        
        # 3. Define the language pairs we need
        required_pairs = [("mr", "en"), ("en", "mr")]
        
        for from_code, to_code in required_pairs:
            installed_packages = argostranslate.package.get_installed_packages()
            is_installed = any(p.from_code == from_code and p.to_code == to_code for p in installed_packages)
            
            if is_installed:
                print(f"✅ Language pair {from_code} -> {to_code} is already installed.")
            else:
                print(f"⬇️ Downloading package for {from_code} -> {to_code}...")
                package_to_install = next(
                    filter(
                        lambda x: x.from_code == from_code and x.to_code == to_code,
                        available_packages
                    ), None
                )
                
                if package_to_install:
                    download_path = package_to_install.download()
                    argostranslate.package.install_from_path(download_path)
                    print(f"✅ Successfully installed {from_code} -> {to_code}")
                else:
                    print(f"❌ Error: Could not find package {from_code} -> {to_code} in index.")
        
        print("--- Initialization Complete ---\n")
    except Exception as e:
        print(f"⚠️ Argos Translation Initialization Failed: {e}")

# Run initialization
install_translation_packages()

def translate_text(text, from_lang):
    if not text or text.strip() == "":
        return ""
    
    # Force 2-letter codes for Argos compatibility
    if from_lang == "mar": from_lang = "mr"
    if from_lang == "eng": from_lang = "en"
    
    # Target is English if source is Marathi, otherwise Marathi
    target_lang = "en" if from_lang == "mr" else "mr"
    
    print(f"[TRANSLATE] {from_lang} -> {target_lang}: {text}")
    
    try:
        translated_text = argostranslate.translate.translate(text, from_lang, target_lang)
        print(f"[RESULT] {translated_text}")
        return translated_text
    except Exception as e:
        print(f"❌ Translation Error: {e}")
        return ""
