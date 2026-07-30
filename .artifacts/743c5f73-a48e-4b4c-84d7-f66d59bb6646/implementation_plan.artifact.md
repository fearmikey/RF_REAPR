# Implementation Plan - Permission Explanations

The user wants to ensure that every permission requested by the app is preceded by an explanation screen. Currently, some permissions (like notifications) are requested immediately on startup, and others (like camera) have minimal explanation.

## User Review Required

> [!IMPORTANT]
> I will be consolidating the initial permission requests (Bluetooth, Location, Notifications) into a single explanation screen shown after the Splash screen. The Camera permission will also be added to this initial screen to ensure all primary features are ready, but `EvidenceCaptureScreen` will also have its own explanation fallback for shortcut launches.

## Proposed Changes

### [Core Permissions & Flow]

#### [MODIFY] [PermissionExplanationScreen.kt](file:///home/michael/AndroidStudioProjects/RF_REAPR/app/src/main/java/com/fearmikey/rf_reapr/ui/permissions/PermissionExplanationScreen.kt)
- Update `permissionsToRequest` to include `Manifest.permission.POST_NOTIFICATIONS` (for API 33+) and `Manifest.permission.CAMERA`.
- Add `PermissionItem` components for "Background Alerts" (Notifications) and "Evidence Capture" (Camera).
- Ensure the screen clearly explains *why* each is needed for a pentesting toolkit.

#### [MODIFY] [MainActivity.kt](file:///home/michael/AndroidStudioProjects/RF_REAPR/app/src/main/java/com/fearmikey/rf_reapr/MainActivity.kt)
- Remove `requestNotificationPermission()` from `onCreate`.
- Update the `SplashScreen` completion logic to check for all 4 permissions:
    - Bluetooth (Scan/Connect)
    - Location (Fine)
    - Notifications (Post - API 33+)
    - Camera
- Navigate to `Screen.PermissionExplanation` if *any* of these are missing.

### [Evidence Capture Specific]

#### [MODIFY] [EvidenceCaptureScreen.kt](file:///home/michael/AndroidStudioProjects/RF_REAPR/app/src/main/java/com/fearmikey/rf_reapr/ui/compliance/EvidenceCaptureScreen.kt)
- Improve the "Permissions required" fallback UI.
- Instead of a simple `Box` with `Text`, use a full-screen layout consistent with the app's aesthetic (similar to `PermissionExplanationScreen`) that specifically explains Camera and Location requirements for evidence tagging.
- Ensure `launchMultiplePermissionRequest()` is only called after the user interacts with the explanation.

## Verification Plan

### Automated Tests
- Check that the app builds successfully.
- Verify that permissions are correctly identified as missing in the `Splash` screen check.

### Manual Verification
1.  **Fresh Install**:
    - Open the app.
    - Verify no system permission prompts appear during Splash.
    - Verify the `PermissionExplanationScreen` appears after Splash.
    - Verify it lists Bluetooth, Location, Notifications, and Camera with explanations.
    - Click "CONTINUE" and verify system prompts appear.
2.  **Notification Check**:
    - Verify no notification prompt appears *until* the explanation screen button is clicked.
3.  **Camera Shortcut**:
    - Launch the app via camera shortcut (if possible in emulator/test device).
    - Verify that if permissions are missing, a proper explanation screen appears before the system camera prompt.
