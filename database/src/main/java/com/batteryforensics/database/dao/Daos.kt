package com.batteryforensics.database.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.batteryforensics.database.entity.MonitoringSampleEntity
import com.batteryforensics.database.entity.TimelineEventEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface MonitoringSampleDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(sample: MonitoringSampleEntity): Long

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAll(samples: List<MonitoringSampleEntity>)

    @Query("SELECT * FROM monitoring_samples ORDER BY timestampEpochMs DESC LIMIT :limit")
    fun observeLatest(limit: Int): Flow<List<MonitoringSampleEntity>>

    @Query(
        "SELECT * FROM monitoring_samples WHERE timestampEpochMs BETWEEN :startMs AND :endMs ORDER BY timestampEpochMs ASC",
    )
    suspend fun samplesBetween(startMs: Long, endMs: Long): List<MonitoringSampleEntity>

    @Query("SELECT COUNT(*) FROM monitoring_samples")
    suspend fun count(): Long

    @Query("DELETE FROM monitoring_samples WHERE timestampEpochMs < :beforeEpochMs")
    suspend fun deleteOlderThan(beforeEpochMs: Long): Int
}

@Dao
interface TimelineEventDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(event: TimelineEventEntity): Long

    @Query("SELECT * FROM timeline_events ORDER BY timestampEpochMs DESC LIMIT :limit")
    fun observeLatest(limit: Int): Flow<List<TimelineEventEntity>>
}
