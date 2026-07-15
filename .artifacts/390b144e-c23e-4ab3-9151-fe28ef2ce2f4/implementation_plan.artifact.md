# Magnetometer Module Enhancements Implementation Plan

Improve user experience and accuracy by providing usage guidance and smoothing out detection status transitions.

## Proposed Changes

### UI Components

#### [MODIFY] [MagnetometerScreen.kt](file:///home/michael/AndroidStudioProjects/RF_REAPR/app/src/main/java/com/example/rf_reapr/ui/physical/MagnetometerScreen.kt)
- **Calibration Popup**: Add an `AlertDialog` that appears when the screen is first launched, advising users to keep the back of the phone clear of their hands for better sensor accuracy.
- **Hysteresis Logic**: Update `DetectionStatus` to use a state-based approach with hysteresis to prevent rapid flickering of status messages when the field strength is near a threshold.

### ViewModel

#### [MODIFY] [MagnetometerViewModel.kt](file:///home/michael/AndroidStudioProjects/RF_REAPR/app/src/main/java/com/example/rf_reapr/ui/physical/MagnetometerViewModel.kt)
- **Status State**: Optionally move the status calculation logic here to manage hysteresis more robustly across recompositions.

## Verification Plan

### Manual Verification
1. Open the Magnetometer screen and verify the "Accuracy Tip" popup appears.
2. Dismiss the popup and ensure it doesn't reappear until the screen is re-entered (or implement logic to show it only once per session).
3. Test hysteresis by placing the device near a magnetic source that causes the reading to hover around a threshold (e.g., 80 $\mu T$). Verify that the status doesn't flicker rapidly.
