# Add GitHub Link to Settings Menu

The goal is to add a clickable hyperlink to the project's GitHub repository at the bottom of the Settings screen.

## Proposed Changes

### UI Layer

#### [MODIFY] [SettingsScreen.kt](file:///home/michael/AndroidStudioProjects/RF_REAPR/app/src/main/java/com/fearmikey/rf_reapr/ui/settings/SettingsScreen.kt)

- Add a `TextButton` at the bottom of the `SettingsScreen` composable that opens the GitHub repository URL (`https://github.com/fearmikey/RF_REAPR`) using `LocalUriHandler`.
- Ensure the link is styled consistently with other links in the screen (e.g., using `TextDecoration.Underline` and the appropriate theme color).

## Verification Plan

### Automated Tests
- I will check if the code compiles by running a build.

### Manual Verification
- Deploy the app to a device/emulator.
- Navigate to the Settings screen.
- Scroll to the bottom and verify the GitHub link is present.
- Click the link and verify it opens the correct URL in the browser.
