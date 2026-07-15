package com.fearmikey.rf_reapr

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
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
import com.fearmikey.rf_reapr.ui.web.WebsiteInspectorScreen
import com.fearmikey.rf_reapr.ui.web.WebsiteInspectorViewModel
import com.fearmikey.rf_reapr.ui.scanner.PortScannerScreen
import com.fearmikey.rf_reapr.ui.scanner.PortScannerViewModel
import com.fearmikey.rf_reapr.ui.settings.SettingsScreen
import com.fearmikey.rf_reapr.ui.settings.SettingsViewModel
import com.fearmikey.rf_reapr.ui.splash.SplashScreen
import com.fearmikey.rf_reapr.ui.topology.TopologyScreen
import com.fearmikey.rf_reapr.ui.topology.TopologyViewModel
import com.fearmikey.rf_reapr.ui.wifi.WifiFingerprintScreen
import com.fearmikey.rf_reapr.ui.wifi.WifiFingerprintViewModel
import com.fearmikey.rf_reapr.ui.permissions.PermissionExplanationScreen
import com.fearmikey.rf_reapr.ui.physical.HidInjectorScreen
import com.fearmikey.rf_reapr.ui.physical.MagnetometerScreen
import com.fearmikey.rf_reapr.ui.physical.MagnetometerViewModel
import com.fearmikey.rf_reapr.ui.wireless.BluetoothProximityFinderScreen
import com.fearmikey.rf_reapr.ui.wireless.BluetoothProximityFinderViewModel
import com.fearmikey.rf_reapr.data.repository.BleProximityRepositoryImpl
import com.fearmikey.rf_reapr.ui.theme.RF_REAPRTheme
import okhttp3.OkHttpClient
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import java.util.concurrent.TimeUnit

class MainActivity : ComponentActivity() {
    private var nfcAdapter: NfcAdapter? = null
    private lateinit var nfcRepository: NfcScannerRepositoryImpl

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        
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
        
        val portRepository = PortScannerRepositoryImpl()
        val vulnerabilityRepository = VulnerabilityRepositoryImpl(apiService, database.vulnerabilityDao())
        val discoveryRepository = NetworkDiscoveryRepositoryImpl(this)
        val wifiRepository = WifiFingerprintRepositoryImpl(this)
        val bleRepository = BleScannerRepositoryImpl(this)
        nfcRepository = NfcScannerRepositoryImpl()
        val settingsRepository = SettingsRepositoryImpl(this)
        val dhcpRepository = DhcpMonitorRepositoryImpl(discoveryRepository)
        val websiteInspectorRepository = WebsiteInspectorRepositoryImpl(okHttpClient)
        val pingRepository = PingRepositoryImpl()
        val logRepository = LogRepositoryImpl(database.eventLogDao())
        val complianceRepository = ComplianceRepositoryImpl(database.complianceDao())
        val proximityRepository = BleProximityRepositoryImpl(this)
        val evidenceRepository = EvidenceRepositoryImpl(database.evidenceDao(), this)
        val magnetometerRepository = MagnetometerRepositoryImpl(this)
        nfcAdapter = NfcAdapter.getDefaultAdapter(this)

        // Schedule Workers
        scheduleRecycleBinCleanup()
        scheduleVulnerabilityUpdates()

        setContent {
            val settingsViewModel: SettingsViewModel = viewModel {
                SettingsViewModel(settingsRepository, logRepository)
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
                    val versionName = getAppVersion()
                    val portScannerViewModel: PortScannerViewModel = viewModel {
                        PortScannerViewModel(portRepository, vulnerabilityRepository, scanSessionRepository, logRepository)
                    }
                    val topologyViewModel: TopologyViewModel = viewModel {
                        TopologyViewModel(discoveryRepository, scanSessionRepository, portRepository, vulnerabilityRepository, logRepository)
                    }
                    val wifiViewModel: WifiFingerprintViewModel = viewModel {
                        WifiFingerprintViewModel(wifiRepository, logRepository)
                    }
                    val nfcViewModel: NfcScannerViewModel = viewModel {
                        NfcScannerViewModel(nfcRepository)
                    }
                    val websiteInspectorViewModel: WebsiteInspectorViewModel = viewModel {
                        WebsiteInspectorViewModel(websiteInspectorRepository, logRepository)
                    }
                    val pingViewModel: PingViewModel = viewModel {
                        PingViewModel(pingRepository, logRepository)
                    }
                    val logViewModel: LogViewModel = viewModel {
                        LogViewModel(logRepository)
                    }
                    val dhcpViewModel: DhcpMonitorViewModel = viewModel {
                        DhcpMonitorViewModel(dhcpRepository)
                    }
                    val complianceViewModel: ComplianceViewModel = viewModel {
                        ComplianceViewModel(complianceRepository)
                    }
                    val proximityViewModel: BluetoothProximityFinderViewModel = viewModel {
                        BluetoothProximityFinderViewModel(proximityRepository, bleRepository)
                    }
                    val evidenceViewModel: EvidenceCaptureViewModel = viewModel {
                        EvidenceCaptureViewModel(evidenceRepository, logRepository)
                    }
                    val recycleBinViewModel: RecycleBinViewModel = viewModel {
                        RecycleBinViewModel(evidenceRepository)
                    }
                    val magnetometerViewModel: MagnetometerViewModel = viewModel {
                        MagnetometerViewModel(magnetometerRepository)
                    }

                    NavHost(
                        navController = navController,
                        startDestination = Screen.Splash.route,
                    ) {
                        composable(Screen.Splash.route) {
                            SplashScreen(versionName = versionName) {
                                val hasBluetooth = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                                    ActivityCompat.checkSelfPermission(this@MainActivity, Manifest.permission.BLUETOOTH_SCAN) == PackageManager.PERMISSION_GRANTED
                                } else {
                                    ActivityCompat.checkSelfPermission(this@MainActivity, Manifest.permission.BLUETOOTH) == PackageManager.PERMISSION_GRANTED
                                }
                                val hasLocation = ActivityCompat.checkSelfPermission(this@MainActivity, Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED
                                
                                if (hasBluetooth && hasLocation) {
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
                            MainMenuScreen { route ->
                                navController.navigate(route)
                            }
                        }
                        composable(Screen.PortScanner.route) { backStackEntry ->
                            val ip = backStackEntry.arguments?.getString("ip")
                            PortScannerScreen(
                                viewModel = portScannerViewModel,
                                initialIp = ip,
                                onBack = { navController.popBackStack() },
                            ) { _, _ -> }
                        }
                        composable(Screen.TopologyMap.route) {
                            TopologyScreen(
                                viewModel = topologyViewModel,
                            ) { navController.popBackStack() }
                        }
                        composable(Screen.WifiFingerprinter.route) {
                            WifiFingerprintScreen(
                                viewModel = wifiViewModel,
                            ) { navController.popBackStack() }
                        }
                        composable(Screen.BluetoothProximityFinder.route) {
                            BluetoothProximityFinderScreen(
                                viewModel = proximityViewModel,
                            ) { navController.popBackStack() }
                        }
                        composable(Screen.NfcScanner.route) {
                            NfcScannerScreen(
                                viewModel = nfcViewModel,
                            ) { navController.popBackStack() }
                        }
                        composable(Screen.Settings.route) {
                            SettingsScreen(
                                viewModel = settingsViewModel,
                            ) { navController.popBackStack() }
                        }
                        composable(Screen.DhcpMonitor.route) {
                            DhcpMonitorScreen(
                                viewModel = dhcpViewModel,
                                onBack = { navController.popBackStack() },
                                onDeviceClick = { device ->
                                    navController.navigate(Screen.PortScanner.createRoute(device.ipAddress))
                                }
                            )
                        }
                        composable(Screen.WebsiteInspector.route) {
                            WebsiteInspectorScreen(
                                viewModel = websiteInspectorViewModel,
                            ) { navController.popBackStack() }
                        }
                        composable(Screen.PingTool.route) {
                            PingScreen(
                                viewModel = pingViewModel,
                            ) { navController.popBackStack() }
                        }
                        composable(Screen.HidInjector.route) {
                            HidInjectorScreen { navController.popBackStack() }
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
                            ) { navController.popBackStack() }
                        }

                        composable(Screen.RecycleBin.route) {
                            RecycleBinScreen(
                                viewModel = recycleBinViewModel,
                            ) { navController.popBackStack() }
                        }

                        composable(Screen.Magnetometer.route) {
                            MagnetometerScreen(
                                viewModel = magnetometerViewModel,
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
                        composable(Screen.TopologyLogs.route) {
                            LogListScreen("TOPOLOGY", "Network Map Logs", logViewModel) { navController.popBackStack() }
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
