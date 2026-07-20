# Implementation Plan - Optional Camera Shortcut

Make the camera shortcut (double pressing the power button) an optional setting in the app settings menu.

## User Review Required

> [!NOTE]
> The camera shortcut will be enabled by default to maintain current behavior, but can now be disabled in Settings.

## Proposed Changes

### Android Manifest
#### [MODIFY] [AndroidManifest.xml](file:///home/michael/AndroidStudioProjects/RF_REAPR/app/src/main/AndroidManifest.xml)
- Move camera intent filters from `MainActivity` to a new `activity-alias` named `.CameraShortcutActivity`.
- This allows enabling/disabling the shortcut functionality independently of the main app entry point.

### Domain Layer
#### [MODIFY] [SettingsRepository.kt](file:///home/michael/AndroidStudioProjects/RF_REAPR/app/src/main/java/com/fearmikey/rf_reapr/domain/repository/SettingsRepository.kt)
- Add `isCameraShortcutEnabled` Flow.
- Add `setCameraShortcutEnabled(enabled: Boolean)` method.

### Data Layer
#### [MODIFY] [SettingsRepositoryImpl.kt](file:///home/michael/AndroidStudioProjects/RF_REAPR/app/src/main/java/com/fearmikey/rf_reapr/data/local/SettingsRepositoryImpl.kt)
- Implement storage for the camera shortcut setting.
- Include logic to enable/disable the `CameraShortcutActivity` component using `PackageManager`.

### Presentation Layer
#### [MODIFY] [SettingsViewModel.kt](file:///home/michael/AndroidStudioProjects/RF_REAPR/app/src/main/java/com/fearmikey/rf_reapr/ui/settings/SettingsViewModel.kt)
- Expose the camera shortcut setting to the UI.
#### [MODIFY] [SettingsScreen.kt](file:///home/michael/AndroidStudioProjects/RF_REAPR/app/src/main/java/com/fearmikey/rf_reapr/ui/settings/SettingsScreen.kt)
- Add a UI toggle (Switch) for the camera shortcut setting.

## Verification Plan

### Manual Verification
1. Open the app and go to **Settings**.
2. Locate the new "**Camera Shortcut**" toggle.
3. Toggle it **OFF**.
4. Lock the device and double-press the power button. Verify that RF_REAPR does **not** open (or the system camera opens if it's the only one).
5. Toggle it **ON**.
6. Lock the device and double-press the power button. Verify that RF_REAPR opens to the camera capture screen.
