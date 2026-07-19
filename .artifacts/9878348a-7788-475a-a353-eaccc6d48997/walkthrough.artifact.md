# Walkthrough - Restrict App Access When Locked

I have updated the application to ensure that only the camera functionality is accessible when the device is locked. Any navigation away from the camera will now respect the device's security and hide the app behind the lock screen.

## Changes

### [MainActivity.kt](file:///home/michael/AndroidStudioProjects/RF_REAPR/app/src/main/java/com/fearmikey/rf_reapr/MainActivity.kt)

- **Removed Immediate Unlock**: The camera shortcut (e.g., double-press power) now launches the camera capture screen directly on top of the lock screen without an immediate PIN/Fingerprint prompt.
- **Dynamic Lock Screen Management**: Added a `NavController.OnDestinationChangedListener` that intelligently manages the `setShowWhenLocked` state.
    - It ensures `setShowWhenLocked(true)` is active for the **Camera Capture** screen.
    - It preserves the state during the **Splash** screen transition (fixing the blank screen/immediate unlock prompt issue).
    - It automatically calls `setShowWhenLocked(false)` when navigating to any other screen (like Main Menu or Settings), ensuring the app remains secure behind the lock screen.
- **Secure Transition**: The existing back handler on the camera capture screen still correctly prompts for an unlock if the user tries to exit to the gallery or main menu while the device is locked.

## Verification Results

### Automated Verification
- The code builds successfully and uses standard Android WindowManager flags to manage the lock screen visibility.

### Manual Verification Recommended
1. **Shortcut Launch**: Double-press the power button while the phone is locked. The camera should open immediately.
2. **Take Evidence**: Capture a photo. You should be able to interact with the camera and see the thumbnail without unlocking.
3. **Exit to App**: Tap "Exit Camera" or press "Back". The system should prompt you to unlock before showing the Evidence Gallery or Main Menu.
4. **Lock While In App**: If you lock the phone while in the main app and then use the camera shortcut, you should only see the camera. Navigating back should show the lock screen again.
