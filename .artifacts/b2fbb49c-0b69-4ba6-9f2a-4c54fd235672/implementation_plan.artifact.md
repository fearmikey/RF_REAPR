# Add API Key Recommendation Popup

The user wants to encourage getting a free NIST NVD API key to improve vulnerability scanning results. We will implement a recommendation popup in the Port Scanner and Network Topology (Audit) screens that directs users to the settings menu if no API key is configured.

## User Review Required

> [!NOTE]
> The popup will appear once per session (or every time the screen is entered if not set) to avoid being too intrusive while still ensuring the user is aware of the benefit of having an API key.

## Proposed Changes

### ViewModels

#### [MODIFY] [PortScannerViewModel.kt](file:///home/michael/AndroidStudioProjects/RF_REAPR/app/src/main/java/com/fearmikey/rf_reapr/ui/scanner/PortScannerViewModel.kt)
- Accept `SettingsRepository` in constructor.
- Expose `isApiKeySet: StateFlow<Boolean>`.

#### [MODIFY] [TopologyViewModel.kt](file:///home/michael/AndroidStudioProjects/RF_REAPR/app/src/main/java/com/fearmikey/rf_reapr/ui/topology/TopologyViewModel.kt)
- Accept `SettingsRepository` in constructor.
- Expose `isApiKeySet: StateFlow<Boolean>`.

### Dependency Injection

#### [MODIFY] [MainActivity.kt](file:///home/michael/AndroidStudioProjects/RF_REAPR/app/src/main/java/com/fearmikey/rf_reapr/MainActivity.kt)
- Inject `settingsRepository` into `PortScannerViewModel` and `TopologyViewModel`.

### UI Layer

#### [MODIFY] [PortScannerScreen.kt](file:///home/michael/AndroidStudioProjects/RF_REAPR/app/src/main/java/com/fearmikey/rf_reapr/ui/scanner/PortScannerScreen.kt)
- Add a state to show the API key recommendation dialog.
- Trigger the dialog in a `LaunchedEffect` if `isApiKeySet` is false.
- Provide a button to navigate to Settings.

#### [MODIFY] [TopologyScreen.kt](file:///home/michael/AndroidStudioProjects/RF_REAPR/app/src/main/java/com/fearmikey/rf_reapr/ui/topology/TopologyScreen.kt)
- Add a state to show the API key recommendation dialog.
- Trigger the dialog when the user attempts to start a "High-Impact Audit" if `isApiKeySet` is false.
- Provide a button to navigate to Settings.

## Verification Plan

### Automated Tests
- Build the project to ensure no DI or compilation errors.

### Manual Verification
- Clear the API key in Settings.
- Navigate to the Port Scanner; verify the recommendation popup appears.
- Click "Go to Settings" and verify it navigates correctly.
- Navigate to Topology Map and click "Audit"; verify the recommendation popup appears.
- Set an API key and verify the popups no longer appear.
