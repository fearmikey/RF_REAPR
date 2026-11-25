# Changelog

All notable changes to this project will be documented in this file.

## [1.6.0] - 2026-11-24

### Added
- **Engagements & Scope**:
    - Active project with an authorized scope (hosts, CIDRs, domains). Target inputs warn when a target is out of scope, and logs/scans are tagged with the project.
    - **Engagement Timeline**: Merged chronological view of logs, findings, evidence, scans and audit runs, with Markdown export.
    - **Nmap XML Import**: Import Nmap results into the network history and findings (external entities disabled, 25 MB cap).
- **Shared Findings Store**:
    - De-duplicated findings fed by Port Scanner, TLS, DNS audit, UPnP, SNMP, ARP, NFC, USB, Cellular, Wi-Fi, Captive Portal, Website Inspector, Subdomain Finder, Shodan, Threat Intel, HIBP, Cloud Assets, BLE GATT and Nmap import.
    - Triage status and notes, manual findings, and automatic FIXED → OPEN regression detection.
    - Export as CSV (formula-injection safe), JSON, Markdown, self-contained HTML, and DefectDojo Generic Findings JSON.
    - **Compliance mapping**: Findings suggest related checklist controls (never marked automatically).
- **Audit Mode**: Guided audit that runs selected checks (Wi-Fi, captive portal, DNS, UPnP, discovery, ports, TLS, SNMP) or a preset against the current network or a target list, records results as findings, grades the run (A–F) and exports a Markdown report. Respects Passive Mode and scope.
- **Audit History**: Saved audit runs with run-to-run comparison (new, resolved, persisting, not re-tested).
- **Known Devices**: Per-project allowlist of expected MACs; the Network Monitor flags unknown devices as findings.
- **Geotagged Survey**: Passive Wi-Fi/BLE/cell logging with GPS (`android.location` only), exportable to WiGLE CSV, KML and GPX. Nothing is uploaded.
- **Wi-Fi Site Survey**: Tap-to-measure on an imported floor plan with an interpolated signal heatmap.
- **BLE GATT Inspector**: Read-only service/characteristic enumeration behind an explicit consent prompt (never writes, subscribes or pairs).
- **Evidence Integrity**: SHA-256 hash recorded at capture/import (existing evidence is backfilled), verified on display, in exports and in reports. Evidence can be linked to a finding.
- **Report Builder**: Findings section with related controls and linked evidence (with hash and integrity status) in PDF and DOCX reports.
- **Encrypted Backup & Restore**: Password-protected backup (PBKDF2 + AES-256-GCM).
- **Background Network Watch**: Opt-in periodic network check (WorkManager, unmetered networks only, skipped in Passive Mode).
- **Main Menu**: Favorites and keyword search; two-pane layouts for Findings and Topology on large screens.

### Changed
- **Tool Enhancement Pass**: Every tool's logic was moved into unit-tested analyzers and the screens were reworked:
    - **Network**: Port Scanner (service banners, port catalog), UPnP/NAT-PMP auditor, DNS Security Auditor, Captive Portal, SNMP Browser, ARP spoofing detector, DHCP monitor, mDNS discovery, MAC/OUI lookup, Ping, Traceroute, iperf3 result parsing, packet capture summaries, subnet calculator, DNS query and Wake-on-LAN.
    - **Wireless & Physical**: BLE tracker detection, Wi-Fi security analysis (incl. evil-twin hints), SDR DSP, NFC, magnetometer, USB OTG and cellular recon. WPS and HID tooling remain defensive.
    - **Web**: TLS scanner grading, hash calculator, certificate decoder, A–F security header grading with cookie/CORS checks, and subdomain takeover hints (dangling CNAMEs).
    - **OSINT**: Cloud asset scanner (anonymously listable buckets only), Shodan/InternetDB, HIBP plus a key-free **Pwned Passwords** check (k-anonymity, only a 5-character SHA-1 prefix is sent), Threat Intel (AbuseIPDB verdicts), Username OSINT (every site rule re-verified, uncertain results reported as such), offline Phone OSINT with a full ITU calling-code table, scoped Google Dorks, EXIF privacy analysis with verified metadata stripping, Payload Encoder with 11 formats and a JWT analyzer, and a validated Reverse Shell reference.
- **Privacy**: Shodan, InternetDB and AbuseIPDB lookups refuse private, loopback and link-local addresses so internal addressing is never sent to third parties.
- **Foreground Service**: Uses the `location` type only while a user-started geotagged survey is running.
- **Architecture**: Process-wide dependency container (`AppContainer`); repositories are no longer recreated per Activity.
- **Database**: Schema v18 with exported Room schemas and non-destructive auto-migrations (16 → 17 → 18). Destructive fallback now applies only to legacy v1–15 databases.
- **Build**: Enabled KSP2; lint runs on a newer standalone lint version instead of disabling the crashing checks.
- **Testing**: 268 unit tests for the extracted analyzers, plus an instrumented database migration test.
- **Maintenance**: Version bump to 1.6.0 (versionCode 23).

## [1.5.7] - 2026-10-28

### Fixed
- **F-Droid Build Compatibility**:
    - Registered the native `iperf3` source as a proper Git submodule (`.gitmodules`) so it is fetched on clean clones and by the F-Droid build server.
    - Added checked-in, Android-specific `iperf_config.h` and `version.h` (`app/src/main/cpp/iperf_android/`) that were previously generated locally by `./configure` and missing from clean checkouts.
    - Pinned the submodule to upstream iperf3 commit `c9b7422`. The previously pinned commit existed only locally and could not be fetched. RF-REAPR's JNI patches (`longjmp` instead of `exit()`, idempotent signal handler, cJSON cast fixes) now live in the main repo under `iperf_android/`.
- **Settings**: Fixed the broken License link (now points to the repository `LICENSE` file).

### Changed
- **Reproducible Builds**:
    - Pinned `ndkVersion` to `27.0.12077973` so native libraries are built with an identical toolchain.
    - Native library is now built with `-ffile-prefix-map` and `--build-id=none` to strip machine-specific paths and IDs.
    - Disabled the Google-encrypted dependency metadata block (`dependenciesInfo`) in the APK/AAB.
- **Repository Hygiene**: Release keystores (`*.jks`, `*.keystore`) are no longer tracked and are now git-ignored.
- **F-Droid Metadata**: Updated build recipe (submodules, NDK, reproducible binary verification, `NonFreeNet` anti-feature) and fastlane short description (now within the 80-character limit).
- **Maintenance**: Version bump to 1.5.7 (versionCode 22).

## [1.5.6] - 2026-10-14

### Added
- **OSINT & Reconnaissance Suite**:
    - **Username OSINT**: Search for social accounts and digital footprints across 30+ platforms (GitHub, Reddit, Twitter, HackerNews, Instagram, etc.).
    - **Phone Number OSINT**: Analyze E.164 phone numbers for country code, carrier format, and international formatting.
    - **Google Dorks Generator**: Generate targeted search engine queries for exposed files, admin portals, open directories, and vulnerable endpoints.
    - **Threat Intelligence Aggregator**: Query AlienVault OTX and IP/Domain threat feeds for threat indicators and pulse counts.
- **Web & Analysis Tools**:
    - **EXIF Metadata Inspector**: Inspect image metadata, camera details, GPS coordinates, and stripped image generation.
    - **Payload Encoder / Decoder**: Multi-format converter supporting URL Encoding, Base64, Hex, HTML Entities, UTF-8, and Rot13.
- **Settings & API Management**:
    - Added AlienVault OTX API key management in Settings for Threat Intel queries.
- **Testing & Quality Assurance**:
    - Added comprehensive ViewModel unit test suite covering EXIF, Google Dorks, Payload Encoder, Phone OSINT, Threat Intel, and Username OSINT.

### Changed
- **Maintenance**: Version bump to 1.5.6 (versionCode 21).

## [1.5.5] - 2026-10-07

### Added
- **New Security Modules & Auditing Tools**:
    - **Network Auditing**: Added Captive Portal Detector, DNS Query Tool, MAC Vendor Lookup, Subnet IP Calculator, and Wake-on-LAN (WoL) Injector.
    - **Wireless Auditing**: Added Cellular Tower Reconnaissance and WPS PIN Calculator.
    - **Web & Infrastructure Security**: Added SSL/TLS X.509 Certificate Decoder, Cryptographic Hash Calculator (MD5, SHA-1, SHA-256), and interactive Reverse Shell Cheatsheet.
    - **Physical & USB Hardware Security**: Added USB OTG Device Auditor and HID Injector SAF storage integration.
- **100% FOSS & F-Droid Compliance**:
    - Replaced Google Play Services Location dependency (`play-services-location`) with native Android `LocationManager` (`android.location.*`) for 100% FOSS compliance.
    - Added Fastlane metadata structure (`full_description`, `short_description`, `title`, and versioned changelogs) under `fastlane/metadata/android/en-US/`.
    - Added official F-Droid build recipe specification (`fdroid/com.fearmikey.rf_reapr.yml`) configured for reproducible builds.
- **UI/UX & Community Support**:
    - Introduced app launch counter tracking and milestone "Support RF-REAPR" community dialog.
    - Added community support buttons ("Buy Me a Coffee", "Report Issues", and "Open Source MIT License" viewer) in Settings.
    - Centralized toolkit navigation into a responsive, unified dashboard (`MainMenuScreen`).
- **System & Foreground Scanning**:
    - Integrated `ScanForegroundService` and `ScanNotificationManager` for persistent background network scans with live status notifications.
    - Added `ScanTimeEstimator` for real-time ETA calculation during network topology discovery.

### Fixed
- **Vulnerability Auditor**:
    - Hardened NVD vulnerability repository parsing to handle missing CPE entries gracefully and avoid NPEs on null CVE records.
- **Unit Tests & Test Automation**:
    - Added comprehensive unit test coverage and test runner script (`run_tests.sh`).

### Changed
- **Maintenance**: Version bump to 1.5.5 (versionCode 20).

## [1.5.4] - 2026-10-07

### Added
- **Compliance & FOSS**:
    - Replaced Google Play Services Location dependency with native Android `LocationManager` for 100% FOSS compliance.
    - Added Fastlane and F-Droid deployment metadata definitions.
- **UI/UX & Community**:
    - Added support prompt dialog and links to community support (Buy Me a Coffee) in Settings.
    - Integrated MIT License viewer and GitHub issue reporting actions in Settings.

### Fixed
- **Network Auditing & Vulnerability Discovery**:
    - Hardened NVD vulnerability repository parsing to handle null CPE entries and improve software keyword search matching heuristics.

### Changed
- **Maintenance**: Version bump to 1.5.4.

## [1.5.3] - 2026-08-31

### Added
- **System & Services**:
    - Introduced `ScanForegroundService` and `ScanNotificationManager` for persistent background scan execution with real-time status notifications.
    - Added `ScanTimeEstimator` utility for calculating accurate ETA estimates during network discovery scans.
    - Centralized application initialization (`RfReaprApplication`) for notification channels and system service setups.
- **Topology & Network Discovery**:
    - Added live estimated scan time display and progress indicators to `TopologyScreen` and `NetworkMapView`.
    - Improved node filtering, interaction controls, and discovery status rendering.
- **Wireless Auditing**:
    - Enhanced `WifiChannelGraph` rendering with cleaner channel distribution visuals and improved fingerprint metrics.
- **Compliance & Guided Workflows**:
    - Redesigned `EvidenceCaptureScreen` UI layout and improved camera state handling.
    - Integrated live task status monitoring into `WorkflowScreen`.

### Changed
- **Maintenance**: Version bump to 1.5.3.

## [1.5.2] - 2026-08-28

### Added
- **Compliance & Reporting**:
    - Integrated **Bluetooth Proximity Analysis** into the automated Report Builder (PDF and DOCX).
    - Added structured tables for BLE devices, including RSSI levels, device names, and manufacturer data.
    - Improved report layout logic for multi-page data tables.
- **Wireless Auditing**: Bluetooth scans are now logged with full device metadata for audit persistence.

### Changed
- **Maintenance**: Version bump to 1.5.2.

## [1.5.1] - 2026-08-26

### Added
- **Physical Security**:
    - **HID Injector Enhancements**: Integrated USB Drive support via Storage Access Framework (SAF).
    - Added **Ducky Script Builder**: A new interactive sheet to compose and save Ducky Scripts directly to connected USB drives.
    - Implemented `UsbDriveRepository` for persistence and management of testing payloads across different storage providers.
- **System**:
    - Added `androidx.documentfile` dependency to support robust SAF operations.

### Fixed
- **Network Auditing**: Resolved stability issues in `iperf3` native execution and improved log reporting in `IperfViewModel`.
- **Submodules**: Fixed signed/unsigned comparison warnings in `iperf3`'s cJSON library.

## [1.5.0] - 2026-08-25

### Added
- **Guided Workflows**: Introduced a new "Operation Modes" start screen (`AppStartMenuScreen`) offering guided, step-by-step audit workflows alongside the classic manual toolkit, complete with mandatory step tracking and per-mode security warnings.
- **Web & Infrastructure**:
    - Integrated **InternetDB** for fast, no-API-key IP reconnaissance as a lightweight alternative/companion to Shodan.
    - Shodan tools now resolve hostnames to IPs automatically and log all manual lookups (Host Recon, Search, InternetDB Quick Recon) for reporting.
- **Performance**: Added Baseline Profile support (`androidx.profileinstaller`) to improve app startup performance.

### Changed
- **Build**: Release builds now enable code and resource shrinking (`isMinifyEnabled`/`isShrinkResources`) with expanded ProGuard rules; added a dedicated debug build type (`.debug` suffix).
- **Dependencies**: Upgraded Compose BOM to `2025.09.00` and Lifecycle libraries to `2.9.4`.
- **UI/UX**: Refreshed Main Menu layout, Topology map, Wi-Fi channel graph, and report graph rendering with numerous polish and stability improvements.
- **Compliance & Reporting**: Further expanded report generation to cover Shodan/InternetDB results.
- **Maintenance**: Version bump to 1.5.0; addressed Android 14+ foreground service and predictive back-gesture requirements.

## [1.4.9] - 2026-08-18

### Added
- **Network Auditing**:
    - **Packet Capture (PCAP)**: Introduced integrated network traffic capture module.
    - Support for both **VPN-based (Non-Root)** and **Native tcpdump (Root)** capture methods.
    - Added real-time traffic monitor and PCAP file management system.
- **Topology Mapping**:
    - Enhanced `NetworkMapView` with improved node rendering and interaction.
    - Added `DeviceDetailBottomSheet` for in-depth node inspection.
    - Refined node list and topology filtering logic.
- **System**:
    - Added necessary `VpnService` and storage configurations for traffic analysis.

### Changed
- **Maintenance**: Version bump to 1.4.9.

## [1.4.8] - 2026-08-17

### Added
- **Network Auditing**:
    - Integrated **SNMP Browser** module for querying network devices (v1/v2c).
    - Support for OID walking, system information retrieval, interface monitoring, and service discovery via SNMP.
    - Added SNMP scan results to the automated Report Builder (PDF/DOCX).
- **Topology Mapping**:
    - Major enhancements to the **Network Topology Map** with improved device type identification and risk analysis.
    - Implemented persistence for discovered network nodes and sessions using Room database.
    - Added detailed topology breakdown to audit reports.
- **System**:
    - Added `snmp4j` library for robust SNMP communication.

### Changed
- **Maintenance**: Version bump to 1.4.8.

## [1.4.7] - 2026-08-17

### Added
- **Network Auditing**:
    - Added **Iperf throughput results** to the automated Report Builder (PDF and DOCX).
    - Enhanced `IperfScreen` with a real-time bandwidth chart for visual performance tracking.
- **Web & Infrastructure**:
    - Improved **Shodan integration** with better domain search support and API key validation.
- **Architecture**:
    - Stabilized `iperf3` JNI implementation with robust error handling and pthread synchronization fixes.
    - Optimized native build process and removed redundant source files.

### Changed
- **Maintenance**: Version bump to 1.4.7.

## [1.4.6] - 2026-08-17

### Added
- **Network Auditing**:
    - Integrated `iperf3` via JNI for high-performance network throughput testing.
    - Added specialized `IperfScreen` with real-time throughput visualization and detailed log output.
    - Added `IperfNative` JNI wrapper and `IperfRepository` for native execution within the app environment.
    - Integrated Iperf into the toolkit with support for both client and server modes (server mode coming soon).
- **Web & Infrastructure**:
    - Integrated **Shodan API** for deep infrastructure and service intelligence.
    - Added `ShodanScreen` to search for host details, services, and vulnerabilities via IP or domain.
    - Added `ApiKeysScreen` in settings for centralized management of Shodan and other third-party API keys.
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
