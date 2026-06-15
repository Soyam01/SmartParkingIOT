# Smart Parking System

A complete **IoT-based Smart Parking System** with real-time spot monitoring, online reservation, automatic license plate recognition (ANPR), and physical gate control.

---

## 📋 Project Overview

This system allows users to:
- View real-time availability of 5 parking spots via a web dashboard.
- Reserve a spot by entering their vehicle number.
- Automatically detect arriving vehicles using a webcam and open the gate if the vehicle is registered.

---

## ✨ Features

- **5 Parking Spots** with ultrasonic sensors (HC-SR04)
- **Real-time Status Updates** (Free / Occupied / Reserved)
- **Online Reservation System** via Web App
- **Automatic Gate Control** using Servo Motor
- **License Plate Recognition** using Python + EasyOCR
- **16x2 LCD Display** for local status
- **Auto-clear Reservation** when a car parks in a reserved spot

---

## 🛠️ Tech Stack

### Hardware
- ESP32 DevKit
- 5 × HC-SR04 Ultrasonic Sensors
- TowerPro MG90S / SG90 Servo Motor
- 16x2 I2C LCD Display
- External 5V Power Supply

### Software
- **Backend**: Spring Boot + JPA + PostgreSQL
- **Frontend**: Thymeleaf + HTML + CSS
- **IoT Firmware**: Arduino C++ (ESP32)
- **ANPR**: Python + OpenCV + EasyOCR
- **Communication**: HTTP REST API

---

## 📁 Project Structure
Smart-Parking-System/
├── parking_arduino/              # ESP32 Code
│   └── smartparking_all_spots.ino
├── parking_spring/               # Spring Boot Backend
│   ├── src/main/java/...
│   └── src/main/resources/
├── smart parking_py/             # Python ANPR
│   ├── webcam_anpr_match_only_general.py
│   └── requirements.txt
├── README.md
└── database.sql

---

## 🚀 How to Run

### 1. Hardware Setup
- Connect 5 Ultrasonic Sensors to defined pins
- Connect Servo to GPIO 12 (Signal)
- Connect I2C LCD (SDA=32, SCL=33)
- Use **external 5V power** for servo and sensors

### 2. Spring Boot Backend
```bash
cd parking_spring
./mvnw spring-boot:run

3. ESP32 Firmware

Open smartparking_all_spots.ino in Arduino IDE
Update WiFi credentials and Spring Boot IP
Upload to ESP32

4. Python ANPR (License Plate Detection)
Bashcd smart parking_py
pip install -r requirements.txt
python webcam_anpr_match_only_general.py

Contributors

Developed by Soyam Shrestha

---

### How to Use:

1. Copy the entire content above.
2. Create a new file in your project root called **`README.md`**
3. Paste the content and save.
4. Push to GitHub.

Would you like me to also create a `requirements.txt` for the Python part and a simple `database.sql` file?