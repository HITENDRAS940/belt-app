/**
 * CareBelt Smart Belt Firmware for Alzheimer's & Dementia Patients
 * Hardware:
 *   - MCU: ESP32 Dev Module
 *   - GPS: u-blox NEO-6M connected to UART2 (ESP32 RX2 = GPIO 16, TX2 = GPIO 17)
 *   - Haptics: Coin / Cylinder Vibration Motor on GPIO 25 (via NPN/MOSFET driver)
 *   - Optional Clasp Sensor: Magnetic Reed / Hall Switch on GPIO 26 (INPUT_PULLUP)
 *   - Optional SOS Button: Tactile Pushbutton on GPIO 27 (INPUT_PULLUP)
 *
 * Dependencies (Arduino Library Manager):
 *   - TinyGPSPlus by Mikal Hart
 *
 * HTTP REST API consumed by the CareBelt Kotlin Android App:
 *   - GET  /location -> Returns JSON telemetry from NEO-6M GPS + belt sensors
 *   - POST /vibrate?pattern=gentle_pulse&durationMs=1200 -> Triggers haptic pattern
 *   - GET  /status   -> Health check
 */

#include <WiFi.h>
#include <WebServer.h>
#include <TinyGPSPlus.h>

// -------------------- Wi-Fi Configuration --------------------
const char* WIFI_SSID     = "YOUR_WIFI_OR_HOTSPOT_SSID";
const char* WIFI_PASSWORD = "YOUR_WIFI_PASSWORD";

// Fallback SoftAP credentials if Wi-Fi router/hotspot is not in range
const char* AP_SSID       = "CareBelt-ESP32";
const char* AP_PASSWORD   = "carebelt123"; // Default IP: 192.168.4.1

// -------------------- Hardware Pin Definitions --------------------
static const int GPS_RX_PIN        = 16;   // Connect to NEO-6M TX
static const int GPS_TX_PIN        = 17;   // Connect to NEO-6M RX
static const uint32_t GPS_BAUD     = 9600; // NEO-6M default baud rate

static const int VIBRATION_PIN     = 25;   // Vibration motor transistor gate/base
static const int CLASP_SENSOR_PIN  = 26;   // LOW = Belt buckled, HIGH = Unbuckled
static const int SOS_BUTTON_PIN    = 27;   // LOW = SOS pressed
static const int BATTERY_ADC_PIN   = 34;   // Voltage divider from LiPo battery

// -------------------- Global Instances --------------------
TinyGPSPlus gps;
HardwareSerial gpsSerial(2);
WebServer server(80);

// Fallback coordinates before first outdoor satellite lock (New Delhi default)
double lastValidLat = 28.613900;
double lastValidLng = 77.209000;
bool hasEverLockedGps = false;

// Non-blocking vibration state machine
bool isVibrating = false;
unsigned long vibrationStartMs = 0;
unsigned long vibrationDurationMs = 0;
String activePattern = "none";

// -------------------- Non-Blocking Vibration Engine --------------------
void startVibrationPattern(const String& pattern, unsigned long durationMs) {
  activePattern = pattern;
  vibrationStartMs = millis();
  vibrationDurationMs = constrain(durationMs, 200UL, 8000UL);
  isVibrating = true;
}

void updateVibrationMotor() {
  if (!isVibrating) {
    digitalWrite(VIBRATION_PIN, LOW);
    return;
  }

  unsigned long elapsed = millis() - vibrationStartMs;
  if (elapsed >= vibrationDurationMs) {
    isVibrating = false;
    activePattern = "none";
    digitalWrite(VIBRATION_PIN, LOW);
    return;
  }

  // Distinct haptic rhythms for Alzheimer's / Dementia wearer cues
  if (activePattern == "gentle_pulse") {
    // Soft double-pulse: 250ms ON, 200ms OFF
    bool motorOn = ((elapsed % 450) < 250);
    digitalWrite(VIBRATION_PIN, motorOn ? HIGH : LOW);
  } else if (activePattern == "return_home") {
    // Directional guiding heartbeat: 400ms ON, 250ms OFF
    bool motorOn = ((elapsed % 650) < 400);
    digitalWrite(VIBRATION_PIN, motorOn ? HIGH : LOW);
  } else if (activePattern == "boundary_alert") {
    // Urgent 3-pulse safe-zone warning: 180ms ON, 120ms OFF
    bool motorOn = ((elapsed % 300) < 180);
    digitalWrite(VIBRATION_PIN, motorOn ? HIGH : LOW);
  } else {
    // Continuous buzz for "find_belt"
    digitalWrite(VIBRATION_PIN, HIGH);
  }
}

// -------------------- Battery & Sensor Helpers --------------------
int readBatteryPercent() {
  // Example LiPo voltage divider on GPIO 34 (falls back to 88% if unconnected)
  int raw = analogRead(BATTERY_ADC_PIN);
  if (raw <= 100) return 88;
  int pct = map(raw, 2000, 2600, 0, 100);
  return constrain(pct, 5, 100);
}

// -------------------- HTTP Route Handlers --------------------
void handleGetLocation() {
  if (gps.location.isValid()) {
    lastValidLat = gps.location.lat();
    lastValidLng = gps.location.lng();
    hasEverLockedGps = true;
  }

  double speedKmh = gps.speed.isValid() ? gps.speed.kmph() : 0.0;
  int satellites  = gps.satellites.isValid() ? gps.satellites.value() : 0;
  double hdop     = gps.hdop.isValid() ? (gps.hdop.value() / 100.0) : 1.5;
  double altitude = gps.altitude.isValid() ? gps.altitude.meters() : 215.0;
  double course   = gps.course.isValid() ? gps.course.deg() : 0.0;

  bool beltFastened = (digitalRead(CLASP_SENSOR_PIN) == LOW);
  bool sosPressed   = (digitalRead(SOS_BUTTON_PIN) == LOW);
  int batteryPct    = readBatteryPercent();

  String json = "{";
  json += "\"patientName\":\"Patient #1 (ESP32 Belt)\",";
  json += "\"latitude\":" + String(lastValidLat, 6) + ",";
  json += "\"longitude\":" + String(lastValidLng, 6) + ",";
  json += "\"speedKmh\":" + String(speedKmh, 2) + ",";
  json += "\"satellites\":" + String(satellites) + ",";
  json += "\"hdop\":" + String(hdop, 2) + ",";
  json += "\"altitude\":" + String(altitude, 1) + ",";
  json += "\"courseDeg\":" + String(course, 1) + ",";
  json += "\"hasFix\":" + String(hasEverLockedGps ? "true" : "false") + ",";
  json += "\"batteryPercent\":" + String(batteryPct) + ",";
  json += "\"beltWorn\":" + String(beltFastened ? "true" : "false") + ",";
  json += "\"sosActive\":" + String(sosPressed ? "true" : "false") + ",";
  json += "\"vibrating\":" + String(isVibrating ? "true" : "false");
  json += "}";

  server.sendHeader("Access-Control-Allow-Origin", "*");
  server.send(200, "application/json", json);
}

void handlePostVibrate() {
  String pattern = server.hasArg("pattern") ? server.arg("pattern") : "gentle_pulse";
  unsigned long durationMs = server.hasArg("durationMs")
      ? server.arg("durationMs").toInt()
      : 1500UL;

  startVibrationPattern(pattern, durationMs);

  String response = "{\"status\":\"ok\",\"pattern\":\"" + pattern +
                    "\",\"durationMs\":" + String(durationMs) + "}";
  server.sendHeader("Access-Control-Allow-Origin", "*");
  server.send(200, "application/json", response);
}

void handleStatus() {
  server.sendHeader("Access-Control-Allow-Origin", "*");
  server.send(200, "application/json", "{\"status\":\"online\",\"device\":\"CareBelt-ESP32\"}");
}

// -------------------- Setup & Loop --------------------
void setup() {
  Serial.begin(115200);
  gpsSerial.begin(GPS_BAUD, SERIAL_8N1, GPS_RX_PIN, GPS_TX_PIN);

  pinMode(VIBRATION_PIN, OUTPUT);
  digitalWrite(VIBRATION_PIN, LOW);
  pinMode(CLASP_SENSOR_PIN, INPUT_PULLUP);
  pinMode(SOS_BUTTON_PIN, INPUT_PULLUP);

  // Brief startup haptic pulse to confirm belt power-on
  startVibrationPattern("gentle_pulse", 600);

  // Try connecting to configured Wi-Fi / Phone Hotspot for 8 seconds
  WiFi.mode(WIFI_AP_STA);
  WiFi.begin(WIFI_SSID, WIFI_PASSWORD);
  Serial.print("Connecting to Wi-Fi");
  unsigned long startAttempt = millis();
  while (WiFi.status() != WL_CONNECTED && millis() - startAttempt < 8000) {
    delay(250);
    Serial.print(".");
  }

  if (WiFi.status() == WL_CONNECTED) {
    Serial.println("\nConnected! ESP32 IP Address: " + WiFi.localIP().toString());
  } else {
    Serial.println("\nStarting SoftAP mode...");
  }

  // Always start SoftAP fallback so caregiver phone can connect directly in the field
  WiFi.softAP(AP_SSID, AP_PASSWORD);
  Serial.println("SoftAP IP Address: " + WiFi.softAPIP().toString());

  server.on("/location", HTTP_GET, handleGetLocation);
  server.on("/vibrate", HTTP_POST, handlePostVibrate);
  server.on("/status", HTTP_GET, handleStatus);
  server.begin();
}

void loop() {
  // Continuously feed characters from NEO-6M GPS UART
  while (gpsSerial.available() > 0) {
    gps.encode(gpsSerial.read());
  }

  // Handle incoming HTTP requests from the Kotlin Android app
  server.handleClient();

  // Drive non-blocking vibration motor patterns
  updateVibrationMotor();
}
