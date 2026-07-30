# Walkthrough - Comprehensive Credential Suite

I have expanded the Credential Tester module with a comprehensive suite of common default usernames and passwords.

## Changes Made

### Data Layer
- [NEW] [DefaultCredentials.kt](file:///home/michael/AndroidStudioProjects/RF_REAPR/app/src/main/java/com/fearmikey/rf_reapr/data/repository/DefaultCredentials.kt): Created a central repository for default credentials, categorized by:
    - **Generic**: Standard admin/root pairs.
    - **Networking**: Vendor-specific defaults (Cisco, Ubiquiti, MikroTik).
    - **Databases**: Common DB admin credentials (PostgreSQL, Oracle, MySQL).
    - **IoT / Embedded**: Raspberry Pi, Telnet, and common numeric pins.
    - **Industrial / PLC**: Common industrial control system defaults.
    - **Vendor Specific**: Specific hardware and testing environment (e.g., Metasploitable) defaults.

### UI Layer
- [MODIFY] [CredentialTesterViewModel.kt](file:///home/michael/AndroidStudioProjects/RF_REAPR/app/src/main/java/com/fearmikey/rf_reapr/ui/scanner/CredentialTesterViewModel.kt): Updated the `runTest` function to use the new `DefaultCredentials.common` list, replacing the previous small hardcoded set. Removed unused imports.

## Verification Results

### Automated Tests
- Verified that the project builds successfully with the new files and changes.

### Manual Verification
- The Credential Tester screen now cycles through a much larger set of credentials during a test, increasing the likelihood of identifying vulnerable default configurations.
