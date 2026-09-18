"""
Image Preprocessor for Bangladeshi Identity Documents
Quality-based adaptive preprocessing pipeline for NID cards and Birth Certificates.
"""

import os
import cv2
import numpy as np


class ImagePreprocessor:
    def __init__(self, output_dir=None):
        self.output_dir = output_dir or os.path.join(os.path.dirname(__file__), "../output/processed_images")
        os.makedirs(self.output_dir, exist_ok=True)

    def assess_quality(self, image: np.ndarray) -> float:
        """
        Assess image quality using Laplacian variance (sharpness/blurriness)
        and illumination uniformity. Returns a score between 0 and 100.
        """
        if image is None:
            return 0.0

        gray = cv2.cvtColor(image, cv2.COLOR_BGR2GRAY) if len(image.shape) == 3 else image
        blur_var = cv2.Laplacian(gray, cv2.CV_64F).var()

        # Score based on variance: >= 300 is crisp, <= 50 is blurry
        blur_score = min(100.0, (blur_var / 300.0) * 60.0)

        # Contrast assessment via standard deviation
        contrast = gray.std()
        contrast_score = min(40.0, (contrast / 64.0) * 40.0)

        return float(round(blur_score + contrast_score, 2))

    def deskew(self, image: np.ndarray) -> np.ndarray:
        """Correct perspective skew if the card is angled."""
        try:
            gray = cv2.cvtColor(image, cv2.COLOR_BGR2GRAY) if len(image.shape) == 3 else image
            thresh = cv2.threshold(gray, 0, 255, cv2.THRESH_BINARY_INV + cv2.THRESH_OTSU)[1]
            coords = np.column_stack(np.where(thresh > 0))
            if len(coords) < 10:
                return image
            angle = cv2.minAreaRect(coords)[-1]

            if angle < -45:
                angle = -(90 + angle)
            elif angle > 45:
                angle = 90 - angle
            else:
                angle = -angle

            if abs(angle) < 0.8 or abs(angle) > 45:
                return image

            (h, w) = image.shape[:2]
            center = (w // 2, h // 2)
            M = cv2.getRotationMatrix2D(center, angle, 1.0)
            rotated = cv2.warpAffine(image, M, (w, h), flags=cv2.INTER_CUBIC, borderMode=cv2.BORDER_REPLICATE)
            return rotated
        except Exception:
            return image

    def preprocess_image(self, image_input, save_intermediate=False) -> np.ndarray:
        """
        Main quality-based adaptive preprocessing.
        Accepts file path (str) or cv2 image (np.ndarray).
        """
        if isinstance(image_input, str):
            image = cv2.imread(image_input)
            if image is None:
                raise ValueError(f"Could not load image from {image_input}")
        else:
            image = image_input

        quality_score = self.assess_quality(image)
        deskewed = self.deskew(image)
        gray = cv2.cvtColor(deskewed, cv2.COLOR_BGR2GRAY) if len(deskewed.shape) == 3 else deskewed

        if quality_score >= 85:
            # High quality: minimal preprocessing
            clahe = cv2.createCLAHE(clipLimit=1.5, tileGridSize=(8, 8))
            processed = clahe.apply(gray)
        elif quality_score >= 50:
            # Medium quality: bilateral filter + CLAHE
            denoised = cv2.bilateralFilter(gray, 9, 75, 75)
            clahe = cv2.createCLAHE(clipLimit=2.5, tileGridSize=(8, 8))
            processed = clahe.apply(denoised)
        else:
            # Low quality: denoising + sharpening + adaptive binarization
            denoised = cv2.fastNlMeansDenoising(gray, h=10)
            clahe = cv2.createCLAHE(clipLimit=3.0, tileGridSize=(8, 8))
            enhanced = clahe.apply(denoised)
            # Unsharp mask
            gaussian = cv2.GaussianBlur(enhanced, (0, 0), 2.0)
            processed = cv2.addWeighted(enhanced, 1.5, gaussian, -0.5, 0)

        if save_intermediate:
            out_path = os.path.join(self.output_dir, "last_preprocessed.jpg")
            cv2.imwrite(out_path, processed)

        return processed
