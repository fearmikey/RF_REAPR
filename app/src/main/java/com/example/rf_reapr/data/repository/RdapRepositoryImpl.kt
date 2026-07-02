package com.example.rf_reapr.data.repository

import com.example.rf_reapr.domain.model.RdapDomainInfo
import com.example.rf_reapr.domain.model.RdapEntity
import com.example.rf_reapr.domain.model.RdapEvent
import com.example.rf_reapr.domain.repository.RdapRepository
import com.google.gson.JsonArray
import com.google.gson.JsonObject
import com.google.gson.JsonParser
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOn
import okhttp3.OkHttpClient
import okhttp3.Request

class RdapRepositoryImpl(
    private val client: OkHttpClient
) : RdapRepository {

    override fun queryDomain(domain: String): Flow<RdapRepository.RdapStatus> = flow {
        emit(RdapRepository.RdapStatus.Loading)
        try {
            // Using a more standard RDAP aggregator/bootstrap service
            // and adding a User-Agent which is often required to avoid 403s
            val request = Request.Builder()
                .url("https://rdap.org/domain/${domain.lowercase()}")
                .header("Accept", "application/rdap+json")
                .header("User-Agent", "RF-REAPR-Toolkit/1.0 (Android; Security-Audit-Tool)")
                .build()

            client.newCall(request).execute().use { response ->
                if (response.code == 403) {
                    emit(RdapRepository.RdapStatus.Error("RDAP query failed: 403 Forbidden. The registry may be blocking the request or requires authentication."))
                    return@use
                }

                if (!response.isSuccessful) {
                    emit(RdapRepository.RdapStatus.Error("RDAP query failed: ${response.code}"))
                    return@use
                }

                val body = response.body?.string() ?: throw Exception("Empty response body")
                val json = JsonParser.parseString(body).asJsonObject
                
                val info = RdapDomainInfo(
                    ldhName = json.get("ldhName")?.asString ?: domain,
                    status = parseList(json.get("status")?.asJsonArray),
                    events = parseEvents(json.get("events")?.asJsonArray),
                    entities = parseEntities(json.get("entities")?.asJsonArray),
                    nameservers = parseNameservers(json.get("nameservers")?.asJsonArray),
                    rawJson = body
                )
                
                emit(RdapRepository.RdapStatus.Success(info))
            }
        } catch (e: Exception) {
            emit(RdapRepository.RdapStatus.Error(e.message ?: "Network error during RDAP query"))
        }
    }.flowOn(Dispatchers.IO)

    private fun parseList(array: JsonArray?): List<String> {
        return array?.map { it.asString } ?: emptyList()
    }

    private fun parseNameservers(array: JsonArray?): List<String> {
        return array?.mapNotNull { it.asJsonObject.get("ldhName")?.asString } ?: emptyList()
    }

    private fun parseEvents(array: JsonArray?): List<RdapEvent> {
        return array?.map { 
            val obj = it.asJsonObject
            RdapEvent(
                eventAction = obj.get("eventAction")?.asString ?: "Unknown",
                eventDate = obj.get("eventDate")?.asString ?: "Unknown"
            )
        } ?: emptyList()
    }

    private fun parseEntities(array: JsonArray?): List<RdapEntity> {
        return array?.map { 
            val obj = it.asJsonObject
            RdapEntity(
                roles = parseList(obj.get("roles")?.asJsonArray),
                vcard = null // vCard parsing is complex, skipping for basic implementation
            )
        } ?: emptyList()
    }
}
