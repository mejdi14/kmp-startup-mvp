package com.mejdi14.kmpstartupmvp.shadow

import android.app.Application
import android.media.MediaPlayer
import android.media.MediaRecorder
import android.net.Uri
import android.os.Build
import android.provider.OpenableColumns
import android.util.Log
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableDoubleStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File

/**
 * Android adapter over the shared [ShadowStore]: forwards its state and owns native playback and
 * recording, like the Mac app's `Store` (`mac/Store.swift`).
 */
class ShadowViewModel(private val app: Application, val store: ShadowStore) : AndroidViewModel(app) {
    val state = store.state

    var studioOpen by mutableStateOf(false)
    var speed by mutableDoubleStateOf(1.0)
        private set
    var looping by mutableStateOf(false)
    var audioReady by mutableStateOf(false)
        private set
    var isPlaying by mutableStateOf(false)
        private set
    var position by mutableDoubleStateOf(0.0)
        private set
    var isTakePlaying by mutableStateOf(false)
        private set
    var recording by mutableStateOf(false)
        private set
    var recordSeconds by mutableDoubleStateOf(0.0)
        private set

    private var original: MediaPlayer? = null
    private var takePlayer: MediaPlayer? = null
    private var recorder: MediaRecorder? = null
    private var recordFile: File? = null
    private var pollJob: Job? = null
    private var recordJob: Job? = null
    private var autoStop: Job? = null
    private var playbackDone: (() -> Unit)? = null
    private var playOnce = false

    val phrase get() = state.value.phrase
    val locked get() = recording || state.value.locked

    init {
        viewModelScope.launch {
            var before = state.value
            state.collect { next ->
                if (before.lesson?.id != next.lesson?.id) {
                    stopPlayback(); original?.release(); original = null; audioReady = false
                    next.lesson?.id?.let(::loadAudio)
                    studioOpen = next.lesson != null
                }
                if (before.index != next.index || before.phrase?.start != next.phrase?.start || before.phrase?.end != next.phrase?.end) {
                    if (!recording) stopPlayback()
                    position = 0.0
                }
                before = next
            }
        }
        store.start()
    }

    private fun loadAudio(id: String) {
        viewModelScope.launch {
            try {
                val bytes = store.audio(store.lessonAudioPath(id))
                val player = withContext(Dispatchers.IO) {
                    val file = File(app.cacheDir, "lesson.wav").apply { writeBytes(bytes) }
                    MediaPlayer().apply { setDataSource(file.path); prepare() }
                }
                if (state.value.lesson?.id != id) { player.release(); return@launch }
                original = player; audioReady = true
            } catch (e: Exception) {
                Log.w(TAG, "Lesson audio failed", e)
                if (state.value.lesson?.id == id) store.show("The lesson audio couldn't be loaded. Open the session again.")
            }
        }
    }

    // Playback

    fun stopPlayback() {
        pollJob?.cancel(); pollJob = null; playbackDone = null
        original?.takeIf { it.isPlaying }?.pause()
        takePlayer?.release(); takePlayer = null
        isPlaying = false; isTakePlaying = false
    }

    fun changeSpeed(value: Double) {
        speed = value
        original?.takeIf { it.isPlaying }?.let { it.playbackParams = it.playbackParams.setSpeed(value.toFloat()) }
    }

    fun playOriginal(once: Boolean = false, rate: Double? = null, onDone: (() -> Unit)? = null) {
        val p = original ?: return
        val ph = phrase ?: return
        stopPlayback()
        seekTo(p, ph.start + if (once || position >= ph.length - 0.05) 0.0 else position)
        playbackDone = onDone; playOnce = once
        p.playbackParams = p.playbackParams.setSpeed((rate ?: speed).toFloat())
        if (!p.isPlaying) p.start()
        isPlaying = true
        pollJob = viewModelScope.launch {
            while (true) {
                delay(30)
                val current = phrase ?: return@launch
                val t = p.currentPosition / 1000.0
                position = (t - current.start).coerceIn(0.0, current.length)
                if (t >= current.end - 0.015 || !p.isPlaying) {
                    if (looping && !playOnce && !recording) seekTo(p, current.start)
                    else {
                        val done = playbackDone
                        stopPlayback(); position = current.length; done?.invoke(); return@launch
                    }
                }
            }
        }
    }

    private fun seekTo(p: MediaPlayer, seconds: Double) {
        val ms = (seconds * 1000).toLong()
        if (Build.VERSION.SDK_INT >= 26) p.seekTo(ms, MediaPlayer.SEEK_CLOSEST) else p.seekTo(ms.toInt())
    }

    fun seek(x: Double) {
        val ph = phrase ?: return
        position = x.coerceIn(0.0, ph.length)
        if (isPlaying) original?.let { seekTo(it, ph.start + position) }
    }

    fun togglePlay() {
        if (state.value.lesson == null) return
        if (isPlaying) {
            if (recording && state.value.mode == Mode.SHADOW) { stopRecording(); return }
            stopPlayback()
        } else playOriginal()
    }

    fun playTake() {
        val r = phrase?.latest ?: return
        stopPlayback()
        viewModelScope.launch {
            try {
                val bytes = store.audio(r.audio)
                val player = withContext(Dispatchers.IO) {
                    val file = File(app.cacheDir, "take.wav").apply { writeBytes(bytes) }
                    MediaPlayer().apply { setDataSource(file.path); prepare() }
                }
                player.setOnCompletionListener { isTakePlaying = false }
                takePlayer = player
                player.start(); isTakePlaying = true
            } catch (e: Exception) {
                Log.w(TAG, "Take playback failed", e)
                store.show("Your take couldn't be played. Try recording it again.")
            }
        }
    }

    fun toggleTake() = if (isTakePlaying) stopPlayback() else playTake()

    fun compareAB() = playOriginal(once = true, rate = phrase?.latest?.playbackSpeed ?: 1.0) { playTake() }

    // Navigation

    fun select(i: Int) { if (!locked) store.select(i) }
    fun next() = select(state.value.index + 1)
    fun previous() = select(state.value.index - 1)
    fun setMode(m: Mode) { if (!recording) store.setMode(m) }

    fun leaveLesson() {
        if (recording) endRecording()?.delete()
        stopPlayback()
        studioOpen = false
    }

    // Recording (the caller has already been granted RECORD_AUDIO)

    fun toggleRecording() {
        if (recording) { stopRecording(); return }
        if (state.value.lesson == null || locked) return
        stopPlayback()
        val file = File(app.cacheDir, "take-${System.currentTimeMillis()}.m4a")
        @Suppress("DEPRECATION")
        val r = if (Build.VERSION.SDK_INT >= 31) MediaRecorder(app) else MediaRecorder()
        try {
            r.setAudioSource(MediaRecorder.AudioSource.MIC)
            r.setOutputFormat(MediaRecorder.OutputFormat.MPEG_4)
            r.setAudioEncoder(MediaRecorder.AudioEncoder.AAC)
            r.setAudioChannels(1)
            r.setAudioSamplingRate(44_100)
            r.setAudioEncodingBitRate(96_000)
            r.setOutputFile(file.path)
            r.prepare(); r.start()
        } catch (e: Exception) {
            Log.w(TAG, "Recording failed to start", e)
            r.release(); file.delete()
            store.show("Microphone recording failed. Please try again.")
            return
        }
        recorder = r; recordFile = file
        recording = true; recordSeconds = 0.0
        val began = System.currentTimeMillis()
        recordJob = viewModelScope.launch { while (true) { recordSeconds = (System.currentTimeMillis() - began) / 1000.0; delay(200) } }
        autoStop = viewModelScope.launch { delay(34_000); stopRecording() }
        if (state.value.mode == Mode.SHADOW) {
            playOriginal(once = true) {
                autoStop?.cancel()
                autoStop = viewModelScope.launch { delay(500); stopRecording() }
            }
        }
    }

    fun stopRecording() {
        if (!recording) return
        val file = endRecording() ?: return
        val shadowSpeed = if (state.value.mode == Mode.SHADOW) speed else 1.0
        viewModelScope.launch {
            val bytes = withContext(Dispatchers.IO) { runCatching { file.readBytes() }.getOrNull().also { file.delete() } }
            if (bytes == null || bytes.isEmpty()) store.show("Microphone recording failed. Please try again.")
            else store.submitTake(bytes, "audio/mp4", shadowSpeed)
        }
    }

    /** Stops the recorder; returns the file, or null when nothing usable was recorded. */
    private fun endRecording(): File? {
        recording = false
        recordJob?.cancel(); autoStop?.cancel(); stopPlayback()
        val ok = runCatching { recorder?.stop() }.isSuccess
        recorder?.release(); recorder = null
        val file = recordFile; recordFile = null
        if (!ok) { file?.delete(); store.show("That take was too short. Record again and speak the whole phrase."); return null }
        return file
    }

    // Import

    fun importFile(uri: Uri) {
        viewModelScope.launch {
            val resolver = app.contentResolver
            val (name, size) = withContext(Dispatchers.IO) {
                resolver.query(uri, arrayOf(OpenableColumns.DISPLAY_NAME, OpenableColumns.SIZE), null, null, null)?.use { c ->
                    if (c.moveToFirst()) (c.getString(0) ?: "My practice") to (if (c.isNull(1)) 0L else c.getLong(1)) else null
                } ?: ("My practice" to 0L)
            }
            if (size > ShadowStore.MAX_UPLOAD) { store.show(ShadowStore.TOO_LARGE); return@launch }
            val bytes = withContext(Dispatchers.IO) { runCatching { resolver.openInputStream(uri)?.use { it.readBytes() } }.getOrNull() }
            if (bytes == null) store.show("That file couldn't be opened. Choose another audio or video file.")
            else store.importFile(name, bytes)
        }
    }

    override fun onCleared() {
        if (recording) endRecording()?.delete()
        stopPlayback(); original?.release(); original = null
    }

    companion object {
        private const val TAG = "ShadowStudio"

        fun factory(app: Application, store: ShadowStore): ViewModelProvider.Factory = viewModelFactory {
            initializer { ShadowViewModel(app, store) }
        }
    }
}
