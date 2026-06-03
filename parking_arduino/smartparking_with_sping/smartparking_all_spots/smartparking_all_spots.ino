#include <WiFi.h>
#include <HTTPClient.h>
#include <ESP32Servo.h>
#include <WebServer.h>
#include <LiquidCrystal_I2C.h>
#include <Wire.h>

// ================== WiFi CONFIG ==================
const char* ssid = "Virinchi_Guest";
const char* password = "welcome2virinchi";

// Use this variable - Change only here if IP changes
const char* springServer = "http://10.10.51.143:8080";   // ← Your current laptop IP

#define NUM_SPOTS 5

// ================== YOUR EXISTING PIN CONFIG (UNCHANGED) ==================
const int trigPins[NUM_SPOTS] = {5, 26, 2, 15, 13};
const int echoPins[NUM_SPOTS] = {18, 25, 21, 22, 23};

#define SERVO_PIN 12

// ================== LCD PINS (Safe) ==================
#define LCD_SDA 32
#define LCD_SCL 33
LiquidCrystal_I2C lcd(0x27, 16, 2);   // Try 0x3F if not working

Servo gateServo;
WebServer server(80);

#define OCCUPIED_DISTANCE_CM 10.0

// ================== SMOOTH GATE ==================
void moveServoSmooth(int startAngle, int endAngle, int delayMs = 20) {
  if (startAngle < endAngle) {
    for (int pos = startAngle; pos <= endAngle; pos++) {
      gateServo.write(pos);
      delay(delayMs);
    }
  } else {
    for (int pos = startAngle; pos >= endAngle; pos--) {
      gateServo.write(pos);
      delay(delayMs);
    }
  }
}

void openMainGate() {
  Serial.println("🚪 OPENING MAIN GATE SLOWLY...");
  lcd.clear();
  lcd.setCursor(0,0);
  lcd.print("Opening Gate...");
  
  moveServoSmooth(0, 90, 18);
  delay(8000);
  
  Serial.println("🚪 CLOSING MAIN GATE SLOWLY...");
  lcd.clear();
  lcd.setCursor(0,0);
  lcd.print("Closing Gate...");
  moveServoSmooth(90, 0, 22);
  Serial.println("✅ MAIN GATE CLOSED");
  
  lcd.clear();
  lcd.setCursor(0,0);
  lcd.print("Gate Closed");
}

// ================== HTTP HANDLER ==================
void handleGateOpen() {
  server.send(200, "text/plain", "Opening Gate");
  openMainGate();
}

// ================== IMPROVED SEND STATUS ==================
void sendSpotStatus(int spotIndex, const String& status) {
  if (WiFi.status() != WL_CONNECTED) {
    Serial.println("WiFi not connected");
    return;
  }

  HTTPClient http;
  String url = String(springServer) + "/api/spot/update?spot=" + String(spotIndex + 1);
  
  Serial.printf("Sending -> Spot %d | %s | URL: %s\n", spotIndex + 1, status.c_str(), url.c_str());

  http.begin(url);
  http.addHeader("Content-Type", "application/json");
  String payload = "{\"status\":\"" + status + "\"}";

  int httpCode = http.POST(payload);
  
  if (httpCode > 0) {
    Serial.printf("Spot %d → %s | SUCCESS | Code: %d\n", spotIndex + 1, status.c_str(), httpCode);
  } else {
    Serial.printf("Spot %d → %s | FAILED | Error: %s\n", 
                  spotIndex + 1, status.c_str(), http.errorToString(httpCode).c_str());
  }
  
  http.end();
}

void setup() {
  Serial.begin(115200);
  delay(1000);
  Serial.println("=== Smart Parking - 5 Spots + LCD ===");

  // Initialize LCD
  Wire.begin(LCD_SDA, LCD_SCL);
  lcd.init();
  lcd.backlight();
  lcd.clear();
  lcd.print(" Smart Parking");
  lcd.setCursor(0,1);
  lcd.print(" Initializing...");
  delay(2000);

  // Initialize Sensors
  for (int i = 0; i < NUM_SPOTS; i++) {
    pinMode(trigPins[i], OUTPUT);
    pinMode(echoPins[i], INPUT);
    Serial.printf("Spot %d → Trig:%d | Echo:%d\n", i+1, trigPins[i], echoPins[i]);
  }

  // Servo
  gateServo.attach(SERVO_PIN);
  gateServo.write(0);
  Serial.println("Servo initialized on pin 12");

  // WiFi
  WiFi.begin(ssid, password);
  Serial.print("Connecting WiFi");
  while (WiFi.status() != WL_CONNECTED) {
    delay(500);
    Serial.print(".");
  }
  Serial.println("\nWiFi Connected! IP: " + WiFi.localIP().toString());

  lcd.clear();
  lcd.print("WiFi Connected!");
  delay(1500);

  server.on("/gate/open", HTTP_GET, handleGateOpen);
  server.begin();
  Serial.println("System Ready");

  lcd.clear();
  lcd.print("System Ready");
  lcd.setCursor(0,1);
  lcd.print("Waiting for cars");
}

void loop() {
  server.handleClient();

  for (int i = 0; i < NUM_SPOTS; i++) {
    digitalWrite(trigPins[i], LOW);
    delayMicroseconds(3);
    digitalWrite(trigPins[i], HIGH);
    delayMicroseconds(10);
    digitalWrite(trigPins[i], LOW);

    long duration = pulseIn(echoPins[i], HIGH, 8000);
    float distance = (duration == 0) ? 999.0 : duration * 0.0343 / 2.0;

    String status = (distance <= OCCUPIED_DISTANCE_CM && distance >= 2.0) ? "occupied" : "free";

    static String lastStatus[5] = {"","","","",""};
    if (status != lastStatus[i]) {
      sendSpotStatus(i, status);
      lastStatus[i] = status;
    }

    Serial.printf("Spot %d: %.1f cm → %s\n", i + 1, distance, status.c_str());
  }

  delay(1000);
}