# Implementation Plan - Credential Tester Status Log View

Add a terminal-like log view to the `CredentialTesterScreen` to show real-time updates during a credential test.

## Proposed Changes

### [Scanner Component]

#### [MODIFY] [CredentialTesterRepository.kt](file:///home/michael/AndroidStudioProjects/RF_REAPR/app/src/main/java/com/fearmikey/rf_reapr/domain/repository/CredentialTesterRepository.kt)
- Add `PairFailed(val pair: CredentialPair)` to the `TestResult` sealed class to allow logging of unsuccessful attempts.

#### [MODIFY] [CredentialTesterRepositoryImpl.kt](file:///home/michael/AndroidStudioProjects/RF_REAPR/app/src/main/java/com/fearmikey/rf_reapr/data/repository/CredentialTesterRepositoryImpl.kt)
- Emit `TestResult.PairFailed` when a credential pair fails the test.

#### [MODIFY] [CredentialTesterViewModel.kt](file:///home/michael/AndroidStudioProjects/RF_REAPR/app/src/main/java/com/fearmikey/rf_reapr/ui/scanner/CredentialTesterViewModel.kt)
- Add a `logs` StateFlow to track test events.
- Update `runTest` to append log messages based on `TestResult` emissions.
- Update `reset` to clear the logs.

#### [MODIFY] [CredentialTesterScreen.kt](file:///home/michael/AndroidStudioProjects/RF_REAPR/app/src/main/java/com/fearmikey/rf_reapr/ui/scanner/CredentialTesterScreen.kt)
- Add a terminal-style `Box` containing a `LazyColumn` to display the logs.
- Implement auto-scrolling to the bottom as new logs are added.
- Position the log view below the "Start Test" button and above the final result card.

## Verification Plan

### Automated Tests
- Build the project to ensure no regressions in the scanner or UI components.

### Manual Verification
- Run a credential test against a target.
- Verify that the terminal view appears and shows "Testing: user:pass ... FAILED" or "SUCCESS" logs in real-time.
- Ensure the view auto-scrolls as it fills up.
- Verify that "Clear Test" (or resetting) clears the logs.
