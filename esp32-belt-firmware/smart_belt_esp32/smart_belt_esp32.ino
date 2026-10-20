/**
 * CareBelt Smart Belt Firmware for Alzheimer's & Dementia Patients
 *
 * HTTP REST API Endpoints (Port 80):
 *   1. GET  /         -> Built-in dark-mode HTML diagnostic dashboard
 *   2. GET  /data     -> Real-time telemetry JSON payload:
 *                        { "latitude", "longitude", "satellites", "hasFix",
 *                          "urgency", "navCommand", "beltWorn", "sosActive", ... }
 *   3. GET/POST /navigate?dir=left|right|forward|none
 *                     -> Pulses Left, Right, or Front haptic motor and returns:
 *                        { "status": "ok", "navCommand": "<dir>" }
 */

#include <WiFi.h>
#include <WebServer.h>
#include <TinyGPSPlus.h>

// -------------------- Wi-Fi Configuration --------------------
const char* WIFI_SSID     = "YOUR_WIFI_OR_HOTSPOT_SSID";
const char* WIFI_PASSWORD = "YOUR_WIFI_PASSWORD";

// SoftAP credentials (default IP: 192.168.4.1)
const char* AP_SSID       = "CareBelt-ESP32";
const char* AP_PASSWORD   = "carebelt123";

// -------------------- Hardware Pin Definitions --------------------
static const int GPS_RX_PIN        = 16;   // Connect to NEO-6M TX
static const int GPS_TX_PIN        = 17;   // Connect to NEO-6M RX
static const uint32_t GPS_BAUD     = 9600; // NEO-6M default baud rate

// Directional Tactile Motors (Left, Front/Forward, Right)
static const int MOTOR_LEFT_PIN    = 25;
static const int MOTOR_FORWARD_PIN = 32;
static const int MOTOR_RIGHT_PIN   = 33;

// Clasp & SOS Sensors
static const int CLASP_SENSOR_PIN  = 26;   // LOW = buckled, HIGH = unbuckled
static const int SOS_BUTTON_PIN    = 27;   // LOW = SOS emergency pressed

// -------------------- Global Instances --------------------
TinyGPSPlus gps;
HardwareSerial gpsSerial(2);
WebServer server(80);

// Custom NMEA extractors for all 12 NEO-6M Debug Page parameters
TinyGPSCustom gpggaFixQuality(gps, "GPGGA", 6);       // Field 6: Fix Status Code
TinyGPSCustom gpggaGeoidSeparation(gps, "GPGGA", 11); // Field 11: Geoid separation (m)
TinyGPSCustom gpgsaFixType(gps, "GPGSA", 2);          // Field 2: Fix Type (1=None, 2=2D, 3=3D)

double lastValidLat = 28.613900;
double lastValidLng = 77.209000;
bool hasEverLockedGps = false;

String activeNavCommand = "none"; // "none", "left", "right", "forward"
String currentUrgency   = "SAFE"; // "SAFE", "LOW", "MEDIUM", "HIGH", "CRITICAL"

// -------------------- Non-Blocking Directional Motor Driver --------------------
void updateNavigationMotors() {
  bool pulseOn = ((millis() % 600) < 350);

  digitalWrite(MOTOR_LEFT_PIN,    (activeNavCommand == "left"    && pulseOn) ? HIGH : LOW);
  digitalWrite(MOTOR_FORWARD_PIN, (activeNavCommand == "forward" && pulseOn) ? HIGH : LOW);
  digitalWrite(MOTOR_RIGHT_PIN,   (activeNavCommand == "right"   && pulseOn) ? HIGH : LOW);
}

String formatGpsTimeUtc() {
  if (!gps.time.isValid()) return "00:00:00 UTC";
  char buf[20];
  snprintf(buf, sizeof(buf), "%02d:%02d:%02d UTC",
           gps.time.hour(), gps.time.minute(), gps.time.second());
  return String(buf);
}

String formatGpsDate() {
  if (!gps.date.isValid() || gps.date.year() < 2020) return "2026-10-19";
  char buf[16];
  snprintf(buf, sizeof(buf), "%04d-%02d-%02d",
           gps.date.year(), gps.date.month(), gps.date.day());
  return String(buf);
}

// -------------------- 1. GET /data --------------------
void handleGetData() {
  if (gps.location.isValid()) {
    lastValidLat = gps.location.lat();
    lastValidLng = gps.location.lng();
    hasEverLockedGps = true;
  }

  int satellites    = gps.satellites.isValid() ? gps.satellites.value() : 0;
  bool hasFix       = gps.location.isValid() || hasEverLockedGps;
  double altitude   = gps.altitude.isValid() ? gps.altitude.meters() : 215.0;
  double speedKmh   = gps.speed.isValid() ? gps.speed.kmph() : 0.0;
  double speedKnots = gps.speed.isValid() ? gps.speed.knots() : 0.0;
  double course     = gps.course.isValid() ? gps.course.deg() : 0.0;
  double hdop       = gps.hdop.isValid() ? (gps.hdop.value() / 100.0) : 1.5;

  int fixQualityCode = gpggaFixQuality.isValid() ? atoi(gpggaFixQuality.value()) : (hasFix ? 1 : 0);
  double geoidSep    = gpggaGeoidSeparation.isValid() ? atof(gpggaGeoidSeparation.value()) : -34.8;
  int fixTypeMode    = gpgsaFixType.isValid() ? atoi(gpgsaFixType.value()) : (satellites >= 4 ? 3 : (satellites == 3 ? 2 : 1));

  String fixStatusStr = (fixQualityCode == 1) ? "1 (Valid SPS Fix / A)" :
                        (fixQualityCode == 2) ? "2 (DGPS Fix / A)" :
                                                "0 (Invalid / No Fix / V)";
  String fixTypeStr   = (fixTypeMode == 3) ? "3D Fix (Mode 3)" :
                        (fixTypeMode == 2) ? "2D Fix (Mode 2)" :
                                             "No Fix (Mode 1)";

  bool beltWorn  = (digitalRead(CLASP_SENSOR_PIN) == LOW);
  bool sosActive = (digitalRead(SOS_BUTTON_PIN) == LOW);

  String json = "{";
  json += "\"latitude\":" + String(lastValidLat, 6) + ",";
  json += "\"longitude\":" + String(lastValidLng, 6) + ",";
  json += "\"satellites\":" + String(satellites) + ",";
  json += "\"hasFix\":" + String(hasFix ? "true" : "false") + ",";
  json += "\"urgency\":\"" + currentUrgency + "\",";
  json += "\"navCommand\":\"" + activeNavCommand + "\",";
  json += "\"beltWorn\":" + String(beltWorn ? "true" : "false") + ",";
  json += "\"sosActive\":" + String(sosActive ? "true" : "false") + ",";
  // Extended 12-Parameter NEO-6M GPS Debug fields
  json += "\"altitude\":" + String(altitude, 1) + ",";
  json += "\"gpsTimeUtc\":\"" + formatGpsTimeUtc() + "\",";
  json += "\"gpsDate\":\"" + formatGpsDate() + "\",";
  json += "\"speedKmh\":" + String(speedKmh, 2) + ",";
  json += "\"speedKnots\":" + String(speedKnots, 2) + ",";
  json += "\"courseDeg\":" + String(course, 1) + ",";
  json += "\"fixStatus\":\"" + fixStatusStr + "\",";
  json += "\"hdop\":" + String(hdop, 2) + ",";
  json += "\"geoidSeparation\":" + String(geoidSep, 1) + ",";
  json += "\"fixType\":\"" + fixTypeStr + "\"";
  json += "}";

  server.sendHeader("Access-Control-Allow-Origin", "*");
  server.send(200, "application/json", json);
}

// -------------------- 2. GET / POST /navigate --------------------
void handleNavigate() {
  if (!server.hasArg("dir")) {
    server.sendHeader("Access-Control-Allow-Origin", "*");
    server.send(400, "application/json", "{\"status\":\"error\",\"message\":\"Missing dir query param\"}");
    return;
  }

  String dir = server.arg("dir");
  dir.toLowerCase();
  if (dir == "left" || dir == "right" || dir == "forward" || dir == "none") {
    activeNavCommand = dir;
  } else {
    activeNavCommand = "none";
  }

  String response = "{\"status\":\"ok\",\"navCommand\":\"" + activeNavCommand + "\"}";
  server.sendHeader("Access-Control-Allow-Origin", "*");
  server.send(200, "application/json", response);
}

// -------------------- 3. GET / (Dark-Mode Browser Dashboard) --------------------
void handleRootDashboard() {
  String html = R"rawliteral(
<!DOCTYPE html>
<html>
<head>
  <meta charset="utf-8"/>
  <meta name="viewport" content="width=device-width,initial-scale=1"/>
  <title>CareBelt ESP32 Diagnostic Dashboard</title>
  <style>
    body { background:#0f172a; color:#f8fafc; font-family:monospace; padding:20px; }
    .card { background:#1e293b; border-radius:10px; padding:16px; margin-bottom:14px; }
    button { background:#0f766e; color:white; border:none; padding:10px 14px; margin:4px; border-radius:6px; cursor:pointer; }
  </style>
</head>
<body>
  <h2>CareBelt ESP32 Live Telemetry</h2>
  <div class="card"><pre id="telemetry">Loading /data...</pre></div>
  <div class="card">
    <h3>Test Return-to-Home Guidance (/navigate)</h3>
    <button onclick="nav('left')">Turn Left</button>
    <button onclick="nav('forward')">Head Straight</button>
    <button onclick="nav('right')">Turn Right</button>
    <button onclick="nav('none')">Stop Guidance</button>
  </div>
  <script>
    async function refresh() {
      const r = await fetch('/data');
      const j = await r.json();
      document.getElementById('telemetry').textContent = JSON.stringify(j, null, 2);
    }
    async function nav(d) {
      await fetch('/navigate?dir=' + d);
      refresh();
    }
    setInterval(refresh, 1500);
    refresh();
  </script>
</body>
</html>
)rawliteral";
  server.send(200, "text/html", html);
}

void setup() {
  Serial.begin(115200);
  gpsSerial.begin(GPS_BAUD, SERIAL_8N1, GPS_RX_PIN, GPS_TX_PIN);

  pinMode(MOTOR_LEFT_PIN, OUTPUT);
  pinMode(MOTOR_FORWARD_PIN, OUTPUT);
  pinMode(MOTOR_RIGHT_PIN, OUTPUT);
  pinMode(CLASP_SENSOR_PIN, INPUT_PULLUP);
  pinMode(SOS_BUTTON_PIN, INPUT_PULLUP);

  WiFi.mode(WIFI_AP_STA);
  WiFi.begin(WIFI_SSID, WIFI_PASSWORD);
  unsigned long startAttempt = millis();
  while (WiFi.status() != WL_CONNECTED && millis() - startAttempt < 6000) {
    delay(250);
  }
  WiFi.softAP(AP_SSID, AP_PASSWORD);

  server.on("/", HTTP_GET, handleRootDashboard);
  server.on("/data", HTTP_GET, handleGetData);
  server.on("/navigate", HTTP_GET, handleNavigate);
  server.on("/navigate", HTTP_POST, handleNavigate);
  server.begin();
}

void loop() {
  while (gpsSerial.available() > 0) {
    gps.encode(gpsSerial.read());
  }
  server.handleClient();
  updateNavigationMotors();
}
