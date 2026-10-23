// Streams the Mac's main screen and sound to MirrorActivity on the TV.
//
// Usage: tvmirror <tv ip> [--extend] [--fps 60] [--mbps N] [--1080] [--h264] [--no-audio] [--mac-sound] [--test]
//   --extend     give the TV its own 16:9 screen (a second desktop) instead of copying the Mac's
//   --1080       send at most 1920x1080 instead of the Mac's full resolution (less Wi-Fi, softer text)
//   --h264       H.264 instead of HEVC (needs about twice the bitrate for the same picture)
//   --mac-sound  keep sound on the Mac's speakers too (by default they are muted while mirroring)
//   --test       send a moving test pattern instead of the screen (needs no Screen Recording permission)
//
// Built and started by scripts/mirror-tv. Wire format: see MirrorActivity in the app.
// Capture: ScreenCaptureKit. Encoding: VideoToolbox hardware encoder, low-latency mode.
// When Wi-Fi can't keep up, frames are skipped (never queued) and the next one is a keyframe.

import CoreMedia
import Foundation
import ScreenCaptureKit
import VideoToolbox

let port: UInt16 = 7070

struct Options {
    var host = ""
    var fps = 60
    var mbps = 0
    var hevc = true
    var full = true
    var audio = true
    var macSound = false
    var test = false
    var extend = false

    static func parse(_ args: [String]) -> Options {
        var o = Options()
        var it = args.dropFirst().makeIterator()
        while let a = it.next() {
            switch a {
            case "--fps": o.fps = Int(it.next() ?? "") ?? o.fps
            case "--mbps": o.mbps = Int(it.next() ?? "") ?? o.mbps
            case "--hevc": o.hevc = true
            case "--h264": o.hevc = false
            case "--1080": o.full = false
            case "--mac-sound": o.macSound = true
            case "--no-audio": o.audio = false
            case "--test": o.test = true
            case "--extend": o.extend = true
            default: o.host = a
            }
        }
        if o.host.isEmpty {
            fail("Usage: tvmirror <tv ip> [--extend] [--fps 60] [--mbps N] [--1080] [--h264] [--no-audio] [--mac-sound] [--test]")
        }
        if o.mbps == 0 {
            // Enough for sharp text; HEVC needs a bit over half what H.264 does.
            o.mbps = o.full ? (o.hevc ? (o.extend ? 35 : 30) : 50) : (o.hevc ? 10 : 15)
        }
        return o
    }
}

func log(_ s: String) {
    FileHandle.standardError.write(("tvmirror: " + s + "\n").data(using: .utf8)!)
}

func fail(_ s: String) -> Never {
    log(s)
    exit(1)
}

// MARK: - Network

/// One TCP connection with a small send buffer, so a slow Wi-Fi shows up as backpressure
/// we can react to (skip frames) instead of seconds of hidden queueing.
final class Sender {
    private let fd: Int32
    private let queue = DispatchQueue(label: "send")
    private let lock = NSLock()
    private var queued = 0
    private let limit: Int

    init(host: String, mbps: Int) {
        // About 150 ms of video may wait to be sent; more than that and frames are skipped.
        limit = max(mbps * 1_000_000 / 8 * 15 / 100, 256 * 1024)
        fd = socket(AF_INET, SOCK_STREAM, 0)
        var one: Int32 = 1
        var sndbuf: Int32 = 256 * 1024
        setsockopt(fd, IPPROTO_TCP, TCP_NODELAY, &one, socklen_t(MemoryLayout<Int32>.size))
        setsockopt(fd, SOL_SOCKET, SO_NOSIGPIPE, &one, socklen_t(MemoryLayout<Int32>.size))
        setsockopt(fd, SOL_SOCKET, SO_SNDBUF, &sndbuf, socklen_t(MemoryLayout<Int32>.size))
        var addr = sockaddr_in()
        addr.sin_family = sa_family_t(AF_INET)
        addr.sin_port = port.bigEndian
        guard inet_pton(AF_INET, host, &addr.sin_addr) == 1 else { fail("Not an IP address: \(host)") }
        let ok = withUnsafePointer(to: &addr) {
            $0.withMemoryRebound(to: sockaddr.self, capacity: 1) {
                connect(fd, $0, socklen_t(MemoryLayout<sockaddr_in>.size))
            }
        }
        if ok != 0 { fail("Can't reach the TV at \(host):\(port): \(String(cString: strerror(errno)))") }
        Thread { [fd] in readAcks(fd) }.start()
    }

    /// Bytes waiting in our queue plus the kernel's unacknowledged bytes.
    private var backlog: Int {
        var unsent: Int32 = 0
        var len = socklen_t(MemoryLayout<Int32>.size)
        getsockopt(fd, SOL_SOCKET, SO_NWRITE, &unsent, &len)
        lock.lock(); defer { lock.unlock() }
        return queued + Int(unsent)
    }

    /// Returns false (and sends nothing) when droppable and the link is backed up.
    @discardableResult
    func send(type: UInt8, pts: Int64, payload: Data, droppable: Bool) -> Bool {
        if droppable && backlog > limit {
            Stats.shared.add("skipped")
            return false
        }
        Stats.shared.add("kB sent", (13 + payload.count) / 1024)
        var packet = Data(capacity: 13 + payload.count)
        packet.append(type)
        withUnsafeBytes(of: pts.bigEndian) { packet.append(contentsOf: $0) }
        withUnsafeBytes(of: Int32(payload.count).bigEndian) { packet.append(contentsOf: $0) }
        packet.append(payload)
        lock.lock(); queued += packet.count; lock.unlock()
        queue.async { [self] in
            packet.withUnsafeBytes { raw in
                var off = 0
                while off < raw.count {
                    let n = Darwin.send(fd, raw.baseAddress! + off, raw.count - off, 0)
                    if n <= 0 {
                        log("The TV closed the connection.")
                        exit(0)
                    }
                    off += n
                }
            }
            lock.lock(); queued -= packet.count; lock.unlock()
        }
        return true
    }

    func close() {
        queue.sync {}
        Darwin.close(fd)
    }
}

/// Counters printed to the log every few seconds, to see where frames go.
final class Stats {
    static let shared = Stats()
    private let lock = NSLock()
    private var counts: [String: Int] = [:]

    func add(_ key: String, _ n: Int = 1) {
        lock.lock(); counts[key, default: 0] += n; lock.unlock()
    }

    private var delays: [Double] = []

    /// Milliseconds from capture to the frame being shown on the TV.
    func delay(_ ms: Double) {
        lock.lock(); delays.append(ms); lock.unlock()
    }

    func flush() -> String {
        lock.lock(); defer { counts = [:]; delays = []; lock.unlock() }
        var parts = counts.sorted { $0.key < $1.key }.map { "\($0.key) \($0.value)" }
        if !delays.isEmpty {
            let sorted = delays.sorted()
            let avg = sorted.reduce(0, +) / Double(sorted.count)
            parts.append(String(format: "delay avg %.0f ms, worst %.0f ms", avg, sorted.last!))
        }
        return parts.joined(separator: ", ")
    }
}

/// The TV sends back each frame's timestamp when it shows it; the difference to now is the full delay.
func readAcks(_ fd: Int32) {
    var pts: Int64 = 0
    while recv(fd, &pts, 8, MSG_WAITALL) == 8 {
        let nowUs = CMTimeGetSeconds(CMClockGetTime(CMClockGetHostTimeClock())) * 1_000_000
        Stats.shared.delay((nowUs - Double(Int64(bigEndian: pts))) / 1000)
    }
}

enum Packet {
    static let config: UInt8 = 1
    static let video: UInt8 = 2
    static let audio: UInt8 = 3
}

// MARK: - Video

final class Encoder {
    private var session: VTCompressionSession!
    private let sender: Sender
    private let hevc: Bool
    private let width: Int
    private let height: Int
    private let lock = NSLock()
    private var forceKey = true
    private var waitingForKey = true

    init(width: Int, height: Int, fps: Int, mbps: Int, hevc: Bool, sender: Sender) {
        self.width = width
        self.height = height
        self.hevc = hevc
        self.sender = sender
        let codec = hevc ? kCMVideoCodecType_HEVC : kCMVideoCodecType_H264
        var status = VTCompressionSessionCreate(
            allocator: nil, width: Int32(width), height: Int32(height), codecType: codec,
            encoderSpecification: [
                kVTVideoEncoderSpecification_EnableLowLatencyRateControl: true,
                kVTVideoEncoderSpecification_RequireHardwareAcceleratedVideoEncoder: true,
            ] as CFDictionary,
            imageBufferAttributes: nil, compressedDataAllocator: nil, outputCallback: nil, refcon: nil,
            compressionSessionOut: &session)
        if status != noErr {
            log("Low-latency encoder not available (\(status)), using the regular one.")
            status = VTCompressionSessionCreate(
                allocator: nil, width: Int32(width), height: Int32(height), codecType: codec,
                encoderSpecification: nil, imageBufferAttributes: nil, compressedDataAllocator: nil,
                outputCallback: nil, refcon: nil, compressionSessionOut: &session)
        }
        if status != noErr { fail("Can't create the video encoder (\(status)).") }
        let props: [CFString: Any] = [
            kVTCompressionPropertyKey_RealTime: true,
            kVTCompressionPropertyKey_AllowFrameReordering: false,
            kVTCompressionPropertyKey_PrioritizeEncodingSpeedOverQuality: true,
            kVTCompressionPropertyKey_AverageBitRate: mbps * 1_000_000,
            kVTCompressionPropertyKey_ExpectedFrameRate: fps,
            kVTCompressionPropertyKey_MaxKeyFrameIntervalDuration: 5,
            kVTCompressionPropertyKey_ProfileLevel: hevc
                ? kVTProfileLevel_HEVC_Main_AutoLevel : kVTProfileLevel_H264_High_AutoLevel,
        ]
        for (k, v) in props {
            VTSessionSetProperty(session, key: k, value: v as CFTypeRef)
        }
        VTCompressionSessionPrepareToEncodeFrames(session)
    }

    func encode(_ pixels: CVPixelBuffer, pts: CMTime) {
        Stats.shared.add("frames in")
        lock.lock()
        let key = forceKey
        forceKey = false
        lock.unlock()
        let frameProps = key ? [kVTEncodeFrameOptionKey_ForceKeyFrame: true] as CFDictionary : nil
        VTCompressionSessionEncodeFrame(
            session, imageBuffer: pixels, presentationTimeStamp: pts, duration: .invalid,
            frameProperties: frameProps, infoFlagsOut: nil
        ) { [self] status, _, sample in
            guard status == noErr, let sample else { return }
            output(sample)
        }
    }

    private func output(_ sample: CMSampleBuffer) {
        let keyframe = !isNotSync(sample)
        let pts = Int64(CMTimeGetSeconds(CMSampleBufferGetPresentationTimeStamp(sample)) * 1_000_000)
        lock.lock()
        let skip = waitingForKey && !keyframe
        lock.unlock()
        if skip {
            Stats.shared.add("waiting for keyframe")
            return
        }
        if keyframe, let config = parameterSets(sample) {
            sender.send(type: Packet.config, pts: pts, payload: config, droppable: false)
        }
        guard let frame = annexB(sample) else { return }
        // Keyframes always go out: everything after them depends on them.
        let sent = sender.send(type: Packet.video, pts: pts, payload: frame, droppable: !keyframe)
        if sent { Stats.shared.add(keyframe ? "keyframes out" : "frames out") }
        lock.lock()
        if sent {
            waitingForKey = false
        } else {
            waitingForKey = true
            forceKey = true
        }
        lock.unlock()
    }

    private func isNotSync(_ sample: CMSampleBuffer) -> Bool {
        guard let list = CMSampleBufferGetSampleAttachmentsArray(sample, createIfNecessary: false) as? [[CFString: Any]],
              let first = list.first else { return false }
        return first[kCMSampleAttachmentKey_NotSync] as? Bool ?? false
    }

    /// Codec byte, width, height, then the parameter sets (SPS/PPS, plus VPS for HEVC) with start codes.
    private func parameterSets(_ sample: CMSampleBuffer) -> Data? {
        guard let desc = CMSampleBufferGetFormatDescription(sample) else { return nil }
        var out = Data([hevc ? 1 : 0])
        withUnsafeBytes(of: UInt16(width).bigEndian) { out.append(contentsOf: $0) }
        withUnsafeBytes(of: UInt16(height).bigEndian) { out.append(contentsOf: $0) }
        var count = 0
        var index = 0
        repeat {
            var ptr: UnsafePointer<UInt8>?
            var size = 0
            let status = hevc
                ? CMVideoFormatDescriptionGetHEVCParameterSetAtIndex(
                    desc, parameterSetIndex: index, parameterSetPointerOut: &ptr, parameterSetSizeOut: &size,
                    parameterSetCountOut: &count, nalUnitHeaderLengthOut: nil)
                : CMVideoFormatDescriptionGetH264ParameterSetAtIndex(
                    desc, parameterSetIndex: index, parameterSetPointerOut: &ptr, parameterSetSizeOut: &size,
                    parameterSetCountOut: &count, nalUnitHeaderLengthOut: nil)
            guard status == noErr, let ptr else { return nil }
            out.append(contentsOf: [0, 0, 0, 1])
            out.append(ptr, count: size)
            index += 1
        } while index < count
        return out
    }

    /// The encoder writes 4-byte lengths before each NAL unit; Android wants start codes.
    private func annexB(_ sample: CMSampleBuffer) -> Data? {
        guard let block = CMSampleBufferGetDataBuffer(sample) else { return nil }
        let length = CMBlockBufferGetDataLength(block)
        var data = Data(count: length)
        let status = data.withUnsafeMutableBytes {
            CMBlockBufferCopyDataBytes(block, atOffset: 0, dataLength: length, destination: $0.baseAddress!)
        }
        guard status == noErr else { return nil }
        data.withUnsafeMutableBytes { raw in
            let p = raw.bindMemory(to: UInt8.self)
            var off = 0
            while off + 4 <= length {
                let n = Int(p[off]) << 24 | Int(p[off + 1]) << 16 | Int(p[off + 2]) << 8 | Int(p[off + 3])
                p[off] = 0; p[off + 1] = 0; p[off + 2] = 0; p[off + 3] = 1
                off += 4 + n
            }
        }
        return data
    }

    func stop() {
        VTCompressionSessionCompleteFrames(session, untilPresentationTimeStamp: .invalid)
        VTCompressionSessionInvalidate(session)
    }
}

// MARK: - Capture

final class Capture: NSObject, SCStreamOutput, SCStreamDelegate {
    private let encoder: Encoder
    private let sender: Sender
    let queue = DispatchQueue(label: "screen", qos: .userInteractive)
    private var last: CVPixelBuffer?
    private var lastAt = DispatchTime.now()
    private var idleTimer: DispatchSourceTimer?

    init(encoder: Encoder, sender: Sender) {
        self.encoder = encoder
        self.sender = sender
        super.init()
        // macOS sends nothing while the screen is still. Repeat the last frame a few times a second,
        // so the TV's decoder always has something to show and a lost frame heals quickly.
        let timer = DispatchSource.makeTimerSource(queue: queue)
        timer.schedule(deadline: .now() + .milliseconds(100), repeating: .milliseconds(100))
        timer.setEventHandler { [self] in
            guard let last, DispatchTime.now().uptimeNanoseconds - lastAt.uptimeNanoseconds > 150_000_000 else { return }
            Stats.shared.add("repeats")
            encoder.encode(last, pts: CMClockGetTime(CMClockGetHostTimeClock()))
        }
        timer.resume()
        idleTimer = timer
    }

    /// Runs on [queue].
    func show(_ pixels: CVPixelBuffer, pts: CMTime) {
        last = pixels
        lastAt = .now()
        encoder.encode(pixels, pts: pts)
    }

    func stream(_ stream: SCStream, didOutputSampleBuffer sample: CMSampleBuffer, of type: SCStreamOutputType) {
        switch type {
        case .screen: screen(sample)
        case .audio: audio(sample)
        default: break
        }
    }

    func stream(_ stream: SCStream, didStopWithError error: Error) {
        fail("Screen capture stopped: \(error.localizedDescription)")
    }

    private func screen(_ sample: CMSampleBuffer) {
        guard let list = CMSampleBufferGetSampleAttachmentsArray(sample, createIfNecessary: false) as? [[SCStreamFrameInfo: Any]],
              let raw = list.first?[.status] as? Int,
              let status = SCFrameStatus(rawValue: raw) else { return }
        guard status == .complete, let pixels = CMSampleBufferGetImageBuffer(sample) else {
            if status != .idle { Stats.shared.add("capture status \(raw)") }
            return
        }
        Stats.shared.add("captured")
        show(pixels, pts: CMSampleBufferGetPresentationTimeStamp(sample))
    }

    /// ScreenCaptureKit gives 32-bit float samples, one buffer per channel; the TV wants 16-bit interleaved.
    private func audio(_ sample: CMSampleBuffer) {
        let frames = CMSampleBufferGetNumSamples(sample)
        guard frames > 0 else { return }
        var pcm = Data(count: frames * 4)
        try? sample.withAudioBufferList { list, _ in
            guard let first = list.first?.mData else { return }
            let left = first.assumingMemoryBound(to: Float.self)
            let right = (list.count > 1 ? list[1].mData : first)!.assumingMemoryBound(to: Float.self)
            let interleaved = list.count == 1 && list[0].mNumberChannels == 2
            pcm.withUnsafeMutableBytes { raw in
                let out = raw.bindMemory(to: Int16.self)
                for i in 0..<frames {
                    let l = interleaved ? left[i * 2] : left[i]
                    let r = interleaved ? left[i * 2 + 1] : right[i]
                    out[i * 2] = Int16(max(-1, min(1, l)) * 32767).littleEndian
                    out[i * 2 + 1] = Int16(max(-1, min(1, r)) * 32767).littleEndian
                }
            }
        }
        let pts = Int64(CMTimeGetSeconds(CMSampleBufferGetPresentationTimeStamp(sample)) * 1_000_000)
        // Sound is small (1.5 Mbps) and gaps are very audible, so it is never skipped.
        sender.send(type: Packet.audio, pts: pts, payload: pcm, droppable: false)
    }
}

// MARK: - Size and sound

/// The Mac's real pixel size, fitted inside 4K (or 1080p), keeping its shape. Encoders want even sizes.
func streamSize(displayID: CGDirectDisplayID, full: Bool) -> (Int, Int) {
    let mode = CGDisplayCopyDisplayMode(displayID)
    let pw = Double(mode?.pixelWidth ?? 1920)
    let ph = Double(mode?.pixelHeight ?? 1080)
    let (maxW, maxH) = full ? (3840.0, 2160.0) : (1920.0, 1080.0)
    let scale = min(1, maxW / pw, maxH / ph)
    return (Int(pw * scale) & ~1, Int(ph * scale) & ~1)
}

func osascript(_ script: String) -> String {
    let p = Process()
    p.executableURL = URL(fileURLWithPath: "/usr/bin/osascript")
    p.arguments = ["-e", script]
    let out = Pipe()
    p.standardOutput = out
    try? p.run()
    p.waitUntilExit()
    return String(data: out.fileHandleForReading.readDataToEndOfFile(), encoding: .utf8)?
        .trimmingCharacters(in: .whitespacesAndNewlines) ?? ""
}

/// Mutes the Mac's speakers so sound only plays on the TV; puts them back however tvmirror exits.
func muteMacWhileMirroring() {
    guard osascript("output muted of (get volume settings)") == "false" else { return }
    _ = osascript("set volume with output muted")
    atexit { _ = osascript("set volume without output muted") }
}

// MARK: - TV screen

/// Keeps the virtual screen alive; macOS removes it when this object goes away or tvmirror exits.
var tvDisplay: CGVirtualDisplay?

/// Adds a 16:9 screen called "TV" to the Mac: 1920x1080 points, drawn at 4K (or 1080p with --1080).
func addTVDisplay(full: Bool) -> CGDirectDisplayID {
    let desc = CGVirtualDisplayDescriptor()
    desc.queue = DispatchQueue.main
    desc.name = "TV"
    desc.maxPixelsWide = 3840
    desc.maxPixelsHigh = 2160
    desc.sizeInMillimeters = CGSize(width: 1210, height: 680)
    desc.vendorID = 0x7476
    desc.productID = 0x0001
    desc.serialNum = 0x0001
    desc.terminationHandler = { _, _ in log("macOS removed the TV screen.") }
    let display = CGVirtualDisplay(descriptor: desc)
    let settings = CGVirtualDisplaySettings()
    settings.hiDPI = full ? 1 : 0
    settings.modes = [CGVirtualDisplayMode(width: 1920, height: 1080, refreshRate: 60)]
    guard display.apply(settings) else { fail("Couldn't create the TV screen on this Mac.") }
    tvDisplay = display
    return display.displayID
}

/// Puts the TV screen above the Mac's, centred, so the mouse reaches it through the top edge.
func placeAboveMac(_ tvID: CGDirectDisplayID) {
    let mac = CGDisplayBounds(CGMainDisplayID())
    let tv = CGDisplayBounds(tvID)
    var config: CGDisplayConfigRef?
    guard CGBeginDisplayConfiguration(&config) == .success else { return }
    CGConfigureDisplayOrigin(config, tvID, Int32(mac.midX - tv.width / 2), Int32(mac.minY - tv.height))
    if CGCompleteDisplayConfiguration(config, .forSession) != .success {
        log("Couldn't move the TV screen above the Mac; arrange it in System Settings > Displays.")
    }
}

// MARK: - Test pattern

/// A moving gradient with a bar sweeping across, generated without Screen Recording permission.
func startTestPattern(width: Int, height: Int, fps: Int, capture: Capture) {
    var pool: CVPixelBufferPool?
    CVPixelBufferPoolCreate(nil, nil, [
        kCVPixelBufferPixelFormatTypeKey: kCVPixelFormatType_420YpCbCr8BiPlanarVideoRange,
        kCVPixelBufferWidthKey: width,
        kCVPixelBufferHeightKey: height,
        kCVPixelBufferIOSurfacePropertiesKey: [:],
    ] as CFDictionary, &pool)
    var frame = 0
    let timer = DispatchSource.makeTimerSource(queue: capture.queue)
    timer.schedule(deadline: .now(), repeating: .nanoseconds(1_000_000_000 / fps))
    timer.setEventHandler {
        var pb: CVPixelBuffer?
        guard let pool, CVPixelBufferPoolCreatePixelBuffer(nil, pool, &pb) == kCVReturnSuccess, let pb else { return }
        CVPixelBufferLockBaseAddress(pb, [])
        let y = CVPixelBufferGetBaseAddressOfPlane(pb, 0)!.assumingMemoryBound(to: UInt8.self)
        let yStride = CVPixelBufferGetBytesPerRowOfPlane(pb, 0)
        let uv = CVPixelBufferGetBaseAddressOfPlane(pb, 1)!.assumingMemoryBound(to: UInt8.self)
        let uvStride = CVPixelBufferGetBytesPerRowOfPlane(pb, 1)
        let bar = (frame * 8) % width
        for row in 0..<height {
            for col in 0..<width {
                y[row * yStride + col] = abs(col - bar) < 40 ? 235 : UInt8(16 + (col + row + frame * 2) % 200)
            }
        }
        for row in 0..<(height / 2) {
            for col in 0..<(width / 2) {
                uv[row * uvStride + col * 2] = UInt8((col + frame) % 256)
                uv[row * uvStride + col * 2 + 1] = UInt8((row * 2) % 256)
            }
        }
        CVPixelBufferUnlockBaseAddress(pb, [])
        frame += 1
        capture.show(pb, pts: CMClockGetTime(CMClockGetHostTimeClock()))
    }
    timer.resume()
    testTimer = timer
}

// MARK: - Main

let opts = Options.parse(CommandLine.arguments)
var signalSources: [DispatchSourceSignal] = []
var testTimer: DispatchSourceTimer?
var statsTimer: DispatchSourceTimer?

func onExit(_ stop: @escaping () async -> Void) {
    for sig in [SIGINT, SIGTERM] {
        signal(sig, SIG_IGN)
        let source = DispatchSource.makeSignalSource(signal: sig)
        source.setEventHandler {
            Task {
                await stop()
                log("Stopped.")
                exit(0)
            }
        }
        source.resume()
        signalSources.append(source)
    }
}

func startStats() {
    let timer = DispatchSource.makeTimerSource(queue: .global())
    timer.schedule(deadline: .now() + 2, repeating: 2)
    timer.setEventHandler { log("last 2s: " + Stats.shared.flush()) }
    timer.resume()
    statsTimer = timer
}

Task {
    if opts.test {
        var id = CGMainDisplayID()
        if opts.extend {
            id = addTVDisplay(full: opts.full)
            try? await Task.sleep(nanoseconds: 1_000_000_000)
            placeAboveMac(id)
        }
        let (w, h) = streamSize(displayID: id, full: opts.full)
        let sender = Sender(host: opts.host, mbps: opts.mbps)
        let encoder = Encoder(width: w, height: h, fps: opts.fps, mbps: opts.mbps, hevc: opts.hevc, sender: sender)
        let capture = Capture(encoder: encoder, sender: sender)
        startTestPattern(width: w, height: h, fps: opts.fps, capture: capture)
        log("Test pattern \(w)x\(h) at \(opts.fps) fps, \(opts.mbps) Mbps \(opts.hevc ? "HEVC" : "H.264") to \(opts.host).")
        startStats()
        onExit {
            encoder.stop()
            sender.close()
        }
        return
    }

    let wantedID = opts.extend ? addTVDisplay(full: opts.full) : CGMainDisplayID()
    var found: SCDisplay?
    // A new screen takes a moment to show up for capture.
    for _ in 0..<20 {
        let content: SCShareableContent
        do {
            content = try await SCShareableContent.excludingDesktopWindows(false, onScreenWindowsOnly: true)
        } catch {
            fail("""
                No permission to record the screen. Open System Settings > Privacy & Security > \
                Screen & System Audio Recording, turn on your terminal app, then run mirror-tv again.
                """)
        }
        found = content.displays.first { $0.displayID == wantedID }
        if found != nil || !opts.extend { break }
        try? await Task.sleep(nanoseconds: 200_000_000)
    }
    guard let display = found else { fail("Screen not found for capture.") }
    if opts.extend { placeAboveMac(display.displayID) }

    let (w, h) = streamSize(displayID: display.displayID, full: opts.full)

    let sender = Sender(host: opts.host, mbps: opts.mbps)
    let encoder = Encoder(width: w, height: h, fps: opts.fps, mbps: opts.mbps, hevc: opts.hevc, sender: sender)
    let capture = Capture(encoder: encoder, sender: sender)

    let config = SCStreamConfiguration()
    config.width = w
    config.height = h
    config.minimumFrameInterval = CMTime(value: 1, timescale: CMTimeScale(opts.fps))
    config.pixelFormat = kCVPixelFormatType_420YpCbCr8BiPlanarVideoRange
    // Room for frames held by the encoder and the still-screen repeat; with fewer, macOS drops frames.
    config.queueDepth = 8
    config.showsCursor = true
    config.capturesAudio = opts.audio
    config.sampleRate = 48_000
    config.channelCount = 2
    config.excludesCurrentProcessAudio = true

    let stream = SCStream(filter: SCContentFilter(display: display, excludingWindows: []), configuration: config, delegate: capture)
    do {
        try stream.addStreamOutput(capture, type: .screen, sampleHandlerQueue: capture.queue)
        if opts.audio {
            try stream.addStreamOutput(capture, type: .audio, sampleHandlerQueue: DispatchQueue(label: "audio", qos: .userInteractive))
        }
        try await stream.startCapture()
    } catch {
        fail("Can't start screen capture: \(error.localizedDescription)")
    }
    log("\(opts.extend ? "TV screen" : "Mirroring") \(w)x\(h) at \(opts.fps) fps, \(opts.mbps) Mbps \(opts.hevc ? "HEVC" : "H.264")\(opts.audio ? " with sound" : "") to \(opts.host).")
    if opts.audio && !opts.macSound { muteMacWhileMirroring() }
    startStats()
    onExit {
        try? await stream.stopCapture()
        encoder.stop()
        sender.close()
    }
}

dispatchMain()
