# Cachar DLSA — Case Records Manager

![Android](https://img.shields.io/badge/Platform-Android-green.svg)
![Kotlin](https://img.shields.io/badge/Language-Kotlin-purple.svg)
![Jetpack Compose](https://img.shields.io/badge/UI-Jetpack%20Compose-blue.svg)
![Material 3](https://img.shields.io/badge/Design-Material%203-orange.svg)
![Architecture](https://img.shields.io/badge/Architecture-MVVM-informational.svg)
![Database](https://img.shields.io/badge/Database-Room%20SQLite-lightgrey.svg)

**Cachar DLSA Case Records Manager** is an offline-first Android application custom-designed for the **Cachar District Legal Services Authority (DLSA)**. It streamlines the lifecycle management of pre-litigation, mediation, front office, and legal aid cases with high efficiency, intuitive navigation, and compact visual data presentation.

---

## ✨ Key Features

- **📊 Comprehensive Case Management**:
  - Track pre-litigation, front office, legal aid, and court-referred mediation cases seamlessly.
  - Record essential metadata including case numbers, categories, court/jurisdiction, assigned mediator, informant/petitioner, respondent, and intake/disposal dates.

- **🗂️ Compact Bento Grid & List Views**:
  - Information-dense, space-efficient card layouts designed for quick scanning and high readability on handheld devices.
  - Real-time status indicators (Settled, Not Settled, Pending, In Progress, Disposed) with color-coded badges.

- **🔍 Advanced Search & Filter Engine**:
  - Instant searching by case number, petitioner, respondent, mediator, or notes.
  - Multi-parameter filtering by status category and intake date ranges.

- **📱 Deep Case Detail Inspection**:
  - Full case breakdown with quick court details, mediator assignments, and party contacts.
  - Direct one-tap phone launcher for contacting petitioners and respondents directly from the app.
  - Quick-action interactive dropdown to update case status on the fly.
  - Integrated notes area for case progress tracking and activity updates.

- **📤 Data Export & Bulk Operations**:
  - Copy individual case details formatted as TSV (Tab-Separated Values) for easy pasting into legal spreadsheets or official reports.
  - Bulk Selection Mode for multi-case updates and batch deletions.

- **⚡ Offline Persistence & Performance**:
  - Built on Room SQLite database with StateFlow reactive updates for 100% offline data safety and instant app load times.

---

## 🛠️ Tech Stack & Architecture

- **Language**: 100% Kotlin
- **UI Framework**: Jetpack Compose with Material Design 3 (M3)
- **Architecture**: MVVM (Model-View-ViewModel) + Clean Architecture principles
- **Asynchronous Processing**: Kotlin Coroutines & `StateFlow`
- **Data Persistence**: Room Database (SQLite)
- **Dependency Management**: Gradle Version Catalog (`libs.versions.toml`)

---

## 🚀 Getting Started

### Prerequisites

- Android Studio Ladybug or newer
- JDK 17 or higher
- Android SDK 24+ (Android 7.0 Nougat minimum)

### Installation & Build

1. **Clone the Repository**:
   ```bash
   git clone https://github.com/your-username/cachar-dlsa.git
   cd cachar-dlsa
   ```

2. **Open in Android Studio**:
   Open Android Studio and select **Open** -> Navigate to the cloned project folder.

3. **Build the Project**:
   Build the project via `Build > Make Project` or using Gradle CLI:
   ```bash
   ./gradlew assembleDebug
   ```

4. **Run on Device / Emulator**:
   Select your target emulator or connected physical Android device and press **Run (Shift + F10)**.

---

## 📁 Project Structure

```text
├── app/
│   ├── src/main/
│   │   ├── java/com/example/
│   │   │   ├── MainActivity.kt        # Primary Entry Point & Jetpack Compose UI Views
│   │   │   └── data/                 # Room Entities, DAOs, Database & Repository
│   │   └── res/                      # XML Resources, Strings, Launch Icons & Themes
│   └── build.gradle.kts              # Module-level Gradle Build Script
├── build.gradle.kts                  # Root Gradle Configuration
├── settings.gradle.kts               # Project Settings & Repositories
└── metadata.json                     # AI Studio Platform Identification
```

---

## 🔐 Security & Privacy

- **Offline-First Storage**: All case records, party details, and phone contacts are stored locally on-device using SQLite.
- **No Unsolicited Cloud Syncing**: Keeps sensitive legal aid records fully private to the Cachar DLSA office environment.

---

## 📄 License

This repository is maintained for the **Cachar District Legal Services Authority**. All rights reserved.
