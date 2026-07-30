# Walkthrough - Integrate Cloud Asset Discovery into Subdomain Enumerator

Streamlined the web reconnaissance workflow by adding a direct link from discovered subdomains to the Cloud Asset Discovery tool.

## Changes Made

### Navigation Layer

#### [Screen.kt](file:///home/michael/AndroidStudioProjects/RF_REAPR/app/src/main/java/com/fearmikey/rf_reapr/ui/navigation/Screen.kt)
- Updated `CloudAssetScanner` route to support an optional `domain` query parameter: `cloud_asset_scanner?domain={domain}`.
- Added `createRoute(domain: String?)` helper to the `CloudAssetScanner` screen object.

### App Layer

#### [MainActivity.kt](file:///home/michael/AndroidStudioProjects/RF_REAPR/app/src/main/java/com/fearmikey/rf_reapr/MainActivity.kt)
- Updated the navigation graph to pass the `domain` argument from the route to the `CloudAssetScannerScreen`.
- Updated the `SubdomainFinderScreen` initialization to provide the navigation callback.

### UI Layer

#### [CloudAssetScannerScreen.kt](file:///home/michael/AndroidStudioProjects/RF_REAPR/app/src/main/java/com/fearmikey/rf_reapr/ui/web/CloudAssetScannerScreen.kt)
- Added `initialDomain` parameter.
- Implemented `LaunchedEffect` to automatically trigger a scan when a domain is passed via navigation.

#### [SubdomainFinderScreen.kt](file:///home/michael/AndroidStudioProjects/RF_REAPR/app/src/main/java/com/fearmikey/rf_reapr/ui/web/SubdomainFinderScreen.kt)
- Added a new `Cloud` icon button to each discovered subdomain row.
- Clicking the button navigates to the Cloud Asset Discovery screen with the selected subdomain pre-filled.

## Verification Results

### Automated Tests
- Ran `./gradlew :app:assembleDebug` - **Build Successful**.

### Manual Verification
1. Open **Subdomain Enumerator**.
2. Run a scan for a domain.
3. For any discovered subdomain, click the **Cloud Discovery** icon (cloud icon).
4. Verify the app navigates to **Cloud Asset Discovery**.
5. Verify the subdomain is in the search field and the scan starts automatically.
