# Android NFC: Tags, NDEF, and EMV Card Metadata

A focused Android Jetpack Compose example for reading NFC tags, writing NDEF messages, and demonstrating contactless EMV card parsing through Android's NFC APIs.

This project is intentionally narrow. It is not trying to be a payment application, a wallet, a point-of-sale terminal, or a general NFC framework. The goal is to make the essential Android NFC mechanics visible: enable reader mode, inspect tag technologies, parse NDEF records, write NDEF messages, exchange ISO-DEP APDUs, parse EMV TLV data, and present the result safely in a Compose UI.

The app demonstrates three flows:

- Scan NFC tags and display technologies, UID, protocol details, NDEF records, message size, tag capacity, and raw MIFARE Ultralight pages when available
- Write text or URI NDEF records to compatible tags
- Read contactless EMV payment-card metadata through ISO-DEP and display only privacy-safe card data

This Android-only project is derived from a Compose Multiplatform NFC project. The Android version is being published first because Android-only clients often evaluate a conventional native Android project more quickly. The CMP version, which demonstrates the same core NFC functionality across Android, iOS, and JVM desktop targets, will be published imminently after additional polish and testing.

---

## Relationship to the CMP version

The Android-only project intentionally keeps the extracted implementation code under the original `com.roywatson.cmp_nfc` package. The Android application id is `com.roywatson.android_nfc`, but the core UI, NFC reader/writer interfaces, EMV parser, NDEF parser, and Android NFC implementations retain their CMP package lineage.

This is deliberate: the project is meant to show how the Android version can be extracted from the Compose Multiplatform codebase with minimal changes, while still publishing as a conventional single-platform Android app. Renaming the implementation packages to match the Android-only project structure would be a trivial refactor and would have no adverse effect on app functionality; the package names are preserved here only to make the CMP-to-Android extraction lineage obvious.

---

## Running the project

Clone the repository and open it in Android Studio. The project is a standard single-module Android application using Jetpack Compose.

To build the Android debug APK:

```bash
./gradlew :app:assembleDebug
```

To run the unit tests:

```bash
./gradlew :app:testDebugUnitTest
```

To compile the instrumented tests:

```bash
./gradlew :app:compileDebugAndroidTestKotlin
```

To run the same verification used while preparing this project:

```bash
./gradlew :app:testDebugUnitTest :app:compileDebugAndroidTestKotlin :app:assembleDebug
```

You need an Android device with NFC hardware to exercise the NFC flows. NFC is declared as optional in the manifest so the app can still install on devices without NFC, but the reader and writer screens will report that NFC is unavailable.

---

## What this demonstrates

**Android reader mode:**

The app uses `NfcAdapter.enableReaderMode()` for foreground NFC interactions. Reader mode gives the app direct control over NFC tag callbacks while the activity is in the foreground.

The Scan and Credit Card flows are one-shot sessions. After a result or error, reader mode is disabled so stale callbacks cannot overwrite the displayed result while the UI waits for "Scan Another Tag" or "Read Another Card".

The Write flow intentionally behaves differently. After a successful write, reader mode remains active until the user cancels, starts another write, changes tabs, or leaves the activity. This prevents Android from immediately rediscovering the freshly written NDEF tag and dispatching it to another system/default handler while the tag is still in the NFC field.

**NFC tag inspection:**

The scanner reads as much platform-provided tag metadata as Android exposes:

- NFC-A, NFC-B, NFC-F, NFC-V, ISO-DEP, MIFARE Classic, MIFARE Ultralight, NDEF, and NDEF Formatable technologies
- UID
- ATQA, SAK, historical bytes, system codes, max transceive sizes, and related protocol details
- NDEF type, writability, capacity, records, payloads, and parsed text/URI content
- MIFARE Ultralight raw memory pages when available

The UI separates actual NDEF message size from tag capacity. Message size is computed from the serialized NDEF message; tag capacity is reported separately from Android's `Ndef.maxSize`.

**NDEF writing:**

The writer supports text and URI records:

```kotlin
NdefRecord.createTextRecord("en", text)
NdefRecord.createUri(uri)
```

The code handles writable NDEF tags and formatable tags. It checks read-only status and capacity before writing, and reports useful errors when a tag cannot accept the requested message.

**EMV card metadata:**

The Credit Card screen demonstrates ISO-DEP APDU exchange and EMV TLV parsing:

1. SELECT PPSE to discover payment applications
2. SELECT AID for the first application
3. GET PROCESSING OPTIONS to obtain AFL data when available
4. READ RECORD across AFL entries or a conservative fallback range
5. Parse PAN, expiry, cardholder name when exposed, and card network

This app demonstrates how to extract full PAN values and the EMV metadata needed for payment processing, but to protect user privacy, full PAN values are used only transiently and are never retained in app state.

The retained `CardData` model stores display-safe data such as masked PAN, last4, expiry date, optional cardholder name, and card type. Test fixtures use obviously synthetic payment-card data.

---

## Project structure

```text
app/src/main/java/com/roywatson/cmp_nfc/
  App.kt                         # Compose UI, bottom navigation, NFC screen state
  MainActivity.kt                # Android entry point

  NfcReader.kt                   # Reader contract
  NfcWriter.kt                   # Writer contract
  NfcCardReader.kt               # EMV card reader contract

  AndroidNfcReader.kt            # Android tag scanning implementation
  AndroidNfcWriter.kt            # Android NDEF writing implementation
  AndroidNfcCardReader.kt        # Android ISO-DEP card reader implementation

  NfcTagData.kt                  # Tag, NDEF, and raw page display models
  NfcReadState.kt                # Scanner state
  NfcWriteState.kt               # Writer state
  CardReadState.kt               # EMV reader state
  CardData.kt                    # Display-safe card data model

  NdefParser.kt                  # NDEF record parsing
  EmvParser.kt                   # EMV BER-TLV parsing and card network helpers
  EmvCardReader.kt               # Platform-independent EMV APDU flow

app/src/test/java/com/roywatson/cmp_nfc/
  NdefParserTest.kt              # NDEF text, URI, and MIME parsing tests
  EmvParserTest.kt               # AID, AFL, PAN, expiry, and cardholder parsing tests
  CardDataTest.kt                # Display-safe card masking tests

app/src/androidTest/java/com/roywatson/cmp_nfc/
  AppContextInstrumentedTest.kt  # Android-only application id assertion
```

---

## Intentional simplifications

This repository is built as a portfolio and article companion project, not as a payment product.

**It does not process payments.**

The EMV flow demonstrates NFC/APDU mechanics and TLV parsing. It does not implement transaction authorization, cryptogram handling, tokenization, certification, PCI scope management, terminal risk management, or any payment network integration.

**It retains only display-safe card data.**

The parser necessarily sees raw EMV bytes while interpreting card records. The app converts any parsed PAN immediately into masked display values and last4. The full PAN is not retained in app state.

The app does not persist user data and Android backup is disabled.

**It uses a direct Compose UI.**

The UI is intentionally plain. The purpose is to exercise the NFC reader, writer, and parser paths, not to demonstrate a custom design system. I have separate portfolio projects for custom Android and Compose Multiplatform theming.

**The extracted package name is preserved.**

Keeping `com.roywatson.cmp_nfc` is an intentional lineage signal. It makes the Android-only project easy to compare with the Compose Multiplatform project from which it was extracted.

**NFC sessions are simple and visible.**

Switching tabs terminates pending NFC work. Scan and Credit Card sessions stop reader mode after terminal results. Write holds reader mode after success for Android dispatch reasons. Those choices are small, explicit lifecycle rules rather than a larger NFC session framework.

---

## Tech stack

| | |
|---|---|
| Language | Kotlin 2.2.10 |
| UI framework | Jetpack Compose |
| Compose BOM | 2026.02.01 |
| Material | Material 3 |
| Android Gradle Plugin | 9.2.1 |
| Android SDK | compile 36.1, min 26, target 36 |
| Coroutines | 1.11.0 |
| JVM target | 11 |

---

## About

Before Roy Watson wrote a line of Android code, he was programming the propellant management system for NASA's Delta launch vehicles, building radiation detection software for Los Alamos and Sandia National Laboratories, and developing embedded vision systems for IBM's autonomous vehicle research. After 30+ years as a systems developer, he approaches Android differently than most.

Over the past 16 years he has specialized in Android, staying current with every major evolution of the platform: Kotlin, Jetpack Compose, and now Compose Multiplatform (KMP) for iOS, Desktop, and Web. Recent clients include the New York Public Library, Auddia, Bechtel, and Goodyear. When performance work requires dropping into C/C++, SIMD intrinsics, or custom memory management, he does not hand it off.

His personal published apps have accumulated 70,000+ Android downloads (4.5 star) and 207,000+ iOS downloads (4.6 star). He studied Physics and Mathematics at Purdue University and is a member of Mensa International.

This project is part of a series of public examples demonstrating current Android and Compose Multiplatform architecture and patterns. The series begins with [Android Custom Theme](https://github.com/roywatson/android_custom_theme) and [Compose Multiplatform Custom Theme](https://github.com/roywatson/cmp_custom_theme), which build a complete custom theming system from first principles. Subsequent projects introduce [Dependency Injection with Koin](https://github.com/roywatson/cmp_di_koin), [DataStore Preferences](https://github.com/roywatson/cmp_di_koin_datastore_prefs), [HTTP networking with ktor](https://github.com/roywatson/cmp_ktor), and [Compose Multiplatform Tone Generator](https://github.com/roywatson/cmp_tone_generator). This project adds a focused Android NFC example: tag scanning, NDEF writing, and safe EMV card metadata parsing.

Questions, corrections, or suggestions are welcome. Reach out through [roywatson.app](https://roywatson.app) or directly at rwatson@roywatson.com.

Available for contract and full-time engagements: [roywatson.app](https://roywatson.app)

[roywatson.app](https://roywatson.app) | [linkedin.com/in/roywatson3](https://linkedin.com/in/roywatson3) | [github.com/roywatson](https://github.com/roywatson)
