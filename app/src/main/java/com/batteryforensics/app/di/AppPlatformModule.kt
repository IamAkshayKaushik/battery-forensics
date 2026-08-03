package com.batteryforensics.app.di

import com.batteryforensics.shizuku.GracefulShizukuGateway
import com.batteryforensics.shizuku.ShizukuDiagnosticsCollector
import com.batteryforensics.shizuku.ShizukuGateway
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object AppPlatformModule {
    @Provides
    @Singleton
    fun provideShizukuGateway(): ShizukuGateway = GracefulShizukuGateway()

    @Provides
    @Singleton
    fun provideShizukuDiagnosticsCollector(gateway: ShizukuGateway): ShizukuDiagnosticsCollector =
        ShizukuDiagnosticsCollector(gateway)
}
