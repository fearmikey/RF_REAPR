package com.example.rf_reapr

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.nfc.NfcAdapter
import android.nfc.Tag
import android.os.Build
import android.os.Bundle
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
import com.example.rf_reapr.data.remote.VulnerabilityApiService
import com.example.rf_reapr.data.repository.BleScannerRepositoryImpl
import com.example.rf_reapr.data.local.SettingsRepositoryImpl
import com.example.rf_reapr.data.repository.DhcpMonitorRepositoryImpl
import com.example.rf_reapr.data.repository.DnsEnumeratorRepositoryImpl
import com.example.rf_reapr.data.repository.HttpInspectorRepositoryImpl
import com.example.rf_reapr.data.repository.NetworkDiscoveryRepositoryImpl
import com.example.rf_reapr.data.repository.NfcScannerRepositoryImpl
import com.example.rf_reapr.data.repository.PortScannerRepositoryImpl
import com.example.rf_reapr.data.repository.RdapRepositoryImpl
import com.example.rf_reapr.data.repository.TlsAuditorRepositoryImpl
import com.example.rf_reapr.data.repository.VulnerabilityRepositoryImpl
import com.example.rf_reapr.data.repository.WifiFingerprintRepositoryImpl
import com.example.rf_reapr.domain.model.ThemePreference
import com.example.rf_reapr.ui.dhcp.DhcpMonitorScreen
import com.example.rf_reapr.ui.dhcp.DhcpMonitorViewModel
import com.example.rf_reapr.ui.dns.DnsEnumeratorScreen
import com.example.rf_reapr.ui.dns.DnsEnumeratorViewModel
import com.example.rf_reapr.ui.http.HttpInspectorScreen
import com.example.rf_reapr.ui.http.HttpInspectorViewModel
import com.example.rf_reapr.ui.menu.MainMenuScreen
import com.example.rf_reapr.ui.navigation.Screen
import com.example.rf_reapr.ui.nfc.NfcScannerScreen
import com.example.rf_reapr.ui.nfc.NfcScannerViewModel
import com.example.rf_reapr.ui.rdap.RdapScreen
import com.example.rf_reapr.ui.rdap.RdapViewModel
import com.example.rf_reapr.ui.scanner.BleScannerScreen
import com.example.rf_reapr.ui.scanner.BleScannerViewModel
import com.example.rf_reapr.ui.scanner.PortScannerScreen
import com.example.rf_reapr.ui.scanner.PortScannerViewModel
import com.example.rf_reapr.ui.settings.SettingsScreen
import com.example.rf_reapr.ui.settings.SettingsViewModel
import com.example.rf_reapr.ui.splash.SplashScreen
import com.example.rf_reapr.ui.tls.TlsAuditorScreen
import com.example.rf_reapr.ui.tls.TlsAuditorViewModel
import com.example.rf_reapr.ui.topology.TopologyScreen
import com.example.rf_reapr.ui.topology.TopologyViewModel
import com.example.rf_reapr.ui.wifi.WifiFingerprintScreen
import com.example.rf_reapr.ui.wifi.WifiFingerprintViewModel
import com.example.rf_reapr.ui.theme.RF_REAPRTheme
import okhttp3.OkHttpClient
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue

class MainActivity : ComponentActivity() {
    private var nfcAdapter: NfcAdapter? = null
    private lateinit var nfcRepository: NfcScannerRepositoryImpl

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        
        // Request permissions for BLE scanning
        if (ActivityCompat.checkSelfPermission(this, Manifest.permission.BLUETOOTH_SCAN) != PackageManager.PERMISSION_GRANTED ||
            ActivityCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION) != PackageManager.PERMISSION_GRANTED) {
            ActivityCompat.requestPermissions(
                this,
                arrayOf(
                    Manifest.permission.BLUETOOTH_SCAN,
                    Manifest.permission.BLUETOOTH_CONNECT,
                    Manifest.permission.ACCESS_FINE_LOCATION
                ),
                1
            )
        }
        
        // Manual DI for demonstration
        val retrofit = Retrofit.Builder()
            .baseUrl("https://api.example-vulnerability-db.com/")
            .addConverterFactory(GsonConverterFactory.create())
            .build()
            
        val apiService = retrofit.create(VulnerabilityApiService::class.java)
        
        val okHttpClient = OkHttpClient()
        
        val portRepository = PortScannerRepositoryImpl()
        val vulnerabilityRepository = VulnerabilityRepositoryImpl(apiService)
        val discoveryRepository = NetworkDiscoveryRepositoryImpl(this)
        val wifiRepository = WifiFingerprintRepositoryImpl(this)
        val bleRepository = BleScannerRepositoryImpl(this)
        nfcRepository = NfcScannerRepositoryImpl()
        val httpRepository = HttpInspectorRepositoryImpl(okHttpClient)
        val tlsRepository = TlsAuditorRepositoryImpl()
        val dnsRepository = DnsEnumeratorRepositoryImpl()
        val settingsRepository = SettingsRepositoryImpl(this)
        val dhcpRepository = DhcpMonitorRepositoryImpl(discoveryRepository)
        val rdapRepository = RdapRepositoryImpl(okHttpClient)
        nfcAdapter = NfcAdapter.getDefaultAdapter(this)

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
                    val portScannerViewModel: PortScannerViewModel = viewModel {
                        PortScannerViewModel(portRepository, vulnerabilityRepository)
                    }
                    val topologyViewModel: TopologyViewModel = viewModel {
                        TopologyViewModel(discoveryRepository)
                    }
                    val wifiViewModel: WifiFingerprintViewModel = viewModel {
                        WifiFingerprintViewModel(wifiRepository)
                    }
                    val bleViewModel: BleScannerViewModel = viewModel {
                        BleScannerViewModel(bleRepository)
                    }
                    val nfcViewModel: NfcScannerViewModel = viewModel {
                        NfcScannerViewModel(nfcRepository)
                    }
                    val httpViewModel: HttpInspectorViewModel = viewModel {
                        HttpInspectorViewModel(httpRepository)
                    }
                    val tlsViewModel: TlsAuditorViewModel = viewModel {
                        TlsAuditorViewModel(tlsRepository)
                    }
                    val dnsViewModel: DnsEnumeratorViewModel = viewModel {
                        DnsEnumeratorViewModel(dnsRepository)
                    }
                    val dhcpViewModel: DhcpMonitorViewModel = viewModel {
                        DhcpMonitorViewModel(dhcpRepository)
                    }
                    val rdapViewModel: RdapViewModel = viewModel {
                        RdapViewModel(rdapRepository)
                    }

                    NavHost(
                        navController = navController,
                        startDestination = Screen.Splash.route
                    ) {
                        composable(Screen.Splash.route) {
                            SplashScreen(onTimeout = {
                                navController.navigate(Screen.MainMenu.route) {
                                    popUpTo(Screen.Splash.route) { inclusive = true }
                                }
                            })
                        }
                        composable(Screen.MainMenu.route) {
                            MainMenuScreen(onNavigate = { route ->
                                navController.navigate(route)
                            })
                        }
                        composable(Screen.PortScanner.route) {
                            PortScannerScreen(
                                viewModel = portScannerViewModel,
                                onBack = { navController.popBackStack() },
                                onUpdateTopology = { results, gateway ->
                                    topologyViewModel.updateTopology(results, gateway)
                                }
                            )
                        }
                        composable(Screen.TopologyMap.route) {
                            TopologyScreen(
                                viewModel = topologyViewModel,
                                onBack = { navController.popBackStack() }
                            )
                        }
                        composable(Screen.WifiFingerprinter.route) {
                            WifiFingerprintScreen(
                                viewModel = wifiViewModel,
                                onBackClick = { navController.popBackStack() }
                            )
                        }
                        composable(Screen.BleAuditor.route) {
                            BleScannerScreen(
                                viewModel = bleViewModel,
                                onBack = { navController.popBackStack() }
                            )
                        }
                        composable(Screen.NfcScanner.route) {
                            NfcScannerScreen(
                                viewModel = nfcViewModel,
                                onBack = { navController.popBackStack() }
                            )
                        }
                        composable(Screen.HttpInspector.route) {
                            HttpInspectorScreen(
                                viewModel = httpViewModel,
                                onBack = { navController.popBackStack() }
                            )
                        }
                        composable(Screen.TlsAuditor.route) {
                            TlsAuditorScreen(
                                viewModel = tlsViewModel,
                                onBack = { navController.popBackStack() }
                            )
                        }
                        composable(Screen.Settings.route) {
                            SettingsScreen(
                                viewModel = settingsViewModel,
                                onBack = { navController.popBackStack() }
                            )
                        }
                        composable(Screen.DnsEnumerator.route) {
                            DnsEnumeratorScreen(
                                viewModel = dnsViewModel,
                                onBack = { navController.popBackStack() }
                            )
                        }
                        composable(Screen.DhcpMonitor.route) {
                            DhcpMonitorScreen(
                                viewModel = dhcpViewModel,
                                onBack = { navController.popBackStack() }
                            )
                        }
                        composable(Screen.RdapAuditor.route) {
                            RdapScreen(
                                viewModel = rdapViewModel,
                                onBack = { navController.popBackStack() }
                            )
                        }
                    }
                }
            }
        }
    }

    override fun onResume() {
        super.onResume()
        nfcAdapter?.enableReaderMode(
            this,
            { tag -> nfcRepository.processTag(tag) },
            NfcAdapter.FLAG_READER_NFC_A or NfcAdapter.FLAG_READER_NFC_B or
                    NfcAdapter.FLAG_READER_NFC_F or NfcAdapter.FLAG_READER_NFC_V or
                    NfcAdapter.FLAG_READER_SKIP_NDEF_CHECK,
            null
        )
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
