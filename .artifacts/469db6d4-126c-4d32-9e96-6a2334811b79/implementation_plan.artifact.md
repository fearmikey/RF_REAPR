# Remove Credential Tester Module

The user wants to completely remove the "Credential Tester" module from the RF_REAPR project.

## Proposed Changes

### [DELETE] Files
- [CredentialTesterRepository.kt](file:///home/michael/AndroidStudioProjects/RF_REAPR/app/src/main/java/com/fearmikey/rf_reapr/domain/repository/CredentialTesterRepository.kt)
- [CredentialTesterRepositoryImpl.kt](file:///home/michael/AndroidStudioProjects/RF_REAPR/app/src/main/java/com/fearmikey/rf_reapr/data/repository/CredentialTesterRepositoryImpl.kt)
- [CredentialTesterScreen.kt](file:///home/michael/AndroidStudioProjects/RF_REAPR/app/src/main/java/com/fearmikey/rf_reapr/ui/scanner/CredentialTesterScreen.kt)
- [CredentialTesterViewModel.kt](file:///home/michael/AndroidStudioProjects/RF_REAPR/app/src/main/java/com/fearmikey/rf_reapr/ui/scanner/CredentialTesterViewModel.kt)

### [MODIFY] Component Updates

#### [MainActivity.kt](file:///home/michael/AndroidStudioProjects/RF_REAPR/app/src/main/java/com/fearmikey/rf_reapr/MainActivity.kt)
- Remove `CredentialTesterRepositoryImpl` instantiation.
- Remove `CredentialTesterViewModel` creation.
- Remove `CredentialTester` route from `NavHost`.
- Remove imports for `CredentialTesterScreen` and `CredentialTesterViewModel`.
- Remove `onNavigateToCredentialTester` lambda in `TopologyScreen` composable call.

#### [MainMenuScreen.kt](file:///home/michael/AndroidStudioProjects/RF_REAPR/app/src/main/java/com/fearmikey/rf_reapr/ui/menu/MainMenuScreen.kt)
- Remove the `Credential Tester` entry from the `allTools` list.

#### [Screen.kt](file:///home/michael/AndroidStudioProjects/RF_REAPR/app/src/main/java/com/fearmikey/rf_reapr/ui/navigation/Screen.kt)
- Remove the `CredentialTester` data object from the `Screen` sealed class.

#### [TopologyScreen.kt](file:///home/michael/AndroidStudioProjects/RF_REAPR/app/src/main/java/com/fearmikey/rf_reapr/ui/topology/TopologyScreen.kt)
- Remove the `onNavigateToCredentialTester` parameter from the `TopologyScreen` composable.
- Remove the usage of `onNavigateToCredentialTester` in the `DeviceDetailBottomSheet` call.

#### [DeviceDetailBottomSheet.kt](file:///home/michael/AndroidStudioProjects/RF_REAPR/app/src/main/java/com/fearmikey/rf_reapr/ui/topology/components/DeviceDetailBottomSheet.kt)
- Remove the `onNavigateToCredentialTester` parameter from the `DeviceDetailBottomSheet` composable.
- Remove the "Test Credentials" button from the UI.

## Verification Plan

### Automated Tests
- Run `./gradlew :app:assembleDebug` to ensure the project still builds successfully without any missing references.

### Manual Verification
- Launch the app and verify:
  - The "Credential Tester" tool is gone from the main menu.
  - The "Test Credentials" button is gone from the Device Detail bottom sheet in the Topology Map.
  - No crashes occur when navigating through the app.
