# Walkthrough: HID Injector Restructuring

I have restructured the HID Injector module to focus on managing and "flashing" scripts to external storage (like USB drives) instead of attempting local execution. This provides a more reliable workflow for use with dedicated HID hardware.

## Changes

### HID Module Restructuring

#### [HidInjectorViewModel.kt](file:///home/michael/AndroidStudioProjects/RF_REAPR/app/src/main/java/com/fearmikey/rf_reapr/ui/physical/HidInjectorViewModel.kt)
- **Removed Execution Logic**: Deleted the `parser` dependency and `runScript` function.
- **Added Flashing Logic**: Implemented `flashScript` and `flashSavedScript` which use Android's Storage Access Framework to write script content directly to a user-selected URI (e.g., a file on a USB drive).
- **Updated State**: Replaced `isExecuting` with `isFlashing` to track the status of write operations.

#### [HidInjectorScreen.kt](file:///home/michael/AndroidStudioProjects/RF_REAPR/app/src/main/java/com/fearmikey/rf_reapr/ui/physical/HidInjectorScreen.kt)
- **New Interaction Model**: The "Run" (Play) icon has been replaced with a "Flash to USB" (USB) icon in the top bar.
- **Improved Script Management**:
    - Tapping the USB icon in the top bar allows flashing the current editor content (defaults to `payload.txt`).
    - The "Saved Scripts" dialog now features a USB icon next to each script, allowing you to flash them directly to a file (defaults to `[ScriptName].txt`).
- **Storage Access**: Integrated `ActivityResultContracts.CreateDocument` to prompt the system file picker, ensuring compatibility with external storage.

#### [MainActivity.kt](file:///home/michael/AndroidStudioProjects/RF_REAPR/app/src/main/java/com/fearmikey/rf_reapr/MainActivity.kt)
- **DI Cleanup**: Removed the `hidParser` instantiation and updated the `HidInjectorViewModel` factory to reflect the simplified dependencies.

### Cleanup
- **Deleted `HidScriptParser.kt`**: Removed the obsolete interface and `DuckyScriptParser` implementation.

## Verification Results

### Automated Tests
- Ran `app:assembleDebug` and verified the project builds successfully.

### Manual Verification Path
1. Open **HID Injector**.
2. Tap the **USB icon** in the top bar.
3. Select a location in the system file picker.
4. Verify the log shows "Flashing successful!".
5. Open the **Scripts** list and verify the USB icon next to a saved script triggers a similar successful flow.
