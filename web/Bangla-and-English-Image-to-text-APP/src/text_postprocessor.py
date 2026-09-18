"""
Text Post-Processor for Bangladeshi Documents
Extracts and validates structured fields from raw OCR output for NID cards and Birth Certificates.
"""

import re
from typing import Dict, Any, List, Optional


class TextPostProcessor:
    BENGALI_DIGITS = {'০': '0', '১': '1', '২': '2', '৩': '3', '৪': '4',
                      '৫': '5', '৬': '6', '৭': '7', '৮': '8', '৯': '9'}

    BENGALI_MONTHS = {
        r'(জানুয়ারি|জানুয়ারী|জানু)': 'Jan',
        r'(ফেব্রুয়ারি|ফেব্রুয়ারী|ফেব্রু)': 'Feb',
        r'(মার্চ)': 'Mar',
        r'(এপ্রিল)': 'Apr',
        r'(মে)': 'May',
        r'(জুন)': 'Jun',
        r'(জুলাই)': 'Jul',
        r'(আগস্ট|আগষ্ট)': 'Aug',
        r'(সেপ্টেম্বর|সেপ্টে)': 'Sep',
        r'(অক্টোবর|অক্টো)': 'Oct',
        r'(নভেম্বর|নভে)': 'Nov',
        r'(ডিসেম্বর|ডিসে)': 'Dec'
    }

    EXCLUDE_KEYWORDS = {
        "Government", "People's", "Republic", "Bangladesh", "National", "ID", "Card",
        "Name", "Father", "Mother", "Husband", "Date", "Birth", "Blood", "Group",
        "গণপ্রজাতন্ত্রী", "বাংলাদেশ", "সরকার", "জাতীয়", "পরিচয়", "পত্র", "নাম",
        "পিতা", "মাতা", "স্বামী", "স্থায়ী", "ঠিকানা", "রক্তের", "গ্রুপ"
    }

    def normalize_text(self, text: str) -> str:
        """Convert Bengali digits to Arabic digits and normalize whitespace."""
        res = text
        for bn_digit, en_digit in self.BENGALI_DIGITS.items():
            res = res.replace(bn_digit, en_digit)
        for pattern, eng in self.BENGALI_MONTHS.items():
            res = re.sub(pattern, eng, res, flags=re.IGNORECASE)
        return res

    def extract_nid_number(self, lines: List[str]) -> Optional[str]:
        """
        Extract NID number with priority on Smart NID (10 digits) or Old NID (13/17 digits).
        Excludes mobile numbers (starting with 01).
        """
        nid_indicators = ["NID NO", "NID No", "National ID", "ID NO", "NID", "NO:", "জাতীয় পরিচয়", "পরিচয়পত্র নম্বর", "আইডি নম্বর", "আইডি নং"]

        # Strategy 1: Lines following or containing NID indicators
        for i, line in enumerate(lines):
            norm = self.normalize_text(line)
            if any(ind.lower() in norm.lower() for ind in nid_indicators):
                digits = re.sub(r'\D', '', norm)
                if len(digits) in (10, 13, 17) and not digits.startswith("01"):
                    return digits
                if i + 1 < len(lines):
                    next_digits = re.sub(r'\D', '', self.normalize_text(lines[i + 1]))
                    if len(next_digits) in (10, 13, 17) and not next_digits.startswith("01"):
                        return next_digits

        # Strategy 2: Spaced digits
        for line in lines:
            norm = self.normalize_text(line)
            spaced = re.findall(r'\b(?:\d[\s\-]*){10,17}\b', norm)
            for m in spaced:
                cleaned = re.sub(r'\D', '', m)
                if len(cleaned) in (10, 13, 17) and not cleaned.startswith("01"):
                    return cleaned

        # Strategy 3: Regex fallbacks
        for line in lines:
            norm = self.normalize_text(line)
            m10 = re.search(r'\b\d{10}\b', norm)
            if m10 and not m10.group(0).startswith("01"):
                return m10.group(0)
            m17 = re.search(r'\b(19\d{2}|20\d{2})\d{13}\b', norm)
            if m17:
                return m17.group(0)
            m13 = re.search(r'\b\d{13}\b', norm)
            if m13:
                return m13.group(0)

        return None

    def extract_dob(self, lines: List[str]) -> Optional[str]:
        """Extract Date of Birth from Bengali or English formatted string."""
        date_pattern = re.compile(r'\b(\d{1,2})[\s\-\/\.]*(Jan|Feb|Mar|Apr|May|Jun|Jul|Aug|Sep|Oct|Nov|Dec)[a-z]*[\s\-\/\.]*(\d{4})\b', re.I)
        numeric_pattern = re.compile(r'\b(\d{1,2})[\/\-\.](\d{1,2})[\/\-\.](\d{4})\b')
        year_first_pattern = re.compile(r'\b(\d{4})[\/\-\.](\d{1,2})[\/\-\.](\d{1,2})\b')

        dob_keywords = ["Date of Birth", "Birth", "DOB", "জন্ম তারিখ", "জন্ম", "তারিখ"]

        for i, line in enumerate(lines):
            norm = self.normalize_text(line)
            if any(kw.lower() in norm.lower() for kw in dob_keywords):
                m = date_pattern.search(norm) or numeric_pattern.search(norm) or year_first_pattern.search(norm)
                if m:
                    return m.group(0)
                if i + 1 < len(lines):
                    next_norm = self.normalize_text(lines[i + 1])
                    m_next = date_pattern.search(next_norm) or numeric_pattern.search(next_norm) or year_first_pattern.search(next_norm)
                    if m_next:
                        return m_next.group(0)

        for line in lines:
            norm = self.normalize_text(line)
            m = date_pattern.search(norm) or numeric_pattern.search(norm)
            if m:
                return m.group(0)

        return None

    def extract_names(self, lines: List[str]) -> Dict[str, Optional[str]]:
        """Extract English Name, Bangla Name, Father Name, and Mother Name."""
        names = {
            "name_english": None,
            "name_bangla": None,
            "father_name": None,
            "mother_name": None
        }

        for i, line in enumerate(lines):
            trimmed = line.strip()

            # English Name
            if re.match(r'^(?:Name|NAME)\s*[:\-\.]', trimmed, re.I):
                candidate = re.sub(r'^(?:Name|NAME)\s*[:\-\.]\s*', '', trimmed, flags=re.I).strip()
                if len(candidate) > 2 and not any(kw.lower() in candidate.lower() for kw in self.EXCLUDE_KEYWORDS):
                    names["name_english"] = candidate
            elif trimmed.lower() == "name" and i + 1 < len(lines):
                candidate = lines[i + 1].strip().lstrip(":-. ")
                if len(candidate) > 2 and not any(kw.lower() in candidate.lower() for kw in self.EXCLUDE_KEYWORDS):
                    names["name_english"] = candidate

            # Bangla Name
            if re.match(r'^(?:নাম)\s*[:\-\.]', trimmed):
                candidate = re.sub(r'^(?:নাম)\s*[:\-\.]\s*', '', trimmed).strip()
                if len(candidate) > 2 and not any(kw in candidate for kw in self.EXCLUDE_KEYWORDS):
                    names["name_bangla"] = candidate
            elif trimmed == "নাম" and i + 1 < len(lines):
                candidate = lines[i + 1].strip().lstrip(":-. ")
                if len(candidate) > 2 and not any(kw in candidate for kw in self.EXCLUDE_KEYWORDS):
                    names["name_bangla"] = candidate

            # Father's Name
            if re.match(r'^(?:পিতা|Father)\s*[:\-\.]', trimmed, re.I):
                candidate = re.sub(r'^(?:পিতা|Father)\s*[:\-\.]\s*', '', trimmed, flags=re.I).strip()
                if len(candidate) > 2:
                    names["father_name"] = candidate

            # Mother's Name
            if re.match(r'^(?:মাতা|Mother)\s*[:\-\.]', trimmed, re.I):
                candidate = re.sub(r'^(?:মাতা|Mother)\s*[:\-\.]\s*', '', trimmed, flags=re.I).strip()
                if len(candidate) > 2:
                    names["mother_name"] = candidate

        # Fallback for English Name: uppercase English line
        if not names["name_english"]:
            for line in lines:
                clean = line.strip()
                if re.match(r'^[A-Z\.\s]{4,35}$', clean) and not any(kw.upper() in clean for kw in self.EXCLUDE_KEYWORDS):
                    names["name_english"] = clean
                    break

        return names

    def extract_blood_group(self, text: str) -> Optional[str]:
        """Detect blood group like O+, A+, B+, AB+, etc."""
        m = re.search(r'\b(A|B|AB|O)\s*([+-]|ve|\+ve|\-ve)\b', text, re.I)
        if m:
            group = m.group(1).upper()
            sign = "+" if "+" in m.group(2) or "pos" in m.group(2).lower() else "-"
            return f"{group}{sign}"
        return None

    def process_extracted_text(self, raw_text: str) -> Dict[str, Any]:
        """Process full raw OCR text and return structured JSON."""
        lines = [line.strip() for line in raw_text.splitlines() if line.strip()]

        nid = self.extract_nid_number(lines)
        dob = self.extract_dob(lines)
        names = self.extract_names(lines)
        blood_group = self.extract_blood_group(raw_text)

        # Document type inference
        doc_type = "UNKNOWN"
        if nid:
            if len(nid) == 10:
                doc_type = "SMART_NID"
            elif len(nid) in (13, 17):
                doc_type = "OLD_NID"
        elif "জন্ম নিবন্ধন" in raw_text or "Birth" in raw_text:
            doc_type = "BIRTH_CERTIFICATE"

        validations = {
            "nid_number": "valid" if nid else "missing",
            "name_english": "found" if names["name_english"] else "missing",
            "name_bangla": "found" if names["name_bangla"] else "missing",
            "father_name": "found" if names["father_name"] else "missing",
            "mother_name": "found" if names["mother_name"] else "missing",
            "date_of_birth": "found" if dob else "missing"
        }

        return {
            "document_info": {
                "type": doc_type,
                "overall_confidence": 0.90 if nid and (names["name_english"] or names["name_bangla"]) else 0.65,
                "processing_status": "success"
            },
            "extracted_fields": {
                "nid_number": nid,
                "name_bangla": names["name_bangla"],
                "name_english": names["name_english"],
                "father_name": names["father_name"],
                "mother_name": names["mother_name"],
                "date_of_birth": dob,
                "blood_group": blood_group,
                "address": None
            },
            "validation_results": validations
        }
