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

---

## 🚀 CI/CD Pipeline & Sideloading Releases

This repository includes a fully automated **GitHub Actions CI/CD pipeline** designed to build, sign, and publish manual sideloadable APK installers whenever you upgrade your app's version.

### 📦 How It Works
The pipeline is located in `.github/workflows/android-release.yml` and triggers automatically under the following conditions:
1. **File Change (Automatic Version Upgrade)**: Whenever you edit `/app/build.gradle.kts` (e.g. updating `versionName = "1.1"` or `versionCode`) and push the change to your primary branches (`main` or `master`). The workflow automatically:
   - Checks out the code and extracts the current `versionName` (e.g., `1.0`).
   - Checks your GitHub repository to see if a release tag `v<versionName>` already exists.
   - If it doesn't exist (meaning this is a new version), it automatically triggers the compilation and signs your APK.
   - Publishes a new GitHub Release with the tag `v<versionName>` and attaches your sideloadable APK file!
   - If the release already exists for that version, it gracefully skips building to avoid duplicate/overwritten builds.
2. **Manual Force Run**: You can trigger a build manually at any time via the **Actions** tab in your GitHub repository and check a force-build option.

The pipeline automates:
* Checking out code and setting up the modern **JDK 17** environment.
* Caching Gradle dependencies to make succeeding runs lightning-fast.
* Bundling and compiling a fully optimized release APK using `:app:assembleRelease`.
* **Zero-Setup Signing Fallback**: If you haven't uploaded custom keystores yet, the workflow automatically generates a temporary self-signed keystore to sign the APK, guaranteeing that your builds *always* compile and sign successfully.
* Packaging and attaching the `.apk` directly to a newly generated **GitHub Release** corresponding to your version tag.

---

### 🔑 Setting Up Custom Release Keys (Optional)

To sign your production-ready release builds with a custom permanent key, you need to create a Java Keystore (`.jks`) file, encode it in Base64, and save it and its credentials in your GitHub repository's secrets.

#### Step 1: Create a Keystore with the Alias `upload`
Run the following `keytool` command (available in any Java Development Kit / JDK) to generate your keystore.
> **Note:** The key alias **MUST** be set to `upload` to match the application's build configuration.

```bash
keytool -genkey -v -keystore my-upload-key.jks -keyalg RSA -keysize 2048 -validity 10000 -alias upload
```
During generation, you will be prompted to create:
1. A **keystore password** (used for `STORE_PASSWORD`).
2. A **key password** for the `upload` alias (used for `KEY_PASSWORD`).

---

#### Step 2: Encode the Keystore File in Base64
To store your binary `.jks` file inside a GitHub Secret, you must convert it into a flat Base64 text string. Use the appropriate command for your operating system:

##### 🐧 Linux
```bash
base64 -w 0 my-upload-key.jks
```

##### 🍎 macOS
```bash
openssl base64 -A -in my-upload-key.jks
# Or alternatively:
base64 -i my-upload-key.jks -o - | tr -d '\n'
```

##### 🪟 Windows (PowerShell)
```powershell
[Convert]::ToBase64String([IO.File]::ReadAllBytes("my-upload-key.jks")) | Out-File -FilePath my-upload-key.txt
```
*(Open the generated `my-upload-key.txt` file and copy its contents. Ensure there are no spaces or extra line breaks.)*

---

#### Step 3: Configure GitHub Secrets
Navigate to your repository on GitHub and go to:
**Settings ➡️ Secrets and variables ➡️ Actions ➡️ Repository secrets** (click **New repository secret**).

Add the following three secrets:

| Secret Name | Value to Provide |
| :--- | :--- |
| `KEYSTORE_BASE64` | The entire Base64 string of your keystore (copied from Step 2). |
| `STORE_PASSWORD` | The master password of your keystore. |
| `KEY_PASSWORD` | The key/alias password for the `upload` alias. |

Once configured, the next release build triggered by a version change will automatically decode your keystore, sign the APK, and publish it under the **Releases** tab!

---

### 📲 How to Sideload and Install on Your Device
To manually install the built release APK on your Android device:

1. **Download the APK**: Navigate to the **Releases** tab on your GitHub repository page and download the attached `BMS_Manager_vX.X.X.apk` file directly onto your Android device (or download to a computer and transfer via USB/email/drive).
2. **Enable Unknown Sources**:
   * On modern Android (8.0 Oreo and above), when you open the `.apk` file, your system will prompt you to authorize your file manager, web browser, or cloud storage app to install apps. Click **Settings** on the prompt and toggle **Allow from this source**.
   * On older Android devices, go to **Settings > Security**, and toggle on **Unknown Sources**.
3. **Run the Installer**: Open the downloaded `.apk` file and tap **Install**. Once complete, tap **Open** to run your brand-new BMS Manager!

