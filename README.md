# CallGuard: Smart Call Screener 📱🛡️

[![Android Version](https://img.shields.io/badge/Android-8.0%2B-green.svg)](https://developer.android.com)
[![License](https://img.shields.io/badge/License-MIT-blue.svg)](LICENSE)
[![Build Status](https://img.shields.io/badge/Build-Gradle-orange.svg)](https://gradle.org)

**CallGuard** is a powerful, privacy-first Android application designed to put you back in control of your phone. Using advanced screening technology and machine learning, CallGuard intercepts unknown callers, asks them why they are calling, and provides you with a real-time transcript before you even pick up.

---

## ✨ Key Features

### 🎙️ AI Call Screening
- **Automated Assistant:** Automatically answers calls from unknown numbers after a custom ring duration.
- **Real-Time Transcription:** Watch the conversation happen in real-time with high-accuracy speech-to-text.
- **Interactive Responses:** Use pre-set or AI-generated responses to ask for more info or tell the caller to wait.

### 🛡️ Advanced Spam Protection
- **STIR/SHAKEN Integration:** Verifies caller identity at the carrier level to prevent caller ID spoofing.
- **ML Detection Engine:** Analyzes call patterns, frequency, and origin to identify potential spam with high confidence.
- **Community-Powered:** Access a global database of reported spam numbers and contribute your own reports.

### 📊 Smart Dashboard
- **Comprehensive Logs:** Review transcripts and recordings of every screened call.
- **Analytics:** Visualize your "Time Saved" and "Spam Blocked" statistics.
- **Material You Design:** A beautiful, dynamic interface that adapts to your system theme.

### 🔒 Privacy First
- **Local Processing:** Transcripts are processed locally on your device by default.
- **Encrypted Data:** All logs and recordings are stored with industry-standard encryption.
- **No Third-Party Tracking:** We value your privacy; no external analytics SDKs are included.

---

## 🏗️ Architecture

The project is built with a modular approach for maximum performance and reliability:

- **`InCallService` Integration:** Seamlessly hooks into the Android default dialer framework.
- **Room Persistence:** Fast and secure local storage for your call history.
- **TTS/STT Engine:** Leverages the latest Android APIs for natural voice synthesis and recognition.

---

## 🚀 Getting Started

### Prerequisites
- Android Studio Ladybug or newer.
- Android SDK 34 (Android 14).
- A physical device or emulator running Android 8.0+.

### Installation
1. Clone the repository:
   ```bash
   git clone https://github.com/TheCaptainCook/CallGuard-Smart-Call-Screener.git
   ```
2. Open the project in Android Studio.
## 🚀 Recent Stability Improvements (May 2026)
- **Android 14 Ready:** Full compliance with API 34 foreground service requirements (typed services & permissions).
- **Safety Hardening:** Fixed critical threading crashes and implemented null-safe hardware management (WakeLocks, PowerManager).
- **Gradle Optimization:** Cleaned up deprecated Gradle properties for compatibility with upcoming AGP 10.0.
- **Clean Repository:** Implemented a strict whitelist-based `.gitignore` to prevent build artifacts and IDE noise from polluting the codebase.

---

## 🛠️ Configuration

CallGuard offers two integration modes:
- **Mode A (System Integrated):** Set CallGuard as your default Caller ID & Spam app for the best experience.
- **Mode B (Standalone):** Use the custom overlay UI if you prefer to keep your existing dialer.

---

## 🤝 Contributing

We welcome contributions! Please see our [CONTRIBUTING.md](CONTRIBUTING.md) for details on our code of conduct and the process for submitting pull requests.

---

## 📄 License

This project is licensed under the MIT License - see the [LICENSE](LICENSE) file for details.
