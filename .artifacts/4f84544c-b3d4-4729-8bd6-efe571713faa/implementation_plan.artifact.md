# Implementation Plan - Remove Packet Capture Module

The Packet Capture (PCAP) module is being removed as it does not meet the user's requirements without root access. This involves deleting the dedicated files and removing all integration points across the project.

## Proposed Changes

### [Component] Domain Layer
#### [DELETE] [PacketCaptureRepository.kt](file:///home/michael/AndroidStudioProjects/RF_REAPR/app/src/main/java/com/fearmikey/rf_reapr/domain/repository/PacketCaptureRepository.kt)
#### [DELETE] [AppInfo.kt](file:///home/michael/AndroidStudioProjects/RF_REAPR/app/src/main/java/com/fearmikey/rf_reapr/domain/model/AppInfo.kt)

### [Component] Data Layer
#### [DELETE] [PacketCaptureRepositoryImpl.kt](file:///home/michael/AndroidStudioProjects/RF_REAPR/app/src/main/java/com/fearmikey/rf_reapr/data/repository/PacketCaptureRepositoryImpl.kt)
#### [DELETE] [CaptureVpnService.kt](file:///home/michael/AndroidStudioProjects/RF_REAPR/app/src/main/java/com/fearmikey/rf_reapr/data/service/CaptureVpnService.kt)

### [Component] UI Layer
#### [DELETE] [PacketCaptureViewModel.kt](file:///home/michael/AndroidStudioProjects/RF_REAPR/app/src/main/java/com/fearmikey/rf_reapr/ui/pcap/PacketCaptureViewModel.kt)
#### [DELETE] [PacketCaptureScreen.kt](file:///home/michael/AndroidStudioProjects/RF_REAPR/app/src/main/java/com/fearmikey/rf_reapr/ui/pcap/PacketCaptureScreen.kt)

### [Component] Integration & Cleanup
#### [MODIFY] [AndroidManifest.xml](file:///home/michael/AndroidStudioProjects/RF_REAPR/app/src/main/AndroidManifest.xml)
- Remove `QUERY_ALL_PACKAGES` permission and its tool override.
- Remove `CaptureVpnService` service declaration.

#### [MODIFY] [MainActivity.kt](file:///home/michael/AndroidStudioProjects/RF_REAPR/app/src/main/java/com/fearmikey/rf_reapr/MainActivity.kt)
- Remove PCAP-related imports.
- Remove instantiation of `pcapRepository`.
- Remove instantiation of `pcapViewModel`.
- Remove `PacketCapture` navigation route from the `NavHost`.

#### [MODIFY] [Screen.kt](file:///home/michael/AndroidStudioProjects/RF_REAPR/app/src/main/java/com/fearmikey/rf_reapr/ui/navigation/Screen.kt)
- Remove the `PacketCapture` route definition.

#### [MODIFY] [MainMenuScreen.kt](file:///home/michael/AndroidStudioProjects/RF_REAPR/app/src/main/java/com/fearmikey/rf_reapr/ui/menu/MainMenuScreen.kt)
- Remove the "No-Root Packet Capture" tool entry from the `allTools` list.

## Verification Plan

### Automated Verification
- Run a project build (`./gradlew assembleDebug`) to ensure no dangling references remain.

### Manual Verification
- Deploy the app to a device.
- Verify that "No-Root Packet Capture" is no longer visible in the Main Menu.
- Navigate through the app to ensure no crashes occur due to missing routes or DI components.
