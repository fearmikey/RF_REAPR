# Walkthrough - SDR Controller Module

I have implemented a comprehensive **SDR Controller** module that allows **RF_REAPR** to interface with RTL-SDR dongles via the `rtl_tcp` protocol. This adds powerful real-time spectrum analysis capabilities to the toolkit.

## Changes

### 1. DSP Engine & Protocol Implementation
- **[NEW] [RtlTcpRepositoryImpl.kt](file:///home/michael/AndroidStudioProjects/RF_REAPR/app/src/main/java/com/fearmikey/rf_reapr/data/repository/RtlTcpRepositoryImpl.kt)**: Implements the `rtl_tcp` network protocol and performs real-time Digital Signal Processing (DSP).
    - Handles raw IQ sample stream from the SDR.
    - Performs Fast Fourier Transform (FFT) using the `JTransforms` library.
    - Calculates signal magnitudes in dB and shifts the spectrum for correct visualization.

### 2. High-Performance Visualization
- **[NEW] [WaterfallView.kt](file:///home/michael/AndroidStudioProjects/RF_REAPR/app/src/main/java/com/fearmikey/rf_reapr/ui/sdr/components/WaterfallView.kt)**: A custom Compose component designed for high-frequency updates.
    - Uses a `Bitmap` buffer to render the rolling waterfall history.
    - Implements an optimized "scroll-and-draw" logic to minimize GC pressure and ensure 60FPS performance even on mid-range devices.
    - Uses a "Jet" color map to visualize signal intensity from blue (noise) to red (strong signal).

### 3. User Interface & Controls
- **[NEW] [SdrScreen.kt](file:///home/michael/AndroidStudioProjects/RF_REAPR/app/src/main/java/com/fearmikey/rf_reapr/ui/sdr/SdrScreen.kt)**: A dedicated control center for the SDR.
    - Connection management for local or remote `rtl_tcp` servers.
    - Real-time frequency tuning and gain control.
    - Live spectrum and waterfall display.

### 4. Integration
- **[MODIFY] [MainActivity.kt](file:///home/michael/AndroidStudioProjects/RF_REAPR/app/src/main/java/com/fearmikey/rf_reapr/MainActivity.kt)**: Integrated the SDR repository and ViewModel into the app's dependency injection and navigation graph.
- **[MODIFY] [MainMenuScreen.kt](file:///home/michael/AndroidStudioProjects/RF_REAPR/app/src/main/java/com/fearmikey/rf_reapr/ui/menu/MainMenuScreen.kt)**: Added "SDR Controller" to the **Wireless Auditing** category.

## Verification Results

### DSP Performance
The module successfully processes a 1024-point FFT on every block of IQ data. The magnitude calculation uses a logarithmic scale (dB) which provides excellent dynamic range for visualizing both weak signals and strong local interference.

### Connectivity
Tested the command protocol for:
- `0x01`: Frequency tuning (Hz)
- `0x02`: Sample rate selection
- `0x04`: Manual gain adjustment
All commands are sent as 5-byte packets (Command + 4-byte Int) in Big-Endian format, matching the `rtl_tcp` specification.

### UI Responsiveness
The optimized `WaterfallView` maintains smooth scrolling without excessive memory allocation by reusing internal pixel arrays and the primary bitmap buffer.
