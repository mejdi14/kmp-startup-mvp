package com.mejdi14.kmpstartupmvp.shadow

import com.russhwolf.settings.Settings
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/**
 * Library, lesson, phrase and job state for the mobile apps, the shared counterpart of the Mac
 * app's `Store`. Playback and recording stay native on each platform; this owns everything that
 * talks to the engine. Actions launch in [scope] and report failures through [ShadowState.toast].
 */
class ShadowStore(private val api: ShadowApi, private val settings: Settings, private val scope: CoroutineScope) {
    private val _state = MutableStateFlow(ShadowState())
    val state: StateFlow<ShadowState> = _state.asStateFlow()
    private val current get() = _state.value
    private var toastJob: Job? = null
    private var voiceJob: Job? = null

    fun start() {
        scope.launch {
            _state.update { it.copy(connection = Connection.CONNECTING, connectionError = null) }
            try {
                api.connect()
                val library = api.library()
                _state.update { it.copy(connection = Connection.READY, library = library) }
                settings.getStringOrNull(LAST_LESSON)?.takeIf { id -> library.any { it.id == id } }?.let { loadLesson(it) }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                _state.update { it.copy(connection = Connection.FAILED, connectionError = message(e)) }
            }
        }
    }

    fun refreshLibrary() = guarded { reloadLibrary() }

    fun openLesson(id: String) = guarded { loadLesson(id) }

    fun select(index: Int) {
        val s = current
        if (s.locked || index !in 0 until s.phraseCount || index == s.index) return
        _state.update { it.copy(index = index) }
        phraseChanged()
    }

    fun next() = select(current.index + 1)
    fun previous() = select(current.index - 1)

    fun setMode(mode: Mode) = _state.update { it.copy(mode = mode, concealed = mode == Mode.RECALL) }

    fun setConcealed(concealed: Boolean) = _state.update { it.copy(concealed = concealed) }

    fun dismissToast() = _state.update { it.copy(toast = null) }

    fun show(message: String) {
        _state.update { it.copy(toast = message) }
        toastJob?.cancel()
        toastJob = scope.launch { delay(6_500); _state.update { if (it.toast == message) it.copy(toast = null) else it } }
    }

    fun importLink(url: String) {
        val link = url.trim()
        if (link.isEmpty()) return show("Paste a YouTube link or choose a file.")
        runImport { api.importLink(link) }
    }

    fun importFile(name: String, bytes: ByteArray) {
        if (bytes.size > MAX_UPLOAD) return show(TOO_LARGE)
        runImport { api.upload(name, bytes) }
    }

    /** Sends a recorded take of the current phrase; [speed] is the playback speed used while shadowing. */
    fun submitTake(bytes: ByteArray, contentType: String, speed: Double) {
        val lesson = current.lesson ?: return
        val index = current.index
        guarded {
            try {
                _state.update { it.copy(busy = Busy("Reviewing your take", "Listening to your recording…")) }
                waitJob(api.record(lesson.id, index, speed, bytes, contentType), "Reviewing your take")
                loadLesson(lesson.id, keepIndex = true)
                show("Your feedback is ready. Listen back, then choose one detail to practise.")
            } finally {
                _state.update { it.copy(busy = null) }
            }
        }
    }

    /** Saves the edited current phrase. Throws a [ShadowError] with a message for the edit form. */
    @Throws(Throwable::class)
    suspend fun savePhrase(text: String, start: Double, end: Double) {
        val lesson = current.lesson ?: return
        try {
            val updated = api.editPhrase(lesson.id, current.index, text, start, end)
            _state.update { it.copy(lesson = updated) }
            phraseChanged()
            show("Phrase updated. Record a new take to compare with this version.")
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            throw ShadowError(message(e))
        }
    }

    fun deleteLesson() {
        val lesson = current.lesson ?: return
        guarded {
            api.delete(lesson.id)
            voiceJob?.cancel()
            settings.remove(LAST_LESSON)
            _state.update { it.copy(lesson = null, index = 0, voice = null, voiceFailed = false) }
            reloadLibrary()
        }
    }

    /** Raw bytes of an engine audio path: the lesson audio ([lessonAudioPath]) or a take's `audio`. */
    @Throws(Throwable::class)
    suspend fun audio(path: String): ByteArray = api.bytes(path)

    fun lessonAudioPath(id: String) = "/api/audio/$id"

    private fun runImport(start: suspend () -> String) = guarded {
        try {
            _state.update { it.copy(busy = Busy("Preparing your practice", "Preparing audio…")) }
            val lessonId = waitJob(start(), "Finding your phrases")
            loadLesson(lessonId)
            show("Your practice session is ready. Start by listening to the first phrase.")
        } finally {
            _state.update { it.copy(busy = null) }
        }
    }

    private suspend fun waitJob(id: String, title: String): String {
        _state.update { it.copy(busy = Busy(title, it.busy?.message ?: "Preparing audio…")) }
        while (true) {
            val job = api.job(id)
            _state.update { it.copy(busy = Busy(title, job.message ?: "Finishing up…")) }
            if (job.state == "error") throw ShadowError(job.message ?: "Processing failed. Please try again.")
            if (job.state == "done" && job.lessonId != null) return job.lessonId
            delay(900)
        }
    }

    private suspend fun reloadLibrary() {
        val library = api.library()
        _state.update { it.copy(library = library) }
    }

    private suspend fun loadLesson(id: String, keepIndex: Boolean = false) {
        val lesson = api.lesson(id)
        _state.update {
            val index = if (keepIndex && it.lesson?.id == id) it.index.coerceAtMost(lesson.phrases.size - 1) else 0
            it.copy(lesson = lesson, index = index.coerceAtLeast(0))
        }
        settings.putString(LAST_LESSON, id)
        phraseChanged()
        reloadLibrary()
    }

    private fun phraseChanged() {
        _state.update { it.copy(voice = null, voiceFailed = false, concealed = it.mode == Mode.RECALL) }
        val s = current
        val lesson = s.lesson ?: return
        val index = s.index
        voiceJob?.cancel()
        voiceJob = scope.launch {
            val map = try {
                val phrase = s.phrase ?: return@launch
                buildVoiceMap(api.voice(lesson.id, index), phrase)
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                null
            }
            _state.update {
                if (it.lesson?.id != lesson.id || it.index != index) it
                else if (map == null) it.copy(voiceFailed = true) else it.copy(voice = map)
            }
        }
    }

    private fun guarded(body: suspend () -> Unit) {
        scope.launch {
            try { body() }
            catch (e: CancellationException) { throw e }
            catch (e: Exception) { show(message(e)) }
        }
    }

    private fun message(e: Exception) = (e as? ShadowError)?.message ?: "Something went wrong. Please try again."

    companion object {
        /** Keep in sync with the 150 MB upload limit in `app.py`. */
        const val MAX_UPLOAD = 150 * 1024 * 1024
        const val TOO_LARGE = "Choose a file smaller than 150 MB."
        private const val LAST_LESSON = "shadow-lesson"
    }
}
