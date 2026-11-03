# webcam_anpr_match_only_general.py
# Smart Parking - License Plate Recognition & Gate Control

import sys
import re
import time
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
SPRING_SERVER_URL = "http://localhost:8080"

# Camera Settings
CAMERA_INDEX = 0
FRAME_WIDTH = 640
FRAME_HEIGHT = 480
MIN_PLATE_LENGTH = 4
OCR_CONFIDENCE_THRESHOLD = 0.35
PLATE_RETRY_SECONDS = 3

# ESP32 Gate Control - UPDATE THIS WITH YOUR ESP32 IP
ESP32_GATE_URL = "http://10.10.55.40:80/gate/open"   # ←←← Change to your ESP32 IP

# ===================== ARRIVAL API =====================
def match_arrival(plate_text):
    """Match the detected plate and notify the website of its assigned spot."""
    try:
        response = requests.post(
            f"{SPRING_SERVER_URL}/api/arrivals/match",
            json={"plateNumber": plate_text},
            timeout=5,
        )
        if response.status_code == 200:
            arrival = response.json()
            if (not isinstance(arrival, dict)
                    or not isinstance(arrival.get('reservationId'), int)
                    or isinstance(arrival.get('reservationId'), bool)
                    or arrival['reservationId'] <= 0
                    or not isinstance(arrival.get('spotNumber'), str)
                    or not re.fullmatch(r'SPOT-[1-5]', arrival['spotNumber'])
                    or not isinstance(arrival.get('plateNumber'), str)
                    or not arrival['plateNumber']):
                print("Invalid arrival response; gate will not open.")
                return None
            print(f"MATCH SUCCESS: {arrival['plateNumber']} -> {arrival['spotNumber']}")
            return arrival
        if response.status_code == 404:
            print("No active reservation for this vehicle.")
        elif response.status_code == 409:
            print(f"Arrival blocked: {response.json().get('message', 'Reservation unavailable')}")
        else:
            print(f"Match request failed: HTTP {response.status_code}")
    except (requests.exceptions.RequestException, ValueError, AttributeError) as error:
        print(f"Failed to match arrival with the Spring website: {error}")
    return None

# ===================== GATE CONTROL =====================
def open_gate():
    """Send command to ESP32 to open the servo gate"""
    try:
        print(f"📡 Sending gate open command to ESP32...")
        response = requests.get(ESP32_GATE_URL, timeout=10)
        if response.status_code == 200:
            print(f"Gate open command accepted at {datetime.now().strftime('%H:%M:%S')}")
            return True
        else:
            print(f"⚠️ Gate command failed: HTTP {response.status_code}")
            return False
    except requests.exceptions.RequestException as e:
        print(f"❌ Failed to connect to ESP32: {e}")
        return False

def main():
    # Importing API helpers for tests must not initialize OCR or open the camera.
    try:
        import cv2
    except ModuleNotFoundError:
        print("OpenCV is not installed for this Python interpreter.")
        print(f"Install it with:\n\n{sys.executable} -m pip install opencv-python\n")
        return 1
    import easyocr

    print("Starting Smart Parking ANPR with Gate Control")
    print(f"Spring website: {SPRING_SERVER_URL}")
    print(f"Gate endpoint: {ESP32_GATE_URL}")
    print("Press 'q' to quit\n")
    reader = easyocr.Reader(['en'], gpu=False)
    print("EasyOCR initialized (CPU mode)")
    cap = cv2.VideoCapture(CAMERA_INDEX, cv2.CAP_DSHOW)
    retry_after = {}
    opened_reservations = set()

    try:
        if not cap.isOpened():
            print(f"ERROR: Cannot open camera at index {CAMERA_INDEX}")
            return 1
        cap.set(cv2.CAP_PROP_FRAME_WIDTH, FRAME_WIDTH)
        cap.set(cv2.CAP_PROP_FRAME_HEIGHT, FRAME_HEIGHT)

        while True:
            ret, frame = cap.read()
            if not ret:
                print("Camera read failed")
                break
            gray = cv2.cvtColor(frame, cv2.COLOR_BGR2GRAY)
            gray = cv2.equalizeHist(gray)
            gray = cv2.GaussianBlur(gray, (3, 3), 0)
            results = reader.readtext(gray, detail=1, paragraph=False, low_text=0.3)
            now = time.monotonic()
            retry_after = {plate: deadline for plate, deadline in retry_after.items() if deadline > now}

            for bbox, text, conf in results:
                # Preserve real letters: changing B to 8 or S to 5 breaks booked plates.
                plate_text = re.sub(r'[^a-zA-Z0-9]', '', text).upper()
                if len(plate_text) < MIN_PLATE_LENGTH or conf < OCR_CONFIDENCE_THRESHOLD:
                    continue
                if time.monotonic() < retry_after.get(plate_text, 0):
                    continue
                retry_after[plate_text] = time.monotonic() + PLATE_RETRY_SECONDS
                print(f"READ: {plate_text} (conf: {conf:.2f})")
                arrival = match_arrival(plate_text)
                if arrival and arrival['reservationId'] not in opened_reservations:
                    if open_gate():
                        opened_reservations.add(arrival['reservationId'])

            cv2.imshow("Smart Parking - ANPR + Gate Control", frame)
            if cv2.waitKey(1) & 0xFF == ord('q'):
                break
    finally:
        cap.release()
        cv2.destroyAllWindows()
        print("Smart Parking ANPR System Stopped")
    return 0


if __name__ == '__main__':
    raise SystemExit(main())
