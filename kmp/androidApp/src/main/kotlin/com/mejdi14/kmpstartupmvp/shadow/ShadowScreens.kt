package com.mejdi14.kmpstartupmvp.shadow

import android.Manifest
import android.content.pm.PackageManager
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledIconButton
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.IconButton
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import kotlinx.coroutines.launch
import kotlin.math.max

private val Pitch = Color(0xFF338FFF)
private val You = Color(0xFFF0619A)
private val Rec = Color(0xFFF04045)
private val Good = Color(0xFF179966)
private val Warn = Color(0xFFEB7821)
private val Speeds = listOf(0.65, 0.8, 1.0, 1.15)

private fun speedLabel(s: Double) = (if (s == 1.0) "1" else s.toString()) + "×"

@Composable
fun ShadowTheme(content: @Composable () -> Unit) {
    val scheme = if (isSystemInDarkTheme()) darkColorScheme(
        primary = Color(0xFFF7F7F7), onPrimary = Color(0xFF101010),
        background = Color(0xFF111111), surface = Color(0xFF111111), surfaceVariant = Color(0xFF1E1E1E), surfaceContainer = Color(0xFF1C1C1C),
    ) else lightColorScheme(
        primary = Color(0xFF101010), onPrimary = Color.White,
        background = Color(0xFFFBFBFA), surface = Color(0xFFFBFBFA), surfaceVariant = Color(0xFFF0F0EE), surfaceContainer = Color(0xFFF2F2F0),
    )
    MaterialTheme(colorScheme = scheme, content = content)
}

/** Root of the Shadow Studio app: connection state, then the library with the studio on top. */
@Composable
fun ShadowApp(vm: ShadowViewModel) {
    val state by vm.state.collectAsStateWithLifecycle()
    var showImport by remember { mutableStateOf(false) }
    Box(Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background)) {
        when {
            state.connection != Connection.READY -> ConnectScreen(state, vm)
            vm.studioOpen && state.lesson != null -> StudioScreen(state, vm, onImport = { showImport = true })
            else -> LibraryScreen(state, vm, onImport = { showImport = true })
        }
        state.busy?.let { BusyOverlay(it) }
        AnimatedVisibility(state.toast != null, Modifier.align(Alignment.BottomCenter)) {
            Surface(
                shape = RoundedCornerShape(14.dp), tonalElevation = 6.dp, shadowElevation = 6.dp,
                modifier = Modifier.navigationBarsPadding().padding(16.dp).fillMaxWidth().clickable { vm.store.dismissToast() },
            ) { Text(state.toast ?: "", Modifier.padding(14.dp), style = MaterialTheme.typography.bodyMedium) }
        }
    }
    if (showImport) ImportSheet(vm) { showImport = false }
}

@Composable
private fun ConnectScreen(state: ShadowState, vm: ShadowViewModel) {
    Column(
        Modifier.fillMaxSize().padding(32.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp, Alignment.CenterVertically),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text("≋", fontSize = 44.sp, color = Pitch)
        if (state.connection == Connection.FAILED) {
            Text(state.connectionError ?: ShadowApi.OFFLINE, style = MaterialTheme.typography.titleMedium)
            Text(
                "This app uses the Shadow Studio engine on your Mac for transcription and feedback. Open Shadow Studio on the Mac and keep it running. " +
                    "The emulator (or a phone on USB) reaches it after you run this on the Mac:\nadb reverse tcp:8765 tcp:8765",
                style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Button(onClick = vm.store::start) { Text("Try again") }
        } else {
            CircularProgressIndicator()
            Text("Connecting to Shadow Studio on your Mac…", color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

@Composable
private fun BusyOverlay(busy: Busy) {
    Box(Modifier.fillMaxSize().background(Color.Black.copy(alpha = 0.3f)).pointerInput(Unit) { detectTapGestures { } }, contentAlignment = Alignment.Center) {
        Surface(shape = RoundedCornerShape(18.dp), tonalElevation = 6.dp, modifier = Modifier.padding(24.dp)) {
            Row(Modifier.padding(20.dp), horizontalArrangement = Arrangement.spacedBy(16.dp), verticalAlignment = Alignment.CenterVertically) {
                CircularProgressIndicator(Modifier.size(28.dp))
                Column {
                    Text(busy.title, style = MaterialTheme.typography.titleMedium)
                    Text(busy.message, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
        }
    }
}

@Composable
private fun Card(modifier: Modifier = Modifier, content: @Composable () -> Unit) {
    Surface(color = MaterialTheme.colorScheme.surfaceVariant, shape = RoundedCornerShape(16.dp), modifier = modifier.fillMaxWidth()) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) { content() }
    }
}

// Library

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun LibraryScreen(state: ShadowState, vm: ShadowViewModel, onImport: () -> Unit) {
    Scaffold(topBar = { TopAppBar(title = { Text("Shadow Studio") }) }) { padding ->
        LazyColumn(Modifier.padding(padding).fillMaxSize(), contentPadding = androidx.compose.foundation.layout.PaddingValues(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            item {
                Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Text("LISTEN CLOSELY. MAKE IT YOURS.", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    Text("Find your flow.", style = MaterialTheme.typography.headlineLarge, fontWeight = FontWeight.Bold)
                    Text("A voice you admire. A phrase at a time. Your own progress.", color = MaterialTheme.colorScheme.onSurfaceVariant)
                    Spacer(Modifier.height(4.dp))
                    Button(onClick = onImport) { Text("＋ Add a voice") }
                }
            }
            if (state.library.isEmpty()) item {
                Text(
                    "Every great voice begins with listening. Bring in a clip with a voice you love, and it becomes small, repeatable moments.",
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            } else {
                item { Text("Your voice library", style = MaterialTheme.typography.titleMedium, modifier = Modifier.padding(top = 8.dp)) }
                items(state.library, key = { it.id }) { item ->
                    Surface(
                        color = MaterialTheme.colorScheme.surfaceVariant, shape = RoundedCornerShape(14.dp),
                        modifier = Modifier.fillMaxWidth().clickable {
                            if (state.lesson?.id == item.id) vm.studioOpen = true else vm.store.openLesson(item.id)
                        },
                    ) {
                        Row(Modifier.padding(14.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                            Text(if (item.sample) "✧" else "≋", color = Pitch, fontSize = 22.sp)
                            Column(Modifier.weight(1f)) {
                                Text(item.title, maxLines = 2, overflow = TextOverflow.Ellipsis)
                                Text(
                                    (if (item.sample) "Guided sample · " else "") + item.summary,
                                    style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                            }
                            Text("›", fontSize = 22.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                    }
                }
            }
            item {
                Text(
                    "◉ Audio and recordings stay on your Mac. This phone only talks to the Shadow Studio engine running there.",
                    style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(top = 8.dp),
                )
            }
        }
    }
}

// Studio

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun StudioScreen(state: ShadowState, vm: ShadowViewModel, onImport: () -> Unit) {
    val lesson = state.lesson ?: return
    val phrase = state.phrase ?: return
    var menu by remember { mutableStateOf(false) }
    var showPhrases by remember { mutableStateOf(false) }
    var showEdit by remember { mutableStateOf(false) }
    var showDelete by remember { mutableStateOf(false) }
    BackHandler { vm.leaveLesson() }
    Scaffold(topBar = {
        TopAppBar(
            title = { Text(state.phraseLabel, style = MaterialTheme.typography.titleSmall) },
            navigationIcon = { TextButton(onClick = vm::leaveLesson) { Text("‹ Library") } },
            actions = {
                TextButton(onClick = { showPhrases = true }, enabled = !vm.locked) { Text("Phrases") }
                Box {
                    IconButton(onClick = { menu = true }, enabled = !vm.locked) { Text("⋯", fontSize = 22.sp) }
                    DropdownMenu(expanded = menu, onDismissRequest = { menu = false }) {
                        DropdownMenuItem(text = { Text("Edit phrase") }, onClick = { menu = false; showEdit = true })
                        DropdownMenuItem(text = { Text("Add a voice") }, onClick = { menu = false; onImport() })
                        DropdownMenuItem(text = { Text("Delete session", color = Rec) }, onClick = { menu = false; showDelete = true })
                    }
                }
            },
        )
    }) { padding ->
        Column(
            Modifier.padding(padding).fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            Column {
                Text(lesson.title, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
                Text(
                    "${if (lesson.source.startsWith("http")) "YouTube" else lesson.source} · ${state.practisedCount} of ${state.phraseCount} practised",
                    style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            PhraseCard(state, phrase, vm)
            VoiceCard(state, vm)
            PlaybackControls(state, vm)
            ModeCard(state, vm)
            val latest = phrase.latest
            if (latest != null) FeedbackCard(latest, phrase.takeCount)
            else Text(
                "Record a take to see where you match the speaker, and what to try next.",
                style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
    if (showPhrases) PhraseList(state, vm) { showPhrases = false }
    if (showEdit) EditDialog(phrase, vm) { showEdit = false }
    if (showDelete) AlertDialog(
        onDismissRequest = { showDelete = false },
        title = { Text("Remove this session?") },
        text = { Text("Its imported audio, recordings, and feedback will be deleted from your Mac.") },
        confirmButton = { TextButton(onClick = { showDelete = false; vm.leaveLesson(); vm.store.deleteLesson() }) { Text("Delete session", color = Rec) } },
        dismissButton = { TextButton(onClick = { showDelete = false }) { Text("Keep session") } },
    )
}

@Composable
private fun PhraseCard(state: ShadowState, phrase: Phrase, vm: ShadowViewModel) {
    Card {
        Text(state.phraseTime, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        if (state.concealed) {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                listOf(70, 44, 92, 58).forEach { Box(Modifier.width(it.dp).height(14.dp).background(MaterialTheme.colorScheme.outlineVariant, CircleShape)) }
            }
            TextButton(onClick = { vm.store.setConcealed(false) }) { Text("Reveal phrase") }
        } else {
            Text("“${phrase.text}”", style = MaterialTheme.typography.titleLarge)
        }
    }
}

@Composable
private fun VoiceCard(state: ShadowState, vm: ShadowViewModel) {
    val voice = state.voice
    Card {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text("How it sounds", style = MaterialTheme.typography.titleSmall, modifier = Modifier.weight(1f))
            Legend(Pitch, "Speaker")
            if (voice?.you != null) { Spacer(Modifier.width(10.dp)); Legend(You, "You") }
        }
        when {
            voice != null -> {
                VoiceChart(voice, vm.position, Modifier.fillMaxWidth().height(140.dp)) { vm.seek(it) }
                if (!state.concealed) Text(
                    buildAnnotatedString {
                        voice.words.forEach { w -> withStyle(SpanStyle(fontWeight = if (w.stressed) FontWeight.Bold else FontWeight.Normal)) { append(w.text + " ") } }
                    },
                    style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Text(
                    listOfNotNull(
                        voice.pauseText,
                        voice.speakerEnding.takeIf { it.isNotEmpty() }?.let { "ending $it" },
                        voice.youEnding.takeIf { it.isNotEmpty() }?.let { "your ending $it" },
                    ).joinToString(" · "),
                    style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            state.voiceFailed -> Text(
                "The voice map isn't available for this phrase.",
                style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.height(140.dp),
            )
            else -> Box(Modifier.fillMaxWidth().height(140.dp), contentAlignment = Alignment.Center) { CircularProgressIndicator() }
        }
    }
}

@Composable
private fun Legend(color: Color, label: String) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Box(Modifier.size(8.dp).background(color, CircleShape))
        Spacer(Modifier.width(4.dp))
        Text(label, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

/** Relative pitch lines (semitones), loudness bars and pauses for one phrase. */
@Composable
private fun VoiceChart(map: VoiceMap, position: Double, modifier: Modifier, onSeek: (Double) -> Unit) {
    val ink = MaterialTheme.colorScheme.onSurface
    Canvas(modifier.pointerInput(map) {
        val toTime = { x: Float -> x / max(1f, size.width.toFloat()) * map.length }
        detectTapGestures { onSeek(toTime(it.x)) }
    }.pointerInput(map) {
        val toTime = { x: Float -> x / max(1f, size.width.toFloat()) * map.length }
        detectDragGestures { change, _ -> onSeek(toTime(change.position.x)) }
    }) {
        val len = max(map.length, 0.1)
        val span = max(map.high - map.low, 1.0)
        fun x(t: Double) = (t / len).toFloat() * size.width
        fun y(st: Double) = 8f + (1f - ((st - map.low) / span).toFloat()) * (size.height * 0.72f - 16f)
        map.pauses.forEach { drawRect(ink.copy(alpha = 0.06f), Offset(x(it.from), 0f), Size(max(1f, x(it.to) - x(it.from)), size.height)) }
        val barWidth = max(1f, size.width / max(map.src.size, 1) * 0.7f)
        map.src.forEach { p ->
            val h = p.e.toFloat() * size.height * 0.26f
            drawRect(ink.copy(alpha = 0.18f), Offset(x(p.x), size.height - h), Size(barWidth, h))
        }
        fun line(pts: List<Pt>) = Path().apply {
            var drawing = false
            pts.forEach { p ->
                val st = p.st
                if (st == null) drawing = false
                else if (drawing) lineTo(x(p.x), y(st))
                else { moveTo(x(p.x), y(st)); drawing = true }
            }
        }
        drawPath(line(map.src), Pitch, style = Stroke(width = 3.dp.toPx(), cap = StrokeCap.Round, join = StrokeJoin.Round))
        map.you?.let { drawPath(line(it), You, style = Stroke(width = 2.dp.toPx(), cap = StrokeCap.Round, join = StrokeJoin.Round)) }
        if (position > 0) drawLine(ink.copy(alpha = 0.5f), Offset(x(position), 0f), Offset(x(position), size.height), strokeWidth = 1.dp.toPx())
    }
}

@OptIn(androidx.compose.foundation.layout.ExperimentalLayoutApi::class)
@Composable
private fun PlaybackControls(state: ShadowState, vm: ShadowViewModel) {
    var speedMenu by remember { mutableStateOf(false) }
    Column(Modifier.fillMaxWidth(), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(24.dp)) {
            IconButton(onClick = vm::previous, enabled = state.hasPrevious && !vm.locked) { Text("⏮", fontSize = 22.sp) }
            FilledIconButton(
                onClick = vm::togglePlay, enabled = vm.audioReady, modifier = Modifier.size(68.dp),
                colors = IconButtonDefaults.filledIconButtonColors(),
            ) { Text(if (vm.isPlaying) "❚❚" else "▶", fontSize = 24.sp) }
            IconButton(onClick = vm::next, enabled = state.hasNext && !vm.locked) { Text("⏭", fontSize = 22.sp) }
        }
        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp, Alignment.CenterHorizontally)) {
            Box {
                FilterChip(selected = vm.speed != 1.0, onClick = { speedMenu = true }, label = { Text("Speed ${speedLabel(vm.speed)}") })
                DropdownMenu(expanded = speedMenu, onDismissRequest = { speedMenu = false }) {
                    Speeds.forEach { s -> DropdownMenuItem(text = { Text(speedLabel(s)) }, onClick = { speedMenu = false; vm.changeSpeed(s) }) }
                }
            }
            FilterChip(selected = vm.looping, onClick = { vm.looping = !vm.looping }, label = { Text("↻ Loop") })
            FilterChip(selected = state.concealed, onClick = { vm.store.setConcealed(!state.concealed) }, label = { Text("◌ Hide text") })
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ModeCard(state: ShadowState, vm: ShadowViewModel) {
    val context = LocalContext.current
    val permission = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        if (granted) vm.toggleRecording()
        else vm.store.show("Microphone access was denied. Allow it in Settings → Apps → Shadow Studio → Permissions, then try again.")
    }
    Card {
        SingleChoiceSegmentedButtonRow(Modifier.fillMaxWidth()) {
            Mode.entries.forEachIndexed { i, m ->
                SegmentedButton(
                    selected = state.mode == m, onClick = { vm.setMode(m) }, enabled = !vm.recording,
                    shape = SegmentedButtonDefaults.itemShape(i, Mode.entries.size),
                ) { Text(m.title) }
            }
        }
        Text(state.mode.heading, style = MaterialTheme.typography.titleMedium)
        Text(state.mode.hint, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Button(
            onClick = {
                if (vm.recording || context.checkSelfPermission(Manifest.permission.RECORD_AUDIO) == PackageManager.PERMISSION_GRANTED) vm.toggleRecording()
                else permission.launch(Manifest.permission.RECORD_AUDIO)
            },
            enabled = !state.locked && (vm.audioReady || state.mode != Mode.SHADOW),
            colors = if (vm.recording) ButtonDefaults.buttonColors(containerColor = Rec, contentColor = Color.White)
            else ButtonDefaults.buttonColors(containerColor = Rec.copy(alpha = 0.14f), contentColor = MaterialTheme.colorScheme.onSurface),
            shape = RoundedCornerShape(14.dp),
            modifier = Modifier.fillMaxWidth().heightIn(min = 52.dp),
        ) {
            Box(Modifier.size(10.dp).background(if (vm.recording) Color.White else Rec, CircleShape))
            Spacer(Modifier.width(8.dp))
            Text(if (vm.recording) "Stop · ${clock(vm.recordSeconds)}" else "Record a take", style = MaterialTheme.typography.titleMedium)
        }
        if (vm.recording) Text(
            if (state.mode == Mode.SHADOW) "Recording · use headphones when shadowing" else "Recording · tap stop when you finish",
            style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        if (state.phrase?.latest != null) Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            FilterChip(selected = vm.isTakePlaying, onClick = vm::toggleTake, enabled = !vm.recording, label = { Text(if (vm.isTakePlaying) "■ Stop" else "▷ Your take") })
            FilterChip(selected = false, onClick = vm::compareAB, enabled = !vm.recording && vm.audioReady, label = { Text("⇄ Original → you") })
        }
    }
}

@Composable
private fun FeedbackCard(result: TakeResult, takes: Int) {
    var details by remember { mutableStateOf(false) }
    Card {
        Row {
            Text("YOUR FEEDBACK", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.weight(1f))
            Text(if (takes == 1) "1 take" else "$takes takes", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Score("${result.accuracy}", "Word accuracy", Modifier.weight(1f))
            Score("${result.rhythm}", "Pace & rhythm", Modifier.weight(1f))
            Score(result.intonationText, "Intonation", Modifier.weight(1f))
        }
        if (result.words.isNotEmpty()) Text(buildAnnotatedString {
            result.words.forEach { m ->
                withStyle(SpanStyle(color = if (m.match) Good else Warn, fontWeight = if (m.match) FontWeight.Normal else FontWeight.SemiBold)) { append(m.word + " ") }
            }
        })
        result.tips.forEach { Text("↗ $it", style = MaterialTheme.typography.bodyMedium) }
        HorizontalDivider()
        Text(
            (if (details) "▾ " else "▸ ") + "How feedback works",
            style = MaterialTheme.typography.bodyMedium, modifier = Modifier.clickable { details = !details },
        )
        if (details) Text(
            "These are practice estimates, not validated pronunciation grades. Word accuracy compares speech recognition with the phrase text; it does not assess individual speech sounds. " +
                "Rhythm uses duration and loudness patterns. Intonation compares relative pitch contours; music and noisy recordings can affect it. " +
                "The line follows the voice going up and down, relative to each speaker’s own pitch. Shaded gaps are pauses of 0.15 seconds or more. " +
                "Bold words are simply the loudest ones, which are often but not always the stressed words. A saved take is stretched to the speaker’s length so the lines line up.",
            style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

@Composable
private fun Score(value: String, title: String, modifier: Modifier) {
    Surface(color = MaterialTheme.colorScheme.background, shape = RoundedCornerShape(12.dp), modifier = modifier) {
        Column(Modifier.padding(12.dp)) {
            Text(value, style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.Bold)
            Text(title, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun PhraseList(state: ShadowState, vm: ShadowViewModel, onDismiss: () -> Unit) {
    val phrases = state.lesson?.phrases.orEmpty()
    ModalBottomSheet(onDismissRequest = onDismiss) {
        Text(
            "Your phrases · ${state.practisedCount} practised",
            style = MaterialTheme.typography.titleMedium, modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
        )
        LazyColumn {
            itemsIndexed(phrases) { i, p ->
                Row(
                    Modifier.fillMaxWidth()
                        .background(if (i == state.index) MaterialTheme.colorScheme.surfaceVariant else Color.Transparent)
                        .clickable { vm.select(i); onDismiss() }
                        .padding(horizontal = 16.dp, vertical = 12.dp),
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    Text((i + 1).toString().padStart(2, '0'), style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    Column(Modifier.weight(1f)) {
                        Text(p.text, maxLines = 3, overflow = TextOverflow.Ellipsis)
                        Text(
                            "${clock(p.start)} – ${clock(p.end)}" + (p.latest?.let { " · ${it.accuracy} accuracy" } ?: ""),
                            style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                    if (p.isPractised) Text("✓", color = Good)
                }
            }
        }
    }
}

// Sheets

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ImportSheet(vm: ShadowViewModel, onDismiss: () -> Unit) {
    var url by remember { mutableStateOf("") }
    val picker = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri != null) { onDismiss(); vm.importFile(uri) }
    }
    ModalBottomSheet(onDismissRequest = onDismiss) {
        Column(Modifier.padding(horizontal = 20.dp).padding(bottom = 24.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Text("A NEW VOICE TO LEARN FROM", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Text("Bring a voice you love.", style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold)
            Text(
                "Choose clear English speech from one speaker. Short clips with little background music work best.",
                style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            OutlinedTextField(
                value = url, onValueChange = { url = it }, singleLine = true, modifier = Modifier.fillMaxWidth(),
                label = { Text("YouTube video link") }, placeholder = { Text("https://www.youtube.com/watch?v=…") },
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Uri),
            )
            Text("Up to 30 minutes. Import content you have permission to use.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Button(onClick = { onDismiss(); vm.store.importLink(url) }, enabled = url.isNotBlank(), modifier = Modifier.fillMaxWidth()) { Text("Create practice session →") }
            Text("or upload a file", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.align(Alignment.CenterHorizontally))
            OutlinedButton(onClick = { picker.launch(arrayOf("audio/*", "video/*")) }, modifier = Modifier.fillMaxWidth()) { Text("↑ Choose an audio or video file") }
            Text("MP3, WAV, M4A, MP4, WebM · up to 150 MB", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Text(
                "◉ Processing and recordings stay on your Mac. Importing a link connects to YouTube; the first transcription downloads a speech model.",
                style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@Composable
private fun EditDialog(phrase: Phrase, vm: ShadowViewModel, onDismiss: () -> Unit) {
    var text by remember { mutableStateOf(phrase.text) }
    var start by remember { mutableStateOf("%.2f".format(phrase.start)) }
    var end by remember { mutableStateOf("%.2f".format(phrase.end)) }
    var error by remember { mutableStateOf("") }
    var saving by remember { mutableStateOf(false) }
    val scope = rememberCoroutineScope()
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Refine this phrase.") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                OutlinedTextField(text, { text = it }, label = { Text("Phrase text") }, minLines = 3, modifier = Modifier.fillMaxWidth())
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedTextField(start, { start = it }, label = { Text("Start (s)") }, singleLine = true, modifier = Modifier.weight(1f), keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal))
                    OutlinedTextField(end, { end = it }, label = { Text("End (s)") }, singleLine = true, modifier = Modifier.weight(1f), keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal))
                }
                Text("Keep phrases under 30 seconds. Correct text before comparing takes.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                if (error.isNotEmpty()) Text(error, color = Rec, style = MaterialTheme.typography.bodySmall)
            }
        },
        confirmButton = {
            TextButton(enabled = !saving, onClick = {
                val s = start.replace(',', '.').toDoubleOrNull()
                val e = end.replace(',', '.').toDoubleOrNull()
                if (s == null || e == null) { error = "Enter text and valid start/end times."; return@TextButton }
                saving = true; error = ""
                scope.launch {
                    try { vm.store.savePhrase(text, s, e); onDismiss() }
                    catch (x: Exception) { error = x.message ?: "Something went wrong. Please try again." }
                    saving = false
                }
            }) { Text("Save phrase") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } },
    )
}
