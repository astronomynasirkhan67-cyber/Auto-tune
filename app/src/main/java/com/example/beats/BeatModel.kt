package com.example.beats

enum class BeatGenre(val displayName: String) {
    ALL("All Genres"),
    HIP_HOP("Hip-Hop"),
    RAP("Rap"),
    PUNJABI("Punjabi"),
    TRAP("Trap"),
    LO_FI("Lo-Fi"),
    ROMANTIC("Romantic"),
    SAD("Sad")
}

data class StudioBeat(
    val id: String,
    val title: String,
    val genre: BeatGenre,
    val bpm: Int,
    val rootKey: String, // e.g. "C", "F#", "A"
    val scaleName: String, // e.g. "Minor", "Major"
    val durationSeconds: Int,
    val description: String,
    val isCustomImport: Boolean = false,
    val customFilePath: String? = null
)
