package com.batteryforensics.monitoring.di

import com.batteryforensics.monitoring.MonitoringRepository
import com.batteryforensics.monitoring.MonitoringRepositoryImpl
import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
abstract class MonitoringBindModule {
    @Binds
    @Singleton
    abstract fun bindMonitoringRepository(impl: MonitoringRepositoryImpl): MonitoringRepository
}
