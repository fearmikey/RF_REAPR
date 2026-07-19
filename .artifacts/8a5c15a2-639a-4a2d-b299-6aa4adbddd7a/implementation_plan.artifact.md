# Implementation Plan - Seamless Transition from Shortcut to App

Update the camera shortcut behavior so that hitting "Back" after capturing evidence prompts for an unlock (if necessary) and takes the user into the Evidence Gallery to review their work, instead of just closing the app.

## User Review Required

> [!IMPORTANT]
> This change allows a "locked" camera session to transition into a full app session. The system will prompt for your PIN/Pattern/Biometric before showing the gallery to ensure security.

## Proposed Changes

### [Navigation & Lifecycle]

#### [MODIFY] [MainActivity.kt](file:///home/michael/AndroidStudioProjects/RF_REAPR/app/src/main/java/com/fearmikey/rf_reapr/MainActivity.kt)
- **Enhanced `onBack` for Camera**:
    - Update the `onBack` logic for the `camera_capture` route.
    - If `popBackStack()` fails (shortcut launch):
        - Check if the device is currently locked using `KeyguardManager.isKeyguardLocked`.
        - If **Unlocked**: Navigate to `Screen.EvidenceGallery.route`.
        - If **Locked**: Call `KeyguardManager.requestDismissKeyguard`.
            - On **Success** (user unlocks): Navigate to `Screen.EvidenceGallery.route`.
            - On **Failure/Cancel**: Keep the app in the current state or `finish()` (as it remains locked).
- **Navigation Stack Management**:
    - Ensure that when transitioning to the Gallery from a shortcut launch, the "Back" button from the Gallery takes the user to the `MainMenu` instead of closing the app, providing a natural entry into the rest of the toolkit.

## Verification Plan

### Manual Verification
1.  **Shortcut Launch (Unlocked)**:
    - Launch via double-press power.
    - Hit "Back".
    - Verify it takes you to the **Evidence Gallery** for the current project.
2.  **Shortcut Launch (Locked)**:
    - Lock the phone.
    - Launch via double-press power.
    - Hit "Back".
    - Verify the system **Unlock Prompt** appears.
    - Unlock the phone.
    - Verify it takes you to the **Evidence Gallery**.
3.  **App Continuity**:
    - From the Gallery (after the shortcut transition), hit "Back" again.
    - Verify it takes you to the **Main Menu** of RF REAPR.
