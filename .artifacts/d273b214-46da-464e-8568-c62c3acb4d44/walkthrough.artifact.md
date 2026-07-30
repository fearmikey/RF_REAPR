# Walkthrough - Reduced Splash Screen Duration

I have successfully reduced the splash screen duration by 1 second.

## Changes

### UI Component

#### [SplashScreen.kt](file:///home/michael/AndroidStudioProjects/RF_REAPR/app/src/main/java/com/fearmikey/rf_reapr/ui/splash/SplashScreen.kt)

Reduced the artificial delay from 4 seconds to 3 seconds to improve the app's perceived startup speed.

render_diffs(file:///home/michael/AndroidStudioProjects/RF_REAPR/app/src/main/java/com/fearmikey/rf_reapr/ui/splash/SplashScreen.kt)

## Verification Results

### Manual Verification
- Verified that the `LaunchedEffect` in `SplashScreen` now uses a 3000ms delay instead of 4000ms.
