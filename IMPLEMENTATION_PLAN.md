# Implementation Plan: CallGuard - Smart Call Screener

This document outlines the step-by-step implementation strategy for CallGuard, a sophisticated Android call screening and spam protection application.

## 🏗️ Project Architecture

The project follows a modular architecture to ensure scalability and maintainability.

### Module Breakdown
- **`com.callguard.app.screening`**: Handles the core logic for intercepting and managing calls.
- **`com.callguard.app.spam`**: Contains the spam detection engine, STIR/SHAKEN verification, and database interactions.
- **`com.callguard.app.conversation`**: Manages Text-to-Speech (TTS), Speech-to-Text (STT), and automated response generation.
- **`com.callguard.app.dashboard`**: UI components for analytics, history, and settings.
- **`com.callguard.app.data`**: Persistence layer using Room database and Preferences.

---

## 🚀 Phase 1: Core Screening (MVP) ✅
**Goal:** Establish the ability to intercept calls and play a basic greeting.

- [x] **Project Setup**
  - Initialize Android Studio project (Java only, no kotlin, Gradle 9.5.1).
  - Configure `AndroidManifest.xml` with required permissions (`READ_PHONE_STATE`, `ANSWER_PHONE_CALLS`, etc.).
- [x] **Basic Call Interception**
  - Implement `BroadcastReceiver` (`CallStateReceiver`) for `PHONE_STATE` changes.
  - Create `CallScreeningService` extending `InCallService` for Android 10+ Mode A integration.
- [x] **Automated Answering**
  - `ScreeningForegroundService` schedules a configurable ring-delay timer; answers via `InCallService`.
- [x] **Basic Greeting Playback**
  - `GreetingEngine` wraps Android TTS with async init, utterance callbacks, and graceful shutdown.
  - Plays the default or user-configured greeting after auto-answer.
- [x] **Notification System**
  - `NotificationHelper` builds the foreground screening notification (with "Take Call" action) and post-call summary notifications.
  - Channels registered in `CallGuardApplication.onCreate()`.
- [x] **Android 14 Compatibility & Hardening**
  - Declared `foregroundServiceType` for all services (`phoneCall`, `microphone`).
  - Implemented typed permissions for API 34+ compliance.
  - Hardened `ScreeningForegroundService` with threading safety (Main thread marshalling) and null-safe `WakeLock` handling.
  - Optimized `.gitignore` with a strict whitelist to keep the repository clean.

> **Phase 1 implemented & hardened.** The core engine is now stable on Android 14 (API 34).

---

## 🧠 Phase 2: Smart Features & Dashboard
**Goal:** Add transcription and a functional UI for users to view call history.

- [ ] **Speech-to-Text (STT) Integration**
  - Implement real-time transcription using Android Speech API or an offline library.
  - Implement android assitant i.e. gemini if available and online else switch to other offline library. 
- [ ] **Basic Dashboard UI**
  - Build the Home screen with Material You components.
  - Implement the "Screening History" list view.
- [ ] **Database Implementation**
  - Set up Room DB to store call logs, transcripts, and caller IDs.
- [ ] **Basic Spam Detection**
  - Implement rule-based filtering (e.g., block hidden numbers).
- [ ] **Contact Integration**
  - Fetch contacts to allow "Whitelisting" of known callers.

---

## 🛡️ Phase 3: Advanced Integration & Security
**Goal:** Deep integration with Android system and robust spam verification.

- [ ] **Phone App Integration (Mode A)**
  - Implement `InCallService` hooks for Android 10+ default dialer integration.
  - Design the Call Screening overlay UI.
- [ ] **STIR/SHAKEN Protocol Implementation**
  - Add logic to verify caller ID attestation levels via carrier headers.
- [ ] **ML-based Spam Analysis**
  - Integrate a local ML model for pattern recognition in call frequency and timing.
- [ ] **Privacy Hardening**
  - Implement local-only processing options for transcripts.
  - Add biometric lock for the app dashboard.

---

## ✨ Phase 4: Enhancement & Polish
**Goal:** Community features and AI-driven optimizations.

- [ ] **Community Spam Database**
  - Build a secure API client to sync with a cloud-based spam list.
  - Implement anonymous spam reporting functionality.
- [ ] **AI-Powered Responses**
  - Use NLP to categorize caller intent (e.g., "Delivery", "Appointment") and offer context-aware responses.
- [ ] **Analytics Suite**
  - Create visual charts for "Time Saved" and "Spam Blocked" metrics.
- [ ] **Multi-language Support**
  - Externalize strings and add support for multiple locales.

---

## 🧪 Testing & Validation
- **Unit Tests**: Coverage for spam detection logic and intent classification.
- **Integration Tests**: End-to-end call handling simulation.
- **Performance**: Monitor battery impact and memory usage using Android Profiler.
