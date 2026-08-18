# RF-REAPR (Reconnaissance, Evaluation, Analysis, and Penetration Reporting)

RF-REAPR is a comprehensive Android-based security auditing and network reconnaissance tool. Designed for security professionals and enthusiasts, it provides a suite of tools to analyze, map, and evaluate the security posture of various network and radio frequency environments.

## Features

RF-REAPR integrates a wide range of security modules:

### Compliance & Auditing
*   **Audit Checklists**: Built-in checklists for various security frameworks (ISO 27001, SOC2, etc.).
*   **Evidence Capture**: Integrated camera system to capture and tag physical security evidence.
*   **Evidence Gallery**: Organize and review collected evidence by project and folder.
*   **Recycle Bin**: Secure deletion with an automated 30-day retention and cleanup system.

### Network Discovery & Analysis
*   **Network Topology Map**: Interactive visualization of network structure and connected devices.
*   **Port Scanner**: Identify open ports and potential vulnerabilities on network devices.
*   **Website Inspector**: Basic web auditing and security header analysis.
*   **Ping Tool**: Network latency and connectivity testing.
*   **Network Throughput (iPerf)**: High-performance bandwidth testing via integrated iperf3.
*   **SNMP Browser**: Query and analyze network devices using the SNMP protocol.
*   **Shodan Intelligence**: Search for host details and vulnerabilities using the Shodan API.
*   **DNS Enumerator**: Discover subdomains and DNS records.
*   **DHCP Monitor**: Monitor DHCP traffic for rogue servers.
*   **RDAP Auditor**: Query registration data for domains and IP ranges.

### Wireless Auditing
*   **Bluetooth Proximity Finder**: Locate and track BLE devices based on signal strength (RSSI).
*   **Wi-Fi Fingerprinting**: Analyze Wi-Fi environments, channel distribution, and signal quality.
*   **SDR Controller**: Real-time spectrum analysis and waterfall display via RTL-SDR (rtl_tcp).
*   **NFC Scanner**: Read and analyze NFC tag data and technology types (Now under Wireless Auditing).

### Physical & Hardware Tools
*   **Magnetometer**: Detect magnetic fields and hidden electronic devices.
*   **HID Injector**: Interface for planning or testing Human Interface Device (HID) payloads.

### Logging & Management
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
1.  Download the latest `RF_REAPR_v1.4.8.apk` from the root of this repository.
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
