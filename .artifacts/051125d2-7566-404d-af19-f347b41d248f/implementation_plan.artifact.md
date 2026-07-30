# Implementation Plan - SDR Controller Module

Implement a Software Defined Radio (SDR) Controller module for the `RF_REAPR` app. This module will interface with RTL-SDR dongles via the `rtl_tcp` protocol, providing real-time spectrum visualization and waterfall display.

## User Review Required

> [!IMPORTANT]
> This implementation relies on the `rtl_tcp` protocol. To use it with a local USB dongle, the user will need to have an RTL-SDR driver app (like the "RTL2832U driver" on Play Store) installed on their device. This is the standard approach for non-root Android SDR apps to ensure stable USB access and lifecycle management.

## Proposed Changes

### Build Configuration

#### [MODIFY] [libs.versions.toml](file:///home/michael/AndroidStudioProjects/RF_REAPR/gradle/libs.versions.toml)
- Add `jtransforms` version and library definition for FFT processing.

#### [MODIFY] [app/build.gradle.kts](file:///home/michael/AndroidStudioProjects/RF_REAPR/app/build.gradle.kts)
- Add `jtransforms` dependency.

---

### Domain Layer

#### [NEW] [SdrConfig.kt](file:///home/michael/AndroidStudioProjects/RF_REAPR/app/src/main/java/com/fearmikey/rf_reapr/domain/model/SdrConfig.kt)
- Data class for SDR parameters: `frequency`, `sampleRate`, `gain`, `ppm`.

#### [NEW] [FftData.kt](file:///home/michael/AndroidStudioProjects/RF_REAPR/app/src/main/java/com/fearmikey/rf_reapr/domain/model/FftData.kt)
- Data class for FFT results: `magnitudes` (FloatArray), `centerFrequency`, `bandwidth`.

#### [NEW] [SdrRepository.kt](file:///home/michael/AndroidStudioProjects/RF_REAPR/app/src/main/java/com/fearmikey/rf_reapr/domain/repository/SdrRepository.kt)
- Interface for SDR operations: `connect()`, `disconnect()`, `setFrequency()`, `setGain()`, and a `Flow<FftData>`.

---

### Data Layer

#### [NEW] [RtlTcpRepositoryImpl.kt](file:///home/michael/AndroidStudioProjects/RF_REAPR/app/src/main/java/com/fearmikey/rf_reapr/data/repository/RtlTcpRepositoryImpl.kt)
- Implementation using `java.net.Socket`.
- Implements the `rtl_tcp` binary protocol.
- Performs real-time DSP:
    - IQ Sample normalization.
    - FFT computation using JTransforms.
    - Magnitude calculation and smoothing.

---

### UI Layer

#### [NEW] [SdrScreen.kt](file:///home/michael/AndroidStudioProjects/RF_REAPR/app/src/main/java/com/fearmikey/rf_reapr/ui/sdr/SdrScreen.kt)
- Main screen with Spectrogram (top) and Waterfall (bottom).
- Tuning overlay for frequency and gain.

#### [NEW] [SdrViewModel.kt](file:///home/michael/AndroidStudioProjects/RF_REAPR/app/src/main/java/com/fearmikey/rf_reapr/ui/sdr/SdrViewModel.kt)
- Manages connection state, tuning logic, and FFT data flow.

#### [NEW] [WaterfallView.kt](file:///home/michael/AndroidStudioProjects/RF_REAPR/app/src/main/java/com/fearmikey/rf_reapr/ui/sdr/components/WaterfallView.kt)
- High-performance custom `Canvas` component.
- Uses a `Bitmap` or `ImageBitmap` buffer to efficiently draw the rolling waterfall.

---

### Integration

#### [MODIFY] [Screen.kt](file:///home/michael/AndroidStudioProjects/RF_REAPR/app/src/main/java/com/fearmikey/rf_reapr/ui/navigation/Screen.kt)
- Add `SdrController` route.

#### [MODIFY] [MainMenuScreen.kt](file:///home/michael/AndroidStudioProjects/RF_REAPR/app/src/main/java/com/fearmikey/rf_reapr/ui/menu/MainMenuScreen.kt)
- Add "SDR Controller" to the "Wireless & RF" section.

#### [MODIFY] [MainActivity.kt](file:///home/michael/AndroidStudioProjects/RF_REAPR/app/src/main/java/com/fearmikey/rf_reapr/MainActivity.kt)
- Instantiate `RtlTcpRepositoryImpl` and provide it to the `SdrViewModel`.

## Verification Plan

### Automated Tests
- **RtlTcpProtocolTest**: Verify command byte generation.
- **DspTest**: Verify FFT magnitude calculation with known signals (sine waves).

### Manual Verification
- Connect to `127.0.0.1:1234` with an RTL-SDR dongle attached.
- Tune to a known local FM station (e.g., 100.1 MHz).
- Verify the FM carrier is visible in the spectrogram.
- Observe the waterfall "scrolling" with signal history.
