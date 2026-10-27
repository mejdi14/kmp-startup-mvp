import Foundation
import AVFoundation
import SharedLogic

/// iOS adapter over the shared `ShadowStore`: mirrors its state and owns native playback and recording,
/// like the Mac app's `Store` (`mac/Store.swift`).
@MainActor
final class ShadowModel: NSObject, ObservableObject, AVAudioPlayerDelegate {
    let store = ShadowGraph().store
    @Published private(set) var state: ShadowState
    @Published var speed = 1.0 { didSet { original?.rate = Float(speed) } }
    @Published var looping = false
    @Published var audioReady = false
    @Published var isPlaying = false
    @Published var position = 0.0
    @Published var isTakePlaying = false
    @Published var recording = false
    @Published var starting = false
    @Published var recordSeconds = 0.0

    private var watcher: Watcher?
    private var original: AVAudioPlayer?
    private var takePlayer: AVAudioPlayer?
    private var recorder: AVAudioRecorder?
    private var recordFile: URL?
    private var pollTask: Task<Void, Never>?
    private var recordTask: Task<Void, Never>?
    private var autoStop: Task<Void, Never>?
    private var playbackDone: (() -> Void)?
    private var playOnce = false

    override init() {
        state = store.state.value as! ShadowState
        super.init()
        watcher = FlowWatchKt.watch(store.state) { [weak self] value in
            MainActor.assumeIsolated { if let s = value as? ShadowState { self?.apply(s) } }
        }
        try? AVAudioSession.sharedInstance().setCategory(.playAndRecord, mode: .default, options: [.defaultToSpeaker, .allowBluetoothA2DP])
        store.start()
    }

    deinit { watcher?.cancel() }

    var phrase: Phrase? { state.phrase }
    var locked: Bool { recording || starting || state.locked }

    private func apply(_ next: ShadowState) {
        let before = state
        state = next
        if before.lesson?.id != next.lesson?.id {
            stopPlayback(); original = nil; audioReady = false
            if let id = next.lesson?.id { loadAudio(id) }
        }
        if before.index != next.index || before.phrase?.start != next.phrase?.start || before.phrase?.end != next.phrase?.end {
            if !recording { stopPlayback() }
            position = 0
        }
    }

    private func loadAudio(_ id: String) {
        Task {
            do {
                let bytes = try await store.audio(path: store.lessonAudioPath(id: id))
                guard state.lesson?.id == id else { return }
                let p = try AVAudioPlayer(data: ShadowGraphKt.dataOf(bytes: bytes))
                p.enableRate = true; p.rate = Float(speed); p.prepareToPlay()
                original = p; audioReady = true
            } catch {
                if state.lesson?.id == id { store.show(message: "The lesson audio couldn't be loaded. Open the session again.") }
            }
        }
    }

    // MARK: Playback

    func stopPlayback() {
        pollTask?.cancel(); pollTask = nil; playbackDone = nil
        original?.pause(); takePlayer?.stop()
        isPlaying = false; isTakePlaying = false
    }

    func playOriginal(once: Bool = false, rate: Double? = nil, onDone: (() -> Void)? = nil) {
        guard let p = original, let ph = phrase else { return }
        stopPlayback()
        try? AVAudioSession.sharedInstance().setActive(true)
        p.rate = Float(rate ?? speed); p.currentTime = ph.start + (once || position >= ph.length - 0.05 ? 0 : position)
        playbackDone = onDone; playOnce = once
        p.play(); isPlaying = true
        pollTask = Task {
            while !Task.isCancelled {
                try? await Task.sleep(nanoseconds: 30_000_000)
                guard !Task.isCancelled, let ph = phrase else { return }
                position = min(max(0, p.currentTime - ph.start), ph.length)
                if p.currentTime >= ph.end - 0.015 || !p.isPlaying {
                    if looping && !playOnce && !recording { p.currentTime = ph.start; p.play() }
                    else { let done = playbackDone; stopPlayback(); position = ph.length; done?(); return }
                }
            }
        }
    }

    func seek(_ x: Double) {
        guard let ph = phrase else { return }
        position = min(max(0, x), ph.length)
        if isPlaying { original?.currentTime = ph.start + position }
    }

    func togglePlay() {
        guard state.lesson != nil, !starting else { return }
        if isPlaying {
            if recording && state.mode == .shadow { stopRecording(); return }
            stopPlayback()
        } else { playOriginal() }
    }

    func playTake() {
        guard let r = phrase?.latest else { return }
        stopPlayback()
        Task {
            do {
                let bytes = try await store.audio(path: r.audio)
                let p = try AVAudioPlayer(data: ShadowGraphKt.dataOf(bytes: bytes))
                try? AVAudioSession.sharedInstance().setActive(true)
                p.delegate = self; takePlayer = p
                p.play(); isTakePlaying = true
            } catch { store.show(message: "Your take couldn't be played. Try recording it again.") }
        }
    }

    func toggleTake() { if isTakePlaying { stopPlayback() } else { playTake() } }

    func compareAB() {
        playOriginal(once: true, rate: phrase?.latest?.playbackSpeed?.doubleValue ?? 1) { [weak self] in self?.playTake() }
    }

    nonisolated func audioPlayerDidFinishPlaying(_ player: AVAudioPlayer, successfully flag: Bool) {
        Task { @MainActor in self.isTakePlaying = false }
    }

    // MARK: Navigation

    func select(_ i: Int) { guard !locked else { return }; store.select(index: Int32(i)) }
    func next() { select(Int(state.index) + 1) }
    func previous() { select(Int(state.index) - 1) }
    func setMode(_ m: Mode) { guard !recording && !starting else { return }; store.setMode(mode: m) }

    func leaveLesson() {
        if recording { cancelRecording() }
        stopPlayback()
    }

    // MARK: Recording

    func toggleRecording() async {
        if recording { stopRecording(); return }
        guard state.lesson != nil, !locked else { return }
        starting = true; stopPlayback()
        guard await AVAudioApplication.requestRecordPermission() else {
            starting = false
            store.show(message: "Microphone access was denied. Allow Shadow Studio in Settings → Privacy & Security → Microphone, then try again.")
            return
        }
        try? AVAudioSession.sharedInstance().setActive(true)
        let url = FileManager.default.temporaryDirectory.appendingPathComponent(UUID().uuidString + ".wav")
        let settings: [String: Any] = [AVFormatIDKey: kAudioFormatLinearPCM, AVSampleRateKey: 16000, AVNumberOfChannelsKey: 1, AVLinearPCMBitDepthKey: 16, AVLinearPCMIsFloatKey: false, AVLinearPCMIsBigEndianKey: false]
        guard let r = try? AVAudioRecorder(url: url, settings: settings), r.record() else {
            starting = false; store.show(message: "Microphone recording failed. Please try again."); return
        }
        recorder = r; recordFile = url
        recording = true; starting = false; recordSeconds = 0
        let began = Date()
        recordTask = Task { while !Task.isCancelled { recordSeconds = Date().timeIntervalSince(began); try? await Task.sleep(nanoseconds: 200_000_000) } }
        autoStop = Task { try? await Task.sleep(nanoseconds: 34_000_000_000); if !Task.isCancelled { stopRecording() } }
        if state.mode == .shadow {
            playOriginal(once: true) { [weak self] in
                self?.autoStop?.cancel()
                self?.autoStop = Task { try? await Task.sleep(nanoseconds: 500_000_000); if !Task.isCancelled { self?.stopRecording() } }
            }
        }
    }

    func stopRecording() {
        guard recording else { return }
        let url = endRecording()
        guard let url else { return }
        defer { try? FileManager.default.removeItem(at: url) }
        guard let data = try? Data(contentsOf: url), !data.isEmpty else { store.show(message: "Microphone recording failed. Please try again."); return }
        store.submitTake(bytes: ShadowGraphKt.bytesOf(data: data), contentType: "audio/wav", speed: state.mode == .shadow ? speed : 1)
    }

    private func cancelRecording() { if let url = endRecording() { try? FileManager.default.removeItem(at: url) } }

    private func endRecording() -> URL? {
        recording = false
        recordTask?.cancel(); autoStop?.cancel(); stopPlayback()
        recorder?.stop(); recorder = nil
        defer { recordFile = nil }
        return recordFile
    }

    // MARK: Import

    func importFile(_ url: URL) {
        let access = url.startAccessingSecurityScopedResource()
        defer { if access { url.stopAccessingSecurityScopedResource() } }
        if ((try? url.resourceValues(forKeys: [.fileSizeKey]).fileSize) ?? 0) > Int(ShadowStore.companion.MAX_UPLOAD) {
            store.show(message: ShadowStore.companion.TOO_LARGE); return
        }
        guard let data = try? Data(contentsOf: url, options: .mappedIfSafe) else {
            store.show(message: "That file couldn't be opened. Choose another audio or video file."); return
        }
        store.importFile(name: url.lastPathComponent, bytes: ShadowGraphKt.bytesOf(data: data))
    }
}
