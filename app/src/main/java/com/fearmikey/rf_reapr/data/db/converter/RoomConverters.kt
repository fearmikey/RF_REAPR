package com.fearmikey.rf_reapr.data.db.converter

import androidx.room.TypeConverter
import com.fearmikey.rf_reapr.domain.model.OpenPort
import com.fearmikey.rf_reapr.domain.model.RiskLevel
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken

class RoomConverters {
    private val gson = Gson()

    @TypeConverter
    fun fromRiskLevel(value: RiskLevel): String = value.name

    @TypeConverter
    fun toRiskLevel(value: String): RiskLevel = enumValueOf(value)

    @TypeConverter
    fun fromOpenPortList(value: List<OpenPort>): String {
        return gson.toJson(value)
    }

    @TypeConverter
    fun toOpenPortList(value: String): List<OpenPort> {
        val listType = object : TypeToken<List<OpenPort>>() {}.type
        return gson.fromJson(value, listType)
    }
}
