"""
Multi OCR Engine for Bangla and English Text Recognition
Integrates EasyOCR and Tesseract OCR with automatic fallback and smart engine selection.
"""

import os
import sys
from typing import Dict, Any, List, Optional


class MultiOCREngine:
    def __init__(self, languages: List[str] = None):
        self.languages = languages or ['bn', 'en']
        self._easyocr_reader = None
        self._tesseract_available = None

    def _get_easyocr_reader(self):
        if self._easyocr_reader is None:
            try:
                import easyocr
                self._easyocr_reader = easyocr.Reader(self.languages, gpu=False)
            except Exception as e:
                print(f"[MultiOCREngine] EasyOCR initialization warning: {e}", file=sys.stderr)
        return self._easyocr_reader

    def is_tesseract_available(self) -> bool:
        if self._tesseract_available is None:
            try:
                import pytesseract
                pytesseract.get_tesseract_version()
                self._tesseract_available = True
            except Exception:
                self._tesseract_available = False
        return self._tesseract_available

    def extract_text_easyocr(self, image_input) -> str:
        reader = self._get_easyocr_reader()
        if not reader:
            return ""
        try:
            results = reader.readtext(image_input, detail=0)
            return "\n".join(results)
        except Exception as e:
            print(f"[MultiOCREngine] EasyOCR error: {e}", file=sys.stderr)
            return ""

    def extract_text_tesseract(self, image_input) -> str:
        if not self.is_tesseract_available():
            return ""
        try:
            import pytesseract
            from PIL import Image
            import numpy as np

            if isinstance(image_input, np.ndarray):
                img = Image.fromarray(image_input)
            elif isinstance(image_input, str):
                img = Image.open(image_input)
            else:
                img = image_input

            return pytesseract.image_to_string(img, lang="ben+eng")
        except Exception as e:
            print(f"[MultiOCREngine] Tesseract error: {e}", file=sys.stderr)
            return ""

    def combine_ocr_results(self, image_input) -> str:
        """
        Smart early exit: Run EasyOCR first; if result is sparse, combine with Tesseract.
        """
        easy_text = self.extract_text_easyocr(image_input)
        if len(easy_text.strip()) > 50:
            return easy_text

        tess_text = self.extract_text_tesseract(image_input)
        combined = f"{easy_text}\n{tess_text}".strip()
        return combined if combined else easy_text
