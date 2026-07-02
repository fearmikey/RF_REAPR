package com.example.rf_reapr.ui.navigation

sealed class Screen(val route: String) {
    data object Splash : Screen("splash")
    data object MainMenu : Screen("main_menu")
    data object PortScanner : Screen("port_scanner")
    data object TopologyMap : Screen("topology_map")
    data object BleAuditor : Screen("ble_auditor")
    data object WifiFingerprinter : Screen("wifi_fingerprinter")
    data object NfcScanner : Screen("nfc_scanner")
    data object HttpInspector : Screen("http_inspector")
    data object TlsAuditor : Screen("tls_auditor")
    data object Settings : Screen("settings")
    data object DnsEnumerator : Screen("dns_enumerator")
    data object DhcpMonitor : Screen("dhcp_monitor")
    data object RdapAuditor : Screen("rdap_auditor")
}
