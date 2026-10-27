package com.mejdi14.kmpstartupmvp.shadow

import kotlin.math.abs
import kotlin.math.ln
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt

/** One point of a voice line: [x] in phrase seconds, loudness [e] 0–1, relative pitch [st] in semitones. */
data class Pt(val x: Double, val e: Double, val st: Double?)

data class Span(val from: Double, val to: Double)

data class VoiceWord(val text: String, val stressed: Boolean)

/**
 * The speaker's line (and the latest take's, stretched to the speaker's length) for one phrase.
 * Port of the Mac app's voice map analysis (`mac/Models.swift`), so both apps draw the same thing.
 */
data class VoiceMap(
    val src: List<Pt>,
    val pauses: List<Span>,
    val words: List<VoiceWord>,
    val you: List<Pt>?,
    val length: Double,
    val low: Double,
    val high: Double,
) {
    val speakerEnding: String get() = ending(src)
    val youEnding: String get() = you?.let(::ending) ?: ""
    val pauseText: String get() = when (pauses.size) {
        0 -> "No pauses"
        1 -> "1 pause"
        else -> "${pauses.size} pauses"
    }
}

internal const val ACTIVE_LEVEL = 0.1

internal fun buildVoiceMap(v: VoiceData, phrase: Phrase): VoiceMap {
    val src = makeTrack(v.source.energy, v.source.pitch) { (it + 1) * v.hop }
    val sp = v.take?.speed ?: 1.0
    val len = phrase.length
    val you = v.take?.let { t ->
        val raw = makeTrack(t.energy, t.pitch) { (it + 1) * v.hop * sp }
        var shift = onsetX(src) - onsetX(raw)
        var scale = sp
        val a = speechSpan(src)
        val b = speechSpan(raw)
        if (a != null && b != null && b.to > b.from) {
            scale = (a.to - a.from) * sp / (b.to - b.from)
            shift = a.from - b.from / sp * scale
        }
        raw.map { Pt(it.x / sp * scale + shift, it.e, it.st) }.filter { it.x in 0.0..len }
    }
    val words = phrase.words.orEmpty()
    val stress = stressed(src, words, phrase.start)
    val shown = if (words.isEmpty()) phrase.text.split(' ').filter { it.isNotBlank() }.map { VoiceWord(it, false) }
    else words.mapIndexed { i, w -> VoiceWord(w.word.trim(), i in stress) }
    val all = (src + you.orEmpty()).mapNotNull { it.st }
    return VoiceMap(
        src = src, pauses = pauses(src), words = shown, you = you, length = len,
        low = min(-4.0, all.minOrNull() ?: -4.0), high = max(4.0, all.maxOrNull() ?: 4.0),
    )
}

internal fun makeTrack(e: List<Double>, hz: List<Double?>, x: (Int) -> Double): List<Pt> {
    val v = hz.filterNotNull().sorted()
    val med = if (v.isEmpty()) null else v[v.size / 2]
    val st = smoothPitch(e.indices.map { i ->
        val h = hz.getOrNull(i)
        if (h == null || med == null) null
        else {
            val s = 12 * ln(h / med) / ln(2.0)
            val f = if (s > 8) s - 12 else if (s < -8) s + 12 else s
            if (abs(f) > 12) null else f
        }
    })
    return e.indices.map { Pt(x(it), e[it], st[it]) }
}

internal fun smoothPitch(v: List<Double?>): List<Double?> {
    val out = v.toMutableList()
    var i = 0
    while (i < v.size) {
        if (v[i] == null) { i++; continue }
        var j = i
        while (j < v.size && v[j] != null) j++
        val m = (i until j).map { k ->
            val lo = max(i, k - 2); val hi = min(j - 1, k + 2)
            (lo..hi).mapNotNull { v[it] }.sorted()[(hi - lo + 1) / 2]
        }
        for (k in i until j) {
            out[k] = if (j - i < 4) null else (m[max(0, k - i - 1)] + m[k - i] + m[min(j - i - 1, k - i + 1)]) / 3
        }
        i = j
    }
    return out
}

internal fun onsetX(t: List<Pt>): Double = if (t.isEmpty()) 0.0 else t[t.indexOfFirst { it.e > ACTIVE_LEVEL }.coerceAtLeast(0)].x

internal fun speechSpan(t: List<Pt>): Span? {
    val a = t.indexOfFirst { it.e > ACTIVE_LEVEL }
    val b = t.indexOfLast { it.e > ACTIVE_LEVEL }
    return if (a < 0 || b < 0) null else Span(t[a].x, t[b].x)
}

internal fun pauses(t: List<Pt>): List<Span> {
    val a = t.indexOfFirst { it.e > ACTIVE_LEVEL }
    val b = t.indexOfLast { it.e > ACTIVE_LEVEL }
    if (a < 0 || b < 0) return emptyList()
    val out = mutableListOf<Span>()
    var s: Int? = null
    for (i in a..b) {
        val on = t[i].e > ACTIVE_LEVEL
        if (!on && s == null) s = i
        val st = s
        if (on && st != null) {
            if (t[i].x - t[st].x >= 0.149) out.add(Span(t[st].x, t[i].x))
            s = null
        }
    }
    return out
}

internal fun ending(t: List<Pt>): String {
    val v = t.mapNotNull { it.st }
    if (v.size < 12) return ""
    val last = v.takeLast(5).average()
    val before = v.dropLast(5).takeLast(10).average()
    val d = last - before
    return if (d > 1.2) "goes up ↗" else if (d < -1.2) "goes down ↘" else "stays flat →"
}

internal fun stressed(t: List<Pt>, words: List<Word>, start: Double): Set<Int> {
    if (t.isEmpty() || words.isEmpty()) return emptySet()
    val peaks = words.map { w -> t.filter { it.x >= w.start - start && it.x <= w.end - start }.maxOfOrNull { it.e } ?: 0.0 }
    val cut = max(0.5, peaks.sortedDescending()[max(0, (words.size / 4.0).roundToInt() - 1)])
    return peaks.indices.filter { peaks[it] >= cut }.toSet()
}
