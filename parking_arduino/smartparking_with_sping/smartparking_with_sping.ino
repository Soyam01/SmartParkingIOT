#include <WiFi.h>
#include <HTTPClient.h>

const char* ssid = "Virinchi College";
const char* password = "virinchi@2024";

// === Change this to your Spring Boot server IP ===
const char* serverUrl = "http://10.5.48.156:8080/api/spot/update";

#define TRIG_PIN 5
#define ECHO_PIN 18
#define OCCUPIED_DISTANCE_CM 10.0
#define MIN_VALID_DISTANCE_CM 2.0

void sendStatusToSpring(const String& status) {
  if (WiFi.status() != WL_CONNECTED) {
    Serial.println("WiFi not connected");
    return;
  }

  HTTPClient http;
  http.begin(serverUrl);
  http.addHeader("Content-Type", "application/json");

  String payload = "{\"status\":\"" + status + "\"}";

  int httpCode = http.POST(payload);

  if (httpCode > 0) {
    Serial.printf("Spring: %d - %s\n", httpCode, http.getString().c_str());
  } else {
    Serial.printf("HTTP failed: %s\n", http.errorToString(httpCode).c_str());
  }

  http.end();
}

void setup() {
  Serial.begin(115200);
  pinMode(TRIG_PIN, OUTPUT);
  pinMode(ECHO_PIN, INPUT);
  digitalWrite(TRIG_PIN, LOW);

  WiFi.begin(ssid, password);
  while (WiFi.status() != WL_CONNECTED) {
    delay(500);
    Serial.print(".");
  }
  Serial.println("\nWiFi connected");
}

void loop() {
  digitalWrite(TRIG_PIN, LOW);
  delayMicroseconds(3);
  digitalWrite(TRIG_PIN, HIGH);
  delayMicroseconds(10);
  digitalWrite(TRIG_PIN, LOW);

  long duration = pulseIn(ECHO_PIN, HIGH, 4000);
  float distance = (duration == 0) ? 999.0 : duration * 0.0343 / 2.0;

  String status = (distance <= OCCUPIED_DISTANCE_CM && distance >= MIN_VALID_DISTANCE_CM)
                    ? "occupied" : "free";

  static String lastStatus = "";
  if (status != lastStatus) {
    sendStatusToSpring(status);
    lastStatus = status;
  }

  Serial.printf("Distance: %.1f cm → %s\n", distance, status.c_str());
  delay(2000);
}