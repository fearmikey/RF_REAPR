package com.fearmikey.rf_reapr.ui.navigation

sealed class Screen(val route: String) {
    data object Splash : Screen("splash")
    data object MainMenu : Screen("main_menu")
    data object PortScanner : Screen("port_scanner?ip={ip}") {
        fun createRoute(ip: String? = null) = if (ip != null) "port_scanner?ip=$ip" else "port_scanner"
    }
    data object TopologyMap : Screen("topology_map")
    data object BluetoothProximityFinder : Screen("bluetooth_proximity_finder")
    data object WifiFingerprinter : Screen("wifi_fingerprinter")
    data object NfcScanner : Screen("nfc_scanner")
    data object Settings : Screen("settings")
    data object DhcpMonitor : Screen("dhcp_monitor")
    data object WebsiteInspector : Screen("website_inspector")
    data object SubdomainFinder : Screen("subdomain_finder")
    data object TlsCipherScanner : Screen("tls_cipher_scanner")
    data object CloudAssetScanner : Screen("cloud_asset_scanner?domain={domain}") {
        fun createRoute(domain: String? = null) = if (domain != null) "cloud_asset_scanner?domain=$domain" else "cloud_asset_scanner"
    }
    data object HibpChecker : Screen("hibp_checker")
    data object PingTool : Screen("ping_tool")
    
    // New Physical Access & Wireless Tools
    data object HidInjector : Screen("hid_injector")
    data object HidAssets : Screen("hid_assets")
    data object EvidenceCapture : Screen("evidence_capture")
    data object EvidenceGallery : Screen("evidence_gallery")
    data object RecycleBin : Screen("recycle_bin")
    data object Magnetometer : Screen("magnetometer")

    // New Network Monitoring Tools
    data object ServiceDiscovery : Screen("service_discovery")
    data object DnsAuditor : Screen("dns_auditor")
    data object Traceroute : Screen("traceroute")
    data object ArpDetector : Screen("arp_detector")
    data object UpnpAuditor : Screen("upnp_auditor")
    data object SdrController : Screen("sdr_controller")

    data object ComplianceChecklists : Screen("compliance_checklists")
    data object AuditChecklist : Screen("audit_checklist/{frameworkId}") {
        fun createRoute(frameworkId: String) = "audit_checklist/$frameworkId"
    }
    data object ReportBuilder : Screen("report_builder")
    data object PermissionExplanation : Screen("permission_explanation")
    
    // Log Screens
    data object WifiLogs : Screen("logs/wifi")
    data object BleLogs : Screen("logs/ble")
    data object PortLogs : Screen("logs/port")
    data object WebLogs : Screen("logs/web")
    data object PingLogs : Screen("logs/ping")
    data object TopologyLogs : Screen("logs/topology")
}
