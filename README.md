# 📱 Wayfinder Android - Navigation Companion App

> **Real-time navigation companion for Meta Quest VR with dynamic rerouting support**

![Status](https://img.shields.io/badge/Status-Active-green)
![Kotlin](https://img.shields.io/badge/Kotlin-1.9-purple)
![Android](https://img.shields.io/badge/Min%20SDK-32-blue)
![Material](https://img.shields.io/badge/Design-Material%203-teal)

---

## 📖 Overview

Wayfinder Android is the "brain" of the Wayfinder navigation system, handling all location, routing, and API logic. It provides real-time navigation data to the Meta Quest VR headset over TCP, creating an immersive mixed reality navigation experience.

### Key Features

- **Real-time GPS Navigation** - Full Google Maps integration with turn-by-turn directions
- **Dynamic Rerouting** - Automatically detects off-route and recalculates paths
- **VR Companion** - Persistent TCP connection to Meta Quest 3
- **Material Design 3** - Modern UI with dark theme and glassmorphism effects
- **Smart Discovery** - UDP broadcast discovery of Quest devices on local network

---

## 🏗️ Architecture

The app follows **Clean Architecture** principles:

```
app/src/main/java/com/wayfinder/wayfinder/
├── core/                        # Core constants and enums
│   ├── Constants.kt             # Network ports, timeouts, thresholds
│   ├── MessageTypes.kt          # Message type enums (ROUTE, STATUS, etc.)
│   └── NavigationState.kt       # Sealed classes for state management
│
├── data/                        # Data layer
│   └── preferences/
│       └── PreferencesManager.kt  # SharedPreferences wrapper
│
├── domain/                      # Business logic
│   ├── model/
│   │   └── NavigationMessages.kt  # DTOs for TCP communication
│   └── usecase/
│       ├── RouteDeviationCalculator.kt  # On-device polyline deviation check
│       └── RerouteManager.kt     # Debounced reroute coordination
│
├── network/                     # Network layer
│   └── tcp/
│       └── TcpConnectionManager.kt  # Persistent TCP with auto-reconnect
│
├── presentation/                # UI layer
│   ├── navigation/
│   │   └── NavigationSessionService.kt  # Foreground navigation service
│   └── settings/
│       └── SettingsFragment.kt   # Settings configuration UI
│
├── MainActivity.kt              # Main entry point with drawer/bottom nav
├── DeviceDiscoveryDialog.kt     # Quest device picker
├── MapFragment.kt               # Map display and route selection
├── NavigationFragment.kt        # Active navigation UI
└── UdpDiscovery.kt              # UDP listener for Quest broadcasts
```

---

## 🔄 Dynamic Rerouting System

### How It Works

1. **User walks off-route** - GPS updates fed into `RouteDeviationCalculator`
2. **On-device checking** - Uses PolyUtil to check distance from route polyline
3. **Debounce logic** - `RerouteManager` requires 3 consecutive off-route checks
4. **Throttling** - Minimum 10 seconds between reroute triggers
5. **API call** - Fetches new route from Google Directions API
6. **Quest update** - Sends new route via persistent TCP connection

### Key Components

| Component | Purpose |
|-----------|---------|
| `RouteDeviationCalculator` | On-device polyline distance check using PolyUtil |
| `RerouteManager` | Debounce, throttle, and coordinate reroutes |
| `NavigationSessionService` | Foreground service for background navigation |
| `TcpConnectionManager` | Persistent socket with heartbeat and auto-reconnect |

### Configuration

Settings are persisted via `PreferencesManager`:

```kotlin
// In SettingsFragment
preferencesManager.setDynamicReroutingEnabled(true)
preferencesManager.setOffRouteThreshold(25f)  // meters
preferencesManager.setAutoReconnectEnabled(true)
```

---

## 📡 Communication Protocol

### UDP Discovery (Port 8888)
Quest broadcasts presence, Android listens:
```
Format: "Quest3_Presence:{IP_ADDRESS}:{DEVICE_NAME}"
Example: "Quest3_Presence:192.168.1.100:Quest3-Wayfinder"
```

### TCP Messages (Port 9898)

| Message Type | Description |
|--------------|-------------|
| `ROUTE` | Navigation waypoints as UnityCoord array |
| `STATUS` | Navigation state (started, rerouting, arrived) |
| `REROUTE` | Reroute notification with reason |
| `END` | Navigation ended |
| `HEARTBEAT` | Keep-alive ping |

**Example Route Message:**
```json
{
  "type": 0,
  "waypoints": [
    {"x": 0.0, "z": 0.0},
    {"x": 1.5, "z": 3.2},
    {"x": 2.1, "z": 7.8}
  ]
}
```

---

## 🎨 UI Design

### Material Design 3 Theme

- **Primary**: Cyan (`#00E5FF`) - matches Quest holographic theme
- **Secondary**: Magenta (`#FF00FF`)
- **Surface**: Dark backgrounds with glassmorphism cards
- **Dark mode**: Primary design target

### Navigation Structure

```
┌─────────────────────────────────────┐
│ ☰ Wayfinder        [Connection ●]  │  ← Toolbar with drawer toggle
├─────────────────────────────────────┤
│                                     │
│         Fragment Container          │  ← MapFragment with inline navigation
│   ┌─────────────────────────────┐   │
│   │ [Search destination...]     │   │  ← Places autocomplete (hidden during nav)
│   │                             │   │
│   │        Google Map           │   │
│   │                             │   │
│   │ [Route Info Sheet]          │   │  ← Shows when route selected
│   └─────────────────────────────┘   │
├─────────────────────────────────────┤
│  [Debug Logs] (optional overlay)    │  ← When Debug Mode enabled
└─────────────────────────────────────┘

Drawer Menu:
  → Settings
  → Help
  → Disconnect Quest
  → Developer
      → Debug Mode (toggle)
```

---

## 🚀 Getting Started

### Prerequisites

- Android Studio Hedgehog (2023.1) or newer
- Kotlin 1.9+
- Google Maps API key
- Meta Quest 3 (for testing VR companion)

### Build

```bash
# Clone repository
git clone https://github.com/25ankurpandey/WayFinder.git
cd Wayfinder

# Build debug APK
./gradlew assembleDebug

# Install on device
./gradlew installDebug
```

### Configuration

1. Add your Google Maps API key to `AndroidManifest.xml`:
```xml
<meta-data
    android:name="com.google.android.geo.API_KEY"
    android:value="YOUR_API_KEY_HERE"/>
```

2. Ensure Quest and Android are on the same WiFi network

---

## 📋 Dependencies

```kotlin
// build.gradle.kts
dependencies {
    // Google Maps
    implementation("com.google.android.gms:play-services-maps:18.2.0")
    implementation("com.google.maps.android:android-maps-utils:3.8.0")
    
    // Networking
    implementation("com.google.code.gson:gson:2.10.1")
    
    // Kotlin Coroutines
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-android:1.7.3")
    
    // Material Design
    implementation("com.google.android.material:material:1.11.0")
    
    // Lifecycle
    implementation("androidx.lifecycle:lifecycle-viewmodel-ktx:2.7.0")
    implementation("androidx.lifecycle:lifecycle-livedata-ktx:2.7.0")
}
```

---

## 🔧 Permissions

```xml
<uses-permission android:name="android.permission.INTERNET" />
<uses-permission android:name="android.permission.ACCESS_FINE_LOCATION" />
<uses-permission android:name="android.permission.ACCESS_COARSE_LOCATION" />
<uses-permission android:name="android.permission.ACCESS_WIFI_STATE" />
<uses-permission android:name="android.permission.FOREGROUND_SERVICE" />
<uses-permission android:name="android.permission.FOREGROUND_SERVICE_LOCATION" />
<uses-permission android:name="android.permission.POST_NOTIFICATIONS" />
```

---

## 🐛 Troubleshooting

### Connection Error Messages

| Error Message | Cause | Solution |
|--------------|-------|----------|
| "Connection refused - is Quest app running?" | Quest app not running or TCP port blocked | Start Wayfinder on Quest first |
| "Connection timed out - check Quest is on same network" | Network issue or Quest not reachable | Verify same WiFi, check IP address |
| "Device unreachable - check network connection" | Network routing issue | Check WiFi connection on both devices |
| "Connection lost" | Quest disconnected or app crashed | Restart Quest app, reconnect |

### Debug Mode

Enable via **Sidebar → Developer → Debug Mode** to see real-time logs:
- Connection state changes
- Route transmission status
- Heartbeat activity
- Use **Copy** button to share logs for troubleshooting

---

## 📝 Contributing

1. Fork the repository
2. Create feature branch (`git checkout -b feature/amazing-feature`)
3. Commit changes (`git commit -m 'Add amazing feature'`)
4. Push to branch (`git push origin feature/amazing-feature`)
5. Open Pull Request

---

<div align="center">

**Built with ❤️ for immersive navigation**

*Wayfinder Android - The brain behind the experience*

</div>
