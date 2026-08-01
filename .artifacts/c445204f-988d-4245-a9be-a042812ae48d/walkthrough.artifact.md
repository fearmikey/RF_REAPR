# Walkthrough - Add GitHub Link to Settings

I have added a hyperlink to the RF_REAPR GitHub repository at the bottom of the Settings screen.

## Changes Made

### UI Components
- Modified [SettingsScreen.kt](file:///home/michael/AndroidStudioProjects/RF_REAPR/app/src/main/java/com/fearmikey/rf_reapr/ui/settings/SettingsScreen.kt) to include a "View on GitHub" `TextButton` below the version information.
- The link uses `LocalUriHandler` to open `https://github.com/fearmikey/RF_REAPR`.
- The link style matches other external links in the settings menu, including theme-dependent coloring.

## Verification Results

### Automated Tests
- Ran `:app:assembleDebug` and the build passed successfully.

### Manual Verification
- The code change correctly positions the GitHub link at the bottom of the scrollable settings column.
