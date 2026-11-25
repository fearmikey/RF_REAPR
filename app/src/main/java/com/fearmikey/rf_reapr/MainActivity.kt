package com.fearmikey.rf_reapr

import android.Manifest
import android.app.KeyguardManager
import android.content.Intent
import android.content.pm.PackageManager
import android.provider.MediaStore
import android.nfc.NfcAdapter
import android.nfc.Tag
import android.os.Build
import android.os.Bundle
import android.os.VibrationEffect
import android.os.Vibrator
import android.os.VibratorManager
import com.fearmikey.rf_reapr.ui.web.HashCalculatorScreen
import com.fearmikey.rf_reapr.ui.web.CertDecoderScreen
import com.fearmikey.rf_reapr.ui.web.ReverseShellCheatsheetScreen
import com.fearmikey.rf_reapr.ui.physical.UsbOtgAuditorScreen
import com.fearmikey.rf_reapr.ui.wireless.CellularReconScreen
import com.fearmikey.rf_reapr.ui.wireless.WpsPinCalculatorScreen
import com.fearmikey.rf_reapr.ui.network.SubnetCalculatorScreen
import com.fearmikey.rf_reapr.ui.network.MacLookupScreen
import com.fearmikey.rf_reapr.ui.network.MacLookupViewModel
import com.fearmikey.rf_reapr.ui.network.SubnetCalculatorViewModel
import com.fearmikey.rf_reapr.ui.network.DnsQueryViewModel
import com.fearmikey.rf_reapr.ui.network.WolViewModel
import com.fearmikey.rf_reapr.system.NetworkAlertNotificationManager
import com.fearmikey.rf_reapr.ui.network.DnsQueryScreen
import com.fearmikey.rf_reapr.ui.network.WolInjectorScreen
import com.fearmikey.rf_reapr.ui.network.CaptivePortalDetectorScreen
import com.fearmikey.rf_reapr.ui.network.CaptivePortalViewModel
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.ui.Modifier
import androidx.core.app.ActivityCompat
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import kotlinx.coroutines.launch
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import androidx.work.*
import com.fearmikey.rf_reapr.data.db.AppDatabase
import com.fearmikey.rf_reapr.data.local.SettingsRepositoryImpl
import com.fearmikey.rf_reapr.data.remote.VulnerabilityApiService
import com.fearmikey.rf_reapr.data.repository.*
import com.fearmikey.rf_reapr.data.worker.RecycleBinWorker
import com.fearmikey.rf_reapr.data.worker.VulnerabilityUpdateWorker
import com.fearmikey.rf_reapr.domain.model.ThemePreference
import com.fearmikey.rf_reapr.ui.dhcp.DhcpMonitorScreen
import com.fearmikey.rf_reapr.ui.dhcp.DhcpMonitorViewModel
import com.fearmikey.rf_reapr.ui.logs.LogListScreen
import com.fearmikey.rf_reapr.ui.logs.LogViewModel
import com.fearmikey.rf_reapr.ui.menu.MainMenuScreen
import com.fearmikey.rf_reapr.ui.navigation.Screen
import com.fearmikey.rf_reapr.ui.compliance.*
import com.fearmikey.rf_reapr.ui.nfc.NfcScannerScreen
import com.fearmikey.rf_reapr.ui.nfc.NfcScannerViewModel
import com.fearmikey.rf_reapr.ui.ping.PingScreen
import com.fearmikey.rf_reapr.ui.ping.PingViewModel
import com.fearmikey.rf_reapr.ui.web.*
import com.fearmikey.rf_reapr.ui.scanner.PortScannerScreen
import com.fearmikey.rf_reapr.ui.scanner.PortScannerViewModel
import com.fearmikey.rf_reapr.ui.settings.ApiKeysScreen
import com.fearmikey.rf_reapr.ui.settings.SettingsScreen
import com.fearmikey.rf_reapr.ui.settings.SettingsViewModel
import com.fearmikey.rf_reapr.ui.splash.SplashScreen
import com.fearmikey.rf_reapr.ui.topology.TopologyScreen
import com.fearmikey.rf_reapr.ui.topology.TopologyViewModel
import com.fearmikey.rf_reapr.ui.wifi.WifiFingerprintScreen
import com.fearmikey.rf_reapr.ui.wifi.WifiFingerprintViewModel
import com.fearmikey.rf_reapr.ui.permissions.PermissionExplanationScreen
import com.fearmikey.rf_reapr.ui.physical.HidAssetsScreen
import com.fearmikey.rf_reapr.ui.physical.HidAssetsViewModel
import com.fearmikey.rf_reapr.ui.physical.HidInjectorScreen
import com.fearmikey.rf_reapr.ui.physical.HidInjectorViewModel
import com.fearmikey.rf_reapr.ui.physical.MagnetometerScreen
import com.fearmikey.rf_reapr.ui.physical.MagnetometerViewModel
import com.fearmikey.rf_reapr.ui.wireless.BluetoothProximityFinderScreen
import com.fearmikey.rf_reapr.ui.wireless.BluetoothProximityFinderViewModel
import com.fearmikey.rf_reapr.data.repository.BleProximityRepositoryImpl
import com.fearmikey.rf_reapr.ui.dns.*
import com.fearmikey.rf_reapr.ui.mdns.*
import com.fearmikey.rf_reapr.ui.traceroute.*
import com.fearmikey.rf_reapr.ui.arp.*
import com.fearmikey.rf_reapr.ui.snmp.*
import com.fearmikey.rf_reapr.ui.network.PacketCaptureScreen
import com.fearmikey.rf_reapr.ui.logs.PcapLogListScreen
import com.fearmikey.rf_reapr.ui.report.*
import com.fearmikey.rf_reapr.ui.sdr.SdrScreen
import com.fearmikey.rf_reapr.ui.sdr.SdrViewModel
import com.fearmikey.rf_reapr.ui.iperf.IperfScreen
import com.fearmikey.rf_reapr.ui.iperf.IperfViewModel
import com.fearmikey.rf_reapr.ui.theme.RF_REAPRTheme
import com.fearmikey.rf_reapr.ui.audit.AuditHistoryScreen
import com.fearmikey.rf_reapr.ui.audit.AuditHistoryViewModel
import com.fearmikey.rf_reapr.ui.audit.AuditModeScreen
import com.fearmikey.rf_reapr.ui.dhcp.KnownDevicesScreen
import com.fearmikey.rf_reapr.ui.dhcp.KnownDevicesViewModel
import com.fearmikey.rf_reapr.ui.engagement.TimelineScreen
import com.fearmikey.rf_reapr.ui.engagement.TimelineViewModel
import com.fearmikey.rf_reapr.ui.survey.GeoSurveyScreen
import com.fearmikey.rf_reapr.ui.survey.GeoSurveyViewModel
import com.fearmikey.rf_reapr.ui.survey.SiteSurveyScreen
import com.fearmikey.rf_reapr.ui.survey.SiteSurveyViewModel
import com.fearmikey.rf_reapr.ui.wireless.GattInspectorScreen
import com.fearmikey.rf_reapr.ui.wireless.GattInspectorViewModel
import com.fearmikey.rf_reapr.ui.audit.AuditModeViewModel
import com.fearmikey.rf_reapr.ui.backup.BackupScreen
import com.fearmikey.rf_reapr.ui.backup.BackupViewModel
import com.fearmikey.rf_reapr.ui.engagement.EngagementScreen
import com.fearmikey.rf_reapr.ui.engagement.EngagementViewModel
import com.fearmikey.rf_reapr.ui.findings.FindingsScreen
import com.fearmikey.rf_reapr.ui.findings.FindingsViewModel
import androidx.navigation.NavBackStackEntry
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.navigation.NavController
import androidx.compose.runtime.DisposableEffect
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ManageSearch
import androidx.compose.material.icons.filled.*
import java.util.concurrent.TimeUnit
import com.fearmikey.rf_reapr.ui.theme.RF_REAPRTheme

class MainActivity : ComponentActivity() {
    private var nfcAdapter: NfcAdapter? = null
    private lateinit var nfcRepository: NfcScannerRepositoryImpl

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // Background execution + live status notifications for active scans/workflows are
        // now handled by RfReaprApplication + ScanForegroundService, independent of this
        // Activity's lifecycle.
        handleCameraShortcuts(intent)

        // Dependencies come from the process-wide AppContainer. Local delegates keep the
        // names used throughout the NavHost below and stay lazy (resolved on first use).
        val container = (application as RfReaprApplication).container
        val settingsRepository by container::settingsRepository
        lifecycleScope.launch {
            settingsRepository.incrementAppLaunchCount()
        }
        val scanSessionRepository by container::scanSessionRepository
        val portRepository by container::portRepository
        val vulnerabilityRepository by container::vulnerabilityRepository
        val discoveryRepository by container::discoveryRepository
        val wifiRepository by container::wifiRepository
        val bleRepository by container::bleRepository
        nfcRepository = container.nfcRepository
        val dhcpRepository by container::dhcpRepository
        val ouiRepository by container::ouiRepository
        val dnsQueryRepository by container::dnsQueryRepository
        val wolRepository by container::wolRepository
        val networkAlertNotifier by container::networkAlertNotifier
        val websiteInspectorRepository by container::websiteInspectorRepository
        val subdomainFinderRepository by container::subdomainFinderRepository
        val tlsCipherScannerRepository by container::tlsCipherScannerRepository
        val cloudAssetScannerRepository by container::cloudAssetScannerRepository
        val hibpRepository by container::hibpRepository
        val usernameOsintRepository by container::usernameOsintRepository
        val threatIntelRepository by container::threatIntelRepository
        val pingRepository by container::pingRepository
        val logRepository by container::logRepository
        val complianceRepository by container::complianceRepository
        val proximityRepository by container::proximityRepository
        val evidenceRepository by container::evidenceRepository
        val magnetometerRepository by container::magnetometerRepository
        val reportRepository by container::reportRepository
        val sdrRepository by container::sdrRepository
        val snmpRepository by container::snmpRepository
        val serviceDiscoveryRepository by container::serviceDiscoveryRepository
        val dnsAuditorRepository by container::dnsAuditorRepository
        val tracerouteRepository by container::tracerouteRepository
        val arpDetectorRepository by container::arpDetectorRepository
        val upnpScannerRepository by container::upnpScannerRepository
        val shodanRepository by container::shodanRepository
        val internetDbRepository by container::internetDbRepository
        val hidRepository by container::hidRepository
        val hidAssetRepository by container::hidAssetRepository
        val usbDriveRepository by container::usbDriveRepository
        val exifRepository by container::exifRepository
        val findingRepository by container::findingRepository
        val engagementRepository by container::engagementRepository
        val backupManager by container::backupManager
        val captivePortalRepository by container::captivePortalRepository
        val auditEngine by container::auditEngine
        val auditRunRepository by container::auditRunRepository
        val knownDeviceRepository by container::knownDeviceRepository
        val surveyRepository by container::surveyRepository
        val siteSurveyRepository by container::siteSurveyRepository
        val gattInspectorRepository by container::gattInspectorRepository
        val nmapImportManager by container::nmapImportManager

        nfcAdapter = NfcAdapter.getDefaultAdapter(this)
        lifecycleScope.launch {
            lifecycle.repeatOnLifecycle(androidx.lifecycle.Lifecycle.State.RESUMED) {
                nfcRepository.readerRequested.collect { updateNfcReaderMode(it) }
            }
        }

        // Schedule Workers
        scheduleRecycleBinCleanup()
        scheduleVulnerabilityUpdates()

        setContent {
            val settingsViewModel: SettingsViewModel = viewModel {
                SettingsViewModel(settingsRepository)
            }
            val themePreference by settingsViewModel.themePreference.collectAsState()
            
            val darkTheme = when (themePreference) {
                ThemePreference.LIGHT -> false
                ThemePreference.DARK -> true
                ThemePreference.SYSTEM -> isSystemInDarkTheme()
            }

            RF_REAPRTheme(darkTheme = darkTheme) {
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = MaterialTheme.colorScheme.background,
                ) {
                    val navController = rememberNavController()

                    DisposableEffect(navController) {
                        val listener = NavController.OnDestinationChangedListener { _, destination, _ ->
                            val route = destination.route
                            val isCamera = route == "camera_capture"
                            val isSplash = route == Screen.Splash.route
                            
                            if (isCamera) {
                                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O_MR1) {
                                    setShowWhenLocked(true)
                                } else {
                                    @Suppress("DEPRECATION")
                                    window.addFlags(android.view.WindowManager.LayoutParams.FLAG_SHOW_WHEN_LOCKED)
                                }
                            } else if (!isSplash) {
                                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O_MR1) {
                                    setShowWhenLocked(false)
                                } else {
                                    @Suppress("DEPRECATION")
                                    window.clearFlags(android.view.WindowManager.LayoutParams.FLAG_SHOW_WHEN_LOCKED)
                                }
                            }
                        }
                        navController.addOnDestinationChangedListener(listener)
                        onDispose {
                            navController.removeOnDestinationChangedListener(listener)
                        }
                    }
                    
                    // Navigate to camera if launched via shortcut
                    LaunchedEffect(intent) {
                        if (isCameraIntent(intent)) {
                            navController.navigate("camera_capture") {
                                popUpTo(Screen.Splash.route) { inclusive = true }
                            }
                        }
                    }

                    val versionName = getAppVersion()
                    val logViewModel: LogViewModel = viewModel {
                        LogViewModel(logRepository)
                    }
                    val complianceViewModel: ComplianceViewModel = viewModel {
                        ComplianceViewModel(complianceRepository, findingRepository)
                    }
                    val evidenceViewModel: EvidenceCaptureViewModel = viewModel {
                        EvidenceCaptureViewModel(evidenceRepository, logRepository)
                    }
                    
                    NavHost(
                        navController = navController,
                        startDestination = Screen.Splash.route,
                    ) {
                        composable(Screen.Splash.route) {
                            SplashScreen(versionName = versionName) {
            val hasBluetooth = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                (ActivityCompat.checkSelfPermission(this@MainActivity, Manifest.permission.BLUETOOTH_SCAN) == PackageManager.PERMISSION_GRANTED) &&
                (ActivityCompat.checkSelfPermission(this@MainActivity, Manifest.permission.BLUETOOTH_CONNECT) == PackageManager.PERMISSION_GRANTED)
            } else {
                (ActivityCompat.checkSelfPermission(this@MainActivity, Manifest.permission.BLUETOOTH) == PackageManager.PERMISSION_GRANTED) &&
                (ActivityCompat.checkSelfPermission(this@MainActivity, Manifest.permission.BLUETOOTH_ADMIN) == PackageManager.PERMISSION_GRANTED)
            }
            val hasLocation = (ActivityCompat.checkSelfPermission(this@MainActivity, Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED) &&
                              (ActivityCompat.checkSelfPermission(this@MainActivity, Manifest.permission.ACCESS_COARSE_LOCATION) == PackageManager.PERMISSION_GRANTED)
            val hasCamera = ActivityCompat.checkSelfPermission(this@MainActivity, Manifest.permission.CAMERA) == PackageManager.PERMISSION_GRANTED
                                val hasNotifications = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                                    ActivityCompat.checkSelfPermission(this@MainActivity, Manifest.permission.POST_NOTIFICATIONS) == PackageManager.PERMISSION_GRANTED
                                } else {
                                    true
                                }
                                
                                if (hasBluetooth && hasLocation && hasCamera && hasNotifications) {
                                    navController.navigate(Screen.MainMenu.route) {
                                        popUpTo(Screen.Splash.route) { inclusive = true }
                                    }
                                } else {
                                    navController.navigate(Screen.PermissionExplanation.route) {
                                        popUpTo(Screen.Splash.route) { inclusive = true }
                                    }
                                }
                            }
                        }
                        composable(Screen.PermissionExplanation.route) {
                            PermissionExplanationScreen {
                                navController.navigate(Screen.MainMenu.route) {
                                    popUpTo(Screen.PermissionExplanation.route) { inclusive = true }
                                }
                            }
                        }
                        composable(Screen.MainMenu.route) {
                            MainMenuScreen(
                                settingsViewModel = settingsViewModel,
                                onNavigate = { route ->
                                    navController.navigate(route)
                                }
                            )
                        }
                        composable(Screen.PortScanner.route) { backStackEntry ->
                            val portScannerViewModel: PortScannerViewModel = viewModel {
                                                    PortScannerViewModel(portRepository, vulnerabilityRepository, scanSessionRepository, logRepository, settingsRepository, findingRepository)
                                                }
                            val ip = backStackEntry.arguments?.getString("ip")
                            PortScannerScreen(
                                viewModel = portScannerViewModel,
                                initialIp = ip,
                                onBack = { navController.popBackStack() },
                                onNavigateToSettings = { navController.navigate(Screen.Settings.route) }
                            ) { _, _ -> }
                        }
                        composable(Screen.TopologyMap.route) {
                            val topologyViewModel: TopologyViewModel = viewModel {
                                                    TopologyViewModel(discoveryRepository, scanSessionRepository, portRepository, internetDbRepository, vulnerabilityRepository, snmpRepository, logRepository, settingsRepository)
                                                }
                            TopologyScreen(
                                viewModel = topologyViewModel,
                                onBack = { navController.popBackStack() },
                                onNavigateToSettings = { navController.navigate(Screen.Settings.route) },
                                onNavigateToPortScanner = { ip -> navController.navigate(Screen.PortScanner.createRoute(ip)) },
                                onNavigateToThreatIntel = { ip -> navController.navigate(Screen.ThreatIntel.createRoute(ip)) },
                                onNavigate = { route -> navController.navigate(route) }
                            )
                        }
                        composable(Screen.WifiFingerprinter.route) {
                            val wifiViewModel: WifiFingerprintViewModel = viewModel {
                                                    WifiFingerprintViewModel(wifiRepository, logRepository)
                                                }
                            WifiFingerprintScreen(
                                viewModel = wifiViewModel,
                            ) { navController.popBackStack() }
                        }
                        composable(Screen.BluetoothProximityFinder.route) {
                            val proximityViewModel: BluetoothProximityFinderViewModel = viewModel {
                                                    BluetoothProximityFinderViewModel(proximityRepository, bleRepository, logRepository, networkAlertNotifier)
                                                }
                            BluetoothProximityFinderScreen(
                                viewModel = proximityViewModel,
                                onInspectGatt = { address, name -> navController.navigate(Screen.GattInspector.createRoute(address, name)) },
                            ) { navController.popBackStack() }
                        }
                        composable(Screen.NfcScanner.route) {
                            val nfcViewModel: NfcScannerViewModel = viewModel {
                                                    NfcScannerViewModel(nfcRepository, logRepository, findingRepository)
                                                }
                            NfcScannerScreen(
                                viewModel = nfcViewModel,
                            ) { navController.popBackStack() }
                        }
                        composable(Screen.Settings.route) {
                            SettingsScreen(
                                viewModel = settingsViewModel,
                                onNavigateToApiKeys = { navController.navigate(Screen.ApiKeys.route) },
                                onBack = { navController.popBackStack() }
                            )
                        }
                        composable(Screen.ApiKeys.route) { backStackEntry ->
                            val highlight = backStackEntry.arguments?.getString("highlight")
                            ApiKeysScreen(
                                viewModel = settingsViewModel,
                                highlightKey = highlight,
                                onBack = { navController.popBackStack() }
                            )
                        }
                        composable(Screen.DhcpMonitor.route) {
                            val dhcpViewModel: DhcpMonitorViewModel = viewModel {
                                                    DhcpMonitorViewModel(dhcpRepository, settingsRepository, networkAlertNotifier, knownDeviceRepository, findingRepository)
                                                }
                            DhcpMonitorScreen(
                                viewModel = dhcpViewModel,
                                onBack = { navController.popBackStack() },
                                onOpenKnownDevices = { navController.navigate(Screen.KnownDevices.route) },
                                onPortScan = { ip ->
                                    navController.navigate(Screen.PortScanner.createRoute(ip))
                                }
                            )
                        }
                        composable(Screen.WebsiteInspector.route) { backStackEntry ->
                            val websiteInspectorViewModel: WebsiteInspectorViewModel = viewModel {
                                                    WebsiteInspectorViewModel(websiteInspectorRepository, logRepository, settingsRepository, findingRepository)
                                                }
                            WebsiteInspectorScreen(
                                viewModel = websiteInspectorViewModel,
                                initialTarget = backStackEntry.targetArg(),
                            ) { navController.popBackStack() }
                        }
                        composable(Screen.SubdomainFinder.route) { backStackEntry ->
                            val subdomainFinderViewModel: SubdomainFinderViewModel = viewModel {
                                                    SubdomainFinderViewModel(subdomainFinderRepository, logRepository, settingsRepository, findingRepository)
                                                }
                            SubdomainFinderScreen(
                                viewModel = subdomainFinderViewModel,
                                onBack = { navController.popBackStack() },
                                onNavigateToCloudScanner = { domain ->
                                    navController.navigate(Screen.CloudAssetScanner.createRoute(domain))
                                },
                                initialTarget = backStackEntry.targetArg()
                            )
                        }
                        composable(Screen.TlsCipherScanner.route) { backStackEntry ->
                            val tlsCipherScannerViewModel: TlsCipherScannerViewModel = viewModel {
                                                    TlsCipherScannerViewModel(tlsCipherScannerRepository, logRepository, settingsRepository, findingRepository)
                                                }
                            TlsCipherScannerScreen(
                                viewModel = tlsCipherScannerViewModel,
                                initialTarget = backStackEntry.targetArg(),
                            ) { navController.popBackStack() }
                        }
                        composable(Screen.CloudAssetScanner.route) { backStackEntry ->
                            val cloudAssetScannerViewModel: CloudAssetScannerViewModel = viewModel {
                                                    CloudAssetScannerViewModel(cloudAssetScannerRepository, logRepository, settingsRepository, findingRepository)
                                                }
                            val domain = backStackEntry.arguments?.getString("domain")
                            CloudAssetScannerScreen(
                                viewModel = cloudAssetScannerViewModel,
                                initialDomain = domain,
                                onBack = { navController.popBackStack() }
                            )
                        }
                        composable(Screen.HibpChecker.route) {
                            val hibpViewModel: HibpViewModel = viewModel {
                                                    HibpViewModel(hibpRepository, settingsRepository, logRepository, findingRepository)
                                                }
                            HibpScreen(
                                viewModel = hibpViewModel,
                            ) { navController.popBackStack() }
                        }
                        composable(Screen.ShodanScanner.route) {
                            val shodanViewModel: ShodanViewModel = viewModel {
                                                    ShodanViewModel(shodanRepository, internetDbRepository, settingsRepository, logRepository, findingRepository)
                                                }
                            ShodanScreen(
                                viewModel = shodanViewModel,
                                onNavigateToApiKeys = { highlight -> 
                                    navController.navigate(Screen.ApiKeys.createRoute(highlight))
                                },
                                onBack = { navController.popBackStack() }
                            )
                        }
                        composable(Screen.GoogleDorks.route) {
                            val dorksViewModel: GoogleDorksViewModel = viewModel {
                                GoogleDorksViewModel()
                            }
                            GoogleDorksScreen(
                                viewModel = dorksViewModel,
                                onBack = { navController.popBackStack() }
                            )
                        }
                        composable(Screen.UsernameOsint.route) {
                            val usernameViewModel: UsernameOsintViewModel = viewModel {
                                UsernameOsintViewModel(usernameOsintRepository, logRepository)
                            }
                            UsernameOsintScreen(
                                viewModel = usernameViewModel,
                                onBack = { navController.popBackStack() }
                            )
                        }
                        composable(Screen.ThreatIntel.route) { backStackEntry ->
                            val threatIntelViewModel: ThreatIntelViewModel = viewModel {
                                ThreatIntelViewModel(threatIntelRepository, settingsRepository, logRepository, findingRepository)
                            }
                            // Prefill the IP when opened from another tool (e.g. the network map).
                            // The lookup itself still requires an explicit tap (online service).
                            val initialIp = backStackEntry.arguments?.getString("ip")
                            LaunchedEffect(initialIp) {
                                if (!initialIp.isNullOrBlank() && threatIntelViewModel.ipAddress.value.isBlank()) {
                                    threatIntelViewModel.onIpAddressChanged(initialIp)
                                }
                            }
                            ThreatIntelScreen(
                                viewModel = threatIntelViewModel,
                                onNavigateToApiKeys = {
                                    navController.navigate(Screen.ApiKeys.createRoute("abuseipdb"))
                                },
                                onBack = { navController.popBackStack() }
                            )
                        }
                        composable(Screen.PingTool.route) { backStackEntry ->
                            val pingViewModel: PingViewModel = viewModel {
                                PingViewModel(pingRepository, logRepository, settingsRepository, discoveryRepository)
                            }
                            PingScreen(
                                viewModel = pingViewModel,
                                initialHost = backStackEntry.arguments?.getString("host"),
                            ) { navController.popBackStack() }
                        }
                        composable(Screen.HidInjector.route) {
                            val hidInjectorViewModel: HidInjectorViewModel = viewModel {
                                                    HidInjectorViewModel(hidRepository, hidAssetRepository, settingsRepository, usbDriveRepository)
                                                }
                            HidInjectorScreen(
                                viewModel = hidInjectorViewModel,
                                onBack = { navController.popBackStack() },
                                onNavigateToAssets = { navController.navigate(Screen.HidAssets.route) }
                            )
                        }
                        composable(Screen.HidAssets.route) {
                            val hidAssetsViewModel: HidAssetsViewModel = viewModel {
                                                    HidAssetsViewModel(hidAssetRepository)
                                                }
                            HidAssetsScreen(
                                viewModel = hidAssetsViewModel,
                                onBack = { navController.popBackStack() }
                            )
                        }
                        
                        // EVIDENCE FLOW
                        composable(Screen.EvidenceCapture.route) {
                            EvidenceProjectSelectionScreen(
                                viewModel = evidenceViewModel,
                                onProjectSelected = {
                                    navController.navigate(Screen.EvidenceGallery.route)
                                },
                                onNavigateToRecycleBin = {
                                    navController.navigate(Screen.RecycleBin.route)
                                },
                            ) { navController.popBackStack() }
                        }
                        
                        composable(Screen.EvidenceGallery.route) {
                            EvidenceGalleryScreen(
                                viewModel = evidenceViewModel,
                                onBack = { navController.popBackStack() },
                            ) { 
                                navController.navigate("camera_capture") 
                            }
                        }
                        
                        composable("camera_capture") {
                            EvidenceCaptureScreen(
                                viewModel = evidenceViewModel,
                            ) { 
                                if (navController.popBackStack()) {
                                    // Standard back behavior
                                } else {
                                    // Shortcut launch behavior - promote to full app
                                    val keyguardManager = getSystemService(KEYGUARD_SERVICE) as KeyguardManager
                                    val navigateToGallery = {
                                        navController.navigate(Screen.MainMenu.route) {
                                            popUpTo(0) { inclusive = true }
                                        }
                                        navController.navigate(Screen.EvidenceCapture.route)
                                        navController.navigate(Screen.EvidenceGallery.route)
                                    }

                                    if (keyguardManager.isKeyguardLocked) {
                                        keyguardManager.requestDismissKeyguard(this@MainActivity, object : KeyguardManager.KeyguardDismissCallback() {
                                            override fun onDismissSucceeded() {
                                                navigateToGallery()
                                            }
                                            override fun onDismissCancelled() {
                                                finish()
                                            }
                                        })
                                    } else {
                                        navigateToGallery()
                                    }
                                }
                            }
                        }

                        composable(Screen.RecycleBin.route) {
                            val recycleBinViewModel: RecycleBinViewModel = viewModel {
                                                    RecycleBinViewModel(evidenceRepository)
                                                }
                            RecycleBinScreen(
                                viewModel = recycleBinViewModel,
                            ) { navController.popBackStack() }
                        }

                        composable(Screen.Magnetometer.route) {
                            val magnetometerViewModel: MagnetometerViewModel = viewModel {
                                                    MagnetometerViewModel(magnetometerRepository)
                                                }
                            MagnetometerScreen(
                                viewModel = magnetometerViewModel,
                                onBack = { navController.popBackStack() }
                            )
                        }

                        // New Network Tools
                        composable(Screen.ServiceDiscovery.route) {
                            val serviceDiscoveryViewModel: ServiceDiscoveryViewModel = viewModel {
                                                    ServiceDiscoveryViewModel(serviceDiscoveryRepository, logRepository, settingsRepository)
                                                }
                            ServiceDiscoveryScreen(
                                viewModel = serviceDiscoveryViewModel,
                                onPortScan = { ip -> navController.navigate(Screen.PortScanner.createRoute(ip)) }
                            ) { navController.popBackStack() }
                        }
                        composable(Screen.DnsAuditor.route) {
                            val dnsAuditorViewModel: DnsAuditorViewModel = viewModel {
                                                    DnsAuditorViewModel(dnsAuditorRepository, logRepository, settingsRepository, findingRepository)
                                                }
                            DnsAuditorScreen(
                                viewModel = dnsAuditorViewModel,
                            ) { navController.popBackStack() }
                        }
                        composable(Screen.Traceroute.route) { backStackEntry ->
                            val tracerouteViewModel: TracerouteViewModel = viewModel {
                                                    TracerouteViewModel(tracerouteRepository, logRepository, settingsRepository)
                                                }
                            TracerouteScreen(
                                viewModel = tracerouteViewModel,
                                initialTarget = backStackEntry.targetArg(),
                                onPingHost = { ip -> navController.navigate(Screen.PingTool.createRoute(ip)) }
                            ) { navController.popBackStack() }
                        }
                        composable(Screen.ArpDetector.route) {
                            val arpDetectorViewModel: ArpDetectorViewModel = viewModel {
                                                    ArpDetectorViewModel(arpDetectorRepository, logRepository, findingRepository)
                                                }
                            ArpDetectorScreen(
                                viewModel = arpDetectorViewModel,
                            ) { navController.popBackStack() }
                        }
                        composable(Screen.UpnpAuditor.route) {
                            val upnpScannerViewModel: UpnpScannerViewModel = viewModel {
                                                    UpnpScannerViewModel(upnpScannerRepository, settingsRepository, logRepository, findingRepository)
                                                }
                            UpnpAuditorScreen(
                                viewModel = upnpScannerViewModel,
                            ) { navController.popBackStack() }
                        }
                        
                        composable(Screen.SdrController.route) {
                            val sdrViewModel: SdrViewModel = viewModel {
                                                    SdrViewModel(sdrRepository)
                                                }
                            SdrScreen(
                                viewModel = sdrViewModel,
                                onNavigateBack = { navController.popBackStack() }
                            )
                        }
                        
                        composable(Screen.IperfTester.route) {
                            val iperfViewModel: IperfViewModel = viewModel {
                                                    IperfViewModel(logRepository)
                                                }
                            IperfScreen(
                                viewModel = iperfViewModel,
                                onBack = { navController.popBackStack() }
                            )
                        }
                        composable(Screen.PacketCapture.route) {
                            PacketCaptureScreen(
                                onBack = { navController.popBackStack() },
                                onOpenCaptures = { navController.navigate(Screen.PacketCaptureLogs.route) }
                            )
                        }
                        composable(Screen.SnmpBrowser.route) { backStackEntry ->
                            val snmpViewModel: SnmpViewModel = viewModel {
                                                    SnmpViewModel(snmpRepository, logRepository, findingRepository)
                                                }
                            SnmpBrowserScreen(
                                viewModel = snmpViewModel,
                                initialTarget = backStackEntry.targetArg(),
                                onBack = { navController.popBackStack() }
                            )
                        }

                        // --- NEW TOOLS ---
                        composable(Screen.SubnetCalculator.route) {
                            val subnetViewModel: SubnetCalculatorViewModel = viewModel { SubnetCalculatorViewModel(discoveryRepository) }
                            SubnetCalculatorScreen(viewModel = subnetViewModel) { navController.popBackStack() }
                        }
                        composable(Screen.MacLookup.route) {
                            val macLookupViewModel: MacLookupViewModel = viewModel { MacLookupViewModel(ouiRepository) }
                            MacLookupScreen(viewModel = macLookupViewModel) { navController.popBackStack() }
                        }
                        composable(Screen.DnsQuery.route) {
                            val dnsQueryViewModel: DnsQueryViewModel = viewModel { DnsQueryViewModel(dnsQueryRepository) }
                            DnsQueryScreen(viewModel = dnsQueryViewModel) { navController.popBackStack() }
                        }
                        composable(Screen.WolInjector.route) {
                            val wolViewModel: WolViewModel = viewModel {
                                WolViewModel(wolRepository, discoveryRepository, scanSessionRepository)
                            }
                            WolInjectorScreen(viewModel = wolViewModel) { navController.popBackStack() }
                        }
                        composable(Screen.CaptivePortalDetector.route) {
                            val captiveViewModel: CaptivePortalViewModel = viewModel {
                                CaptivePortalViewModel(captivePortalRepository, logRepository, findingRepository)
                            }
                            CaptivePortalDetectorScreen(viewModel = captiveViewModel) { navController.popBackStack() }
                        }
                        composable(Screen.HashCalculator.route) {
                            HashCalculatorScreen { navController.popBackStack() }
                        }
                        composable(Screen.CertDecoder.route) {
                            CertDecoderScreen { navController.popBackStack() }
                        }
                        composable(Screen.ReverseShellCheatsheet.route) {
                            ReverseShellCheatsheetScreen { navController.popBackStack() }
                        }
                        composable(Screen.ExifInspector.route) {
                            val exifViewModel: ExifInspectorViewModel = viewModel {
                                ExifInspectorViewModel(exifRepository)
                            }
                            ExifInspectorScreen(
                                viewModel = exifViewModel,
                                onBack = { navController.popBackStack() }
                            )
                        }
                        composable(Screen.PayloadEncoder.route) {
                            val payloadViewModel: PayloadEncoderViewModel = viewModel {
                                PayloadEncoderViewModel()
                            }
                            PayloadEncoderScreen(
                                viewModel = payloadViewModel,
                                onBack = { navController.popBackStack() }
                            )
                        }
                        composable(Screen.PhoneOsint.route) {
                            val phoneViewModel: PhoneOsintViewModel = viewModel {
                                PhoneOsintViewModel()
                            }
                            PhoneOsintScreen(
                                viewModel = phoneViewModel,
                                onBack = { navController.popBackStack() }
                            )
                        }
                        composable(Screen.UsbOtgAuditor.route) {
                            UsbOtgAuditorScreen(
                                onFindings = { drafts -> container.applicationScope.launch { findingRepository.record(drafts) } }
                            ) { navController.popBackStack() }
                        }
                        composable(Screen.CellularRecon.route) {
                            CellularReconScreen(
                                onFindings = { drafts -> container.applicationScope.launch { findingRepository.record(drafts) } }
                            ) { navController.popBackStack() }
                        }

                        // Engagement, findings and backup
                        composable(Screen.Engagement.route) {
                            val engagementViewModel: EngagementViewModel = viewModel {
                                EngagementViewModel(engagementRepository, BuildConfig.VERSION_NAME, nmapImportManager)
                            }
                            EngagementScreen(
                                viewModel = engagementViewModel,
                                onOpenFindings = { navController.navigate(Screen.Findings.route) },
                                onOpenBackup = { navController.navigate(Screen.Backup.route) },
                                onNavigate = { route -> navController.navigate(route) },
                                onBack = { navController.popBackStack() }
                            )
                        }
                        composable(Screen.Findings.route) {
                            val findingsViewModel: FindingsViewModel = viewModel {
                                FindingsViewModel(findingRepository, engagementRepository, BuildConfig.VERSION_NAME, evidenceRepository)
                            }
                            FindingsScreen(viewModel = findingsViewModel) { navController.popBackStack() }
                        }
                        composable(Screen.AuditMode.route) {
                            val auditModeViewModel: AuditModeViewModel = viewModel {
                                AuditModeViewModel(auditEngine, settingsRepository, BuildConfig.VERSION_NAME, auditRunRepository)
                            }
                            AuditModeScreen(
                                viewModel = auditModeViewModel,
                                onOpenFindings = { navController.navigate(Screen.Findings.route) },
                                onOpenEngagement = { navController.navigate(Screen.Engagement.route) },
                                onOpenHistory = { navController.navigate(Screen.AuditHistory.route) },
                                onBack = { navController.popBackStack() }
                            )
                        }
                        composable(Screen.AuditHistory.route) {
                            val auditHistoryViewModel: AuditHistoryViewModel = viewModel { AuditHistoryViewModel(auditRunRepository) }
                            AuditHistoryScreen(
                                viewModel = auditHistoryViewModel,
                                onOpenLogs = { navController.navigate(Screen.AuditLogs.route) },
                                onBack = { navController.popBackStack() }
                            )
                        }
                        composable(Screen.Timeline.route) {
                            val timelineViewModel: TimelineViewModel = viewModel { TimelineViewModel(engagementRepository) }
                            TimelineScreen(viewModel = timelineViewModel) { navController.popBackStack() }
                        }
                        composable(Screen.KnownDevices.route) {
                            val knownDevicesViewModel: KnownDevicesViewModel = viewModel { KnownDevicesViewModel(knownDeviceRepository) }
                            KnownDevicesScreen(viewModel = knownDevicesViewModel) { navController.popBackStack() }
                        }
                        composable(Screen.GeoSurvey.route) {
                            val geoSurveyViewModel: GeoSurveyViewModel = viewModel { GeoSurveyViewModel(surveyRepository) }
                            GeoSurveyScreen(viewModel = geoSurveyViewModel) { navController.popBackStack() }
                        }
                        composable(Screen.SiteSurvey.route) {
                            val siteSurveyViewModel: SiteSurveyViewModel = viewModel { SiteSurveyViewModel(siteSurveyRepository) }
                            SiteSurveyScreen(viewModel = siteSurveyViewModel) { navController.popBackStack() }
                        }
                        composable(Screen.GattInspector.route) { backStackEntry ->
                            val gattViewModel: GattInspectorViewModel = viewModel {
                                GattInspectorViewModel(gattInspectorRepository, findingRepository, logRepository)
                            }
                            GattInspectorScreen(
                                viewModel = gattViewModel,
                                address = backStackEntry.arguments?.getString("address").orEmpty(),
                                deviceName = backStackEntry.arguments?.getString("name")?.takeUnless { it.isBlank() || it == "{name}" },
                            ) { navController.popBackStack() }
                        }
                        composable(Screen.Backup.route) {
                            val backupViewModel: BackupViewModel = viewModel {
                                BackupViewModel(backupManager, BuildConfig.VERSION_NAME)
                            }
                            BackupScreen(viewModel = backupViewModel) { navController.popBackStack() }
                        }
                        composable(Screen.WpsPinCalculator.route) {
                            WpsPinCalculatorScreen { navController.popBackStack() }
                        }
                        // ------------------------------

                        
                        // Log Routes
                        composable(Screen.WifiLogs.route) {
                            LogListScreen("WIFI", "WiFi Scanning Logs", logViewModel) { navController.popBackStack() }
                        }
                        composable(Screen.BleLogs.route) {
                            LogListScreen("BLE", "Bluetooth Scanning Logs", logViewModel) { navController.popBackStack() }
                        }
                        composable(Screen.PortLogs.route) {
                            LogListScreen("PORT", "Port Scanning Logs", logViewModel) { navController.popBackStack() }
                        }
                        composable(Screen.WebLogs.route) {
                            LogListScreen("WEB", "Website Inspector Logs", logViewModel) { navController.popBackStack() }
                        }
                        composable(Screen.PingLogs.route) {
                            LogListScreen("PING", "Ping Report Logs", logViewModel) { navController.popBackStack() }
                        }
                        composable(Screen.IperfLogs.route) {
                            LogListScreen("IPERF", "iPerf Tester Logs", logViewModel) { navController.popBackStack() }
                        }
                        composable(Screen.TopologyLogs.route) {
                            LogListScreen("TOPOLOGY", "Network Discovery Logs", logViewModel) { navController.popBackStack() }
                        }
                        composable(Screen.SnmpLogs.route) {
                            LogListScreen("SNMP", "SNMP Browser Logs", logViewModel) { navController.popBackStack() }
                        }
                        composable(Screen.AuditLogs.route) {
                            LogListScreen("AUDIT", "Audit Mode Logs", logViewModel) { navController.popBackStack() }
                        }
                        composable(Screen.PacketCaptureLogs.route) {
                            PcapLogListScreen(
                                onBack = { navController.popBackStack() }
                            )
                        }
                        
                        // Compliance Routes
                        composable(Screen.ComplianceChecklists.route) {
                            ComplianceChecklistsScreen(
                                viewModel = complianceViewModel,
                                onFrameworkClick = { frameworkId ->
                                    navController.navigate(Screen.AuditChecklist.createRoute(frameworkId))
                                },
                            ) { navController.popBackStack() }
                        }
                        composable(Screen.AuditChecklist.route) { backStackEntry ->
                            val frameworkId = backStackEntry.arguments?.getString("frameworkId") ?: ""
                            AuditChecklistScreen(
                                frameworkId = frameworkId,
                                viewModel = complianceViewModel,
                            ) { navController.popBackStack() }
                        }
                        composable(
                            route = Screen.ReportBuilder.route,
                            arguments = listOf(
                                androidx.navigation.navArgument("autoGenerate") {
                                    type = androidx.navigation.NavType.BoolType
                                    defaultValue = false
                                },
                                androidx.navigation.navArgument("startTime") {
                                    type = androidx.navigation.NavType.StringType
                                    nullable = true
                                    defaultValue = null
                                }
                            )
                        ) { backStackEntry ->
                            val autoGenerate = backStackEntry.arguments?.getBoolean("autoGenerate") ?: false
                            val startTimeStr = backStackEntry.arguments?.getString("startTime")
                            val startTime = startTimeStr?.toLongOrNull()
                            
                            val reportBuilderViewModel: ReportBuilderViewModel = viewModel {
                                ReportBuilderViewModel(reportRepository, engagementRepository.activeProjectId.value)
                            }
                            ReportBuilderScreen(
                                viewModel = reportBuilderViewModel,
                                autoGenerate = autoGenerate,
                                startTime = startTime,
                                onBack = { navController.popBackStack() }
                            )
                        }
                    }
                }
            }
        }
    }

    /** Optional `target` query argument (null when absent or still the `{target}` placeholder). */
    private fun NavBackStackEntry.targetArg(): String? =
        arguments?.getString("target")?.takeUnless { it.isBlank() || it == "{target}" }

    private fun scheduleRecycleBinCleanup() {
        val constraints = Constraints.Builder()
            .setRequiresBatteryNotLow(requiresBatteryNotLow = true)
            .setRequiresStorageNotLow(requiresStorageNotLow = true)
            .build()

        val cleanupRequest = PeriodicWorkRequestBuilder<RecycleBinWorker>(24, TimeUnit.HOURS)
            .setConstraints(constraints)
            .build()

        WorkManager.getInstance(this).enqueueUniquePeriodicWork(
            "RecycleBinCleanup",
            ExistingPeriodicWorkPolicy.KEEP,
            cleanupRequest,
        )
    }

    private fun scheduleVulnerabilityUpdates() {
        val constraints = Constraints.Builder()
            .setRequiredNetworkType(NetworkType.CONNECTED)
            .build()

        // One-time update on startup
        val initialUpdateRequest = OneTimeWorkRequestBuilder<VulnerabilityUpdateWorker>()
            .setConstraints(constraints)
            .build()

        // Periodic updates every 24 hours
        val periodicUpdateRequest = PeriodicWorkRequestBuilder<VulnerabilityUpdateWorker>(24, TimeUnit.HOURS)
            .setConstraints(constraints)
            .build()

        WorkManager.getInstance(this).apply {
            enqueue(initialUpdateRequest)
            enqueueUniquePeriodicWork(
                "VulnerabilityUpdate",
                ExistingPeriodicWorkPolicy.KEEP,
                periodicUpdateRequest
            )
        }
    }

    private fun getAppVersion(): String {
        return try {
            val packageInfo = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                packageManager.getPackageInfo(packageName, PackageManager.PackageInfoFlags.of(0))
            } else {
                @Suppress("DEPRECATION")
                packageManager.getPackageInfo(packageName, 0)
            }
            packageInfo.versionName ?: "Unknown"
        } catch (_: Exception) {
            "Unknown"
        }
    }

    private fun handleCameraShortcuts(intent: Intent?) {
        if (intent == null) return
        
        if (isCameraIntent(intent)) {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O_MR1) {
                setShowWhenLocked(true)
                setTurnScreenOn(true)
            } else {
                @Suppress("DEPRECATION")
                window.addFlags(
                    android.view.WindowManager.LayoutParams.FLAG_SHOW_WHEN_LOCKED or
                    android.view.WindowManager.LayoutParams.FLAG_TURN_SCREEN_ON
                )
            }
            
            // Note: We don't call requestDismissKeyguard here anymore to allow 
            // secure camera usage directly on the lock screen.
        }
    }

    private fun isCameraIntent(intent: Intent?): Boolean {
        return intent?.action == MediaStore.INTENT_ACTION_STILL_IMAGE_CAMERA ||
               intent?.action == MediaStore.INTENT_ACTION_STILL_IMAGE_CAMERA_SECURE ||
               intent?.action == MediaStore.ACTION_IMAGE_CAPTURE
    }

    /**
     * Reader mode only while the NFC Scanner screen asks for it. It used to be on for the
     * whole app, intercepting taps (and NFC payments) on every screen.
     */
    private fun updateNfcReaderMode(enabled: Boolean) {
        val adapter = nfcAdapter ?: return
        if (enabled) {
            adapter.enableReaderMode(
                this,
                { tag ->
                    vibratePhone()
                    nfcRepository.processTag(tag)
                },
                NfcAdapter.FLAG_READER_NFC_A or NfcAdapter.FLAG_READER_NFC_B or
                    NfcAdapter.FLAG_READER_NFC_F or NfcAdapter.FLAG_READER_NFC_V or
                    NfcAdapter.FLAG_READER_NFC_BARCODE or
                    NfcAdapter.FLAG_READER_SKIP_NDEF_CHECK,
                null,
            )
        } else {
            runCatching { adapter.disableReaderMode(this) }
        }
    }

    private fun vibratePhone() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            val vibratorManager = getSystemService(VIBRATOR_MANAGER_SERVICE) as VibratorManager
            val vibrator = vibratorManager.defaultVibrator
            vibrator.vibrate(VibrationEffect.createOneShot(50, VibrationEffect.DEFAULT_AMPLITUDE))
        } else {
            @Suppress("DEPRECATION")
            val vibrator = getSystemService(VIBRATOR_SERVICE) as Vibrator
            @Suppress("DEPRECATION")
            vibrator.vibrate(50)
        }
    }

    override fun onPause() {
        super.onPause()
        runCatching { nfcAdapter?.disableReaderMode(this) }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        handleCameraShortcuts(intent)
        if (NfcAdapter.ACTION_TAG_DISCOVERED == intent.action) {
            val tag: Tag? = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                intent.getParcelableExtra(NfcAdapter.EXTRA_TAG, Tag::class.java)
            } else {
                @Suppress("DEPRECATION")
                intent.getParcelableExtra(NfcAdapter.EXTRA_TAG)
            }
            tag?.let { nfcRepository.processTag(it) }
        }
    }
}
