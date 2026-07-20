# Walkthrough - Optional Camera Shortcut

I have implemented a new setting that allows users to enable or disable the camera shortcut (double-pressing the power button).

## Changes

### 1. Decoupled Camera Shortcut from MainActivity
Moved the camera intent filters from `MainActivity` to a new `activity-alias` named `.CameraShortcutActivity` in [AndroidManifest.xml](file:///home/michael/AndroidStudioProjects/RF_REAPR/app/src/main/AndroidManifest.xml). This allows the shortcut functionality to be toggled independently of the main app.

### 2. Updated Settings Data Layer
- Added `isCameraShortcutEnabled` and `setCameraShortcutEnabled` to [SettingsRepository.kt](file:///home/michael/AndroidStudioProjects/RF_REAPR/app/src/main/java/com/fearmikey/rf_reapr/domain/repository/SettingsRepository.kt).
- Implemented the logic in [SettingsRepositoryImpl.kt](file:///home/michael/AndroidStudioProjects/RF_REAPR/app/src/main/java/com/fearmikey/rf_reapr/data/local/SettingsRepositoryImpl.kt) to enable/disable the `.CameraShortcutActivity` component using `PackageManager.setComponentEnabledSetting`.

### 3. Updated Settings UI
- Added a new `Switch` in the [SettingsScreen.kt](file:///home/michael/AndroidStudioProjects/RF_REAPR/app/src/main/java/com/fearmikey/rf_reapr/ui/settings/SettingsScreen.kt) under the Appearance section.
- Exposed the new setting through [SettingsViewModel.kt](file:///home/michael/AndroidStudioProjects/RF_REAPR/app/src/main/java/com/fearmikey/rf_reapr/ui/settings/SettingsViewModel.kt).

## Verification Results

### Automated Tests
- Ran `app:assembleDebug` and the build finished successfully.

### Manual Verification
- You can now find the **Camera Shortcut** toggle in the **Settings** screen.
- When disabled, the app component that handles camera intents is programmatically disabled at the OS level, preventing it from appearing in the camera shortcut chooser or launching automatically.
