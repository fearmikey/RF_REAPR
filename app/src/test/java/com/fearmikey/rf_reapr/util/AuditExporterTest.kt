package com.fearmikey.rf_reapr.util

import com.fearmikey.rf_reapr.domain.model.ComplianceControl
import com.fearmikey.rf_reapr.domain.model.ComplianceStatus
import org.junit.Assert.assertTrue
import org.junit.Test

class AuditExporterTest {

    @Test
    fun testGenerateTextReport_containsExpectedFormatting() {
        // Arrange
        val frameworkId = "CIS_CONTROLS_V8"
        val controls = listOf(
            ComplianceControl(
                id = "1.1",
                frameworkId = frameworkId,
                name = "Establish and Maintain Detailed Enterprise Asset Inventory",
                description = "Maintain an accurate inventory.",
                status = ComplianceStatus.COMPLIANT,
                notes = "All assets accounted for"
            ),
            ComplianceControl(
                id = "1.2",
                frameworkId = frameworkId,
                name = "Address Unauthorized Assets",
                description = "Ensure unauthorized assets are removed.",
                status = ComplianceStatus.NON_COMPLIANT,
                notes = "Found rogue AP on guest network"
            )
        )

        // Act
        val report = AuditExporter.generateTextReport(frameworkId, controls)

        // Assert
        assertTrue("Report should contain framework ID", report.contains("Framework: CIS_CONTROLS_V8"))
        assertTrue("Report should contain compliant control", report.contains("1.1: Establish and Maintain Detailed Enterprise Asset Inventory [COMPLIANT]"))
        assertTrue("Report should contain non-compliant control", report.contains("1.2: Address Unauthorized Assets [NON-COMPLIANT]"))
        assertTrue("Report should contain auditor notes", report.contains("Auditor Notes: Found rogue AP on guest network"))
    }
}
