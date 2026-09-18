"""
Main OCR System for Bangladeshi Documents
Combines image preprocessing, multi-OCR engine recognition, and field postprocessing.
"""

import os
import json
import time
from datetime import datetime
from typing import Dict, Any, List

from .image_preprocessor import ImagePreprocessor
from .multi_ocr_engine import MultiOCREngine
from .text_postprocessor import TextPostProcessor


class BanglaEnglishOCR:
    def __init__(self, languages: List[str] = None):
        self.preprocessor = ImagePreprocessor()
        self.engine = MultiOCREngine(languages=languages)
        self.postprocessor = TextPostProcessor()

    def process_document(self, image_path: str, save_intermediate: bool = False) -> Dict[str, Any]:
        """
        Process an identity document image and return structured extraction JSON.
        """
        start_time = time.time()
        if not os.path.exists(image_path):
            raise FileNotFoundError(f"Image not found at {image_path}")

        # 1. Preprocess
        preprocessed = self.preprocessor.preprocess_image(image_path, save_intermediate=save_intermediate)

        # 2. Recognize text
        raw_text = self.engine.combine_ocr_results(preprocessed)

        # 3. Postprocess and extract fields
        result = self.postprocessor.process_extracted_text(raw_text)

        # 4. Attach metadata
        elapsed = round(time.time() - start_time, 2)
        result["metadata"] = {
            "input_image": image_path,
            "processing_time_seconds": elapsed,
            "timestamp": datetime.utcnow().isoformat(),
            "raw_text_length": len(raw_text)
        }

        return result

    def process_multiple_documents(self, image_paths: List[str]) -> List[Dict[str, Any]]:
        return [self.process_document(p) for p in image_paths if os.path.exists(p)]


if __name__ == "__main__":
    import sys
    if len(sys.argv) > 1:
        ocr = BanglaEnglishOCR()
        res = ocr.process_document(sys.argv[1])
        print(json.dumps(res, indent=2, ensure_ascii=False))
    else:
        print("Usage: python bangla_english_ocr.py <image_path>")
