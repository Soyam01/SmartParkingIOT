# webcam_anpr_match_only_general.py
# Smart Parking - License Plate Recognition & Gate Control

import cv2
import easyocr
import numpy as np
import time
import psycopg2
from psycopg2 import Error
from datetime import datetime

# Try to import requests, show helpful message if missing
try:
    import requests
except ImportError:
    print("❌ 'requests' module not found!")
    print("Please install it by running: pip install requests")
    print("Then run this script again.")
    exit(1)

# ===================== CONFIGURATION =====================
DB_CONFIG = {
    'host': 'localhost',
    'database': 'smart_parking',
    'user': 'postgres',
    'password': 'soyam'
}

# Camera Settings
CAMERA_INDEX = 0
FRAME_WIDTH = 640
FRAME_HEIGHT = 480
MIN_PLATE_LENGTH = 4
OCR_CONFIDENCE_THRESHOLD = 0.35

# ESP32 Gate Control - UPDATE THIS WITH YOUR ESP32 IP
ESP32_GATE_URL = "http://192.168.1.14:80/gate/open"   # ←←← Change to your ESP32 IP

print("🚀 Starting Smart Parking ANPR with Gate Control")
print(f"Gate endpoint: {ESP32_GATE_URL}")
print("Press 'q' to quit\n")

# ===================== INITIALIZATION =====================
reader = easyocr.Reader(['en'], gpu=False)
print("EasyOCR initialized (CPU mode)")

cap = cv2.VideoCapture(CAMERA_INDEX, cv2.CAP_DSHOW)

if not cap.isOpened():
    print(f"ERROR: Cannot open camera at index {CAMERA_INDEX}")
    exit()

cap.set(cv2.CAP_PROP_FRAME_WIDTH, FRAME_WIDTH)
cap.set(cv2.CAP_PROP_FRAME_HEIGHT, FRAME_HEIGHT)

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
        print(f"DB Check Error: {e}")
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
            print(f"✅ MATCH SUCCESS: {plate_text} (ID: {row[0]})")
            return True
        return False
    except Error as e:
        print(f"DB Update Error: {e}")
        return False
    finally:
        if 'conn' in locals() and conn:
            cur.close()
            conn.close()

# ===================== GATE CONTROL =====================
def open_gate():
    """Send command to ESP32 to open the servo gate"""
    try:
        print(f"📡 Sending gate open command to ESP32...")
        response = requests.get(ESP32_GATE_URL, timeout=10)
        if response.status_code == 200:
            print(f"✅ GATE OPENED SUCCESSFULLY at {datetime.now().strftime('%H:%M:%S')}")
            return True
        else:
            print(f"⚠️ Gate command failed: HTTP {response.status_code}")
            return False
    except requests.exceptions.RequestException as e:
        print(f"❌ Failed to connect to ESP32: {e}")
        return False

# ===================== MAIN LOOP =====================
while True:
    ret, frame = cap.read()
    if not ret:
        print("Camera read failed")
        break

    gray = cv2.cvtColor(frame, cv2.COLOR_BGR2GRAY)
    gray = cv2.equalizeHist(gray)
    gray = cv2.GaussianBlur(gray, (3, 3), 0)

    results = reader.readtext(gray, detail=1, paragraph=False, low_text=0.3)

    for (bbox, text, conf) in results:
        plate_text = text.upper().replace(' ', '').strip()
        
        # OCR cleanup
        plate_text = plate_text.replace('S', '5') \
                               .replace('I', '1') \
                               .replace('L', '1') \
                               .replace('O', '0') \
                               .replace('B', '8') \
                               .replace('Z', '2') \
                               .replace('Q', '0') \
                               .replace('G', '6') \
                               .replace('T', '1')
        
        plate_text = ''.join(c for c in plate_text if c.isalnum())

        if len(plate_text) < MIN_PLATE_LENGTH or conf < OCR_CONFIDENCE_THRESHOLD:
            continue

        print(f"READ: {plate_text} (conf: {conf:.2f})")

        if is_plate_registered(plate_text):
            if mark_as_matched(plate_text):
                open_gate()                    # ← Open physical gate
        else:
            print(f"   → Not registered or already matched")

    # Live preview
    cv2.imshow("Smart Parking - ANPR + Gate Control", frame)

    if cv2.waitKey(1) & 0xFF == ord('q'):
        break

# ===================== CLEANUP =====================
cap.release()
cv2.destroyAllWindows()
print("Smart Parking ANPR System Stopped")