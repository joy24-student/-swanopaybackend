"""
Bangladeshi OCR System - Optical Character Recognition for Bangladeshi Documents
Supports mixed Bangla and English text extraction from National ID cards and Birth Certificates.
"""

from .bangla_english_ocr import BanglaEnglishOCR
from .text_postprocessor import TextPostProcessor
from .image_preprocessor import ImagePreprocessor

__all__ = ["BanglaEnglishOCR", "TextPostProcessor", "ImagePreprocessor"]
