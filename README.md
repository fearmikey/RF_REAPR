# RF-REAPR (Reconnaissance, Evaluation, Analysis, and Penetration Reporting)

RF-REAPR is a comprehensive Android-based security auditing and network reconnaissance tool. Designed for security professionals and enthusiasts, it provides a suite of tools to analyze, map, and evaluate the security posture of various network and radio frequency environments.

## Features

RF-REAPR integrates a wide range of security modules:

### Toolkit Dashboard & Operation Modes
*   **Centralized Dashboard**: Categorized security audit toolkit with instant access to network, wireless, web, physical, and compliance modules.
*   **Passive vs. Active Modes**: Global stealth mode toggle to inhibit active network transmissions when operating in sensitive environments.
*   **Background Scanning Service**: Persistent foreground service with real-time progress notifications and accurate scan time estimation (ETA).

### Compliance & Auditing
*   **Audit Checklists**: Built-in checklists for various security frameworks (ISO 27001, SOC2, etc.).
*   **Evidence Capture**: Integrated camera system to capture and tag physical security evidence with FOSS location tagging.
*   **Evidence Gallery**: Organize and review collected evidence by project and folder.
*   **Recycle Bin**: Secure deletion with an automated 30-day retention and cleanup worker.

### Network Discovery & Analysis
*   **Network Topology Map**: Interactive visualization of network structure and connected devices with persistent state.
*   **Port Scanner**: Identify open ports and potential vulnerabilities on network devices.
*   **Packet Capture (PCAP)**: Integrated traffic capture (VPN or Root) for deep packet analysis.
*   **Website Inspector**: Basic web auditing and security header analysis.
*   **Ping Tool**: Network latency and connectivity testing.
*   **Network Throughput (iPerf)**: High-performance bandwidth testing via integrated native iperf3.
*   **SNMP Browser**: Query and analyze network devices using the SNMP protocol.
*   **Shodan Intelligence**: Search for host details and vulnerabilities using the Shodan API, with InternetDB fallback for quick, no-key IP recon.
*   **DNS Enumerator & Query**: Subdomain discovery and custom DNS record querying (A, AAAA, MX, TXT, NS).
*   **Captive Portal Detector**: Test network connectivity and detect captive portal redirects.
*   **MAC Vendor Lookup & Subnet Calculator**: Identify hardware vendors from OUI MACs and calculate CIDR ranges.
*   **Wake-on-LAN (WoL) Injector**: Wake remote hosts via WoL magic packets.
*   **DHCP Monitor**: Monitor DHCP traffic for rogue servers.
*   **RDAP Auditor**: Query registration data for domains and IP ranges.

### Wireless Auditing
*   **Bluetooth Proximity Finder**: Locate and track BLE devices based on signal strength (RSSI).
*   **Wi-Fi Fingerprinting**: Analyze Wi-Fi environments, channel distribution, and signal quality.
*   **Cellular Tower Recon**: Analyze cellular network towers, signal metrics, and operator information.
*   **WPS PIN Calculator**: Audit router WPS PIN algorithms and security status.
*   **SDR Controller**: Real-time spectrum analysis and waterfall display via RTL-SDR (rtl_tcp).
*   **NFC Scanner**: Read and analyze NFC tag data and technology types.

### Web & Infrastructure Security
*   **Cert Decoder**: Decode and inspect SSL/TLS X.509 certificate chains.
*   **Hash Calculator**: Compute MD5, SHA-1, SHA-256, and SHA-512 cryptographic hashes.
*   **Reverse Shell Cheatsheet**: Reference payload generator for authorized penetration testing.

### Physical & Hardware Tools
*   **Magnetometer**: Detect magnetic fields and hidden electronic devices.
*   **USB OTG Auditor**: Inspect connected USB hardware and OTG storage devices.
*   **HID Injector**: Payload planner and interactive Ducky Script Builder with Storage Access Framework (SAF) USB drive support.

### Logging, Privacy & FOSS Compliance
*   **100% FOSS & Offline-First**: Zero proprietary SDKs or tracking frameworks (F-Droid reproducible build compliant).
*   **Centralized Logging**: Specialized log views for Wi-Fi, BLE, Port Scanning, Web, and Ping modules.
*   **Local Storage**: Robust data persistence using Room database for sessions, nodes, and evidence.

## Tech Stack

*   **Language**: [Kotlin](https://kotlinlang.org/)
*   **UI Framework**: [Jetpack Compose](https://developer.android.com/jetpack/compose) (Material 3)
*   **Database**: [Room](https://developer.android.com/training/data-storage/room)
*   **Networking**: [Retrofit](https://square.github.io/retrofit/) & [OkHttp](https://square.github.io/okhttp/)
*   **Architecture**: MVVM (Model-View-ViewModel) with Repository Pattern
*   **Concurrency**: [Kotlin Coroutines](https://kotlinlang.org/docs/coroutines-overview.html) & Flow

## Getting Started

### Prerequisites

*   Android 8.0 (API level 26) or higher.
*   Android Studio Ladybug or newer.

### Installation

#### Option 1: Download APK (Recommended for users)
1.  Download the latest `RF_REAPR_v1.5.5.apk` from the [Releases](https://github.com/fearmikey/RF_REAPR/releases) page of this repository.
2.  Transfer the APK to your Android device and install it (you may need to "Allow installation from unknown sources").

#### Option 2: Build from Source (Recommended for developers)
1.  Clone the repository:
    ```bash
    git clone https://github.com/fearmikey/RF_REAPR.git
    ```
2.  Open the project in **Android Studio**.
3.  Build and run the app on your physical device.

## Disclaimer

RF-REAPR is intended for **educational and authorized security auditing purposes only**. Unauthorized access to networks or devices is illegal. The developers assume no liability for misuse of this tool.

## License

This project is licensed under the MIT License - see the [LICENSE](LICENSE) file for details.
