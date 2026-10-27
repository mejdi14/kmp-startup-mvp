import SwiftUI
import UniformTypeIdentifiers
import SharedLogic

enum Shade {
    static let pitch = Color(red: 0.2, green: 0.56, blue: 1)
    static let you = Color(red: 0.94, green: 0.38, blue: 0.6)
    static let rec = Color(red: 0.94, green: 0.25, blue: 0.27)
    static let good = Color(red: 0.09, green: 0.6, blue: 0.4)
    static let warn = Color(red: 0.92, green: 0.47, blue: 0.13)
    static let card = Color(.secondarySystemBackground)
}

let speeds: [Double] = [0.65, 0.8, 1, 1.15]
let modes: [Mode] = [.listen, .recall, .shadow]

func speedLabel(_ s: Double) -> String { (s == 1 ? "1" : String(format: "%g", s)) + "×" }
func clock(_ s: Double) -> String { ShadowModelsKt.clock(seconds: s) }

/// Root of the Shadow Studio app: connection state, then the library with the studio pushed on top.
struct ShadowRoot: View {
    @StateObject private var model = ShadowModel()
    @State private var path: [String] = []
    @State private var showImport = false

    var body: some View {
        ZStack {
            if model.state.connection == .ready {
                NavigationStack(path: $path) {
                    LibraryScreen(showImport: $showImport, open: open)
                        .navigationDestination(for: String.self) { _ in StudioScreen(showImport: $showImport) }
                }
            } else { ConnectScreen() }
            if let busy = model.state.busy { BusyOverlay(busy: busy) }
        }
        .overlay(alignment: .bottom) {
            if let t = model.state.toast {
                Text(t).font(.subheadline).multilineTextAlignment(.leading)
                    .padding(14).frame(maxWidth: .infinity, alignment: .leading)
                    .background(.regularMaterial, in: RoundedRectangle(cornerRadius: 14))
                    .padding(.horizontal, 16).padding(.bottom, 8)
                    .onTapGesture { model.store.dismissToast() }
                    .transition(.move(edge: .bottom).combined(with: .opacity))
            }
        }
        .animation(.easeInOut(duration: 0.2), value: model.state.toast)
        .sheet(isPresented: $showImport) { ImportSheet() }
        .onChange(of: model.state.lesson?.id) { old, new in
            if let new, path.last != new { path = [new] }
            if new == nil, old != nil { path = [] }
        }
        .onChange(of: path) { _, p in if p.isEmpty { model.leaveLesson() } }
        .environmentObject(model)
    }

    func open(_ id: String) {
        if model.state.lesson?.id == id { path = [id] } else { model.store.openLesson(id: id) }
    }
}

struct ConnectScreen: View {
    @EnvironmentObject var model: ShadowModel
    var body: some View {
        VStack(spacing: 18) {
            Image(systemName: "waveform").font(.system(size: 44)).foregroundStyle(Shade.pitch)
            if model.state.connection == .failed {
                Text(model.state.connectionError ?? ShadowApi.companion.OFFLINE).font(.headline).multilineTextAlignment(.center)
                Text("This app uses the Shadow Studio engine on your Mac for transcription and feedback. Open Shadow Studio on the Mac (or run “Start Shadow Studio.command”), keep it running, then try again. In the Simulator it is reached at 127.0.0.1:8765.")
                    .font(.subheadline).foregroundStyle(.secondary).multilineTextAlignment(.center)
                Button("Try again") { model.store.start() }.buttonStyle(.borderedProminent).tint(.primary)
            } else {
                ProgressView()
                Text("Connecting to Shadow Studio on your Mac…").foregroundStyle(.secondary)
            }
        }
        .padding(32)
    }
}

struct BusyOverlay: View {
    let busy: Busy
    var body: some View {
        ZStack {
            Color.black.opacity(0.25).ignoresSafeArea()
            HStack(spacing: 14) {
                ProgressView()
                VStack(alignment: .leading, spacing: 4) {
                    Text(busy.title).font(.headline)
                    Text(busy.message).font(.subheadline).foregroundStyle(.secondary)
                }
            }
            .padding(20).frame(maxWidth: 340, alignment: .leading)
            .background(.regularMaterial, in: RoundedRectangle(cornerRadius: 18))
            .padding(24)
        }
    }
}

// MARK: Library

struct LibraryScreen: View {
    @EnvironmentObject var model: ShadowModel
    @Binding var showImport: Bool
    let open: (String) -> Void

    var body: some View {
        List {
            Section {
                VStack(alignment: .leading, spacing: 6) {
                    Text("LISTEN CLOSELY. MAKE IT YOURS.").font(.caption.weight(.semibold)).foregroundStyle(.secondary)
                    Text("Find your flow.").font(.largeTitle.bold())
                    Text("A voice you admire. A phrase at a time. Your own progress.").foregroundStyle(.secondary)
                }
                .padding(.vertical, 6)
                Button { showImport = true } label: { Label("Add a voice", systemImage: "plus.circle.fill") }
            }
            if model.state.library.isEmpty {
                Section {
                    Text("Every great voice begins with listening. Bring in a clip with a voice you love, and it becomes small, repeatable moments.")
                        .foregroundStyle(.secondary)
                }
            } else {
                Section("Your voice library") {
                    ForEach(model.state.library, id: \.id) { item in
                        Button { open(item.id) } label: {
                            HStack(spacing: 12) {
                                Image(systemName: item.sample ? "sparkles" : "waveform").foregroundStyle(Shade.pitch).frame(width: 28)
                                VStack(alignment: .leading, spacing: 3) {
                                    Text(item.title).foregroundStyle(.primary).lineLimit(2)
                                    Text(item.sample ? "Guided sample · " + item.summary : item.summary).font(.caption).foregroundStyle(.secondary)
                                }
                                Spacer()
                                Image(systemName: "chevron.right").font(.caption).foregroundStyle(.tertiary)
                            }
                        }
                    }
                }
            }
            Section {
                Label("Audio and recordings stay on your Mac. This phone only talks to the Shadow Studio engine running there.", systemImage: "lock.fill")
                    .font(.footnote).foregroundStyle(.secondary)
            }
        }
        .navigationTitle("Shadow Studio")
        .navigationBarTitleDisplayMode(.inline)
        .refreshable { model.store.refreshLibrary() }
    }
}

// MARK: Studio

struct StudioScreen: View {
    @EnvironmentObject var model: ShadowModel
    @Binding var showImport: Bool
    @State private var showPhrases = false
    @State private var showEdit = false
    @State private var showDelete = false

    var body: some View {
        Group {
            if let lesson = model.state.lesson, let phrase = model.phrase {
                ScrollView {
                    VStack(alignment: .leading, spacing: 16) {
                        VStack(alignment: .leading, spacing: 4) {
                            Text(lesson.title).font(.title2.bold())
                            Text("\(lesson.source.hasPrefix("http") ? "YouTube" : lesson.source) · \(model.state.practisedCount) of \(model.state.phraseCount) practised").font(.subheadline).foregroundStyle(.secondary)
                        }
                        PhraseCard(phrase: phrase)
                        VoiceCard()
                        PlaybackControls()
                        ModeCard()
                        if let r = phrase.latest { FeedbackCard(result: r, takes: Int(phrase.takeCount)) }
                        else {
                            Text("Record a take to see where you match the speaker, and what to try next.")
                                .font(.subheadline).foregroundStyle(.secondary)
                        }
                    }
                    .padding(16)
                }
            } else { ProgressView().frame(maxWidth: .infinity, maxHeight: .infinity) }
        }
        .navigationTitle(model.state.phraseLabel)
        .navigationBarTitleDisplayMode(.inline)
        .toolbar {
            ToolbarItemGroup(placement: .topBarTrailing) {
                Button { showPhrases = true } label: { Image(systemName: "list.bullet") }.disabled(model.locked)
                Menu {
                    Button { showEdit = true } label: { Label("Edit phrase", systemImage: "pencil") }
                    Button { showImport = true } label: { Label("Add a voice", systemImage: "plus") }
                    Button(role: .destructive) { showDelete = true } label: { Label("Delete session", systemImage: "trash") }
                } label: { Image(systemName: "ellipsis.circle") }
                .disabled(model.locked)
            }
        }
        .sheet(isPresented: $showPhrases) { PhraseList() }
        .sheet(isPresented: $showEdit) { EditSheet() }
        .confirmationDialog("Remove this session?", isPresented: $showDelete, titleVisibility: .visible) {
            Button("Delete session", role: .destructive) { model.leaveLesson(); model.store.deleteLesson() }
            Button("Keep session", role: .cancel) {}
        } message: { Text("Its imported audio, recordings, and feedback will be deleted from your Mac.") }
    }
}

struct PhraseCard: View {
    @EnvironmentObject var model: ShadowModel
    let phrase: Phrase
    var body: some View {
        VStack(alignment: .leading, spacing: 10) {
            Text(model.state.phraseTime).font(.caption.monospacedDigit()).foregroundStyle(.secondary)
            if model.state.concealed {
                HStack { ForEach([70, 44, 92, 58], id: \.self) { w in Capsule().fill(.quaternary).frame(width: CGFloat(w), height: 14) } }
                Button("Reveal phrase") { model.store.setConcealed(concealed: false) }.font(.subheadline)
            } else {
                Text("“" + phrase.text + "”").font(.title3.weight(.medium)).fixedSize(horizontal: false, vertical: true)
            }
        }
        .padding(16).frame(maxWidth: .infinity, alignment: .leading)
        .background(Shade.card, in: RoundedRectangle(cornerRadius: 16))
    }
}

struct VoiceCard: View {
    @EnvironmentObject var model: ShadowModel
    var body: some View {
        VStack(alignment: .leading, spacing: 10) {
            HStack {
                Text("How it sounds").font(.subheadline.weight(.semibold))
                Spacer()
                Legend(color: Shade.pitch, label: "Speaker")
                if model.state.voice?.you != nil { Legend(color: Shade.you, label: "You") }
            }
            if let v = model.state.voice {
                GeometryReader { geo in
                    VoiceChart(map: v, position: model.position)
                        .contentShape(Rectangle())
                        .gesture(DragGesture(minimumDistance: 0).onChanged { g in model.seek(g.location.x / max(1, geo.size.width) * v.length) })
                }
                .frame(height: 140)
                if !model.state.concealed {
                    v.words.reduce(Text("")) { t, w in t + Text(w.text + " ").fontWeight(w.stressed ? .bold : .regular) }
                        .font(.footnote).foregroundStyle(.secondary)
                }
                Text([v.pauseText, v.speakerEnding.isEmpty ? nil : "ending " + v.speakerEnding, v.youEnding.isEmpty ? nil : "your ending " + v.youEnding].compactMap { $0 }.joined(separator: " · "))
                    .font(.caption).foregroundStyle(.secondary)
            } else if model.state.voiceFailed {
                Text("The voice map isn't available for this phrase.").font(.caption).foregroundStyle(.secondary).frame(height: 140)
            } else {
                ProgressView().frame(maxWidth: .infinity, minHeight: 140)
            }
        }
        .padding(16)
        .background(Shade.card, in: RoundedRectangle(cornerRadius: 16))
    }
}

struct Legend: View {
    let color: Color; let label: String
    var body: some View { HStack(spacing: 4) { Circle().fill(color).frame(width: 8, height: 8); Text(label).font(.caption).foregroundStyle(.secondary) } }
}

/// Relative pitch lines (semitones), loudness bars and pauses for one phrase.
struct VoiceChart: View {
    let map: VoiceMap
    let position: Double
    var body: some View {
        Canvas { ctx, size in
            let len = max(map.length, 0.1), span = max(map.high - map.low, 1)
            func x(_ t: Double) -> CGFloat { CGFloat(t / len) * size.width }
            func y(_ st: Double) -> CGFloat { 8 + (1 - CGFloat((st - map.low) / span)) * (size.height * 0.72 - 16) }
            for p in map.pauses { ctx.fill(Path(CGRect(x: x(p.from), y: 0, width: max(1, x(p.to) - x(p.from)), height: size.height)), with: .color(.primary.opacity(0.06))) }
            for p in map.src {
                let h = CGFloat(p.e) * size.height * 0.26
                ctx.fill(Path(CGRect(x: x(p.x), y: size.height - h, width: max(1, size.width / CGFloat(max(map.src.count, 1)) * 0.7), height: h)), with: .color(.primary.opacity(0.18)))
            }
            ctx.stroke(line(map.src, x, y), with: .color(Shade.pitch), style: StrokeStyle(lineWidth: 3, lineCap: .round, lineJoin: .round))
            if let you = map.you { ctx.stroke(line(you, x, y), with: .color(Shade.you), style: StrokeStyle(lineWidth: 2, lineCap: .round, lineJoin: .round)) }
            if position > 0 {
                ctx.stroke(Path { $0.move(to: CGPoint(x: x(position), y: 0)); $0.addLine(to: CGPoint(x: x(position), y: size.height)) }, with: .color(.primary.opacity(0.5)), lineWidth: 1)
            }
        }
    }

    func line(_ pts: [Pt], _ x: (Double) -> CGFloat, _ y: (Double) -> CGFloat) -> Path {
        var path = Path(), drawing = false
        for p in pts {
            guard let st = p.st?.doubleValue else { drawing = false; continue }
            let pt = CGPoint(x: x(p.x), y: y(st))
            if drawing { path.addLine(to: pt) } else { path.move(to: pt); drawing = true }
        }
        return path
    }
}

struct PlaybackControls: View {
    @EnvironmentObject var model: ShadowModel
    var body: some View {
        VStack(spacing: 14) {
            HStack(spacing: 28) {
                Button { model.previous() } label: { Image(systemName: "backward.fill").font(.title3) }.disabled(!model.state.hasPrevious || model.locked)
                Button { model.togglePlay() } label: {
                    Image(systemName: model.isPlaying ? "pause.fill" : "play.fill").font(.title).foregroundStyle(Color(.systemBackground))
                        .frame(width: 68, height: 68).background(Color.primary, in: Circle())
                }
                .disabled(!model.audioReady || model.starting)
                .accessibilityLabel(model.isPlaying ? "Pause" : "Play original phrase")
                Button { model.next() } label: { Image(systemName: "forward.fill").font(.title3) }.disabled(!model.state.hasNext || model.locked)
            }
            .foregroundStyle(.primary)
            HStack(spacing: 10) {
                Menu {
                    ForEach(speeds, id: \.self) { s in Button(speedLabel(s)) { model.speed = s } }
                } label: { Chip(label: "Speed " + speedLabel(model.speed), on: model.speed != 1) }
                Button { model.looping.toggle() } label: { Chip(label: "↻ Loop", on: model.looping) }
                Button { model.store.setConcealed(concealed: !model.state.concealed) } label: { Chip(label: "◌ Hide text", on: model.state.concealed) }
            }
        }
        .frame(maxWidth: .infinity)
    }
}

struct Chip: View {
    let label: String; let on: Bool
    var body: some View {
        Text(label).font(.subheadline).padding(.horizontal, 12).padding(.vertical, 7)
            .foregroundStyle(on ? Color(.systemBackground) : .primary)
            .background(on ? Color.primary : Shade.card, in: Capsule())
    }
}

struct ModeCard: View {
    @EnvironmentObject var model: ShadowModel
    var body: some View {
        VStack(alignment: .leading, spacing: 12) {
            Picker("Practice mode", selection: Binding(get: { model.state.mode }, set: { model.setMode($0) })) {
                ForEach(modes, id: \.self) { Text($0.title).tag($0) }
            }
            .pickerStyle(.segmented)
            .disabled(model.recording || model.starting)
            Text(model.state.mode.heading).font(.headline)
            Text(model.state.mode.hint).font(.subheadline).foregroundStyle(.secondary)
            Button { Task { await model.toggleRecording() } } label: {
                HStack {
                    Circle().fill(model.recording ? Color.white : Shade.rec).frame(width: 10, height: 10)
                    Text(model.recording ? "Stop · " + clock(model.recordSeconds) : "Record a take").font(.headline)
                }
                .frame(maxWidth: .infinity, minHeight: 50)
                .foregroundStyle(model.recording ? .white : .primary)
                .background(model.recording ? Shade.rec : Shade.rec.opacity(0.12), in: RoundedRectangle(cornerRadius: 14))
            }
            .disabled(model.starting || model.state.locked || !model.audioReady && model.state.mode == .shadow)
            if model.recording {
                Text(model.state.mode == .shadow ? "Recording · use headphones when shadowing" : "Recording · tap stop when you finish").font(.caption).foregroundStyle(.secondary)
            }
            if model.phrase?.latest != nil {
                HStack(spacing: 10) {
                    Button { model.toggleTake() } label: { Chip(label: model.isTakePlaying ? "■ Stop" : "▷ Your take", on: model.isTakePlaying) }
                    Button { model.compareAB() } label: { Chip(label: "⇄ Original → you", on: false) }
                }
                .disabled(model.recording || !model.audioReady)
            }
        }
        .padding(16)
        .background(Shade.card, in: RoundedRectangle(cornerRadius: 16))
    }
}

struct FeedbackCard: View {
    let result: TakeResult
    let takes: Int
    var body: some View {
        VStack(alignment: .leading, spacing: 14) {
            HStack {
                Text("YOUR FEEDBACK").font(.caption.weight(.semibold)).foregroundStyle(.secondary)
                Spacer()
                Text(takes == 1 ? "1 take" : "\(takes) takes").font(.caption).foregroundStyle(.secondary)
            }
            HStack(spacing: 10) {
                Score(value: "\(result.accuracy)", title: "Word accuracy")
                Score(value: "\(result.rhythm)", title: "Pace & rhythm")
                Score(value: result.intonationText, title: "Intonation")
            }
            if !result.words.isEmpty {
                result.words.reduce(Text("")) { t, m in t + Text(m.word + " ").foregroundColor(m.match ? Shade.good : Shade.warn).fontWeight(m.match ? .regular : .semibold) }
                    .font(.subheadline)
            }
            ForEach(result.tips, id: \.self) { tip in
                Label { Text(tip).font(.subheadline) } icon: { Image(systemName: "arrow.up.right").foregroundStyle(.secondary) }
            }
            DisclosureGroup("How feedback works") {
                Text("These are practice estimates, not validated pronunciation grades. Word accuracy compares speech recognition with the phrase text; it does not assess individual speech sounds. Rhythm uses duration and loudness patterns. Intonation compares relative pitch contours; music and noisy recordings can affect it. The line follows the voice going up and down, relative to each speaker’s own pitch. Shaded gaps are pauses of 0.15 seconds or more. Bold words are simply the loudest ones, which are often but not always the stressed words. A saved take is stretched to the speaker’s length so the lines line up.")
                    .font(.footnote).foregroundStyle(.secondary).padding(.top, 6)
            }
            .font(.subheadline)
        }
        .padding(16)
        .background(Shade.card, in: RoundedRectangle(cornerRadius: 16))
    }
}

struct Score: View {
    let value: String; let title: String
    var body: some View {
        VStack(alignment: .leading, spacing: 4) {
            Text(value).font(.title.bold().monospacedDigit())
            Text(title).font(.caption).foregroundStyle(.secondary)
        }
        .frame(maxWidth: .infinity, alignment: .leading)
        .padding(12)
        .background(Color(.systemBackground), in: RoundedRectangle(cornerRadius: 12))
    }
}

struct PhraseList: View {
    @EnvironmentObject var model: ShadowModel
    @Environment(\.dismiss) var dismiss
    var body: some View {
        NavigationStack {
            List {
                ForEach(Array((model.state.lesson?.phrases ?? []).enumerated()), id: \.offset) { i, p in
                    Button { model.select(i); dismiss() } label: {
                        HStack(alignment: .top, spacing: 12) {
                            Text(String(format: "%02d", i + 1)).font(.caption.monospacedDigit()).foregroundStyle(.secondary).padding(.top, 2)
                            VStack(alignment: .leading, spacing: 3) {
                                Text(p.text).foregroundStyle(.primary).lineLimit(3)
                                Text(clock(p.start) + " – " + clock(p.end) + (p.latest.map { " · \($0.accuracy) accuracy" } ?? "")).font(.caption).foregroundStyle(.secondary)
                            }
                            Spacer()
                            if p.isPractised { Image(systemName: "checkmark.circle.fill").foregroundStyle(Shade.good) }
                        }
                    }
                    .listRowBackground(i == Int(model.state.index) ? Shade.card : nil)
                }
            }
            .navigationTitle("Your phrases · \(model.state.practisedCount) practised")
            .navigationBarTitleDisplayMode(.inline)
            .toolbar { ToolbarItem(placement: .confirmationAction) { Button("Done") { dismiss() } } }
        }
    }
}

// MARK: Sheets

struct ImportSheet: View {
    @EnvironmentObject var model: ShadowModel
    @Environment(\.dismiss) var dismiss
    @State private var url = ""
    @State private var picking = false

    var body: some View {
        NavigationStack {
            Form {
                Section {
                    Text("Choose clear English speech from one speaker. Short clips with little background music work best.").font(.subheadline).foregroundStyle(.secondary)
                }
                Section {
                    TextField("https://www.youtube.com/watch?v=…", text: $url).keyboardType(.URL).textInputAutocapitalization(.never).autocorrectionDisabled()
                    Button("Create practice session") { model.store.importLink(url: url); dismiss() }.disabled(url.trimmingCharacters(in: .whitespaces).isEmpty)
                } header: { Text("YouTube video link") } footer: { Text("Up to 30 minutes. Import content you have permission to use.") }
                Section {
                    Button { picking = true } label: { Label("Choose an audio or video file", systemImage: "square.and.arrow.up") }
                } header: { Text("Or upload a file") } footer: { Text("MP3, WAV, M4A, MP4, WebM · up to 150 MB") }
                Section {
                    Label("Processing and recordings stay on your Mac. Importing a link connects to YouTube; the first transcription downloads a speech model.", systemImage: "lock.fill")
                        .font(.footnote).foregroundStyle(.secondary)
                }
            }
            .navigationTitle("Bring a voice you love")
            .navigationBarTitleDisplayMode(.inline)
            .toolbar { ToolbarItem(placement: .cancellationAction) { Button("Cancel") { dismiss() } } }
            .fileImporter(isPresented: $picking, allowedContentTypes: [.audio, .movie, .audiovisualContent]) { result in
                if case let .success(file) = result { dismiss(); model.importFile(file) }
            }
        }
    }
}

struct EditSheet: View {
    @EnvironmentObject var model: ShadowModel
    @Environment(\.dismiss) var dismiss
    @State private var text = ""
    @State private var start = ""
    @State private var end = ""
    @State private var error = ""
    @State private var saving = false

    var body: some View {
        NavigationStack {
            Form {
                Section("Phrase text") { TextEditor(text: $text).frame(minHeight: 110) }
                Section {
                    HStack { Text("Start (seconds)"); Spacer(); TextField("0", text: $start).keyboardType(.decimalPad).multilineTextAlignment(.trailing) }
                    HStack { Text("End (seconds)"); Spacer(); TextField("0", text: $end).keyboardType(.decimalPad).multilineTextAlignment(.trailing) }
                } footer: { Text("Keep phrases under 30 seconds. Correct text before comparing takes.") }
                if !error.isEmpty { Section { Text(error).foregroundStyle(Shade.rec) } }
            }
            .navigationTitle("Refine this phrase")
            .navigationBarTitleDisplayMode(.inline)
            .toolbar {
                ToolbarItem(placement: .cancellationAction) { Button("Cancel") { dismiss() } }
                ToolbarItem(placement: .confirmationAction) { Button("Save") { save() }.disabled(saving) }
            }
            .onAppear {
                guard let p = model.phrase else { return }
                text = p.text; start = String(format: "%.2f", p.start); end = String(format: "%.2f", p.end)
            }
        }
    }

    func save() {
        guard let s = Double(start.replacingOccurrences(of: ",", with: ".")), let e = Double(end.replacingOccurrences(of: ",", with: ".")) else {
            error = "Enter text and valid start/end times."; return
        }
        saving = true; error = ""
        Task {
            do { try await model.store.savePhrase(text: text, start: s, end: e); dismiss() }
            catch { self.error = (error as NSError).userInfo["KotlinException"].flatMap { ($0 as? KotlinThrowable)?.message } ?? "Something went wrong. Please try again." }
            saving = false
        }
    }
}
