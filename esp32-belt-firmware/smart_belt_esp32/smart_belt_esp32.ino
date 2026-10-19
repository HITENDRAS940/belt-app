#include <Arduino.h>
#include <WiFi.h>
#include <WebServer.h>
#include <TinyGPSPlus.h>

// =====================================================
// CAREBELT: ADVANCED WANDERING & OBSTACLE ASSISTANCE
// =====================================================

// --- WI-FI CREDENTIALS ---
const char* WIFI_SSID     = "OnePlus 10R 5G";   // Replace with your Wi-Fi SSID
const char* WIFI_PASSWORD = "notfreewifi";        // Replace with your Wi-Fi Password

// Fallback SoftAP credentials if Wi-Fi router is out of range
const char* AP_SSID       = "CareBelt-ESP32";
const char* AP_PASSWORD   = "carebelt123";

WebServer server(80);

// --- GPS CONFIGURATION (UART2) ---
static const int GPS_RX_PIN        = 16;   // NEO-6M TX -> ESP32 RX2
static const int GPS_TX_PIN        = 17;   // NEO-6M RX -> ESP32 TX2
static const uint32_t GPS_BAUD     = 9600;

TinyGPSPlus gps;
HardwareSerial gpsSerial(2);

// Fallback coordinates before first outdoor satellite lock (New Delhi default)
double lastValidLat = 28.613900;
double lastValidLng = 77.209000;
bool hasEverLockedGps = false;

// --- HAPTIC MOTOR PINS (Moved off 16 & 17) ---
const int MOTOR_FRONT = 4;
const int MOTOR_RIGHT = 5;
const int MOTOR_LEFT  = 18;

// --- ULTRASONIC SENSOR PINS ---
const int FRONT_TRIG = 19;
const int FRONT_ECHO = 21;

const int RIGHT_TRIG = 22;
const int RIGHT_ECHO = 23;

const int LEFT_TRIG  = 32;
const int LEFT_ECHO  = 33;

const int DOWN_TRIG  = 25; // Terrain / Pothole sensor
const int DOWN_ECHO  = 26;

// --- SAFETY & SENSOR VARIABLES ---
const int CLASP_SENSOR_PIN = 12; // LOW = Belt buckled
const int SOS_BUTTON_PIN   = 14; // LOW = SOS pressed

float distanceCM[4];         // 0: Front, 1: Right, 2: Left, 3: Down
float previousDistanceCM[4];
float velocityCMs[4];

const float MAX_DISTANCE = 300.0;
const float OBSTACLE_DISTANCE = 120.0;
const float DROPOFF_THRESHOLD = 110.0;      // Pothole / drop-off
const float UPWARD_STAIR_THRESHOLD = 35.0;  

// Timing
unsigned long lastSensorRead = 0;
const unsigned long SENSOR_INTERVAL = 100; // ms
unsigned long lastSerialPrint = 0;
const unsigned long SERIAL_PRINT_INTERVAL = 1000; // ms
unsigned long previousTime = 0;

// System States
enum Urgency { U_SAFE, U_LOW, U_MEDIUM, U_HIGH, U_CRITICAL };
Urgency currentUrgency = U_SAFE;

enum Direction { NONE, FRONT, RIGHT, LEFT, BACK };
Direction safestDirection = NONE;

bool hapticState = false;
unsigned long lastHapticToggle = 0;

// =====================================================
// ULTRASONIC LOGIC
// =====================================================
float readUltrasonic(int trigPin, int echoPin) {
  digitalWrite(trigPin, LOW);
  delayMicroseconds(2);
  digitalWrite(trigPin, HIGH);
  delayMicroseconds(10);
  digitalWrite(trigPin, LOW);

  long duration = pulseIn(echoPin, HIGH, 25000); // 25ms timeout (~4m max)
  if (duration == 0) return -1;

  float distance = duration * 0.0343 / 2.0;
  if (distance > MAX_DISTANCE) distance = MAX_DISTANCE;
  if (distance < 2) distance = 2;
  return distance;
}

void readSensors() {
  distanceCM[0] = readUltrasonic(FRONT_TRIG, FRONT_ECHO);
  delay(4);
  distanceCM[1] = readUltrasonic(RIGHT_TRIG, RIGHT_ECHO);
  delay(4);
  distanceCM[2] = readUltrasonic(LEFT_TRIG, LEFT_ECHO);
  delay(4);
  distanceCM[3] = readUltrasonic(DOWN_TRIG, DOWN_ECHO);
}

void calculateVelocity() {
  unsigned long currentTime = millis();
  float dt = (currentTime - previousTime) / 1000.0;
  if (dt <= 0) return;

  for (int i = 0; i < 3; i++) {
    if (distanceCM[i] < 0 || previousDistanceCM[i] < 0) {
      velocityCMs[i] = 0;
    } else {
      velocityCMs[i] = (previousDistanceCM[i] - distanceCM[i]) / dt;
    }
  }
  previousTime = currentTime;
}

float calculateTTC(int sensorIndex) {
  float distance = distanceCM[sensorIndex];
  float closingSpeed = velocityCMs[sensorIndex];
  if (distance < 0 || closingSpeed <= 0) return 9999.0;
  return distance / closingSpeed;
}

Urgency determineUrgency() {
  float minimumTTC = 9999.0;
  bool obstacleDetected = false;

  for (int i = 0; i < 3; i++) {
    if (distanceCM[i] >= 0 && distanceCM[i] < OBSTACLE_DISTANCE) {
      obstacleDetected = true;
      float ttc = calculateTTC(i);
      if (ttc < minimumTTC) minimumTTC = ttc;
    }
  }

  if (distanceCM[3] > DROPOFF_THRESHOLD) return U_CRITICAL;       // Drop-off
  if (distanceCM[3] < UPWARD_STAIR_THRESHOLD) return U_HIGH;    // Stair climb
  if (!obstacleDetected) return U_SAFE;

  if (minimumTTC < 1.0) return U_CRITICAL;
  if (minimumTTC < 2.0) return U_HIGH;
  if (minimumTTC < 4.0) return U_MEDIUM;
  return U_LOW;
}

void calculateSafeDirection() {
  float front = (distanceCM[0] < 0) ? MAX_DISTANCE : distanceCM[0];
  float right = (distanceCM[1] < 0) ? MAX_DISTANCE : distanceCM[1];
  float left  = (distanceCM[2] < 0) ? MAX_DISTANCE : distanceCM[2];

  bool frontBlocked = (front < OBSTACLE_DISTANCE);
  bool rightBlocked = (right < OBSTACLE_DISTANCE);
  bool leftBlocked  = (left < OBSTACLE_DISTANCE);

  if (!frontBlocked) { safestDirection = FRONT; return; }
  if (!leftBlocked && rightBlocked) { safestDirection = LEFT; return; }
  if (leftBlocked && !rightBlocked) { safestDirection = RIGHT; return; }
  if (!leftBlocked && !rightBlocked) { safestDirection = (left >= right) ? LEFT : RIGHT; return; }
  safestDirection = BACK;
}

// =====================================================
// MOTOR CONTROL
// =====================================================
void stopMotors() {
  digitalWrite(MOTOR_FRONT, LOW);
  digitalWrite(MOTOR_RIGHT, LOW);
  digitalWrite(MOTOR_LEFT,  LOW);
}

void setDirectionalFeedback() {
  stopMotors();
  switch (safestDirection) {
    case FRONT: digitalWrite(MOTOR_FRONT, HIGH); break;
    case RIGHT: digitalWrite(MOTOR_RIGHT, HIGH); break;
    case LEFT:  digitalWrite(MOTOR_LEFT,  HIGH); break;
    case BACK:  
      digitalWrite(MOTOR_FRONT, HIGH);
      digitalWrite(MOTOR_RIGHT, HIGH);
      digitalWrite(MOTOR_LEFT,  HIGH); 
      break;
    default: stopMotors(); break;
  }
}

int getPulseInterval() {
  switch (currentUrgency) {
    case U_SAFE:     return 0;
    case U_LOW:      return 1000;
    case U_MEDIUM:   return 500;
    case U_HIGH:     return 250;
    case U_CRITICAL: return 100;
  }
  return 1000;
}

// =====================================================
// WEB SERVER DASHBOARD & API
// =====================================================
String getUrgencyString() {
  switch (currentUrgency) {
    case U_SAFE:     return "SAFE";
    case U_LOW:      return "LOW";
    case U_MEDIUM:   return "MEDIUM";
    case U_HIGH:     return "HIGH";
    case U_CRITICAL: return "CRITICAL";
  }
  return "UNKNOWN";
}

void handleDataJson() {
  if (gps.location.isValid()) {
    lastValidLat = gps.location.lat();
    lastValidLng = gps.location.lng();
    hasEverLockedGps = true;
  }

  String json = "{";
  json += "\"latitude\":" + String(lastValidLat, 6) + ",";
  json += "\"longitude\":" + String(lastValidLng, 6) + ",";
  json += "\"satellites\":" + String(gps.satellites.isValid() ? gps.satellites.value() : 0) + ",";
  json += "\"hasFix\":" + String(hasEverLockedGps ? "true" : "false") + ",";
  json += "\"front\":" + String(distanceCM[0]) + ",";
  json += "\"right\":" + String(distanceCM[1]) + ",";
  json += "\"left\":"  + String(distanceCM[2]) + ",";
  json += "\"down\":"  + String(distanceCM[3]) + ",";
  json += "\"urgency\":\"" + getUrgencyString() + "\",";
  json += "\"beltWorn\":" + String(digitalRead(CLASP_SENSOR_PIN) == LOW ? "true" : "false") + ",";
  json += "\"sosActive\":" + String(digitalRead(SOS_BUTTON_PIN) == LOW ? "true" : "false");
  json += "}";

  server.sendHeader("Access-Control-Allow-Origin", "*");
  server.send(200, "application/json", json);
}

void handleRoot() {
  String html = R"rawliteral(
  <!DOCTYPE html>
  <html>
  <head>
    <title>CareBelt Tracker & Navigation</title>
    <meta name="viewport" content="width=device-width, initial-scale=1">
    <style>
      body { font-family: Arial, sans-serif; text-align: center; background: #121212; color: #fff; margin: 0; padding: 20px; }
      h1 { color: #00E676; margin-bottom: 10px; }
      .grid { display: grid; grid-template-columns: repeat(auto-fit, minmax(140px, 1fr)); gap: 15px; max-width: 600px; margin: auto; }
      .card { background: #1E1E1E; padding: 15px; border-radius: 10px; box-shadow: 0 4px 6px rgba(0,0,0,0.3); }
      .card h3 { margin: 0 0 10px 0; color: #BBB; font-size: 14px; }
      .value { font-size: 20px; font-weight: bold; color: #00E676; }
      .status-box { background: #1E1E1E; max-width: 600px; margin: 20px auto; padding: 15px; border-radius: 10px; text-align: left; }
      .status-row { display: flex; justify-content: space-between; padding: 8px 0; border-bottom: 1px solid #333; }
    </style>
    <script>
      setInterval(function() {
        fetch('/data')
          .then(res => res.json())
          .then(data => {
            document.getElementById('lat').innerText = data.latitude.toFixed(6);
            document.getElementById('lng').innerText = data.longitude.toFixed(6);
            document.getElementById('sats').innerText = data.satellites;
            document.getElementById('urgency').innerText = data.urgency;
            document.getElementById('belt').innerText = data.beltWorn ? "BUCKLED" : "UNBUCKLED";
            document.getElementById('sos').innerText = data.sosActive ? "ACTIVE!" : "NORMAL";
          });
      }, 1000);
    </script>
  </head>
  <body>
    <h1>CareBelt Dashboard</h1>
    <p>Wandering Prevention & Obstacle Guidance System</p>
    <div class="status-box">
      <div class="status-row"><span>Latitude:</span><span class="value" id="lat">--</span></div>
      <div class="status-row"><span>Longitude:</span><span class="value" id="lng">--</span></div>
      <div class="status-row"><span>Satellites:</span><span class="value" id="sats">--</span></div>
      <div class="status-row"><span>Obstacle Urgency:</span><span class="value" id="urgency">--</span></div>
      <div class="status-row"><span>Belt Status:</span><span class="value" id="belt">--</span></div>
      <div class="status-row"><span>SOS State:</span><span class="value" id="sos">--</span></div>
    </div>
  </body>
  </html>
  )rawliteral";
  server.send(200, "text/html", html);
}

// =====================================================
// SETUP & LOOP
// =====================================================
void setup() {
  Serial.begin(115200);

  // Initialize GPS UART2
  gpsSerial.begin(GPS_BAUD, SERIAL_8N1, GPS_RX_PIN, GPS_TX_PIN);

  // Configure Motor Outputs
  pinMode(MOTOR_FRONT, OUTPUT);
  pinMode(MOTOR_RIGHT, OUTPUT);
  pinMode(MOTOR_LEFT,  OUTPUT);
  stopMotors();

  // Configure Ultrasonic Sensors
  pinMode(FRONT_TRIG, OUTPUT); pinMode(FRONT_ECHO, INPUT);
  pinMode(RIGHT_TRIG, OUTPUT); pinMode(RIGHT_ECHO, INPUT);
  pinMode(LEFT_TRIG,  OUTPUT); pinMode(LEFT_ECHO,  INPUT);
  pinMode(DOWN_TRIG,  OUTPUT); pinMode(DOWN_ECHO,  INPUT);

  // Configure Safety Inputs
  pinMode(CLASP_SENSOR_PIN, INPUT_PULLUP);
  pinMode(SOS_BUTTON_PIN,   INPUT_PULLUP);

  previousTime = millis();

  // Wi-Fi Connection
  WiFi.mode(WIFI_AP_STA);
  WiFi.begin(WIFI_SSID, WIFI_PASSWORD);
  Serial.print("Connecting to Wi-Fi");
  unsigned long startAttempt = millis();
  while (WiFi.status() != WL_CONNECTED && millis() - startAttempt < 7000) {
    delay(250);
    Serial.print(".");
  }

  if (WiFi.status() == WL_CONNECTED) {
    Serial.println("\nConnected! IP: " + WiFi.localIP().toString());
  } else {
    WiFi.softAP(AP_SSID, AP_PASSWORD);
    Serial.println("\nFallback SoftAP Started. IP: 192.168.4.1");
  }

  server.on("/", handleRoot);
  server.on("/data", handleDataJson);
  server.begin();
}

void loop() {
  // Feed GPS Data stream
  while (gpsSerial.available() > 0) {
    gps.encode(gpsSerial.read());
  }

  server.handleClient();

  unsigned long currentTime = millis();

  // Sensor reading block
  if (currentTime - lastSensorRead >= SENSOR_INTERVAL) {
    for (int i = 0; i < 4; i++) previousDistanceCM[i] = distanceCM[i];
    readSensors();
    calculateVelocity();
    currentUrgency = determineUrgency();
    calculateSafeDirection();
    lastSensorRead = currentTime;
  }

  // Handle Safe Obstacle-Free State
  if (currentUrgency == U_SAFE) {
    stopMotors();
    return;
  }

  // Handle Dynamic Haptic Obstacle / Stair Feedback
  int pulseInterval = getPulseInterval();
  if (currentTime - lastHapticToggle >= (unsigned long)pulseInterval) {
    lastHapticToggle = currentTime;
    hapticState = !hapticState;

    if (hapticState) {
      setDirectionalFeedback();
    } else {
      stopMotors();
    }
  }
}
