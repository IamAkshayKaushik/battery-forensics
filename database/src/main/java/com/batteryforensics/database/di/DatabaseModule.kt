package com.batteryforensics.database.di

import android.content.Context
import androidx.room.Room
import com.batteryforensics.database.BatteryForensicsDatabase
import com.batteryforensics.database.dao.MonitoringSampleDao
import com.batteryforensics.database.dao.TimelineEventDao
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object DatabaseModule {
    @Provides
    @Singleton
    fun provideDatabase(@ApplicationContext context: Context): BatteryForensicsDatabase =
        Room.databaseBuilder(
            context,
            BatteryForensicsDatabase::class.java,
            "battery_forensics.db",
        )
            // v1→v2 adds monitoring columns. Destructive fallback is OK for debug builds;
            // see docs/MONITORING.md. Production should ship a typed Migration when schema stabilizes.
            .fallbackToDestructiveMigration()
            .build()

    @Provides
    fun provideMonitoringSampleDao(db: BatteryForensicsDatabase): MonitoringSampleDao =
        db.monitoringSampleDao()

    @Provides
    fun provideTimelineEventDao(db: BatteryForensicsDatabase): TimelineEventDao =
        db.timelineEventDao()
}
