"""
Streamlit Web Application for Bangladeshi NID & Document OCR
Interactive interface for uploading and analyzing Bangladeshi NID cards and Birth Certificates.
"""

import streamlit as st
import json
import os
import tempfile
from PIL import Image

try:
    from src.bangla_english_ocr import BanglaEnglishOCR
except ImportError:
    import sys
    sys.path.append(os.path.dirname(__file__))
    from src.bangla_english_ocr import BanglaEnglishOCR

st.set_page_config(
    page_title="Bangladeshi NID & Document OCR",
    page_icon="🇧🇩",
    layout="wide"
)

st.title("🇧🇩 Bangladeshi Document OCR & NID Extraction")
st.markdown("Automated identification and extraction of **National ID (NID)** and **Birth Certificate** documents supporting Bangla and English text.")

col1, col2 = st.columns([1, 1])

with col1:
    uploaded_file = st.file_uploader("Upload NID or Document Image", type=["jpg", "jpeg", "png", "webp"])
    if uploaded_file is not None:
        image = Image.open(uploaded_file)
        st.image(image, caption="Uploaded Document", use_column_width=True)

with col2:
    if uploaded_file is not None:
        if st.button("🚀 Extract Information", type="primary"):
            with st.spinner("Processing document with multi-engine OCR..."):
                with tempfile.NamedTemporaryFile(delete=False, suffix=".jpg") as tmp:
                    image.save(tmp.name)
                    tmp_path = tmp.name

                try:
                    ocr = BanglaEnglishOCR()
                    results = ocr.process_document(tmp_path)

                    doc_info = results.get("document_info", {})
                    fields = results.get("extracted_fields", {})

                    st.success(f"Document Recognized: **{doc_info.get('type', 'NID')}** (Confidence: {int(doc_info.get('overall_confidence', 0.8) * 100)}%)")

                    # Metric cards
                    m1, m2 = st.columns(2)
                    m1.metric("NID Number", fields.get("nid_number") or "Not Detected")
                    m2.metric("Date of Birth", fields.get("date_of_birth") or "Not Detected")

                    st.markdown("### Extracted Identity Details")
                    st.table([
                        {"Field": "Bangla Name (বাংলা নাম)", "Value": fields.get("name_bangla") or "—"},
                        {"Field": "English Name", "Value": fields.get("name_english") or "—"},
                        {"Field": "Father's Name (পিতা)", "Value": fields.get("father_name") or "—"},
                        {"Field": "Mother's Name (মাতা)", "Value": fields.get("mother_name") or "—"},
                        {"Field": "Blood Group", "Value": fields.get("blood_group") or "—"},
                    ])

                    st.markdown("### Raw Output (JSON)")
                    st.json(results)

                    st.download_button(
                        label="📥 Download JSON Results",
                        data=json.dumps(results, indent=2, ensure_ascii=False),
                        file_name="nid_ocr_results.json",
                        mime="application/json"
                    )
                except Exception as e:
                    st.error(f"Error during OCR processing: {e}")
                finally:
                    if os.path.exists(tmp_path):
                        os.unlink(tmp_path)
