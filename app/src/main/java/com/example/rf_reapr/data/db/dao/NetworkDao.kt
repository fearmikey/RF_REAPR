package com.example.rf_reapr.data.db.dao

import androidx.room.*
import com.example.rf_reapr.data.db.entity.NetworkNodeEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface NetworkDao {
    @Query("SELECT * FROM network_nodes ORDER BY lastSeen DESC")
    fun getAllNodes(): Flow<List<NetworkNodeEntity>>

    @Query("SELECT * FROM network_nodes WHERE ipAddress = :ip")
    suspend fun getNodeByIp(ip: String): NetworkNodeEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertNode(node: NetworkNodeEntity)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertNodes(nodes: List<NetworkNodeEntity>)

    @Update
    suspend fun updateNode(node: NetworkNodeEntity)

    @Delete
    suspend fun deleteNode(node: NetworkNodeEntity)

    @Query("DELETE FROM network_nodes")
    suspend fun deleteAllNodes()
}
