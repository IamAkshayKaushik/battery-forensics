package com.batteryforensics.database

import androidx.room.Database
import androidx.room.RoomDatabase
import androidx.room.TypeConverters
import com.batteryforensics.database.dao.MonitoringSampleDao
import com.batteryforensics.database.dao.TimelineEventDao
import com.batteryforensics.database.entity.MonitoringSampleEntity
import com.batteryforensics.database.entity.TimelineEventEntity

@Database(
    entities = [
        MonitoringSampleEntity::class,
        TimelineEventEntity::class,
    ],
    version = 3,
    exportSchema = true,
)
@TypeConverters(Converters::class)
abstract class BatteryForensicsDatabase : RoomDatabase() {
    abstract fun monitoringSampleDao(): MonitoringSampleDao
    abstract fun timelineEventDao(): TimelineEventDao
}
