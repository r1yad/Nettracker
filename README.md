# NetTrack 📡

[![Android CI](https://img.shields.io/badge/CI-GitHub%20Actions-blue.svg?logo=githubactions&logoColor=white)](.github/workflows/android.yml)
[![Android](https://img.shields.io/badge/Platform-Android-3DDC84.svg?logo=android&logoColor=white)](https://developer.android.com)
[![Kotlin](https://img.shields.io/badge/Language-Kotlin-7F52FF.svg?logo=kotlin&logoColor=white)](https://kotlinlang.org)
[![Jetpack Compose](https://img.shields.io/badge/UI-Jetpack%20Compose%20M3-4285F4.svg?logo=jetpackcompose&logoColor=white)](https://developer.android.com/jetpack/compose)
[![Min SDK](https://img.shields.io/badge/Min%20SDK-24-blue.svg)](https://developer.android.com/about/dashboards)
[![Target SDK](https://img.shields.io/badge/Target%20SDK-36-green.svg)](https://developer.android.com)
[![Architecture](https://img.shields.io/badge/Architecture-MVVM%20%2B%20Clean-orange.svg)](https://developer.android.com/topic/architecture)
[![License](https://img.shields.io/badge/License-MIT-lightgrey.svg)](LICENSE)

**NetTrack** is a modern, high-precision Android data usage monitoring and threshold alerting app built with Kotlin and Jetpack Compose. It tracks your internet usage continuously in the background, breaks down consumption by individual applications, and pushes alerts whenever custom daily or monthly thresholds are surpassed.

---

## ✨ Features

- **Continuous Background Monitoring**:
  - Ongoing foreground service (`DataTrackerService`) with real-time persistent status notification showing today's Wi-Fi, Mobile data, and quota progress.
  - Periodic background checks via **Android Jetpack WorkManager** (`DataCheckWorker`) ensuring threshold alerts fire reliably even if the app is closed.
  - Automatic restart on device reboot (`BootReceiver`).

- **Threshold Alerts & Notifications**:
  - **Daily Limits**: Set custom limits (500 MB, 1 GB, 2 GB, 5 GB, or custom) with an early warning threshold (e.g. at 80%) and high-priority alarm notification when the 100% threshold is exceeded.
  - **Monthly Quotas & Billing Cycles**: Configurable monthly data quotas with custom billing cycle reset day (1st–28th).
  - **Automated Summary Reports**: Scheduled daily evening usage summaries and monthly cycle reports.
  - **Diagnostics & Testing**: One-tap in-app trigger buttons to preview and test threshold alerts and summary notifications immediately on device.

- **Per-App Data Usage Analytics**:
  - Real-time per-app breakdown querying system `NetworkStatsManager`.
  - Filters by **Time Range** (*Today*, *Yesterday*, *This Week*, *This Month*) and **Network Type** (*All Networks*, *Cellular Mobile*, *Wi-Fi*).
  - Search applications by name or package ID.
  - Deep-link to open Android system app settings directly from any app's telemetry sheet.

- **Modern Material 3 Telemetry UI**:
  - Animated circular telemetry dial highlighting daily quota consumption.
  - Split metric cards for Wi-Fi and Cellular Mobile traffic.
  - 7-day stacked bar chart showing historical consumption trends.
  - Historical event log of all triggered threshold alerts with timestamps and traffic recorded.

---

## 🛠️ Architecture & Tech Stack

- **UI & Design**: [Jetpack Compose](https://developer.android.com/jetpack/compose) with Material Design 3 (M3), dynamic state animation, and edge-to-edge support.
- **Language**: Kotlin 2.2 with Coroutines & StateFlow.
- **Background Tasks**: 
  - Android Foreground Service with persistent notifications.
  - Android Jetpack [WorkManager](https://developer.android.com/topic/libraries/architecture/workmanager) for periodic background evaluation.
- **Telemetry**: Android `NetworkStatsManager` (`Context.NETWORK_STATS_SERVICE`) & `AppOpsManager`.
- **Local Persistence**: [Room Database](https://developer.android.com/training/data-storage/room) & SharedPreferences for quota settings, historical daily snapshots, and alert logs.
- **Testing**: Local JVM unit tests using [Robolectric](https://robolectric.org/).

---

## 📱 Permissions Required

| Permission | Purpose |
|---|---|
| `android.permission.PACKAGE_USAGE_STATS` | Required by Android to read per-app internet telemetry and data usage stats. |
| `android.permission.POST_NOTIFICATIONS` | Allows pushing threshold warnings, limit exceeded alerts, and daily summary notifications (Android 13+). |
| `android.permission.FOREGROUND_SERVICE` | Keeps the live data monitor running in the background. |
| `android.permission.FOREGROUND_SERVICE_SPECIAL_USE` | Declares foreground service subtype for telemetry tracking (Android 14+). |
| `android.permission.RECEIVE_BOOT_COMPLETED` | Automatically resumes background monitoring when the device reboots. |

*Note: The app includes a user-friendly permission onboarding card with a direct shortcut to system settings.*

---

## 🚀 Getting Started

### Prerequisites

- Android Studio Ladybug / Meerkat (or newer)
- Android SDK 36 (compileSdk)
- Minimum SDK: Android 7.0 (API Level 24)
- JDK 17 or JDK 21

### Building from Source

1. **Clone the repository**:
   ```bash
   git clone https://github.com/your-username/nettrack.git
   cd nettrack
   ```

2. **Open in Android Studio**:
   - Open Android Studio, select **Open**, and navigate to the cloned `nettrack` directory.
   - Wait for Gradle sync to complete.

3. **Build via Command Line**:
   ```bash
   gradle assembleDebug
   ```

4. **Run Unit Tests**:
   ```bash
   gradle :app:testDebugUnitTest
   ```

---

## 📂 Project Structure

```
nettrack/
├── app/
│   ├── src/
│   │   ├── main/
│   │   │   ├── java/com/example/
│   │   │   │   ├── data/
│   │   │   │   │   ├── local/         # Room Database, DAO, Entities & Preferences
│   │   │   │   │   ├── model/         # Data Models & Enums
│   │   │   │   │   └── network/       # NetworkStatsHelper & Telemetry logic
│   │   │   │   ├── receiver/          # BootReceiver (boot completed restart)
│   │   │   │   ├── service/           # DataTrackerService, NotificationHelper & WorkManager
│   │   │   │   ├── ui/
│   │   │   │   │   ├── components/    # UsageGauge, ProgressBar, AppIconImage, Banner
│   │   │   │   │   ├── screens/       # Dashboard, Apps, Thresholds, History
│   │   │   │   │   └── theme/         # Colors, Typography & M3 Theme
│   │   │   │   ├── viewmodel/         # MainViewModel & UI State
│   │   │   │   ├── MainActivity.kt    # Main activity with edge-to-edge M3 Scaffold
│   │   │   │   └── NetTrackApplication.kt
│   │   │   ├── res/                   # Drawables, mipmaps, strings, XML rules
│   │   │   └── AndroidManifest.xml    # Permissions, services, receivers declarations
│   │   └── test/                      # Robolectric & JUnit test suite
│   └── build.gradle.kts               # Module build configuration (version 1.0.0)
├── gradle/
│   └── libs.versions.toml             # Gradle Version Catalog
├── build.gradle.kts                   # Root build file
├── settings.gradle.kts                # Project settings
└── README.md                          # Project documentation
```

---

## 📄 License

This project is open-source and available under the [MIT License](LICENSE).
