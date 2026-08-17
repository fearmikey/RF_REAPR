# Changelog

All notable changes to this project will be documented in this file.

## [1.4.6] - 2026-08-17

### Added
- **Network Auditing**:
    - Integrated `iperf3` via JNI for high-performance network throughput testing.
    - Added specialized `IperfScreen` with real-time throughput visualization and detailed log output.
    - Added `IperfNative` JNI wrapper and `IperfRepository` for native execution within the app environment.
    - Integrated Iperf into the toolkit with support for both client and server modes (server mode coming soon).
- **Architecture**: Integrated CMake and C++ toolchain for native tool support.

### Changed
- **Maintenance**: Version bump to 1.4.6.

## [1.4.5] - 2026-08-01

### Added
- **Compliance & Reporting**: 
    - Significantly enhanced PDF and DOCX report generation with detailed "Audit Logs" section.
    - Added structured reporting for Port scans, Web scans (Cloud Assets, Subdomains, TLS Ciphers), Ping results, and Network Topology.
    - Improved network node tables in reports to include open ports and better layout.
- **UI/UX**: Added "View on GitHub" link in the Settings screen.

### Changed
- **Web & Infrastructure**: Refactored Web Audit view models to save structured log data (JSON), enabling rich reporting.
- **Maintenance**: Version bump to 1.4.5.

## [1.4.4] - 2026-07-30

### Added
- **System**: Introduced "Passive Mode" (Stealth) vs. "Active Mode" (Detectable) global toggle.
- **UI/UX**: Redesigned Main Menu with categorized toolkit cards and tool inhibitions for stealth operations.
- **Network Auditing**:
    - Integrated Service Discovery (mDNS) and UPnP/NAT-PMP Auditor.
    - Added DNS Security Auditor and Visual Traceroute.
    - Added ARP Spoofing Detector.
- **Physical Access**: Integrated HID Injector and Magnetometer into the toolkit.
- **Web & Infrastructure**: Added Subdomain Enumerator, TLS Cipher Scanner, and Cloud Asset Discovery.

### Changed
- **Architecture**: Significant expansion of the Repository pattern to support new network and physical audit tools.
- **Maintenance**: Version bump to 1.4.4.

### Removed
- **Modules**: Removed deprecated Credential Tester and legacy HID Script Parser.

## [1.4.3] - 2026-07-30

### Added
- **Wireless Auditing**: SDR Controller module for real-time spectrum analysis and waterfall display via RTL-SDR (rtl_tcp).
- **SDR Controller**: Frequency presets for FM Radio, Airband, NOAA Weather, and more.

### Fixed
- **SDR Controller**: Improved driver launch mechanism and package visibility for Android 11+.
- **SDR Controller**: Enhanced frequency tuning with MHz input support.

## [1.4.2] - 2026-07-19

### Added
- **Auditing Modules**:
    - HaveIBeenPwned Checker for account breach analysis.
    - UPnP/NAT-PMP Auditor for discovering router port mapping vulnerabilities.
    - Service Credential Tester for non-destructive default password checks.
    - ARP Spoofing Detector for monitoring network gateway security.
- **Settings**:
    - Integration for HaveIBeenPwned API key persistence.

### Changed
- **UI/UX**: Renamed HIBP references to full HaveIBeenPwned name for better clarity.
- **Maintenance**: Resolved various deprecation warnings in ArpDetector and CredentialTester UI.

## [1.4.1] - 2026-07-19

### Added
- **Networking**:
    - DNS Auditor for domain analysis.
    - Service Discovery (mDNS) to locate network services.
    - Traceroute utility for network path analysis.
    - Cloud Asset Scanner and Subdomain Finder for web reconnaissance.
    - TLS Cipher Scanner for SSL/TLS security audits.
- **Physical Security**:
    - HID Assets and HID Injector modules for hardware-based testing.
    - Magnetometer screen for physical environment checks.
- **Compliance & Reporting**:
    - Report Builder for generating comprehensive audit reports.
    - Evidence Capture enhancements with camera and gallery integration.
    - Automated cleanup worker for deleted evidence in Recycle Bin.
- **System**:
    - Dynamic version utility and centralized notification management.
    - Global "Stop All" receiver for background tasks.

### Changed
- **Architecture**: Major refactoring to Repository pattern across all modules for better testability and separation of concerns.
- **UI/UX**: Refined main menu structure and navigation flow.
- **Database**: Room database expansion to support HID assets and improved session management.

### Removed
- Outdated or redundant implementations of Packet Capture and RDAP tools.

## [1.3.0] - 2026-07-15

### Added
- **UI/UX**: Dynamic version display on the splash screen.

### Fixed
- **Evidence Capture**: Resolved undismissable project creator popup.
- **Modules**: Addressed various deprecations in BLE, Wi-Fi, and Database modules for better stability.

### Changed
- **Repository**: Cleaned up outdated APK files from the repository root.
- **System**: General refactoring and minor performance improvements.

## [1.2.6] - 2024-07-07

### Added
- **UI/UX Refinement**:
    - Renamed "Wireless & RF" category to "Wireless Auditing" for better consistency.
    - Moved "NFC Scanner" to the "Wireless Auditing" category.

### Fixed
- **Build Warnings & Stability**:
    - Resolved multiple build warnings across the project.
    - Added API level checks for Bluetooth scanning (API 31+).
    - Modernized vibration logic using `VibrationEffect` on supported versions.
    - Cleaned up unused imports and navigation objects.
    - Standardized code style with trailing lambdas and commas.

## [1.2.5] - 2024-05-20

### Added
- **Compliance Module**:
    - Audit Checklist Screen for various frameworks.
    - Evidence Capture system with camera integration.
    - Evidence Gallery and Project Selection.
    - Recycle Bin for deleted evidence with automated cleanup worker.
- **Wireless Tools**:
    - Bluetooth Proximity Finder for locating BLE devices.
    - Enhanced Wi-Fi Channel Graph.
- **Network Tools**:
    - Website Inspector for basic web auditing.
    - Improved Ping utility and Log viewer.
- **System**:
    - Room database implementation for local storage of sessions, nodes, and evidence.
    - Repository pattern implementation for data management.
    - Permission handling improvements.
- **UI/UX**:
    - Magnetometer screen for physical security checks.
    - HID Injector interface.
    - Main menu restructuring.

### Changed
- Updated app acronym to "Reconnaissance, Evaluation, Analysis, and Penetration Reporting".
- Refactored topology mapping and device identification logic.
- Updated project dependencies and Gradle configuration.

### Fixed
- Various UI layout issues and navigation bugs.

## [1.1.0] - Initial Release
- Basic network scanning capabilities.
- BLE and NFC scanning.
- Initial project structure.
