# Implementation Plan - Integrate Cloud Asset Discovery into Subdomain Enumerator

Add a direct link from discovered subdomains to the Cloud Asset Discovery tool to streamline the reconnaissance workflow.

## Proposed Changes

### Navigation Layer

#### [MODIFY] [Screen.kt](file:///home/michael/AndroidStudioProjects/RF_REAPR/app/src/main/java/com/fearmikey/rf_reapr/ui/navigation/Screen.kt)
- Update `CloudAssetScanner` to support an optional `domain` parameter.
- Add a `createRoute(domain: String?)` helper function.

### App Layer

#### [MODIFY] [MainActivity.kt](file:///home/michael/AndroidStudioProjects/RF_REAPR/app/src/main/java/com/fearmikey/rf_reapr/MainActivity.kt)
- Update the `NavHost` composable for `Screen.CloudAssetScanner.route` to extract the `domain` argument.
- Pass the extracted `domain` to the `CloudAssetScannerScreen`.

### UI Layer

#### [MODIFY] [CloudAssetScannerScreen.kt](file:///home/michael/AndroidStudioProjects/RF_REAPR/app/src/main/java/com/fearmikey/rf_reapr/ui/web/CloudAssetScannerScreen.kt)
- Add `initialDomain: String? = null` parameter to the `CloudAssetScannerScreen` composable.
- Use `LaunchedEffect` to trigger `viewModel.scanAssets(initialDomain)` if `initialDomain` is provided and a scan isn't already running.

#### [MODIFY] [SubdomainFinderScreen.kt](file:///home/michael/AndroidStudioProjects/RF_REAPR/app/src/main/java/com/fearmikey/rf_reapr/ui/web/SubdomainFinderScreen.kt)
- Update `SubdomainFinderScreen` to accept an `onNavigateToCloudScanner: (String) -> Unit` callback.
- Pass this callback down through `SubdomainResultsList` to `SubdomainItemRow`.
- Add a `Cloud` icon button in `SubdomainItemRow` that triggers the navigation.

## Verification Plan

### Automated Tests
- Build the app using `./gradlew :app:assembleDebug` to ensure no compilation errors.

### Manual Verification
- Run a subdomain search for a domain (e.g., `google.com`).
- Once subdomains are discovered, click the new "Cloud" icon on one of the results (e.g., `mail.google.com`).
- Verify that the app navigates to the Cloud Asset Discovery screen, the "Target Domain" field is pre-filled with the subdomain, and a scan starts automatically.
