# Reduce Splash Screen Duration

The user reported that the splash screen lasts too long. This plan aims to reduce the splash screen duration from 4 seconds to 3 seconds.

## Proposed Changes

### UI Component

#### [MODIFY] [SplashScreen.kt](file:///home/michael/AndroidStudioProjects/RF_REAPR/app/src/main/java/com/fearmikey/rf_reapr/ui/splash/SplashScreen.kt)
- Update the `delay` in `SplashScreen` composable from `4000.milliseconds` to `3000.milliseconds`.

## Verification Plan

### Manual Verification
- Deploy the app and verify that the splash screen now transitions to the next screen (Main Menu or Permission Explanation) after 3 seconds instead of 4 seconds.
