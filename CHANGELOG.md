# Changelog

All notable changes to this project will be documented in this file.

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
