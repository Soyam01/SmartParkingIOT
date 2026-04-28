# webcam_anpr.py
# Smart Parking - License Plate Recognition from External Webcam
# Saves detections to PostgreSQL database

import cv2
import easyocr
import numpy as np
import time
import psycopg2
from psycopg2 import Error
from datetime import datetime

# ===================== CONFIGURATION =====================
# PostgreSQL connection settings - CHANGE THESE TO YOUR VALUES
DB_CONFIG = {
    'host': 'localhost',
    'database': 'smart_parking',
    'user': 'postgres',          # ← your PostgreSQL username
    'password': 'soyam'  # ← your PostgreSQL password
}

# Webcam settings
CAMERA_INDEX = 0                # Your external webcam (built-in disabled)
FRAME_WIDTH = 640
FRAME_HEIGHT = 480
MIN_PLATE_LENGTH = 5            # Minimum characters to consider valid plate
OCR_CONFIDENCE_THRESHOLD = 0.4  # Minimum confidence to save (0.0–1.0)

# Debounce - avoid saving the same plate many times in short period
DEBOUNCE_SECONDS = 3.0

# ===================== INITIALIZATION =====================
print("Starting Webcam ANPR system...")

# Initialize EasyOCR (English + numbers)
reader = easyocr.Reader(['en'], gpu=False)
print("EasyOCR initialized (CPU mode)")

# Initialize webcam with DirectShow backend (usually best for USB cams on Windows)
cap = cv2.VideoCapture(CAMERA_INDEX, cv2.CAP_DSHOW)

if not cap.isOpened():
    print(f"ERROR: Could not open webcam at index {CAMERA_INDEX}")
    print("Try index 0 or 1, or check Device Manager / drivers")
    exit()

# Set resolution for better performance
cap.set(cv2.CAP_PROP_FRAME_WIDTH, FRAME_WIDTH)
cap.set(cv2.CAP_PROP_FRAME_HEIGHT, FRAME_HEIGHT)

print(f"Webcam opened successfully at {FRAME_WIDTH}x{FRAME_HEIGHT}")
print("ANPR running → saving detections to PostgreSQL")
print("Press 'q' to quit")

# ===================== DATABASE FUNCTION =====================
def save_to_db(plate_text, confidence=None):
    """Insert detected plate into PostgreSQL"""
    connection = None
    try:
        connection = psycopg2.connect(**DB_CONFIG)
        cursor = connection.cursor()
        
        query = """
        INSERT INTO license_plates 
        (plate_number, confidence, detected_at, source)
        VALUES (%s, %s, %s, %s)
        RETURNING id;
        """
        
        now = datetime.now()
        
        # IMPORTANT FIX: Convert numpy float64 → native Python float
        confidence_py = float(confidence) if confidence is not None else None
        
        cursor.execute(query, (plate_text, confidence_py, now, 'webcam'))
        inserted_id = cursor.fetchone()[0]
        
        connection.commit()
        
        conf_str = f"{confidence_py:.2f}" if confidence_py is not None else "N/A"
        print(f"[{now.strftime('%Y-%m-%d %H:%M:%S')}] SAVED → {plate_text} "
              f"(conf: {conf_str}) - ID: {inserted_id}")
              
    except Error as e:
        print(f"Database error: {e}")
    except Exception as e:
        print(f"Unexpected error during save: {e}")
    finally:
        if connection and connection.closed == 0:
            cursor.close()
            connection.close()

# ===================== MAIN LOOP =====================
last_detection_time = 0

while True:
    ret, frame = cap.read()
    if not ret:
        print("Failed to capture frame - exiting")
        break

    # Preprocess for OCR
    gray = cv2.cvtColor(frame, cv2.COLOR_BGR2GRAY)

    # Run OCR
    results = reader.readtext(gray, detail=1, paragraph=False)

    current_time = time.time()

    for (bbox, text, conf) in results:
        plate_text = text.upper().replace(' ', '').strip()
        
        if len(plate_text) >= MIN_PLATE_LENGTH and conf >= OCR_CONFIDENCE_THRESHOLD:
            # Debounce
            if current_time - last_detection_time > DEBOUNCE_SECONDS:
                print(f"DETECTED: {plate_text} (confidence: {conf:.2f})")
                # Convert numpy float → Python float before saving
                save_to_db(plate_text, float(conf))
                last_detection_time = current_time

    # Show live preview (comment out these lines if you run headless)
    cv2.imshow("Smart Parking - Webcam ANPR", frame)

    # Press 'q' to quit
    if cv2.waitKey(1) & 0xFF == ord('q'):
        break

# ===================== CLEANUP =====================
cap.release()
cv2.destroyAllWindows()
print("Webcam ANPR stopped")