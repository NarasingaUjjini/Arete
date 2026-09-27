package com.trailmap.gps.data

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.TypeConverter
import androidx.room.TypeConverters
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

class Converters {
    @TypeConverter
    fun fromSource(value: RouteSource): String = value.name

    @TypeConverter
    fun toSource(value: String): RouteSource = RouteSource.valueOf(value)
}

@Database(entities = [RouteEntity::class, TripPackEntity::class], version = 2, exportSchema = false)
@TypeConverters(Converters::class)
abstract class AppDatabase : RoomDatabase() {
    abstract fun routeDao(): RouteDao
    abstract fun tripPackDao(): TripPackDao

    companion object {
        @Volatile
        private var instance: AppDatabase? = null

        private val MIGRATION_1_2 = object : Migration(1, 2) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL(
                    """
                    CREATE TABLE IF NOT EXISTS trip_packs (
                        id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                        routeId INTEGER NOT NULL,
                        name TEXT NOT NULL,
                        minLon REAL NOT NULL,
                        minLat REAL NOT NULL,
                        maxLon REAL NOT NULL,
                        maxLat REAL NOT NULL,
                        corridorMeters REAL NOT NULL,
                        areaMode TEXT NOT NULL,
                        includeTopo INTEGER NOT NULL,
                        includeDem INTEGER NOT NULL,
                        includeHillshade INTEGER NOT NULL,
                        includeImagery INTEGER NOT NULL,
                        includeConditions INTEGER NOT NULL,
                        status TEXT NOT NULL,
                        estimatedBytes INTEGER NOT NULL,
                        actualBytes INTEGER NOT NULL,
                        createdAt INTEGER NOT NULL,
                        updatedAt INTEGER NOT NULL,
                        verifiedAt INTEGER NOT NULL,
                        manifestJson TEXT NOT NULL,
                        error TEXT,
                        maxZoom INTEGER NOT NULL
                    )
                    """.trimIndent()
                )
            }
        }

        fun get(context: Context): AppDatabase =
            instance ?: synchronized(this) {
                instance ?: Room.databaseBuilder(
                    context.applicationContext,
                    AppDatabase::class.java,
                    "trailmap.db"
                ).addMigrations(MIGRATION_1_2).build().also { instance = it }
            }
    }
}
