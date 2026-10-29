# Manga Vision Bridge (Mihon Android Extension)

A dedicated native extension for **Mihon (and Tachiyomi forks)** that exposes an embedded HTTP REST and WebSocket bridge on your Android device (`127.0.0.1:8765`).

This extension allows the **Manga Vision AI Web Application** to:
1. **Browse your Mihon Library** (read titles, authors, categories, unread counts).
2. **Read downloaded chapters and CBZ archives** stored locally on your device with zero re-downloading and zero network usage.
3. **Synchronize reading progress back to Mihon** in real-time as you turn pages in the web reader.
4. **Remain active in the background** without Android battery killers terminating the connection, thanks to its built-in Foreground Service.

---

## Folder Structure

This directory (`/android-extension/`) is kept **strictly separate** from the web application codebase.

```
android-extension/
├── build.gradle.kts
├── settings.gradle.kts
├── README.md
└── src/
    └── main/
        ├── AndroidManifest.xml
        ├── res/
        │   └── values/strings.xml
        └── java/eu/kanade/tachiyomi/extension/all/mangavisionbridge/
            ├── MangaVisionBridge.kt       # Extension entry point & preferences
            ├── BridgeHttpServer.kt        # Ktor Netty HTTP & WebSocket server
            ├── BridgeWebSocketHub.kt      # Real-time event broadcasting
            ├── MihonDatabaseReader.kt     # Direct SQLite reader for tachiyomi.db
            ├── DownloadStreamer.kt        # Local CBZ & image file streamer
            └── BridgeForegroundService.kt # Android foreground service with notification
```

---

## How to Build the Extension APK

### Prerequisites
- Java JDK 17+ installed.
- Android SDK (API Level 34).

### Build Command
From this directory:

```bash
# On Linux / macOS:
./gradlew assembleDebug

# On Windows:
gradlew.bat assembleDebug
```

The compiled APK will be generated at:
```
build/outputs/apk/debug/android-extension-debug.apk
```

---

## How to Install into Mihon

1. **Transfer the APK to your phone** (via USB, local download, or Google Drive).
2. Tap the APK file on your Android device and tap **Install**.
3. Open **Mihon** (or Tachiyomi):
   - Go to **Browse** $\rightarrow$ **Sources**.
   - You will see **Manga Vision Bridge** listed under your sources.
4. Tap the gear icon (Settings) next to Manga Vision Bridge:
   - Check **"Enable Bridge Server"** (enabled by default).
   - Note the **Pairing PIN** (e.g. `123456`).
5. A persistent notification will appear in your Android status bar:
   `Manga Vision Bridge Active · Listening on port 8765`.

---

## How to Connect from the Web Application

1. Open your Manga Vision Web Application in Chrome or your browser on the phone (or PC on the same Wi-Fi).
2. Click **"Connect to Mihon Bridge"** in the top bar or inside **Settings $\rightarrow$ Mihon Connection**.
3. Enter your **Pairing PIN**.
4. Tap **Connect**. Your Mihon library and downloaded chapters will now appear directly in your Web Library!
