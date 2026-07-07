package com.example.rf_reapr.domain.model

enum class ComplianceFramework(val id: String, val title: String, val description: String) {
    NIST("NIST", "NIST SP 800-171", "Protecting Controlled Unclassified Information in Nonfederal Systems and Organizations."),
    ISO27001("ISO27001", "ISO/IEC 27001", "Information security management systems requirements."),
    SOC2("SOC2", "SOC 2 Type II", "Trust Services Criteria for Security, Availability, Processing Integrity, Confidentiality, and Privacy.")
}
