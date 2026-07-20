# Implementation Plan - HaveIBeenPwned Checker

Adds a new OSINT tool to check if an email address or account has been compromised in known data breaches, utilizing the HaveIBeenPwned API.

## Proposed Changes

### Domain Layer
*   #### [NEW] [HibpRepository](file:///home/michael/AndroidStudioProjects/RF_REAPR/app/src/main/java/com/fearmikey/rf_reapr/domain/repository/HibpRepository.kt)
    Interface for breach and paste lookups.
*   #### [NEW] [HibpModels](file:///home/michael/AndroidStudioProjects/RF_REAPR/app/src/main/java/com/fearmikey/rf_reapr/domain/model/HibpModels.kt)
    Data classes for `Breach` and `Paste`.

### Data Layer
*   #### [NEW] [HibpRepositoryImpl](file:///home/michael/AndroidStudioProjects/RF_REAPR/app/src/main/java/com/fearmikey/rf_reapr/data/repository/HibpRepositoryImpl.kt)
    Implementation using `OkHttpClient`. It will fetch the `hibp_api_key` from `SettingsRepository`.

### UI Layer
*   #### [NEW] [HibpViewModel](file:///home/michael/AndroidStudioProjects/RF_REAPR/app/src/main/java/com/fearmikey/rf_reapr/ui/web/HibpViewModel.kt)
    Handles the search logic, API key validation, and result state.
*   #### [NEW] [HibpScreen](file:///home/michael/AndroidStudioProjects/RF_REAPR/app/src/main/java/com/fearmikey/rf_reapr/ui/web/HibpScreen.kt)
    User interface for performing searches and viewing breach details.

### Integration
*   #### [MODIFY] [Screen](file:///home/michael/AndroidStudioProjects/RF_REAPR/app/src/main/java/com/fearmikey/rf_reapr/ui/navigation/Screen.kt)
    Add `HibpChecker` route.
*   #### [MODIFY] [MainActivity](file:///home/michael/AndroidStudioProjects/RF_REAPR/app/src/main/java/com/fearmikey/rf_reapr/MainActivity.kt)
    Register the new repository and viewmodel.
*   #### [MODIFY] [MainMenuScreen](file:///home/michael/AndroidStudioProjects/RF_REAPR/app/src/main/java/com/fearmikey/rf_reapr/ui/menu/MainMenuScreen.kt)
    Add the "HIBP Checker" tool under "Web & Infrastructure".

## Verification Plan

### Automated Tests
-   Unit tests for `HibpRepositoryImpl` with a mocked `OkHttpClient`.

### Manual Verification
-   Verify the tool shows a "Missing API Key" warning if no HIBP key is set in Settings.
-   Enter an email address and verify the results appear (Breach name, date, description).
-   Verify that the "Back" button navigates correctly back to the Main Menu.
