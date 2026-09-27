package com.trailmap.gps.data

import androidx.room.Dao
import androidx.room.Entity
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.PrimaryKey
import androidx.room.Query
import androidx.room.Update
import kotlinx.coroutines.flow.Flow

enum class TripPackStatus {
    EMPTY,
    DOWNLOADING,
    PAUSED,
    READY,
    PARTIAL,
    STALE,
    FAILED
}

enum class TripPackAreaMode { CORRIDOR, CUSTOM }

enum class PackItemStatus { OK, MISSING, FAILED, STALE, ONLINE_ONLY, SKIPPED }

@Entity(tableName = "trip_packs")
data class TripPackEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val routeId: Long,
    val name: String,
    val minLon: Double,
    val minLat: Double,
    val maxLon: Double,
    val maxLat: Double,
    val corridorMeters: Double,
    val areaMode: String = TripPackAreaMode.CORRIDOR.name,
    val includeTopo: Boolean = true,
    val includeDem: Boolean = true,
    val includeHillshade: Boolean = false,
    val includeImagery: Boolean = false,
    val includeConditions: Boolean = false,
    val status: String = TripPackStatus.EMPTY.name,
    val estimatedBytes: Long = 0,
    val actualBytes: Long = 0,
    val createdAt: Long = System.currentTimeMillis(),
    val updatedAt: Long = System.currentTimeMillis(),
    val verifiedAt: Long = 0,
    val manifestJson: String = "[]",
    val error: String? = null,
    val maxZoom: Int = 14
) {
    fun packStatus(): TripPackStatus =
        runCatching { TripPackStatus.valueOf(status) }.getOrDefault(TripPackStatus.EMPTY)

    fun area(): TripPackAreaMode =
        runCatching { TripPackAreaMode.valueOf(areaMode) }.getOrDefault(TripPackAreaMode.CORRIDOR)
}

data class PackCheckItem(
    val id: String,
    val label: String,
    val required: Boolean,
    val status: PackItemStatus,
    val bytes: Long = 0,
    val message: String = ""
)

@Dao
interface TripPackDao {
    @Query("SELECT * FROM trip_packs ORDER BY updatedAt DESC")
    fun observeAll(): Flow<List<TripPackEntity>>

    @Query("SELECT * FROM trip_packs WHERE routeId = :routeId ORDER BY updatedAt DESC")
    fun observeForRoute(routeId: Long): Flow<List<TripPackEntity>>

    @Query("SELECT * FROM trip_packs WHERE routeId = :routeId ORDER BY updatedAt DESC LIMIT 1")
    suspend fun latestForRoute(routeId: Long): TripPackEntity?

    @Query("SELECT * FROM trip_packs WHERE id = :id")
    suspend fun getById(id: Long): TripPackEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(pack: TripPackEntity): Long

    @Update
    suspend fun update(pack: TripPackEntity)

    @Query("DELETE FROM trip_packs WHERE id = :id")
    suspend fun deleteById(id: Long)
}
