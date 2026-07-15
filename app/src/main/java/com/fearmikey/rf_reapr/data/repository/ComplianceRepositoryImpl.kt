package com.fearmikey.rf_reapr.data.repository

import com.fearmikey.rf_reapr.data.db.dao.ComplianceDao
import com.fearmikey.rf_reapr.data.db.entity.ComplianceFindingEntity
import com.fearmikey.rf_reapr.domain.model.ComplianceControl
import com.fearmikey.rf_reapr.domain.model.ComplianceFramework
import com.fearmikey.rf_reapr.domain.model.ComplianceStatus
import com.fearmikey.rf_reapr.domain.repository.ComplianceRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

class ComplianceRepositoryImpl(
    private val complianceDao: ComplianceDao
) : ComplianceRepository {

    override fun getFrameworks(): List<ComplianceFramework> {
        return ComplianceFramework.entries
    }

    override fun getControlsForFramework(frameworkId: String): Flow<List<ComplianceControl>> {
        val staticControls = getStaticControls(frameworkId)
        return complianceDao.getFindingsForFramework(frameworkId).map { findings ->
            staticControls.map { control ->
                val finding = findings.find { it.controlId == control.id }
                val status = try {
                    ComplianceStatus.valueOf(finding?.status ?: "NONE")
                } catch (e: Exception) {
                    ComplianceStatus.NONE
                }
                control.copy(
                    status = status,
                    notes = finding?.notes ?: ""
                )
            }
        }
    }

    override suspend fun updateControlStatus(
        controlId: String,
        frameworkId: String,
        status: ComplianceStatus,
        notes: String
    ) {
        val entity = ComplianceFindingEntity(
            controlId = controlId,
            frameworkId = frameworkId,
            status = status.name,
            notes = notes
        )
        complianceDao.insertFinding(entity)
    }

    private fun getStaticControls(frameworkId: String): List<ComplianceControl> {
        return when (frameworkId) {
            "NIST" -> listOf(
                ComplianceControl("NIST-3.1.1", "NIST", "Authorized User Access", "Limit system access to authorized users, processes acting on behalf of authorized users, and devices (including other systems)."),
                ComplianceControl("NIST-3.1.2", "NIST", "Transaction/Function Limiting", "Limit system access to the types of transactions and functions that authorized users are permitted to execute."),
                ComplianceControl("NIST-3.5.3", "NIST", "Multifactor Authentication", "Use multifactor authentication for local and network access to privileged accounts and for network access to non-privileged accounts."),
                ComplianceControl("NIST-3.12.1", "NIST", "Security Control Assessment", "Periodically assess the security controls in organizational systems to determine if the controls are effective in their application."),
                ComplianceControl("NIST-3.12.3", "NIST", "Continuous Monitoring", "Monitor organizational systems on an ongoing basis and notify designated organizational officials when a predetermined set of security events occurs.")
            )
            "ISO27001" -> listOf(
                ComplianceControl("ISO-A.5.1", "ISO27001", "InfoSec Policies", "Information security policies shall be defined, approved by management, published and communicated to employees and relevant external parties."),
                ComplianceControl("ISO-A.9.2.1", "ISO27001", "User Registration", "A formal user registration and de-registration process shall be implemented to enable assignment of access rights."),
                ComplianceControl("ISO-A.9.4.2", "ISO27001", "Secure Log-on", "Where required by the access control policy, access to systems and applications shall be controlled by a secure log-on procedure."),
                ComplianceControl("ISO-A.12.4.1", "ISO27001", "Event Logging", "Event logs recording user activities, exceptions, faults and information security events shall be produced, kept and regularly reviewed."),
                ComplianceControl("ISO-A.18.1.1", "ISO27001", "Legal Identification", "All relevant legislative statutory, regulatory, contractual requirements and the organization’s approach to meet these requirements shall be explicitly identified.")
            )
            "SOC2" -> listOf(
                ComplianceControl("SOC2-CC1.1", "SOC2", "Integrity & Ethics", "The organization demonstrates a commitment to integrity and ethical values through directives, actions, and behavior."),
                ComplianceControl("SOC2-CC6.1", "SOC2", "Logical Access Restriction", "The entity restricts logical access to relevant information assets to authorized users, processes, and devices."),
                ComplianceControl("SOC2-CC6.7", "SOC2", "Data Transmission Control", "The entity restricts the transmission, movement, and removal of information to authorized users and processes, and protects it during transmission."),
                ComplianceControl("SOC2-CC7.1", "SOC2", "Vulnerability Management", "The entity identifies and manages system vulnerabilities to maintain the security of information and systems."),
                ComplianceControl("SOC2-CC7.2", "SOC2", "Security Monitoring", "The entity monitors its system for security events and vulnerabilities, and evaluates the impact of any identified events.")
            )
            else -> emptyList()
        }
    }
}
