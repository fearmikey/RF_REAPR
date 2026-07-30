# Restructure HID Injector to Flash to External Storage

The HID Injector module currently attempts to execute scripts locally, which is unreliable on Android due to kernel/hardware limitations for acting as an HID device. This plan restructures the module to focus on managing scripts and "flashing" (writing) them to external USB storage for use with dedicated HID hardware (e.g., USB Rubber Ducky).

## User Review Required

> [!IMPORTANT]
> The "Run Script" functionality will be removed. Users will instead save scripts to their external storage. I will default the filename to `payload.txt` when flashing, as this is the standard for most HID injectors.

## Proposed Changes

### [HID Module]

#### [MODIFY] [HidInjectorViewModel.kt](file:///home/michael/AndroidStudioProjects/RF_REAPR/app/src/main/java/com/fearmikey/rf_reapr/ui/physical/HidInjectorViewModel.kt)
- Remove `parser: HidScriptParser` dependency.
- Remove `runScript()` and `isExecuting` state.
- Add `flashScript(payload: HidPayload, uri: Uri, context: Context)` to write the script content to the selected URI.
- Add `isFlashing` state to provide feedback during the write operation.

#### [MODIFY] [HidInjectorScreen.kt](file:///home/michael/AndroidStudioProjects/RF_REAPR/app/src/main/java/com/fearmikey/rf_reapr/ui/physical/HidInjectorScreen.kt)
- Update the "Run" button to a "Flash to USB" button.
- Implement `rememberLauncherForActivityResult` with `ActivityResultContracts.CreateDocument("text/plain")`.
- Update the "Saved Scripts" dialog to include a "Flash" icon for each entry, allowing direct flashing of saved scripts.
- Improve the log display to show flashing status instead of execution logs.

#### [MODIFY] [MainActivity.kt](file:///home/michael/AndroidStudioProjects/RF_REAPR/app/src/main/java/com/fearmikey/rf_reapr/MainActivity.kt)
- Remove `hidParser` instantiation and pass only `hidRepository` and `hidAssetRepository` to `HidInjectorViewModel`.

#### [DELETE] [HidScriptParser.kt](file:///home/michael/AndroidStudioProjects/RF_REAPR/app/src/main/java/com/fearmikey/rf_reapr/domain/service/HidScriptParser.kt)
- This interface and its implementation `DuckyScriptParser` are no longer needed.

## Verification Plan

### Automated Tests
- Verify the code compiles and the ViewModel logic for writing to a URI is correct.

### Manual Verification
- Deploy the app to a device.
- Navigate to HID Injector.
- Create or import a script.
- Tap "Flash to USB".
- Verify that the system file picker appears.
- Select a location (simulating a USB drive) and verify the "Flashing successful" log appears.
- Verify "Saved Scripts" list also allows flashing.
