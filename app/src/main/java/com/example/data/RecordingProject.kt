package com.example.data

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "recording_projects")
data class RecordingProject(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    val title: String,
    val createdAt: Long = System.currentTimeMillis(),
    val durationMs: Long,
    val vocalWetPath: String,
    val vocalDryPath: String,
    val beatTitle: String? = null,
    val beatGenre: String? = null,
    val beatFilePath: String? = null,
    val keyRoot: String = "C",
    val scaleName: String = "Major",
    val autoTuneMode: String = "Hard",
    val mixedExportPath: String? = null
)
