package com.example.data

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import kotlinx.coroutines.flow.Flow

@Dao
interface RecordingProjectDao {

    @Query("SELECT * FROM recording_projects ORDER BY createdAt DESC")
    fun getAllProjects(): Flow<List<RecordingProject>>

    @Query("SELECT * FROM recording_projects WHERE id = :id LIMIT 1")
    suspend fun getProjectById(id: Long): RecordingProject?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(project: RecordingProject): Long

    @Update
    suspend fun update(project: RecordingProject)

    @Delete
    suspend fun delete(project: RecordingProject)
}
