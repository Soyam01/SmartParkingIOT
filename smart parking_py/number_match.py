# webcam_anpr_match_only_general.py
# Smart Parking - License Plate Recognition & Matching
# General purpose - works with any standard plate format

import cv2
import easyocr
import numpy as np
import time
import psycopg2
from psycopg2 import Error

# ===================== CONFIGURATION =====================
DB_CONFIG = {
    'host': 'localhost',
    'database': 'smart_parking',
    'user': 'postgres',
    'password': 'soyam'
}

CAMERA_INDEX = 0
FRAME_WIDTH = 640
FRAME_HEIGHT = 480
MIN_PLATE_LENGTH = 4            # Lowered to catch short plates too (e.g. US 6 chars, some EU 7–8)
OCR_CONFIDENCE_THRESHOLD = 0.35

# ===================== INITIALIZATION =====================
print("Starting General-Purpose Webcam ANPR - Matching Mode")
print("Accepts any standard license plate format (no country-specific rules)")
reader = easyocr.Reader(['en'], gpu=False)
print("EasyOCR initialized (CPU mode)")

cap = cv2.VideoCapture(CAMERA_INDEX, cv2.CAP_DSHOW)

if not cap.isOpened():
    print(f"ERROR: Cannot open camera at index {CAMERA_INDEX}")
    exit()

cap.set(cv2.CAP_PROP_FRAME_WIDTH, FRAME_WIDTH)
cap.set(cv2.CAP_PROP_FRAME_HEIGHT, FRAME_HEIGHT)

print(f"Camera opened: {FRAME_WIDTH}x{FRAME_HEIGHT}")
print("Press 'q' to quit\n")

# ===================== DATABASE FUNCTIONS =====================
def is_plate_registered(plate_text):
    try:
        conn = psycopg2.connect(**DB_CONFIG)
        cur = conn.cursor()
        cur.execute("""
            SELECT id FROM reservations 
            WHERE plate_number ILIKE %s 
            AND active = true
            LIMIT 1
        """, (plate_text,))
        return cur.fetchone() is not None
    except Error as e:
        print(f"Check error: {e}")
        return False
    finally:
        if 'conn' in locals() and conn:
            cur.close()
            conn.close()

def mark_as_matched(plate_text):
    try:
        conn = psycopg2.connect(**DB_CONFIG)
        cur = conn.cursor()
        cur.execute("""
            UPDATE reservations 
            SET matched_at = CURRENT_TIMESTAMP,
                active = false
            WHERE plate_number ILIKE %s 
            AND active = true
            RETURNING id
        """, (plate_text,))
        
        row = cur.fetchone()
        conn.commit()
        
        if row:
            print(f"→ MATCH SUCCESS: {plate_text} (Reservation ID: {row[0]})")
            return True
        return False
    except Error as e:
        print(f"Update error: {e}")
        return False
    finally:
        if 'conn' in locals() and conn:
            cur.close()
            conn.close()

# ===================== MAIN LOOP =====================
while True:
    ret, frame = cap.read()
    if not ret:
        print("Camera read failed")
        break

    gray = cv2.cvtColor(frame, cv2.COLOR_BGR2GRAY)
    
    # Light preprocessing - helps globally
    gray = cv2.equalizeHist(gray)  # better contrast
    gray = cv2.GaussianBlur(gray, (3, 3), 0)  # reduce noise

    results = reader.readtext(gray, detail=1, paragraph=False, low_text=0.3)

    for (bbox, text, conf) in results:
        # General cleaning - suitable for most countries
        plate_text = text.upper().replace(' ', '').strip()
        
        # Fix common OCR confusions (works worldwide)
        plate_text = plate_text.replace('S', '5') \
                               .replace('I', '1') \
                               .replace('L', '1') \
                               .replace('O', '0') \
                               .replace('B', '8') \
                               .replace('Z', '2') \
                               .replace('Q', '0') \
                               .replace('G', '6') \
                               .replace('T', '1')  # sometimes confused
        
        # Keep only letters + digits (standard for most plates)
        plate_text = ''.join(c for c in plate_text if c.isalnum())
        
        if len(plate_text) < MIN_PLATE_LENGTH or conf < OCR_CONFIDENCE_THRESHOLD:
            continue

        print(f"READ: {plate_text} (conf: {conf:.2f})  [raw: {text}]")

        # Try to match
        if is_plate_registered(plate_text):
            mark_as_matched(plate_text)
        else:
            print(f"   → Not registered or already matched")

    # Live preview
    cv2.imshow("Smart Parking - ANPR Matching", frame)

    if cv2.waitKey(1) & 0xFF == ord('q'):
        break

# Cleanup
cap.release()
cv2.destroyAllWindows()
print("Stopped")