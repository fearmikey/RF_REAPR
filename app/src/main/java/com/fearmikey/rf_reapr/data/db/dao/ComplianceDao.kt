package com.fearmikey.rf_reapr.data.db.dao

import androidx.room.*
import com.fearmikey.rf_reapr.data.db.entity.ComplianceFindingEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface ComplianceDao {
    @Query("SELECT * FROM compliance_findings WHERE frameworkId = :frameworkId")
    fun getFindingsForFramework(frameworkId: String): Flow<List<ComplianceFindingEntity>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertFinding(finding: ComplianceFindingEntity)

    @Query("SELECT * FROM compliance_findings WHERE controlId = :controlId")
    suspend fun getFindingByControlId(controlId: String): ComplianceFindingEntity?
}
