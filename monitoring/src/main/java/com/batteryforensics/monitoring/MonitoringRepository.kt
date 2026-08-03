package com.batteryforensics.monitoring

import com.batteryforensics.core.model.MonitoringSample
import com.batteryforensics.database.dao.MonitoringSampleDao
import com.batteryforensics.database.mapper.toDomain
import com.batteryforensics.database.mapper.toEntity
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import javax.inject.Inject
import javax.inject.Singleton

interface MonitoringRepository {
    suspend fun persist(sample: MonitoringSample): Long
    fun observeLatest(limit: Int = 50): Flow<List<MonitoringSample>>
    suspend fun samplesBetween(startMs: Long, endMs: Long): List<MonitoringSample>
    suspend fun sampleCount(): Long
}

@Singleton
class MonitoringRepositoryImpl @Inject constructor(
    private val dao: MonitoringSampleDao,
    private val collector: SampleCollector,
) : MonitoringRepository {
    suspend fun captureAndPersist(): MonitoringSample {
        val sample = collector.collect()
        dao.insert(sample.toEntity())
        return sample
    }

    override suspend fun persist(sample: MonitoringSample): Long = dao.insert(sample.toEntity())

    override fun observeLatest(limit: Int): Flow<List<MonitoringSample>> =
        dao.observeLatest(limit).map { list -> list.map { it.toDomain() } }

    override suspend fun samplesBetween(startMs: Long, endMs: Long): List<MonitoringSample> =
        dao.samplesBetween(startMs, endMs).map { it.toDomain() }

    override suspend fun sampleCount(): Long = dao.count()
}
