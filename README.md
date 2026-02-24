# Android Auto Google Drive Uploader

[![License: GPL v3](https://img.shields.io/badge/License-GPLv3-blue.svg)](https://www.gnu.org/licenses/gpl-3.0)
[![Android](https://img.shields.io/badge/Android-29%2B-green.svg)](https://developer.android.com)
[![Kotlin](https://img.shields.io/badge/Kotlin-2.0-purple.svg)](https://kotlinlang.org)

An Android app that **automatically uploads files to Google Drive**. Select files manually or watch a folder for changes — new files are synced to your Drive in the background via a foreground service.

## ✨ Features

- **Manual File Upload** — Pick files from your device and upload them to Google Drive
- **Folder Watching** — Monitor a folder for new files and auto-upload them
- **Background Sync** — Foreground service keeps uploads running reliably
- **Google Sign-In** — Secure authentication with your Google account
- **Upload Progress** — Real-time upload status and results
- **Material 3 UI** — Modern, clean interface built with Jetpack Compose

## 🛠️ Tech Stack

| Technology | Purpose |
|---|---|
| **Kotlin** | Primary language |
| **Jetpack Compose** | UI framework (Material 3) |
| **Google Drive API v3** | Cloud file storage |
| **Google Sign-In** | OAuth 2.0 authentication |
| **DataStore** | Local preferences storage |
| **Coroutines** | Asynchronous operations |
| **Foreground Service** | Background folder watching |

## 📋 Prerequisites

- **Android Studio** (latest stable)
- **Android SDK 29+**
- **Google Cloud Project** with Drive API enabled
- **OAuth 2.0 Client ID** (Android type) configured in Google Cloud Console

## 🚀 Setup

1. **Clone the repository**
   ```bash
   git clone https://github.com/samoba-islam/android-auto-google-drive-uploader-.git
   cd android-auto-google-drive-uploader-
   ```

2. **Configure Google Cloud**
   - Go to [Google Cloud Console](https://console.cloud.google.com/)
   - Create a new project (or select existing)
   - Enable **Google Drive API**
   - Go to **Credentials** → Create **OAuth 2.0 Client ID** (Android)
   - Add your app's **package name**: `com.shawon.gdrive`
   - Add your **SHA-1 fingerprint**:
     ```bash
     ./gradlew signingReport
     ```

3. **Build & Run**
   ```bash
   ./gradlew assembleDebug
   ```
   Or open in Android Studio and run on a device/emulator.

## 📁 Project Structure

```
app/src/main/java/com/shawon/gdrive/
├── MainActivity.kt                 # Entry point
├── auth/
│   └── GoogleAuthManager.kt        # Google Sign-In handling
├── data/
│   └── PreferencesManager.kt       # DataStore preferences
├── drive/
│   ├── DriveService.kt             # Google Drive API operations
│   └── UploadState.kt              # Upload state models
├── service/
│   ├── FolderWatchService.kt       # Foreground service for folder monitoring
│   └── RecursiveFileObserver.kt    # File system observer
└── ui/
    ├── screens/
    │   └── MainScreen.kt           # Main UI screen
    ├── theme/                       # Material 3 theming
    └── viewmodel/
        └── MainViewModel.kt        # UI state management
```

## 📄 License

This project is licensed under the **GNU General Public License v3.0** — see the [LICENSE](LICENSE) file for details.

```
Copyright (C) 2024  Android Auto Google Drive Uploader Contributors

This program is free software: you can redistribute it and/or modify
it under the terms of the GNU General Public License as published by
the Free Software Foundation, either version 3 of the License, or
(at your option) any later version.
```
## 👤 Author

**Shawon Hossain**

- GitHub: [@samoba-islam](https://github.com/samoba-islam)
- Website: [samoba.pages.dev](https://samoba.pages.dev)

---

## 🤝 Contributing

Contributions are welcome! Please feel free to submit a Pull Request.

1. Fork the repository
2. Create your feature branch (`git checkout -b feature/amazing-feature`)
3. Commit your changes (`git commit -m 'Add amazing feature'`)
4. Push to the branch (`git push origin feature/amazing-feature`)
5. Open a Pull Request

---

<p align="center">
  Made with ❤️ for the Android community
</p>
