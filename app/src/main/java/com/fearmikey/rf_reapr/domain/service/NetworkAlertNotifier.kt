package com.fearmikey.rf_reapr.domain.service

import com.fearmikey.rf_reapr.domain.model.MonitoredDevice

/** Posts user-visible alerts for network monitoring events (e.g. a new device joining). */
interface NetworkAlertNotifier {
    fun notifyDeviceDetected(device: MonitoredDevice, reason: String)

    /** Generic security alert (e.g. ARP spoofing). [key] dedupes repeated alerts. */
    fun notifySecurityAlert(title: String, text: String, key: String = title) = Unit
}
