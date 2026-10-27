package com.mejdi14.kmpstartupmvp.shadow

import kotlinx.serialization.Serializable

/** Response shapes of Shadow Studio's local engine (`app.py`), mirroring `mac/Models.swift`. */
@Serializable data class Mark(val word: String, val match: Boolean)

@Serializable data class Curves(val original: List<Double> = emptyList(), val you: List<Double> = emptyList())

@Serializable data class TakeResult(
    val id: String,
    val audio: String,
    val accuracy: Int,
    val rhythm: Int,
    val intonation: Int? = null,
    val words: List<Mark> = emptyList(),
    val curves: Curves = Curves(),
    val tips: List<String> = emptyList(),
    val playbackSpeed: Double? = null,
    val heard: String = "",
) {
    val intonationText: String get() = intonation?.toString() ?: "—"
}

@Serializable data class Word(val start: Double, val end: Double, val word: String)

@Serializable data class Phrase(
    val start: Double,
    val end: Double,
    val text: String,
    val words: List<Word>? = null,
    val practised: Boolean? = null,
    val latest: TakeResult? = null,
    val takes: Int? = null,
) {
    val length: Double get() = end - start
    val isPractised: Boolean get() = practised == true
    val takeCount: Int get() = takes ?: 0
}

@Serializable data class Lesson(
    val id: String,
    val title: String,
    val source: String,
    val duration: Double,
    val phrases: List<Phrase>,
    val sample: Boolean? = null,
)

@Serializable data class LibraryItem(
    val id: String,
    val title: String,
    val source: String,
    val duration: Double,
    val sample: Boolean = false,
    val count: Int,
    val practised: Int,
) {
    val summary: String get() = "${clock(duration)} · $count phrases · $practised practised"
}

@Serializable internal data class JobStart(val jobId: String)
@Serializable internal data class Job(val state: String, val message: String? = null, val lessonId: String? = null)
@Serializable internal data class Track(val energy: List<Double>, val pitch: List<Double?>, val speed: Double? = null)
@Serializable internal data class VoiceData(val hop: Double, val source: Track, val take: Track? = null)
@Serializable internal data class Detail(val detail: String? = null)

/** A message the user can act on; the text is shown as is. */
class ShadowError(message: String) : Exception(message)

enum class Mode(val title: String, val detail: String, val heading: String, val hint: String) {
    LISTEN("Listen", "Catch the details", "Ready when you are.", "Listen first, then record your own take."),
    RECALL("Recall", "Try it on your own", "Hold the phrase. Make it yours.", "The words are hidden. Record from memory."),
    SHADOW("Shadow", "Speak along", "Follow the speaker’s flow.", "Use headphones. The original plays while you record."),
}

enum class Connection { CONNECTING, READY, FAILED }

data class Busy(val title: String, val message: String)

data class ShadowState(
    val connection: Connection = Connection.CONNECTING,
    val connectionError: String? = null,
    val library: List<LibraryItem> = emptyList(),
    val lesson: Lesson? = null,
    val index: Int = 0,
    val mode: Mode = Mode.LISTEN,
    val concealed: Boolean = false,
    val busy: Busy? = null,
    val toast: String? = null,
    val voice: VoiceMap? = null,
    val voiceFailed: Boolean = false,
) {
    val phrase: Phrase? get() = lesson?.phrases?.getOrNull(index)
    val phraseCount: Int get() = lesson?.phrases?.size ?: 0
    val practisedCount: Int get() = lesson?.phrases?.count { it.isPractised } ?: 0
    val locked: Boolean get() = busy != null
    val hasPrevious: Boolean get() = index > 0
    val hasNext: Boolean get() = index < phraseCount - 1
    val sampleId: String? get() = library.firstOrNull { it.sample }?.id
    val phraseLabel: String get() = "PHRASE ${pad(index + 1)} / ${pad(phraseCount)}"
    val phraseTime: String get() = phrase?.let { "${clock(it.start)} – ${clock(it.end)}" } ?: ""
}

private fun pad(n: Int) = n.toString().padStart(2, '0')

/** `mm:ss`, like the Mac app's `fmt`. */
fun clock(seconds: Double): String {
    val s = seconds.toInt().coerceAtLeast(0)
    return "${pad(s / 60)}:${pad(s % 60)}"
}
