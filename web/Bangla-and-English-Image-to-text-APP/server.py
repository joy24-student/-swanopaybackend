"""
FastAPI Microservice for Bangladeshi NID & Document OCR
Provides REST API endpoints for image-to-text extraction of Bangladeshi NID cards and Birth Certificates.
"""

import os
import sys
import base64
import tempfile
from typing import Optional, Dict, Any

# Add current directory and src to sys.path
BASE_DIR = os.path.dirname(os.path.abspath(__file__))
if BASE_DIR not in sys.path:
    sys.path.insert(0, BASE_DIR)

from src.bangla_english_ocr import BanglaEnglishOCR

try:
    from fastapi import FastAPI, HTTPException, UploadFile, File
    from fastapi.middleware.cors import CORSMiddleware
    from pydantic import BaseModel
    import uvicorn
    FASTAPI_AVAILABLE = True
except ImportError:
    FASTAPI_AVAILABLE = False

if FASTAPI_AVAILABLE:
    app = FastAPI(
        title="Bangladeshi NID & Document OCR Service",
        version="1.0.0",
        description="Extracts NID numbers, Bangla and English names, DOB, and details from identity documents."
    )

    app.add_middleware(
        CORSMiddleware,
        allow_origins=["*"],
        allow_credentials=True,
        allow_methods=["*"],
        allow_headers=["*"],
    )

    ocr_engine = BanglaEnglishOCR()

    class ExtractionRequest(BaseModel):
        image_base64: Optional[str] = None
        front_base64: Optional[str] = None
        back_base64: Optional[str] = None
        doc_type: Optional[str] = "NID"

    @app.get("/")
    @app.get("/health")
    def health_check():
        return {
            "status": "ok",
            "service": "Bangladeshi NID OCR Microservice",
            "version": "1.0.0",
            "supported_docs": ["Smart NID (10 Digits)", "Old NID (13/17 Digits)", "Birth Certificate"],
            "languages": ["bn", "en"]
        }

    def _process_image_buffer(image_bytes: bytes) -> Dict[str, Any]:
        with tempfile.NamedTemporaryFile(delete=False, suffix=".jpg") as tmp:
            tmp.write(image_bytes)
            tmp_path = tmp.name
        try:
            return ocr_engine.process_document(tmp_path)
        finally:
            if os.path.exists(tmp_path):
                os.unlink(tmp_path)

    @app.post("/api/extract")
    async def extract_nid(request: ExtractionRequest):
        raw_b64 = request.image_base64 or request.front_base64 or request.back_base64
        if not raw_b64:
            raise HTTPException(status_code=400, detail="An image base64 string is required (image_base64, front_base64, or back_base64).")

        clean_b64 = raw_b64.split(",")[-1]
        try:
            image_bytes = base64.b64decode(clean_b64)
        except Exception as e:
            raise HTTPException(status_code=400, detail=f"Invalid base64 encoding: {e}")

        if len(image_bytes) == 0:
            raise HTTPException(status_code=400, detail="Decoded image buffer is empty.")

        try:
            result = _process_image_buffer(image_bytes)
            fields = result.get("extracted_fields", {})
            doc_info = result.get("document_info", {})
            return {
                "ok": True,
                "nid_number": fields.get("nid_number"),
                "name_bangla": fields.get("name_bangla"),
                "name_english": fields.get("name_english"),
                "father_name": fields.get("father_name"),
                "mother_name": fields.get("mother_name"),
                "date_of_birth": fields.get("date_of_birth"),
                "blood_group": fields.get("blood_group"),
                "doc_type": doc_info.get("type", "UNKNOWN"),
                "confidence": doc_info.get("overall_confidence", 0.0),
                "raw_text": result.get("metadata", {}).get("raw_text", ""),
                "full_result": result
            }
        except Exception as e:
            raise HTTPException(status_code=500, detail=f"OCR extraction failed: {str(e)}")

    @app.post("/api/extract-upload")
    async def extract_nid_upload(file: UploadFile = File(...)):
        image_bytes = await file.read()
        if not image_bytes:
            raise HTTPException(status_code=400, detail="Uploaded file is empty.")

        try:
            result = _process_image_buffer(image_bytes)
            fields = result.get("extracted_fields", {})
            doc_info = result.get("document_info", {})
            return {
                "ok": True,
                "nid_number": fields.get("nid_number"),
                "name_bangla": fields.get("name_bangla"),
                "name_english": fields.get("name_english"),
                "father_name": fields.get("father_name"),
                "mother_name": fields.get("mother_name"),
                "date_of_birth": fields.get("date_of_birth"),
                "blood_group": fields.get("blood_group"),
                "doc_type": doc_info.get("type", "UNKNOWN"),
                "confidence": doc_info.get("overall_confidence", 0.0),
                "full_result": result
            }
        except Exception as e:
            raise HTTPException(status_code=500, detail=f"OCR extraction failed: {str(e)}")

def main():
    port = int(os.environ.get("PORT", 5055))
    host = os.environ.get("HOST", "0.0.0.0")
    print(f"[*] Starting Bangladeshi NID OCR server on {host}:{port}...")
    if FASTAPI_AVAILABLE:
        uvicorn.run("server:app", host=host, port=port, reload=False)
    else:
        print("[!] FastAPI/uvicorn not found. Install with: pip install fastapi uvicorn")
        sys.exit(1)

if __name__ == "__main__":
    main()
