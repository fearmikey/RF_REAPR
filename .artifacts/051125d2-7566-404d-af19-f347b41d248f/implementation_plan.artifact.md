# Implementation Plan - SDR Driver Launch Fix

Fix the "broken links" for the SDR driver launch by implementing a more robust detection and launch mechanism, handling F-Droid specifically, and addressing Android 11+ package visibility more comprehensively.

## User Review Required

> [!IMPORTANT]
> I will update the driver launch logic to specifically support F-Droid and try multiple launch methods (Launcher Intent, Explicit Activity, and F-Droid deep links). This should resolve the "broken link" issue even if the standard Play Store isn't available or the package name isn't indexed by the `market://` scheme in F-Droid.

## Proposed Changes

### Build Configuration

#### [MODIFY] [AndroidManifest.xml](file:///home/michael/AndroidStudioProjects/RF_REAPR/app/src/main/AndroidManifest.xml)
- Expand `<queries>` to include common intent patterns and more package names.

---

### UI Layer

#### [MODIFY] [SdrViewModel.kt](file:///home/michael/AndroidStudioProjects/RF_REAPR/app/src/main/java/com/fearmikey/rf_reapr/ui/sdr/SdrViewModel.kt)
- **Robust Launching**:
    1. Try `getLaunchIntentForPackage`.
    2. Try explicit `MainActivity` launch for known drivers.
    3. If neither works, provide a choice or a smarter fallback.
- **F-Droid Integration**: Add `org.fdroid.fdroid` to queries and support opening the app page directly in F-Droid using `fdroid.app://details?id=...` or a web fallback that F-Droid can intercept.

#### [MODIFY] [SdrScreen.kt](file:///home/michael/AndroidStudioProjects/RF_REAPR/app/src/main/java/com/fearmikey/rf_reapr/ui/sdr/SdrScreen.kt)
- Update the "Launch Driver" button to provide better feedback if the driver is not found.
- Add a "Manual Config" hint if the user wants to use a remote SDR or a driver I didn't detect.

## Verification Plan

### Manual Verification
- **Scenario 1: Driver Installed (F-Droid)**: Tap "Launch Driver" -> Verify it opens the Martin Marinov app directly without going to the Play Store.
- **Scenario 2: Driver Missing**: Tap "Launch Driver" -> Verify it opens F-Droid (if installed) or the browser to the F-Droid/Play Store page.
- **Scenario 3: Manual Trigger**: Verify that entering a host/port still works even if the "Launch" button fails (ensures no hard dependency on the button).
