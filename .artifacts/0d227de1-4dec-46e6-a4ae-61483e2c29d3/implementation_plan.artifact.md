# Implementation Plan - Credential Tester Safety & Anti-Lockout

Add safety measures to the `CredentialTester` to prevent IP lockouts and service crashes on sensitive networking hardware.

## Proposed Changes

### [Scanner Component]

#### [MODIFY] [CredentialTesterRepositoryImpl.kt](file:///home/michael/AndroidStudioProjects/RF_REAPR/app/src/main/java/com/fearmikey/rf_reapr/data/repository/CredentialTesterRepositoryImpl.kt)
- **Increased Throttling**: Increase the mandatory delay between attempts to **1500ms** (1.5 seconds).
- **Lockout Detection**:
    - Stop the test immediately if the server returns HTTP `429 Too Many Requests` or `403 Forbidden` (after several 200s/401s).
    - Emit a specific `TestResult.Failure` with a lockout warning.

### [UI Component]

#### [MODIFY] [CredentialTesterScreen.kt](file:///home/michael/AndroidStudioProjects/RF_REAPR/app/src/main/java/com/fearmikey/rf_reapr/ui/scanner/CredentialTesterScreen.kt)
- **Warning Banner**: Add a `Card` above the "Start Test" button with an `Icons.Default.Warning` and a message: *"CAUTION: Automated testing can trigger account lockouts or IP bans on some hardware."*

## Verification Plan

### Automated Tests
- Build verification.

### Manual Verification
- Run a test against a target.
- Verify the 1.5s delay between `[DEBUG]` logs.
- Verify the presence of the warning banner in the UI.
