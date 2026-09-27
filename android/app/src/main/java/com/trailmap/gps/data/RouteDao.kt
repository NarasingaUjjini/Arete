package com.trailmap.gps.data

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import kotlinx.coroutines.flow.Flow

@Dao
interface RouteDao {
    @Query("SELECT * FROM routes ORDER BY importedAt DESC")
    fun observeAll(): Flow<List<RouteEntity>>

    @Query("SELECT * FROM routes WHERE id = :id")
    suspend fun getById(id: Long): RouteEntity?

    @Query("SELECT * FROM routes WHERE id = :id")
    fun observeById(id: Long): Flow<RouteEntity?>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(route: RouteEntity): Long

    @Update
    suspend fun update(route: RouteEntity)

    @Query("DELETE FROM routes WHERE id = :id")
    suspend fun deleteById(id: Long)

    @Query("SELECT * FROM routes WHERE name LIKE '%' || :query || '%' ORDER BY importedAt DESC")
    fun search(query: String): Flow<List<RouteEntity>>
}
