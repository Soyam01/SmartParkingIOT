#include <WiFi.h>
#include <HTTPClient.h>
#include <ESP32Servo.h>
#include <WebServer.h>

// ================== WiFi CONFIG ==================
const char* ssid = "NTFiber_14B8_2.4G";
const char* password = "xm5GEXNk";
const char* springServer = "http://192.168.1.7:8080/api/spot/update";   // ← CHANGE THIS

#define NUM_SPOTS 5

// ================== SAFE PIN CONFIG (5 Spots) ==================
const int trigPins[NUM_SPOTS] = {5,  26,  2, 15, 13};   // Trig
const int echoPins[NUM_SPOTS] = {18, 25, 21, 22, 23};  // Echo

#define SERVO_PIN 12   // Changed to 12 (very safe pin)

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
  moveServoSmooth(0, 90, 18);
  delay(8000);
  Serial.println("🚪 CLOSING MAIN GATE SLOWLY...");
  moveServoSmooth(90, 0, 22);
  Serial.println("✅ MAIN GATE CLOSED");
}

void handleGateOpen() {
  server.send(200, "text/plain", "Opening Gate");
  openMainGate();
}

// ================== SEND SPOT STATUS TO SPRING BOOT ==================
void sendSpotStatus(int spotIndex, const String& status) {
  if (WiFi.status() != WL_CONNECTED) return;

  HTTPClient http;
  
  // Simpler URL for testing
  String url = "http://192.168.1.7:8080/api/spot/update?spot=" + String(spotIndex + 1);
  
  http.begin(url);
  http.addHeader("Content-Type", "application/json");
  String payload = "{\"status\":\"" + status + "\"}";

  int httpCode = http.POST(payload);
  
  if (httpCode > 0) {
    Serial.printf("Spot %d → %s | Spring: %d\n", spotIndex + 1, status.c_str(), httpCode);
  } else {
    Serial.printf("Spot %d → %s | HTTP Failed: %d\n", spotIndex + 1, status.c_str(), httpCode);
  }
  http.end();
}

void setup() {
  Serial.begin(115200);
  delay(1000);
  Serial.println("=== Smart Parking - 5 Spots + 1 Gate ===");

  for (int i = 0; i < NUM_SPOTS; i++) {
    pinMode(trigPins[i], OUTPUT);
    pinMode(echoPins[i], INPUT);
    Serial.printf("Spot %d → Trig:%d | Echo:%d\n", i+1, trigPins[i], echoPins[i]);
  }

  gateServo.attach(SERVO_PIN);
  gateServo.write(0);
  Serial.printf("Main Gate Servo on pin %d\n", SERVO_PIN);

  WiFi.begin(ssid, password);
  Serial.print("Connecting WiFi");
  while (WiFi.status() != WL_CONNECTED) {
    delay(500);
    Serial.print(".");
  }
  Serial.println("\nWiFi Connected! IP: " + WiFi.localIP().toString());

  server.on("/gate/open", HTTP_GET, handleGateOpen);
  server.begin();
  Serial.println("System Ready");
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

    Serial.printf("Spot %d: %.1f cm → %s\n", i+1, distance, status.c_str());
  }

  delay(1000);
}