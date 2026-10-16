# AutoTune Studio Pro - Android Build & Installation Guide

### APK Build Status: **SUCCESSFUL**
- **Application Name**: AutoTune Studio Pro
- **Application ID (Package)**: `com.autotunestudiopro.app`
- **Output APK Path**: `app/build/outputs/apk/debug/app-debug.apk`
- **Artifact Download Path**: `AutoTuneStudioPro-debug.apk`
- **Build Type**: Android Debug APK (Signed with standard Android debug keystore for direct sideloading)
- **Target SDK**: Android 16 (API 36)
- **Minimum SDK**: Android 7.0 (API 24)

---

### Step-by-Step Installation on Your Android Phone

1. **Locate or Transfer the APK**:
   - The compiled APK is available directly at `app/build/outputs/apk/debug/app-debug.apk` (and in artifacts as `AutoTuneStudioPro-debug.apk`).
   - You can also export the project as a ZIP or download the APK file to your phone via USB, Google Drive, or local file sharing.

2. **Open the APK on Your Android Phone**:
   - Tap on `AutoTuneStudioPro-debug.apk` in your phone's File Manager or Downloads folder.

3. **Allow Installation from This Source**:
   - If prompted by Android with *"Install unknown apps"*, tap **Settings** and toggle **Allow from this source**.

4. **Tap Install**:
   - Confirm the prompt to install **AutoTune Studio Pro**.

5. **Open AutoTune Studio Pro**:
   - Launch the app from your home screen or app drawer.

6. **Grant Microphone Permission**:
   - On first launch, tap **"Grant Permission"** to allow the app to capture microphone audio for real-time DSP pitch detection, Auto-Tune, and recording.

---

### Building via Android Studio or Terminal

If you cloned or exported the project repository to your local computer:

1. **Open in Android Studio**:
   - Open Android Studio (Ladybug / Iguana or later).
   - Select **Open** and choose the project root folder.
   - Wait for Gradle sync to complete.

2. **Build Debug APK via Terminal**:
   ```bash
   ./gradlew assembleDebug
   ```
   The APK will be generated at:
   `app/build/outputs/apk/debug/app-debug.apk`

3. **Install Directly via ADB**:
   ```bash
   adb install -r app/build/outputs/apk/debug/app-debug.apk
   ```

4. **Build Release APK**:
   ```bash
   ./gradlew assembleRelease
   ```
