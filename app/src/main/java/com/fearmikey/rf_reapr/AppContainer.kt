package com.fearmikey.rf_reapr

import android.app.Application
import com.fearmikey.rf_reapr.data.backup.BackupManager
import com.fearmikey.rf_reapr.data.db.AppDatabase
import com.fearmikey.rf_reapr.data.local.ActiveProjectStore
import com.fearmikey.rf_reapr.data.local.SettingsRepositoryImpl
import com.fearmikey.rf_reapr.data.remote.VulnerabilityApiService
import com.fearmikey.rf_reapr.data.repository.*
import com.fearmikey.rf_reapr.system.NetworkAlertNotificationManager
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import okhttp3.OkHttpClient
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory

/**
 * Manual dependency injection container, owned by [RfReaprApplication].
 *
 * Repositories live for the whole process instead of being re-created with every
 * Activity (rotation used to orphan running monitors in the old instances), and are
 * reachable from WorkManager workers. Everything is lazy so cold start only pays for what
 * the first screen uses. All repositories get the Application context (no Activity leaks).
 */
class AppContainer(private val app: Application) {

    /** Process-lifetime scope for long-lived state holders. */
    val applicationScope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

    val database: AppDatabase by lazy { AppDatabase.getDatabase(app) }

    val okHttpClient: OkHttpClient by lazy { OkHttpClient() }

    private val vulnerabilityApi: VulnerabilityApiService by lazy {
        Retrofit.Builder()
            .baseUrl("https://services.nvd.nist.gov/rest/json/")
            .addConverterFactory(GsonConverterFactory.create())
            .build()
            .create(VulnerabilityApiService::class.java)
    }

    // ---- Engagement / findings --------------------------------------------------------
    val activeProjectStore by lazy { ActiveProjectStore(app, database.evidenceDao(), applicationScope) }
    val findingRepository by lazy { FindingRepositoryImpl(database.findingDao(), activeProjectStore) }
    val engagementRepository by lazy {
        EngagementRepositoryImpl(
            database.evidenceDao(), database.scanSessionDao(), database.eventLogDao(),
            findingRepository, activeProjectStore
        )
    }
    val backupManager by lazy { BackupManager(app) }

    // ---- Core -------------------------------------------------------------------------
    val settingsRepository by lazy { SettingsRepositoryImpl(app) }
    val logRepository by lazy { LogRepositoryImpl(database.eventLogDao()) { activeProjectStore.activeProjectId.value } }
    val scanSessionRepository by lazy {
        ScanSessionRepositoryImpl(database.networkDao(), database.scanSessionDao()) { activeProjectStore.activeProjectId.value }
    }
    val networkAlertNotifier by lazy { NetworkAlertNotificationManager(app) }

    // ---- Network ----------------------------------------------------------------------
    val portRepository by lazy { PortScannerRepositoryImpl() }
    val vulnerabilityRepository by lazy { VulnerabilityRepositoryImpl(vulnerabilityApi, database.vulnerabilityDao(), settingsRepository) }
    val discoveryRepository by lazy { NetworkDiscoveryRepositoryImpl(app) }
    val dhcpRepository by lazy { DhcpMonitorRepositoryImpl(discoveryRepository) }
    val ouiRepository by lazy { OuiRepositoryImpl(app) }
    val dnsQueryRepository by lazy { DnsQueryRepositoryImpl(okHttpClient) }
    val wolRepository by lazy { WolRepositoryImpl(app) }
    val pingRepository by lazy { PingRepositoryImpl() }
    val snmpRepository by lazy { SnmpRepositoryImpl() }
    val serviceDiscoveryRepository by lazy { ServiceDiscoveryRepositoryImpl(app) }
    val dnsAuditorRepository by lazy { DnsAuditorRepositoryImpl(app, okHttpClient) }
    val tracerouteRepository by lazy { TracerouteRepositoryImpl() }
    val arpDetectorRepository by lazy { ArpDetectorRepositoryImpl(app, networkAlertNotifier) }
    val upnpScannerRepository by lazy { UpnpScannerRepositoryImpl(okHttpClient, discoveryRepository) }

    // ---- Wireless & physical ----------------------------------------------------------
    val wifiRepository by lazy { WifiFingerprintRepositoryImpl(app) }
    val bleRepository by lazy { BleScannerRepositoryImpl(app) }
    val proximityRepository by lazy { BleProximityRepositoryImpl(app) }
    val nfcRepository by lazy { NfcScannerRepositoryImpl() }
    val magnetometerRepository by lazy { MagnetometerRepositoryImpl(app) }
    val sdrRepository by lazy { RtlTcpRepositoryImpl() }
    val hidRepository by lazy { LocalHidRepository(database.hidScriptDao()) }
    val hidAssetRepository by lazy { LocalHidAssetRepository(app, database.hidAssetDao()) }
    val usbDriveRepository by lazy { UsbDriveRepositoryImpl(app, database.usbDriveDao()) }

    // ---- Web & OSINT ------------------------------------------------------------------
    val websiteInspectorRepository by lazy { WebsiteInspectorRepositoryImpl(okHttpClient) }
    val subdomainFinderRepository by lazy { SubdomainFinderRepositoryImpl(okHttpClient) }
    val tlsCipherScannerRepository by lazy { TlsCipherScannerRepositoryImpl() }
    val cloudAssetScannerRepository by lazy { CloudAssetScannerRepositoryImpl(okHttpClient) }
    val hibpRepository by lazy { HibpRepositoryImpl(okHttpClient, settingsRepository) }
    val usernameOsintRepository by lazy { UsernameOsintRepositoryImpl(okHttpClient) }
    val threatIntelRepository by lazy { ThreatIntelRepositoryImpl(okHttpClient) }
    val shodanRepository by lazy { ShodanRepositoryImpl(okHttpClient, settingsRepository) }
    val internetDbRepository by lazy { InternetDbRepositoryImpl(okHttpClient) }
    val exifRepository by lazy { ExifRepositoryImpl(app) }

    // ---- Evidence, compliance, reporting ----------------------------------------------
    val complianceRepository by lazy { ComplianceRepositoryImpl(database.complianceDao()) }
    val evidenceRepository by lazy { EvidenceRepositoryImpl(database.evidenceDao(), app) }
    val reportRepository by lazy {
        ReportRepositoryImpl(
            app, database.scanSessionDao(), database.evidenceDao(), database.complianceDao(), database.eventLogDao()
        )
    }
}
