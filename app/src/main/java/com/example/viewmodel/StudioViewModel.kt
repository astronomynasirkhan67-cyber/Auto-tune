package com.example.viewmodel

import android.app.Application
import android.content.Context
import android.content.Intent
import android.media.MediaPlayer
import android.net.Uri
import android.util.Log
import androidx.core.content.FileProvider
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.audio.AudioDecoder
import com.example.audio.LiveAudioEngine
import com.example.audio.RecordingState
import com.example.audio.WavAudioUtil
import com.example.beats.BeatGenre
import com.example.beats.BeatSynthesizer
import com.example.beats.StudioBeat
import com.example.data.AppDatabase
import com.example.data.RecordingProject
import com.example.dsp.MusicalScale
import com.example.dsp.ScaleType
import com.example.dsp.StudioPreset
import com.example.dsp.VocalPresets
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File

enum class StudioTab(val title: String) {
    STUDIO("Studio"),
    EFFECTS("FX Rack"),
    BEATS("Beats"),
    PROJECTS("Projects")
}

data class PlaybackState(
    val playingProjectId: Long? = null,
    val isPlaying: Boolean = false,
    val currentPositionMs: Int = 0,
    val durationMs: Int = 0
)

class StudioViewModel(application: Application) : AndroidViewModel(application) {

    private val context: Context get() = getApplication<Application>().applicationContext
    private val database = AppDatabase.getDatabase(context)
    private val projectDao = database.recordingProjectDao()

    val audioEngine = LiveAudioEngine(context)

    // Navigation Tab
    private val _currentTab = MutableStateFlow(StudioTab.STUDIO)
    val currentTab: StateFlow<StudioTab> = _currentTab.asStateFlow()

    fun setTab(tab: StudioTab) {
        _currentTab.value = tab
    }

    // Beats Library
    private val _beatsList = MutableStateFlow<List<StudioBeat>>(emptyList())
    val beatsList: StateFlow<List<StudioBeat>> = _beatsList.asStateFlow()

    private val _selectedGenre = MutableStateFlow(BeatGenre.ALL)
    val selectedGenre: StateFlow<BeatGenre> = _selectedGenre.asStateFlow()

    private val _selectedBeat = MutableStateFlow<StudioBeat?>(null)
    val selectedBeat: StateFlow<StudioBeat?> = _selectedBeat.asStateFlow()

    private val _previewPlayingBeatId = MutableStateFlow<String?>(null)
    val previewPlayingBeatId: StateFlow<String?> = _previewPlayingBeatId.asStateFlow()

    private var previewMediaPlayer: MediaPlayer? = null

    // Project Playback
    private val _playbackState = MutableStateFlow(PlaybackState())
    val playbackState: StateFlow<PlaybackState> = _playbackState.asStateFlow()
    private var projectMediaPlayer: MediaPlayer? = null

    // UI Status / Notification Message
    private val _statusMessage = MutableStateFlow<String?>(null)
    val statusMessage: StateFlow<String?> = _statusMessage.asStateFlow()

    // Active Preset
    private val _activePresetName = MutableStateFlow("Hard Trap")
    val activePresetName: StateFlow<String> = _activePresetName.asStateFlow()

    // Database Projects List
    val savedProjects: StateFlow<List<RecordingProject>> = projectDao.getAllProjects()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    // Auto-Tune Reactive State for UI
    private val _isHardAutoTune = MutableStateFlow(audioEngine.isHardAutoTune)
    val isHardAutoTune: StateFlow<Boolean> = _isHardAutoTune.asStateFlow()

    private val _rootKeyIndex = MutableStateFlow(audioEngine.rootKeyIndex)
    val rootKeyIndex: StateFlow<Int> = _rootKeyIndex.asStateFlow()

    private val _scaleType = MutableStateFlow(audioEngine.scaleType)
    val scaleType: StateFlow<ScaleType> = _scaleType.asStateFlow()

    private val _correctionStrength = MutableStateFlow(audioEngine.correctionStrength)
    val correctionStrength: StateFlow<Float> = _correctionStrength.asStateFlow()

    private val _pitchSpeed = MutableStateFlow(audioEngine.pitchSpeed)
    val pitchSpeed: StateFlow<Float> = _pitchSpeed.asStateFlow()

    private val _vocalVolume = MutableStateFlow(audioEngine.vocalVolume)
    val vocalVolume: StateFlow<Float> = _vocalVolume.asStateFlow()

    private val _beatVolume = MutableStateFlow(audioEngine.beatVolume)
    val beatVolume: StateFlow<Float> = _beatVolume.asStateFlow()

    private val _isLiveMonitorEnabled = MutableStateFlow(audioEngine.isLiveMonitorEnabled)
    val isLiveMonitorEnabled: StateFlow<Boolean> = _isLiveMonitorEnabled.asStateFlow()

    init {
        // Load default beats
        loadBeats()
        // Apply default preset
        applyPreset(VocalPresets.ALL_PRESETS.first())
    }

    private fun loadBeats() {
        viewModelScope.launch(Dispatchers.IO) {
            val builtins = BeatSynthesizer.getBuiltinBeats()
            _beatsList.value = builtins
            if (_selectedBeat.value == null && builtins.isNotEmpty()) {
                _selectedBeat.value = builtins.first()
            }
        }
    }

    fun setSelectedGenre(genre: BeatGenre) {
        _selectedGenre.value = genre
    }

    fun selectBeat(beat: StudioBeat) {
        _selectedBeat.value = beat
        stopBeatPreview()
        _statusMessage.value = "Selected beat: ${beat.title}"

        // Match key and scale if available
        val rootIdx = MusicalScale.ROOT_NOTES.indexOf(beat.rootKey)
        if (rootIdx >= 0) {
            setRootKey(rootIdx)
        }
        if (beat.scaleName.contains("Minor", ignoreCase = true)) {
            setScaleType(ScaleType.MINOR)
        } else if (beat.scaleName.contains("Major", ignoreCase = true)) {
            setScaleType(ScaleType.MAJOR)
        }
    }

    fun toggleBeatPreview(beat: StudioBeat) {
        if (_previewPlayingBeatId.value == beat.id) {
            stopBeatPreview()
        } else {
            viewModelScope.launch(Dispatchers.IO) {
                try {
                    val file = if (beat.isCustomImport && beat.customFilePath != null) {
                        File(beat.customFilePath)
                    } else {
                        BeatSynthesizer.getOrGenerateBeatFile(context, beat)
                    }

                    withContext(Dispatchers.Main) {
                        stopBeatPreview()
                        previewMediaPlayer = MediaPlayer().apply {
                            setDataSource(file.absolutePath)
                            isLooping = true
                            prepare()
                            start()
                        }
                        _previewPlayingBeatId.value = beat.id
                    }
                } catch (e: Exception) {
                    Log.e("StudioViewModel", "Error previewing beat", e)
                    withContext(Dispatchers.Main) {
                        _statusMessage.value = "Error playing beat preview"
                    }
                }
            }
        }
    }

    fun stopBeatPreview() {
        try {
            previewMediaPlayer?.stop()
            previewMediaPlayer?.release()
        } catch (ignored: Exception) {}
        previewMediaPlayer = null
        _previewPlayingBeatId.value = null
    }

    fun importCustomAudioBeat(uri: Uri) {
        viewModelScope.launch(Dispatchers.IO) {
            val customDir = File(context.filesDir, "custom_beats").apply { mkdirs() }
            val timestamp = System.currentTimeMillis()
            val destWav = File(customDir, "import_$timestamp.wav")

            val success = AudioDecoder.decodeToWav(context, uri, destWav)
            if (success && destWav.exists() && destWav.length() > 100) {
                val newBeat = StudioBeat(
                    id = "custom_$timestamp",
                    title = "Imported Beat ${beatsList.value.count { it.isCustomImport } + 1}",
                    genre = BeatGenre.ALL,
                    bpm = 120,
                    rootKey = "C",
                    scaleName = "Chromatic",
                    durationSeconds = 60,
                    description = "Custom imported track from device",
                    isCustomImport = true,
                    customFilePath = destWav.absolutePath
                )
                withContext(Dispatchers.Main) {
                    _beatsList.value = listOf(newBeat) + _beatsList.value
                    selectBeat(newBeat)
                    _statusMessage.value = "Imported custom beat successfully!"
                }
            } else {
                withContext(Dispatchers.Main) {
                    _statusMessage.value = "Failed to decode audio file"
                }
            }
        }
    }

    // ==========================================
    // AUTOTUNE & EFFECT CONTROLS
    // ==========================================

    fun setAutoTuneMode(isHard: Boolean) {
        audioEngine.isHardAutoTune = isHard
        _isHardAutoTune.value = isHard
        _activePresetName.value = "Custom"
    }

    fun setRootKey(index: Int) {
        audioEngine.rootKeyIndex = index
        _rootKeyIndex.value = index
    }

    fun setScaleType(scale: ScaleType) {
        audioEngine.scaleType = scale
        _scaleType.value = scale
    }

    fun setCorrectionStrength(strength: Float) {
        val s = strength.coerceIn(0.0f, 1.0f)
        audioEngine.correctionStrength = s
        _correctionStrength.value = s
        _activePresetName.value = "Custom"
    }

    fun setPitchSpeed(speed: Float) {
        val sp = speed.coerceIn(0.0f, 1.0f)
        audioEngine.pitchSpeed = sp
        _pitchSpeed.value = sp
        _activePresetName.value = "Custom"
    }

    fun setManualPitchShift(semitones: Int) {
        audioEngine.manualPitchShiftSemitones = semitones.coerceIn(-12, 12)
    }

    fun setVocalVolume(volume: Float) {
        val v = volume.coerceIn(0.0f, 2.0f)
        audioEngine.vocalVolume = v
        _vocalVolume.value = v
    }

    fun setBeatVolume(volume: Float) {
        val v = volume.coerceIn(0.0f, 2.0f)
        audioEngine.setBeatPlayerVolume(v)
        _beatVolume.value = v
    }

    fun toggleLiveMonitor() {
        val next = !_isLiveMonitorEnabled.value
        audioEngine.isLiveMonitorEnabled = next
        _isLiveMonitorEnabled.value = next
        if (next) {
            _statusMessage.value = "Live monitor ON (headphones recommended)"
        } else {
            _statusMessage.value = "Live monitor OFF"
        }
    }

    fun applyPreset(preset: StudioPreset) {
        audioEngine.applyPreset(preset)
        _isHardAutoTune.value = preset.isHardAutoTune
        _correctionStrength.value = preset.correctionStrength
        _pitchSpeed.value = preset.pitchSpeed
        _activePresetName.value = preset.name
        _statusMessage.value = "Applied preset: ${preset.name}"
    }

    // ==========================================
    // RECORDING WORKFLOW
    // ==========================================

    fun startRecording() {
        stopBeatPreview()
        stopProjectPlayback()

        viewModelScope.launch(Dispatchers.IO) {
            var beatFileToPlay: File? = null
            val beat = _selectedBeat.value
            if (beat != null) {
                beatFileToPlay = if (beat.isCustomImport && beat.customFilePath != null) {
                    File(beat.customFilePath)
                } else {
                    BeatSynthesizer.getOrGenerateBeatFile(context, beat)
                }
            }

            val started = audioEngine.startRecording(beatFileToPlay)
            withContext(Dispatchers.Main) {
                if (started) {
                    _statusMessage.value = "Recording started with Auto-Tune!"
                } else {
                    _statusMessage.value = "Failed to start audio recording. Check mic permission."
                }
            }
        }
    }

    fun pauseRecording() {
        audioEngine.pauseRecording()
        _statusMessage.value = "Recording paused"
    }

    fun resumeRecording() {
        audioEngine.resumeRecording()
        _statusMessage.value = "Recording resumed"
    }

    fun stopRecording() {
        val duration = audioEngine.audioMetrics.value.recordingDurationMs
        val (wetFile, dryFile) = audioEngine.stopRecording()

        if (wetFile != null && wetFile.exists() && duration > 500) {
            viewModelScope.launch(Dispatchers.IO) {
                val beat = _selectedBeat.value
                val beatFile = if (beat != null) {
                    if (beat.isCustomImport && beat.customFilePath != null) File(beat.customFilePath)
                    else BeatSynthesizer.getOrGenerateBeatFile(context, beat)
                } else null

                val projectCount = savedProjects.value.size + 1
                val newProject = RecordingProject(
                    title = "Session Track #$projectCount",
                    durationMs = duration,
                    vocalWetPath = wetFile.absolutePath,
                    vocalDryPath = dryFile?.absolutePath ?: wetFile.absolutePath,
                    beatTitle = beat?.title,
                    beatGenre = beat?.genre?.displayName,
                    beatFilePath = beatFile?.absolutePath,
                    keyRoot = MusicalScale.ROOT_NOTES[rootKeyIndex.value],
                    scaleName = scaleType.value.displayName,
                    autoTuneMode = if (isHardAutoTune.value) "Hard" else "Natural"
                )

                projectDao.insert(newProject)
                withContext(Dispatchers.Main) {
                    _statusMessage.value = "Project saved to library!"
                    _currentTab.value = StudioTab.PROJECTS
                }
            }
        } else {
            _statusMessage.value = "Recording cancelled or too short"
        }
    }

    // ==========================================
    // PROJECT PLAYBACK & EXPORT
    // ==========================================

    fun toggleProjectPlayback(project: RecordingProject) {
        if (_playbackState.value.playingProjectId == project.id && _playbackState.value.isPlaying) {
            stopProjectPlayback()
        } else {
            stopProjectPlayback()
            stopBeatPreview()

            val filePath = project.mixedExportPath ?: project.vocalWetPath
            val file = File(filePath)
            if (!file.exists()) {
                _statusMessage.value = "Audio file not found"
                return
            }

            try {
                val mp = MediaPlayer().apply {
                    setDataSource(file.absolutePath)
                    prepare()
                    start()
                }
                projectMediaPlayer = mp

                _playbackState.value = PlaybackState(
                    playingProjectId = project.id,
                    isPlaying = true,
                    currentPositionMs = 0,
                    durationMs = mp.duration
                )

                mp.setOnCompletionListener {
                    stopProjectPlayback()
                }
            } catch (e: Exception) {
                Log.e("StudioViewModel", "Error playing project", e)
                _statusMessage.value = "Error playing recording"
            }
        }
    }

    fun stopProjectPlayback() {
        try {
            projectMediaPlayer?.stop()
            projectMediaPlayer?.release()
        } catch (ignored: Exception) {}
        projectMediaPlayer = null
        _playbackState.value = PlaybackState()
    }

    fun exportProjectMix(project: RecordingProject) {
        viewModelScope.launch(Dispatchers.IO) {
            _statusMessage.value = "Mixing master WAV track..."
            val vocalFile = File(project.vocalWetPath)
            if (!vocalFile.exists()) {
                withContext(Dispatchers.Main) {
                    _statusMessage.value = "Original vocal file missing"
                }
                return@launch
            }

            val vocalShorts = WavAudioUtil.readWavFileToShorts(vocalFile)
            val beatShorts = if (project.beatFilePath != null && File(project.beatFilePath).exists()) {
                WavAudioUtil.readWavFileToShorts(File(project.beatFilePath))
            } else {
                ShortArray(0)
            }

            val exportDir = File(context.filesDir, "exports").apply { mkdirs() }
            val sanitizedTitle = project.title.replace(Regex("[^a-zA-Z0-9_-]"), "_")
            val exportFile = File(exportDir, "${sanitizedTitle}_Master.wav")

            val mixedStereo = WavAudioUtil.mixVocalAndBeatToStereo(
                vocalPcm = vocalShorts,
                vocalChannels = 1,
                vocalVolume = vocalVolume.value,
                beatPcm = beatShorts,
                beatChannels = 1,
                beatVolume = beatVolume.value,
                masterVolume = 1.0f
            )

            WavAudioUtil.saveShortsToWav(
                shorts = mixedStereo,
                outputFile = exportFile,
                sampleRate = 44100,
                channels = 2 // Pristine Stereo master
            )

            // Update project entity
            val updated = project.copy(mixedExportPath = exportFile.absolutePath)
            projectDao.update(updated)

            withContext(Dispatchers.Main) {
                _statusMessage.value = "Master WAV exported: ${exportFile.name}"
            }
        }
    }

    fun shareProject(project: RecordingProject) {
        val targetPath = project.mixedExportPath ?: project.vocalWetPath
        val file = File(targetPath)
        if (!file.exists()) {
            _statusMessage.value = "File not found for sharing"
            return
        }

        try {
            val uri = FileProvider.getUriForFile(
                context,
                "${context.packageName}.fileprovider",
                file
            )

            val shareIntent = Intent(Intent.ACTION_SEND).apply {
                type = "audio/wav"
                putExtra(Intent.EXTRA_STREAM, uri)
                putExtra(Intent.EXTRA_SUBJECT, "My Song - ${project.title}")
                putExtra(Intent.EXTRA_TEXT, "Listen to my song '${project.title}' recorded with AutoTune Studio Pro!")
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }

            val chooser = Intent.createChooser(shareIntent, "Share Track").apply {
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            context.startActivity(chooser)
        } catch (e: Exception) {
            Log.e("StudioViewModel", "Error sharing song", e)
            _statusMessage.value = "Error sharing file"
        }
    }

    fun renameProject(project: RecordingProject, newTitle: String) {
        if (newTitle.isBlank()) return
        viewModelScope.launch(Dispatchers.IO) {
            projectDao.update(project.copy(title = newTitle.trim()))
            withContext(Dispatchers.Main) {
                _statusMessage.value = "Renamed to '$newTitle'"
            }
        }
    }

    fun deleteProject(project: RecordingProject) {
        stopProjectPlayback()
        viewModelScope.launch(Dispatchers.IO) {
            // Delete audio files
            try { File(project.vocalWetPath).delete() } catch (ignored: Exception) {}
            try { File(project.vocalDryPath).delete() } catch (ignored: Exception) {}
            project.mixedExportPath?.let {
                try { File(it).delete() } catch (ignored: Exception) {}
            }
            projectDao.delete(project)
            withContext(Dispatchers.Main) {
                _statusMessage.value = "Project deleted"
            }
        }
    }

    fun clearStatusMessage() {
        _statusMessage.value = null
    }

    override fun onCleared() {
        super.onCleared()
        audioEngine.stopEngine()
        stopBeatPreview()
        stopProjectPlayback()
    }
}
