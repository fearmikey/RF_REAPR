# Implementation Plan - Fix Camera Shortcut Blank Screen

The issue is that the `OnDestinationChangedListener` is calling `setShowWhenLocked(false)` immediately when the app starts at the `Splash` screen, even when launched via a camera shortcut. This hides the app behind the lock screen before it can navigate to the camera.

## User Review Required

> [!NOTE]
> I will adjust the logic so that `setShowWhenLocked(false)` is only called when navigating to a screen that is definitely NOT the camera and not a transient state like the Splash screen during a camera launch.

## Proposed Changes

### MainActivity

#### [MODIFY] [MainActivity.kt](file:///home/michael/AndroidStudioProjects/RF_REAPR/app/src/main/java/com/fearmikey/rf_reapr/MainActivity.kt)

-   **Update `OnDestinationChangedListener`**:
    -   Prevent `setShowWhenLocked(false)` from being called if the destination is `Splash` and the current intent is a camera intent.
    -   Ensure `setShowWhenLocked(true)` is called when entering `camera_capture` to be safe.
-   **Refine `handleCameraShortcuts`**:
    -   Keep the initial flag setting to ensure the activity can start over the lock screen.

## Verification Plan

### Manual Verification
1.  **Launch Camera from Lock Screen**:
    -   Double-press the power button.
    -   Verify the camera opens correctly (Splash screen might be visible for a split second, then camera).
2.  **Verify Unlock Flow**:
    -   Exit camera to gallery.
    -   Verify it still prompts for unlock.
3.  **Verify Non-Camera Launch**:
    -   Open app normally while locked (if possible via notification or other means).
    -   Verify it doesn't bypass the lock screen.
