# BMS Manager

An open-source, full-featured **Battery Management System (BMS)** application built with modern Android development practices. It provides real-time telemetric monitoring, Bluetooth Low Energy (BLE) integration with hardware, dynamic safe simulation, data persistence, and customizable battery protection parameters for lithium-ion and LiFePO4 packs.

---

## 🎨 Core Features

*   **Real-Time Telemetry & Visual Dashboard**: Displays vital battery statistics such as Pack Voltage, Current, State of Charge (SOC), Cell Balancers status, and Pack/MOS/Ambient Temperatures in a sleek Material Design 3 layout.
*   **Active Bluetooth Low Energy (BLE) Connectivity**:
    *   **Automated Scanning**: Detects nearby compatible BMS hardware peripherals.
    *   **GATT Service Bindings**: Integrates natively with manufacturers' hardware configurations.
    *   **Remote Switches**: Toggle charge and discharge MOS gates directly from the mobile application.
*   **Virtual BMS Simulator**: Provides a robust, multi-condition software simulator replicating real-world charge/discharge behavior, thermal propagation, and custom imbalance faults.
*   **Historical Logging & Charting**: Local storage persists telemetry streams to SQLite and visualizes historical voltage, temperature, and SOC trends over time.
*   **Adaptive Safety Protections**: Dynamic user-configurable alarm thresholds for cellular overvoltage, undervoltage, overcurrent limits, and extreme temperature ranges.

---

## 🛠️ Technology Stack

*   **Language**: 100% Kotlin with type-safety and modern styling.
*   **UI Framework**: Jetpack Compose (Material Design 3) with dynamic light/dark theming.
*   **Asynchronous Flow**: Kotlin Coroutines & `StateFlow`/`SharedFlow` for non-blocking reactive streams.
*   **Database Engine**: Room Database (SQLite) for high-performance offline-first telemetry logging.
*   **Networking / Hardware Interface**: Android Bluetooth Low Energy (BLE) stack with custom characteristic polling and parser systems.

---

## 📂 Architecture Overview

The app is structured following the **MVVM (Model-View-ViewModel)** pattern and Clean Architecture guidelines:

```
com.example
├── bluetooth
│   └── BmsBluetoothManager.kt       # Handles BLE connection state, peripheral parsing & virtual simulation
├── data
│   ├── BatteryHistoryLog.kt         # History schema for SQLite database
│   ├── BatterySettings.kt           # User configurations (cell count, alarm limits, etc.)
│   ├── BmsDao.kt                    # Data Access Object for Room database operations
│   ├── BmsDatabase.kt               # Central Room database interface
│   └── BmsRepository.kt             # Single source of truth combining Local Persistence & Settings
├── ui
│   ├── BmsViewModel.kt              # Business logic state machine linking Telemetry and UI
│   ├── components
│   │   ├── BmsTelemetryChart.kt     # Canvas-based real-time charting library for visual telemetry
│   │   └── ...                      # UI reusable elements
│   ├── screens
│   │   └── BmsMainScreen.kt         # The main multi-panel telemetry, logs, and settings dashboard
│   └── theme
│       └── Theme.kt                 # Material 3 dynamic color palettes
└── MainActivity.kt                  # App core entry point and ViewModel provider
```

---

## 🔌 Supported Protocols

The **BmsBluetoothManager** features a built-in multi-protocol parsing engine that decodes raw byte frames and registers for the following hardware platforms:

1.  **JBD / Xiaoxiang BMS**:
    *   *Service UUID*: `0000ff00-0000-1000-8000-00805f9b34fb`
    *   Decodes dual registers: basic information (`0x03`) and individual cell voltages (`0x04`).
    *   Translates individual cell balancing bitmasks and hardware-level alarms.
2.  **Daly BMS**:
    *   *Service UUID*: `0000fff0-0000-1000-8000-00805f9b34fb`
    *   Supports frame commands (`0x90` to `0x93`) for SOC, min/max cell indices, temperatures, and MOS states.
3.  **Generic Serial / Modbus RTU**:
    *   Decodes standard high-contrast serial registers for general-purpose custom BMS systems.

---

## 🚀 Building & Running

### Prerequisites
*   Android Studio Ladybug or newer.
*   Android SDK Platform 34 (Android 14) or newer.
*   A physical Android device with Bluetooth and Location permissions enabled (if connecting to real physical hardware).

### Compilation
Build the project using Gradle:
```bash
gradle :app:assembleDebug
```
