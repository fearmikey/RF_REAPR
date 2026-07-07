package com.example.rf_reapr.domain.model

enum class ComplianceStatus {
    NONE,
    COMPLIANT,
    NON_COMPLIANT
}

data class ComplianceControl(
    val id: String,
    val frameworkId: String,
    val name: String,
    val description: String,
    val status: ComplianceStatus = ComplianceStatus.NONE,
    val notes: String = ""
)
