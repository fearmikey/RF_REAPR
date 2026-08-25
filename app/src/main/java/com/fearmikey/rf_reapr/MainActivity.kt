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
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.ui.Modifier
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import androidx.lifecycle.DefaultLifecycleObserver
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.ProcessLifecycleOwner
import androidx.lifecycle.viewmodel.compose.viewModel
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
import com.fearmikey.rf_reapr.domain.service.ActiveTaskMonitor
import com.fearmikey.rf_reapr.system.ScanNotificationManager
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
import com.fearmikey.rf_reapr.ui.menu.AppStartMenuScreen
import com.fearmikey.rf_reapr.ui.workflow.WorkflowScreen
import com.fearmikey.rf_reapr.ui.workflow.WorkflowViewModel
import com.fearmikey.rf_reapr.domain.model.AppMode
import com.fearmikey.rf_reapr.domain.model.WorkflowStep
import androidx.compose.material.icons.automirrored.filled.ManageSearch
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
import okhttp3.OkHttpClient
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory
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
    private lateinit var notificationManager: ScanNotificationManager

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        
        notificationManager = ScanNotificationManager(this)

        ProcessLifecycleOwner.get().lifecycle.addObserver(object : DefaultLifecycleObserver {
            override fun onStart(owner: LifecycleOwner) {
                notificationManager.cancelNotification()
            }

            override fun onStop(owner: LifecycleOwner) {
                if (ActiveTaskMonitor.hasActiveTasks()) {
                    notificationManager.showBackgroundNotification()
                }
            }
        })
        
        handleCameraShortcuts(intent)

        // Manual DI
        val retrofit = Retrofit.Builder()
            .baseUrl("https://services.nvd.nist.gov/rest/json/")
            .addConverterFactory(GsonConverterFactory.create())
            .build()
            
        val apiService = retrofit.create(VulnerabilityApiService::class.java)
        val okHttpClient = OkHttpClient()
        val database = AppDatabase.getDatabase(this)
        
        val scanSessionRepository = ScanSessionRepositoryImpl(
            database.networkDao(),
            database.scanSessionDao(),
        )
        
        val settingsRepository = SettingsRepositoryImpl(this)
        val portRepository = PortScannerRepositoryImpl()
        val vulnerabilityRepository = VulnerabilityRepositoryImpl(apiService, database.vulnerabilityDao(), settingsRepository)
        val discoveryRepository = NetworkDiscoveryRepositoryImpl(this)
        val wifiRepository = WifiFingerprintRepositoryImpl(this)
        val bleRepository = BleScannerRepositoryImpl(this)
        nfcRepository = NfcScannerRepositoryImpl()
        val dhcpRepository = DhcpMonitorRepositoryImpl(discoveryRepository)
        val websiteInspectorRepository = WebsiteInspectorRepositoryImpl(okHttpClient)
        val subdomainFinderRepository = SubdomainFinderRepositoryImpl(okHttpClient)
        val tlsCipherScannerRepository = TlsCipherScannerRepositoryImpl()
        val cloudAssetScannerRepository = CloudAssetScannerRepositoryImpl(okHttpClient)
        val hibpRepository = HibpRepositoryImpl(okHttpClient, settingsRepository)
        val pingRepository = PingRepositoryImpl()
        val logRepository = LogRepositoryImpl(database.eventLogDao())
        val complianceRepository = ComplianceRepositoryImpl(database.complianceDao())
        val proximityRepository = BleProximityRepositoryImpl(this)
        val evidenceRepository = EvidenceRepositoryImpl(database.evidenceDao(), this)
        val magnetometerRepository = MagnetometerRepositoryImpl(this)
        val reportRepository = ReportRepositoryImpl(
            this,
            database.scanSessionDao(),
            database.evidenceDao(),
            database.complianceDao(),
            database.eventLogDao()
        )
        
        val sdrRepository = RtlTcpRepositoryImpl()
        val snmpRepository = SnmpRepositoryImpl()
        
        // New Repositories
        val serviceDiscoveryRepository = ServiceDiscoveryRepositoryImpl(this)
        val dnsAuditorRepository = DnsAuditorRepositoryImpl(this, okHttpClient)
        val tracerouteRepository = TracerouteRepositoryImpl()
        val arpDetectorRepository = ArpDetectorRepositoryImpl(this)
        val upnpScannerRepository = UpnpScannerRepositoryImpl()
        val shodanRepository = ShodanRepositoryImpl(okHttpClient, settingsRepository)
        val internetDbRepository = InternetDbRepositoryImpl(okHttpClient)
        val hidRepository = LocalHidRepository(database.hidScriptDao())
        val hidAssetRepository = LocalHidAssetRepository(this, database.hidAssetDao())

        nfcAdapter = NfcAdapter.getDefaultAdapter(this)

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
                        ComplianceViewModel(complianceRepository)
                    }
                    val evidenceViewModel: EvidenceCaptureViewModel = viewModel {
                        EvidenceCaptureViewModel(evidenceRepository, logRepository)
                    }
                    
                    val appModes = remember {
                        listOf(
                            AppMode(
                                id = "internal_network_recon",
                                title = "Internal Network Recon",
                                description = "Automated discovery and vulnerability mapping for internal LANs.",
                                icon = Icons.AutoMirrored.Filled.ManageSearch,
                                securityWarning = "Internal Network Recon performs automated network scans, service discovery, and vulnerability checks on the local network. This may trigger intrusion detection systems (IDS) or cause instability on legacy devices. Ensure you have explicit authorization for the target network.",
                                steps = listOf(
                                    WorkflowStep("net_disc", "Network Discovery", "Map devices and identify targets.", Screen.TopologyMap.route, Icons.Default.Router, isMandatory = true),
                                    WorkflowStep("port_scan", "Port Scanner", "Find open ports and running services.", Screen.PortScanner.route, Icons.Default.Search),
                                    WorkflowStep("svc_disc", "Service Discovery", "Discover network services via mDNS/Bonjour.", Screen.ServiceDiscovery.route, Icons.Default.SettingsRemote),
                                    WorkflowStep("dns_audit", "DNS Security Auditor", "Check for DNS hijacking and leaks.", Screen.DnsAuditor.route, Icons.Default.LockPerson),
                                    WorkflowStep("upnp_audit", "UPnP Auditor", "Audit router port mappings.", Screen.UpnpAuditor.route, Icons.Default.Router)
                                )
                            ),
                            AppMode(
                                id = "external_business_recon",
                                title = "External Business Recon",
                                description = "Public-facing asset discovery and vulnerability auditing.",
                                icon = Icons.Default.Language,
                                requiresTarget = true,
                                targetHint = "example.com",
                                securityWarning = "External Business Recon performs OSINT, subdomain discovery, and automated vulnerability scanning of public-facing endpoints. Ensure you have explicit legal authorization to audit the target domain and associated infrastructure.",
                                steps = listOf(
                                    WorkflowStep("subdomain_finder", "Subdomain Finder", "Discover public-facing hostnames and IPs.", Screen.SubdomainFinder.route, Icons.Default.Language, isMandatory = true),
                                    WorkflowStep("cloud_scanner", "Cloud Asset Scanner", "Identify exposed cloud storage and services.", Screen.CloudAssetScanner.route, Icons.Default.Cloud),
                                    WorkflowStep("website_inspector", "Website Inspector", "Audit security headers, DNS, and RDAP info.", Screen.WebsiteInspector.route, Icons.Default.Info),
                                    WorkflowStep("tls_scanner", "TLS Cipher Scanner", "Evaluate SSL/TLS configuration strength.", Screen.TlsCipherScanner.route, Icons.Default.Lock),
                                    WorkflowStep("shodan_search", "Shodan Quick Search", "Query Shodan for exposed infrastructure.", Screen.ShodanScanner.route, Icons.Default.Search),
                                    WorkflowStep("hibp_audit", "HIBP Breach Check", "Check for domain-related data breaches.", Screen.HibpChecker.route, Icons.Default.Shield)
                                )
                            )
                        )
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
                                    navController.navigate(Screen.AppStartMenu.route) {
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
                                navController.navigate(Screen.AppStartMenu.route) {
                                    popUpTo(Screen.PermissionExplanation.route) { inclusive = true }
                                }
                            }
                        }
                        composable(Screen.AppStartMenu.route) {
                            AppStartMenuScreen(
                                modes = appModes,
                                onNavigateToMode = { modeId ->
                                    navController.navigate(Screen.Workflow.createRoute(modeId))
                                },
                                onNavigateToTools = {
                                    navController.navigate(Screen.MainMenu.route)
                                }
                            ) {
                                navController.navigate(Screen.Settings.route)
                            }
                        }
                        composable(Screen.Workflow.route) { backStackEntry ->
                            val modeId = backStackEntry.arguments?.getString("modeId")
                            val mode = appModes.find { it.id == modeId }
                            if (mode != null) {
                                val workflowViewModel: WorkflowViewModel = viewModel {
                                    WorkflowViewModel(
                                        mode = mode,
                                        discoveryRepository = discoveryRepository,
                                        portScannerRepository = portRepository,
                                        serviceDiscoveryRepository = serviceDiscoveryRepository,
                                        dnsAuditorRepository = dnsAuditorRepository,
                                        upnpScannerRepository = upnpScannerRepository,
                                        subdomainFinderRepository = subdomainFinderRepository,
                                        websiteInspectorRepository = websiteInspectorRepository,
                                        cloudAssetScannerRepository = cloudAssetScannerRepository,
                                        tlsCipherScannerRepository = tlsCipherScannerRepository,
                                        shodanRepository = shodanRepository,
                                        hibpRepository = hibpRepository,
                                        logRepository = logRepository
                                    )
                                }
                                WorkflowScreen(
                                    viewModel = workflowViewModel,
                                    onNavigateToReport = { startTime ->
                                        navController.navigate(Screen.ReportBuilder.createRoute(autoGenerate = true, startTime = startTime))
                                    },
                                    onBack = { navController.popBackStack() }
                                )
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
                                                    PortScannerViewModel(portRepository, vulnerabilityRepository, scanSessionRepository, logRepository, settingsRepository)
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
                                onNavigateToSettings = { navController.navigate(Screen.Settings.route) }
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
                                                    BluetoothProximityFinderViewModel(proximityRepository, bleRepository)
                                                }
                            BluetoothProximityFinderScreen(
                                viewModel = proximityViewModel,
                            ) { navController.popBackStack() }
                        }
                        composable(Screen.NfcScanner.route) {
                            val nfcViewModel: NfcScannerViewModel = viewModel {
                                                    NfcScannerViewModel(nfcRepository)
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
                                                    DhcpMonitorViewModel(dhcpRepository, settingsRepository)
                                                }
                            DhcpMonitorScreen(
                                viewModel = dhcpViewModel,
                                onBack = { navController.popBackStack() },
                                onDeviceClick = { device ->
                                    navController.navigate(Screen.PortScanner.createRoute(device.ipAddress))
                                }
                            )
                        }
                        composable(Screen.WebsiteInspector.route) {
                            val websiteInspectorViewModel: WebsiteInspectorViewModel = viewModel {
                                                    WebsiteInspectorViewModel(websiteInspectorRepository, logRepository, settingsRepository)
                                                }
                            WebsiteInspectorScreen(
                                viewModel = websiteInspectorViewModel,
                            ) { navController.popBackStack() }
                        }
                        composable(Screen.SubdomainFinder.route) {
                            val subdomainFinderViewModel: SubdomainFinderViewModel = viewModel {
                                                    SubdomainFinderViewModel(subdomainFinderRepository, logRepository, settingsRepository)
                                                }
                            SubdomainFinderScreen(
                                viewModel = subdomainFinderViewModel,
                                onBack = { navController.popBackStack() },
                                onNavigateToCloudScanner = { domain ->
                                    navController.navigate(Screen.CloudAssetScanner.createRoute(domain))
                                }
                            )
                        }
                        composable(Screen.TlsCipherScanner.route) {
                            val tlsCipherScannerViewModel: TlsCipherScannerViewModel = viewModel {
                                                    TlsCipherScannerViewModel(tlsCipherScannerRepository, logRepository, settingsRepository)
                                                }
                            TlsCipherScannerScreen(
                                viewModel = tlsCipherScannerViewModel,
                            ) { navController.popBackStack() }
                        }
                        composable(Screen.CloudAssetScanner.route) { backStackEntry ->
                            val cloudAssetScannerViewModel: CloudAssetScannerViewModel = viewModel {
                                                    CloudAssetScannerViewModel(cloudAssetScannerRepository, logRepository, settingsRepository)
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
                                                    HibpViewModel(hibpRepository, settingsRepository, logRepository)
                                                }
                            HibpScreen(
                                viewModel = hibpViewModel,
                            ) { navController.popBackStack() }
                        }
                        composable(Screen.ShodanScanner.route) {
                            val shodanViewModel: ShodanViewModel = viewModel {
                                                    ShodanViewModel(shodanRepository, internetDbRepository, settingsRepository, logRepository)
                                                }
                            ShodanScreen(
                                viewModel = shodanViewModel,
                                onNavigateToApiKeys = { highlight -> 
                                    navController.navigate(Screen.ApiKeys.createRoute(highlight))
                                },
                                onBack = { navController.popBackStack() }
                            )
                        }
                        composable(Screen.PingTool.route) {
                            val pingViewModel: PingViewModel = viewModel {
                                                    PingViewModel(pingRepository, logRepository, settingsRepository)
                                                }
                            PingScreen(
                                viewModel = pingViewModel,
                            ) { navController.popBackStack() }
                        }
                        composable(Screen.HidInjector.route) {
                            val hidInjectorViewModel: HidInjectorViewModel = viewModel {
                                                    HidInjectorViewModel(hidRepository, hidAssetRepository, settingsRepository)
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
                            ) { navController.popBackStack() }
                        }
                        composable(Screen.DnsAuditor.route) {
                            val dnsAuditorViewModel: DnsAuditorViewModel = viewModel {
                                                    DnsAuditorViewModel(dnsAuditorRepository, logRepository, settingsRepository)
                                                }
                            DnsAuditorScreen(
                                viewModel = dnsAuditorViewModel,
                            ) { navController.popBackStack() }
                        }
                        composable(Screen.Traceroute.route) {
                            val tracerouteViewModel: TracerouteViewModel = viewModel {
                                                    TracerouteViewModel(tracerouteRepository, logRepository, settingsRepository)
                                                }
                            TracerouteScreen(
                                viewModel = tracerouteViewModel,
                            ) { navController.popBackStack() }
                        }
                        composable(Screen.ArpDetector.route) {
                            val arpDetectorViewModel: ArpDetectorViewModel = viewModel {
                                                    ArpDetectorViewModel(arpDetectorRepository)
                                                }
                            ArpDetectorScreen(
                                viewModel = arpDetectorViewModel,
                            ) { navController.popBackStack() }
                        }
                        composable(Screen.UpnpAuditor.route) {
                            val upnpScannerViewModel: UpnpScannerViewModel = viewModel {
                                                    UpnpScannerViewModel(upnpScannerRepository, settingsRepository)
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
                                onBack = { navController.popBackStack() }
                            )
                        }
                        composable(Screen.SnmpBrowser.route) {
                            val snmpViewModel: SnmpViewModel = viewModel {
                                                    SnmpViewModel(snmpRepository, logRepository)
                                                }
                            SnmpBrowserScreen(
                                viewModel = snmpViewModel,
                                onBack = { navController.popBackStack() }
                            )
                        }
                        
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
                                ReportBuilderViewModel(reportRepository)
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

    override fun onResume() {
        super.onResume()
        nfcAdapter?.enableReaderMode(
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
        nfcAdapter?.disableReaderMode(this)
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
