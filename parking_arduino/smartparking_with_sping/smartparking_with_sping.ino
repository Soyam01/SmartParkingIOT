#include <WiFi.h>
#include <HTTPClient.h>
#include <ESP32Servo.h>
#include <WebServer.h>        // Built-in, no need to install

// ================== CONFIG ==================
const char* ssid = "Virinchi_2nd_2.4g";
const char* password = "virinchi@2025";

const char* springServer = "http://10.5.48.108:8080/api/spot/update";  // Your laptop IP

// Pins
#define TRIG_PIN   5
#define ECHO_PIN   18
#define SERVO_PIN  13

Servo myservo;
WebServer server(80);

#define OCCUPIED_DISTANCE_CM 10.0

// ================== SMOOTH SERVO CONTROL ==================
void moveServoSmooth(int startAngle, int endAngle, int stepDelay = 15) {
  Serial.printf("Moving servo from %d° to %d° slowly...\n", startAngle, endAngle);
  
  if (startAngle < endAngle) {
    for (int pos = startAngle; pos <= endAngle; pos += 1) {
      myservo.write(pos);
      delay(stepDelay);           // ← Change this to control speed
    }
  } else {
    for (int pos = startAngle; pos >= endAngle; pos -= 1) {
      myservo.write(pos);
      delay(stepDelay);
    }
  }
  Serial.println("Servo movement completed");
}

// Open gate slowly (like real parking barrier)
void openGate() {
  Serial.println("🚪 OPENING GATE SLOWLY...");
  moveServoSmooth(0, 90, 20);     // Slow open (20ms per degree)
  delay(8000);                    // Keep gate open for 8 seconds (you can change)
  Serial.println("🚪 CLOSING GATE SLOWLY...");
  moveServoSmooth(90, 0, 25);     // Slightly slower close
  Serial.println("🚪 GATE CLOSED");
}

void handleGateOpen() {
  server.send(200, "text/plain", "Gate Opening");
  openGate();
}

void sendStatusToSpring(const String& status) {
  if (WiFi.status() != WL_CONNECTED) return;
  
  HTTPClient http;
  http.begin(springServer);
  http.addHeader("Content-Type", "application/json");
  String payload = "{\"status\":\"" + status + "\"}";
  
  int httpCode = http.POST(payload);
  if (httpCode > 0) {
    Serial.printf("Spring: %d\n", httpCode);
  } else {
    Serial.printf("HTTP failed: %s\n", http.errorToString(httpCode).c_str());
  }
  http.end();
}

void setup() {
  Serial.begin(115200);
  pinMode(TRIG_PIN, OUTPUT);
  pinMode(ECHO_PIN, INPUT);

  myservo.attach(SERVO_PIN);
  myservo.write(0);                 // Start closed

  WiFi.begin(ssid, password);
  Serial.print("Connecting to WiFi");
  while (WiFi.status() != WL_CONNECTED) {
    delay(500);
    Serial.print(".");
  }
  Serial.println("\nWiFi Connected! IP: " + WiFi.localIP().toString());

  server.on("/gate/open", HTTP_GET, handleGateOpen);
  server.begin();
  Serial.println("Web Server started → Gate endpoint ready");
}

void loop() {
  server.handleClient();

  // Ultrasonic Sensor
  digitalWrite(TRIG_PIN, LOW);
  delayMicroseconds(3);
  digitalWrite(TRIG_PIN, HIGH);
  delayMicroseconds(10);
  digitalWrite(TRIG_PIN, LOW);

  long duration = pulseIn(ECHO_PIN, HIGH, 5000);
  float distance = (duration == 0) ? 999.0 : duration * 0.0343 / 2.0;

  String status = (distance <= OCCUPIED_DISTANCE_CM && distance >= 2.0) ? "occupied" : "free";

  static String lastStatus = "";
  if (status != lastStatus) {
    sendStatusToSpring(status);
    lastStatus = status;
  }

  Serial.printf("Distance: %.1f cm → %s\n", distance, status.c_str());
  delay(1500);
}