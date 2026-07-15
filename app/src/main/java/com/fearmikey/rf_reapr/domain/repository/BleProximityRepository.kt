package com.fearmikey.rf_reapr.domain.repository

import kotlinx.coroutines.flow.Flow

interface BleProximityRepository {
    fun trackDevice(address: String): Flow<Int>
}
