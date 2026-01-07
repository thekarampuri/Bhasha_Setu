import argostranslate.package
import argostranslate.translate
import os

def install_translation_packages():
    # Update package index
    argostranslate.package.update_package_index()
    
    # Get available packages
    available_packages = argostranslate.package.get_available_packages()
    
    # Required pairs
    required_pairs = [("mr", "en"), ("en", "mr")]
    
    for from_code, to_code in required_pairs:
        # Check if already installed
        installed_packages = argostranslate.package.get_installed_packages()
        if any(p.from_code == from_code and p.to_code == to_code for p in installed_packages):
            print(f"Package {from_code} -> {to_code} already installed.")
            continue
            
        # Find and install
        package_to_install = next(
            filter(
                lambda x: x.from_code == from_code and x.to_code == to_code,
                available_packages
            ), None
        )
        if package_to_install:
            print(f"Installing package {from_code} -> {to_code}...")
            argostranslate.package.install_from_path(package_to_install.download())
        else:
            print(f"Could not find package for {from_code} -> {to_code}")

# Initialize packages on import
install_translation_packages()

def translate_text(text, from_lang):
    if not text:
        return ""
        
    # Determine target language
    target_lang = "en" if from_lang == "mr" else "mr"
    
    try:
        translated_text = argostranslate.translate.translate(text, from_lang, target_lang)
        return translated_text
    except Exception as e:
        print(f"Translation Error: {e}")
        return text # Fallback to original text on error
