# CareBelt Tracker — Smart Belt for Alzheimer's & Dementia Patients

A Kotlin Android application (Jetpack Compose + Google Maps SDK) and companion ESP32 firmware designed for tracking and gently guiding Alzheimer's and dementia patients wearing a smart belt equipped with an **ESP32**, **u-blox NEO-6M GPS**, and **Haptic Vibration Motor**.

---

## 1. Project Structure

- [`app/`](file:///Users/hitendrasingh/Desktop/BELT-APP/app) — Kotlin Android App (Jetpack Compose, Material 3, Google Maps Compose, OkHttp, Coroutines/StateFlow)
  - [`BeltModels.kt`](file:///Users/hitendrasingh/Desktop/BELT-APP/app/src/main/java/com/carebelt/tracker/model/BeltModels.kt) — Telemetry data classes, vibration pattern definitions, Safe Zone config, and Haversine/bearing math (`GeoUtils`)
  - [`BeltRepository.kt`](file:///Users/hitendrasingh/Desktop/BELT-APP/app/src/main/java/com/carebelt/tracker/data/BeltRepository.kt) — Polls `GET /location` and sends `POST /vibrate` to the ESP32 belt, plus a built-in realistic GPS simulator
  - [`BeltTrackerViewModel.kt`](file:///Users/hitendrasingh/Desktop/BELT-APP/app/src/main/java/com/carebelt/tracker/ui/BeltTrackerViewModel.kt) — Reactive state management, breadcrumb trail history, and automatic Safe Zone breach vibration trigger
  - [`BeltTrackerScreen.kt`](file:///Users/hitendrasingh/Desktop/BELT-APP/app/src/main/java/com/carebelt/tracker/ui/BeltTrackerScreen.kt) — Google Maps view with patient marker, Safe Zone circle, breadcrumb trail, live telemetry bar, and vibration motor controls
- [`esp32-belt-firmware/smart_belt_esp32/smart_belt_esp32.ino`](file:///Users/hitendrasingh/Desktop/BELT-APP/esp32-belt-firmware/smart_belt_esp32/smart_belt_esp32.ino) — ESP32 Arduino sketch for NEO-6M UART2 reading and non-blocking vibration motor patterns

---

## 2. Setting Up Your Google Maps API Key

1. Open [`local.properties`](file:///Users/hitendrasingh/Desktop/BELT-APP/local.properties) in the root of this project.
2. Replace `YOUR_GOOGLE_MAPS_API_KEY_HERE` with your Google Maps SDK for Android API Key:
   ```properties
   MAPS_API_KEY=AIzaSyYourActualGoogleMapsKeyHere
   ```
3. Rebuild and run the app in Android Studio or via `./gradlew assembleDebug`.

---

## 3. ESP32 + NEO-6M + Vibration Motor Wiring

| Component Pin | ESP32 Pin | Notes |
| :--- | :--- | :--- |
| **NEO-6M VCC** | `3.3V` / `5V` | Check module regulator (most NEO-6M boards accept 3.3V–5V) |
| **NEO-6M GND** | `GND` | Common ground |
| **NEO-6M TX** | `GPIO 16 (RX2)` | ESP32 UART2 Receive |
| **NEO-6M RX** | `GPIO 17 (TX2)` | ESP32 UART2 Transmit |
| **Vibration Motor Driver IN** | `GPIO 25` | Drive via 2N2222 / S8050 transistor or 2N7000 MOSFET + flyback diode |
| **Buckle Reed Switch (Optional)** | `GPIO 26` | `INPUT_PULLUP` (`LOW` = fastened, `HIGH` = unbuckled) |
| **SOS Tactile Button (Optional)** | `GPIO 27` | `INPUT_PULLUP` (`LOW` = SOS active) |
