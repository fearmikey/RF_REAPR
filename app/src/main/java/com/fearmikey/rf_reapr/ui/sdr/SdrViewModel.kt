package com.fearmikey.rf_reapr.ui.sdr

import android.content.Context
import android.content.Intent
import androidx.core.net.toUri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.fearmikey.rf_reapr.domain.model.FftData
import com.fearmikey.rf_reapr.domain.model.SdrConfig
import com.fearmikey.rf_reapr.domain.repository.SdrRepository
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch

class SdrViewModel(private val repository: SdrRepository) : ViewModel() {

    val config: StateFlow<SdrConfig> = repository.config
    val fftData: StateFlow<FftData> = repository.fftData
    val error: StateFlow<String?> = repository.error

    fun connect(host: String, port: Int) {
        viewModelScope.launch {
            repository.connect(host, port)
        }
    }

    fun disconnect() {
        viewModelScope.launch {
            repository.disconnect()
        }
    }

    fun setFrequency(frequency: Long) {
        viewModelScope.launch {
            repository.setFrequency(frequency)
        }
    }

    fun setGain(gain: Int) {
        viewModelScope.launch {
            repository.setGain(gain)
        }
    }

    fun launchDriver(context: Context) {
        val driverPackages = listOf(
            "com.martinmarinov.rtl_tcp_driver",
            "com.sdrtouch.rtlsdr",
            "org.wa0ane.rtlsdr"
        )
        
        var launched = false
        val pm = context.packageManager

        for (packageName in driverPackages) {
            // Method 1: Standard Launcher Intent
            val launchIntent = pm.getLaunchIntentForPackage(packageName)
            if (launchIntent != null) {
                context.startActivity(launchIntent)
                launched = true
                break
            }
            
            // Method 2: Explicit MainActivity (some versions might not have Category.LAUNCHER correctly)
            try {
                val intent = Intent(Intent.ACTION_MAIN)
                intent.setClassName(packageName, "$packageName.MainActivity")
                intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                pm.getActivityInfo(intent.component!!, 0) // Check if it exists
                context.startActivity(intent)
                launched = true
                break
            } catch (_: Exception) {}
        }

        if (!launched) {
            val primaryPackage = "com.martinmarinov.rtl_tcp_driver"
            
            // Try F-Droid specific deep link first since user mentioned F-Droid
            try {
                val fdroidIntent = Intent(Intent.ACTION_VIEW, "fdroid.app://details?id=$primaryPackage".toUri())
                fdroidIntent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                context.startActivity(fdroidIntent)
                return
            } catch (_: Exception) {}

            // Fallback: Market link (F-Droid also intercepts this)
            try {
                val marketIntent = Intent(Intent.ACTION_VIEW, "market://details?id=$primaryPackage".toUri())
                marketIntent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                context.startActivity(marketIntent)
            } catch (_: Exception) {
                val webIntent = Intent(Intent.ACTION_VIEW, "https://f-droid.org/packages/$primaryPackage/".toUri())
                webIntent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                context.startActivity(webIntent)
            }
        }
    }
}
